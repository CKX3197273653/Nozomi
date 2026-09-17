package org.mate.mate10.controller;

import org.mate.mate10.common.Result;
import org.mate.mate10.entity.SysUser;
import org.mate.mate10.service.SysUserService;
import org.mate.mate10.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/blocker/user")
public class SysUserController {
    @Autowired
    private SysUserService sysUserService;
    private final JwtUtil jwtUtil;

    public SysUserController(SysUserService sysUserService, JwtUtil jwtUtil) {
        this.sysUserService = sysUserService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    public Result<?> login(
            @RequestParam String username,
            @RequestParam String password
    ){
        if (!StringUtils.hasText(username)||!StringUtils.hasText(password)){
            return  Result.error("用户名/邮箱和密码不能空");
        }
        SysUser user = sysUserService.login(username, password);
        if (user == null){
            return Result.error("用户名/邮箱,密码错误或用户被禁用");
        }

        Map<String, Object>claims = new HashMap<>();
        claims.put("userId",user.getId());
        claims.put("username",user.getUsername());
        claims.put("role", user.getUserRole());

        String token = jwtUtil.generateToken(claims);

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("user", user);

        return Result.success(result);
    }

    @PostMapping("/register")
    public Result<?> register(@RequestBody SysUser sysUser){
        if (!StringUtils.hasText(sysUser.getUsername()) || !StringUtils.hasText(sysUser.getPassword())){
            return Result.error("用户名和密码不能为空");
        }
        
        // 检查用户名是否已存在
        if (sysUserService.existsByUsername(sysUser.getUsername())) {
            return Result.error("用户名已存在");
        }
        
        // 检查邮箱是否已存在（如果提供了邮箱）
        if (StringUtils.hasText(sysUser.getEmail()) && sysUserService.existsByEmail(sysUser.getEmail())) {
            return Result.error("邮箱已被注册");
        }
        //昵称检查
        if (sysUserService.existsByNickname(sysUser.getNickname())) {
            return Result.error("昵称已存在");
        }
        //手机号检查
        if (sysUserService.existsByPhone(sysUser.getPhone())) {
            return Result.error("手机号已存在");
        }
        // 设置默认值
        sysUser.setStatus(1); // 默认启用
        sysUser.setUserRole(2); // 默认普通用户
        
        // 注册用户
        boolean success = sysUserService.addUser(sysUser);
        if (success) {
            return Result.success("注册成功");
        } else {
            return Result.error("注册失败，请重试");
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/add")
    public Result<?> add(@RequestBody SysUser sysUser){
        boolean success = sysUserService.addUser(sysUser);
        if (success){
            return Result.success();
        }else {
            return Result.error("新增失败,用户名不能空,不能重复");
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/update")
    public Result<?> updateUser(@RequestBody SysUser sysUser){
        boolean success = sysUserService.updateUser(sysUser);
        if (success){
            return Result.success();
        }else {
            return Result.error("更新失败:用户名重复或者是空的");
        }
    }
    //改名字
    @GetMapping("/check-username")
    public Result<Boolean> checkUsername(@RequestParam String username){
        boolean exists  = sysUserService.existsByUsername(username);
        return Result.success(exists);
    }
    @PutMapping("/update-username")
    public Result<?> updateUsername(
            @RequestParam Long userId,
            @RequestParam String username
    ){
        boolean success = sysUserService.updateUsername(userId,username);
        return success ? Result.success() : Result.error("失败喽");
    }


    @PutMapping("/updatePassword")
    public Result<?> updatePassword(
            @RequestParam Long userId,
            @RequestParam String oldPassword,
            @RequestParam String newPassword
    ){
        boolean success = sysUserService.updatePassword(userId,oldPassword,newPassword);
            if (success){
                return Result.success();
            }else {
                return Result.error("修改失败:用户不存在,旧密码错了,新旧密码不能是空的");
            }
    }
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/disable/{userId}")
    public Result<?> disableUser(@PathVariable Long userId){
        boolean success = sysUserService.disableUser(userId);
        if (success){
            return Result.success();
        }else {
            return Result.error("禁用失败,用户不能不存在,也可能被禁用过了");
        }
    }
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/delete/{userId}")
    public Result<?> deleteUser(@PathVariable Long userId){
        boolean success = sysUserService.deleteUser(userId);
        if (success){
            return Result.success();
        }else {
            return Result.error("删除失败,用户不存在,也不能空删除");
        }
    }
    @GetMapping("/get/{userId}")
    public Result<SysUser> getUserById(@PathVariable Long userId){
        if (userId == null){
            return Result.error("用户id不能空");
        }
        SysUser user = sysUserService.getById(userId);
        if (user == null){
            return Result.error("用户不存在");
        }return Result.success(user);
    }
}
