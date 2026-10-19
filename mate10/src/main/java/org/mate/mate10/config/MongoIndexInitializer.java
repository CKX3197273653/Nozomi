package org.mate.mate10.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MongoIndexInitializer {

    private final MongoTemplate mongoTemplate;

    @PostConstruct
    public void ensureIndexes() {
        createIndexes("chat_messages", """
                [
                  { "key": { "chat_id": 1, "create_time": -1 }, "name": "idx_chat_time" },
                  { "key": { "report_no": 1 },                    "name": "idx_report_no" }
                ]
                """);
        //部分唯一索引在同一个report_no下,superseded=false且最多一条
        //名字直接用已经存在的report_no_1下的 -- 名字相同+定义相同 == 幂等no-op
        createIndexes("report_memory", """
                [
                  { "key": { "report_no": 1 },
                    "name": "report_no_1",
                    "unique": true,
                    "partialFilterExpression": { "superseded": false } }
                ]
                """);
    }

    private void createIndexes(String collection, String indexesJson) {
        try {
            Document cmd = Document.parse(
                    "{ \"createIndexes\": \"" + collection + "\", \"indexes\": " + indexesJson + " }");
            mongoTemplate.executeCommand(cmd);
            log.info("[MongoIndex] {} 索引就绪", collection);
        } catch (Exception e) {
            // 已存在同名但定义不同 → 这里会报 IndexOptionsConflict,记下来但不影响启动
            log.warn("[MongoIndex] {} 建索引失败(可能是已有同名索引定义不同): {}",
                    collection, e.getMessage());
        }
    }
}
