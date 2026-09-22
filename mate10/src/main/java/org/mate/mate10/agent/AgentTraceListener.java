package org.mate.mate10.agent;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.observability.api.event.ToolExecutedEvent;
import dev.langchain4j.observability.api.listener.ToolExecutedEventListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.Map;

//每个agent实例有一个listener 且持有请求的 emitter,线程安全不依赖 invocationContext框架内部机制
@Slf4j
public class AgentTraceListener implements ToolExecutedEventListener {

    //返回过长截断
    private static final int MAX_RESULT_CHARS = 800;

    private final SseEmitter emitter;

    public AgentTraceListener(SseEmitter emitter) {
        this.emitter = emitter;
    }

    @Override
    public void onEvent(ToolExecutedEvent event) {
        ToolExecutionRequest request = event.request();
        log.info("[Tool] {} 完成", request.name());

        // 用 LinkedHashMap 而不是 Map.of —— 值可能为 null
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", request.name());
        payload.put("status", "done");
        payload.put("result", truncate(event.resultText()));

        AgentTraceContext.send(emitter, "tool", payload);
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > MAX_RESULT_CHARS
                ? s.substring(0, MAX_RESULT_CHARS) + "\n...（已截断）"
                : s;
    }
}
