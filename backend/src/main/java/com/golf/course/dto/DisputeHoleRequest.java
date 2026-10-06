package com.golf.course.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DisputeHoleRequest(
        @NotNull Integer holeNo,
        @NotNull Integer proposedStrokes,
        @NotBlank String reason
) {
}
