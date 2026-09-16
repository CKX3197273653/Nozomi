package org.mate.mate10.dto.eval;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class MissItem {
    private Integer id;
    private String question;
    private String category;
    private List<String> missing;
}
