package org.mate.mate10.repository;

import org.mate.mate10.document.ReportMemoryDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportMemoryRepository extends MongoRepository<ReportMemoryDocument,String> {
    //有效记录,可当作健康检查
    List<ReportMemoryDocument> findByReportNoAndSupersededFalse(String reportNo);

    //报告最新一条(读取入口)
    ReportMemoryDocument findFirstByReportNoAndSupersededFalseOrderByAnalyzedAtDesc(String reportNo);
}
