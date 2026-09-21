package org.mate.mate10.mapper.qc;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.mate.mate10.entity.qc.QcReport;

import java.util.List;
import java.util.Map;

@Mapper
public interface QcReportMapper extends BaseMapper<QcReport> {
    @Select("Select * FROM qc_report Where product_name = #{productName} ORDER BY inspect_time DESC")
    List<QcReport> selectByProductName(String productName);

    @Select("SELECT * FROM qc_report WHERE status = #{status} ORDER BY create_time DESC")
    List<QcReport> selectByStatus(String status);
    //时间趋势统计
    @Select("SELECT DATE(r.inspect_time) as date, " +
            "COUNT(DISTINCT r.id) as totalCount, " +
            "COUNT(d.id) as defectCount " +
            "FROM qc_report r " +
            "LEFT JOIN qc_defect d ON r.id = d.report_id " +
            "WHERE r.inspect_time >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY) " +
            "GROUP BY DATE(r.inspect_time) " +
            "ORDER BY date ASC")
    List<Map<String, Object>> countByTimeTrend(@Param("days") int days);

    //产品线缺陷统计
    @Select("SELECT r.production_line, " +
            "COUNT(DISTINCT r.id) as totalCount, " +
            "COUNT(d.id) as defectCount " +
            "FROM qc_report r " +
            "LEFT JOIN qc_defect d ON r.id = d.report_id " +
            "GROUP BY r.production_line " +
            "ORDER BY totalCount DESC")
    List<Map<String, Object>> countByProductionLine();
    //产品批次缺陷统计
    @Select("SELECT r.product_batch, " +
            "MAX(r.production_line) AS production_line, " +
            "COUNT(DISTINCT r.id) AS totalCount, " +
            "COUNT(d.id) AS count, " +
            "ROUND(100.0 * COUNT(DISTINCT CASE WHEN r.total_defects = 0 THEN r.id END) " +
            "  / COUNT(DISTINCT r.id), 1) AS passRate " +
            "FROM qc_report r " +
            "LEFT JOIN qc_defect d ON r.id = d.report_id " +
            "GROUP BY r.product_batch " +
            "ORDER BY count DESC " +
            "LIMIT #{limit}")
    List<Map<String, Object>> countByProductBatch(@Param("limit") int limit);

    @Select("SELECT * FROM qc_report WHERE report_no = #{reportNo} LIMIT 1")
    QcReport selectByReportNo(@Param("reportNo") String reportNo);
}
