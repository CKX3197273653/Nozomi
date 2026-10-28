package org.mate.mate10.service.Impl;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mate.mate10.config.RagProperties;
import org.mate.mate10.service.Reranker;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HybridRetrieverTest {
    private EmbeddingModel embeddingModel;
    private EmbeddingStore<TextSegment> embeddingStore;
    private LuceneIndexer luceneIndexer;
    private Reranker reranker;
    private RagProperties ragProperties;
    private HybridRetriever retriever;

    @BeforeEach
    void setUp() {
        embeddingModel = mock(EmbeddingModel.class);
        embeddingStore = mock(EmbeddingStore.class);
        luceneIndexer = mock(LuceneIndexer.class);
        reranker = mock(Reranker.class);

        ragProperties = new RagProperties();
        ragProperties.setTopk(10);
        ragProperties.setHybridEnabled(true);
        RagProperties.Rerank rerank = new RagProperties.Rerank();
        rerank.setRecallFactor(3);
        ragProperties.setRerank(rerank);

        retriever = new HybridRetriever(ragProperties);
        ReflectionTestUtils.setField(retriever, "embeddingModel", embeddingModel);
        ReflectionTestUtils.setField(retriever, "embeddingStore", embeddingStore);
        ReflectionTestUtils.setField(retriever, "luceneIndexer", luceneIndexer);
        ReflectionTestUtils.setField(retriever, "reranker", reranker);
    }
    // 让向量检索返回指定文本
    private void mockVectorHits(List<String> texts) {
        Embedding q = mock(Embedding.class);
        when(embeddingModel.embed(anyString())).thenReturn(Response.from(q));

        List<EmbeddingMatch<TextSegment>> matches = texts.stream().map(t -> {
            EmbeddingMatch<TextSegment> m = mock(EmbeddingMatch.class);
            when(m.embedded()).thenReturn(TextSegment.from(t));
            return m;
        }).toList();

        EmbeddingSearchResult<TextSegment> result = mock(EmbeddingSearchResult.class);
        when(result.matches()).thenReturn(matches);
        when(embeddingStore.search(any())).thenReturn(result);
    }
    @Test
    @DisplayName("rerank 开：先按文本去重，再交给 reranker")
    void retrieve_withRerank_dedupsAndCallsReranker() {
        mockVectorHits(List.of("A", "B", "C"));
        when(luceneIndexer.search(anyString(), anyInt())).thenReturn(List.of("B", "D", "E"));
        when(reranker.rerank(anyString(), anyList(), anyInt())).thenReturn(List.of("B", "A"));

        List<String> result = retriever.retrieve("问题");

        // B 在向量和关键词都出现，去重后只剩一个
        verify(reranker).rerank(eq("问题"), eq(List.of("A", "B", "C", "D", "E")), eq(10));
        assertThat(result).containsExactly("B", "A");
    }

    @Test
    @DisplayName("rerank 关（reranker=null）：回退 RRF，不调 reranker")
    void retrieve_withoutReranker_fallsBackToRrf() {
        ReflectionTestUtils.setField(retriever, "reranker", null);
        mockVectorHits(List.of("A", "B"));
        when(luceneIndexer.search(anyString(), anyInt())).thenReturn(List.of("B", "C"));

        List<String> result = retriever.retrieve("问题");

        assertThat(result).isNotEmpty();
        assertThat(result).contains("B");   // B 两边都出现，RRF 里分数最高
        verify(reranker, never()).rerank(anyString(), anyList(), anyInt());
    }
    @Test
    @DisplayName("混合检索关：只走向量，不查关键词")
    void retrieve_hybridDisabled_vectorOnly() {
        ragProperties.setHybridEnabled(false);
        mockVectorHits(List.of("A", "B"));
        when(reranker.rerank(anyString(), anyList(), anyInt())).thenReturn(List.of("A"));

        List<String> result = retriever.retrieve("问题");

        verify(luceneIndexer, never()).search(anyString(), anyInt());
        verify(reranker).rerank(eq("问题"), eq(List.of("A", "B")), eq(10));
    }

    @Test
    @DisplayName("reranker 抛异常：回退 RRF，异常不外抛（R02 兜底）")
    void retrieve_rerankThrows_fallsBackToRrf() {
        mockVectorHits(List.of("A", "B"));
        when(luceneIndexer.search(anyString(), anyInt())).thenReturn(List.of("B", "C"));
        when(reranker.rerank(anyString(), anyList(), anyInt()))
                .thenThrow(new IllegalStateException("onnx 原生库崩了"));

        // 关键断言：不抛异常，返回 RRF 融合结果
        List<String> result = retriever.retrieve("问题");

        verify(reranker).rerank(anyString(), anyList(), anyInt());
        assertThat(result).containsExactly("B", "A", "C");
    }

    @Test
    @DisplayName("reranker 返回空列表：视为失败，同样回退 RRF（静默失效也要兜）")
    void retrieve_rerankReturnsEmpty_fallsBackToRrf() {
        mockVectorHits(List.of("A", "B"));
        when(luceneIndexer.search(anyString(), anyInt())).thenReturn(List.of("B", "C"));
        when(reranker.rerank(anyString(), anyList(), anyInt())).thenReturn(List.of());

        List<String> result = retriever.retrieve("问题");

        verify(reranker).rerank(anyString(), anyList(), anyInt());
        assertThat(result).containsExactly("B", "A", "C");
    }

    @Test
    @DisplayName("混合检索关 + reranker 抛异常：回退截断后的向量结果")
    void retrieve_hybridDisabled_rerankThrows_fallsBackToVector() {
        ragProperties.setHybridEnabled(false);
        mockVectorHits(List.of("A", "B"));
        when(reranker.rerank(anyString(), anyList(), anyInt()))
                .thenThrow(new RuntimeException("boom"));

        List<String> result = retriever.retrieve("问题");

        verify(luceneIndexer, never()).search(anyString(), anyInt());
        assertThat(result).containsExactly("A", "B");
    }
}
