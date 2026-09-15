package org.mate.mate10.config;


import dev.langchain4j.model.chat.ChatModel;
import org.springframework.stereotype.Component;

@Component
public class ChatModelFactory {

    private final ChatModel chatModel;

    public ChatModelFactory( ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public ChatModel getChatModel() {
        return chatModel;
    }
}