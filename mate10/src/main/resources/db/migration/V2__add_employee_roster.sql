-- ============================================================
-- V2: 员工工号名册（注册白名单 + 占用标记）
-- 用 IF NOT EXISTS：已有手工建过这张表的库重复执行也不报错
-- ============================================================
CREATE TABLE IF NOT EXISTS `sys_employee` (
                                              `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
                                              `emp_no`        VARCHAR(32)  NOT NULL                COMMENT '员工工号（唯一）',
    `name`          VARCHAR(64)  DEFAULT NULL            COMMENT '姓名',
    `department`    VARCHAR(64)  DEFAULT NULL            COMMENT '部门',
    `registered`    TINYINT      NOT NULL DEFAULT 0      COMMENT '0-未占用 1-已被注册占用',
    `user_id`       BIGINT       DEFAULT NULL            COMMENT '占用该工号的用户ID',
    `register_time` DATETIME     DEFAULT NULL            COMMENT '注册（占用）时间',
    `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_emp_no` (`emp_no`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='员工工号名册';