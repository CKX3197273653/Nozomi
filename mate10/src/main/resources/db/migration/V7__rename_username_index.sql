-- ============================================================
-- V7: 统一索引命名
--     手工建的老库里 username 的唯一索引叫 `username`，
--     而 V1 声明的是 `uk_username` —— 功能等价，但名字不一致。
--
-- 为什么要统一（不只是"好看"）：
--   ① 结构对比时不再产生噪音 —— "两库签名哈希"能当【结构漂移探测器】✅
--   ② 报错信息里的 key 名一致 —— 例如
--      Duplicate entry 'xxx' for key 'sys_user.uk_username'
--      名字不统一时，不同环境报出的 key 名不同，排查时会困惑 ✅
--
-- ⚠️ RENAME INDEX 在 MySQL 8 / InnoDB 下是【元数据操作】，
--    不重建表、不搬数据 → 瞬时完成，零风险 ✅
-- ============================================================

SET @old_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME   = 'sys_user'
                      AND INDEX_NAME   = 'username');

SET @new_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME   = 'sys_user'
                      AND INDEX_NAME   = 'uk_username');

-- 只有当【旧名存在】且【新名不存在】时才重命名
-- （防止"重命名到一个已存在的名字" → Duplicate key name ❌）
SET @ddl := IF(@old_exists > 0 AND @new_exists = 0,
    'ALTER TABLE `sys_user` RENAME INDEX `username` TO `uk_username`',
    'DO 0');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;