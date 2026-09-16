package org.mate.mate10.service.Impl;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.mate.mate10.config.ChatModelFactory;
import org.mate.mate10.service.RagService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class RagServiceImpl implements RagService {

    //文本转向量
    @Autowired
    private EmbeddingModel embeddingModel;
    //存储和检索向量数据,存储文本片段
    @Autowired
    private EmbeddingStore<TextSegment> embeddingStore;
    //调用模型回答
    @Autowired
    private ChatModelFactory chatModelFactory;

    private static final int MAX_CHUNK_CHARS = 400;

    //粒度控制
    private static final boolean USE_HEADING_SPLIT = true;
//    private static final boolean USE_HEADING_SPLIT = false;

    @Override
    public void addDocument(Path filePath) {
        Document document = FileSystemDocumentLoader.loadDocument(filePath, new TextDocumentParser());
        storeDocument(document);
    }

    @Override
    public void addDocuments(List<Path> filePaths) {
        for (Path filePath : filePaths) {
            addDocument(filePath);
        }
    }

    @Override
    public void addText(String text) {
        Document document = Document.from(text);
        storeDocument(document);
    }

    private void storeDocument(Document document) {
        List<TextSegment> segments = USE_HEADING_SPLIT
                ? splitByHeading(document)
                : DocumentSplitters.recursive(512,128).split(document);

        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
        embeddingStore.addAll(embeddings, segments);
    }
    //按 markdown 二级标题切分,保证一个chunk只讲一个主题   长字段按照字符兜底切分
    private List<TextSegment> splitByHeading(Document document) {
        String text = document.text();
        //提取文档标题
        String title = "";
        int firstNewline = text.indexOf('\n');
        if (text.startsWith("#") && firstNewline > 0) {
            title = text.substring(0, firstNewline).strip();
        }

        String[] sections = Pattern.compile("(?=^## )", Pattern.MULTILINE).split(text);

        List<TextSegment> result = new ArrayList<>();
        for (String s : sections) {
            String t = s.strip();
            if (t.isEmpty()) continue;

            String context = (t.startsWith("# ") || title.isEmpty())
                    ? t
                    : title + "\n\n" + t;
            if (context.length() <= MAX_CHUNK_CHARS) {
                result.add(TextSegment.from(context,document.metadata()));
            }else {
                result.addAll(
                        DocumentSplitters.recursive(MAX_CHUNK_CHARS,50)
                                .split(Document.from(context))
                );
            }
        }
        return result;
    }
    @Override
    public String ask(String question) {
        String context = retrieve(question);
        if (context == null || context.isEmpty()) {
            return chatModelFactory.getChatModel().chat(question);
        }

        String prompt = String.format("""
                使用以下信息回答问题：

                %s

                问题：%s

                如果信息中没有相关内容，请直接回答，不要编造。
                """, context, question);

        return chatModelFactory.getChatModel().chat(prompt);
    }

    @Override
    public void clearKnowledgeBase() {
        embeddingStore.removeAll();
    }

    @Override
    public String retrieve(String question) {
        Embedding queryEmbedding = embeddingModel.embed(question).content();
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(10)
                .build();

        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(request).matches();
        if (matches.isEmpty()) {
            return null;
        }
        return matches.stream()
                .map(m -> m.embedded().text())
                .collect(Collectors.joining("\n\n"));
    }
}
