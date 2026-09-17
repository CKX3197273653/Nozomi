package org.mate.mate10.config;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mate.mate10.document.ChatMessageDocument;
import org.mate.mate10.service.MongoChatMessageService;
import org.mate.mate10.service.RagService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;


class ChatContextBuilderTest {

    private final MongoChatMessageService messageService = mock(MongoChatMessageService.class);
    private final RagService ragService = mock(RagService.class);
    private final ChatProperties chatProperties = new ChatProperties();

    private final ChatContextBuilder builder = new ChatContextBuilder(messageService, ragService,chatProperties);


    @Test
    @DisplayName("首轮对话：历史为空时，上下文 = SystemMessage + 当前问题")
    void build_firstTurn() {
        when(messageService.getRecentMessages(anyLong(), anyInt())).thenReturn(List.of());

        List<ChatMessage> messages = builder.build(1L, "你好", "general");

        assertThat(messages).hasSize(2);
        assertThat(messages.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(messages.get(1)).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) messages.get(1)).singleText()).isEqualTo("你好");
    }

    @Test
    @DisplayName("多轮对话：历史消息应拼在当前问题之前，且角色正确")
    void build_withHistory() {
        when(messageService.getRecentMessages(anyLong(), anyInt()))
                .thenReturn(List.of(
                        doc("User", "我叫小明"),
                        doc("ai", "你好，小明")
                ));

        List<ChatMessage> messages = builder.build(1L, "我叫什么名字？", "general");

        assertThat(messages).hasSize(4);
        assertThat(messages.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(messages.get(1)).isInstanceOf(UserMessage.class);
        assertThat(messages.get(2)).isInstanceOf(AiMessage.class);
        assertThat(messages.get(3)).isInstanceOf(UserMessage.class);
    }

    @Test
    @DisplayName("chatId 为 null：降级为单轮，不查历史")
    void build_nullChatId_shouldDegrade() {
        List<ChatMessage> messages = builder.build(null, "你好", "general");

        assertThat(messages).hasSize(2);
        verify(messageService, never()).getRecentMessages(anyLong(), anyInt());
    }

    @Test
    @DisplayName("空消息应被跳过，不污染上下文")
    void build_shouldSkipBlankMessages() {
        when(messageService.getRecentMessages(anyLong(), anyInt()))
                .thenReturn(List.of(doc("User", "   "), doc("ai", "")));

        List<ChatMessage> messages = builder.build(1L, "你好", "general");

        assertThat(messages).hasSize(2);
    }

    @Test
    @DisplayName("角色大小写兼容：USER / AI / ai 都应识别")
    void build_roleShouldBeCaseInsensitive() {
        when(messageService.getRecentMessages(anyLong(), anyInt()))
                .thenReturn(List.of(doc("USER", "问题"), doc("AI", "回答")));

        List<ChatMessage> messages = builder.build(1L, "新问题", "general");

        assertThat(messages.get(1)).isInstanceOf(UserMessage.class);
        assertThat(messages.get(2)).isInstanceOf(AiMessage.class);
    }

    // RAG

    @Test
    @DisplayName("qc 模式：应该注入知识库检索到的资料")
    void build_qcMode_shouldInjectRagContext() {
        when(messageService.getRecentMessages(anyLong(), anyInt())).thenReturn(List.of());
        when(ragService.retrieve("划痕标准")).thenReturn("划痕深度不超过 0.1mm");

        List<ChatMessage> messages = builder.build(1L, "划痕标准", "qc");

        // SystemMessage(角色) + SystemMessage(资料) + UserMessage(问题) = 3
        assertThat(messages).hasSize(3);
        assertThat(messages.get(1)).isInstanceOf(SystemMessage.class);
        assertThat(((SystemMessage) messages.get(1)).text()).contains("0.1mm");
    }

    @Test
    @DisplayName("qc 模式：知识库无结果时，不注入空资料")
    void build_qcMode_noResult_shouldNotInject() {
        when(messageService.getRecentMessages(anyLong(), anyInt())).thenReturn(List.of());
        when(ragService.retrieve(anyString())).thenReturn(null);

        List<ChatMessage> messages = builder.build(1L, "问题", "qc");

        assertThat(messages).hasSize(2);      // 只有 SystemMessage + 问题
    }

    @Test
    @DisplayName("general 模式：不应该调用知识库检索")
    void build_generalMode_shouldNotCallRag() {
        when(messageService.getRecentMessages(anyLong(), anyInt())).thenReturn(List.of());

        builder.build(1L, "问题", "general");

        verify(ragService, never()).retrieve(anyString());
    }

    private ChatMessageDocument doc(String role, String content) {
        ChatMessageDocument d = new ChatMessageDocument();
        d.setRole(role);
        d.setContent(content);
        return d;
    }
}