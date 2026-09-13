package org.mate.mate10.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "milvus") // 映射 milvus 前缀的配置
public class MilvusProperties {
    private Integer  port=19530;
    private String host = "127.0.0.1";
    private String collectionName = "rag_documents";
    private Integer dimension = 2048;
    private String username = "root";
    private String password = "root";
}
