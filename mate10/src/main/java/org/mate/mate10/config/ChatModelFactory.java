package org.mate.mate10.config;

import dev.langchain4j.model.chat.ChatModel;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
public class ChatModelFactory {

//    private final ChatModel ollamaChatModel;
    private final ChatModel cloudChatModel;

    public ChatModelFactory(ChatModel ollamaChatModel, ChatModel cloudChatModel) {
//        this.ollamaChatModel = ollamaChatModel;
        this.cloudChatModel = cloudChatModel;
    }

    public ChatModel getChatModel() {
        // 这里可以根据需要添加逻辑，比如从配置读取当前模式
        // 默认返回 cloud（因为 active profile 已经决定了哪个 Bean 存在）
        return cloudChatModel;
    }
}