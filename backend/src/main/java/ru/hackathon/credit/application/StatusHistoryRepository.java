package ru.hackathon.credit.application;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StatusHistoryRepository extends JpaRepository<StatusHistory, Long> {

    List<StatusHistory> findByApplicationIdOrderByIdAsc(Long applicationId);
}
