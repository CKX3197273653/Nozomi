package org.mate.mate10.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.mate.mate10.agent.QcAgentTools;
import org.mate.mate10.agent.QcAnalysisAgent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

//质检 Agent 装配
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
    public QcAnalysisAgent ollamaQcAnalysisAgent(QcAgentTools tools) {
        ChatModel model = OllamaChatModel.builder()
                .baseUrl(ollamaProperties.getBaseUrl())
                .modelName(ollamaProperties.getAgentModel())
                .build();

        return AiServices.builder(QcAnalysisAgent.class)
                .chatModel(model)
                .tools(tools)
                .build();
    }

    @Bean
    @Profile("cloud")
    public QcAnalysisAgent cloudQcAnalysisAgent(QcAgentTools tools) {
        ChatModel model = OpenAiChatModel.builder()
                .baseUrl(cloudAiProperties.getChat().getBaseUrl())
                .apiKey(cloudAiProperties.getChat().getApiKey())
                .modelName(cloudAiProperties.getChat().getModel())
                .build();

        return AiServices.builder(QcAnalysisAgent.class)
                .chatModel(model)
                .tools(tools)
                .build();
    }
}
