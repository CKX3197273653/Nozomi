package org.mate.mate10.service;

import org.mate.mate10.document.ChatMessageDocument;

import java.util.List;

public interface MongoChatMessageService {
    void saveUserMessage(Long chatId, Long userId, String content);

    void saveAiMessage(Long chatId, Long userId, String content);

    List<ChatMessageDocument> getMessagesByChatId(Long chatId);
    List<ChatMessageDocument> getMessageByUserId(Long userId);

    List<ChatMessageDocument> getMessageByChatIdAndRole(Long chatId, String role);
}
