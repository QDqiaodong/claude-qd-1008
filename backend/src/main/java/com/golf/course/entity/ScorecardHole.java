package com.golf.course.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.Getter;
import lombok.Setter;

/**
 * 记分卡当前逐洞数据。{@code strokes} 是待确认/正式计入口径；争议提案和裁决信息随当前洞保留。
 */
@Entity
@Table(name = "scorecard_hole", uniqueConstraints = {
        @UniqueConstraint(name = "uk_scorecard_hole", columnNames = {"scorecard_id", "hole_id"}),
        @UniqueConstraint(name = "uk_scorecard_hole_no", columnNames = {"scorecard_id", "hole_no"})
})
@Getter
@Setter
public class ScorecardHole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scorecard_id", nullable = false)
    private Scorecard scorecard;

    @Column(name = "hole_id", nullable = false)
    private Long holeId;

    @Column(name = "hole_no", nullable = false)
    private Integer holeNo;

    /** 球洞标准杆快照，避免后续球洞资料调整影响历史记分卡 */
    @Column(name = "par", nullable = false)
    private Integer par;

    /** 草稿允许为空；提交前每一洞必须为 ≥1 的合法杆数 */
    @Column(name = "strokes")
    private Integer strokes;

    @Column(name = "proposed_strokes")
    private Integer proposedStrokes;

    @Column(name = "dispute_reason", length = 255)
    private String disputeReason;

    @Column(name = "disputed_by", length = 48)
    private String disputedBy;

    @Column(name = "disputed_at")
    private LocalDateTime disputedAt;

    /** 维持 / 修正 */
    @Column(name = "ruling_decision", length = 16)
    private String rulingDecision;

    @Column(name = "ruling_note", length = 255)
    private String rulingNote;

    @Column(name = "ruled_by", length = 48)
    private String ruledBy;

    @Column(name = "ruled_at")
    private LocalDateTime ruledAt;
}
