package org.mate.mate10;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mate.mate10.entity.qc.QcProductionParam;
import org.mate.mate10.entity.qc.QcReport;
import org.mate.mate10.repository.ReportMemoryRepository;
import org.mate.mate10.service.ReportMemoryService;
import org.mate.mate10.service.qc.QcDefectService;
import org.mate.mate10.service.qc.QcProductionParamService;
import org.mate.mate10.service.qc.QcReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles({"dev", "ollama", "test"})
@TestPropertySource(properties = "JWT_SECRET_KEY=integration-test-secret-key-0123456789abcdef")
@Testcontainers
class QcReportCacheIntegrationTest {

    @Autowired
    private QcReportService qcReportService;

    @Autowired
    private QcDefectService qcDefectService;

    @Autowired
    private QcProductionParamService qcProductionParamService;

    @Autowired
    private ReportMemoryService reportMemoryService;

    @Autowired
    private ReportMemoryRepository memoryRepository;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private MongoTemplate mongoTemplate;


    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("mate10db")
            .withImagePullPolicy(imageName -> false);

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379)
            .withImagePullPolicy(imageName -> false);


    @Container
    static GenericContainer<?> mongo = new GenericContainer<>("mongo:6")
            .withExposedPorts(27017)
            .withImagePullPolicy(imageName -> false)
            .waitingFor(Wait.forLogMessage(".*Waiting for connections.*", 1));

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        //MySQL
        r.add("spring.datasource.url", mysql::getJdbcUrl);
        r.add("spring.datasource.username", mysql::getUsername);
        r.add("spring.datasource.password", mysql::getPassword);

        //Redis
        r.add("spring.data.redis.host", redis::getHost);
        r.add("spring.data.redis.port", () -> redis.getMappedPort(6379).toString());
        r.add("spring.data.redis.password", () -> "");

        //属性名是 spring.mongodb.uri（SB 4.0），不是 spring.data.mongodb.uri
        r.add("spring.mongodb.uri",
                () -> "mongodb://" + mongo.getHost() + ":" + mongo.getMappedPort(27017) + "/mate10db");

        //测试用临时 Lucene 目录，不和后端抢锁
        r.add("app.rag.lucene-dir", () -> System.getProperty("java.io.tmpdir") + "/mate10-test-lucene");
        //关掉 Rerank，测试不加载 ONNX 模型
        r.add("app.rag.rerank.enabled", () -> "false");
    }


    @BeforeEach
    void ensurePartialUniqueIndex() {
        IndexOptions options = new IndexOptions()
                .unique(true)
                .partialFilterExpression(Filters.eq("superseded", false));
        mongoTemplate.getCollection("report_memory")
                .createIndex(Indexes.ascending("report_no"), options);
    }

    //缓存测试

    @Test
    @DisplayName("@Cacheable 缓存 + 写操作清缓存（真实 Redis）")
    void cacheAndEvict() {
        QcReport report = new QcReport();
        report.setReportNo("R" + System.currentTimeMillis());
        report.setProductName("测试产品");
        report.setStatus("待检测");
        Long id = qcReportService.createReport(report);

        assertThat(qcReportService.getById(id)).isNotNull();

        String cacheKey = "qcDetail::report:" + id;
        assertThat(stringRedisTemplate.hasKey(cacheKey)).isTrue();

        qcReportService.deleteReport(id);
        assertThat(stringRedisTemplate.hasKey(cacheKey)).isFalse();
    }

    @Test
    @DisplayName("QcDefectService 写操作会清空 qcStats 缓存（真实 Redis）")
    void defectWriteEvictsCache() {
        //空结果同样写进
        qcDefectService.countByDefectType();
        assertThat(stringRedisTemplate.hasKey("qcStats::defectType")).isTrue();
        //删除不存在的,@EvictQcCache一样会执行
        qcDefectService.deleteDefect(9999L);
        //清空断言
        assertThat(stringRedisTemplate.hasKey("qcStats::defectType")).isFalse();
    }

    @Test
    @DisplayName("QcProductionParamService 写操作会清空缓存")
    void paramWriteEvictsCache() {
        //借助缺陷统计把 qcStats::defectType 塞进缓存
        //跨 service 也可以被 param 的写操作清除掉,正好验证 allEntries 使全局清空
        qcDefectService.countByDefectType();
        assertThat(stringRedisTemplate.hasKey("qcStats::defectType")).isTrue();
        //param 的更新操作(id 存在也无所谓,拦截器一样 clear)
        QcProductionParam param = new QcProductionParam();
        param.setId(999L);
        param.setOperator("测试");
        qcProductionParamService.updateParam(param);

        //断言清空
        assertThat(stringRedisTemplate.hasKey("qcStats::defectType")).isFalse();
    }

    //并发入档
    @Test
    @DisplayName("并发入档：不抛异常，且最终恰好 1 条有效档案")
    void concurrentArchive_shouldNotThrow_andKeepExactlyOneActive() throws Exception {
        String reportNo = "R-TEST-CONCURRENT";
        String answer = "## 根因判断\n测试结论\n\n## 改进建议\n测试建议";

        //保证测试可重复运行（幂等）——上次留下的档案会让本次行为不同
        memoryRepository.deleteAll(
                memoryRepository.findByReportNoOrderByAnalyzedAtAsc(reportNo));

        int threads = 4;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);          // 让线程同时起跑
        List<Future<String>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return reportMemoryService.saveConclusion(reportNo, answer, 1L, "v-test");
            }));
        }
        start.countDown();

        long archived = 0;
        for (Future<String> f : futures) {
            //  saveConclusion 若没 catch DuplicateKeyException，这里会抛 ExecutionException -> 测试失败
            if (f.get() != null) {
                archived++;
            }
        }
        pool.shutdown();
        assertThat(archived).isGreaterThanOrEqualTo(1);

        assertThat(memoryRepository.findByReportNoAndSupersededFalse(reportNo)).hasSize(1);
        assertThat(memoryRepository.findByReportNoOrderByAnalyzedAtAsc(reportNo))
                .hasSize((int) archived);
    }
}