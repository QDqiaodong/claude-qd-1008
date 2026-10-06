package com.golf.course.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.golf.course.common.ApiResponse;
import com.golf.course.common.BizException;
import com.golf.course.common.Dict;
import com.golf.course.dto.DisputeRequest;
import com.golf.course.dto.RulingRequest;
import com.golf.course.dto.SaveDraftRequest;
import com.golf.course.dto.ScorecardActionRequest;
import com.golf.course.dto.ScorecardVersionView;
import com.golf.course.dto.ScorecardView;
import com.golf.course.service.ScorecardService;

@RestController
@RequestMapping("/api/scorecards")
public class ScorecardController {

    private final ScorecardService scorecardService;

    public ScorecardController(ScorecardService scorecardService) {
        this.scorecardService = scorecardService;
    }

    @GetMapping
    public ApiResponse<List<ScorecardView>> list(@RequestParam(required = false) Long tournamentId,
                                                 @RequestParam(required = false) Long playerId,
                                                 @RequestParam(required = false) String status,
                                                 @RequestParam(defaultValue = Dict.ROLE_SCOREKEEPER) String role) {
        validateRole(role);
        return ApiResponse.success(scorecardService.list(tournamentId, playerId, status, role));
    }

    @GetMapping("/{id}")
    public ApiResponse<ScorecardView> get(@PathVariable Long id,
                                          @RequestParam(defaultValue = Dict.ROLE_SCOREKEEPER) String role,
                                          @RequestParam(required = false) Long playerId) {
        validateRole(role);
        return ApiResponse.success(scorecardService.get(id, role, playerId));
    }

    @GetMapping("/{id}/history")
    public ApiResponse<List<ScorecardVersionView>> history(@PathVariable Long id,
                                                           @RequestParam(defaultValue = Dict.ROLE_SCOREKEEPER) String role,
                                                           @RequestParam(required = false) Long playerId) {
        validateRole(role);
        return ApiResponse.success(scorecardService.history(id, role, playerId));
    }

    /** 记分员创建或更新草稿；提交后的普通编辑会被服务层拒绝。 */
    @PostMapping("/draft")
    public ApiResponse<ScorecardView> saveDraft(@Valid @RequestBody SaveDraftRequest request) {
        return ApiResponse.success("草稿已保存", scorecardService.saveDraft(request));
    }

    @PostMapping("/{id}/submit")
    public ApiResponse<ScorecardView> submit(@PathVariable Long id,
                                             @Valid @RequestBody ScorecardActionRequest request) {
        return ApiResponse.success("记分卡已提交，等待球员确认", scorecardService.submit(id, request));
    }

    @PostMapping("/{id}/confirm")
    public ApiResponse<ScorecardView> confirm(@PathVariable Long id,
                                              @RequestParam Long playerId,
                                              @Valid @RequestBody ScorecardActionRequest request) {
        return ApiResponse.success("记分卡已整卡确认并正式签核",
                scorecardService.confirm(id, request, playerId));
    }

    @PostMapping("/{id}/dispute")
    public ApiResponse<ScorecardView> dispute(@PathVariable Long id,
                                              @RequestParam Long playerId,
                                              @Valid @RequestBody DisputeRequest request) {
        return ApiResponse.success("争议已提交给裁判，争议成绩暂不计入排行榜",
                scorecardService.dispute(id, request, playerId));
    }

    @PostMapping("/{id}/rule")
    public ApiResponse<ScorecardView> rule(@PathVariable Long id,
                                           @RequestParam(defaultValue = Dict.ROLE_REFEREE) String role,
                                           @Valid @RequestBody RulingRequest request) {
        if (!Dict.ROLE_REFEREE.equals(role)) {
            throw new BizException("该操作仅裁判可执行");
        }
        return ApiResponse.success("裁判裁决完成，记分卡已重新交球员确认", scorecardService.rule(id, request));
    }

    private void validateRole(String role) {
        if (!Dict.isValidScorecardRole(role)) {
            throw new BizException("角色不合法，应为：记分员 / 球员 / 裁判");
        }
    }
}
