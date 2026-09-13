package org.mate.mate10.controller.qc;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.mate.mate10.common.Result;
import org.mate.mate10.entity.qc.QcReport;
import org.mate.mate10.service.qc.QcReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/qc/report")
public class QcReportController {
    @Autowired
    private QcReportService qcReportService;

    //创建报告
    @PostMapping("/create")
    public Result<Long> createReport(@RequestBody QcReport report) {
        //校验
        if (report.getReportNo() == null || report.getReportNo().isEmpty()) {
            return Result.error("编号不能空");
        }
        if (report.getProductName() == null || report.getProductName().isEmpty()) {
            return Result.error("产品名称不能空");
        }
        //默认值
        if (report.getStatus() == null){
            report.setStatus("PENDING");
        }
        if (report.getInspectTime() == null){
            report.setInspectTime(LocalDateTime.now());
        }

        Long reportId = qcReportService.createReport(report);
        return Result.success(reportId);
    }

    //分页查询
    @GetMapping("/page")
    public Result<Page<QcReport>> pageList(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String reportNo,
            @RequestParam(required = false) String productName,
            @RequestParam(required = false) String productBatch,
            @RequestParam(required = false) String productionLine,
            @RequestParam(required = false) String status) {
        Page<QcReport> page = qcReportService.pageList(pageNum, pageSize,
                reportNo, productName, productBatch, productionLine, status);
        return Result.success(page);
    }

    //查询详细报告
    @GetMapping("/{id}")
    public Result<QcReport> getById(@PathVariable Long id) {
        QcReport report = qcReportService.getById(id);
        if (report != null) {
            return Result.success(report);
        }
        return Result.error("报告不存在");
    }

    //文件上传
    @PostMapping("/upload")
    public Result<Long> uploadReport(
            @RequestParam("file") MultipartFile file,
            @RequestParam String reportNo,
            @RequestParam String productName,
            @RequestParam String productBatch,
            @RequestParam String productionLine,
            @RequestParam String inspector,
            @RequestParam(value = "prompt",required = false) String prompt){
        try{
            //校验
            if (file.isEmpty()){
                return Result.error("文件不能空");
            }
            //获取文件名称和判断类型
            String originalFilename = file.getOriginalFilename();
            String fileType = getFileType(originalFilename);
            if (fileType == null) {
                return Result.error("不支持的文件类型，仅支持 PDF、图片、Word、Excel");
            }
            //保存到本地
            String fileUrl = saveFile(file);
            //创建报告记录
            QcReport report = new QcReport();
            report.setReportNo(reportNo);
            report.setProductName(productName);
            report.setProductBatch(productBatch);
            report.setProductionLine(productionLine);
            report.setInspector(inspector);
            report.setFileUrl(fileUrl);
            report.setFileType(fileType);
            report.setStatus("PENDING");
            report.setInspectTime(LocalDateTime.now());
            //调用Service保存
            Long reportId = qcReportService.createReport(report);
            qcReportService.sendAnalyzeTask(reportId, report.getFileUrl(), report.getFileType(),prompt);
            return Result.success(reportId);
        } catch (IOException e) {
            return Result.error("文件上传失败：" + e.getMessage());
        }
    }
        //更新报告
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id,
    @RequestParam String status) {
        boolean success = qcReportService.updateStatus(id, status);
        if (!success) {
        return Result.error("更新状态失败");
        }
        return Result.success();
    }
    //删除报告
    @DeleteMapping("/{id}")
    public Result<Void> deleteReport(@PathVariable Long id) {
        boolean success = qcReportService.deleteReport(id);
            if (!success) {
                return Result.error("删除失败，报告可能不存在");
            }
        return Result.success();
    }
    //私有方法
    //根据文件名称判断类型
    private static final Map<String, String> FILE_TYPE_MAP;

    // 静态代码块初始化 Map
    static {
        Map<String, String> map = new java.util.HashMap<>();
        // PDF
        map.put(".pdf", "PDF");
        // 图片
        map.put(".jpg", "IMAGE");
        map.put(".jpeg", "IMAGE");
        map.put(".png", "IMAGE");
        map.put(".gif", "IMAGE");
        map.put(".bmp", "IMAGE");
        // Word 文档
        map.put(".doc", "WORD");
        map.put(".docx", "WORD");
        // Excel 表格
        map.put(".xls", "EXCEL");
        map.put(".xlsx", "EXCEL");
        // 赋值给常量
        FILE_TYPE_MAP = java.util.Collections.unmodifiableMap(map);
    }
    private String getFileType(String filename) {
        if (filename == null || !filename.contains(".")) {
            return null;
        }
        String suffix = filename.substring(filename.lastIndexOf(".")).toLowerCase();
        return FILE_TYPE_MAP.get(suffix);
    }
        //保存到本地
        private String saveFile(MultipartFile file) throws IOException {
            // 获取原始文件名
            String originalFilename = file.getOriginalFilename();
            // 获取文件后缀
            String suffix = originalFilename.substring(originalFilename.lastIndexOf("."));
            // 生成唯一文件名（时间戳+后缀）
            String newFilename = System.currentTimeMillis() + suffix;
            // 创建保存目录
            String uploadDir = "uploads/qc/";
            java.io.File dir = new java.io.File(uploadDir);
            if (!dir.exists()) {
                dir.mkdirs();}
            // 写入文件
            Path path = Paths.get(uploadDir + newFilename);
            Files.copy(file.getInputStream(), path, StandardCopyOption.REPLACE_EXISTING);
            // 返回文件路径
            return uploadDir + newFilename;
    }

    @GetMapping("/stats/trend")
    public Result<List<Map<String, Object>>> statsByTrend(
            @RequestParam(value = "days", defaultValue = "7") int days) {
        List<Map<String, Object>> result = qcReportService.countByTimeTrend(days);
        return Result.success(result);
    }
    @GetMapping("/stats/production-line")
    public Result<List<Map<String, Object>>> statsByProductionLine() {
        List<Map<String, Object>> result = qcReportService.countByProductionLine();
        return Result.success(result);
    }
    @GetMapping("/stats/product-batch")
    public Result<List<Map<String, Object>>> statsByProductBatch(
            @RequestParam(value = "limit", defaultValue = "10") int limit) {
        List<Map<String, Object>> result = qcReportService.countByProductBatch(limit);
        return Result.success(result);
    }
    //根因分析
    @GetMapping("/{id}/root-cause")
    public Result<Map<String, Object>> analyzeRootCause(@PathVariable Long id) {
        Map<String, Object> result = qcReportService.analyzeRootCause(id);
        if (result.containsKey("error")) {
            return Result.error((String) result.get("error"));
        }
        return Result.success(result);
    }
}
