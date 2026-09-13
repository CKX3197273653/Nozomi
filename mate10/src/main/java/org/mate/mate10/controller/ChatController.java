package org.mate.mate10.controller;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.data.message.ChatMessage;
import org.mate.mate10.config.ChatModelFactory;
import org.mate.mate10.dto.ChatRequest;
import org.mate.mate10.entity.Chat;
import org.mate.mate10.service.ChatRecordService;
import org.mate.mate10.service.ChatService;
import org.mate.mate10.service.MongoChatMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChatController {
    @Autowired
    private ChatModelFactory chatModelFactory;
    @Autowired
    private ChatService chatService;

    @Autowired
    private MongoChatMessageService mongoChatMessageService;

    @Autowired
    private ChatRecordService recordService;


    @PostMapping("/ai/chat")
    public Chat createChat(@RequestBody Chat chat){
        return chatService.createChat(chat.getUserId(), chat.getTitle());
    }


    @PostMapping("/ai/chatRecord")
    public String chat(@RequestBody ChatRequest request){
        String message = request.getMessage();
        Long userId = request.getUserId();
        Long chatId = request.getChatId();

        // 保存用户消息到MongoDB
        if (userId != null && chatId != null) {
            mongoChatMessageService.saveUserMessage(chatId, userId, message);
        }

        ChatMessage userMessage = UserMessage.from(message);
        ChatResponse response = chatModelFactory.getChatModel().chat(userMessage);
        String answer = response.aiMessage().text();

        // 保存助手消息到MongoDB
        if (userId != null && chatId != null) {
            mongoChatMessageService.saveAiMessage(chatId, userId, answer);
        }

        //发送Kafka
        if (userId != null && chatId != null){
            recordService.sendChatRecordToKafka(chatId,userId,message,answer);
        }

        return answer;
    }

}
