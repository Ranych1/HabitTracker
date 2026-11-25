package com.kinlant.habittracker.repository;

import com.kinlant.habittracker.model.HabitLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface HabitLogRepository extends JpaRepository<HabitLog, Long> {
    Optional<HabitLog> findByHabitIdAndCompletedDate(Long habitId, LocalDate completedDate);
    List<HabitLog> findByHabitUserIdAndCompletedDateBetween(Long userId, LocalDate startDate, LocalDate endDate);

    @Transactional
    void deleteByHabitId(Long habitId);
}