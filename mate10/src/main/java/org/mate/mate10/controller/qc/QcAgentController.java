package org.mate.mate10.controller.qc;

import lombok.extern.slf4j.Slf4j;
import org.mate.mate10.agent.QcAnalysisAgent;
import org.mate.mate10.common.Result;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
public class QcAgentController {
    private final QcAnalysisAgent qcAnalysisAgent;

    public QcAgentController(QcAnalysisAgent qcAnalysisAgent) {
        this.qcAnalysisAgent = qcAnalysisAgent;
    }

    @PostMapping("/ai/qc-agent")
    public Result<String> analyze(@RequestBody Map<String, String> body) {
        String question = body.getOrDefault("question", "");
        if (question.isBlank()) {
            return Result.error("question 不能为空");
        }
        long start = System.currentTimeMillis();
        try {
            String answer = qcAnalysisAgent.analyze(question);
            log.info("Agent 分析完成，耗时 {} ms", System.currentTimeMillis() - start);
            return Result.success(answer);
        } catch (Exception e) {
            log.error("Agent 分析失败", e);
            return Result.error("Agent 分析失败：" + e.getMessage());
        }
    }
}
