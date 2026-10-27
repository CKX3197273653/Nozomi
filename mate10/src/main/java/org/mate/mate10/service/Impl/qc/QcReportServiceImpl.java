package org.mate.mate10.service.Impl.qc;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.extern.slf4j.Slf4j;
import org.mate.mate10.common.AiResultParser;
import org.mate.mate10.common.EvictQcCache;
import org.mate.mate10.common.QcSeverityUtil;
import org.mate.mate10.config.ChatModelFactory;
import org.mate.mate10.dto.AiAnalysisResult;
import org.mate.mate10.entity.qc.QcDefect;
import org.mate.mate10.entity.qc.QcProductionParam;
import org.mate.mate10.entity.qc.QcReport;
import org.mate.mate10.mapper.qc.QcDefectMapper;
import org.mate.mate10.mapper.qc.QcProductionParamMapper;
import org.mate.mate10.mapper.qc.QcReportMapper;
import org.mate.mate10.repository.ReportMemoryRepository;
import org.mate.mate10.service.FileParseService;
import org.mate.mate10.service.qc.QcDefectService;
import org.mate.mate10.service.qc.QcProductionParamService;
import org.mate.mate10.service.qc.QcReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
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
    @Autowired
    private CacheManager cacheManager;
    @Autowired
    private QcDefectMapper qcDefectMapper;
    @Autowired
    private QcProductionParamMapper qcProductionParamMapper;
    @Autowired
    private AiResultParser aiResultParser;
    @Autowired
    private QcAnalysisResultApplier analysisResultApplier;
    @Autowired
    private ReportMemoryRepository repository;


    @Override
    @Transactional  // 事务：三个操作要么都成功，要么都失败
    @EvictQcCache
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
        String severityLevel = QcSeverityUtil.from(defects);
        updateDefectCount(reportId, defectCount, severityLevel);
        return reportId;
    }

    //读取文件内容
    private String readFileContent(String fileUrl, String fileType) {
        // 现在调用 FileParseService
        return fileParseService.parseFileContent(fileUrl, fileType);
    }


    //调用AI识别缺陷
    private String callAiForAnalysis(Long reportId, String fileContent, String prompt) {
        try {
            //空值检查
            if (fileContent == null || fileContent.isEmpty()) {
                throw new IllegalStateException(
                        "文件内容为空，无法分析（PDF/Word 可能不存在或解析失败）");
            }
            String finalPrompt = (prompt != null && !prompt.isEmpty())
                    ? prompt
                    : getDefaultAnalyzePrompt();
            log.debug("AI分析：Prompt长度={}, 文件内容长度={}", finalPrompt.length(), fileContent.length());
            //构建完整消息
            String fullMessage = finalPrompt + "\n\n【质检报告内容】\n" + fileContent;
            ChatMessage userMessage = UserMessage.from(fullMessage);
            ChatResponse response = chatModelFactory.getChatModel().chat(userMessage);
            String aiResponse = response.aiMessage().text();
            log.debug("AI分析: 返回结果长度" + (aiResponse != null ? aiResponse.length() : 0));
            //返回AI解析
            return aiResponse;
        } catch (Exception e) {
            log.error("AI分析: 缺陷识别失败 {}", e.getMessage(), e);
            throw new IllegalStateException("AI 缺陷识别失败", e);
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
                "只输出下面这一个 JSON 对象，不要输出任何解释、标题、Markdown 代码块或前后缀文字 \n" +
                "【输出格式】\n" +
                " 只输出下面这一个 JSON 对象,不要输出任何解释/标题/Markdown 代码块 \n" +
                "{\n" +
                "  \"defects\": [\n" +
                "    {\n" +
                "      \"defectType\": \"缺陷类型\",\n" +
                "      \"defectPosition\": \"缺陷位置\",\n" +
                "      \"severity\": \"严重程度\",\n" +
                "      \"description\": \"缺陷描述\",\n" +
                "      \"confidence\": 95.0\n" +
                "    }\n" +
                "  ],\n" +
                "  \"params\": {\n" +
                "    \"temperature\": 1480.0,\n" +
                "    \"pressure\": 122.0,\n" +
                "    \"humidity\": 43.0,\n" +
                "    \"speed\": 24.0,\n" +
                "    \"operator\": \"操作员姓名\",\n" +
                "    \"remark\": \"工艺异常的直接线索,如'浇注温度超上限 40°C'\"\n" +
                "  }\n" +
                "}\n\n" +
                "【params 的规则】\n" +
                "1. 只填报告中【明确写出】的数值;没写的字段填 null,绝对不要编造\n" +
                "2. 单位:温度 °C、压力 MPa、湿度 %、速度 rpm\n" +
                "3. 如果整份报告都没有生产参数,params 输出 null\n\n" +
                "如果报告中没有发现缺陷,defects 输出空数组 []";
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
                log.warn("AI分析: 返回格式错误,没有 defects");
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

            log.info("AI分析：解析完成，识别到 {} 个缺陷", defects.size());

        } catch (Exception e) {
            throw new IllegalStateException("AI 返回内容无法解析为 JSON：" + e.getMessage(), e);
        }

        return defects;
    }

    private String cleanJsonResponse(String response) {
        if (response == null) return "{}";
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

        log.debug("AI分析:清理后的JSON长度={}", s.length());
        return s.trim();
    }

    private String getTextOrDefault(com.fasterxml.jackson.databind.JsonNode node,
                                    String fieldName, String defaultValue) {
        if (node.has(fieldName) && !node.get(fieldName).isNull()) {
            return node.get(fieldName).asText();
        }
        return defaultValue;
    }

    //增删改查
    @Override
    @EvictQcCache
    public Long createReport(QcReport report) {
        qcReportMapper.insert(report);
        return report.getId();
    }

    @Override
    @EvictQcCache
    public boolean updateReport(QcReport report) {
        return qcReportMapper.updateById(report) > 0;
    }

    //归档为删除报告的默认动作
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteReport(Long id,Long  operatorId) {
        if (id == null) return false;
        boolean ok = qcReportMapper.update(null,
                new LambdaUpdateWrapper<QcReport>()
                        .set(QcReport::getDeleted, 1)
                        .set(QcReport::getArchiveTime, LocalDateTime.now())
                        .set(QcReport::getArchivedBy, operatorId)
                        .eq(QcReport::getId, id)) > 0;
        evictQcCache();
        log.info("[Archive] 归档报告 id={} 操作人={}", id, operatorId);
        return ok;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean unarchiveReport(Long id,Long operatorId){
        if (id == null) return false;
        //update(null, wrapper.set(deleted,0)); MP 会给Update 自动追加deleted = 0 -> 已归档行匹配不到 -> 静默失效
        boolean ok =qcReportMapper.unarchiveRawById(id) > 0;
        evictQcCache();
        log.info("[Unarchive] 取消归档 id={} 操作人={} -> {}",id,operatorId,ok ? "成功":"无变化(报告不存在或未归档)");
        return ok;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean purgeReport(Long id) {
        if (id == null) return false;
        QcReport raw = qcReportMapper.selectRawById(id);
        if (raw == null) return false;
        qcDefectMapper.delete(new LambdaQueryWrapper<QcDefect>()
                .eq(QcDefect::getReportId, id));
        qcProductionParamMapper.delete(new LambdaQueryWrapper<QcProductionParam>()
                .eq(QcProductionParam::getReportId, id));
        //物理删主表
        boolean ok = qcReportMapper.deleteRawById(id) > 0;
        if (ok){
            deleteAttachmentFile(raw.getFileUrl());
            purgeMemory(raw.getReportNo());
        }
        evictQcCache();
        return ok;
    }

    private void deleteAttachmentFile(String fileUrl) {
        if (!StringUtils.hasText(fileUrl)) return;
        try {
            java.nio.file.Path base = java.nio.file.Paths.get("uploads").toAbsolutePath().normalize();
            java.nio.file.Path file = java.nio.file.Paths.get(fileUrl).toAbsolutePath().normalize();
            if (!file.startsWith(base)) {
                log.warn("[Purge] 附件不在 uploads/ 下,跳过: {}", file);
                return;
            }
            boolean deleted = java.nio.file.Files.deleteIfExists(file);
            log.info("[Purge] 删除附件 {} → {}", file, deleted ? "成功" : "文件不存在");
        } catch (Exception e) {
            log.warn("[Purge] 删除附件失败(不影响数据): {} , {}", fileUrl, e.getMessage());
        }
    }
    //请理结论档案(report_memory)
    //report_no是业务编号,可能被重复使用,不清会在ChatContextBUilder读到上一个旧结论
    //chat_message是用户会话资产,report_no删除会破坏别人会话的完整性
    private void purgeMemory(String reportNo) {
        if (!StringUtils.hasText(reportNo)) return;
        try{
            long deleted = repository.deleteByReportNo(reportNo);
            log.info("[Purge] 清理结论档案 reportNo={} 共 {} 条", reportNo,deleted);
        }catch (Exception e){
            log.warn("[Purge] 清理结论档案失败 reportNo={} , {}", reportNo, e.getMessage());
        }
    }


    private void evictQcCache() {
        // RedisConfig 配了 immediateWrites，clear() 会立即同步执行
        cacheManager.getCache("qcStats").clear();
        cacheManager.getCache("qcDetail").clear();
    }

    @Override
    @Cacheable(cacheNames = "qcDetail", key = "'report:' + #id", unless = "#result == null ")
    public QcReport getById(Long id) {
        return qcReportMapper.selectById(id);
    }


    @Override
    public Page<QcReport> pageList(int pageNum, int pageSize,
                                   String reportNo, String productName,
                                   String productBatch, String productionLine,
                                   String status) {
        //创建分页对象
        Page<QcReport> page = new Page<>(pageNum, pageSize);
        //查询条件
        LambdaQueryWrapper<QcReport> wrapper = new LambdaQueryWrapper<>();
        //动态条件(非空)
        if (StringUtils.hasText(reportNo)) wrapper.like(QcReport::getReportNo, reportNo);
        if (StringUtils.hasText(productName)) wrapper.like(QcReport::getProductName, productName);
        if (StringUtils.hasText(productBatch)) wrapper.like(QcReport::getProductBatch, productBatch);
        if (StringUtils.hasText(productionLine)) wrapper.like(QcReport::getProductionLine, productionLine);
        if (StringUtils.hasText(status)) wrapper.eq(QcReport::getStatus, status);
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
    @EvictQcCache
    public boolean updateStatus(Long id, String status) {
        QcReport report = new QcReport();
        report.setId(id);
        report.setStatus(status);
        return qcReportMapper.updateById(report) > 0;
    }

    @Override
    @EvictQcCache
    public boolean updateDefectCount(Long id, int defectCount, String severityLevel) {
        QcReport report = new QcReport();
        report.setId(id);
        report.setTotalDefects(defectCount);
        report.setSeverityLevel(severityLevel);
        return qcReportMapper.updateById(report) > 0;
    }

    //发送Kafka消息（只发消息不写库，因此无需失效缓存）
    @Override
    public void sendAnalyzeTask(Long reportId, String fileUrl, String fileType, String prompt) {
        try {
            java.util.Map<String, Object> msgMap = new java.util.HashMap<>();
            msgMap.put("reportId", reportId);
            msgMap.put("fileUrl", fileUrl);
            msgMap.put("fileType", fileType);
            msgMap.put("prompt", prompt);

            String json = objectMapper.writeValueAsString(msgMap);

            kafkaTemplate.send("qc-report-topic", json);

            log.info("Kafka消息发送成功,reportId:" + reportId);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException("Kafka序列化失败", e);
        }
    }

    //消费者 接受JSON字符串,解析后调用业务方法,Spring Kafka会自动来调用
    /* 告诉spring Kafka监听qc-report-topic主题,有消息自动调用 */
    @org.springframework.kafka.annotation.KafkaListener(topics = "qc-report-topic")
    /*参数是字符串不是Map,这是Kafka原始格式*/
    public void consumeAnalyzeTask(String messageJson) {
        Long reportId = null;
        try {
            java.util.Map<String, Object> msg = objectMapper.readValue(messageJson, java.util.Map.class);
            Object rid = msg.get("reportId");
            if (rid == null) {
                throw new IllegalArgumentException("消息缺少 reportId: " + messageJson);
            }
            reportId = ((Number) rid).longValue();

            String fileUrl = (String) msg.get("fileUrl");
            String fileType = (String) msg.get("fileType");
            String prompt = (String) msg.get("prompt");

            log.info("Kafka消费者接收到消息 reportId={}", reportId);
            processAnalyzeTask(reportId, fileUrl, fileType, prompt);
            log.info("Kafka消费者消息处理成功 reportId={}", reportId);
        } catch (Exception e) {
            log.error("Kafka消息处理失败,交给重试 reportId={}", reportId, e);
            throw new RuntimeException("Kafka消息处理失败 reportId=" + reportId, e);
        }
    }

    //执行AI分析
    @Override
    @EvictQcCache
    public void processAnalyzeTask(Long reportId, String fileUrl, String fileType, String prompt) {
        //报告不存在就失败,用了 selectById -> 已归档的报告查不到,正好符合"归档的报告不再重新分析"
        if (reportId == null || qcReportMapper.selectById(reportId) == null) {
            throw new IllegalArgumentException("报告不存在: reportId=" + reportId);
        }
        try {
            log.info("业务处理-分析报告{}", reportId);
            //状态更改为 PROCESSING
            updateStatus(reportId, "PROCESSING");
            //读取文件内容
            String fileContent = readFileContent(fileUrl, fileType);
            log.info("业务处理-提取文件长度{}", fileContent != null ? fileContent.length() : 0);
            //调模型
            String aiResponse = callAiForAnalysis(reportId, fileContent, prompt);
            AiAnalysisResult aiResult = aiResultParser.parse(aiResponse,reportId);
            if (!aiResult.getWarnings().isEmpty()) {
                log.warn("业务处理-AI解析告警 reportId={} {}", reportId, aiResult.getWarnings());
            }
            int defectCount = analysisResultApplier.apply(reportId, aiResult);
            updateStatus(reportId, "COMPLETED");
            log.info("业务处理-报告分析完毕{} 缺陷数:{}", reportId, defectCount);
        } catch (Exception e) {
            //兜底一次 FAILED,到那时别让他覆盖原始异常
            try {
                updateStatus(reportId, "FAILED");
            } catch (Exception ignore) {
                log.warn("更新 FAILED 状态也失败了 reportId={}", reportId);
            }
            log.error("报告分析失败, reportId={}", reportId, e);
            throw new RuntimeException("报告分析失败", e);
        }
    }

    @Override
    @Cacheable(cacheNames = "qcStats", key = "'trend:' + (#days <= 0 ? 7 : #days)")
    public List<Map<String, Object>> countByTimeTrend(int days) {
        if (days <= 0) {
            days = 7;  // 默认 7 天
        }
        return qcReportMapper.countByTimeTrend(days);
    }

    @Override
    @Cacheable(cacheNames = "qcStats", key = "'line'")
    public List<Map<String, Object>> countByProductionLine() {
        return qcReportMapper.countByProductionLine();
    }

    @Override
    @Cacheable(cacheNames = "qcStats", key = "'batch:' + (#limit <= 0 ? 10 : #limit)")
    public List<Map<String, Object>> countByProductBatch(int limit) {
        if (limit <= 0) {
            limit = 10;  // 默认显示前 10 个批次
        }
        return qcReportMapper.countByProductBatch(limit);
    }

    @Override
    public Map<String, Object> analyzeRootCause(Long reportId) {
        long start = System.currentTimeMillis();
        Map<String, Object> result = new java.util.HashMap<>();
        try {
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
            dev.langchain4j.data.message.ChatMessage userMessage = dev.langchain4j.data.message.UserMessage.from(prompt);
            dev.langchain4j.model.chat.response.ChatResponse response = chatModelFactory.getChatModel().chat(userMessage);
            String aiResponse = response.aiMessage().text();
            log.debug("根因分析AI 返回长度" + (aiResponse != null ? aiResponse.length() : 0));
            //解析
            result = parseRootCauseResponse(aiResponse);
            //附加原始数据
            result.put("defectCount", defects.size());
            result.put("severityLevel", report.getSeverityLevel());
        } catch (Exception e) {
            log.error("根因分析 失败" + e.getMessage(), e);
            result.put("error", "根因分析失败：" + e.getMessage());
        }
        log.info("[L0] 根因分析完成，耗时 {} ms", System.currentTimeMillis() - start);
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
            log.error("根因分析 JSON解析失败" + e.getMessage());
            result.put("error", "AI 返回解析失败");
            result.put("rawResponse", aiResponse);
        }

        return result;
    }

    @Cacheable(cacheNames = "qcDetail", key = "'reportNo:' + #reportNo", unless = "#result == null")
    @Override
    public QcReport getByReportNo(String reportNo) {
        if (!StringUtils.hasText(reportNo)) {
            return null;
        }
        return qcReportMapper.selectByReportNo(reportNo.trim());
    }
}
