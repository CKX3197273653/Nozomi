package org.mate.mate10.dto.eval;

import lombok.Data;

import java.util.List;

@Data
public class EvalCase {
    private Integer id;
    private String question;
    private String category;
    private List<String> expected;
}
