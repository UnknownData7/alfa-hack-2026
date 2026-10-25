package ru.hackathon.credit.application;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ApplicationRepository extends JpaRepository<LoanApplication, Long> {

    List<LoanApplication> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

    Optional<LoanApplication> findByIdAndCompanyId(Long id, Long companyId);

    List<LoanApplication> findByStatusIn(Collection<ApplicationStatus> statuses);

    @Query(value = "select nextval('application_number_seq')", nativeQuery = true)
    long nextNumber();
}
