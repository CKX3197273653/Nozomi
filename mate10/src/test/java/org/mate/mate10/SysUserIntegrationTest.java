package org.mate.mate10;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mate.mate10.entity.SysUser;
import org.mate.mate10.service.SysUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles({"dev", "ollama"})
@TestPropertySource(properties = "JWT_SECRET_KEY=integration-test-secret-key-0123456789abcdef")
@Testcontainers
public class SysUserIntegrationTest {
    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withUsername("mate10db")
            .withImagePullPolicy(imageName -> false);

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }
    @Autowired
    SysUserService sysUserService;

    @Test
    @DisplayName("注册 + 登录走真实 MySQL")
    void registerThenLogin() {
        SysUser u = new SysUser();
        u.setUsername("it_user_001");
        u.setPassword("pass123456");
        u.setNickname("集成测试");

        assertThat(sysUserService.addUser(u)).isTrue();

        SysUser logged = sysUserService.login("it_user_001", "pass123456");
        assertThat(logged).isNotNull();
        assertThat(logged.getUsername()).isEqualTo("it_user_001");
    }
}
