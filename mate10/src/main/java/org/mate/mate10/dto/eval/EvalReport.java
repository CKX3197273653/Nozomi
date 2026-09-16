package org.mate.mate10.dto.eval;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class EvalReport {
    private int total;
    private int  hits;
    private double hitRate;
    private List<MissItem> misses;
    private Map<String, String> byCategory;
}
