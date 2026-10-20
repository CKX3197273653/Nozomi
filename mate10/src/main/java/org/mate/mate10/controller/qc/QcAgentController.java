package org.mate.mate10.controller.qc;

import lombok.extern.slf4j.Slf4j;
import org.mate.mate10.agent.AgentFactory;
import org.mate.mate10.agent.QcAnalysisAgent;
import org.mate.mate10.common.Result;
import org.mate.mate10.config.ChatContextBuilder;
import org.mate.mate10.document.ReportMemoryDocument;
import org.mate.mate10.service.MongoChatMessageService;
import org.mate.mate10.service.ReportDataFingerprintService;
import org.mate.mate10.service.ReportMemoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.mate.mate10.common.MemoryChangeReason;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
public class QcAgentController {
    private static final DateTimeFormatter MINUTE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    @Autowired
    private AgentFactory agentFactory;
    @Autowired
    @Qualifier("agentStreamExecutor")
    private ThreadPoolTaskExecutor streamExecutor;
    @Autowired
    private MongoChatMessageService mongoChatMessageService;
    @Autowired
    private ChatContextBuilder chatContextBuilder;
    @Autowired
    private ReportMemoryService reportMemoryService;
    @Autowired
    private ReportDataFingerprintService fingerprintService;

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
            String reportNo = reportNos.isEmpty() ? null : reportNos.get(0);
            //指纹只计算一次,复用判断和入档共用
            String dataVersion = (reportNo == null) ? null : fingerprintService.fingerprint(reportNo);

            String reused = tryReuse(question, reportNo, dataVersion);
            String answer;
            if(reused != null) {
                answer = reused;
            }else {
                //  组装
                String history = chatContextBuilder.buildAgentHistory(chatId, reportNos);
                QcAnalysisAgent agent = agentFactory.create(null);
                String raw = agent.analyze(history,question);
                //先入档拿到覆盖原因.再拼提示行.最后才落第一层
                String changeReason = archive(reportNo, raw, userId, dataVersion);
                answer = prependHeader(raw, reportNo, changeReason);
            }
            //取代saveMemory
            saveConversation(chatId, userId, question, answer, reportNo);
            log.info("Agent 分析完成，耗时 {} ms{}", System.currentTimeMillis() - start,
                    reused != null ? "（复用既有结论，未调用模型）" : "");
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
                String reportNo = reportNos.isEmpty() ? null : reportNos.get(0);
                String dataVersion = (reportNo == null) ? null : fingerprintService.fingerprint(reportNo);
                String reused = tryReuse(question, reportNo, dataVersion);
                String answer;
                if (reused != null) {
                    //复用,不建Agent,不调模型,照常发 SSE事件,前端无感
                    answer = reused;
                }else{
                    String history = chatContextBuilder.buildAgentHistory(chatId, reportNos);
                    QcAnalysisAgent agent = agentFactory.create(emitter);
                    String raw = agent.analyze(history,question);
                    String changeReason = archive(reportNo, raw, userId, dataVersion);
                    answer = prependHeader(raw, reportNo, changeReason);
                }

