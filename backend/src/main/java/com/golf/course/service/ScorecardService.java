package com.golf.course.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.golf.course.common.BizException;
import com.golf.course.common.Dict;
import com.golf.course.common.StaleVersionException;
import com.golf.course.dto.CloseCheckView;
import com.golf.course.dto.DisputeHoleRequest;
import com.golf.course.dto.DisputeRequest;
import com.golf.course.dto.HoleStrokeRequest;
import com.golf.course.dto.OfficialLeaderboardRow;
import com.golf.course.dto.RulingHoleRequest;
import com.golf.course.dto.RulingRequest;
import com.golf.course.dto.SaveDraftRequest;
import com.golf.course.dto.ScorecardActionRequest;
import com.golf.course.dto.ScorecardHoleView;
import com.golf.course.dto.ScorecardVersionView;
import com.golf.course.dto.ScorecardView;
import com.golf.course.dto.VersionHoleView;
import com.golf.course.entity.Hole;
import com.golf.course.entity.Player;
import com.golf.course.entity.Scorecard;
import com.golf.course.entity.ScorecardHole;
import com.golf.course.entity.ScorecardVersion;
import com.golf.course.entity.ScorecardVersionHole;
import com.golf.course.entity.Tournament;
import com.golf.course.entity.TournamentEntry;
import com.golf.course.repository.HoleRepository;
import com.golf.course.repository.PlayerRepository;
import com.golf.course.repository.ScorecardRepository;
import com.golf.course.repository.ScorecardVersionRepository;
import com.golf.course.repository.TournamentEntryRepository;
import com.golf.course.repository.TournamentRepository;

/**
 * 记分卡签核闭环：草稿维护 → 提交快照 → 球员确认/争议 → 裁判裁决 → 正式签核。
 *
 * <p>所有状态推进均先对主记录加行级悲观锁，再核对页面携带的 expectedVersion；
 * 快照只追加、不更新，正式排行榜只读取「已签核」的当前版本。</p>
 */
@Service
public class ScorecardService {

    private final ScorecardRepository scorecardRepository;
    private final ScorecardVersionRepository versionRepository;
    private final TournamentRepository tournamentRepository;
    private final PlayerRepository playerRepository;
    private final HoleRepository holeRepository;
    private final TournamentEntryRepository entryRepository;

    public ScorecardService(ScorecardRepository scorecardRepository,
                            ScorecardVersionRepository versionRepository,
                            TournamentRepository tournamentRepository,
                            PlayerRepository playerRepository,
                            HoleRepository holeRepository,
                            TournamentEntryRepository entryRepository) {
        this.scorecardRepository = scorecardRepository;
        this.versionRepository = versionRepository;
        this.tournamentRepository = tournamentRepository;
        this.playerRepository = playerRepository;
        this.holeRepository = holeRepository;
        this.entryRepository = entryRepository;
    }

