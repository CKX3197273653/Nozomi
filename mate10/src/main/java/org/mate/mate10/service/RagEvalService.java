package org.mate.mate10.service;


import lombok.extern.slf4j.Slf4j;
import org.mate.mate10.dto.eval.EvalCase;
import org.mate.mate10.dto.eval.EvalReport;
import org.mate.mate10.dto.eval.MissItem;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import java.io.InputStream;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@Service
@Slf4j
public class RagEvalService {

    private final RagService ragService;
    private final ObjectMapper objectMapper;

    public RagEvalService(RagService ragService, ObjectMapper objectMapper) {
        this.ragService = ragService;
        this.objectMapper = objectMapper;
    }

    public EvalReport run() throws IOException {
        List<EvalCase> cases = loadCases();
        List<MissItem> misses = new ArrayList<>();
        Map<String, int[]> stat = new HashMap<>();   // category -> [total, hits]
        int hits = 0;

        for (EvalCase c : cases) {
            String retrieved = ragService.retrieve(c.getQuestion());
            final String context = (retrieved == null) ? "" : retrieved;

            List<String> missing = c.getExpected().stream()
                    .filter(kw -> !context.contains(kw))
                    .toList();

            int[] s = stat.computeIfAbsent(c.getCategory(), k -> new int[2]);
            s[0]++;
            if (missing.isEmpty()) {
                hits++;
                s[1]++;
            } else {
                misses.add(new MissItem(c.getId(), c.getQuestion(), c.getCategory(), missing));
                log.warn("评测未命中 [{}] {} -> 缺失 {}", c.getId(), c.getQuestion(), missing);
            }
        }

        EvalReport r = new EvalReport();
        r.setTotal(cases.size());
        r.setHits(hits);
        r.setHitRate(cases.isEmpty() ? 0 : (double) hits / cases.size());
        r.setMisses(misses);

        Map<String, String> byCat = new LinkedHashMap<>();
        stat.forEach((k, v) -> byCat.put(k, v[1] + "/" + v[0]));
        r.setByCategory(byCat);
        return r;
    }

    private List<EvalCase> loadCases() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/eval/qa-set.json")) {
            if (in == null) throw new IllegalStateException("找不到 /eval/qa-set.json");
            return objectMapper.readValue(in, new TypeReference<List<EvalCase>>() {});
        }
    }
}