package com.kinlant.habittracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class HabitTrackerApplication {

    public static void main(String[] args) {
        SpringApplication.run(HabitTrackerApplication.class, args);
        System.out.println("🚀 Сервер HabitTracker (kinlant) запущен: http://localhost:8080");
    }
}