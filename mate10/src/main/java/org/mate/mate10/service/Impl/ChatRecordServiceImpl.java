package org.mate.mate10.service.Impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mate.mate10.config.KafkaConfig;
import org.mate.mate10.entity.ChatRecord;
import org.mate.mate10.mapper.ChatRecordMapper;
import org.mate.mate10.service.ChatRecordService;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.baomidou.mybatisplus.extension.toolkit.Db.save;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatRecordServiceImpl implements ChatRecordService {
    private final ChatRecordMapper chatRecordMapper;
    private final ChatModel ChatModel;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;
    //消费者
    @KafkaListener(topics = "chat-topic")
    public void consumeChatMessage(String messageJson)  {
        try {
            Map<String,Object> meg = objectMapper.readValue(messageJson,Map.class);
            Long chatId = ((Number)meg.get("chatId")).longValue();
            Long userId = ((Number)meg.get("userId")).longValue();
            String question = (String) meg.get("question");
            String answer = (String) meg.get("answer");
            saveChatRecord(chatId,userId,question,answer);
            log.info("Kafka消息处理成功，chatId:{}", chatId);
        } catch (Exception e) {
            log.error("Kafka消息处理失败，消息内容：{}，原因：{}", messageJson, e.getMessage(), e);
        }
    }
    //生产者
    @Override
    public void sendChatRecordToKafka(Long chatId, Long userId, String question, String answer) {
        try {
        Map<String,Object> msgMap = new HashMap<>();
        msgMap.put("chatId",chatId);
        msgMap.put("userId",userId);
        msgMap.put("question",question);
        msgMap.put("answer",answer);
            String json = objectMapper.writeValueAsString(msgMap);
            kafkaTemplate.send("chat-topic",json);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
    //关键字获取
    @Override
    public String extractKeyword(String question,String answer){
        String prompt = """
            请从以下对话中提取1-3个关键字，用逗号分隔，不要其他文字：
            
            问题：%s
            答案：%s
            """;
        try {
            ChatMessage userMessage = UserMessage.from(
                    String.format(prompt,question,answer)
            );
            ChatResponse response = ChatModel.chat(userMessage);
            String keyword = response.aiMessage().text();
            return keyword.trim().replaceAll("[\\[\\]\"]", "");
        }catch (Exception e){
            e.printStackTrace();
            return "默认";
        }
}

@Override
public void saveChatRecord(Long chatId,Long userId,String question,String answer){
        //关键字提取
    String keyword = extractKeyword(question, answer);
    //创建ChatRecord对象
    ChatRecord record = new ChatRecord();
    record.setChatId(chatId);
    record.setUserId(userId);
    record.setKeyword(keyword);
    record.setCreateTime(LocalDateTime.now());

    chatRecordMapper.insert(record);
}
    @Override
    public List<ChatRecord> getChatMessages(Long chatId) {
        if (chatId == null) {
            return Collections.emptyList();
        }
        return getChatRecords(chatId, QueryType.BY_CHAT_ID);
    }



    public enum QueryType{
        BY_USER_ID, BY_CHAT_ID
}
public List<ChatRecord> getChatRecords(Long id,QueryType queryType){
        if (id == null){
            return Collections.emptyList();
        }
    return switch (queryType) {
        case BY_USER_ID -> chatRecordMapper.selectListByUserId(id);
        case BY_CHAT_ID -> chatRecordMapper.selectListByChatId(id);
        default -> Collections.emptyList();
    };
}

@Override
public Map<String,Object> getMyChatAnalysis(Long userId) {
    List<ChatRecord> records = (userId == null)
            ? chatRecordMapper.selectAllRecords()
            :chatRecordMapper.selectListByUserId(userId);

    if (records.isEmpty()) {
        return Collections.emptyMap();
    }

    Map<String, Integer> keywordCount = new HashMap<>();
    for (ChatRecord record : records) {
        String keyword = record.getKeyword();
        if (keyword != null && !keyword.isEmpty()) {
            String[] keywords = keyword.split(",");
            for (String k : keywords) {
                k = k.trim();
                keywordCount.put(k, keywordCount.getOrDefault(k, 0) + 1);
            }
        }
    }

    String prompt = """
            基于以下关键字统计，生成 ECharts图所需的数据，严格返回 JSON 格式：
            {
                "keywords": ["关键词1", "关键词2", ...],
                "keywordCount": [数量1, 数量2, ...],
                "topics": ["分类1", "分类2", ...],
                "topicPercent": [占比1, 占比2, ...]
            }
            关键字统计：%s
            """;
    try{
        ChatMessage userMessage = UserMessage.from(
                String.format(prompt,keywordCount)
        );
        ChatResponse response = ChatModel.chat(userMessage);
        String llmResult= response.aiMessage().text();
        return objectMapper.readValue(llmResult, Map.class);
    }catch (Exception e){
        Map<String, Object> errorMap = new HashMap<>();
        errorMap.put("error", "分析失败: " + e.getMessage());
        return errorMap;
    }
    }

}
