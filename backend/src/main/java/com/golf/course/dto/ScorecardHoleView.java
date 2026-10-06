package com.golf.course.dto;

import java.time.LocalDateTime;

public record ScorecardHoleView(
        Long id,
        Long holeId,
        Integer holeNo,
        Integer par,
        Integer strokes,
        Integer proposedStrokes,
        String disputeReason,
        String disputedBy,
        LocalDateTime disputedAt,
        String rulingDecision,
        String rulingNote,
        String ruledBy,
        LocalDateTime ruledAt,
        Integer toPar
) {
}
