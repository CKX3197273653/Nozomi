package org.mate.mate10.service.Impl;

import lombok.RequiredArgsConstructor;
import org.mate.mate10.document.ChatMessageDocument;
import org.mate.mate10.repository.ChatMessageRepository;
import org.mate.mate10.service.MongoChatMessageService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MongoChatMessageServiceImpl implements MongoChatMessageService {
    private final ChatMessageRepository chatMessageRepository;

    @Override
    public void saveUserMessage(Long chatId,Long userId,String content){
        ChatMessageDocument message = new ChatMessageDocument();
        message.setChatId(chatId);
        message.setUserId(userId);
        message.setRole("User");
        message.setContent(content);
        message.setCreateTime(LocalDateTime.now());
        chatMessageRepository.save(message);
    }
    @Override
    public void saveAiMessage(Long chatId,Long userId,String content){
        ChatMessageDocument message = new ChatMessageDocument();
        message.setChatId(chatId);
        message.setUserId(userId);
        message.setRole("ai");
        message.setContent(content);
        message.setCreateTime(LocalDateTime.now());
        chatMessageRepository.save(message);
    }
    @Override
    public List<ChatMessageDocument> getRecentMessages(Long chatId, int limit) {
        List<ChatMessageDocument> desc = chatMessageRepository
                .findByChatIdOrderByCreateTimeDesc(chatId, PageRequest.of(0, limit));
        List<ChatMessageDocument> asc = new ArrayList<>(desc);
        Collections.reverse(asc);   // 转成时间升序
        return asc;
    }
    @Override
    public  List<ChatMessageDocument> getMessagesByChatId(Long chatId){
        return chatMessageRepository.findByChatId(chatId);
    }
    @Override
    public  List<ChatMessageDocument> getMessageByUserId(Long userId){
        return chatMessageRepository.findByUserId(userId);
    }
    @Override
    public List<ChatMessageDocument> getMessageByChatIdAndRole(Long chatId,String role){
        return chatMessageRepository.findByChatIdAndRole(chatId, role);
    }
}
