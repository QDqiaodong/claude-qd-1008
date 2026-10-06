package com.golf.course.dto;

public record VersionHoleView(
        Integer holeNo,
        Integer par,
        Integer strokes,
        Integer proposedStrokes,
        String disputeReason,
        String disputedBy,
        String rulingDecision,
        String rulingNote,
        String ruledBy
) {
}
