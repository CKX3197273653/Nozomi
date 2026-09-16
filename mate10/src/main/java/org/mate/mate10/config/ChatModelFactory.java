package org.mate.mate10.config;


import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Component
public class ChatModelFactory {

    private final ChatModel chatModel;
    private final StreamingChatModel streamingChatModel;

    public ChatModelFactory( ChatModel chatModel , StreamingChatModel streamingChatModel) {
        this.chatModel = chatModel;
        this.streamingChatModel = streamingChatModel;
    }

    public ChatModel getChatModel() {
        return chatModel;
    }

    public StreamingChatModel getStreamingChatModel() {
        return streamingChatModel;
    }

    @Bean
    //org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor，Spring 会管理它的生命周期。
    public ThreadPoolTaskExecutor streamExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("chat-stream-");
        executor.initialize();
        return executor;
    }

}