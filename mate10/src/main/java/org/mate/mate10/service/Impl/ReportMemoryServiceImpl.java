package org.mate.mate10.service.Impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mate.mate10.document.ReportMemoryDocument;
import org.mate.mate10.repository.ReportMemoryRepository;
import org.mate.mate10.service.ReportMemoryService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReportMemoryServiceImpl implements ReportMemoryService {
    private static final String S_CONCLUSION = "根因判断";
    private static final String S_ADVICE = "改进建议";

    private final ReportMemoryRepository repository;

    @Override
    public void saveConclusion(String reportNo, String answer, Long userId) {
        //不是报告分析不入档
        String conclusion = extractSection(answer, S_CONCLUSION);
        if (conclusion == null || conclusion.isBlank()) {
            log.info("[Memory] 无「{}」段，不入档 reportNo={}", S_CONCLUSION, reportNo);
            return;
        }
        //旧的有效结论全标记,superseded留痕
        List<ReportMemoryDocument> old = repository.findByReportNoAndSupersededFalse(reportNo);
        if (!old.isEmpty()) {
            old.forEach(d -> d.setSuperseded(true));
            repository.saveAll(old);
        }

        //写入新结论
        ReportMemoryDocument doc = new ReportMemoryDocument();
        doc.setReportNo(reportNo);
        doc.setConclusion(conclusion);
        doc.setAdvice(extractSection(answer, S_ADVICE));
        doc.setStatus("AI_DRAFT");
        doc.setSuperseded(false);
        doc.setAnalyzedBy(userId);
        doc.setAnalyzedAt(LocalDateTime.now());
        repository.save(doc);

        log.info("[Memory] 结论入档 reportNo={}（覆盖旧版 {} 条），结论 {} 字符",
                reportNo, old.size(), conclusion.length());
    }

    @Override
    public ReportMemoryDocument getCurrent(String reportNo) {
        return repository.findFirstByReportNoAndSupersededFalseOrderByAnalyzedAtDesc(reportNo);
    }

    @Override
    public List<ReportMemoryDocument> getCurrentConclusions(List<String> reportNos) {
        List<ReportMemoryDocument> result = new ArrayList<>();
        if (reportNos == null || reportNos.isEmpty()) {
            return result;
        }
        for (String no : reportNos) {
            ReportMemoryDocument d = getCurrent(no);
            if (d != null) {
                result.add(d);
            }
        }
        return result;
    }

    //切出 Markdown中"## <heading>" 到下一个标题之间的内容
    //{@code QcAnalysisAgent}的SystemMessage已经强制了,根因判断,改进建议,判断依据,信息缺口四个固定结构
    private String extractSection(String answer, String section) {
        if (answer == null) {
            return null;
        }
        Pattern p = Pattern.compile(
                "^#{1,6}\\s*" + Pattern.quote(section) + "[^\\n]*\\n+(.*?)(?=^#{1,6}\\s|\\z)",
                Pattern.DOTALL | Pattern.MULTILINE);
        Matcher m = p.matcher(answer);
        return m.find() ? m.group(1).trim() : null;
    }

}
