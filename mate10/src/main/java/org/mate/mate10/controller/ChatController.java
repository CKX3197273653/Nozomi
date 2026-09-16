package org.mate.mate10.controller;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import lombok.extern.slf4j.Slf4j;
import org.mate.mate10.common.Result;
import org.mate.mate10.config.ChatContextBuilder;
import org.mate.mate10.config.ChatModelFactory;
import org.mate.mate10.dto.ChatRequest;
import org.mate.mate10.entity.Chat;
import org.mate.mate10.service.ChatRecordService;
import org.mate.mate10.service.ChatService;
import org.mate.mate10.service.MongoChatMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;

@Slf4j
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
    @Autowired
    private ThreadPoolTaskExecutor streamExecutor;
    @Autowired
    private ChatContextBuilder chatContextBuilder;


    @PostMapping("/ai/chat")
    public Result<Chat> createChat(@RequestBody Chat chat){
        return Result.success(chatService.createChat(chat.getUserId(), chat.getTitle()));
    }


    @PostMapping("/ai/chatRecord")
    public Result<String> chat(@RequestBody ChatRequest request){
        String message = request.getMessage();
        String mode = request.getMode();
        Long userId = request.getUserId();
        Long chatId = request.getChatId();

        // 组装上下文
        List<ChatMessage> messages = chatContextBuilder.build(chatId, message, mode);

        if (userId != null && chatId != null) {
            mongoChatMessageService.saveUserMessage(chatId, userId, message);
        }

        ChatResponse response = chatModelFactory.getChatModel().chat(messages);
        String answer = response.aiMessage().text();

        if (userId != null && chatId != null) {
            mongoChatMessageService.saveAiMessage(chatId, userId, answer);
            recordService.sendChatRecordToKafka(chatId, userId, message, answer);
        }

        return Result.success(answer);
    }
                                                        //前端读流
    @PostMapping(value = "/ai/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody ChatRequest request){
        //5分钟  不设置时间,连接会一直保持,一直占用资源
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L);
        String message = request.getMessage();
        String mode = request.getMode();
        Long userId = request.getUserId();
        Long chatId = request.getChatId();

        //用户信息
        if (userId != null && chatId != null) {
            mongoChatMessageService.saveUserMessage(chatId, userId, message);
        }
        //放进独立线程执行,方法返回 emitter
        streamExecutor.execute(() -> {
            StringBuilder full = new  StringBuilder();
            try {
                List<ChatMessage> messages  = chatContextBuilder.build(chatId,message,mode);
                chatModelFactory.getStreamingChatModel().chat(messages,
                        new StreamingChatResponseHandler() {

                    @Override
                    public void onPartialResponse(String token){
                        full.append(token);
                        try {
                            emitter.send(SseEmitter.event().data(token));
                        }catch(IOException e){
                            log.warn( "客户断开连接" +e.getMessage());
                            emitter.completeWithError(e);
                        }
                    }
                            @Override
                            public void onCompleteResponse(ChatResponse completeResponse) {
                                String answer = full.toString();
                                try {
                                    if (userId != null && chatId != null) {
                                        mongoChatMessageService.saveAiMessage(chatId, userId, answer);
                                        recordService.sendChatRecordToKafka(chatId, userId, message, answer);
                                    }
                                } finally {
                                    // 无论保存/Kafka 是否失败，都必须发结束标记并关闭连接
                                    try {
                                        emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                                    } catch (Exception ignored) {
                                    }
                                    emitter.complete();
                                }
                            }
                    @Override
                    public void onError(Throwable error) {
                        log.error( "流式生成失败" + error.getMessage());
                        emitter.completeWithError(error);
                    }
                });
            } catch (Exception e) {
                log.error("流式端点异常" + e);
                emitter.completeWithError(e);
            }
        });
        return emitter; //保持连接
    }

}
