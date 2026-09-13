package org.mate.mate10.service;

import org.mate.mate10.entity.ChatRecord;
import org.springframework.kafka.annotation.KafkaListener;

import java.util.List;
import java.util.Map;
public interface ChatRecordService {
    String extractKeyword(String question,String answer);

    void saveChatRecord(Long chatId,Long userId,String question,String answer);
    Map<String,Object> getMyChatAnalysis(Long userId);

    List<ChatRecord> getChatMessages(Long chatId);

    void sendChatRecordToKafka(Long chatId,Long userId,String question,String answer);

}
