-- ============================================================
-- V5: 补齐 V1 声明过、但手工建的库里缺失的 email 唯一索引
-- ⚠️ 执行前先查重，有脏数据会失败：
--    SELECT email, COUNT(*) c FROM sys_user
--     WHERE email IS NOT NULL GROUP BY email HAVING c > 1;
-- ============================================================
SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME   = 'sys_user'
                      AND INDEX_NAME   = 'uk_email');
SET @ddl := IF(@idx_exists = 0,
    'ALTER TABLE `sys_user` ADD UNIQUE KEY `uk_email` (`email`)',
    'DO 0');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;