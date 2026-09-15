package org.mate.mate10.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;
import org.mate.mate10.entity.Chat;

import java.util.List;

public interface ChatMapper extends BaseMapper<Chat> {
    @Select("SELECT * FROM chat where userId = #{userId} ORDER BY createTime DESC")
    List<Chat> selectByUserId(Long userId);
}
