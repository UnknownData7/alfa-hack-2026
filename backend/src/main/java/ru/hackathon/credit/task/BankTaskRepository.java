package ru.hackathon.credit.task;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BankTaskRepository extends JpaRepository<BankTask, Long> {

    List<BankTask> findByStatusInOrderByPriorityAscCreatedAtAsc(Collection<String> statuses);

    List<BankTask> findByApplicationIdOrderByIdAsc(Long applicationId);

    Optional<BankTask> findFirstByAssigneeUserIdAndStatus(Long assigneeUserId, String status);

    /**
     * Следующая задача по правилам очереди: сначала приоритет, затем более ранняя. Задачи, назначенные
     * другому андеррайтеру (возврат после запроса данных), пропускаются. SKIP LOCKED не даёт двум
     * андеррайтерам получить одну задачу.
     */
    @Query(value = """
            select * from bank_task
            where status = 'NEW' and (assignee_user_id is null or assignee_user_id = :userId)
            order by priority, created_at
            limit 1
            for update skip locked
            """, nativeQuery = true)
    Optional<BankTask> lockNextAvailable(@Param("userId") Long userId);
}
