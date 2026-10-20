-- ============================================================
-- V10: 审计补强
--   ① chat_record.report_no   —— 让"谁讨论过哪份报告"可精确关联
--   ② qc_report.archived_by   —— 归档也记清楚【谁】归档的
-- 幂等守卫(与 V8/V9 同风格)
-- ============================================================

-- ① chat_record.report_no
SET @exist := (SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE()
                 AND TABLE_NAME   = 'chat_record'
                 AND COLUMN_NAME  = 'report_no');
SET @ddl := IF(@exist = 0,
    'ALTER TABLE chat_record ADD COLUMN `report_no` VARCHAR(100) DEFAULT NULL COMMENT ''关联的报告编号(审计用)''',
    'SELECT ''chat_record.report_no 已存在,跳过''');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- ② chat_record.report_no 索引(审计接口会按它查)
SET @exist := (SELECT COUNT(*) FROM information_schema.STATISTICS
               WHERE TABLE_SCHEMA = DATABASE()
                 AND TABLE_NAME   = 'chat_record'
                 AND INDEX_NAME   = 'idx_report_no');
SET @ddl := IF(@exist = 0,
    'ALTER TABLE chat_record ADD KEY `idx_report_no` (`report_no`)',
    'SELECT ''idx_report_no 已存在,跳过''');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- ③ qc_report.archived_by
SET @exist := (SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE()
                 AND TABLE_NAME   = 'qc_report'
                 AND COLUMN_NAME  = 'archived_by');
SET @ddl := IF(@exist = 0,
    'ALTER TABLE qc_report ADD COLUMN `archived_by` BIGINT DEFAULT NULL COMMENT ''归档操作人 userId''',
    'SELECT ''qc_report.archived_by 已存在,跳过''');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;