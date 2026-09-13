package org.mate.mate10.controller.qc;

import org.mate.mate10.common.Result;
import org.mate.mate10.entity.qc.QcProductionParam;
import org.mate.mate10.service.qc.QcProductionParamService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/qc/param")
public class QcProductionParamController {
    @Autowired
    private QcProductionParamService qcProductionParamService;

    //生产参数
    @PostMapping("/create")
    public Result<Long> createParam(@RequestBody QcProductionParam param){
        if (param.getReportId()==null){
            return Result.error("报告id不能空");
        }
        Long paramId = qcProductionParamService.addParam(param);
        return Result.success(paramId);
    }
    //更新生产参数
    @PutMapping("/update")
    public Result<Void> updateParam(@RequestBody QcProductionParam param){
        if (param.getId()==null){
            return Result.error("报告id不能空");
        }
        boolean success = qcProductionParamService.updateParam(param);
        if (!success){
            return Result.error("更新失败");
        }
        return Result.success();
    }
    //根据ID查生产参数
    @GetMapping("/report/{reportId}")
    public Result<QcProductionParam> getByReportId(@PathVariable Long reportId) {
        QcProductionParam param = qcProductionParamService.getByReportId(reportId);
        if (param == null) {
            return Result.error("未找到生产参数");
        }
        return Result.success(param);
    }
}