    @Transactional(readOnly = true)
    public List<ScorecardView> list(Long tournamentId, Long playerId, String status, String role) {
        List<Scorecard> cards;
        if (Dict.ROLE_PLAYER.equals(role)) {
            if (playerId == null) {
                throw new BizException("球员视角必须选择球员");
            }
            cards = tournamentId == null
                    ? scorecardRepository.findAll().stream().filter(c -> playerId.equals(c.getPlayer().getId())).toList()
                    : scorecardRepository.findByTournamentIdAndPlayerIdOrderByRoundNoAsc(tournamentId, playerId);
        } else if (Dict.ROLE_REFEREE.equals(role)) {
            cards = scorecardRepository.findAll().stream()
                    .filter(c -> tournamentId == null || tournamentId.equals(c.getTournament().getId()))
                    .filter(c -> Dict.SCORECARD_DISPUTED.equals(c.getStatus()))
                    .toList();
        } else {
            requireScorekeeper(role);
            cards = tournamentId == null ? scorecardRepository.findAll()
                    : scorecardRepository.findByTournamentIdOrderByRoundNoAscPlayerIdAsc(tournamentId);
        }
        return cards.stream()
                .filter(c -> status == null || status.isBlank() || status.equals(c.getStatus()))
                .filter(c -> playerId == null || playerId.equals(c.getPlayer().getId()))
                .sorted(Comparator.comparing(Scorecard::getRoundNo)
                        .thenComparing(c -> c.getPlayer().getId()))
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public ScorecardView get(Long id, String role, Long playerId) {
        Scorecard card = scorecardRepository.findById(id)
                .orElseThrow(() -> new BizException("记分卡不存在：" + id));
        authorizeRead(card, role, playerId);
        return toView(card);
    }

    @Transactional(readOnly = true)
    public List<ScorecardVersionView> history(Long id, String role, Long playerId) {
        Scorecard card = scorecardRepository.findById(id)
                .orElseThrow(() -> new BizException("记分卡不存在：" + id));
        authorizeRead(card, role, playerId);
        return versionRepository.findByScorecardIdOrderByIdAsc(id).stream()
                .map(this::toVersionView)
                .toList();
    }

    @Transactional
    public ScorecardView saveDraft(SaveDraftRequest request) {
        Tournament tournament = tournamentRepository.findByIdForUpdate(request.tournamentId())
                .orElseThrow(() -> new BizException("赛事不存在：" + request.tournamentId()));
        Player player = resolvePlayer(request.playerId());
        validateRound(request.roundNo(), tournament);
        if ("已结束".equals(tournament.getStatus())) {
            throw new BizException("赛事已结束，不能继续维护草稿");
        }

        List<Hole> courseHoles = expectedPlayableHoles();
        Map<Long, HoleStrokeRequest> requested = validateDraftHoles(request.holes(), courseHoles);
        LocalDateTime now = LocalDateTime.now();

        Scorecard card = scorecardRepository
                .findByIdForUpdateForBusinessKey(tournament.getId(), player.getId(), request.roundNo())
                .orElse(null);
        if (card == null) {
            if (request.expectedVersion() != null) {
                throw new BizException("记分卡尚未创建，expectedVersion 应为空");
            }
            card = new Scorecard();
            card.setTournament(tournament);
            card.setPlayer(player);
            card.setRoundNo(request.roundNo());
            card.setStatus(Dict.SCORECARD_DRAFT);
            card.setVersion(0L);
            card.setCreatedAt(now);
            for (Hole hole : courseHoles) {
                ScorecardHole sh = new ScorecardHole();
                sh.setHoleId(hole.getId());
                sh.setHoleNo(hole.getHoleNo());
                sh.setPar(hole.getPar());
                card.addHole(sh);
            }
        } else {
            checkVersion(card, request.expectedVersion());
            if (!Dict.SCORECARD_DRAFT.equals(card.getStatus())) {
                throw new BizException("记分卡已提交为「" + card.getStatus() + "」，普通草稿编辑不能改写签核快照");
            }
            ensureHolesMatchCourse(card, courseHoles);
        }

        for (ScorecardHole sh : card.getHoles()) {
            HoleStrokeRequest value = requested.get(sh.getHoleId());
            sh.setStrokes(value == null ? null : value.strokes());
        }
        card.setLastAction("保存草稿");
        card.setUpdatedAt(now);
        ensureEntry(tournament, player, now);

        try {
            saveLockedCard(card);
        } catch (DataIntegrityViolationException e) {
            throw new BizException("该赛事、球员和轮次的记分卡已由其他记分员创建，请刷新后维护已有草稿");
        }
        return toView(card);
    }

    @Transactional
    public ScorecardView submit(Long id, ScorecardActionRequest request) {
        requireScorekeeper(Dict.ROLE_SCOREKEEPER);
        Scorecard card = lockCardForAction(id);
        checkVersion(card, request.expectedVersion());
        if (!Dict.SCORECARD_DRAFT.equals(card.getStatus())) {
            throw new BizException("仅「草稿」状态可以提交，当前状态：" + card.getStatus());
        }
        Tournament tournament = card.getTournament();
        if (!"进行中".equals(tournament.getStatus())) {
            throw new BizException("只有赛事处于「进行中」时才能提交记分卡");
        }
        validateRound(card.getRoundNo(), tournament);
        validateCompleteHoles(card);

        Long baseVersion = card.getVersion();
        LocalDateTime now = LocalDateTime.now();
        String actor = defaultActor(request.actorName(), Dict.ROLE_SCOREKEEPER);
        snapshot(card, baseVersion, "BEFORE", "提交", Dict.ROLE_SCOREKEEPER, actor,
                "提交前草稿快照", now);

        card.setStatus(Dict.SCORECARD_PENDING);
        card.setSubmittedBy(actor);
        card.setSubmittedAt(now);
        card.setLastAction("提交");
        card.setUpdatedAt(now);
        saveLockedCard(card);

        snapshot(card, baseVersion, "AFTER", "提交", Dict.ROLE_SCOREKEEPER, actor,
                "提交后进入待球员确认", now);
        versionRepository.flush();
        return toView(card);
    }

    @Transactional
    public ScorecardView confirm(Long id, ScorecardActionRequest request, Long actingPlayerId) {
        Scorecard card = lockCardForAction(id);
        checkVersion(card, request.expectedVersion());
        requirePlayer(card, actingPlayerId);
        if (!Dict.SCORECARD_PENDING.equals(card.getStatus())) {
            throw new BizException("仅「待球员确认」状态可以整卡确认，当前状态：" + card.getStatus());
        }
        if (!"进行中".equals(card.getTournament().getStatus())) {
            throw new BizException("赛事已收口，不能再确认记分卡");
        }

        Long baseVersion = card.getVersion();
        LocalDateTime now = LocalDateTime.now();
        String actor = defaultActor(request.actorName(), card.getPlayer().getName());
        snapshot(card, baseVersion, "BEFORE", "球员确认", Dict.ROLE_PLAYER, actor,
                "球员确认前快照", now);

        card.setStatus(Dict.SCORECARD_CONFIRMED);
        card.setConfirmedBy(actor);
        card.setConfirmedAt(now);
        card.setLastAction("球员确认");
        card.setUpdatedAt(now);
        saveLockedCard(card);

        snapshot(card, baseVersion, "AFTER", "球员确认", Dict.ROLE_PLAYER, actor,
                "球员整卡确认，正式计入排行榜", now);
        versionRepository.flush();
        return toView(card);
    }

    @Transactional
    public ScorecardView dispute(Long id, DisputeRequest request, Long actingPlayerId) {
        Scorecard card = lockCardForAction(id);
        checkVersion(card, request.expectedVersion());
        requirePlayer(card, actingPlayerId);
        if (!Dict.SCORECARD_PENDING.equals(card.getStatus())) {
            throw new BizException("仅「待球员确认」状态可以提出争议，当前状态：" + card.getStatus());
        }
        if (!"进行中".equals(card.getTournament().getStatus())) {
            throw new BizException("赛事已收口，不能再提出争议");
        }
        if (request.holes() == null || request.holes().isEmpty()) {
            throw new BizException("请至少选择一个存在争议的球洞");
        }
        Map<Integer, ScorecardHole> byNo = card.getHoles().stream()
                .collect(Collectors.toMap(ScorecardHole::getHoleNo, h -> h, (a, b) -> a));
        Set<Integer> requestedNos = new HashSet<>();
        for (DisputeHoleRequest dh : request.holes()) {
            if (dh.holeNo() == null || !byNo.containsKey(dh.holeNo())) {
                throw new BizException("争议球洞不存在：" + dh.holeNo());
            }
            if (!requestedNos.add(dh.holeNo())) {
                throw new BizException("球洞争议不能重复：第 " + dh.holeNo() + " 洞");
            }
            if (dh.proposedStrokes() == null || dh.proposedStrokes() < Dict.MIN_STROKES) {
                throw new BizException("第 " + dh.holeNo() + " 洞的争议杆数须 ≥ " + Dict.MIN_STROKES);
            }
            if (dh.reason() == null || dh.reason().isBlank() || dh.reason().trim().length() > Dict.SCORECARD_NOTE_MAX) {
                throw new BizException("第 " + dh.holeNo() + " 洞必须填写不超过 " + Dict.SCORECARD_NOTE_MAX + " 字的争议理由");
            }
        }

        Long baseVersion = card.getVersion();
        LocalDateTime now = LocalDateTime.now();
        String actor = defaultActor(request.actorName(), card.getPlayer().getName());
        snapshot(card, baseVersion, "BEFORE", "球员争议", Dict.ROLE_PLAYER, actor,
                "球员提出争议前快照", now);

        for (DisputeHoleRequest dh : request.holes()) {
            ScorecardHole sh = byNo.get(dh.holeNo());
            sh.setProposedStrokes(dh.proposedStrokes());
            sh.setDisputeReason(dh.reason().trim());
            sh.setDisputedBy(actor);
            sh.setDisputedAt(now);
            sh.setRulingDecision(null);
            sh.setRulingNote(null);
            sh.setRuledBy(null);
            sh.setRuledAt(null);
        }
        card.setStatus(Dict.SCORECARD_DISPUTED);
        card.setLastAction("球员争议");
        card.setUpdatedAt(now);
        saveLockedCard(card);

        snapshot(card, baseVersion, "AFTER", "球员争议", Dict.ROLE_PLAYER, actor,
                "球员对 " + requestedNos.size() + " 个球洞提出争议，暂不计入排行榜", now);
        versionRepository.flush();
        return toView(card);
    }

    @Transactional
    public ScorecardView rule(Long id, RulingRequest request) {
        Scorecard card = lockCardForAction(id);
        checkVersion(card, request.expectedVersion());
        if (!Dict.SCORECARD_DISPUTED.equals(card.getStatus())) {
            throw new BizException("裁判只能处理「争议中」的记分卡，当前状态：" + card.getStatus());
        }
        if (!Dict.isValidRulingDecision(request.decision())) {
            throw new BizException("裁决类型必须是「维持」或「修正」");
        }
        if (request.note() == null || request.note().isBlank() || request.note().trim().length() > Dict.SCORECARD_NOTE_MAX) {
            throw new BizException("裁判必须填写不超过 " + Dict.SCORECARD_NOTE_MAX + " 字的裁决说明");
        }
        if (!"进行中".equals(card.getTournament().getStatus())) {
            throw new BizException("赛事已收口，不能再作出裁判裁决");
        }

        List<ScorecardHole> disputed = card.getHoles().stream()
                .filter(h -> h.getDisputeReason() != null)
                .sorted(Comparator.comparing(ScorecardHole::getHoleNo))
                .toList();
        if (disputed.isEmpty()) {
            throw new BizException("该记分卡没有待裁决的争议球洞");
        }
        Map<Integer, Integer> corrections = new HashMap<>();
        if (request.correctedHoles() != null) {
            for (RulingHoleRequest rh : request.correctedHoles()) {
                if (rh.holeNo() == null || corrections.put(rh.holeNo(), rh.correctedStrokes()) != null) {
                    throw new BizException("修正球洞重复：" + rh.holeNo());
                }
            }
        }
        Set<Integer> disputedNos = disputed.stream().map(ScorecardHole::getHoleNo).collect(Collectors.toSet());
        if (Dict.RULING_UPHOLD.equals(request.decision()) && !corrections.isEmpty()) {
            throw new BizException("选择「维持」时不能提交修正杆数");
        }
        if (Dict.RULING_CORRECT.equals(request.decision()) && !corrections.keySet().equals(disputedNos)) {
            throw new BizException("选择「修正」时必须为每一个争议球洞给出修正杆数");
        }
        corrections.forEach((no, strokes) -> {
            if (strokes == null || strokes < Dict.MIN_STROKES) {
                throw new BizException("第 " + no + " 洞的修正杆数须 ≥ " + Dict.MIN_STROKES);
            }
        });

        Long baseVersion = card.getVersion();
        LocalDateTime now = LocalDateTime.now();
        String referee = defaultActor(request.refereeName(), Dict.ROLE_REFEREE);
        snapshot(card, baseVersion, "BEFORE", "裁判裁决", Dict.ROLE_REFEREE, referee,
                "裁判裁决前争议快照", now);

        for (ScorecardHole sh : disputed) {
            if (Dict.RULING_CORRECT.equals(request.decision())) {
                sh.setStrokes(corrections.get(sh.getHoleNo()));
            }
            sh.setRulingDecision(request.decision());
            sh.setRulingNote(request.note().trim());
            sh.setRuledBy(referee);
            sh.setRuledAt(now);
            sh.setProposedStrokes(null);
            sh.setDisputeReason(null);
            sh.setDisputedBy(null);
            sh.setDisputedAt(null);
        }
        card.setStatus(Dict.SCORECARD_PENDING);
        card.setLastAction("裁判裁决");
        card.setUpdatedAt(now);
        saveLockedCard(card);

        snapshot(card, baseVersion, "AFTER", "裁判裁决", Dict.ROLE_REFEREE, referee,
                "裁决后重新交球员确认：" + request.note().trim(), now);
        versionRepository.flush();
        return toView(card);
    }

    @Transactional(readOnly = true)
    public List<OfficialLeaderboardRow> officialLeaderboard(Long tournamentId) {
        resolveTournament(tournamentId);
        Set<Long> entryPlayerIds = entryRepository.findByTournamentIdOrderByIdAsc(tournamentId).stream()
                .map(e -> e.getPlayer().getId())
                .collect(Collectors.toSet());
        Map<Long, List<Scorecard>> byPlayer = scorecardRepository
                .findByTournamentIdOrderByRoundNoAscPlayerIdAsc(tournamentId).stream()
                .filter(c -> Dict.SCORECARD_CONFIRMED.equals(c.getStatus()))
                .filter(c -> entryPlayerIds.contains(c.getPlayer().getId()))
                .collect(Collectors.groupingBy(c -> c.getPlayer().getId()));

        List<OfficialLeaderboardRow> rows = new ArrayList<>();
        byPlayer.forEach((playerId, cards) -> {
            Player player = cards.get(0).getPlayer();
            int strokes = cards.stream().flatMap(c -> c.getHoles().stream())
                    .map(ScorecardHole::getStrokes).filter(java.util.Objects::nonNull)
                    .mapToInt(Integer::intValue).sum();
            int toPar = cards.stream().flatMap(c -> c.getHoles().stream())
                    .filter(h -> h.getStrokes() != null)
                    .mapToInt(h -> h.getStrokes() - h.getPar()).sum();
            rows.add(new OfficialLeaderboardRow(playerId, player.getName(), cards.size(), strokes, toPar));
        });
        return rows.stream()
                .sorted(Comparator.comparingInt(OfficialLeaderboardRow::totalStrokes)
                        .thenComparingInt(OfficialLeaderboardRow::totalToPar)
                        .thenComparing(OfficialLeaderboardRow::playerName))
                .toList();
    }

    @Transactional(readOnly = true)
    public CloseCheckView checkCanClose(Long tournamentId) {
        Tournament tournament = resolveTournament(tournamentId);
        return buildCloseCheck(tournament);
    }

    public void assertCanClose(Tournament tournament) {
        CloseCheckView check = buildCloseCheck(tournament);
        if (!check.canClose()) {
            throw new BizException("赛事不能收口：" + String.join("；", check.reasons()));
        }
    }

    private CloseCheckView buildCloseCheck(Tournament tournament) {
        List<String> reasons = new ArrayList<>();
        List<TournamentEntry> entries = entryRepository.findByTournamentIdOrderByIdAsc(tournament.getId());
        if (entries.isEmpty()) {
            reasons.add("赛事尚无参赛球员");
        }
        List<Scorecard> cards = scorecardRepository
                .findByTournamentIdOrderByRoundNoAscPlayerIdAsc(tournament.getId());
        Map<Long, Map<Integer, Scorecard>> byPlayer = cards.stream()
                .collect(Collectors.groupingBy(c -> c.getPlayer().getId(),
                        Collectors.toMap(Scorecard::getRoundNo, c -> c, (a, b) -> a)));

        for (TournamentEntry entry : entries) {
            Map<Integer, Scorecard> roundCards = byPlayer.getOrDefault(entry.getPlayer().getId(), Map.of());
            for (int round = 1; round <= safeTotalRounds(tournament); round++) {
                Scorecard card = roundCards.get(round);
                String who = "球员「" + entry.getPlayer().getName() + "」第 " + round + " 轮";
                if (card == null) {
                    reasons.add(who + "尚未创建记分卡");
                } else if (!Dict.SCORECARD_CONFIRMED.equals(card.getStatus())) {
                    reasons.add(who + "仍为「" + card.getStatus() + "」");
                }
            }
        }
        return new CloseCheckView(reasons.isEmpty(), reasons);
    }

    private Scorecard lockCard(Long id) {
        return scorecardRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BizException("记分卡不存在：" + id));
    }

