package org.mate.mate10.controller.qc;

import lombok.extern.slf4j.Slf4j;
import org.mate.mate10.agent.AgentFactory;
import org.mate.mate10.agent.QcAnalysisAgent;
import org.mate.mate10.common.Result;
import org.mate.mate10.config.ChatContextBuilder;
import org.mate.mate10.service.MongoChatMessageService;
import org.mate.mate10.service.ReportMemoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
public class QcAgentController {

    @Autowired
    private AgentFactory agentFactory;

    @Autowired
    private ThreadPoolTaskExecutor streamExecutor;

    @Autowired
    private MongoChatMessageService mongoChatMessageService;

    @Autowired
    private ChatContextBuilder chatContextBuilder;

    @Autowired
    private ReportMemoryService reportMemoryService;

    //非流式
    @PostMapping("/ai/qc-agent")
    public Result<String> analyze(@RequestBody Map<String, String> body) {
        String question = (body == null) ? "" : body.getOrDefault("question", "");
        if (question.isBlank()) {
            return Result.error("question 不能为空");
        }
        Long chatId = (body == null) ? null : parseLong(body.get("chatId"));
        Long userId = (body == null) ? null : parseLong(body.get("userId"));

        long start = System.currentTimeMillis();
        try {
            // 提取编号
            List<String> reportNos = chatContextBuilder.extractReportNo(question);
            //  组装
            String history = chatContextBuilder.buildAgentHistory(chatId, reportNos);

            QcAnalysisAgent agent = agentFactory.create(null);
            String answer = agent.analyze(history, question);

            saveMemory(chatId, userId, question, answer, reportNos);
            log.info("Agent 分析完成，耗时 {} ms", System.currentTimeMillis() - start);
            return Result.success(answer);
        } catch (Exception e) {
            log.error("Agent 分析失败", e);
            return Result.error("Agent 分析失败：" + e.getMessage());
        }
    }


    //流式
    @PostMapping(value = "/ai/qc-agent/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody Map<String, String> body) {
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L);   // 5 分钟超时
        String question = (body == null) ? "" : body.getOrDefault("question", "");
        Long chatId = (body == null) ? null : parseLong(body.get("chatId"));
        Long userId = (body == null) ? null : parseLong(body.get("userId"));

        if (question.isBlank()) {
            emitter.completeWithError(new IllegalArgumentException("question 不能为空"));
            return emitter;
        }

        streamExecutor.execute(() -> {
            long start = System.currentTimeMillis();
            try {
                emitter.send(SseEmitter.event().name("start").data(question));

                //  提取编号  组装历史   调 Agent   落库
                List<String> reportNos = chatContextBuilder.extractReportNo(question);
                String history = chatContextBuilder.buildAgentHistory(chatId, reportNos);
                QcAnalysisAgent agent = agentFactory.create(emitter);
                String answer = agent.analyze(history, question);

                emitter.send(SseEmitter.event().name("message").data(answer));
                saveMemory(chatId, userId, question, answer, reportNos);
                log.info("Agent 流式分析完成，耗时 {} ms", System.currentTimeMillis() - start);

            } catch (Exception e) {
                log.error("Agent 流式执行失败", e);
                try {
                    emitter.send(SseEmitter.event().name("error")
                            .data("Agent 执行失败：" + e.getMessage()));
                } catch (Exception ignored) {
                    // 前端已断开，或 emitter 已结束（超时/已 complete）——都无需处理
                }
            } finally {
                try {
                    emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                } catch (Exception  ignored) {
                    // 前端已断开，或 emitter 已结束（超时/已 complete）——都无需处理
                }
                emitter.complete();
            }
        });

        return emitter;
    }

    // 双键落库
    /*
    * 第一层: 会话事件流 - 原始回答,提供会话上下文 + 审计可追溯
    * 第二层: 报告结论档案 - 蒸馏结论,供团队共享
    * 第一层需要chatId.第二层需要reportNo,不能用统一的 early return判断,两个必须要独立的if
    * */
    private void saveMemory(Long chatId, Long userId, String question,
                            String answer, List<String> reportNos) {
        String reportNo = reportNos.isEmpty() ? null : reportNos.get(0);
        //第一层,会话事件流,我们在这里拿userId+chatId
        if(userId != null && chatId != null) {
            mongoChatMessageService.saveUserMessage(chatId, userId, question, reportNo);
            mongoChatMessageService.saveAiMessage(chatId, userId, answer, reportNo);
        }
        //第二层,报告结论档案,这里我们只需要reportNo,这里的chatId如果是null也是需要入档的
        if (reportNo != null) {
            reportMemoryService.saveConclusion(reportNo,answer,userId);
        }
    }

    private Long parseLong(String s) {
        return (s == null || s.isBlank()) ? null : Long.parseLong(s);
    }
}