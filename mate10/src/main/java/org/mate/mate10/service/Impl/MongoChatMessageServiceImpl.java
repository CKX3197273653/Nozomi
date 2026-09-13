package org.mate.mate10.service.Impl;

import lombok.RequiredArgsConstructor;
import org.mate.mate10.document.ChatMessageDocument;
import org.mate.mate10.repository.ChatMessageRepository;
import org.mate.mate10.service.ChatRecordService;
import org.mate.mate10.service.MongoChatMessageService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MongoChatMessageServiceImpl implements MongoChatMessageService {
    private final ChatMessageRepository chatMessageRepository;
    private final ChatRecordService chatRecordService;
    private String currentQuestion = null;

    @Override
    public void saveUserMessage(Long chatId,Long userId,String content){
        ChatMessageDocument message = new ChatMessageDocument();
        message.setChatId(chatId);
        message.setUserId(userId);
        message.setRole("User");
        message.setContent(content);
        message.setCreateTime(LocalDateTime.now());
        chatMessageRepository.save(message);
        this.currentQuestion = content;
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
        if (this.currentQuestion != null){
            chatRecordService.saveChatRecord(chatId,userId,this.currentQuestion,content);
            this.currentQuestion = null;
        }
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
