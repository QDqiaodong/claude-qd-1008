package com.golf.course.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ScorecardVersionView(
        Long id,
        Long cardVersion,
        String snapshotType,
        String action,
        String status,
        String actorRole,
        String actorName,
        String note,
        LocalDateTime createdAt,
        List<VersionHoleView> holes
) {
}
