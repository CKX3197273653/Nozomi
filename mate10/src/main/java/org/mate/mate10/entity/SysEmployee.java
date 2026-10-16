package org.mate.mate10.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_employee")
public class SysEmployee {

    @TableId(type = IdType.AUTO)
    private Long id;
    //唯一工号
    private String empNo;

    private String name;

    private String department;

    //被注册状态
    private Integer registered;

    //使用工号的用户ID
    private Long userId;

    private LocalDateTime registerTime;

    private LocalDateTime createTime;
}
