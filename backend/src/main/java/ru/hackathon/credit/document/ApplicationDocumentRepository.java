package ru.hackathon.credit.document;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationDocumentRepository extends JpaRepository<ApplicationDocument, Long> {

    List<ApplicationDocument> findByApplicationIdOrderByIdAsc(Long applicationId);

    Optional<ApplicationDocument> findByIdAndApplicationId(Long id, Long applicationId);

    long countByApplicationId(Long applicationId);
}
