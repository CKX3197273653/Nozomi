package org.mate.mate10.service.Impl.qc;

import org.mate.mate10.entity.qc.QcDefect;
import org.mate.mate10.mapper.qc.QcDefectMapper;
import org.mate.mate10.service.qc.QcDefectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class QcDefectServiceImpl implements QcDefectService {
    @Autowired
    private QcDefectMapper qcDefectMapper;

    @Override
    public Long addDefect(QcDefect defect) {
        qcDefectMapper.insert(defect);
        return defect.getId();
    }
    @Override
    public int batchAddDefects(List<QcDefect> defects) {
        if (defects == null || defects.isEmpty()){
            return 0;
        }
        return qcDefectMapper.batchInsert(defects);
    }

    @Override
    public boolean updateDefect(QcDefect defect) {
        return qcDefectMapper.updateById(defect) > 0;
    }

    @Override
    public boolean deleteDefect(Long id) {
        return qcDefectMapper.deleteById(id) > 0;
    }
    @Override
    public List<QcDefect> getByReportId(Long reportId) {
        return qcDefectMapper.selectByReportId(reportId);
    }

    @Override
    public List<Map<String, Object>> countByDefectType() {
        return qcDefectMapper.countByDefectType();
    }

    @Override
    public List<Map<String, Object>> countBySeverity() {
        return qcDefectMapper.countBySeverity();
    }

    @Override
    public boolean confirmDefect(Long id) {
        QcDefect defect = new QcDefect();
        defect.setId(id);
        defect.setIsConfirmed(1);
        return qcDefectMapper.updateById(defect) > 0;
    }

    @Override
    public Map<String, Object> getDefects3DForECharts(Long reportId) {
        //  查询缺陷
        List<QcDefect> defects = qcDefectMapper.selectByReportId(reportId);

        // 转换为 ECharts 格式
        List<Map<String, Object>> defectPoints = new java.util.ArrayList<>();

        for (QcDefect defect : defects) {
            Map<String, Object> point = new java.util.HashMap<>();
            point.put("name", defect.getDefectType());
            point.put("value", mapPositionToXYZ(defect.getDefectPosition()));
            point.put("itemStyle", Map.of("color", getSeverityColor(defect.getSeverity())));
            defectPoints.add(point);
        }

        //  组装返回
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("defectPoints", defectPoints);
        return result;
    }

    @Override
    public List<QcDefect> getByBatchAndLine(String productBatch, String productionLine) {
        return qcDefectMapper.selectByBatchAndLine(productBatch, productionLine);
    }

    //文字转3D坐标数据
    private double[] mapPositionToXYZ(String position) {
        if (position == null || position.isEmpty()) {
            return new double[]{0.5, 0.5, 0.5};
        }

        String pos = position.toLowerCase();
        double x = 0.5, y = 0.5, z = 0.5;

        if (pos.contains("左")) x = 0.2;
        else if (pos.contains("右")) x = 0.8;

        if (pos.contains("上") || pos.contains("顶")) y = 0.9;
        else if (pos.contains("下") || pos.contains("底")) y = 0.1;

        if (pos.contains("前")) z = 0.8;
        else if (pos.contains("后")) z = 0.2;

        return new double[]{x, y, z};
    }
    //程度转颜色
    private String getSeverityColor(String severity) {
        if (severity == null) return "#FFCC00";
        switch (severity.toUpperCase()) {
            case "CRITICAL": return "#FF0000";
            case "HIGH":     return "#FF6600";
            case "MEDIUM":   return "#FFCC00";
            case "LOW":      return "#00CC00";
            default:         return "#FFCC00";
        }
    }


}
