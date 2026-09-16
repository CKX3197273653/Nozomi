package org.mate.mate10.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@EnableConfigurationProperties({OllamaProperties.class, CloudAiProperties.class, MilvusProperties.class})
public class LangChain4jConfig {

    private final OllamaProperties ollamaProperties;
    private final CloudAiProperties cloudAiProperties;
    private final MilvusProperties milvusProperties;

    public LangChain4jConfig(OllamaProperties o, CloudAiProperties c, MilvusProperties m) {
        this.ollamaProperties = o;
        this.cloudAiProperties = c;
        this.milvusProperties = m;
    }

    //对话模型

    @Bean
    @Profile("ollama")
    public ChatModel ollamaChatModel() {
        return OllamaChatModel.builder()
                .baseUrl(ollamaProperties.getBaseUrl())
                .modelName(ollamaProperties.getDefaultModel())
                .build();
    }

    @Bean
    @Profile("cloud")
    public ChatModel cloudChatModel() {
        return OpenAiChatModel.builder()
                .baseUrl(cloudAiProperties.getChat().getBaseUrl())
                .apiKey(cloudAiProperties.getChat().getApiKey())
                .modelName(cloudAiProperties.getChat().getModel())
                .build();
    }

    // Embedding 模型

    @Bean
    @Profile("ollama")
    public EmbeddingModel ollamaEmbeddingModel() {
        return OllamaEmbeddingModel.builder()
                .baseUrl(ollamaProperties.getBaseUrl())
                .modelName(ollamaProperties.getEmbeddingModel())
                .build();
    }

    @Bean
    @Profile("cloud")
    public EmbeddingModel cloudEmbeddingModel() {
        return OpenAiEmbeddingModel.builder()
                .baseUrl(cloudAiProperties.getEmbedding().getBaseUrl())
                .apiKey(cloudAiProperties.getEmbedding().getApiKey())
                .modelName(cloudAiProperties.getEmbedding().getModel())
                .dimensions(cloudAiProperties.getEmbedding().getDimensions())
                .build();
    }

    //向量库

    @Bean
    public MilvusEmbeddingStore milvusEmbeddingStore() {
        return MilvusEmbeddingStore.builder()
                .uri(String.format("http://%s:%d", milvusProperties.getHost(), milvusProperties.getPort()))
                .collectionName(milvusProperties.getCollectionName())
                .dimension(milvusProperties.getDimension())
                .username(milvusProperties.getUsername())
                .password(milvusProperties.getPassword())
                .build();
    }

    //流式对话模型
    @Bean
    @Profile("ollama")
    public StreamingChatModel ollamaStreamingChatModel() {
        return OllamaStreamingChatModel.builder()
                .baseUrl(ollamaProperties.getBaseUrl())
                .modelName(ollamaProperties.getDefaultModel())
                .build();
    }
    @Bean
    @Profile("cloud")
    public StreamingChatModel cloudStreamingChatModel() {
        return OpenAiStreamingChatModel.builder()
                .baseUrl(cloudAiProperties.getChat().getBaseUrl())
                .apiKey(cloudAiProperties.getChat().getApiKey())
                .modelName(cloudAiProperties.getChat().getModel())
                .build();
    }
}