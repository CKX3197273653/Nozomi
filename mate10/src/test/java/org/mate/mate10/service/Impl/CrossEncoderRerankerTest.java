package org.mate.mate10.service.Impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CrossEncoderRerankerTest {
    @Test
    @DisplayName("按分数降序重排并截断 Top-K")
    void sortByScoreDesc_sortsDescAndTruncates() {
        List<String> candidates = List.of("A","B","C","D");
        List<Double> scores = List.of(0.1,0.9,0.3,0.8);

        List<String> result = CrossEncoderReranker.sortByScoreDesc(candidates, scores, 2);
        assertThat(result).containsExactly("B","D");
    }

    @Test
    @DisplayName("topK 大于候选数时，返回全部（按分数排序）")
    void sortByScoreDesc_topKLargerThanSize() {
        List<String> candidates = List.of("A", "B", "C");
        List<Double> scores = List.of(0.2, 0.7, 0.5);

        List<String> result = CrossEncoderReranker.sortByScoreDesc(candidates, scores, 10);

        assertThat(result).containsExactly("B", "C", "A");
    }

    @Test
    @DisplayName("分数并列时不应崩溃（顺序稳定即可）")
    void sortByScoreDesc_ties() {
        List<String> candidates = List.of("A", "B", "C");
        List<Double> scores = List.of(0.5, 0.5, 0.5);

        List<String> result = CrossEncoderReranker.sortByScoreDesc(candidates, scores, 3);

        assertThat(result).containsExactlyInAnyOrder("A", "B", "C");
        assertThat(result).hasSize(3);
    }
    @Test
    @DisplayName("候选为空时返回空列表")
    void sortByScoreDesc_empty() {
        List<String> result = CrossEncoderReranker.sortByScoreDesc(List.of(), List.of(), 3);

        assertThat(result).isEmpty();
    }
}
