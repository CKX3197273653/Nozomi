package org.mate.mate10.controller.qc;

import org.mate.mate10.common.Result;
import org.mate.mate10.entity.qc.QcDefect;
import org.mate.mate10.service.qc.QcDefectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/qc/defect")
public class QcDefectController {
    @Autowired
    private QcDefectService qcDefectService;

    //增删改查
    @PostMapping("/create")
    public Result<Long> createDefect(@RequestBody QcDefect defect){
        if (defect.getReportId()==null){
            return Result.error("报告Id不能为空");
        }
        if (defect.getDefectType()==null || defect.getDefectType().isEmpty()){
            return Result.error("缺陷类型不能为空");
        }
        Long defectId = qcDefectService.addDefect(defect);
        return Result.success(defectId);
    }
    @PutMapping("/update")
    public Result<Long> updateDefect(@RequestBody QcDefect defect){
        if (defect.getId()==null){
            return Result.error("参数id不能为空");
        }
        boolean success = qcDefectService.updateDefect(defect);
        if (!success){
            return Result.error("更新失败");
        }
        return Result.success(defect.getId());
    }
    @DeleteMapping("/{id}")
    public Result<Void> deleteDefect(@PathVariable() Long id){
        boolean success = qcDefectService.deleteDefect(id);
        if (!success){
            return Result.error("删除失败");
        }
        return Result.success();
    }
    @GetMapping("/report/{reportId}")
    public Result<List<QcDefect>> getByReportId(@PathVariable() Long reportId){
        List<QcDefect> defects = qcDefectService.getByReportId(reportId);
        return Result.success(defects);
    }
    //统计分析=>echarts
    @GetMapping("/stats/type")
    public Result<List<Map<String, Object>>> statsByType() {
        List<Map<String, Object>> result = qcDefectService.countByDefectType();
        return Result.success(result);
    }
    //分层统计
    @GetMapping("/stats/severity")
    public Result<List<Map<String,Object>>> statsBySeverity() {
        List<Map<String,Object>> result = qcDefectService.countBySeverity();
        return Result.success(result);
    }

    //人工确认
    @PutMapping("/{id}/confirm")
    public Result<Void> confirmDefect(@PathVariable() Long id){
        boolean success = qcDefectService.confirmDefect(id);
        if (!success){
            return  Result.error("确认失败");
        }
        return Result.success();
    }

    //3D可视化（ECharts格式）
    @GetMapping("/3d/{reportId}")
    public Result<Map<String, Object>> getDefects3D(@PathVariable Long reportId) {
        Map<String, Object> result = qcDefectService.getDefects3DForECharts(reportId);
        return Result.success(result);
    }
    @GetMapping("/search")
    public Result<List<QcDefect>> search(
            @RequestParam(required = false) String productBatch,
            @RequestParam(required = false) String productionLine){
        if ((productBatch == null || productBatch.trim().isEmpty())
                && (productionLine == null || productionLine.trim().isEmpty())) {
            return Result.error("请输入批次号或生产线");
        }
        return Result.success(qcDefectService.getByBatchAndLine(
                productBatch == null ? null : productBatch.trim(),
                productionLine == null ? null : productionLine.trim()));
    }
}
