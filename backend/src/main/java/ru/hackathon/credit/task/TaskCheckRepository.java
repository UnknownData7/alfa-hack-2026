package ru.hackathon.credit.task;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskCheckRepository extends JpaRepository<TaskCheck, Long> {

    List<TaskCheck> findByTaskIdOrderByIdAsc(Long taskId);

    Optional<TaskCheck> findByTaskIdAndCheckCode(Long taskId, String checkCode);
}
