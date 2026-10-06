package com.golf.course.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RulingRequest(
        @NotNull Long expectedVersion,
        String refereeName,
        @NotBlank String decision,
        @NotBlank String note,
        List<RulingHoleRequest> correctedHoles
) {
}