                emitter.send(SseEmitter.event().name("message").data(answer));
                saveConversation(chatId, userId, question, answer, reportNo);
                log.info("Agent 流式分析完成，耗时 {} ms{}", System.currentTimeMillis() - start,
                        reused != null ? "（复用既有结论，未调用模型）" : "");

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
    //数据指纹复用 ,,和档案一样,有就复用
    private String tryReuse(String question, String reportNo, String dataVersion) {

        if (reportNo == null || dataVersion == null) {
            return null;
        }
        if (!isRootCauseQuestion(question)) {
            log.info("[Reuse] reportNo={} 非根因分析类问句，不复用", reportNo);
            return null;
        }
        if (wantsFresh(question)) {
            log.info("[Reuse] reportNo={} 用户要求重新分析，跳过复用", reportNo);
            return null;
        }
        ReportMemoryDocument cur = reportMemoryService.getCurrent(reportNo);
        if (cur == null || cur.getDataVersion() == null) {
            return null;                                  // 没有档案 → 正常分析
        }
        if (!dataVersion.equals(cur.getDataVersion())) {
            log.info("[Reuse] reportNo={} 数据已更新（{} → {}），需重新分析",
                    reportNo, cur.getDataVersion(), dataVersion);
            return null;                                  // 数据变了 → 正常分析
        }
        log.info("[Reuse] reportNo={} 数据指纹一致（{}），复用既有结论，跳过 Agent 调用",
                reportNo, dataVersion);
        //优先返回完整回答(保证四段);老档案没有 fullAnswer 时退化为两段拼接
        //优先返回完整回答,老档案没有完整回答(fullAnswer)时退化为两段拼接
        String body = (cur.getFullAnswer() != null && !cur.getFullAnswer().isBlank())
                ? cur.getFullAnswer()
                : "## 根因判断\n" + cur.getConclusion()
                  + "\n\n## 改进建议\n" + (cur.getAdvice() == null ? "-" : cur.getAdvice());

        String lastAnalyzedAt  = (cur.getAnalyzedAt() == null) ? "-"
                : cur.getAnalyzedAt().format(MINUTE_FORMAT);
        return ">  **数据未变化**：报告数据自 " + lastAnalyzedAt  + " 起未发生变化，"
                + "以下为该报告的**既有结论**（未重新分析）。\n"
                + "> 如需重新分析，请说「重新分析 " + reportNo + " 的根因」。\n\n"
                + body;
    }
    //只有根因分析问句才参与复用,档案里存的就是根因分析
    private boolean isRootCauseQuestion(String question) {
        return question.contains("根因") || question.contains("归因")
                || question.contains("原因") || question.contains("为什么");
    }

    //逃生门,需要重新分析用
    private boolean wantsFresh(String question) {
        return question.matches(".*(重新|再分析|重算|强制|最新分析).*");
    }
//提示行
/*
* 新分析路径的提示行,新分析标识
* 非报告分析(reportNo为null)或未入档( changeReason == null )不加
* */
    private String prependHeader(String raw, String reportNo, String changeReason) {
        if (reportNo == null || changeReason == null) {
            return raw;
        }
        String head = switch (changeReason) {
            case MemoryChangeReason.FIRST ->
                    ">  **首次分析** " + reportNo + "，以下结论已入档。\n\n";
            case MemoryChangeReason.DATA_UPDATED ->
                    ">  **数据已更新**：本次基于最新数据重新分析，"
                            + "以下结论将覆盖 " + reportNo + " 的上一条档案。\n\n";
            case MemoryChangeReason.RE_ANALYZE ->
                    ">  **本次为新分析**（数据未变化）：以下结论将覆盖 "
                            + reportNo + " 的上一条档案。\n\n";
            default -> {
                log.warn("[Header] 未知的覆盖原因：{}，使用通用提示行", changeReason);
                yield ">  **本次为新分析**：以下结论将覆盖 " + reportNo + " 的上一条档案。\n\n";
            }
        };
        return head + raw;
    }

    // 双键落库
    /*
    * 第一层: 会话事件流 - 原始回答,提供会话上下文 + 审计可追溯
    * 第二层: 报告结论档案 - 蒸馏结论,供团队共享
    * 第一层需要chatId.第二层需要reportNo,不能用统一的 early return判断,两个必须要独立的if
    *
    * 这里和旧 saveMemory等价,为了方便先入档后拼提示行,所以只拆分
    * */
    private void saveConversation(Long chatId, Long userId, String question,
                                  String answer, String reportNo) {
        //第一层:会话事件流(原始问答,供会话上下文 + 审计可追溯)
        if (userId != null && chatId != null) {
            mongoChatMessageService.saveUserMessage(chatId, userId, question, reportNo);
            mongoChatMessageService.saveAiMessage(chatId, userId, answer, reportNo);
        }
        //第二层已在 archive() 里完成
    }

    //第二层 结论入档
    private String archive(String reportNo, String answer, Long userId, String dataVersion) {
        return (reportNo == null) ? null
                : reportMemoryService.saveConclusion(reportNo, answer, userId, dataVersion);
    }

    private Long parseLong(String s) {
        return (s == null || s.isBlank()) ? null : Long.parseLong(s);
    }
}