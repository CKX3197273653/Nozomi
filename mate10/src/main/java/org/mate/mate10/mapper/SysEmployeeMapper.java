package org.mate.mate10.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.mate.mate10.entity.SysEmployee;

public interface SysEmployeeMapper extends BaseMapper<SysEmployee> {

    //先改后查,防并发请求同时收到registered = 0
    @Update("""
            UPDATE sys_employee
               SET registered = 1, user_id = #{userId}, register_time = NOW()
             WHERE emp_no = #{empNo} AND registered = 0
            """)
    int occupy(@Param("empNo") String empNo, @Param("userId") Long userId);

    @Update("""
            UPDATE sys_employee
               SET user_id = #{userId}
             WHERE emp_no = #{empNo}
            """)
    int bindUser(@Param("empNo") String empNo, @Param("userId") Long userId);
}
