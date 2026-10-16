-- ============================================================
-- V6: chat 表补索引 —— V1 声明过，但手工建的库缺失
--     idx_user_id      → 「按用户查会话」不用全表扫描
--     idx_update_time  → 「按最后活跃排序」不用全表扫描
-- ============================================================
SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME   = 'chat'
                      AND INDEX_NAME   = 'idx_user_id');
SET @ddl := IF(@idx_exists = 0,
    'ALTER TABLE `chat` ADD KEY `idx_user_id` (`userId`) USING BTREE', 'DO 0');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME   = 'chat'
                      AND INDEX_NAME   = 'idx_update_time');
SET @ddl := IF(@idx_exists = 0,
    'ALTER TABLE `chat` ADD KEY `idx_update_time` (`updateTime`) USING BTREE', 'DO 0');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;