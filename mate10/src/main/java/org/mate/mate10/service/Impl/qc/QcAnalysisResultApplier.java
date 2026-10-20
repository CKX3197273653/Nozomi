package org.mate.mate10.service.Impl.qc;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mate.mate10.common.QcSeverityUtil;
import org.mate.mate10.dto.AiAnalysisResult;
import org.mate.mate10.entity.qc.QcReport;
import org.mate.mate10.mapper.qc.QcReportMapper;
import org.mate.mate10.service.qc.QcDefectService;
import org.mate.mate10.service.qc.QcProductionParamService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class QcAnalysisResultApplier {

    private final QcDefectService qcDefectService;
    private final QcProductionParamService qcProductionParamService;
    private final QcReportMapper qcReportMapper;
    
    @Transactional(rollbackFor = Exception.class)
    public int apply(Long reportId, AiAnalysisResult result) {
        //先删旧的再写新的,让消息重放不翻倍
        int removed = qcDefectService.deleteByReportId(reportId);
        if (removed > 0) {
            log.info("[Apply] 清理旧缺陷 {} 条 reportId={}", removed, reportId);
        }
        if (result.getDefects() != null && !result.getDefects().isEmpty()) {
            qcDefectService.batchAddDefects(result.getDefects());
        }

        //    生产参数只有 AI 抽到才替换,抽不到就保留原数据避免"重跑一次 AI"把人工补录的参数清空
        if (result.hasParam()) {
            int rp = qcProductionParamService.deleteByReportId(reportId);
            if (rp > 0) {
                log.info("[Apply] 清理旧参数 {} 条 reportId={}", rp, reportId);
            }
            qcProductionParamService.addParam(result.getParam());
            log.info("[Apply] 生产参数已写入 reportId={}", reportId);
        } else {
            log.warn("[Apply] AI 未抽取到生产参数，保留原数据 reportId={}", reportId);
        }

        //统计用库里的真实条数，不用"本批 size"
        int realCount = qcDefectService.countByReportId(reportId);
        QcReport patch = new QcReport();
        patch.setId(reportId);
        patch.setTotalDefects(realCount);
        patch.setSeverityLevel(QcSeverityUtil.from(result.getDefects()));
        qcReportMapper.updateById(patch);

        return realCount;
    }
}
