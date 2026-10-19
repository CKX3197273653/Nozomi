package org.mate.mate10.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.mate.mate10.dto.AiAnalysisResult;
import org.mate.mate10.entity.qc.QcDefect;
import org.mate.mate10.entity.qc.QcProductionParam;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class AiResultParser {

    private static final Pattern NUMBER = Pattern.compile("-?\\d+(\\.\\d+)?");
    private final ObjectMapper objectMapper = new ObjectMapper();

    //外部入口,如果IllegalStateException为空/不是 JSON这样的错误让Kafka重试
    public AiAnalysisResult parse(String aiResponse, Long reportId) {
        AiAnalysisResult result = new AiAnalysisResult();
        result.setRawResponse(aiResponse);

        if (!StringUtils.hasText(aiResponse)) {
            throw new IllegalStateException("AI 返回内容为空");
        }

        JsonNode root;
        try {
            String cleaned = cleanJson(aiResponse);
            root = objectMapper.readTree(cleaned);
        } catch (Exception e) {
            // 连 JSON 都不是 → 抛出去,让消费者重试 / 进死信
            throw new IllegalStateException("AI 返回内容无法解析为 JSON：" + e.getMessage(), e);
        }

        result.setDefects(parseDefects(root, reportId, result));
        result.setParam(parseParam(root, reportId, result));

        log.info("[AiResult] reportId={} 缺陷 {} 条,参数{},告警 {} 条",
                reportId, result.getDefects().size(),
                result.hasParam() ? "已解析" : "无",
                result.getWarnings().size());
        return result;
    }

    //  缺陷
    private java.util.List<QcDefect> parseDefects(
            JsonNode root, Long reportId, AiAnalysisResult result) {

        java.util.List<QcDefect> list = new java.util.ArrayList<>();
        JsonNode arr = findArray(root, "defects", "缺陷", "缺陷列表", "defectList", "items");
        if (arr == null) {
            result.warn("没有找到 defects 数组");
            return list;
        }

        for (JsonNode n : arr) {
            QcDefect d = new QcDefect();
            d.setReportId(reportId);
            d.setDefectType(orDefault(
                    text(n, "defectType", "缺陷类型", "type", "类型"), "未知", result, "defectType"));
            d.setDefectPosition(orDefault(
                    text(n, "defectPosition", "缺陷位置", "position", "位置"), "未知位置", result, "defectPosition"));
            d.setSeverity(normalizeSeverity(
                    text(n, "severity", "严重程度", "level", "等级")));
            d.setDescription(orDefault(
                    text(n, "description", "缺陷描述", "desc", "描述"), "", result, "description"));

            BigDecimal confidence = decimal(n, "confidence", "置信度", "score");
            d.setConfidence(confidence != null ? confidence : BigDecimal.valueOf(80.0));
            d.setIsConfirmed(0);
            list.add(d);
        }
        return list;
    }

    //severity归一化成表里约定的四个值
    private String normalizeSeverity(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "MEDIUM";
        }
        String s = raw.trim().toUpperCase();
        if (s.contains("CRITICAL") || s.contains("致命") || s.contains("严重")) return "CRITICAL";
        if (s.contains("HIGH") || s.contains("高")) return "HIGH";
        if (s.contains("LOW") || s.contains("低") || s.contains("轻微")) return "LOW";
        return "MEDIUM";
    }

    //生产参数
    private QcProductionParam parseParam(
            JsonNode root, Long reportId, AiAnalysisResult result) {

        JsonNode p = findObject(root, "params", "参数", "生产参数", "productionParams", "process");
        if (p == null) {
            return null;
        }

        QcProductionParam param = new QcProductionParam();
        param.setReportId(reportId);
        param.setTemperature(decimal(p, "temperature", "温度", "浇注温度", "temp"));
        param.setPressure(decimal(p, "pressure", "压力", "保压压力"));
        param.setHumidity(decimal(p, "humidity", "湿度"));
        param.setSpeed(decimal(p, "speed", "速度", "转速"));
        param.setOperator(text(p, "operator", "操作员", "操作人"));
        param.setRemark(text(p, "remark", "备注", "说明"));
        param.setCreateTime(LocalDateTime.now());

        if (param.getTemperature() == null && param.getPressure() == null
                && param.getHumidity() == null && param.getSpeed() == null) {
            result.warn("params 里没有可用数值,忽略");
            return null;
        }
        return param;
    }

    //容错工具
    String cleanJson(String response) {
        String s = response.trim();
        if (s.contains("```json")) {
            s = s.substring(s.indexOf("```json") + 7);
            int end = s.indexOf("```");
            if (end > 0) s = s.substring(0, end);
        } else if (s.contains("```")) {
            s = s.substring(s.indexOf("```") + 3);
            int end = s.indexOf("```");
            if (end > 0) s = s.substring(0, end);
        }
        int start = s.indexOf('{');
        int end2 = s.lastIndexOf('}');
        if (start >= 0 && end2 > start) {
            s = s.substring(start, end2 + 1);
        }
        return s.trim();
    }

    private JsonNode findArray(JsonNode root, String... names) {
        JsonNode n = findNode(root, names);
        return (n != null && n.isArray()) ? n : null;
    }

    private JsonNode findObject(JsonNode root, String... names) {
        JsonNode n = findNode(root, names);
        return (n != null && n.isObject()) ? n : null;
    }

    private JsonNode findNode(JsonNode root, String... names) {
        if (root == null) return null;
        for (String name : names) {
            if (root.has(name) && !root.get(name).isNull()) {
                return root.get(name);
            }
        }
        return null;
    }
    
    String text(JsonNode node, String... names) {
        JsonNode v = findNode(node, names);
        if (v == null) return null;
        return v.isValueNode() ? v.asText() : v.toString();
    }

    BigDecimal decimal(JsonNode node, String... names) {
        JsonNode v = findNode(node, names);
        if (v == null) return null;
        if (v.isNumber()) return BigDecimal.valueOf(v.asDouble());
        if (v.isTextual()) {
            Matcher m = NUMBER.matcher(v.asText().replace(",", ""));
            if (m.find()) {
                try {
                    return new BigDecimal(m.group());
                } catch (NumberFormatException ignore) {
                    return null;
                }
            }
        }
        return null;
    }

    private String orDefault(String value, String def, AiAnalysisResult result, String field) {
        if (StringUtils.hasText(value)) return value;
        result.warn("字段缺少: " + field);
        return def;
    }
}