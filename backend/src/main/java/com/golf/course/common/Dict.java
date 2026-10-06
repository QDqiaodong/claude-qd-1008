package com.golf.course.common;

import java.util.List;

/**
 * 高尔夫球场词典与业务规则参数表：球洞状态、开球时间状态、球员差点阈值、
 * 赛事状态流转、成绩杆数与标准杆对比口径，以及各类阈值。
 *
 * <p>规则集中在此，便于出题时被要求调整或扩展。</p>
 */
public final class Dict {

    private Dict() {
    }

    // ---------------- 球洞状态 ----------------
    /** 开放 → 维护 → 开放；封闭为人工停用终态，可从维护进入 */
    public static final List<String> HOLE_STATUSES = List.of("开放", "维护", "封闭");

    // ---------------- 开球时间状态 ----------------
    public static final List<String> TEE_STATUSES = List.of("可订", "已订");

    // ---------------- 球员差点 ----------------
    /** 差点取值范围 0 ~ 54（USGA 上限 54） */
    public static final int HANDICAP_MIN = 0;
    public static final int HANDICAP_MAX = 54;

    // ---------------- 赛事状态：报名中 → 进行中 → 已结束 ----------------
    public static final List<String> TOURNAMENT_FLOW = List.of("报名中", "进行中", "已结束");

    public static final List<String> TOURNAMENT_STATUSES = List.of("报名中", "进行中", "已结束");

    // ---------------- 成绩 ----------------
    /** 单洞最少杆数（一杆起） */
    public static final int MIN_STROKES = 1;

    // ---------------- 记分卡签核 ----------------
    public static final String ROLE_SCOREKEEPER = "记分员";
    public static final String ROLE_PLAYER = "球员";
    public static final String ROLE_REFEREE = "裁判";
    public static final List<String> SCORECARD_ROLES = List.of(ROLE_SCOREKEEPER, ROLE_PLAYER, ROLE_REFEREE);

    public static final String SCORECARD_DRAFT = "草稿";
    public static final String SCORECARD_PENDING = "待球员确认";
    public static final String SCORECARD_DISPUTED = "争议中";
    public static final String SCORECARD_CONFIRMED = "已签核";

    public static final String RULING_UPHOLD = "维持";
    public static final String RULING_CORRECT = "修正";

    /** 单场赛事常规轮次上限（4 轮） */
    public static final int MAX_ROUNDS = 4;

    /** 争议/裁决理由的数据库长度上限 */
    public static final int SCORECARD_NOTE_MAX = 255;

    // ---------------- 取值方法 ----------------

    public static boolean isValidHoleStatus(String status) {
        return status != null && HOLE_STATUSES.contains(status);
    }

    public static boolean isValidTeeStatus(String status) {
        return status != null && TEE_STATUSES.contains(status);
    }

    /** 差点必须落在 0 ~ 54 之间（含端点） */
    public static boolean isValidHandicap(Integer handicap) {
        return handicap != null && handicap >= HANDICAP_MIN && handicap <= HANDICAP_MAX;
    }

    public static boolean isValidTournamentStatus(String status) {
        return status != null && TOURNAMENT_STATUSES.contains(status);
    }

    /** 赛事状态只能逐级推进：报名中 → 进行中 → 已结束 */
    public static boolean canProgressTournament(String from, String to) {
        int i = TOURNAMENT_FLOW.indexOf(from);
        return i >= 0 && i + 1 < TOURNAMENT_FLOW.size() && TOURNAMENT_FLOW.get(i + 1).equals(to);
    }

    public static boolean isValidScorecardRole(String role) {
        return role != null && SCORECARD_ROLES.contains(role);
    }

    public static boolean isOfficialScorecardStatus(String status) {
        return SCORECARD_CONFIRMED.equals(status);
    }

    public static boolean isValidRulingDecision(String decision) {
        return RULING_UPHOLD.equals(decision) || RULING_CORRECT.equals(decision);
    }

}
