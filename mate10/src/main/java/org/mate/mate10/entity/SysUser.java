package org.mate.mate10.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
@Data
@TableName("sys_user")
public class SysUser {
    @TableId
    private Long id;
    @TableField("username")
    private String username;
    @TableField("email")
    private String email;
    @TableField("password")
    private String password;
    @TableField("nickname")
    private String nickname;
    @TableField("phone")
    private String phone;
    @TableField("status")
    private Integer status;
    @TableField("lastLoginTime")
    private LocalDateTime lastLoginTime;
    @TableField("createTime")
    private LocalDateTime createTime;
    @TableField("updateTime")
    private LocalDateTime updateTime;
    @TableField("role")
    private int userRole;
}
