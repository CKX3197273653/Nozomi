package org.mate.mate10.config;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.extern.slf4j.Slf4j;
import org.mate.mate10.document.ChatMessageDocument;
import org.mate.mate10.service.MongoChatMessageService;
import org.mate.mate10.service.RagService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
@Slf4j
public class ChatContextBuilder {
    private final ChatProperties chatProperties;
    private final MongoChatMessageService messageService;
    private final RagService ragService;

    public ChatContextBuilder(MongoChatMessageService messageService, RagService ragService,  ChatProperties chatProperties) {
        this.messageService = messageService;
        this.ragService = ragService;
        this.chatProperties = chatProperties;
    }

    public List<ChatMessage> build(Long chatId, String question,String mode) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(chatProperties.getSystemPrompt()));
        if ("qc".equals(mode)) {
            String context = ragService.retrieve(question);
            if (context != null && !context.isBlank()) {
                messages.add(SystemMessage.from(
                        "以下是知识库检索到的参考资料,请优先依据它回答: \n\n"+context));
                log.info("注入知识库资料{}字符",context.length());
            }
        }

        messages.addAll(recentHistory(chatId));
        messages.add(UserMessage.from(question));
        return messages;
    }

    private List<ChatMessage> recentHistory(Long chatId) {
        if (chatId == null) {
            return List.of();
        }
        List<ChatMessageDocument> history =
                messageService.getRecentMessages(chatId, chatProperties.getMaxHistoryMessages());

        List<ChatMessage> picked = new ArrayList<>();
        int used = 0;
        for (int i = history.size() - 1; i >= 0; i--) {
            String content = history.get(i).getContent();
            if (content == null || content.isBlank()) {
                continue;
            }
            if (used + content.length() > chatProperties.getMaxHistoryChars()) {
                break;
            }
            used += content.length();
            picked.add(toMessage(history.get(i)));
        }
        Collections.reverse(picked);
        log.info("组装上下文：历史 {} 条，约 {} 字符", picked.size(), used);
        return picked;
    }

    private ChatMessage toMessage(ChatMessageDocument doc) {
        return isAi(doc.getRole())
                ? AiMessage.from(doc.getContent())
                : UserMessage.from(doc.getContent());
    }

    private boolean isAi(String role) {
        return role != null
                && (role.equalsIgnoreCase("ai") || role.equalsIgnoreCase("assistant"));
    }
}
