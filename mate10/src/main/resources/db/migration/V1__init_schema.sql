-- ============================================================
-- V1: 初始化业务表结构
-- 说明：Flyway 在【目标数据库】中执行本脚本，因此不需要 CREATE DATABASE / USE
-- ============================================================

-- 用户账号表
CREATE TABLE IF NOT EXISTS `sys_user` (
                                          `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
                                          `username`      VARCHAR(100) NOT NULL COMMENT '用户名（唯一）',
                                          `email`         VARCHAR(200) DEFAULT NULL COMMENT '邮箱（唯一）',
                                          `password`      VARCHAR(255) NOT NULL COMMENT 'BCrypt 加密后的密码',
                                          `nickname`      VARCHAR(100) DEFAULT NULL COMMENT '昵称',
                                          `phone`         BIGINT       DEFAULT NULL COMMENT '手机号',
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
CREATE TABLE IF NOT EXISTS `chat_record` (
                                             `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '记录ID',
                                             `user_id`     BIGINT       NOT NULL COMMENT '用户ID',
                                             `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '创建时间',
                                             `chat_id`     BIGINT       NOT NULL COMMENT '会话ID',
                                             `keyword`     VARCHAR(255) NOT NULL COMMENT '关键词',
                                             PRIMARY KEY (`id`),
                                             KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天记录表';

-- 会话表（列名驼峰，与 Chat 实体的 @TableField 映射一致）
CREATE TABLE IF NOT EXISTS `chat` (
                                      `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '会话ID',
                                      `userId`     BIGINT       DEFAULT NULL COMMENT '用户ID',
                                      `title`      VARCHAR(255) DEFAULT NULL COMMENT '会话标题',
                                      `createTime` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                      `updateTime` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                      PRIMARY KEY (`id`),
                                      KEY `idx_user_id` (`userId`) USING BTREE,
                                      KEY `idx_update_time` (`updateTime`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天会话表';