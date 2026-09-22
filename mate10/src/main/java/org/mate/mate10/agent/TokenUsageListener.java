package org.mate.mate10.agent;

import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.output.TokenUsage;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class TokenUsageListener implements ChatModelListener {

    private final AtomicInteger inputTokens = new AtomicInteger();
    private final AtomicInteger outputTokens = new AtomicInteger();

    @Override
    public void onResponse(ChatModelResponseContext context) {
        TokenUsage usage = context.chatResponse().tokenUsage();
        if (usage == null) {
            return;
        }
        if (usage.inputTokenCount() != null) {
            inputTokens.addAndGet(usage.inputTokenCount());
        }
        if (usage.outputTokenCount() != null) {
            outputTokens.addAndGet(usage.outputTokenCount());
        }
        log.info("[Token] input={}, output={}, total={}",
                usage.inputTokenCount(), usage.outputTokenCount(), usage.totalTokenCount());
    }
    public int total() {
        return inputTokens.get() + outputTokens.get();
    }

    public int input() {
        return inputTokens.get();
    }

    public int output() {
        return outputTokens.get();
    }

}
