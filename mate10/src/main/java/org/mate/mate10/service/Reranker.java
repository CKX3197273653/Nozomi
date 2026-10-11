package org.mate.mate10.service;

import java.util.List;

public interface Reranker {
    List<String> rerank(String query,List<String> candidates, int topK);
}
