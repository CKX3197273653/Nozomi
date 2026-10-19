package org.mate.mate10.config;

import io.milvus.client.MilvusClient;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Slf4j
@Configuration
public class MilvusConfig {

    @Value("${milvus.host}")
    private String host;

    @Value("${milvus.port}")
    private Integer port;

    @Value("${milvus.username:}")
    private String username;

    @Value("${milvus.password:}")
    private String password;

    @Bean(destroyMethod = "close")
    public MilvusClient milvusClient() {
        ConnectParam.Builder builder = ConnectParam.newBuilder()
                .withHost(host)
                .withPort(port);

        if (StringUtils.hasText(username) && StringUtils.hasText(password)) {
            log.info("[Milvus] 使用鉴权方式连接 {}:{}, user={}", host, port, username);
            builder.withAuthorization(username, password);
        } else {
            log.info("[Milvus] 未配置凭据,以匿名方式连接 {}:{}", host, port);
        }

        return new MilvusServiceClient(builder.build());
    }
}