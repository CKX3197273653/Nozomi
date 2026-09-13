package org.mate.mate10.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.mate.mate10.entity.SysUser;
import org.mate.mate10.mapper.SysUserMapper;
import org.mate.mate10.service.SysUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser>
        implements SysUserService {
    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
@Autowired
SysUserServiceImpl(SysUserMapper sysUserMapper,PasswordEncoder passwordEncoder){
    this.sysUserMapper = sysUserMapper;
    this.passwordEncoder = passwordEncoder;
}
    // 登录
    @Override
    public SysUser login(String usernameOrEmail, String password) {
        SysUser user = sysUserMapper.selectByUsernameOrEmail(usernameOrEmail);
        if (user == null) {
            return null;
        }
        //密码对比
        if (!passwordEncoder.matches(password, user.getPassword())) {
            return null;
        }
        // 配合disableUser针对封禁用户
        if (user.getStatus() == 0) {
            return null;
        }
        return user;
    }
    private boolean isUsernameDuplicate(String username, Long excludeUserId) {
        // 判空
        if (username == null || username.trim().isEmpty()) {
            return true;
        }
        LambdaQueryWrapper<SysUser> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysUser::getUsername, username.trim());
        // 排除自身ID
        if (excludeUserId != null) {
            queryWrapper.ne(SysUser::getId, excludeUserId);
        }
        return count(queryWrapper) > 0;
    }

    // 增
    @Override
    public boolean addUser(SysUser sysUser) {
        if (sysUser == null){
            return false;
        }
        if (isUsernameDuplicate(sysUser.getUsername(), null)) {
            return false;
        }
        sysUser.setPassword(passwordEncoder.encode(sysUser.getPassword()));
        sysUser.setStatus(1);
        return save(sysUser);
    }
    //改
    @Override
    public boolean updateUser(SysUser sysUser) {
        if (sysUser.getId() == null) {
            return false;
        }
        SysUser existUser = getById(sysUser.getId());
        if (existUser == null) {
            return false;
        }
        if (!existUser.getUsername().equals(sysUser.getUsername())) {
            if (isUsernameDuplicate(sysUser.getUsername(), sysUser.getId())) {
                return false;
            }
        }
        return updateById(sysUser);
    }

    @Override
    public boolean existsByUsername(String username){
    return this.lambdaQuery()
            .eq(SysUser::getUsername,username)
            .exists();
    }

    @Override
    public boolean existsByEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return false;
        }
        return this.lambdaQuery()
                .eq(SysUser::getEmail, email)
                .exists();
    }
    @Override
    public boolean updateUsername(Long userId, String username) {
        if (userId == null || username == null || username.isBlank()) {
            return false;
        }
        if (existsByUsername(username)) {
            return false;
        }
        return this.lambdaUpdate()
                .eq(SysUser::getId, userId)
                .set(SysUser::getUsername, username)
                .update();
    }

    @Override
    public boolean existsByPhone(String phone) {
        if (!StringUtils.hasText(phone)) {
            return false;
        }
        return this.lambdaQuery()
                .eq(SysUser::getPhone, phone)
                .exists();
    }

    @Override
    public boolean existsByNickname(String nickname) {
        if (!StringUtils.hasText(nickname)) {
            return false;
        }
        return this.lambdaQuery()
                .eq(SysUser::getNickname, nickname)
                .exists();
    }

    // 改密码
    @Override
    public boolean updatePassword(Long userId, String oldPassword, String newPassword) {
        if (userId == null || !StringUtils.hasText(oldPassword) || !StringUtils.hasText(newPassword)) {
            return false;
        }
        SysUser user = getById(userId);
        if (user == null) {
            return false;
        }
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            return false;
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        return updateById(user);
    }
    //删
    @Override
    public boolean disableUser(Long userId) {
        if (userId == null) {
            return false;
        }
        SysUser existUser = getById(userId);
        if (existUser == null) {
            return false;
        }
        if (existUser.getStatus() == null || existUser.getStatus() == 0) {
            return false;
        }
        SysUser updateUser = new SysUser();
        updateUser.setId(userId);
        updateUser.setStatus(0);
        return updateById(updateUser);
    }

    // 删
    @Override
    public boolean deleteUser(Long userId) {
        if (userId == null) {
            return false;
        }
        if (getById(userId) == null) {
            return false;
        }
        return removeById(userId);
    }
}
