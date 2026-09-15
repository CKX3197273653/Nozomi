package org.mate.mate10.service.Impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.mate.mate10.entity.Chat;
import org.mate.mate10.entity.ChatRecord;
import org.mate.mate10.mapper.ChatMapper;
import org.mate.mate10.mapper.ChatRecordMapper;
import org.mate.mate10.service.ChatRecordService;
import org.mate.mate10.service.ChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ChatServiceImpl extends ServiceImpl<ChatMapper, Chat> implements ChatService {
    private final  ChatRecordService chatRecordService;

    private final  ChatRecordMapper chatRecordMapper;

    @Override
    public Chat createChat(Long userId,String title){
        Chat chat =  new Chat();
        chat.setUserId(userId);
        chat.setTitle(title);
        chat.setCreateTime(LocalDateTime.now());
        chat.setUpdateTime(LocalDateTime.now());
        save(chat);
        return  chat;
    }
    @Override
    public List<Chat> getUserChats(Long userId) {
        return baseMapper.selectByUserId(userId);
    }
    @Override
    public Map<String, Object> getChatDetail(Long chatId) {
        Map<String, Object> result = new HashMap<>();
        Chat chat = getById(chatId);
        result.put("chat", chat);

        List<ChatRecord> messages = chatRecordService.getChatMessages(chatId);
        result.put("messages", messages);

        return result;
    }
    @Override
    public boolean updateChatTitle(Long chatId, String title) {
        Chat chat = new Chat();
        chat.setId(chatId);
        chat.setTitle(title);
        chat.setUpdateTime(LocalDateTime.now());
        return updateById(chat);
    }

    @Override
    public boolean deleteChat(Long chatId) {
        chatRecordMapper.deleteByChatId(chatId);
        return removeById(chatId);
    }



}
