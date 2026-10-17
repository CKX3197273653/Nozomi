package org.mate.mate10.controller;

import lombok.extern.slf4j.Slf4j;
import org.mate.mate10.common.CurrentUserProvider;
import org.mate.mate10.common.PasswordPolicy;
import org.mate.mate10.common.Result;
import org.mate.mate10.entity.SysUser;
import org.mate.mate10.service.SysUserService;
import org.mate.mate10.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.mate.mate10.dto.RegisterRequest;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/blocker/user")
public class SysUserController {
    @Autowired
    private SysUserService sysUserService;
    @Autowired
    private CurrentUserProvider currentUser;
    private final JwtUtil jwtUtil;

    public SysUserController(SysUserService sysUserService, JwtUtil jwtUtil) {
        this.sysUserService = sysUserService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    public Result<?> login(
            @RequestParam String username,
            @RequestParam String password
    ) {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            return Result.error("用户名/邮箱和密码不能空");
        }
        SysUser user = sysUserService.login(username, password);
        if (user == null) {
            return Result.error("用户名/邮箱,密码错误或用户被禁用");
        }

        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("username", user.getUsername());
        claims.put("role", user.getUserRole());

        String token = jwtUtil.generateToken(claims);

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("user", user);

        return Result.success(result);
    }

    @PostMapping("/register")
    public Result<?> register(@RequestBody RegisterRequest req) {
        SysUser user = new SysUser();
        user.setUsername(req.getUsername());
        user.setPassword(req.getPassword());
        user.setNickname(req.getNickname());
        user.setPhone(req.getPhone());
        user.setEmail(req.getEmail());

        sysUserService.registerByRoster(user, req.getEmpNo());
        return Result.success("注册成功");
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/add")
    public Result<?> add(@RequestBody SysUser sysUser) {
        boolean success = sysUserService.addUser(sysUser);
        if (success) {
            return Result.success();
        } else {
            return Result.error("新增失败,用户名不能空,不能重复");
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/update")
    public Result<?> updateUser(@RequestBody SysUser sysUser) {
        boolean success = sysUserService.updateUser(sysUser);
        if (success) {
            return Result.success();
        } else {
            return Result.error("更新失败:用户名重复或者是空的");
        }
    }

    //改名字
    @GetMapping("/check-username")
    public Result<Boolean> checkUsername(@RequestParam String username) {
        boolean exists = sysUserService.existsByUsername(username);
        return Result.success(exists);
    }

    @PutMapping("/update-username")
    public Result<?> updateUsername(
            @RequestParam Long userId,
            @RequestParam String username
    ) {
        Long me = currentUser.userIdOrNull();
        if (me == null) {
            return Result.error(401, "请登录");
        }
        if (!currentUser.isAdmin() && !me.equals(userId)) {
            return Result.error(403, "只可以修改自己的用户名");
        }
        if (!StringUtils.hasText(username)) {
            return Result.error("用户名不能为空");
        }

        boolean success = sysUserService.updateUsername(userId, username);
        return success ? Result.success() : Result.error("用户名已经存在或修改失败");
    }


    @PutMapping("/updatePassword")
    public Result<?> updatePassword(
            @RequestParam Long userId,
            @RequestParam String oldPassword,
            @RequestParam String newPassword
    ) {
        Long me = currentUser.userIdOrNull();
        if (me == null) {
            return Result.error(401,"未登录");
        }
        if (!me.equals(userId)) {
            return Result.error(403,"只能修改自己的密码");
        }
        String pwdErr = PasswordPolicy.validate(newPassword);
        if (pwdErr != null) {
            return Result.error(pwdErr);
        }
        boolean success = sysUserService.updatePassword(userId, oldPassword, newPassword);
        if (success) {
            return Result.success();
        } else {
            return Result.error("修改失败:用户不存在,旧密码错了,新旧密码不能是空的");
        }

    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/disable/{userId}")
    public Result<?> disableUser(@PathVariable Long userId) {
        boolean success = sysUserService.disableUser(userId);
        if (success) {
            return Result.success();
        } else {
            return Result.error("禁用失败,用户不能不存在,也可能被禁用过了");
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/delete/{userId}")
    public Result<?> deleteUser(@PathVariable Long userId) {
        boolean success = sysUserService.deleteUser(userId);
        if (success) {
            return Result.success();
        } else {
            return Result.error("删除失败,用户不存在,也不能空删除");
        }
    }

    @GetMapping("/get/{userId}")
    public Result<SysUser> getUserById(@PathVariable Long userId) {
        if (userId == null) {
            return Result.error("用户id不能空");
        }
        SysUser user = sysUserService.getById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }
        return Result.success(user);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/resetPassword/{userId}")
    public Result<?> resetPassword(@PathVariable Long userId,
                                   @RequestParam String newPassword) {
        String pwdErr = PasswordPolicy.validate(newPassword);
        if (pwdErr != null) {
            return Result.error(pwdErr);
        }
        boolean success = sysUserService.resetPassword(userId,newPassword);
        if (!success) {
            return Result.error("重置失败");
        }
        log.info("[Admin] {} 重置了 userId={}的密码", currentUser.userIdOrNull(),userId);
        return Result.success("密码已重置");
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/updateRole/{userId}")
    public Result<?> updateRole(@PathVariable Long userId,
                                @RequestParam Integer role) {
        Long me = currentUser.userIdOrNull();
        if (me != null && me.equals(userId)) {
            return Result.error("不能修改自己的角色");
        }
        if (role == null || (role != 1 && role != 2)) {
            return Result.error("角色只能是 1(管理员) 或 2(普通用户)");
        }
        boolean success = sysUserService.updateRole(userId, role);
        if (!success) {
            return Result.error("修改失败:用户不存在");
        }
        log.info("[Admin] {} 把 userId={} 的 role 改为 {}", me, userId, role);
        return Result.success("角色已更新");
    }


}
