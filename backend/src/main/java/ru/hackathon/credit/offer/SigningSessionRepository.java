package ru.hackathon.credit.offer;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SigningSessionRepository extends JpaRepository<SigningSession, Long> {

    List<SigningSession> findByApplicationIdAndStatus(Long applicationId, String status);

    Optional<SigningSession> findTopByApplicationIdOrderByIdDesc(Long applicationId);
}
