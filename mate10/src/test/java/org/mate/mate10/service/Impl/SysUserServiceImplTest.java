package org.mate.mate10.service.Impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mate.mate10.entity.SysUser;
import org.mate.mate10.mapper.SysUserMapper;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SysUserServiceImplTest {

    // 两个依赖都 mock
    private final SysUserMapper sysUserMapper = mock(SysUserMapper.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

    // 构造器是包级私有
    private final SysUserServiceImpl service =
            new SysUserServiceImpl(sysUserMapper, passwordEncoder);

    @Test
    @DisplayName("正常路径：用户存在 + 密码正确 + 状态正常 → 返回用户")
    void login_success() {
        SysUser user = user("admin1", "$2a$10$hash", 1);
        when(sysUserMapper.selectByUsernameOrEmail("admin1")).thenReturn(user);
        when(passwordEncoder.matches("admin123", "$2a$10$hash")).thenReturn(true);

        SysUser result = service.login("admin1", "admin123");

        assertThat(result).isNotNull();
        assertThat(result.getUsername()).isEqualTo("admin1");
    }

    @Test
    @DisplayName("分支 1：用户不存在 → 返回 null，且不校验密码")
    void login_userNotFound() {
        when(sysUserMapper.selectByUsernameOrEmail("nobody")).thenReturn(null);

        SysUser result = service.login("nobody", "anyPassword");

        assertThat(result).isNull();
        //用户都不存在，就不该再去校验密码（省一次 BCrypt 计算）
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    @DisplayName("分支 2：密码错误 → 返回 null")
    void login_wrongPassword() {
        SysUser user = user("admin1", "$2a$10$hash", 1);
        when(sysUserMapper.selectByUsernameOrEmail("admin1")).thenReturn(user);
        when(passwordEncoder.matches("wrongPassword", "$2a$10$hash")).thenReturn(false);

        assertThat(service.login("admin1", "wrongPassword")).isNull();
    }

    @Test
    @DisplayName("分支 3：用户被禁用（status=0）→ 返回 null")
    void login_disabledUser() {
        SysUser user = user("admin1", "$2a$10$hash", 0);
        when(sysUserMapper.selectByUsernameOrEmail("admin1")).thenReturn(user);
        when(passwordEncoder.matches("admin123", "$2a$10$hash")).thenReturn(true);

        assertThat(service.login("admin1", "admin123")).isNull();
    }

    //辅助方法

    private SysUser user(String username, String password, int status) {
        SysUser u = new SysUser();
        u.setId(1L);
        u.setUsername(username);
        u.setPassword(password);
        u.setStatus(status);
        return u;
    }
}