package ru.hackathon.credit.task;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BankDecisionRepository extends JpaRepository<BankDecision, Long> {

    List<BankDecision> findByTaskIdInOrderByIdAsc(Collection<Long> taskIds);
}
