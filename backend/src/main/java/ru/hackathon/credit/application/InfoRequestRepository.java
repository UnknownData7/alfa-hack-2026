package ru.hackathon.credit.application;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InfoRequestRepository extends JpaRepository<InfoRequest, Long> {

    List<InfoRequest> findByApplicationIdOrderByIdAsc(Long applicationId);

    List<InfoRequest> findByApplicationIdAndStatus(Long applicationId, String status);

    Optional<InfoRequest> findByIdAndApplicationId(Long id, Long applicationId);
}
