-- ============================================================
-- V8: 报告归档(逻辑删除) + 报告编号唯一索引
-- 说明:MySQL 8 不支持 ADD COLUMN IF NOT EXISTS,
--       统一用 information_schema 守卫实现幂等(与 V5~V7 同风格)
-- 前置:已确认 report_no 无重复
-- ============================================================

-- ① 归档标记:0=正常,1=已归档
SET @exist := (SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE()
                 AND TABLE_NAME   = 'qc_report'
                 AND COLUMN_NAME  = 'deleted');
SET @ddl := IF(@exist = 0,
    'ALTER TABLE qc_report ADD COLUMN `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT ''逻辑删除:0正常 1已归档''',
    'SELECT ''deleted 已存在,跳过''');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- ② 归档时间(可审计谁在什么时候归档的)
SET @exist := (SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE()
                 AND TABLE_NAME   = 'qc_report'
                 AND COLUMN_NAME  = 'archive_time');
SET @ddl := IF(@exist = 0,
    'ALTER TABLE qc_report ADD COLUMN `archive_time` DATETIME DEFAULT NULL COMMENT ''归档时间''',
    'SELECT ''archive_time 已存在,跳过''');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- ③ 报告编号唯一索引
SET @exist := (SELECT COUNT(*) FROM information_schema.STATISTICS
               WHERE TABLE_SCHEMA = DATABASE()
                 AND TABLE_NAME   = 'qc_report'
                 AND INDEX_NAME   = 'uk_report_no');
SET @ddl := IF(@exist = 0,
    'ALTER TABLE qc_report ADD UNIQUE KEY `uk_report_no` (`report_no`)',
    'SELECT ''uk_report_no 已存在,跳过''');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- ④ 列表默认"只查未归档 + 按时间排序"的组合索引
SET @exist := (SELECT COUNT(*) FROM information_schema.STATISTICS
               WHERE TABLE_SCHEMA = DATABASE()
                 AND TABLE_NAME   = 'qc_report'
                 AND INDEX_NAME   = 'idx_deleted_create_time');
SET @ddl := IF(@exist = 0,
    'ALTER TABLE qc_report ADD KEY `idx_deleted_create_time` (`deleted`, `create_time`)',
    'SELECT ''idx_deleted_create_time 已存在,跳过''');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;