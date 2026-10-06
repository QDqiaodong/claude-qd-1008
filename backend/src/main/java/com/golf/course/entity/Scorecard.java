package com.golf.course.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import lombok.Getter;
import lombok.Setter;

/**
 * 单球员、单赛事、单轮次的记分卡主表。
 *
 * <p>当前可编辑数据在草稿和重新裁决阶段允许修改；正式提交/裁决时另存不可变快照。
 * {@code version} 同时用于页面乐观版本校验和 JPA 并发控制。</p>
 */
@Entity
@Table(name = "scorecard", uniqueConstraints = {
        @UniqueConstraint(name = "uk_scorecard_round", columnNames = {"tournament_id", "player_id", "round_no"})
})
@Getter
@Setter
public class Scorecard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tournament_id", nullable = false)
    private Tournament tournament;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", nullable = false)
    private Player player;

    @Column(name = "round_no", nullable = false)
    private Integer roundNo;

    /** 草稿 / 待球员确认 / 争议中 / 已签核 */
    @Column(name = "status", nullable = false, length = 24)
    private String status;

    /** 页面提交的乐观版本；数据库版本号由 JPA 负责维护 */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "submitted_by", length = 48)
    private String submittedBy;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "confirmed_by", length = 48)
    private String confirmedBy;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "last_action", length = 32)
    private String lastAction;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "scorecard", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ScorecardHole> holes = new ArrayList<>();

    public void addHole(ScorecardHole hole) {
        holes.add(hole);
        hole.setScorecard(this);
    }
}
