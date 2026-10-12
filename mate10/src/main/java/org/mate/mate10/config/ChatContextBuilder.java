package org.mate.mate10.config;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.extern.slf4j.Slf4j;
import org.mate.mate10.document.ChatMessageDocument;
import org.mate.mate10.document.ReportMemoryDocument;
import org.mate.mate10.service.MongoChatMessageService;
import org.mate.mate10.service.RagService;
import org.mate.mate10.service.ReportMemoryService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class ChatContextBuilder {
    private static final Pattern REPORT_NO = Pattern.compile("\\bR\\d{11}\\b");

    private final ChatProperties chatProperties;
    private final MongoChatMessageService messageService;
    private final RagService ragService;
    private final ReportMemoryService reportMemoryService;

    public ChatContextBuilder(MongoChatMessageService messageService,
                              RagService ragService,
                              ChatProperties chatProperties,
                              ReportMemoryService reportMemoryService) {
        this.messageService = messageService;
        this.ragService = ragService;
        this.chatProperties = chatProperties;
        this.reportMemoryService = reportMemoryService;
    }

    //聊天路径
    public List<ChatMessage> build(Long chatId, String question, String mode) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(chatProperties.getSystemPrompt()));
        if ("qc".equals(mode)) {
            String context = ragService.retrieve(question);
            if (context != null && !context.isBlank()) {
                messages.add(SystemMessage.from(
                        "以下是知识库检索到的参考资料,请优先依据它回答: \n\n" + context));
                log.info("注入知识库资料{}字符", context.length());
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

    // Agent 路径

    /**
     * 组装 Agent 的历史参考块（String，注入 SystemMessage 的 {{history}}）。
     *
     * <p>路由策略：</p>
     * <ul>
     *   <li>有编号 → 读【报告结论档案】（report_memory，团队共享的「当前认识」）</li>
     *   <li>无编号 → 读【本次会话上文】（chat_messages，按 chatId）</li>
     * </ul>
     *
     * <p>为什么不返回 List&lt;ChatMessage&gt;：Agent 的历史是【参考资料】而非【对话轮次】，
     * 语义上属于 system 上下文。</p>
     */
    public String buildAgentHistory(Long chatId, List<String> reportNos) {
        //有编号 → 报告结论档案（团队共享）
        if (reportNos != null && !reportNos.isEmpty()) {
            return formatConclusions(reportNos);
        }
        //无编号 → 本次会话上文
        if (chatId != null) {
            String body = joinWithBudget(
                    messageService.getRecentMessages(chatId, chatProperties.getAgentSessionLimit()),
                    chatProperties.getAgentHistoryChars());
            return body.isBlank() ? "" : "【本次会话的上文】\n" + body;
        }
        return "";
    }

    /**
     * 格式化【报告结论档案】。
     *
     * <p>⚠️ 这里【不能】复用 {@link #joinWithBudget}：那个方法依赖
     * {@code ChatMessageDocument.getContent()/getRole()}，而结论档案是
     * {@link ReportMemoryDocument}（只有 conclusion/advice/status/analyzedAt），
     * 数据形态完全不同。</p>
     */
    private String formatConclusions(List<String> reportNos) {
        List<ReportMemoryDocument> memories = reportMemoryService.getCurrentConclusions(reportNos);
        if (memories.isEmpty()) {
            return "【报告 " + String.join("/", reportNos) + " 暂无历史结论】请重新调查后给出结论。";
        }
        StringBuilder sb = new StringBuilder();
        for (ReportMemoryDocument m : memories) {
            sb.append("【报告 ").append(m.getReportNo()).append(" 的当前结论】\n");
            sb.append(m.getConclusion()).append("\n");
            if (m.getAdvice() != null && !m.getAdvice().isBlank()) {
                sb.append("改进建议：").append(m.getAdvice()).append("\n");
            }
            sb.append("（").append(m.getStatus())
                    .append(" · 分析于 ").append(m.getAnalyzedAt()).append("）\n\n");
        }
        return sb.toString().trim();
    }

    public List<String> extractReportNo(String question) {
        List<String> nos = new ArrayList<>();
        if (question == null) {
            return nos;
        }
        Matcher m = REPORT_NO.matcher(question);
        while (m.find()) {
            nos.add(m.group());
        }
        return nos;
    }

    // 会话历史格式化（Agent 会话分支专用）

    private String joinWithBudget(List<ChatMessageDocument> docs, int budget) {
        List<String> picked = new ArrayList<>();
        int used = 0;
        for (int i = docs.size() - 1; i >= 0; i--) {            // 从新到旧
            String content = docs.get(i).getContent();
            if (content == null || content.isBlank()) continue;
            if (used + content.length() > budget) break;        // 预算不够丢最旧的
            used += content.length();
            picked.add(prefix(docs.get(i)) + content);
        }
        Collections.reverse(picked);                            // 恢复时间正序
        log.info("组装 Agent 历史（会话）：{} 条，约 {} 字符", picked.size(), used);
        return String.join("\n\n", picked);
    }

    private String prefix(ChatMessageDocument doc) {
        return isAi(doc.getRole()) ? "[助手]" : "[用户]";
    }

    //共用小工具

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