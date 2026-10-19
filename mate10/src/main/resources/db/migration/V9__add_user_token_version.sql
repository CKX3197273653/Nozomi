-- ============================================================
-- V9: 用户 token 版本号
-- 用途:改密码 / 重置密码 / 禁用时自增 → 使【该用户所有已签发 token】立即失效
-- 前置:确认没有重复列(幂等守卫,与 V5~V8 同风格)
-- ============================================================

SET @exist := (SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE()
                 AND TABLE_NAME   = 'sys_user'
                 AND COLUMN_NAME  = 'token_version');
SET @ddl := IF(@exist = 0,
    'ALTER TABLE sys_user ADD COLUMN `token_version` INT NOT NULL DEFAULT 0 COMMENT ''token 版本号:自增即让旧 token 全部失效''',
    'SELECT ''token_version 已存在,跳过''');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;