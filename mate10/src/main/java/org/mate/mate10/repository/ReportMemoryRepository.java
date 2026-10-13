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

    //审计,报告的完整结论版本链(含被覆盖的历史版本),实践正序
    List<ReportMemoryDocument> findByReportNoOrderByAnalyzedAtAsc(String reportNo);
}
