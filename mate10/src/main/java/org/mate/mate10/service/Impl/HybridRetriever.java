package org.mate.mate10.service.Impl;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.mate.mate10.config.RagProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class HybridRetriever {

    private static final int RRF_K = 60;

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private EmbeddingStore<TextSegment> embeddingStore;

    @Autowired
    private LuceneIndexer luceneIndexer;

    private final RagProperties ragProperties;

    public HybridRetriever(RagProperties ragProperties) {
        this.ragProperties = ragProperties;
    }

    //返回融合后的chunk
    public List<String> retrieve(String question) {
        int topK = ragProperties.getTopk();

        //向量检索
        List<String> vectorHits = vectorSearch(question, topK);

        //混合检索关闭 → 直接返回向量结果
        if (!ragProperties.isHybridEnabled()) {
            return vectorHits;
        }

        //关键词检索
        List<String> keywordHits = luceneIndexer.search(question, topK);

        //RRF 融合
        List<String> fused = rrfFuse(vectorHits, keywordHits);
        log.info("[Hybrid] 向量 {} 条 + 关键词 {} 条 → 融合 {} 条",
                vectorHits.size(), keywordHits.size(), fused.size());
        return fused;
    }

    private List<String> vectorSearch(String question, int topK) {
        try {
            Embedding q = embeddingModel.embed(question).content();
            EmbeddingSearchRequest req = EmbeddingSearchRequest.builder()
                    .queryEmbedding(q)
                    .maxResults(topK)
                    .build();
            return embeddingStore.search(req).matches().stream()
                    .map(m -> m.embedded().text())
                    .toList();
        } catch (Exception e) {
            log.warn("[Hybrid] 向量检索失败：{}", e.getMessage());
            return List.of();
        }
    }
    //RRF 融合：score(d) = Σ 1 / (k + rank_i(d))
    @SafeVarargs
    private List<String> rrfFuse(List<String>... lists) {
        Map<String, Double> score = new LinkedHashMap<>();
        Map<String, String> textOf = new HashMap<>();

        for (List<String> list : lists) {
            for (int i = 0; i < list.size(); i++) {
                String text = list.get(i);
                String key = Integer.toHexString(text.hashCode());
                textOf.put(key, text);
                // 排名从 1 开始：i=0 → rank 1
                score.merge(key, 1.0 / (RRF_K + i + 1), Double::sum);
            }
        }

        return score.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(ragProperties.getTopk())
                .map(e -> textOf.get(e.getKey()))
                .toList();
    }
}