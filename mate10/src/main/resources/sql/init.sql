-- ============================================================
-- Mate10 智能质检分析平台 - 数据库初始化脚本
-- ------------------------------------------------------------
-- 使用方式（任选其一）：
--   1. 命令行: mysql -uroot -p < init.sql
--   2. MySQL 客户端: source 本文件路径
-- 说明：
--   - 幂等脚本，可重复执行（全部使用 IF NOT EXISTS）
--   - 数据库默认 root/root 与 application.yml 一致
-- ============================================================

-- ---------- 1. 创建数据库 ----------

-- 业务/质检数据库（application.yml 默认数据源）
CREATE DATABASE IF NOT EXISTS `mate10db`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

-- 聊天数据库（ChatMapper 使用 mate10sql.chat 跨库查询）
CREATE DATABASE IF NOT EXISTS `mate10sql`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

-- ---------- 2. mate10db：业务表 ----------
USE `mate10db`;

-- 用户账号表
CREATE TABLE IF NOT EXISTS `sys_user` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `username`      VARCHAR(100) NOT NULL COMMENT '用户名（唯一）',
    `email`         VARCHAR(200) DEFAULT NULL COMMENT '邮箱（唯一）',
    `password`      VARCHAR(255) NOT NULL COMMENT 'BCrypt 加密后的密码',
    `nickname`      VARCHAR(100) DEFAULT NULL COMMENT '昵称',
    `phone`         BIGINT       DEFAULT NULL COMMENT '手机号（注意：建议后续改为 VARCHAR(20)，避免前导0丢失）',
    `status`        INT          DEFAULT 1 COMMENT '状态：1-正常 0-禁用',
    `lastLoginTime` DATETIME     DEFAULT NULL COMMENT '最后登录时间',
    `createTime`    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updateTime`    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `role`          INT          DEFAULT 2 COMMENT '角色：1-管理员 2-普通用户',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    UNIQUE KEY `uk_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户账号表';

-- 质检报告表
CREATE TABLE IF NOT EXISTS `qc_report` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '报告ID',
    `report_no`       VARCHAR(100) NOT NULL COMMENT '报告编号',
    `product_name`    VARCHAR(200) DEFAULT NULL COMMENT '产品名称',
    `product_batch`   VARCHAR(100) DEFAULT NULL COMMENT '产品批次',
    `production_line` VARCHAR(100) DEFAULT NULL COMMENT '生产线',
    `inspector`       VARCHAR(100) DEFAULT NULL COMMENT '质检员',
    `inspect_time`    DATETIME     DEFAULT NULL COMMENT '质检时间',
    `file_url`        VARCHAR(500) DEFAULT NULL COMMENT '附件地址',
    `file_type`       VARCHAR(50)  DEFAULT NULL COMMENT '附件类型',
    `status`          VARCHAR(50)  DEFAULT NULL COMMENT '报告状态',
    `total_defects`   INT          DEFAULT 0 COMMENT '缺陷总数',
    `severity_level`  VARCHAR(50)  DEFAULT NULL COMMENT '严重等级',
    `create_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='质检报告表';

-- 缺陷表
CREATE TABLE IF NOT EXISTS `qc_defect` (
    `id`              BIGINT         NOT NULL AUTO_INCREMENT COMMENT '缺陷ID',
    `report_id`       BIGINT         NOT NULL COMMENT '所属报告ID',
    `defect_type`     VARCHAR(100)   DEFAULT NULL COMMENT '缺陷类型',
    `defect_position` VARCHAR(200)   DEFAULT NULL COMMENT '缺陷位置',
    `severity`        VARCHAR(50)    DEFAULT NULL COMMENT '严重程度',
    `description`     VARCHAR(1000)  DEFAULT NULL COMMENT '缺陷描述',
    `confidence`      DECIMAL(10,4)  DEFAULT NULL COMMENT 'AI 置信度',
    `is_confirmed`    INT            DEFAULT 0 COMMENT '是否人工确认',
    `create_time`     DATETIME       DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_report_id` (`report_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='缺陷表';

-- 生产参数表
CREATE TABLE IF NOT EXISTS `qc_production_param` (
    `id`          BIGINT         NOT NULL AUTO_INCREMENT COMMENT '参数ID',
    `report_id`   BIGINT         NOT NULL COMMENT '所属报告ID',
    `temperature` DECIMAL(10,2)  DEFAULT NULL COMMENT '温度',
    `pressure`    DECIMAL(10,2)  DEFAULT NULL COMMENT '压力',
    `humidity`    DECIMAL(10,2)  DEFAULT NULL COMMENT '湿度',
    `speed`       DECIMAL(10,2)  DEFAULT NULL COMMENT '速度',
    `operator`    VARCHAR(100)   DEFAULT NULL COMMENT '操作员',
    `remark`      VARCHAR(1000)  DEFAULT NULL COMMENT '备注',
    `create_time` DATETIME       DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_report_id` (`report_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='生产参数表';

-- 聊天记录表
-- 注意：ChatRecordMapper 查询不带库前缀（默认数据源），因此本表必须建在 mate10db
CREATE TABLE IF NOT EXISTS `chat_record` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    `user_id`     BIGINT       NOT NULL COMMENT '用户ID',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '创建时间',
    `chat_id`     BIGINT       NOT NULL COMMENT '会话ID',
    `keyword`     VARCHAR(255) NOT NULL COMMENT '关键词',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天记录表';

-- ---------- 3. mate10sql：聊天表 ----------
USE `mate10sql`;

-- 会话表（ChatMapper 使用 mate10sql.chat 跨库查询）
CREATE TABLE IF NOT EXISTS `chat` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '...',
    `userId`     BIGINT       DEFAULT NULL COMMENT '用户ID',
    `title`      VARCHAR(255) DEFAULT NULL COMMENT '会话标题',
    `createTime` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updateTime` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`userId`) USING BTREE,
    KEY `idx_update_time` (`updateTime`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天会话表';

-- ---------- 4.（可选）种子数据 ----------
-- 以下为管理员账号示例，密码需为 BCrypt 加密值。
-- 生产环境请通过注册接口创建账号，或替换占位 hash：
-- INSERT INTO mate10db.sys_user (username, email, password, nickname, status, role)
-- VALUES ('admin', 'admin@mate10.com', '$2a$10$替换为真实BCrypt哈希', '管理员', 1, 1);

-- 初始化完成
