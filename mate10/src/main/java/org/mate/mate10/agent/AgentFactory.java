package org.mate.mate10.agent;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

//每次请求创建一个agent实例
//工具执行要把轨迹事件推给本次请求的前端连接 用闭包捕获emitter
@FunctionalInterface
public interface AgentFactory {

    /**
     * @param emitter 本次请求的 SSE 连接；非流式场景传 {@code null}
     */
    QcAnalysisAgent create(SseEmitter emitter);
}
