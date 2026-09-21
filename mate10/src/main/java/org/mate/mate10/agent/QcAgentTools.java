package org.mate.mate10.agent;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.mate.mate10.entity.qc.QcDefect;
import org.mate.mate10.entity.qc.QcReport;
import org.mate.mate10.service.RagService;
import org.mate.mate10.service.qc.QcDefectService;
import org.mate.mate10.service.qc.QcReportService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
//质检 Agent 工具及
public class QcAgentTools {
    private final QcReportService reportService;
    private final QcDefectService defectService;
    private final RagService ragService;

    public QcAgentTools(QcReportService reportService, QcDefectService defectService, RagService ragService) {
        this.reportService = reportService;
        this.defectService = defectService;
        this.ragService = ragService;
    }
    @Tool("""
        获取质检报告的基本信息。
        返回：报告编号、产品名称、产品批次、生产线、质检员、
             报告状态、缺陷总数、严重等级(NORMAL/WARNING/CRITICAL)。
        用途：分析某个报告时，建议先调用本工具了解背景。
        注意：reportId 必须是数字，若不知道 ID 请先向用户询问。
        """)
    public String getReport(@P("质检报告 ID，整数") Long reportId) {
        log.info("[Tool] getReport(reportId={})", reportId);
        QcReport r = reportService.getById(reportId);
        if (r == null) {
            return "未找到 ID 为 " + reportId + " 的质检报告。";
        }
        return """
            报告ID: %s
            报告编号: %s
            产品名称: %s
            产品批次: %s
            生产线: %s
            质检员: %s
            质检时间: %s
            状态: %s
            缺陷总数: %s
            严重等级: %s
            """.formatted(
                r.getId(), r.getReportNo(), r.getProductName(), r.getProductBatch(),
                r.getProductionLine(), r.getInspector(), r.getInspectTime(),
                r.getStatus(), r.getTotalDefects(), r.getSeverityLevel());
    }

    @Tool("""
        查询指定质检报告下的全部缺陷记录。
        返回每条缺陷的：类型、位置、严重程度(LOW/MEDIUM/HIGH/CRITICAL)、
        描述、AI置信度、是否已人工确认。
        用途：了解报告的缺陷构成，判断是否存在某种问题模式
             （例如集中在某个位置、某类缺陷占比异常高）。
        """)
    public String getDefects(@P("质检报告 ID，整数") Long reportId) {
        log.info("[Tool] getDefects(reportId={})", reportId);
        List<QcDefect> list = defectService.getByReportId(reportId);
        if (list == null || list.isEmpty()) {
            return "报告 " + reportId + " 下没有缺陷记录（可能是合格品）。";
        }
        return list.stream()
                .map(d -> "  - 类型=%s | 位置=%s | 严重=%s | 置信度=%s | 已确认=%s | 描述=%s"
                        .formatted(d.getDefectType(), d.getDefectPosition(), d.getSeverity(),
                                d.getConfidence(),
                                (d.getIsConfirmed() != null && d.getIsConfirmed() == 1 ? "是" : "否"),
                                d.getDescription()))
                .collect(Collectors.joining("\n", "共 " + list.size() + " 条缺陷：\n", ""));
    }

    @Tool("""
        检索质检知识库，获取质量标准、检验规范、缺陷处理建议等资料。
        用途：需要判断"该缺陷是否超标""应当如何处理"时调用。
        建议：用具体的问题检索，例如"尺寸偏差的允差范围"，
             不要用宽泛的"质检标准"。
        """)
    public String searchKnowledge(
            @P("检索问题，用自然语言描述") String question) {
        log.info("[Tool] searchKnowledge(question={})", question);
        String result = ragService.retrieve(question);
        return (result == null || result.isBlank())
                ? "知识库中没有检索到与该问题相关的内容。"
                : result;
    }


    @Tool("""
    按【报告编号】查询质检报告。
    报告编号是业务编号（形如 R20260808001），
    与数据库主键 ID（纯数字，如 15）不同。
    当用户提供的是带字母前缀的编号时，先用本工具换取数字 ID。
    返回：报告ID、产品名称、批次、生产线、状态、缺陷总数、严重等级。
    用途：拿到报告ID 后，再调用 getReport / getDefects 继续调查。
    """)
    public String getReportByNo(@P("报告编号，形如 R20260808001") String reportNo) {
        log.info("[Tool] getReportByNo(reportNo={})", reportNo);
        QcReport r = reportService.getByReportNo(reportNo);
        if (r == null) {
            return "未找到报告编号为 " + reportNo + " 的质检报告。"
                    + "请确认编号是否正确（形如 R20260808001），或改用数字 ID 查询。";
        }
        return """
                报告ID: %s      ← 后续请用这个数字 ID 调用 getReport / getDefects
                报告编号: %s
                产品名称: %s
                产品批次: %s
                生产线: %s
                质检员: %s
                状态: %s
                缺陷总数: %s
                严重等级: %s
                """.formatted(
                r.getId(), r.getReportNo(), r.getProductName(), r.getProductBatch(),
                r.getProductionLine(), r.getInspector(), r.getStatus(),
                r.getTotalDefects(), r.getSeverityLevel());
    }
}
