package org.mate.mate10.service.Impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mate.mate10.entity.qc.QcDefect;
import org.mate.mate10.entity.qc.QcProductionParam;
import org.mate.mate10.entity.qc.QcReport;
import org.mate.mate10.service.ReportDataFingerprintService;
import org.mate.mate10.service.qc.QcDefectService;
import org.mate.mate10.service.qc.QcProductionParamService;
import org.mate.mate10.service.qc.QcReportService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReportDataFingerprintServiceImpl  implements ReportDataFingerprintService {

    private final QcReportService reportService;
    private final QcDefectService defectService;
    private final QcProductionParamService paramService;

    @Override
    public String fingerprint(String reportNo) {
        QcReport report = reportService.getByReportNo(reportNo);
        if (report == null) {
            return null;
        }
        String summary = buildSummary(report);
        String hash = sha256(summary);
        log.info("[Fingerprint] reportNo={} → {}", reportNo, hash);
        log.debug("[Fingerprint] 摘要：{}", summary);   // 排查用：能看出「哪里变了」
        return hash;
    }

    //拼规范化摘要,同一份数据的字符串必须一致
    //排序,null统一为"-"+数值规范 (BigDecimal)
    String buildSummary(QcReport r) {
        StringBuilder sb = new StringBuilder();

        //报告本身（不含 createTime / updateTime / fileUrl / fileType）
        sb.append("report=").append(safe(r.getReportNo()))
                .append("|name=").append(safe(r.getProductName()))
                .append("|batch=").append(safe(r.getProductBatch()))
                .append("|line=").append(safe(r.getProductionLine()))
                .append("|inspector=").append(safe(r.getInspector()))
                .append("|inspectTime=").append(safe(r.getInspectTime()))
                .append("|status=").append(safe(r.getStatus()))
                .append("|total=").append(safe(r.getTotalDefects()))
                .append("|severity=").append(safe(r.getSeverityLevel()));

        //必须按id排序.查询顺序变化不能让指纹假变
        List<QcDefect> defects = defectService.getByReportId(r.getId());
        sb.append("|defects=").append(defects == null ? 0 : defects.size()).append('[');
        if (defects != null) {
            defects.stream()
                    .sorted(Comparator.comparing(QcDefect::getId,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .forEach(d -> sb.append(safe(d.getDefectType())).append(',')
                            .append(safe(d.getDefectPosition())).append(',')
                            .append(safe(d.getSeverity())).append(',')
                            .append(safe(d.getConfidence())).append(',')
                            .append(safe(d.getIsConfirmed())).append(';'));
        }
        sb.append(']');
        //生产参数(一个报告一条)
        QcProductionParam p = paramService.getByReportId(r.getId());
        sb.append("|params=");
        if (p == null) {
            sb.append("none");
        } else {
            sb.append(safe(p.getTemperature())).append(',')
                    .append(safe(p.getPressure())).append(',')
                    .append(safe(p.getHumidity())).append(',')
                    .append(safe(p.getSpeed())).append(',')
                    .append(safe(p.getOperator())).append(',')
                    .append(safe(p.getRemark()));
        }
        return sb.toString();
    }
    //统一的null,数值规范化
    static String safe(Object v){
        if(v == null){
            return "-";
        }
        if (v instanceof BigDecimal bd){
            return bd.stripTrailingZeros().toPlainString();
        }
        return v.toString();
    }
    //取 SHA-256前八字节 == 16位hex,足够短了,而且碰撞概率不如买彩票
    static String sha256(String s){
        try{
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i=0; i < 8; i++) {
                hex.append(String.format("%02x", digest[i]));
            }
            return hex.toString();
        }catch (NoSuchAlgorithmException e){
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
