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
    //索引目录
    private String luceneDir = "./data/lucene-index";
    //混合检索
    private boolean hybridEnabled = true;
    //重排
    private Rerank rerank = new Rerank();

    @Data
    public static class Rerank {
        private boolean enabled = false;  //重排
        private String modelPath = "";   //ONNX 模型文件路径
        private String tokenizerPath = "";  //tokenizer.json 路径
        private int recallFactor = 3;  //召回倍数,先召回 topK * recallFactor条件候选,再重排取 topK
        private int maxLength = 510;   //最大token长度  (bge-reranker-base 默认510)
    }
}
