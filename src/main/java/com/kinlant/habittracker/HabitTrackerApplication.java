package com.kinlant.habittracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class HabitTrackerApplication {

    public static void main(String[] args) {
        SpringApplication.run(HabitTrackerApplication.class, args);
        System.out.println("🚀 Сервер HabitTracker (kinlant) запущен: http://localhost:8080");
    }git config --global user.name "Kirill Altunin"
    git config --global user.email "your-github-email@example.com"
    git commit -m "QA-DOC: HabitTracker MVP, автотесты, CI и документация"
    git push -u origin main
}