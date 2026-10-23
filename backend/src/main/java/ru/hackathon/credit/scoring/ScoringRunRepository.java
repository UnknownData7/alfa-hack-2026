package ru.hackathon.credit.scoring;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ScoringRunRepository extends JpaRepository<ScoringRun, Long> {

    List<ScoringRun> findByApplicationIdOrderByRunNoAsc(Long applicationId);

    Optional<ScoringRun> findTopByApplicationIdOrderByRunNoDesc(Long applicationId);
}
