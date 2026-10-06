package com.golf.course.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.golf.course.entity.Scorecard;

public interface ScorecardRepository extends JpaRepository<Scorecard, Long> {

    List<Scorecard> findByTournamentIdOrderByRoundNoAscPlayerIdAsc(Long tournamentId);

    List<Scorecard> findByTournamentIdAndPlayerIdOrderByRoundNoAsc(Long tournamentId, Long playerId);

    Optional<Scorecard> findByTournamentIdAndPlayerIdAndRoundNo(Long tournamentId, Long playerId, Integer roundNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Scorecard s where s.tournament.id = :tournamentId and s.player.id = :playerId and s.roundNo = :roundNo")
    Optional<Scorecard> findByIdForUpdateForBusinessKey(@Param("tournamentId") Long tournamentId,
                                                        @Param("playerId") Long playerId,
                                                        @Param("roundNo") Integer roundNo);

    /** 串行化同一张卡的提交/确认/裁决，配合 expectedVersion 拒绝过期页面操作 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Scorecard s where s.id = :id")
    Optional<Scorecard> findByIdForUpdate(@Param("id") Long id);
}
