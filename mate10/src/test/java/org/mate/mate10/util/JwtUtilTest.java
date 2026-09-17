package org.mate.mate10.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {

    private static final String TEST_KEY = "unit-test-secret-key-0123456789-abcdefghij";

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        // @Value 注入的字段，单元测试里用反射设置
        ReflectionTestUtils.setField(jwtUtil, "secretKey", TEST_KEY);
        // @PostConstruct 不会自动执行，手动调用
        jwtUtil.validateKey();
    }

    @Test
    @DisplayName("正常路径：生成的 token 能被解析，claims 一致")
    void generateAndParse_shouldReturnSameClaims() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", 123L);
        claims.put("username", "admin1");
        claims.put("role", 2);

        String token = jwtUtil.generateToken(claims);
        Claims parsed = jwtUtil.parseToken(token);

        assertThat(token).isNotBlank();
        assertThat(parsed.get("username", String.class)).isEqualTo("admin1");
        assertThat(parsed.get("userId", Long.class)).isEqualTo(123L);
        assertThat(parsed.get("role", Integer.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("安全：用【其他密钥】签的 token 必须被拒绝")
    void parse_wrongKey_shouldFail() {
        String otherKey = "another-secret-key-9876543210-zyxwvutsrq";

        Map<String, Object> fakeClaims = new HashMap<>();
        fakeClaims.put("username", "hacker");
        fakeClaims.put("role", 1);

        String foreignToken = Jwts.builder()
                .setClaims(fakeClaims)
                .setExpiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(Keys.hmacShaKeyFor(otherKey.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThatThrownBy(() -> jwtUtil.parseToken(foreignToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("安全：【过期】的 token 必须被拒绝")
    void parse_expiredToken_shouldFail() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("username", "admin1");

        String expiredToken = Jwts.builder()
                .setClaims(claims)
                .setExpiration(new Date(System.currentTimeMillis() - 1000))
                .signWith(Keys.hmacShaKeyFor(TEST_KEY.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThatThrownBy(() -> jwtUtil.parseToken(expiredToken))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("安全：【被篡改】的 token 必须被拒绝")
    void parse_tamperedToken_shouldFail() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("username", "admin1");
        claims.put("role", 2);

        String token = jwtUtil.generateToken(claims);


        String tampered = token.substring(0, token.length() - 5) + "AAAAA";

        assertThatThrownBy(() -> jwtUtil.parseToken(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("配置校验：密钥太短时，validateKey 应该抛异常")
    void validateKey_tooShort_shouldThrow() {
        JwtUtil shortKeyUtil = new JwtUtil();
        ReflectionTestUtils.setField(shortKeyUtil, "secretKey", "abc");

        assertThatThrownBy(shortKeyUtil::validateKey)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("长度不足");
    }
}