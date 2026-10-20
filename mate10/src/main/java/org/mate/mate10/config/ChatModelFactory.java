package org.mate.mate10.config;


import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
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
}