package org.mate.mate10.service.qc;

import org.mate.mate10.entity.qc.QcDefect;

import java.util.List;
import java.util.Map;

public interface QcDefectService {
    //增
    Long addDefect(QcDefect qcDefect);
    int batchAddDefects(List<QcDefect> qcDefects);

    //更
    boolean updateDefect(QcDefect qcDefect);

    //删
    boolean deleteDefect(Long id);

    //查
    List<QcDefect> getByReportId(Long reportId);

    //按缺陷类型统计
    List<Map<String,Object>> countByDefectType();
    //严重程度统计
    List<Map<String, Object>> countBySeverity();

    //人工确认缺陷
    boolean confirmDefect(Long id);
    //3D可视化数据
    Map<String, Object> getDefects3DForECharts(Long reportId);

    //模糊查
    List<QcDefect> getByBatchAndLine(String productBatch, String productionLine);

}
