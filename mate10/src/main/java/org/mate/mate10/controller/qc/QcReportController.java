package org.mate.mate10.controller.qc;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.mate.mate10.common.ResultCode;
import org.springframework.security.access.prepost.PreAuthorize;
import org.mate.mate10.common.Result;
import org.mate.mate10.dto.ReportAuditDTO;
import org.mate.mate10.entity.qc.QcReport;
import org.mate.mate10.service.ReportAuditService;
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

@Slf4j
@RestController
@RequestMapping("/api/qc/report")
public class QcReportController {
    @Autowired
    private QcReportService qcReportService;
    @Autowired
    private ReportAuditService reportAuditService;
    @Autowired
    private org.mate.mate10.common.CurrentUserProvider currentUser;

    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024;

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{reportNo}/audit")
    public Result<ReportAuditDTO> audit(@PathVariable String reportNo) {
        if (reportNo == null || reportNo.isBlank()) {
            return Result.error(ResultCode.BAD_REQUEST,"reportNo 不能为空");
        }
        return Result.success(reportAuditService.audit(reportNo));
    }

    //创建报告
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/create")
    public Result<Long> createReport(@RequestBody QcReport report) {
        //校验
        if (report.getReportNo() == null || report.getReportNo().isEmpty()) {
            return Result.error(ResultCode.BAD_REQUEST, "编号不能空");
        }
        if (report.getProductName() == null || report.getProductName().isEmpty()) {
            return Result.error(ResultCode.BAD_REQUEST, "产品名称不能空");
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
        page.getRecords().forEach(this::fillFileExists);
        return Result.success(page);
    }

    //查询详细报告
    @GetMapping("/{id}")
    public Result<QcReport> getById(@PathVariable Long id) {
        QcReport report = qcReportService.getById(id);
        if (report != null) {
            fillFileExists(report);
            return Result.success(report);
        }
        return Result.error(ResultCode.NOT_FOUND, "报告不存在");
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
            //空文件
            if (file.isEmpty()){
                return Result.error(ResultCode.BAD_REQUEST, "文件不能空");
            }
            //大小
            if (file.getSize() > MAX_FILE_SIZE) {
                return Result.error(ResultCode.BAD_REQUEST,
                        "文件超过 " + (MAX_FILE_SIZE / 1024 / 1024) + "MB，请压缩后重试");
            }
            //获取文件名称和判断类型
            String originalFilename = file.getOriginalFilename();
            String fileType = getFileType(originalFilename);
            if (fileType == null) {
                return Result.error(ResultCode.BAD_REQUEST, "不支持的文件类型，仅支持 PDF、图片、Word、Excel");
            }
            if (!magicMatches(file, fileType)) {
                return Result.error(ResultCode.BAD_REQUEST,
                        "文件内容与扩展名不符（可能被改过后缀），请上传真实文件");
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
            //调用Service保存, 落库失败回滚到刚保存的附件, 文件系统不在事物中, 只能补偿
            Long reportId ;
            try{
                reportId = qcReportService.createReport(report);
            }catch(RuntimeException e){
                deleteUploadedFile(fileUrl);
                throw e;
            }
            qcReportService.sendAnalyzeTask(reportId, report.getFileUrl(), report.getFileType(),prompt);
            return Result.success(reportId);
        } catch (IOException e) {
            return Result.error(ResultCode.ERROR, "文件上传失败：" + e.getMessage());
        }
    }
        //更新报告
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id,
    @RequestParam String status) {
        boolean success = qcReportService.updateStatus(id, status);
        if (!success) {
            return Result.error(ResultCode.NOT_FOUND, "更新状态失败，报告可能不存在");
        }
        return Result.success();
    }
    //删除报告
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public Result<Void> deleteReport(@PathVariable Long id) {
        Long operatorId = currentUser.userIdOrNull();
        boolean success = qcReportService.deleteReport(id,operatorId);
            if (!success) {
                return Result.error(ResultCode.NOT_FOUND, "删除失败，报告可能不存在");
            }
        return Result.success();
    }

