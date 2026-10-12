package org.mate.mate10;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mate.mate10.entity.qc.QcProductionParam;
import org.mate.mate10.entity.qc.QcReport;
import org.mate.mate10.service.qc.QcDefectService;
import org.mate.mate10.service.qc.QcProductionParamService;
import org.mate.mate10.service.qc.QcReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles({"dev", "ollama", "test"})
@TestPropertySource(properties = "JWT_SECRET_KEY=integration-test-secret-key-0123456789abcdef")
@Testcontainers
class QcReportCacheIntegrationTest {

    @Autowired
    private QcDefectService qcDefectService;

    @Autowired
    private QcProductionParamService qcProductionParamService;

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("mate10db")
            .withImagePullPolicy(imageName -> false);

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379)
            .withImagePullPolicy(imageName -> false);

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", mysql::getJdbcUrl);
        r.add("spring.datasource.username", mysql::getUsername);
        r.add("spring.datasource.password", mysql::getPassword);
        r.add("spring.data.redis.host", redis::getHost);
        r.add("spring.data.redis.port", () -> redis.getMappedPort(6379).toString());
        r.add("spring.data.redis.password", () -> "");
        //测试用临时Lucene目录,不和后端抢锁
        r.add("app.rag.lucene-dir", () -> System.getProperty("java.io.tmpdir") + "/mate10-test-lucene");
        //关掉Rerank,测试不加载ONNX模型
        r.add("app.rag.rerank.enabled", () -> "false");
    }

    @Autowired
    private QcReportService qcReportService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private CacheManager cacheManager;

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
        //借助缺陷统计把 qcStats::severity 塞进缓存
        //跨 service 也可以被 param的写操作清除掉,正好验证allEntries 使全局清空
        qcDefectService.countByDefectType();
        assertThat(stringRedisTemplate.hasKey("qcStats::defectType")).isTrue();
        //param的更新操作(id存在也无所谓,拦截器一样clear)
        QcProductionParam param = new QcProductionParam();
        param.setId(999L);
        param.setOperator("测试");
        qcProductionParamService.updateParam(param);

        //断言清空
        assertThat(stringRedisTemplate.hasKey("qcStats::defectType")).isFalse();
    }



}