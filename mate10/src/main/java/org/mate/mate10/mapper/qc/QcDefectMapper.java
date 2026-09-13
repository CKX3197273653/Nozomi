package org.mate.mate10.mapper.qc;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.mate.mate10.entity.qc.QcDefect;

import java.util.List;
import java.util.Map;

@Mapper
public interface QcDefectMapper extends BaseMapper<QcDefect> {
    @Select("SELECT * FROM qc_defect WHERE report_id = #{reportId} ORDER BY severity DESC")
    List<QcDefect> selectByReportId(Long reportId);

    @Select("SELECT defect_type, COUNT(*) as defectCount FROM qc_defect GROUP BY defect_type ORDER BY defectCount DESC")
    List<Map<String, Object>> countByDefectType();
    @Select("SELECT severity, COUNT(*) as count FROM qc_defect GROUP BY severity ORDER BY count DESC")
    List<Map<String, Object>> countBySeverity();

    @Insert("<script>" +
            "INSERT INTO qc_defect (report_id, defect_type, defect_position, severity, description, confidence, is_confirmed) VALUES " +
            "<foreach collection='list' item='item' separator=','>" +
            "(#{item.reportId}, #{item.defectType}, #{item.defectPosition}, #{item.severity}, #{item.description}, #{item.confidence}, #{item.isConfirmed})" +
            "</foreach>" +
            "</script>")
    int batchInsert(@Param("list") List<QcDefect> list);

    // 按 批次号/生产线 模糊查询缺陷（关联报告表）
    @Select("<script>" +
            "SELECT d.* FROM qc_defect d " +
            "JOIN qc_report r ON d.report_id = r.id " +
            "WHERE 1=1 " +
            "<if test='productBatch != null and productBatch != \"\"'>" +
            "  AND r.product_batch LIKE CONCAT('%', #{productBatch}, '%')" +
            "</if>" +
            "<if test='productionLine != null and productionLine != \"\"'>" +
            "  AND r.production_line LIKE CONCAT('%', #{productionLine}, '%')" +
            "</if>" +
            "ORDER BY d.create_time DESC" +
            "</script>")
    List<QcDefect> selectByBatchAndLine(@Param("productBatch") String productBatch,
                                        @Param("productionLine") String productionLine);

}