    //取消归档
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}/unarchive")
    public Result<Void> unarchiveReport(@PathVariable Long id) {
        Long operatorId = currentUser.userIdOrNull();
        boolean success = qcReportService.unarchiveReport(id,operatorId);
        if (!success){
            return Result.error(ResultCode.NOT_FOUND,"取消归档失败,报告不存在或未归档");
        }
        return  Result.success();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}/purge")
    public Result<Void> purgeReport(@PathVariable Long id) {
        boolean ok = qcReportService.purgeReport(id);
        if (!ok) {
            return Result.error(ResultCode.NOT_FOUND, "彻底删除失败，报告不存在");
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
            try (java.io.InputStream in = file.getInputStream()) {
                Files.copy(in, path, StandardCopyOption.REPLACE_EXISTING);
            }
            // 返回文件路径
            return uploadDir + newFilename;
    }
    private void deleteUploadedFile(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) return;
        try{
            Path base = Paths.get("uploads").toAbsolutePath().normalize();
            Path f = Paths.get(fileUrl).toAbsolutePath().normalize();
            if (!f.startsWith(base)) {
                log.warn("[Upload] 附件不在 uploads/ 下, 跳过清理:{}",f);
                return;
            }
            boolean deleted = Files.deleteIfExists(f);
            log.warn("[Upload] 落库失败, 已回滚附件 {} -> {}",f,deleted ? "已删除" : "文件不存在");
        }catch (Exception e){
            //清理失败不掩盖原始异常,只记日志
            log.warn("[Upload] 回滚附件失败: {} , {}",fileUrl,e.getMessage());
        }
    }
    private boolean magicMatches(MultipartFile file, String fileType) {
        byte[] head;
        try (java.io.InputStream in = file.getInputStream()) {
            head = in.readNBytes(8);
        } catch (IOException e) {
            log.warn("[Upload] 读取文件头失败: {}", e.getMessage());
            return false;
        }
        if (head.length < 4) {
            return false;
        }
        return switch (fileType) {
            case "PDF" -> head[0] == 0x25 && head[1] == 0x50 && head[2] == 0x44 && head[3] == 0x46;
            case "WORD", "EXCEL" -> isZip(head) || isOle(head);
            case "IMAGE" -> isPng(head) || isJpeg(head) || isGif(head) || isBmp(head);
            default -> false;
        };
    }
    private boolean isPng(byte[] h) {
        return (h[0] & 0xFF) == 0x89 && h[1] == 0x50 && h[2] == 0x4E && h[3] == 0x47;
    }
    private boolean isJpeg(byte[] h) {
        return (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF;
    }
    private boolean isGif(byte[] h) {
        return h[0] == 0x47 && h[1] == 0x49 && h[2] == 0x46 && h[3] == 0x38;
    }
    private boolean isBmp(byte[] h) {
        return h[0] == 0x42 && h[1] == 0x4D;
    }
    // .docx/.xlsx 或 .doc/.xls
    private boolean isZip(byte[] h) {
        return h[0] == 0x50 && h[1] == 0x4B
                && (h[2] == 0x03 || h[2] == 0x05 || h[2] == 0x07)
                && (h[3] == 0x04 || h[3] == 0x06 || h[3] == 0x08);
    }
    //老版 .doc / .xls 是 OLE 复合文档: D0 CF 11 E0
    private boolean isOle(byte[] h) {
        return (h[0] & 0xFF) == 0xD0 && (h[1] & 0xFF) == 0xCF
                && h[2] == 0x11 && (h[3] & 0xFF) == 0xE0;
    }

    private void fillFileExists(QcReport report) {
        if (report == null)return;;
        report.setFileExists(attachmentExists(report.getFileUrl()));
    }
    private boolean attachmentExists(String fileUrl) {
        if (!org.springframework.util.StringUtils.hasText(fileUrl)) {
            return false;
        }
        try{
            java.nio.file.Path base = java.nio.file.Paths.get("uploads").toAbsolutePath().normalize();
            java.nio.file.Path file = java.nio.file.Paths.get(fileUrl).toAbsolutePath().normalize();
            //历史数据是 /files/reportN.pdf(URL路径,不在uploads下) -> 视为缺失
            if (!file.startsWith(base)) {
                return false;
            }
            return java.nio.file.Files.isRegularFile(file);
        }catch(Exception e){
            log.warn("[Attachment] 检查附件失败: {} , {}", fileUrl, e.getMessage());
            return false;
        }
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
            return Result.error(ResultCode.ERROR, (String) result.get("error"));
        }
        return Result.success(result);
    }
}
