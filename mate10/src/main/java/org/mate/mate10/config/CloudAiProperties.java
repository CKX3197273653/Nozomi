package org.mate.mate10.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "cloud-ai")
public class CloudAiProperties {
    private Endpoint chat = new Endpoint();
    private Endpoint embedding = new Endpoint();

    @Data
    public static class Endpoint {
        private String baseUrl;
        private String apiKey;
        private String model;
        private Integer dimensions = 2048;  // 默认值，与智谱 embedding-3 输出一致
    }
}
