package org.mate.mate10.controller;
import lombok.RequiredArgsConstructor;
import org.mate.mate10.document.ChatMessageDocument;
import org.mate.mate10.dto.ChatMessageResponse;
import org.mate.mate10.service.MongoChatMessageService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.stream.Collectors;
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class MongoChatController {
    private final MongoChatMessageService mongoChatMessageService;
    @GetMapping("/messages/{chatId}")
    public List<ChatMessageResponse> getMessagesByChatId(@PathVariable Long chatId) {
        List<ChatMessageDocument> documents = mongoChatMessageService.getMessagesByChatId(chatId);
        return documents.stream().map(this::convertToResponse).collect(Collectors.toList());}
    @GetMapping("/user/{userId}/messages")
    public List<ChatMessageResponse> getMessagesByUserId(@PathVariable Long userId) {
        List<ChatMessageDocument> documents = mongoChatMessageService.getMessageByUserId(userId);
        return documents.stream().map(this::convertToResponse).collect(Collectors.toList());}
    @GetMapping("/messages/{chatId}/{role}")
    public List<ChatMessageResponse> getMessagesByChatIdAndRole(
            @PathVariable Long chatId,
            @PathVariable String role) {
        List<ChatMessageDocument> documents = mongoChatMessageService.getMessageByChatIdAndRole(chatId, role);
        return documents.stream().map(this::convertToResponse).collect(Collectors.toList());}
    private ChatMessageResponse convertToResponse(ChatMessageDocument document) {
        ChatMessageResponse response = new ChatMessageResponse();
        response.setId(document.getId());
        response.setUserId(document.getUserId());
        response.setChatId(document.getChatId());
        response.setRole(document.getRole());
        response.setContent(document.getContent());
        response.setCreateTime(document.getCreateTime());
        return response;}}
