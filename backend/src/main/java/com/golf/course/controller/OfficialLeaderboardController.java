package com.golf.course.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.golf.course.common.ApiResponse;
import com.golf.course.dto.OfficialLeaderboardRow;
import com.golf.course.service.ScorecardService;

@RestController
@RequestMapping("/api/tournaments")
public class OfficialLeaderboardController {

    private final ScorecardService scorecardService;

    public OfficialLeaderboardController(ScorecardService scorecardService) {
        this.scorecardService = scorecardService;
    }

    /** 正式排行榜：仅统计已签核记分卡；待确认、争议中以及历史版本都不会计入。 */
    @GetMapping("/{id}/official-leaderboard")
    public ApiResponse<List<OfficialLeaderboardRow>> official(@PathVariable Long id) {
        return ApiResponse.success(scorecardService.officialLeaderboard(id));
    }
}
