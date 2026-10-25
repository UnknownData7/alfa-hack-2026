package ru.hackathon.credit.feed;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NewsItemRepository extends JpaRepository<NewsItem, Long> {

    @Query(value = "select news_id from feed_dismissal where company_id = :companyId", nativeQuery = true)
    List<Long> dismissedNewsIds(@Param("companyId") Long companyId);

    @Modifying
    @Query(value = "insert into feed_dismissal (company_id, news_id) values (:companyId, :newsId) on conflict do nothing",
            nativeQuery = true)
    void dismiss(@Param("companyId") Long companyId, @Param("newsId") Long newsId);
}
