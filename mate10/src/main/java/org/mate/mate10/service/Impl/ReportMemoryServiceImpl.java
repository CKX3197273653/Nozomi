package org.mate.mate10.service.Impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mate.mate10.document.ReportMemoryDocument;
import org.mate.mate10.repository.ReportMemoryRepository;
import org.mate.mate10.common.MemoryChangeReason;
import org.mate.mate10.service.ReportMemoryService;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
    public String saveConclusion(String reportNo, String answer, Long userId, String dataVersion) {
        //不是报告分析不入档
        String conclusion = extractSection(answer, S_CONCLUSION);
        if (conclusion == null || conclusion.isBlank()) {
            log.info("[Memory] 无「{}」段，不入档 reportNo={}", S_CONCLUSION, reportNo);
            return null;
        }
        //旧的有效结论全标记,superseded留痕
        List<ReportMemoryDocument> old = repository.findByReportNoAndSupersededFalse(reportNo);
        //查询不保证循序,显示取最近一条,不依赖列表位置,索引万一被删也不会静默判断错误
        String prevVersion = old.stream()
                .max(Comparator.comparing(ReportMemoryDocument::getAnalyzedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .map(ReportMemoryDocument::getDataVersion)
                .orElse(null);
        //覆盖原因(审计) 数据没变就是重新分析,变了才是数据已更新
        String changeReason = old.isEmpty()
                ? MemoryChangeReason.FIRST
                : Objects.equals(prevVersion, dataVersion)
                  ? MemoryChangeReason.RE_ANALYZE
                  : MemoryChangeReason.DATA_UPDATED;

        if (!old.isEmpty()) {
            old.forEach(d -> d.setSuperseded(true));
            repository.saveAll(old);
        }

        //写入新结论
        ReportMemoryDocument doc = new ReportMemoryDocument();
        doc.setReportNo(reportNo);
        doc.setConclusion(conclusion);
        doc.setAdvice(extractSection(answer, S_ADVICE));
        doc.setFullAnswer(answer);
        doc.setStatus("AI_DRAFT");
        doc.setSuperseded(false);
        doc.setAnalyzedBy(userId);
        doc.setAnalyzedAt(LocalDateTime.now());
        doc.setDataVersion(dataVersion);
        doc.setChangeReason(changeReason);


        try {
            repository.save(doc);
        } catch (DuplicateKeyException e) {
            //另一请求已经写入有效档案,本次自动放弃,不重复写入
            //report_memory 的部分唯一索引 {report_no:1, superseded:false}
            log.warn("[Memory] reportNo={} 并发入档冲突（另一请求已写入有效档案），本次放弃。"
                    + "旧版 {} 条已标记 superseded，数据仍自洽", reportNo, old.size());
            return null;
        }

        log.info("[Memory] 结论入档 reportNo={}（覆盖旧版 {} 条，原因：{}），结论 {} 字符",
                reportNo, old.size(), changeReason, conclusion.length());
        return changeReason;
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
