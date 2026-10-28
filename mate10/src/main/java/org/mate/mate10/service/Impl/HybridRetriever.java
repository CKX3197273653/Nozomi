package org.mate.mate10.service.Impl;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.mate.mate10.config.RagProperties;
import org.mate.mate10.service.Reranker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Supplier;

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

    @Autowired(required = false)
    private Reranker reranker;

    private final RagProperties ragProperties;

    public HybridRetriever(RagProperties ragProperties) {
        this.ragProperties = ragProperties;
    }

    //返回融合后的chunk
    public List<String> retrieve(String question) {
        int topK = ragProperties.getTopk();
        RagProperties.Rerank rerankCfg = ragProperties.getRerank();
        boolean rerankOn = reranker != null;

        int recallK = rerankOn ? topK * rerankCfg.getRecallFactor() : topK;

        //向量检索
        List<String> vectorHits = vectorSearch(question, recallK);

        //混合检索关闭 → 直接返回向量结果
        if (!ragProperties.isHybridEnabled()) {
            return rerankOn ? safeRerank(question, vectorHits, topK, () -> limit(vectorHits, topK)) : vectorHits;
        }
        //关键词检索
        List<String> keywordHits = luceneIndexer.search(question, recallK);
        //合并去重
        List<String> candidates = dedup(vectorHits, keywordHits);
        //重排
        if (rerankOn) {
            List<String> ranked = safeRerank(question, candidates, topK, () -> rrfFuse(vectorHits, keywordHits));
            log.info("[Hybrid] 召回 {} 条（向量 {} + 关键词 {}）→ 去重 {} 条 → 重排取 {} 条",
                    vectorHits.size() + keywordHits.size(), vectorHits.size(),
                    keywordHits.size(), candidates.size(), ranked.size());
            return ranked;
        }

        //RRF 融合
        return rrfFuse(vectorHits, keywordHits);

    }

    /*
    * 精排兜底包装
    * reranker整条链路里唯一可能在运行期间整段抛出异常的环节
    * */
    private List<String> safeRerank(String question, List<String> candidates, int topK,
                                    Supplier<List<String>> fallback) {
        try {
            List<String> ranked = reranker.rerank(question, candidates, topK);
            if (ranked == null || ranked.isEmpty()) {
                log.warn("[Hybrid] 重排返回空（候选 {} 条），回退", candidates.size());
                return fallback.get();
            }
            return ranked;
        } catch (Exception e) {
            log.warn("[Hybrid] 重排异常，回退（候选 {} 条）：{}", candidates.size(), e.getMessage());
            return fallback.get();
        }
    }

    //截断到 topK（混合检索关闭且精排失败时的回退物）
    private List<String> limit(List<String> list, int topK) {
        if (list.size() <= topK) {
            return list;
        }
        return new ArrayList<>(list.subList(0, topK));
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

    //按文本去重，保留首次出现顺序
    private List<String> dedup(List<String>... lists) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (List<String> list : lists) {
            seen.addAll(list);
        }
        return new ArrayList<>(seen);
    }

    //RRF 融合：score(d) = Σ 1 / (k + rank_i(d))
    @SafeVarargs
    private List<String> rrfFuse(List<String>... lists) {
        Map<String, Double> score = new LinkedHashMap<>();
        for (List<String> list : lists) {
            for (int i = 0; i < list.size(); i++) {
                String text = list.get(i);
                score.merge(text, 1.0 / (RRF_K + i + 1), Double::sum);
            }
        }
        return score.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(ragProperties.getTopk())
                .map(Map.Entry::getKey)
                .toList();
    }
}