package org.mate.mate10.controller;

import org.mate.mate10.common.Result;
import org.mate.mate10.service.VectorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/vector")
public class VectorController {
    @Autowired
    private VectorService vectorService;

    //创建集合
    @PostMapping("collection")
    public Result<Void> createCollection(@RequestParam String collName,
                                           @RequestParam int dim) {
        vectorService.createCollection(collName,dim);
        vectorService.createIndex(collName);
        vectorService.loadCollection(collName);
        return Result.success();
    }

    //插入向量
    @PostMapping
    public Result<Long> insert(@RequestParam String collName,
                                 @RequestBody List<List<Float>> vectors){
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < vectors.size(); i++) {
            ids.add(System.currentTimeMillis() + i);
        }
        Long cnt = vectorService.insert(collName,ids,vectors);
        return Result.success();
    }

    //相似度检索
    @PostMapping("/search")
    public Result<List<List<Long>>> search(@RequestParam String collName,
                                           @RequestParam List<Float> vector,
                                           @RequestParam(defaultValue = "5")int topK) {
        List<List<Long>> result = vectorService.searchSimilar(collName, List.of(vector), topK);
        return Result.success(result);
    }

    //按ID查询
    @PostMapping("/query")
    public List<List<Object>> query(@RequestParam String collName, @RequestBody List<Long> ids) {
        return vectorService.queryByIds(collName, ids);
    }
    // 删除向量
    @DeleteMapping("/delete")
    public Result<Long> delete(@RequestParam String collName, @RequestBody List<Long> ids) {
        long cnt = vectorService.deleteByIds(collName, ids);
        return Result.success(cnt);
    }

    // 更新向量
    @PutMapping("/update")
    public Result<Long> update(@RequestParam String collName,
                                 @RequestParam Long id,
                                 @RequestBody List<Float> vector) {

        Long result = vectorService.updateById(collName, id, vector);
        Long cnt = (result != null) ? result : 0L;
        return Result.success(cnt);
    }

    // 删除整个集合
    @DeleteMapping("/collection")
    public String dropCollection(@RequestParam String collName) {
        vectorService.dropCollection(collName);
        return "集合已删除：" + collName;
    }

    // 判断集合是否存在
    @GetMapping("/collection/exists")
    public boolean hasCollection(@RequestParam String collName) {
        return vectorService.hasCollection(collName);
    }
}
