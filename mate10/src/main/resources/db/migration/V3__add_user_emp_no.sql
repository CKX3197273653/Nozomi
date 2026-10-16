-- ============================================================
-- V3: sys_user 增加 emp_no（可空 + 唯一），把注册用户与工号绑定
-- MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，用 information_schema 判断保证幂等
-- ============================================================
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME   = 'sys_user'
                      AND COLUMN_NAME  = 'emp_no');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `sys_user` ADD COLUMN `emp_no` VARCHAR(32) DEFAULT NULL COMMENT ''员工工号''',
    'DO 0');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME   = 'sys_user'
                      AND INDEX_NAME   = 'uk_user_emp_no');
SET @ddl := IF(@idx_exists = 0,
    'ALTER TABLE `sys_user` ADD UNIQUE KEY `uk_user_emp_no` (`emp_no`)',
    'DO 0');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;