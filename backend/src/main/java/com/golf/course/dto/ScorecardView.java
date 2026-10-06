package com.golf.course.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ScorecardView(
        Long id,
        Long tournamentId,
        String tournamentName,
        Long playerId,
        String playerName,
        Integer roundNo,
        String status,
        Long version,
        String submittedBy,
        LocalDateTime submittedAt,
        String confirmedBy,
        LocalDateTime confirmedAt,
        String lastAction,
        LocalDateTime updatedAt,
        Integer totalStrokes,
        Integer totalToPar,
        boolean official,
        List<ScorecardHoleView> holes
) {
}
