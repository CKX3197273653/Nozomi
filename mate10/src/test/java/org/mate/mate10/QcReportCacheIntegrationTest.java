package org.mate.mate10;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mate.mate10.entity.qc.QcReport;
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
}