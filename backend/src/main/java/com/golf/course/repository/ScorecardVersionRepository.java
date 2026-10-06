package com.golf.course.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.golf.course.entity.ScorecardVersion;

public interface ScorecardVersionRepository extends JpaRepository<ScorecardVersion, Long> {

    List<ScorecardVersion> findByScorecardIdOrderByIdAsc(Long scorecardId);
}
