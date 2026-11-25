package com.kinlant.habittracker.config;

import com.kinlant.habittracker.controller.AuthController;
import com.kinlant.habittracker.model.Habit;
import com.kinlant.habittracker.model.HabitLog;
import com.kinlant.habittracker.model.User;
import com.kinlant.habittracker.repository.HabitLogRepository;
import com.kinlant.habittracker.repository.HabitRepository;
import com.kinlant.habittracker.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component // Говорит Spring запустить этот класс автоматически при старте приложения
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final HabitRepository habitRepository;
    private final HabitLogRepository habitLogRepository;

    public DataSeeder(UserRepository userRepository,
                      HabitRepository habitRepository,
                      HabitLogRepository habitLogRepository) {
        this.userRepository = userRepository;
        this.habitRepository = habitRepository;
        this.habitLogRepository = habitLogRepository;
    }

    @Override
    public void run(String... args) {
        // Если в базе ещё нет демо-пользователя — создаём его и набор привычек (TSK-06)
        if (!userRepository.existsByEmail("demo@kinlant.ru")) {
            User demoUser = new User("demo@kinlant.ru", AuthController.hashPassword("1234"));
            userRepository.save(demoUser);

            Habit h1 = new Habit(
                    "Силовая тренировка в зале",
                    "Разминка, гиперэкстензия и базовые упражнения (45 минут)",
                    "1,2,3,4,5,6,7",
                    demoUser
            );
            Habit h2 = new Habit(
                    "Практика программирования на Java",
                    "Решить минимум 2 задачи или разобрать новую тему Spring Boot",
                    "1,2,3,4,5,6,7",
                    demoUser
            );
            Habit h3 = new Habit(
                    "Пить 2 литра воды и режим сна",
                    "Ложиться спать до 23:30 для восстановления",
                    "1,2,3,4,5,6,7",
                    demoUser
            );

            habitRepository.save(h1);
            habitRepository.save(h2);
            habitRepository.save(h3);

            // Добавим несколько отметок за прошлые дни, чтобы график статистики за неделю (US-05) сразу выглядел красиво
            LocalDate today = LocalDate.now();
            habitLogRepository.save(new HabitLog(h1, today.minusDays(3)));
            habitLogRepository.save(new HabitLog(h2, today.minusDays(3)));

            habitLogRepository.save(new HabitLog(h1, today.minusDays(2)));
            habitLogRepository.save(new HabitLog(h2, today.minusDays(2)));
            habitLogRepository.save(new HabitLog(h3, today.minusDays(2)));

            habitLogRepository.save(new HabitLog(h2, today.minusDays(1)));
            habitLogRepository.save(new HabitLog(h3, today.minusDays(1)));

            habitLogRepository.save(new HabitLog(h2, today));

            System.out.println("✅ [TSK-06] Демо-данные загружены! Логин: demo@kinlant.ru | Пароль: 1234");
        }
    }
}