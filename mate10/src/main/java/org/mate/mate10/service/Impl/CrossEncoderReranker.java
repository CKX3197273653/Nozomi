package org.mate.mate10.service.Impl;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.model.scoring.onnx.OnnxScoringModel;
import lombok.extern.slf4j.Slf4j;
import org.mate.mate10.config.RagProperties;
import org.mate.mate10.service.Reranker;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.rag.rerank.enabled", havingValue = "true")
public class CrossEncoderReranker implements Reranker {

    private final ScoringModel scoringModel;

    public CrossEncoderReranker(RagProperties  ragProperties) {
        String modelPath = ragProperties.getRerank().getModelPath();
        String tokenizerPath = ragProperties.getRerank().getTokenizerPath();

        if (modelPath == null || modelPath.isBlank() || !Files.exists(Path.of(modelPath))) {
            throw new IllegalStateException("[Rerank] 模型文件不存在:" + modelPath );
        }
        if (tokenizerPath == null || tokenizerPath.isBlank() || !Files.exists(Path.of(tokenizerPath))) {
            throw new IllegalStateException("[Rerank] tokenizer 文件不存在：" + tokenizerPath);
        }
        long start = System.currentTimeMillis();
        this.scoringModel = new OnnxScoringModel(modelPath, tokenizerPath,
                ragProperties.getRerank().getMaxLength());
        log.info("[Rerank] Cross-Encoder 加载完成，耗时 {} ms：{}",
                System.currentTimeMillis() - start, modelPath);
    }

    @Override
    public List<String> rerank(String query, List<String> candidates, int topK) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        List<TextSegment> segments = candidates.stream()
                .map(TextSegment::from)
                .toList();
        List<Double> scores = scoringModel.scoreAll(segments, query).content();
        return sortByScoreDesc(candidates, scores, topK);
    }

    static List<String> sortByScoreDesc(List<String> candidates, List<Double> scores, int topK) {
        int n = candidates.size();
        List<Integer> order = IntStream.range(0, n).boxed()
                .sorted((a, b) -> Double.compare(scores.get(b), scores.get(a)))
                .toList();
        int limit = Math.min(topK, n);
        List<String> result = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            result.add(candidates.get(order.get(i)));
        }
        return result;
    }
}
