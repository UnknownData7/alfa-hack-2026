package ru.hackathon.credit.feed;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedItemRepository extends JpaRepository<FeedItem, Long> {

    List<FeedItem> findTop20ByCompanyIdAndReadFalseOrderByCreatedAtDesc(Long companyId);

    Optional<FeedItem> findByIdAndCompanyId(Long id, Long companyId);
}
