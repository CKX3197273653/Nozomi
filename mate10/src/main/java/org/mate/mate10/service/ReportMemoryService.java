package org.mate.mate10.service;

import org.mate.mate10.document.ReportMemoryDocument;

import java.util.List;

public interface ReportMemoryService {
    //从Agent回答中蒸馏结论并覆盖,非报告分析自动忽略
    void saveConclusion(String reportNo, String answer, Long userId);

    //取报告的当前有效结论,没有返回null
    ReportMemoryDocument getCurrent(String reportNo);

    //批量取当前有效结论(给 ChatContextBuilder用)
    List<ReportMemoryDocument> getCurrentConclusions(List<String> reportNos);
}
