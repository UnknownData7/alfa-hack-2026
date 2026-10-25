package ru.hackathon.credit.offer;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DisbursementRepository extends JpaRepository<Disbursement, Long> {

    Optional<Disbursement> findByApplicationId(Long applicationId);
}
