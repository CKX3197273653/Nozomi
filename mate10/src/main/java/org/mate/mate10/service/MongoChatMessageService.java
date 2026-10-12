package org.mate.mate10.service;

import org.mate.mate10.document.ChatMessageDocument;

import java.util.List;

public interface MongoChatMessageService {
    void saveUserMessage(Long chatId, Long userId, String content);
    void saveAiMessage(Long chatId, Long userId, String content);
    void saveUserMessage(Long chatId, Long userId, String content, String reportNo);
    void saveAiMessage(Long chatId, Long userId, String content, String reportNo);

    List<ChatMessageDocument> getMessagesByChatId(Long chatId);
    List<ChatMessageDocument> getMessageByUserId(Long userId);

    List<ChatMessageDocument> getMessageByChatIdAndRole(Long chatId, String role);

    List<ChatMessageDocument> getRecentMessages(Long chatId, int maxHistoryMessages);

    //按报告编号取历史讨论记录,当无调用方,agent的档案已改走,这个方法预留为审计视图+报告讨论历史,回答则是  这份报告什么时候讨论过
    List<ChatMessageDocument> getRecentMessagesByReportNo(String reportNo, int limit);
}
