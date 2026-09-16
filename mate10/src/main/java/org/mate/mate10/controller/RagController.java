package org.mate.mate10.controller;

import org.mate.mate10.dto.eval.EvalReport;
import org.mate.mate10.service.RagEvalService;
import org.springframework.core.io.Resource;
import org.mate.mate10.common.Result;
import org.mate.mate10.service.RagService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@RestController
@RequestMapping("/rag")
public class RagController {

    @Autowired
    private RagService ragService;
    @Autowired
    private RagEvalService ragEvalService;

    @PostMapping("/add-text")
    public Result<Void> addText(@RequestBody String text) {
        ragService.addText(text);
        return Result.success();
    }

    @PostMapping("/upload")
    public Result<Void> uploadFile(@RequestParam("file") MultipartFile file) throws IOException {
        Path tempFile = Files.createTempFile("rag-", ".txt");
        Files.copy(file.getInputStream(), tempFile, StandardCopyOption.REPLACE_EXISTING);
        ragService.addDocument(tempFile);
        Files.delete(tempFile);
        return Result.success();
    }

    @PostMapping("/ask")
    public Result<String> ask(@RequestBody String question) {
        String answer = ragService.ask(question);
        return Result.success(answer);
    }

    @DeleteMapping("/clear")
    public Result<Void> clear() {
        ragService.clearKnowledgeBase();
        return Result.success();
    }
    //预设上下文
    @PostMapping("/qc-ask")
    public Result<String> qcAsk(@RequestBody String question) {
        String qcPrompt = "你是一个专业的质量检测助手，请根据以下知识回答问题。\n\n";
        String answer = ragService.ask(qcPrompt + question);
        return Result.success(answer);
    }
    //预设质检标准知识库
//    @PostMapping("/init-qc-knowledge")
//    public Result<Void> initQcKnowledge() {
//        String qcStandards = """
//        【质量检测标准】
//
//        1. 外观检测标准：
//           - 划痕：深度不超过 0.1mm，长度不超过 5mm 为合格
//           - 裂纹：任何可见裂纹均为不合格
//           - 色差：ΔE 值小于 2.0 为合格
//           - 变形：扭曲度不超过 0.5mm 为合格
//
//        2. 尺寸检测标准：
//           - 长度公差：±0.05mm
//           - 宽度公差：±0.03mm
//           - 高度公差：±0.02mm
//
//        3. 重量检测标准：
//           - 单件重量公差：±5%
//
//        4. 常见缺陷处理建议：
//           - 划痕：建议抛光处理
//           - 裂纹：建议报废或焊接修复
//           - 色差：建议重新喷涂
//           - 变形：建议校正或更换
//        """;
//        ragService.addText(qcStandards);
//        return Result.success();
//    }
    @PostMapping("/init-qc-knowledge")
    public Result<Integer> initQcKnowledge() throws IOException {

        ragService.clearKnowledgeBase();

        Resource[] resources = new PathMatchingResourcePatternResolver()
                .getResources("classpath:knowledge/*.md");

        int count = 0;
        for (Resource r : resources) {
            String text = new String(r.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            ragService.addText(text);
            count++;
        }
        return Result.success(count);   // 返回加载了多少篇
    }

    @GetMapping("/eval")
    public Result<EvalReport> eval() {
        try{
            return Result.success(ragEvalService.run());
        }catch(Exception e){
            return Result.error("评测失败: " + e.getMessage());

        }
    }
}