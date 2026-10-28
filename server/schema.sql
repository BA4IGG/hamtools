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

-- 示例：创建一个运维可查的管理账号（可选）
-- INSERT INTO ham_users (callsign, nickname, password_hash)
-- VALUES ('BA4IGG', '作者', '$2a$10$替换为bcrypt哈希');
