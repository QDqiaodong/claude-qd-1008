package com.golf.course.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

/**
 * 记分卡快照中的逐洞明细，只追加、不修改。
 */
@Entity
@Table(name = "scorecard_version_hole")
@Getter
@Setter
public class ScorecardVersionHole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "version_id", nullable = false)
    private ScorecardVersion version;

    @Column(name = "hole_no", nullable = false)
    private Integer holeNo;

    @Column(name = "par", nullable = false)
    private Integer par;

    @Column(name = "strokes")
    private Integer strokes;

    @Column(name = "proposed_strokes")
    private Integer proposedStrokes;

    @Column(name = "dispute_reason", length = 255)
    private String disputeReason;

    @Column(name = "disputed_by", length = 48)
    private String disputedBy;

    @Column(name = "ruling_decision", length = 16)
    private String rulingDecision;

    @Column(name = "ruling_note", length = 255)
    private String rulingNote;

    @Column(name = "ruled_by", length = 48)
    private String ruledBy;
}
