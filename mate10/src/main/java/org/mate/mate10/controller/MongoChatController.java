package org.mate.mate10.controller;

import lombok.RequiredArgsConstructor;
import org.mate.mate10.common.CurrentUserProvider;
import org.mate.mate10.common.Result;
import org.mate.mate10.document.ChatMessageDocument;
import org.mate.mate10.dto.ChatMessageResponse;
import org.mate.mate10.entity.Chat;
import org.mate.mate10.service.ChatService;
import org.mate.mate10.service.MongoChatMessageService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class MongoChatController {
    private final MongoChatMessageService mongoChatMessageService;
    private final ChatService chatService;
    private final CurrentUserProvider currentUser;

    //按会话查消息
    @GetMapping("/messages/{chatId}")
    public Result<List<ChatMessageResponse>> getMessagesByChatId(@PathVariable Long chatId) {
        if (!canAccessChat(chatId)) {
            return Result.error(403, "无权访问该会话");
        }
        return Result.success(toResponses(mongoChatMessageService.getMessagesByChatId(chatId)));
    }

    //按用户查消息
    @GetMapping("/user/{userId}/messages")
    public Result<List<ChatMessageResponse>> getMessagesByUserId(@PathVariable Long userId) {
        Long me = currentUser.userIdOrNull();
        if (me == null) {
            return Result.error(401, "未登录");
        }
        if (!currentUser.isAdmin() && !me.equals(userId)) {
            return Result.error(403, "只能查看自己的消息");
        }
        return Result.success(toResponses(mongoChatMessageService.getMessageByUserId(userId)));
    }

    //会话+角色查消息
    @GetMapping("/messages/{chatId}/{role}")
    public Result<List<ChatMessageResponse>> getMessagesByChatIdAndRole(
            @PathVariable Long chatId,
            @PathVariable String role) {
        if (!canAccessChat(chatId)) {
            return Result.error(403, "无权访问该会话");
        }
        return Result.success(toResponses(
                mongoChatMessageService.getMessageByChatIdAndRole(chatId, role)));
    }

    private boolean canAccessChat(Long chatId) {
        if (currentUser.isAdmin()) {
            return true;
        }
        Long me = currentUser.userIdOrNull();
        if (me == null) {
            return false;
        }
        Chat chat = chatService.getById(chatId);
        return chat != null && me.equals(chat.getUserId());
    }

    private List<ChatMessageResponse> toResponses(List<ChatMessageDocument> docs) {
        return docs.stream().map(this::convertToResponse).toList();
    }

    private ChatMessageResponse convertToResponse(ChatMessageDocument document) {
        ChatMessageResponse response = new ChatMessageResponse();
        response.setId(document.getId());
        response.setUserId(document.getUserId());
        response.setChatId(document.getChatId());
        response.setRole(document.getRole());
        response.setContent(document.getContent());
        response.setCreateTime(document.getCreateTime());
        return response;
    }
}
