package org.mate.mate10.service;

public interface ReportDataFingerprintService {
    //计算报告的当前数据指纹 -- 影响结论的数据的规范化摘要
    String fingerprint(String reportNo);
}
