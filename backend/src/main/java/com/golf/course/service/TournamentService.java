package com.golf.course.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.golf.course.common.BizException;
import com.golf.course.common.Dict;
import com.golf.course.dto.CloseCheckView;
import com.golf.course.entity.Tournament;
import com.golf.course.repository.TournamentRepository;

/**
 * 赛事业务。核心规则：状态只能逐级推进：报名中 → 进行中 → 已结束。
 */
@Service
public class TournamentService {

    private final TournamentRepository tournamentRepository;
    private final ScorecardService scorecardService;

    public TournamentService(TournamentRepository tournamentRepository,
                             ScorecardService scorecardService) {
        this.tournamentRepository = tournamentRepository;
        this.scorecardService = scorecardService;
    }

    public List<Tournament> list() {
        return tournamentRepository.findAll();
    }

    public Tournament get(Long id) {
        return tournamentRepository.findById(id)
                .orElseThrow(() -> new BizException("赛事不存在：" + id));
    }

    public CloseCheckView checkCanClose(Long id) {
        return scorecardService.checkCanClose(id);
    }

    @Transactional
    public Tournament create(Tournament input) {
        if (input.getTournamentNo() == null || input.getTournamentNo().isBlank()) {
            throw new BizException("赛事编号不能为空");
        }
        tournamentRepository.findByTournamentNo(input.getTournamentNo().trim()).ifPresent(t -> {
            throw new BizException("赛事编号已存在：" + input.getTournamentNo());
        });
        if (input.getName() == null || input.getName().isBlank()) {
            throw new BizException("赛事名称不能为空");
        }
        if (!Dict.isValidTournamentStatus(input.getStatus())) {
            input.setStatus("报名中");
        }
        if (input.getTotalRounds() == null) {
            input.setTotalRounds(1);
        }
        if (input.getTotalRounds() < 1 || input.getTotalRounds() > Dict.MAX_ROUNDS) {
            throw new BizException("赛事轮次必须在 1 ~ " + Dict.MAX_ROUNDS + " 之间");
        }
        Tournament t = new Tournament();
        t.setTournamentNo(input.getTournamentNo().trim());
        t.setName(input.getName().trim());
        t.setStartDate(input.getStartDate());
        t.setEndDate(input.getEndDate());
        t.setStatus(input.getStatus());
        t.setTotalRounds(input.getTotalRounds());
        t.setLocation(input.getLocation());
        t.setNote(input.getNote());
        t.setCreatedAt(LocalDateTime.now());
        return tournamentRepository.save(t);
    }

    @Transactional
    public Tournament advance(Long id, String target) {
        Tournament t = tournamentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BizException("赛事不存在：" + id));
        if (!Dict.isValidTournamentStatus(target)) {
            throw new BizException("赛事状态不合法：" + target);
        }
        if (!Dict.canProgressTournament(t.getStatus(), target)) {
            throw new BizException("不允许从「" + t.getStatus() + "」直接转为「" + target + "」");
        }
        if ("已结束".equals(target)) {
            scorecardService.assertCanClose(t);
        }
        t.setStatus(target);
        return tournamentRepository.save(t);
    }
}
