package org.mate.mate10.service.Impl.qc;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.ollama.OllamaChatModel;
import org.mate.mate10.config.ChatModelFactory;
import org.mate.mate10.entity.qc.QcDefect;
import org.mate.mate10.entity.qc.QcProductionParam;
import org.mate.mate10.entity.qc.QcReport;
import org.mate.mate10.mapper.qc.QcReportMapper;
import org.mate.mate10.service.FileParseService;
import org.mate.mate10.service.qc.QcDefectService;
import org.mate.mate10.service.qc.QcProductionParamService;
import org.mate.mate10.service.qc.QcReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class QcReportServiceImpl implements QcReportService {
    @Autowired
    private QcReportMapper qcReportMapper;
    @Autowired
    private QcDefectService qcDefectService;
    @Autowired
    private QcProductionParamService qcProductionParamService;
    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    @Autowired
    private org.springframework.kafka.core.KafkaTemplate<String, String> kafkaTemplate;
    @Autowired
    private FileParseService fileParseService;
    @Autowired
    private ChatModelFactory chatModelFactory;

    @Override
    @Transactional  // 事务：三个操作要么都成功，要么都失败
    public Long createFullReport(QcReport report, List<QcDefect> defects, QcProductionParam param) {
        //插入报告，拿到 reportId
        Long reportId = createReport(report);
        //给每个缺陷设置 reportId，然后批量插入
        for (QcDefect defect : defects) {
            defect.setReportId(reportId);
        }
        qcDefectService.batchAddDefects(defects);
        //给生产参数设置 reportId，然后插入
        param.setReportId(reportId);
        qcProductionParamService.addParam(param);
        //更新报告的缺陷数量
        int defectCount = defects.size();
        String severityLevel = calculateSeverityLevel(defects);
        updateDefectCount(reportId, defectCount, severityLevel);
        return reportId;
    }
    //读取文件内容
    private String readFileContent(String fileUrl, String fileType) {
        // 现在调用 FileParseService
        return fileParseService.parseFileContent(fileUrl, fileType);
    }

    //调用AI识别缺陷
    private java.util.List<org.mate.mate10.entity.qc.QcDefect> analyzeDefectsByAI(Long reportId, String fileContent, String prompt){
        try {
            //空值检查
            if (fileContent == null ||fileContent.isEmpty()){
                System.out.println("AI分析:文件内容为空,--即将跳过--");
                return new  java.util.ArrayList<>();
            }
            String finalPrompt = (prompt != null && !prompt.isEmpty())
                    ? prompt
                    : getDefaultAnalyzePrompt();
            System.out.println("【AI分析】使用的Prompt长度：" + finalPrompt.length());
            System.out.println("【AI分析】文件内容长度：" + fileContent.length());
            //构建完整消息
            String fullMessage = finalPrompt + "\n\n【质检报告内容】\n" + fileContent;
            //调用AI
//            dev.langchain4j.data.message.ChatMessage userMessage = dev.langchain4j.data.message.UserMessage.from(prompt);
//            dev.langchain4j.model.chat.response.ChatResponse response = chatModel.chat(userMessage);
//            String aiResponse = response.aiMessage().text();
            ChatMessage userMessage = UserMessage.from(fullMessage);
ChatResponse response = chatModelFactory.getChatModel().chat(userMessage);
            String aiResponse = response.aiMessage().text();
            System.out.println("【AI分析】AI 返回结果长度：" + (aiResponse != null ? aiResponse.length() : 0));
            //返回AI解析
            return parseAiResponse(aiResponse, reportId);
        }catch (Exception e) {
            System.err.println("【AI分析】缺陷识别失败：" + e.getMessage());
            e.printStackTrace();
            return new java.util.ArrayList<>();
        }
    }
    private String getDefaultAnalyzePrompt() {
        return "你是一个专业的质量检测分析师。请分析以下质检报告内容，识别其中的缺陷信息。\n\n" +
                "【任务要求】\n" +
                "请从报告中提取所有缺陷信息，包括：\n" +
                "1. 缺陷类型（如：划痕、裂纹、色差、毛刺、变形等）\n" +
                "2. 缺陷位置（如：左侧、底部、边缘等）\n" +
                "3. 严重程度（LOW/MEDIUM/HIGH/CRITICAL）\n" +
                "4. 缺陷描述（简要描述缺陷情况）\n" +
                "5. 置信度（0-100，表示识别的准确程度）\n\n" +
                "【输出格式】\n" +
                "请严格按以下 JSON 格式输出，不要添加其他文字：\n" +
                "{\n" +
                "  \"defects\": [\n" +
                "    {\n" +
                "      \"defectType\": \"缺陷类型\",\n" +
                "      \"defectPosition\": \"缺陷位置\",\n" +
                "      \"severity\": \"严重程度\",\n" +
                "      \"description\": \"缺陷描述\",\n" +
                "      \"confidence\": 95.0\n" +
                "    }\n" +
                "  ]\n" +
                "}\n\n" +
                "如果报告中没有发现缺陷，请返回：\n" +
                "{\n" +
                "  \"defects\": []\n" +
                "}";
    }
    private java.util.List<org.mate.mate10.entity.qc.QcDefect> parseAiResponse(String aiResponse, Long reportId) {
        java.util.List<org.mate.mate10.entity.qc.QcDefect> defects = new java.util.ArrayList<>();

        try {
            // 清理 AI 返回内容
            String cleanedResponse = cleanJsonResponse(aiResponse);

            // 解析 JSON
            com.fasterxml.jackson.databind.JsonNode root =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(cleanedResponse);

            com.fasterxml.jackson.databind.JsonNode defectsNode = root.get("defects");
            if (defectsNode == null || !defectsNode.isArray()) {
                System.out.println("【AI分析】AI 返回格式错误，没有 defects 数组");
                return defects;
            }

            // 遍历每个缺陷
            for (com.fasterxml.jackson.databind.JsonNode defectNode : defectsNode) {
                org.mate.mate10.entity.qc.QcDefect defect = new org.mate.mate10.entity.qc.QcDefect();
                defect.setReportId(reportId);
                defect.setDefectType(getTextOrDefault(defectNode, "defectType", "未知"));
                defect.setDefectPosition(getTextOrDefault(defectNode, "defectPosition", "未知位置"));
                defect.setSeverity(getTextOrDefault(defectNode, "severity", "MEDIUM"));
                defect.setDescription(getTextOrDefault(defectNode, "description", ""));

                double confidence = defectNode.has("confidence") ?
                        defectNode.get("confidence").asDouble() : 80.0;
                defect.setConfidence(java.math.BigDecimal.valueOf(confidence));
                defect.setIsConfirmed(0);

                defects.add(defect);
            }

            System.out.println("【AI分析】解析完成，识别到 " + defects.size() + " 个缺陷");

        } catch (Exception e) {
            System.err.println("【AI分析】JSON 解析失败：" + e.getMessage());
            e.printStackTrace();
        }

        return defects;
    }

    private String cleanJsonResponse(String response) {
        if (response == null) return "{}";

        if (response.contains("```json")) {
            response = response.substring(response.indexOf("```json") + 7);
            response = response.substring(0, response.indexOf("```"));
        } else if (response.contains("```")) {
            response = response.substring(response.indexOf("```") + 3);
            response = response.substring(0, response.indexOf("```"));
        }

        return response.trim();
    }

    private String getTextOrDefault(com.fasterxml.jackson.databind.JsonNode node,
                                    String fieldName, String defaultValue) {
        if (node.has(fieldName) && !node.get(fieldName).isNull()) {
            return node.get(fieldName).asText();
        }
        return defaultValue;
    }



    //根据缺陷列表计算严重程度
    private String calculateSeverityLevel(List<QcDefect> defects) {
        if (defects == null | defects.isEmpty()){
            return "NORMAL";
        }
        boolean hasCritical = defects.stream()
                .anyMatch(d -> "CRITICAL".equals(d.getSeverity()));
        boolean hasHigh = defects.stream()
                .anyMatch(d -> "HIGH".equals(d.getSeverity()));
        if (hasCritical) return "CRITICAL";
        if (hasHigh) return "WARNING";
        return "NORMAL";
    }

    //增删改查
    @Override
    public Long createReport(QcReport report){
        qcReportMapper.insert(report);
        return report.getId();
    }
    @Override
    public boolean updateReport(QcReport report){
        return qcReportMapper.updateById(report)>0;
    }
    @Override
    public boolean deleteReport(Long id){
        return qcReportMapper.deleteById(id)>0;
    }
    @Override
    public QcReport getById(Long id){
        return qcReportMapper.selectById(id);
    }


    @Override
    public Page<QcReport> pageList(int pageNum, int pageSize,
                                   String reportNo, String productName,
                                   String productBatch, String productionLine,
                                   String status){
        //创建分页对象
        Page<QcReport> page = new Page<>(pageNum,pageSize);
        //查询条件
        LambdaQueryWrapper<QcReport> wrapper = new LambdaQueryWrapper<>();
        //动态条件(非空)
        if (StringUtils.hasText(reportNo))       wrapper.like(QcReport::getReportNo, reportNo);
        if (StringUtils.hasText(productName))    wrapper.like(QcReport::getProductName, productName);
        if (StringUtils.hasText(productBatch))   wrapper.like(QcReport::getProductBatch, productBatch);
        if (StringUtils.hasText(productionLine)) wrapper.like(QcReport::getProductionLine, productionLine);
        if (StringUtils.hasText(status))         wrapper.eq(QcReport::getStatus, status);
        //排序
        wrapper.orderByDesc(QcReport::getCreateTime);
        //分页查询
        return qcReportMapper.selectPage(page, wrapper);
    }
    @Override
    public List<QcReport> getByProductName(String productName) {
        return qcReportMapper.selectByProductName(productName);
    }
    @Override
    public List<QcReport> getByStatus(String status) {
        return qcReportMapper.selectByStatus(status);
    }


    @Override
    public boolean updateStatus(Long id, String status) {
        QcReport report = new QcReport();
        report.setId(id);
        report.setStatus(status);
        return qcReportMapper.updateById(report) > 0;
    }

    @Override
    public boolean updateDefectCount(Long id, int defectCount, String severityLevel) {
        QcReport report = new QcReport();
        report.setId(id);
        report.setTotalDefects(defectCount);
        report.setSeverityLevel(severityLevel);
        return qcReportMapper.updateById(report) > 0;
    }
    //发送Kafka消息
    @Override
    public void sendAnalyzeTask(Long reportId, String fileUrl, String fileType,String prompt) {
        try{
            java.util.Map<String,Object> msgMap = new java.util.HashMap<>();
            msgMap.put("reportId", reportId);
            msgMap.put("fileUrl", fileUrl);
            msgMap.put("fileType", fileType);
            msgMap.put("prompt", prompt);

            String json = objectMapper.writeValueAsString(msgMap);

            kafkaTemplate.send("qc-report-topic", json);

            System.out.println("Kafka消息发送成功,reportId:"+reportId);
        }catch (com.fasterxml.jackson.core.JsonProcessingException e){
            throw new RuntimeException("Kafka序列化失败",e);
        }
    }
    //消费者 接受JSON字符串,解析后调用业务方法,Spring Kafka会自动来调用
    /* 告诉spring Kafka监听qc-report-topic主题,有消息自动调用 */
    @org.springframework.kafka.annotation.KafkaListener(topics = "qc-report-topic")
    /*参数是字符串不是Map,这是Kafka原始格式*/
    public void consumeAnalyzeTask(String messageJson){
        try{
            //解析JSON字符串为Map
            java.util.Map<String,Object> msg = objectMapper.readValue(messageJson,java.util.Map.class);
            //从Map中提取参数
            /* JSON解析数字时,ObjectMapper默认转为Integer或者Long,为了保险我们统一((Number) ...).longValue()*/
            Long reportId = ((Number) msg.get("reportId")).longValue();
            String fileUrl = (String)msg.get("fileUrl");
            String fileType = (String)msg.get("fileType");
            String prompt = (String)msg.get("prompt");
            System.out.println("Kafka消费者接受到消息,reportId"+reportId);

            //调用业务方法处理
            processAnalyzeTask(reportId,fileUrl,fileType,prompt);
            System.out.println("Kafka消费者消息处理成功,reportId"+reportId);
        }catch (Exception e){
            System.err.println("Kafka消费者消息处理失败"+e.getMessage());
            e.printStackTrace();
        }
    }

    //执行AI分析
    @Override
    public void processAnalyzeTask(Long reportId, String fileUrl, String fileType,String prompt) {
      try{
          System.out.println("业务处理-分析报告:"+reportId);
          //状态更改为 PROCESSING
          updateStatus(reportId,"PROCESSING");
          //读取文件内容
          String fileContent = readFileContent(fileUrl,fileType);
          System.out.println("业务处理-提取文件长度"+(fileContent != null ? fileContent.length() : 0));

          java.util.List<org.mate.mate10.entity.qc.QcDefect> defects = analyzeDefectsByAI(reportId,fileContent,prompt);

          if (defects!=null && !defects.isEmpty()){
              qcDefectService.batchAddDefects(defects);
          }
          int defectCount = (defects != null) ? defects.size() : 0;
          String severityLevel = calculateSeverityLevel(defects);
          updateDefectCount(reportId,defectCount,severityLevel);
          updateStatus(reportId,"COMPLETED");

          System.out.println("业务处理-报告分析完毕,reportId"+reportId+"缺陷数:"+defectCount);
      }catch (Exception e){
          updateStatus(reportId,"FAILED");
          System.err.println("业务处理-报告分析失败,reportId"+reportId+"原因:"+e.getMessage());
          throw new RuntimeException("报告分析失败",e);
      }
    }

    @Override
    public List<Map<String, Object>> countByTimeTrend(int days) {
        if (days <= 0) {
            days = 7;  // 默认 7 天
        }
        return qcReportMapper.countByTimeTrend(days);
    }

    @Override
    public List<Map<String, Object>> countByProductionLine() {
        return qcReportMapper.countByProductionLine();
    }


    @Override
    public List<Map<String, Object>> countByProductBatch(int limit) {
        if (limit <= 0) {
            limit = 10;  // 默认显示前 10 个批次
        }
        return qcReportMapper.countByProductBatch(limit);
    }

    @Override
    public Map<String, Object> analyzeRootCause(Long reportId) {
       Map<String,Object> result = new java.util.HashMap<>();
       try{
           //查询列表
           QcReport report = qcReportMapper.selectById(reportId);
           if (report == null) {
               result.put("error", "报告不存在");
               return result;
           }
           //查询缺陷列表
           List<QcDefect> defects = qcDefectService.getByReportId(reportId);
           if (defects == null || defects.isEmpty()) {
               result.put("error", "该报告暂无缺陷数据");
               return result;
           }
           //查询生产参数
           QcProductionParam param = qcProductionParamService.getByReportId(reportId);
           if (param == null) {
               result.put("error", "该报告暂无生产参数数据");
               return result;
           }
           //构建根因分析
           String prompt = buildRootCausePrompt(report, defects, param);
//调用ai
            dev.langchain4j.data.message.ChatMessage userMessage = dev.langchain4j.data.message.UserMessage.from(prompt);
            dev.langchain4j.model.chat.response.ChatResponse response = chatModelFactory.getChatModel().chat(userMessage);
           String aiResponse = response.aiMessage().text();
           System.out.println("【根因分析】AI 返回长度：" + (aiResponse != null ? aiResponse.length() : 0));
           //解析
           result = parseRootCauseResponse(aiResponse);
           //附加原始数据
           result.put("defectCount", defects.size());
           result.put("severityLevel", report.getSeverityLevel());
       } catch (Exception e) {
           System.err.println("【根因分析】失败：" + e.getMessage());
           e.printStackTrace();
           result.put("error", "根因分析失败：" + e.getMessage());
       }
       return result;
    }
    //分析prompt
    private String buildRootCausePrompt(QcReport report, List<QcDefect> defects, QcProductionParam param) {
        StringBuilder sb = new StringBuilder();

        sb.append("你是一个专业的质量检测分析师，请根据以下信息进行根因分析。\n\n");

        // 报告信息
        sb.append("【报告信息】\n");
        sb.append("产品名称：").append(report.getProductName()).append("\n");
        sb.append("产品批次：").append(report.getProductBatch()).append("\n");
        sb.append("生产线：").append(report.getProductionLine()).append("\n");
        sb.append("检验员：").append(report.getInspector()).append("\n\n");

        // 缺陷信息
        sb.append("【缺陷信息】共 ").append(defects.size()).append(" 个缺陷\n");
        for (int i = 0; i < defects.size(); i++) {
            QcDefect d = defects.get(i);
            sb.append(i + 1).append(". 类型：").append(d.getDefectType())
                    .append("，位置：").append(d.getDefectPosition())
                    .append("，严重程度：").append(d.getSeverity())
                    .append("，描述：").append(d.getDescription()).append("\n");
        }
        sb.append("\n");

        // 生产参数
        sb.append("【生产参数】\n");
        sb.append("温度：").append(param.getTemperature()).append("°C\n");
        sb.append("压力：").append(param.getPressure()).append("MPa\n");
        sb.append("湿度：").append(param.getHumidity()).append("%\n");
        sb.append("速度：").append(param.getSpeed()).append("rpm\n");
        sb.append("操作员：").append(param.getOperator()).append("\n");
        if (param.getRemark() != null && !param.getRemark().isEmpty()) {
            sb.append("备注：").append(param.getRemark()).append("\n");
        }
        sb.append("\n");

        // 任务要求
        sb.append("【任务要求】\n");
        sb.append("请分析每个缺陷可能的根本原因，特别是与生产参数的关联性。\n");
        sb.append("请给出具体的参数调整建议。\n\n");

        sb.append("【输出格式】\n");
        sb.append("请严格按以下 JSON 格式输出，不要添加其他文字：\n");
        sb.append("{\n");
        sb.append("  \"rootCauses\": [\n");
        sb.append("    {\n");
        sb.append("      \"defectType\": \"缺陷类型\",\n");
        sb.append("      \"possibleCause\": \"可能原因\",\n");
        sb.append("      \"relatedParam\": \"相关参数(temperature/pressure/humidity/speed)\",\n");
        sb.append("      \"currentValue\": \"当前值\",\n");
        sb.append("      \"suggestedValue\": \"建议值\",\n");
        sb.append("      \"confidence\": 85.0,\n");
        sb.append("      \"recommendation\": \"具体建议\"\n");
        sb.append("    }\n");
        sb.append("  ],\n");
        sb.append("  \"summary\": \"总体分析结论\"\n");
        sb.append("}");

        return sb.toString();
    }
    //ai根因分析
    private Map<String, Object> parseRootCauseResponse(String aiResponse) {
        Map<String, Object> result = new java.util.HashMap<>();

        try {
            // 清理 markdown 标记
            String cleaned = cleanJsonResponse(aiResponse);

            com.fasterxml.jackson.databind.JsonNode root =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(cleaned);

            // 提取 summary
            String summary = getTextOrDefault(root, "summary", "无分析结论");
            result.put("summary", summary);

            // 提取 rootCauses 数组
            com.fasterxml.jackson.databind.JsonNode causesNode = root.get("rootCauses");
            if (causesNode == null || !causesNode.isArray()) {
                result.put("rootCauses", new java.util.ArrayList<>());
                return result;
            }

            List<Map<String, Object>> rootCauses = new java.util.ArrayList<>();
            for (com.fasterxml.jackson.databind.JsonNode causeNode : causesNode) {
                Map<String, Object> cause = new java.util.HashMap<>();
                cause.put("defectType", getTextOrDefault(causeNode, "defectType", "未知"));
                cause.put("possibleCause", getTextOrDefault(causeNode, "possibleCause", "未知"));
                cause.put("relatedParam", getTextOrDefault(causeNode, "relatedParam", "未知"));
                cause.put("currentValue", getTextOrDefault(causeNode, "currentValue", "未知"));
                cause.put("suggestedValue", getTextOrDefault(causeNode, "suggestedValue", "未知"));
                cause.put("confidence", causeNode.has("confidence") ?
                        causeNode.get("confidence").asDouble() : 80.0);
                cause.put("recommendation", getTextOrDefault(causeNode, "recommendation", "无建议"));
                rootCauses.add(cause);
            }

            result.put("rootCauses", rootCauses);

        } catch (Exception e) {
            System.err.println("【根因分析】JSON 解析失败：" + e.getMessage());
            result.put("error", "AI 返回解析失败");
            result.put("rawResponse", aiResponse);
        }

        return result;
    }
}
