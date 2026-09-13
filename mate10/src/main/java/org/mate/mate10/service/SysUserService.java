package org.mate.mate10.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.mate.mate10.entity.SysUser;

public interface SysUserService extends IService<SysUser> {
    SysUser login(String username,String password);

    boolean addUser(SysUser sysUser);

    boolean updateUser(SysUser sysUser);

    boolean updatePassword(Long userId, String oldPassword, String newPassword);

    boolean disableUser(Long userId);

    boolean deleteUser(Long userId);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean updateUsername(Long userId, String username);
    boolean existsByPhone(String phone);

    boolean existsByNickname(String nickname);
}
