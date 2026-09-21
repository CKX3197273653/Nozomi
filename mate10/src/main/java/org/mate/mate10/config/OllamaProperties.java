package org.mate.mate10.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "ollama") // 映射 ollama 前缀的配置
public class OllamaProperties {

    private String baseUrl = "http://localhost:11434";

    private String defaultModel = "deepseek-r1:latest";

    private String embeddingModel = "all-minilm:latest";

    private String agentModel = "llama3.1:latest";

}
