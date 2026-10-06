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

import lombok.Getter;
import lombok.Setter;

/**
 * 记分卡不可变状态快照。每次提交、确认、争议和裁决均保存 BEFORE / AFTER 两条记录。
 */
@Entity
@Table(name = "scorecard_version")
@Getter
@Setter
public class ScorecardVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scorecard_id", nullable = false)
    private Scorecard scorecard;

    /** 操作发生时主表的乐观版本号；同一操作的 BEFORE / AFTER 相同 */
    @Column(name = "card_version", nullable = false)
    private Long cardVersion;

    /** BEFORE / AFTER */
    @Column(name = "snapshot_type", nullable = false, length = 16)
    private String snapshotType;

    /** 提交 / 球员确认 / 球员争议 / 裁判裁决 */
    @Column(name = "action", nullable = false, length = 32)
    private String action;

    @Column(name = "status", nullable = false, length = 24)
    private String status;

    @Column(name = "actor_role", length = 16)
    private String actorRole;

    @Column(name = "actor_name", length = 48)
    private String actorName;

    @Column(name = "note", length = 255)
    private String note;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "version", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ScorecardVersionHole> holes = new ArrayList<>();

    public void addHole(ScorecardVersionHole hole) {
        holes.add(hole);
        hole.setVersion(this);
    }
}
