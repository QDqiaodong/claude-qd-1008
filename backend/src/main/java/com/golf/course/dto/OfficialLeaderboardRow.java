package com.golf.course.dto;

public record OfficialLeaderboardRow(
        Long playerId,
        String playerName,
        int completedRounds,
        int totalStrokes,
        int totalToPar
) {
}
