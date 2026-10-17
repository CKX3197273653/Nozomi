package org.mate.mate10.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.mate.mate10.common.PasswordPolicy;
import org.mate.mate10.entity.SysUser;
import org.mate.mate10.mapper.SysUserMapper;
import org.mate.mate10.service.SysUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.mate.mate10.common.RegisterRejectedException;
import org.mate.mate10.mapper.SysEmployeeMapper;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser>
        implements SysUserService {
    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final SysEmployeeMapper sysEmployeeMapper;
@Autowired
SysUserServiceImpl(SysUserMapper sysUserMapper,PasswordEncoder passwordEncoder,  SysEmployeeMapper sysEmployeeMapper){
    this.sysUserMapper = sysUserMapper;
    this.passwordEncoder = passwordEncoder;
    this.sysEmployeeMapper = sysEmployeeMapper;
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
        String pwdErr = PasswordPolicy.validate(sysUser.getPassword());
        if (pwdErr != null) {
            return false;
        }
        sysUser.setPassword(passwordEncoder.encode(sysUser.getPassword()));
        sysUser.setStatus(1);
        return save(sysUser);
    }
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long registerByRoster(SysUser user, String empNo) {
        //入参校验
        //入参校验 —— 注册是【一刀切】:表单上有的框都必须填
        if (!StringUtils.hasText(user.getUsername())) {
            throw new RegisterRejectedException("用户名不能为空");
        }
        if (!StringUtils.hasText(user.getNickname())) {
            throw new RegisterRejectedException("昵称不能为空");
        }
        if (!StringUtils.hasText(user.getPhone())) {
            throw new RegisterRejectedException("手机号不能为空");
        }
        if (!StringUtils.hasText(user.getEmail())) {
            throw new RegisterRejectedException("邮箱不能为空");
        }
        if (!StringUtils.hasText(empNo)) {
            throw new RegisterRejectedException("员工工号不能为空");
        }
        //密码规则(非空 + 8~16 + 无空格)
        String pwdErr = PasswordPolicy.validate(user.getPassword());
        if (pwdErr != null) {
            throw new RegisterRejectedException(pwdErr);
        }


        String no = empNo.trim();

        //② 唯一性校验（给用户友好提示;真正的兜底靠数据库唯一索引）
        if (isUsernameDuplicate(user.getUsername(), null)) {
            throw new RegisterRejectedException("用户名已存在");
        }
        if (existsByEmail(user.getEmail())) {
            throw new RegisterRejectedException("邮箱已被注册");
        }
        if (existsByNickname(user.getNickname())) {
            throw new RegisterRejectedException("昵称已存在");
        }
        if (existsByPhone(user.getPhone())) {
            throw new RegisterRejectedException("手机号已存在");
        }

        //服务端强制默认值 —— 【绝不信任客户端传来的 role / status / id】
        user.setId(null);              // ⚠防止客户端塞 id 覆盖已有用户
        user.setEmpNo(no);
        user.setStatus(1);
        user.setUserRole(2);
        user.setPassword(passwordEncoder.encode(user.getPassword()));

        if (!save(user)) {
            throw new RegisterRejectedException("注册失败，请重试");
        }

        //原子占用工号
        if (sysEmployeeMapper.occupy(no, user.getId()) == 0) {
            throw new RegisterRejectedException("工号不存在或已被注册，请联系管理员");
        }

        log.info("[Register] 员工自助注册成功 userId={} empNo={}", user.getId(), no);
        return user.getId();
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
            if (isUsernameDuplicate(sysUser.getUsername(), existUser.getId())) {
                return false;
            }
        }
        //白名单只润许这几个字段,password根本不参与,不会被覆盖
        SysUser patch = new SysUser();
        patch.setId(sysUser.getId());
        patch.setUsername(sysUser.getUsername());
        patch.setNickname(sysUser.getNickname());
        patch.setPhone(sysUser.getPhone());
        patch.setEmail(sysUser.getEmail());
        return updateById(patch);
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

    @Override
    public boolean enableUser(Long userId) {
        if (userId == null) {
            return false;
        }
        SysUser existUser = getById(userId);
        if (existUser == null) {
            return false;
        }
        if (existUser.getStatus() != null && existUser.getStatus() == 1) {
            return false;
        }
        SysUser patch = new SysUser();
        patch.setId(userId);
        patch.setStatus(1);
        return updateById(patch);
    }

    // 删
    @Override
    public boolean deleteUser(Long userId) {
        return disableUser(userId);
    }

    @Override
    public boolean resetPassword(Long userId, String newPassword) {
        if (userId == null || !StringUtils.hasText(newPassword)) {
            return false;
        }
        if (getById(userId) == null) {
            return false;
        }
        SysUser patch = new  SysUser();
        patch.setId(userId);
        patch.setPassword(passwordEncoder.encode(newPassword));
        return updateById(patch);
    }

    @Override
    public boolean updateRole(Long userId, Integer role) {
    if (userId == null ||  role == null) {
        return false;
    }
    if (getById(userId) == null) {
        return  false;
    }
    SysUser patch = new  SysUser();
    patch.setId(userId);
    patch.setUserRole(role);
    return updateById(patch);
    }

}
