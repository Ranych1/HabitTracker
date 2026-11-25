package com.kinlant.habittracker.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(
        name = "habit_logs",
        uniqueConstraints = @UniqueConstraint(columnNames = {"habit_id", "completed_date"})
)
public class HabitLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "habit_id", nullable = false)
    @JsonIgnore
    private Habit habit;

    @Column(name = "completed_date", nullable = false)
    private LocalDate completedDate;

    public HabitLog() {
    }

    public HabitLog(Habit habit, LocalDate completedDate) {
        this.habit = habit;
        this.completedDate = completedDate;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Habit getHabit() { return habit; }
    public void setHabit(Habit habit) { this.habit = habit; }

    public LocalDate getCompletedDate() { return completedDate; }
    public void setCompletedDate(LocalDate completedDate) { this.completedDate = completedDate; }
}