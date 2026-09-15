package org.mate.mate10.service;

import java.nio.file.Path;
import java.util.List;

public interface RagService {
    void addDocument(Path filePath);
    void addDocuments(List<Path> filePaths);
    void addText(String text);
    String ask(String question);
    void clearKnowledgeBase();
    String retrieve(String question);
}