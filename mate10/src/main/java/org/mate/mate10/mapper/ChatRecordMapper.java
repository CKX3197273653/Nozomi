package org.mate.mate10.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Select;
import org.mate.mate10.entity.ChatRecord;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

public interface  ChatRecordMapper extends BaseMapper<ChatRecord> {

    @Select("SELECT * FROM chat_record WHERE user_id = #{userId} ORDER BY create_time DESC LIMIT 30")
    List<ChatRecord> selectListByUserId(long userId);
    @Delete("DELETE FROM chat_record WHERE chat_id = #{chatId}")
    void deleteByChatId(Long chatId);
    @Select("select * from chat_record order by create_time desc limit 1000")
    List<ChatRecord> selectAllRecords();
    @Select("select * from chat_record")
    List<ChatRecord> selectListByChatId(long chatId);
}