    /** 统一按「赛事 → 记分卡」加锁，避免赛事收口与签核操作交叉等待。 */
    private Scorecard lockCardForAction(Long id) {
        Scorecard read = scorecardRepository.findById(id)
                .orElseThrow(() -> new BizException("记分卡不存在：" + id));
        lockTournament(read);
        return scorecardRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BizException("记分卡不存在：" + id));
    }

    private Tournament lockTournament(Scorecard card) {
        return tournamentRepository.findByIdForUpdate(card.getTournament().getId())
                .orElseThrow(() -> new BizException("赛事不存在：" + card.getTournament().getId()));
    }

    private void saveLockedCard(Scorecard card) {
        try {
            scorecardRepository.saveAndFlush(card);
        } catch (OptimisticLockingFailureException e) {
            ScorecardView current = scorecardRepository.findById(card.getId())
                    .map(this::toView).orElse(null);
            throw new StaleVersionException("数据已被其他用户更新，请基于当前状态重试", current);
        }
    }

    private void checkVersion(Scorecard card, Long expectedVersion) {
        if (expectedVersion == null || !expectedVersion.equals(card.getVersion())) {
            ScorecardView current = toView(card);
            throw new StaleVersionException("数据已变化：您操作的是版本 " + expectedVersion
                    + "，当前版本为 " + card.getVersion() + "（" + card.getStatus() + "），请刷新查看最新状态", current);
        }
    }

    private void snapshot(Scorecard card, Long cardVersion, String type, String action,
                          String actorRole, String actorName, String note, LocalDateTime time) {
        ScorecardVersion version = new ScorecardVersion();
        version.setScorecard(card);
        version.setCardVersion(cardVersion);
        version.setSnapshotType(type);
        version.setAction(action);
        version.setStatus(card.getStatus());
        version.setActorRole(actorRole);
        version.setActorName(actorName);
        version.setNote(note);
        version.setCreatedAt(time);
        for (ScorecardHole sh : card.getHoles()) {
            ScorecardVersionHole vh = new ScorecardVersionHole();
            vh.setHoleNo(sh.getHoleNo());
            vh.setPar(sh.getPar());
            vh.setStrokes(sh.getStrokes());
            vh.setProposedStrokes(sh.getProposedStrokes());
            vh.setDisputeReason(sh.getDisputeReason());
            vh.setDisputedBy(sh.getDisputedBy());
            vh.setRulingDecision(sh.getRulingDecision());
            vh.setRulingNote(sh.getRulingNote());
            vh.setRuledBy(sh.getRuledBy());
            version.addHole(vh);
        }
        versionRepository.save(version);
    }

    private Map<Long, HoleStrokeRequest> validateDraftHoles(List<HoleStrokeRequest> input, List<Hole> courseHoles) {
        Map<Long, Hole> courseById = courseHoles.stream().collect(Collectors.toMap(Hole::getId, h -> h));
        Map<Long, HoleStrokeRequest> result = new HashMap<>();
        if (input != null) {
            for (HoleStrokeRequest hs : input) {
                if (hs == null || hs.holeId() == null) {
                    throw new BizException("草稿球洞必须指定 holeId");
                }
                Hole hole = courseById.get(hs.holeId());
                if (hole == null) {
                    throw new BizException("球洞不存在：" + hs.holeId());
                }
                if (result.put(hs.holeId(), hs) != null) {
                    throw new BizException("第 " + hole.getHoleNo() + " 洞在提交数据中重复");
                }
                if (hs.strokes() != null && hs.strokes() < Dict.MIN_STROKES) {
                    throw new BizException("第 " + hole.getHoleNo() + " 洞杆数须 ≥ " + Dict.MIN_STROKES);
                }
            }
        }
        return result;
    }

    private void ensureHolesMatchCourse(Scorecard card, List<Hole> courseHoles) {
        Map<Long, ScorecardHole> existing = card.getHoles().stream()
                .collect(Collectors.toMap(ScorecardHole::getHoleId, h -> h, (a, b) -> a));
        Set<Integer> nos = new HashSet<>();
        for (ScorecardHole h : card.getHoles()) {
            if (!nos.add(h.getHoleNo())) {
                throw new BizException("记分卡存在重复洞号：第 " + h.getHoleNo() + " 洞");
            }
        }
        for (Hole hole : courseHoles) {
            if (!existing.containsKey(hole.getId())) {
                ScorecardHole sh = new ScorecardHole();
                sh.setHoleId(hole.getId());
                sh.setHoleNo(hole.getHoleNo());
                sh.setPar(hole.getPar());
                card.addHole(sh);
            }
        }
        card.getHoles().sort(Comparator.comparing(ScorecardHole::getHoleNo));
    }

    private void validateCompleteHoles(Scorecard card) {
        List<Hole> courseHoles = expectedPlayableHoles();
        Set<Long> holeIds = new HashSet<>();
        Set<Integer> holeNos = new HashSet<>();
        for (ScorecardHole sh : card.getHoles()) {
            if (!holeIds.add(sh.getHoleId())) {
                throw new BizException("记分卡存在重复球洞 ID：" + sh.getHoleId());
            }
            if (!holeNos.add(sh.getHoleNo())) {
                throw new BizException("记分卡存在重复洞号：第 " + sh.getHoleNo() + " 洞");
            }
            if (sh.getStrokes() == null || sh.getStrokes() < Dict.MIN_STROKES) {
                throw new BizException("第 " + sh.getHoleNo() + " 洞尚无合法杆数");
            }
        }
        if (card.getHoles().size() != courseHoles.size()
                || !holeIds.equals(courseHoles.stream().map(Hole::getId).collect(Collectors.toSet()))) {
            throw new BizException("应打 " + courseHoles.size() + " 个洞，当前记分卡球洞不完整");
        }
    }

    private List<Hole> expectedPlayableHoles() {
        return sortedCourseHoles().stream()
                .filter(h -> "开放".equals(h.getStatus()))
                .toList();
    }

    private List<Hole> sortedCourseHoles() {
        List<Hole> holes = holeRepository.findAll();
        if (holes.isEmpty()) {
            throw new BizException("球场尚未配置球洞，无法维护记分卡");
        }
        return holes.stream().sorted(Comparator.comparing(Hole::getHoleNo)).toList();
    }

    private void ensureEntry(Tournament tournament, Player player, LocalDateTime now) {
        entryRepository.findByTournamentIdAndPlayerId(tournament.getId(), player.getId())
                .orElseGet(() -> {
                    TournamentEntry entry = new TournamentEntry();
                    entry.setTournament(tournament);
                    entry.setPlayer(player);
                    entry.setCreatedAt(now);
                    return entryRepository.save(entry);
                });
    }

    private void validateRound(Integer roundNo, Tournament tournament) {
        if (roundNo == null || roundNo < 1) {
            throw new BizException("轮次必须大于 0");
        }
        if (roundNo > safeTotalRounds(tournament)) {
            throw new BizException("轮次不能超过赛事总轮次 " + safeTotalRounds(tournament));
        }
    }

    private int safeTotalRounds(Tournament tournament) {
        return tournament.getTotalRounds() == null ? 1 : tournament.getTotalRounds();
    }

    private void authorizeRead(Scorecard card, String role, Long playerId) {
        if (Dict.ROLE_PLAYER.equals(role)) {
            if (playerId == null || !playerId.equals(card.getPlayer().getId())) {
                throw new BizException("球员只能查看自己的记分卡");
            }
        } else if (Dict.ROLE_REFEREE.equals(role)) {
            if (!Dict.SCORECARD_DISPUTED.equals(card.getStatus())) {
                throw new BizException("裁判只能查看和处理争议中的记分卡");
            }
        } else {
            requireScorekeeper(role);
        }
    }

    private void requirePlayer(Scorecard card, Long playerId) {
        if (playerId == null || !playerId.equals(card.getPlayer().getId())) {
            throw new BizException("只能处理本人待确认的记分卡");
        }
    }

    private void requireScorekeeper(String role) {
        if (!Dict.ROLE_SCOREKEEPER.equals(role)) {
            throw new BizException("该操作仅记分员可执行");
        }
    }

    private Tournament resolveTournament(Long id) {
        if (id == null) {
            throw new BizException("必须指定赛事");
        }
        return tournamentRepository.findById(id)
                .orElseThrow(() -> new BizException("赛事不存在：" + id));
    }

    private Player resolvePlayer(Long id) {
        if (id == null) {
            throw new BizException("必须指定球员");
        }
        return playerRepository.findById(id)
                .orElseThrow(() -> new BizException("球员不存在：" + id));
    }

    private String defaultActor(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private ScorecardView toView(Scorecard card) {
        List<ScorecardHoleView> holes = card.getHoles().stream()
                .sorted(Comparator.comparing(ScorecardHole::getHoleNo))
                .map(h -> new ScorecardHoleView(
                        h.getId(), h.getHoleId(), h.getHoleNo(), h.getPar(), h.getStrokes(),
                        h.getProposedStrokes(), h.getDisputeReason(), h.getDisputedBy(), h.getDisputedAt(),
                        h.getRulingDecision(), h.getRulingNote(), h.getRuledBy(), h.getRuledAt(),
                        h.getStrokes() == null ? null : h.getStrokes() - h.getPar()))
                .toList();
        int total = holes.stream().filter(h -> h.strokes() != null).mapToInt(ScorecardHoleView::strokes).sum();
        int toPar = holes.stream().filter(h -> h.toPar() != null).mapToInt(ScorecardHoleView::toPar).sum();
        return new ScorecardView(
                card.getId(), card.getTournament().getId(), card.getTournament().getName(),
                card.getPlayer().getId(), card.getPlayer().getName(), card.getRoundNo(), card.getStatus(),
                card.getVersion(), card.getSubmittedBy(), card.getSubmittedAt(), card.getConfirmedBy(),
                card.getConfirmedAt(), card.getLastAction(), card.getUpdatedAt(), total, toPar,
                Dict.isOfficialScorecardStatus(card.getStatus()), holes);
    }

    private ScorecardVersionView toVersionView(ScorecardVersion v) {
        return new ScorecardVersionView(
                v.getId(), v.getCardVersion(), v.getSnapshotType(), v.getAction(), v.getStatus(),
                v.getActorRole(), v.getActorName(), v.getNote(), v.getCreatedAt(),
                v.getHoles().stream().sorted(Comparator.comparing(ScorecardVersionHole::getHoleNo))
                        .map(h -> new VersionHoleView(h.getHoleNo(), h.getPar(), h.getStrokes(),
                                h.getProposedStrokes(), h.getDisputeReason(), h.getDisputedBy(),
                                h.getRulingDecision(), h.getRulingNote(), h.getRuledBy()))
                        .toList());
    }
}
