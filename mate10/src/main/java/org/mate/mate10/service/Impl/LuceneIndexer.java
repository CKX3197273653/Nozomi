package org.mate.mate10.service.Impl;

import dev.langchain4j.data.segment.TextSegment;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.wltea.analyzer.lucene.IKAnalyzer;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class LuceneIndexer {

    private static final String F_TEXT = "text";
    private static final String F_UID  = "uid";

    private static final Pattern ID_PATTERN = Pattern.compile("[A-Za-z]\\d{4,}");
    private static final float ID_BOOST = 5.0f;

    private final Directory directory;
    private final Analyzer analyzer;
    private final IndexWriter writer;

    public LuceneIndexer(String indexDir) throws Exception {
        Files.createDirectories(Paths.get(indexDir));
        this.directory = FSDirectory.open(Paths.get(indexDir));
        this.analyzer = new IKAnalyzer(true);                
        IndexWriterConfig cfg = new IndexWriterConfig(analyzer);
        cfg.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        this.writer = new IndexWriter(directory, cfg);
        this.writer.commit();
        log.info("[Lucene] 索引目录：{}", Paths.get(indexDir).toAbsolutePath());
    }
    //Milvus写入时同步批量写入
    public synchronized void addAll(List<TextSegment> segments) {
        if (segments == null || segments.isEmpty()) {
            return;
        }
        try {
            for (TextSegment seg : segments) {
                Document doc = new Document();
                doc.add(new StringField(F_UID, uidOf(seg), Field.Store.YES));
                doc.add(new TextField(F_TEXT, seg.text(), Field.Store.YES));
                writer.addDocument(doc);
            }
            writer.commit();
            log.info("[Lucene] 写入 {} 个分块", segments.size());
        } catch (Exception e) {
            log.error("[Lucene] 写入失败", e);
        }
    }

    public synchronized List<String> search(String question, int topK) {
        List<String> result = new ArrayList<>();
        if (question == null || question.isBlank()) {
            return result;
        }
        try (DirectoryReader reader = DirectoryReader.open(directory)) {
            if (reader.numDocs() == 0) {
                log.warn("[Lucene] 索引为空，跳过关键词检索（需要重新初始化知识库）");
                return result;
            }

            IndexSearcher searcher = new IndexSearcher(reader);
            QueryParser parser = new MultiFieldQueryParser(new String[]{F_TEXT}, analyzer);
            parser.setDefaultOperator(QueryParser.Operator.OR);

            log.info("[Lucene] 分词结果：{}", analyze(question));

            Query query = parser.parse(QueryParser.escape(question));

            List<String> ids = new ArrayList<>();
            Matcher m = ID_PATTERN.matcher(question);
            while (m.find()) {
                ids.add(m.group().toLowerCase());
            }
            if (!ids.isEmpty()) {
                BooleanQuery.Builder builder = new BooleanQuery.Builder();
                builder.add(query, BooleanClause.Occur.SHOULD);
                for (String id : ids) {
                    builder.add(
                            new BoostQuery(new TermQuery(new Term(F_TEXT, id)), ID_BOOST),
                            BooleanClause.Occur.SHOULD);
                }
                query = builder.build();
                log.info("[Lucene] 标识符增强：{}（boost {}）", ids, ID_BOOST);
            }

            TopDocs topDocs = searcher.search(query, topK);
            for (ScoreDoc sd : topDocs.scoreDocs) {
                Document doc = searcher.doc(sd.doc);
                result.add(doc.get(F_TEXT));
            }
            log.info("[Lucene] 关键词检索命中 {} 条", result.size());

        } catch (Exception e) {
            log.warn("[Lucene] 检索失败：{}", e.getMessage());
        }
        return result;
    }

    public synchronized void clear() {
        try {
            writer.deleteAll();
            writer.commit();
            log.info("[Lucene] 索引已清空");
        } catch (Exception e) {
            log.error("[Lucene] 清空失败", e);
        }
    }

    private static String uidOf(TextSegment seg) {
        return Integer.toHexString(seg.text().hashCode());
    }

    public List<String> analyze(String text) {
        List<String> terms = new ArrayList<>();
        try (TokenStream ts = analyzer.tokenStream(F_TEXT, text)) {
            CharTermAttribute attr = ts.addAttribute(CharTermAttribute.class);
            ts.reset();
            while (ts.incrementToken()) {
                terms.add(attr.toString());
            }
            ts.end();
        } catch (Exception e) {
            log.warn("[Lucene] 分词失败：{}", e.getMessage());
        }
        return terms;
    }

    public void close() throws Exception {
        writer.close();
        directory.close();
        analyzer.close();
    }
}