package ru.hackathon.credit.scoring;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ExternalCheckResultRepository extends JpaRepository<ExternalCheckResult, Long> {

    List<ExternalCheckResult> findByApplicationIdOrderByIdAsc(Long applicationId);
}
