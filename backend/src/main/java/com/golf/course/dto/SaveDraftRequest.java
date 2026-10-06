package com.golf.course.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;

public record SaveDraftRequest(
        @NotNull Long tournamentId,
        @NotNull Long playerId,
        @NotNull Integer roundNo,
        String actorName,
        Long expectedVersion,
        List<HoleStrokeRequest> holes
) {
}
