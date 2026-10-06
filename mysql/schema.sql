SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET character_set_client = utf8mb4;
SET character_set_connection = utf8mb4;
SET character_set_results = utf8mb4;

-- ---------------------------------------------------------------------
-- 高尔夫球场 · 球洞 / 开球时间 / 球员 / 赛事 / 成绩
-- 库名 golf_course；application.yml 里 ddl-auto: none，表结构全部由本文件在 MySQL 镜像构建期导入。
-- 注意：mysql 容器是 UTC，种子数据里的时间一律写 UTC_TIMESTAMP() + INTERVAL 8 HOUR。

CREATE DATABASE IF NOT EXISTS golf_course
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE golf_course;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS scorecard_version_hole;
DROP TABLE IF EXISTS scorecard_version;
DROP TABLE IF EXISTS scorecard_hole;
DROP TABLE IF EXISTS scorecard;
DROP TABLE IF EXISTS tournament_entry;
DROP TABLE IF EXISTS score;
DROP TABLE IF EXISTS leaderboard;
DROP TABLE IF EXISTS tee_time;
DROP TABLE IF EXISTS tournament;
DROP TABLE IF EXISTS player;
DROP TABLE IF EXISTS hole;
SET FOREIGN_KEY_CHECKS = 1;

-- ---------------- 球洞 ----------------
-- 状态：开放 / 维护 / 封闭；par 取 3 / 4 / 5
CREATE TABLE hole (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    hole_no INT NOT NULL UNIQUE,
    par INT NOT NULL,
    yardage INT NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 球员 ----------------
-- handicap 差点取值范围 0 ~ 54
CREATE TABLE player (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    player_no VARCHAR(16) NOT NULL UNIQUE,
    name VARCHAR(48) NOT NULL,
    handicap INT NOT NULL,
    phone VARCHAR(24),
    note VARCHAR(64),
    created_at DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 开球时间 ----------------
-- 同一球员同一天同一时段唯一（防止重复预订）；状态：可订 / 已订
CREATE TABLE tee_time (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    play_date DATE NOT NULL,
    player_id BIGINT NOT NULL,
    slot VARCHAR(8) NOT NULL,
    status VARCHAR(16) NOT NULL,
    note VARCHAR(64),
    created_at DATETIME,
    UNIQUE KEY uk_tee_player_slot (player_id, play_date, slot)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 赛事 ----------------
-- 状态：报名中 → 进行中 → 已结束
CREATE TABLE tournament (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tournament_no VARCHAR(24) NOT NULL UNIQUE,
    name VARCHAR(64) NOT NULL,
    start_date DATE,
    end_date DATE,
    status VARCHAR(16) NOT NULL,
    total_rounds INT NOT NULL DEFAULT 1,
    location VARCHAR(48),
    note VARCHAR(64),
    created_at DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 赛事排行榜（乐观锁演示实体） ----------------
-- version 为 @Version 乐观锁版本号
CREATE TABLE leaderboard (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tournament_id BIGINT NOT NULL,
    round_no INT NOT NULL,
    leader_name VARCHAR(48),
    total_score INT,
    version BIGINT,
    updated_at DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 成绩（按洞录入） ----------------
CREATE TABLE score (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    player_id BIGINT NOT NULL,
    tournament_id BIGINT NOT NULL,
    hole_id BIGINT NOT NULL,
    round_no INT NOT NULL,
    strokes INT NOT NULL,
    created_at DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 赛事参赛名单 ----------------
-- 收口检查以该名单为准：每名参赛球员的每个应打轮次都必须有「已签核」记分卡
CREATE TABLE tournament_entry (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tournament_id BIGINT NOT NULL,
    player_id BIGINT NOT NULL,
    created_at DATETIME,
    UNIQUE KEY uk_tournament_player (tournament_id, player_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 赛事记分卡签核 ----------------
-- 状态：草稿 / 待球员确认 / 争议中 / 已签核；version 为页面与 JPA 共用乐观版本
CREATE TABLE scorecard (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tournament_id BIGINT NOT NULL,
    player_id BIGINT NOT NULL,
    round_no INT NOT NULL,
    status VARCHAR(24) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    submitted_by VARCHAR(48),
    submitted_at DATETIME,
    confirmed_by VARCHAR(48),
    confirmed_at DATETIME,
    last_action VARCHAR(32),
    updated_at DATETIME,
    created_at DATETIME,
    UNIQUE KEY uk_scorecard_round (tournament_id, player_id, round_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 当前逐洞数据；洞 ID 与洞号均唯一，从数据库层阻断重复洞号
CREATE TABLE scorecard_hole (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    scorecard_id BIGINT NOT NULL,
    hole_id BIGINT NOT NULL,
    hole_no INT NOT NULL,
    par INT NOT NULL,
    strokes INT,
    proposed_strokes INT,
    dispute_reason VARCHAR(255),
    disputed_by VARCHAR(48),
    disputed_at DATETIME,
    ruling_decision VARCHAR(16),
    ruling_note VARCHAR(255),
    ruled_by VARCHAR(48),
    ruled_at DATETIME,
    UNIQUE KEY uk_scorecard_hole (scorecard_id, hole_id),
    UNIQUE KEY uk_scorecard_hole_no (scorecard_id, hole_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 不可变快照主表：每次提交/确认/争议/裁决各存 BEFORE、AFTER 两条
CREATE TABLE scorecard_version (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    scorecard_id BIGINT NOT NULL,
    card_version BIGINT NOT NULL,
    snapshot_type VARCHAR(16) NOT NULL,
    action VARCHAR(32) NOT NULL,
    status VARCHAR(24) NOT NULL,
    actor_role VARCHAR(16),
    actor_name VARCHAR(48),
    note VARCHAR(255),
    created_at DATETIME NOT NULL,
    KEY idx_scorecard_version_card (scorecard_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 不可变快照逐洞明细
CREATE TABLE scorecard_version_hole (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    version_id BIGINT NOT NULL,
    hole_no INT NOT NULL,
    par INT NOT NULL,
    strokes INT,
    proposed_strokes INT,
    dispute_reason VARCHAR(255),
    disputed_by VARCHAR(48),
    ruling_decision VARCHAR(16),
    ruling_note VARCHAR(255),
    ruled_by VARCHAR(48),
    KEY idx_version_hole_version (version_id, hole_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================================
-- 种子数据
-- =====================================================================

INSERT INTO hole (id, hole_no, par, yardage, status, created_at) VALUES
(1, 1, 4, 412, '开放', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 60 DAY),
(2, 2, 5, 528, '开放', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 60 DAY),
(3, 3, 3, 168, '开放', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 60 DAY),
(4, 4, 4, 395, '开放', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 60 DAY),
(5, 5, 4, 430, '维护', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 60 DAY),
(6, 6, 3, 190, '开放', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 60 DAY),
(7, 7, 5, 545, '开放', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 60 DAY),
(8, 8, 4, 408, '开放', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 60 DAY),
(9, 9, 3, 205, '封闭', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 60 DAY);

INSERT INTO player (id, player_no, name, handicap, phone, note, created_at) VALUES
(1,  'P-001', '林浩',     12, '13801012001', '单差点进阶',     UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 50 DAY),
(2,  'P-002', '陈雨桐',   18, '13801012002', '周末常客',       UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 50 DAY),
(3,  'P-003', '王启明',    8, '13801012003', '低差点',         UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 50 DAY),
(4,  'P-004', '赵子涵',   24, '13801012004', '新手',           UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 50 DAY),
(5,  'P-005', '刘振华',    0, '13801012005', '职业教练',       UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 50 DAY),
(6,  'P-006', '孙佳怡',   15, '13801012006', '女子组',         UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 50 DAY),
(7,  'P-007', '周文博',   30, '13801012007', '长者组',         UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 50 DAY),
(8,  'P-008', '吴晓彤',   20, '13801012008', '业余赛事常客',   UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 50 DAY),
(9,  'P-009', '郑凯',     10, '13801012009', '稳定发挥',       UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 50 DAY),
(10, 'P-010', '黄诗韵',   28, '13801012010', '新人',           UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 50 DAY);

INSERT INTO tee_time (id, play_date, player_id, slot, status, note, created_at) VALUES
(1,  '2026-10-01', 1,  '07:00', '已订', '晨场第一开球',   UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 6 DAY),
(2,  '2026-10-01', 3,  '07:10', '已订', NULL,             UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 6 DAY),
(3,  '2026-10-01', 5,  '09:00', '已订', '教练陪打',       UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 6 DAY),
(4,  '2026-10-01', 2,  '09:10', '可订', '待确认',         UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 6 DAY),
(5,  '2026-10-02', 4,  '08:00', '已订', '新手首场',       UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 5 DAY),
(6,  '2026-10-02', 6,  '08:10', '已订', NULL,             UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 5 DAY),
(7,  '2026-10-02', 7,  '13:30', '已订', '长者组',         UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 5 DAY),
(8,  '2026-10-03', 8,  '10:00', '已订', '赛事热身',       UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 4 DAY),
(9,  '2026-10-03', 9,  '10:10', '可订', '待拼场',         UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 4 DAY),
(10, '2026-10-03', 10, '15:00', '已订', '新人练习',       UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 4 DAY);

INSERT INTO tournament (id, tournament_no, name, start_date, end_date, status, total_rounds, location, note, created_at) VALUES
(1, 'T-2026-001', '金秋会员邀请赛', '2026-10-04', '2026-10-05', '进行中', 1, 'A场主球场', '9 洞比杆赛', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 30 DAY),
(2, 'T-2026-002', '青少年高尔夫训练营', '2026-11-01', '2026-11-03', '报名中', 1, '练习场', '低差点组', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 28 DAY);

INSERT INTO tournament_entry (id, tournament_id, player_id, created_at) VALUES
(1, 1, 1, UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 20 DAY),
(2, 1, 3, UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 20 DAY),
(3, 1, 5, UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 20 DAY);

INSERT INTO leaderboard (id, tournament_id, round_no, leader_name, total_score, version, updated_at) VALUES
(1, 1, 1, '王启明', 33, 0, UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 2 DAY),
(2, 1, 2, '林浩',   35, 0, UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 1 DAY),
(3, 2, 1, '孙佳怡', 40, 0, UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 1 DAY);

INSERT INTO score (id, player_id, tournament_id, hole_id, round_no, strokes, created_at) VALUES
(1,  1, 1, 1, 1, 4,  UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 2 DAY),
(2,  1, 1, 2, 1, 6,  UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 2 DAY),
(3,  1, 1, 3, 1, 3,  UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 2 DAY),
(4,  3, 1, 1, 1, 3,  UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 2 DAY),
(5,  3, 1, 2, 1, 5,  UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 2 DAY),
(6,  3, 1, 3, 1, 3,  UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 2 DAY),
(7,  5, 1, 1, 1, 4,  UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 2 DAY),
(8,  5, 1, 2, 1, 5,  UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 2 DAY),
(9,  6, 2, 4, 1, 5,  UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 1 DAY),
(10, 6, 2, 5, 1, 4,  UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 1 DAY),
(11, 6, 2, 6, 1, 3,  UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 1 DAY),
(12, 8, 2, 7, 1, 6,  UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 1 DAY);
