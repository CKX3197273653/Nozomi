package org.mate.mate10.service.qc;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.mate.mate10.entity.qc.QcDefect;
import org.mate.mate10.entity.qc.QcProductionParam;
import org.mate.mate10.entity.qc.QcReport;

import java.util.List;
import java.util.Map;

public interface QcReportService {

    Long createFullReport(QcReport report, List<QcDefect> defects, QcProductionParam qcProductionParam);

    //创建报告
    Long createReport(QcReport report);
    //更新报告
    boolean updateReport(QcReport report);
    //删除报告
    boolean deleteReport(Long id);
    //ID查询
    QcReport getById(Long id);


    //分页查询
    Page<QcReport> pageList(int pageNum, int pageSize,
                            String reportNo, String productName,
                            String productBatch, String productionLine,
                            String status);
    //产品名称查询
    List<QcReport> getByProductName(String productName);
    //状态查询
    List<QcReport> getByStatus(String status);

    //更新处理状态
    boolean updateStatus(Long id,String status);
    //更新缺陷设计
    boolean updateDefectCount(Long id,int defectCount,String severityLevel);

    void sendAnalyzeTask(Long reportId,String fileUrl,String fileType,String prompt);

    void processAnalyzeTask(Long reportId,String fileUrl,String fileType,String prompt);
    //时间趋势统计
    List<Map<String, Object>> countByTimeTrend(int days);
    //产品线缺陷统计
    List<Map<String, Object>> countByProductionLine();
    //产品批次缺陷统计
    List<Map<String, Object>> countByProductBatch(int limit);
    //根因分析
    Map<String, Object> analyzeRootCause(Long reportId);
}
