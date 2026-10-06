package com.golf.course.dto;

import jakarta.validation.constraints.NotNull;

public record ScorecardActionRequest(
        @NotNull Long expectedVersion,
        String actorName
) {
}
