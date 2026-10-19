package org.mate.mate10.dto;

import lombok.Data;
import org.mate.mate10.entity.qc.QcDefect;
import org.mate.mate10.entity.qc.QcProductionParam;

import java.util.ArrayList;
import java.util.List;

@Data
public class AiAnalysisResult {
    //识别到的缺陷(可能为空)
    private List<QcDefect> defects = new ArrayList<>();

    //生产参数;报告没有 ->null,此时上层不清空已有参数
    private QcProductionParam param;

    //AI原始返回的文本,只用来排查
    private String rawResponse;
    
    //解析过程的可读报警
    private List<String> warnings = new ArrayList<>();

    public void warn(String message) {
        this.warnings.add(message);
    }

    public boolean hasParam() {
        return param != null;
    }
}
