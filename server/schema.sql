-- HAM工具合集 账号库表结构
-- 用法：mysql -u root -p hamtools < schema.sql

CREATE DATABASE IF NOT EXISTS `hamtools`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE `hamtools`;

CREATE TABLE IF NOT EXISTS `ham_users` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `callsign`      VARCHAR(16)  NOT NULL COMMENT '业余电台呼号，全大写，唯一',
  `nickname`      VARCHAR(64)  DEFAULT NULL COMMENT '昵称，可为空',
  `password_hash` VARCHAR(255) NOT NULL COMMENT 'bcrypt 哈希',
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_callsign` (`callsign`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- QSL 卡片交换（v1.3）。服务端启动时的 db.ensureSchema() 也会自动建这三张表，
-- 这里保留一份便于手工部署、备份与审查。
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `qsl_profiles` (
  `user_id`       BIGINT UNSIGNED NOT NULL COMMENT '关联 ham_users.id',
  `callsign`      VARCHAR(16)  NOT NULL COMMENT '冗余呼号，便于名录查询，保存时以账号表为准',
  `display_name`  VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '收件人 / 显示名',
  `grid`          VARCHAR(8)   NOT NULL DEFAULT '' COMMENT '梅登海德网格',
  `qth`           VARCHAR(128) NOT NULL DEFAULT '' COMMENT '公开的 QTH 概述，不含详细地址',
  `email`         VARCHAR(128) NOT NULL DEFAULT '',
  `address`       TEXT         DEFAULT NULL COMMENT '邮寄详细地址：敏感字段，仅已接受交换的对方可见',
  `note`          VARCHAR(255) NOT NULL DEFAULT '' COMMENT '交换偏好说明',
  `paper_enabled` TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否接受纸质卡片交换',
  `eqsl_enabled`  TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否接受 eQSL 交换',
  `discoverable`  TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否出现在可交换名录（仅脱敏字段）',
  `card_id`       BIGINT UNSIGNED DEFAULT NULL COMMENT '当前使用的卡片图片',
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `uk_qsl_callsign` (`callsign`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `qsl_cards` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`    BIGINT UNSIGNED NOT NULL,
  `mode`       VARCHAR(8)   NOT NULL COMMENT 'paper | eqsl',
  `file_name`  VARCHAR(128) NOT NULL COMMENT '磁盘随机文件名，不含用户可控路径',
  `mime`       VARCHAR(32)  NOT NULL,
  `size_bytes` INT UNSIGNED NOT NULL,
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_qsl_cards_user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `qsl_exchanges` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `from_user_id`  BIGINT UNSIGNED NOT NULL COMMENT '发起方账号',
  `to_user_id`    BIGINT UNSIGNED DEFAULT NULL COMMENT '接收方账号；NULL 表示对方未注册，仅作历史记录',
  `peer_callsign` VARCHAR(16)  NOT NULL COMMENT '对方呼号',
  `mode`          VARCHAR(8)   NOT NULL COMMENT 'paper | eqsl',
  `qso_date`      VARCHAR(16)  NOT NULL DEFAULT '',
  `qso_band`      VARCHAR(16)  NOT NULL DEFAULT '',
  `qso_mode`      VARCHAR(16)  NOT NULL DEFAULT '',
  `rst_sent`      VARCHAR(8)   NOT NULL DEFAULT '',
  `rst_rcvd`      VARCHAR(8)   NOT NULL DEFAULT '',
  `note`          VARCHAR(255) NOT NULL DEFAULT '',
  `status`        VARCHAR(16)  NOT NULL DEFAULT 'pending' COMMENT 'pending/accepted/sent/completed/declined/cancelled/recorded',
  `from_card_id`  BIGINT UNSIGNED DEFAULT NULL,
  `to_card_id`    BIGINT UNSIGNED DEFAULT NULL,
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_qsl_ex_from` (`from_user_id`),
  INDEX `idx_qsl_ex_to` (`to_user_id`),
  INDEX `idx_qsl_ex_peer` (`peer_callsign`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 考试练习云同步（登录后才使用）。服务端启动时的 db.ensureSchema() 也会自动建表。
-- data 只存题号进度、复习数字、顺序练习断点与成绩摘要的 JSON，不含题目正文，
-- 不含用户所选答案，也不含任何个人资料；字段白名单见 src/routes/exam.js。
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `exam_progress` (
  `user_id`     BIGINT UNSIGNED NOT NULL COMMENT '关联 ham_users.id',
  `data`        MEDIUMTEXT   NOT NULL,
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 示例：创建一个运维可查的管理账号（可选）
-- INSERT INTO ham_users (callsign, nickname, password_hash)
-- VALUES ('BA4IGG', '作者', '$2a$10$替换为bcrypt哈希');
