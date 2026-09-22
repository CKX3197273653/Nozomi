package org.mate.mate10.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 事件发送工具 —— 无状态。
 *
 * <p>emitter 由调用方通过参数传入（工厂里用闭包捕获），
 * 因此不依赖 ThreadLocal，跨线程安全。</p>
 */
@Slf4j
public final class AgentTraceContext {

    private AgentTraceContext() {
        // 工具类，禁止实例化
    }

    /** JSON 序列化器（Jackson 2，与项目其他地方保持一致） */
    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * 发送一个 SSE 事件。
     *
     * <p>emitter 为 null（非流式场景）或前端已断开时静默跳过 ——
     * 绝不能因此中断 Agent 执行。</p>
     */
    public static void send(SseEmitter emitter, String event, Object data) {
        if (emitter == null) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name(event).data(JSON.writeValueAsString(data)));
        } catch (Exception e) {
            // 前端断开是常见情况（切页面/关标签页），不能影响 Agent 执行，
            // 也不该用 error 级别刷日志
            log.debug("SSE 事件发送失败（前端可能已断开）：{}", e.getMessage());
        }
    }
}
