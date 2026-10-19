package org.mate.mate10.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigInteger;
import java.time.LocalDateTime;
@Data
@TableName("sys_user")
public class SysUser {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("username")
    private String username;
    @TableField("email")
    private String email;
    @TableField("password")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
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
    private Integer userRole;
    @TableField("emp_no")
    private String empNo;
    //token版本号,更改密码/重置/禁用时自增会使旧token失效
    private Integer tokenVersion;
}
