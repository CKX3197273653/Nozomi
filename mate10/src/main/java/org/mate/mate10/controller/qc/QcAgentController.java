package org.mate.mate10.controller.qc;

import lombok.extern.slf4j.Slf4j;
import org.mate.mate10.agent.AgentFactory;
import org.mate.mate10.agent.QcAnalysisAgent;
import org.mate.mate10.common.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

@Slf4j
@RestController
public class QcAgentController {

    @Autowired
    private AgentFactory agentFactory;

    @Autowired
    private ThreadPoolTaskExecutor streamExecutor;

    /** 非流式：一次性返回结果（用于简单测试 / Apifox） */
    @PostMapping("/ai/qc-agent")
    public Result<String> analyze(@RequestBody Map<String, String> body) {
        String question = body.getOrDefault("question", "");
        if (question.isBlank()) {
            return Result.error("question 不能为空");
        }
        long start = System.currentTimeMillis();
        try {
            // 非流式场景：传 null emitter，轨迹事件自动丢弃
            QcAnalysisAgent agent = agentFactory.create(null);
            String answer = agent.analyze(question);
            log.info("Agent 分析完成，耗时 {} ms", System.currentTimeMillis() - start);
            return Result.success(answer);
        } catch (Exception e) {
            log.error("Agent 分析失败", e);
            return Result.error("Agent 分析失败：" + e.getMessage());
        }
    }

    /**
     * 流式：实时推送【工具调用过程】+ 最终答案。
     *
     * <p>事件序列：</p>
     * <pre>
     *   event: start   data: 问题文本
     *   event: tool    data: {"name":"getReport","args":"{...}","status":"running",...}
     *   event: tool    data: {"name":"getReport","status":"done","result":"报告ID: 1 ..."}
     *   event: message data: "## 根因判断 ..."
     *   event: done    data: [DONE]
     * </pre>
     *
     * <p>⚠️ 必须放在独立线程执行 —— 否则模型调用会阻塞 Servlet 线程，
     * 事件被缓冲、流式失效（与 {@code /ai/chat/stream} 同理）。</p>
     */
    @PostMapping(value = "/ai/qc-agent/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody Map<String, String> body) {
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L);   // 5 分钟超时
        String question = (body == null) ? "" : body.getOrDefault("question", "");

        if (question.isBlank()) {
            emitter.completeWithError(new IllegalArgumentException("question 不能为空"));
            return emitter;
        }

        streamExecutor.execute(() -> {
            long start = System.currentTimeMillis();
            try {
                emitter.send(SseEmitter.event().name("start").data(question));

                // 关键：把 emitter 交给工厂，闭包把它绑定到本次 Agent 实例
                QcAnalysisAgent agent = agentFactory.create(emitter);
                String answer = agent.analyze(question);

                emitter.send(SseEmitter.event().name("message").data(answer));
                log.info("Agent 流式分析完成，耗时 {} ms", System.currentTimeMillis() - start);

            } catch (Exception e) {
                log.error("Agent 流式执行失败", e);
                try {
                    emitter.send(SseEmitter.event().name("error")
                            .data("Agent 执行失败：" + e.getMessage()));
                } catch (IOException ignored) {
                    // 前端已断开
                }
            } finally {
                try {
                    emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                } catch (IOException ignored) {
                    // 前端已断开
                }
                emitter.complete();
            }
        });

        return emitter;
    }
}
