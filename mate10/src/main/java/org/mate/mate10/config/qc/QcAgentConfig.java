package org.mate.mate10.config.qc;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.mate.mate10.agent.*;
import org.mate.mate10.config.CloudAiProperties;
import org.mate.mate10.config.OllamaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Configuration
public class QcAgentConfig {

    private final OllamaProperties ollamaProperties;
    private final CloudAiProperties cloudAiProperties;

    public QcAgentConfig(OllamaProperties ollamaProperties,
                         CloudAiProperties cloudAiProperties) {
        this.ollamaProperties = ollamaProperties;
        this.cloudAiProperties = cloudAiProperties;
    }

    @Bean
    @Profile("ollama")
    public AgentFactory ollamaAgentFactory(QcAgentTools tools) {
        ChatModel model = OllamaChatModel.builder()
                .baseUrl(ollamaProperties.getBaseUrl())
                .modelName(ollamaProperties.getAgentModel())
                .listeners(List.of(new TokenUsageListener()))
                .build();
        return emitter -> buildAgent(model, tools, emitter);
    }

    @Bean
    @Profile("cloud")
    public AgentFactory cloudAgentFactory(QcAgentTools tools) {
        ChatModel model = OpenAiChatModel.builder()
                .baseUrl(cloudAiProperties.getChat().getBaseUrl())
                .apiKey(cloudAiProperties.getChat().getApiKey())
                .modelName(cloudAiProperties.getChat().getModel())
                .listeners(List.of(new TokenUsageListener()))
                .build();
        return emitter -> buildAgent(model, tools, emitter);
    }

    //每次请求调用一次, 用两个官方钩子做轨迹点
    private QcAnalysisAgent buildAgent(ChatModel model, QcAgentTools tools, SseEmitter emitter) {
        return AiServices.builder(QcAnalysisAgent.class)
                .chatModel(model)
                .tools(tools)
                .beforeToolExecution(b -> {
                    // 用 LinkedHashMap 而不是 Map.of —— arguments() 可能为 null
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("name", b.request().name());
                    payload.put("args", b.request().arguments());
                    payload.put("status", "running");
                    AgentTraceContext.send(emitter, "tool", payload);
                })
                .registerListener(new AgentTraceListener(emitter))
                .build();
    }
}
