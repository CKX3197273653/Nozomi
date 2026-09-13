package org.mate.mate10.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.mate.mate10.entity.SysUser;

public interface SysUserMapper extends BaseMapper<SysUser>{
    default SysUser selectByUsernameOrEmail(String usernameOrEmail){
        return this.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, usernameOrEmail)
                        .or()
                        .eq(SysUser::getEmail, usernameOrEmail)
        );
    }
    
    // 检查邮箱是否已存在
    boolean existsByEmail(@Param("email") String email);
}
