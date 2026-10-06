package com.golf.course.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record DisputeRequest(
        @NotNull Long expectedVersion,
        String actorName,
        @NotEmpty List<DisputeHoleRequest> holes
) {
}
