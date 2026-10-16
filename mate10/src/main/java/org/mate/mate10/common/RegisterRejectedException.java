package org.mate.mate10.common;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.mate.mate10.entity.SysEmployee;

//注册因为(工号不在名册, 已被占用, 用户名重复...被拒绝)
public class RegisterRejectedException  extends RuntimeException {
    public RegisterRejectedException(String message) {
        super(message);
    }
}
