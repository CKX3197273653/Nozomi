package org.mate.mate10.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.rag")
public class RagProperties {
    //检索返回块数
    private int topk = 10;
    //单个分块最大字符数
    private int maxChunkChars = 400;
    //分块目标大小
    private int chunkSize = 512;
    //相邻分块重叠字符数
    private  int chunkOverlap = 128;
    //是否按照标题分块
    private boolean useHeadingSplit = true;
}
