package org.mate.mate10.service.Impl;

import dev.langchain4j.data.segment.TextSegment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LuceneIndexerTest {

    @TempDir
    Path tempDir;

    private LuceneIndexer indexer;

    @BeforeEach
    void setUp() throws Exception {
        indexer = new LuceneIndexer(tempDir.toString());
    }

    @AfterEach
    void tearDown() throws Exception {
        indexer.close();
    }

    private void add(String... texts) {
        indexer.addAll(Arrays.stream(texts).map(TextSegment::from).toList());
    }

    @Test
    @DisplayName("索引后能检索到对应文本")
    void search_returnsIndexedText() {
        add("质量检测标准：划痕深度不超过0.1mm", "尺寸公差：长度±0.05mm");

        List<String> hits = indexer.search("划痕标准", 5);

        assertThat(hits).contains("质量检测标准：划痕深度不超过0.1mm");
    }

    @Test
    @DisplayName("报告编号（标识符）应被 boost 排到最前")
    void search_identifierBoosted() {
        add("报告 R20260812002 的缺陷分析", "这是一段普通的质量描述文字");

        List<String> hits = indexer.search("分析 R20260812002 的根因", 5);

        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0)).contains("R20260812002");
    }

    @Test
    @DisplayName("clear 后索引为空，检索返回空")
    void clear_emptiesIndex() {
        add("划痕标准：深度不超过0.1mm");
        indexer.clear();

        List<String> hits = indexer.search("划痕", 5);

        assertThat(hits).isEmpty();
    }

    @Test
    @DisplayName("空查询返回空列表，不报错")
    void search_emptyQuery() {
        add("划痕标准：深度不超过0.1mm");

        List<String> hits = indexer.search("", 5);

        assertThat(hits).isEmpty();
    }
}