package com.kinlant.habittracker.controller;

import com.kinlant.habittracker.model.Habit;
import com.kinlant.habittracker.model.HabitLog;
import com.kinlant.habittracker.model.User;
import com.kinlant.habittracker.repository.HabitLogRepository;
import com.kinlant.habittracker.repository.HabitRepository;
import com.kinlant.habittracker.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.*;

@RestController
@RequestMapping("/api/habits")
public class HabitController {

    private final HabitRepository habitRepository;
    private final HabitLogRepository habitLogRepository;
    private final UserRepository userRepository;

    public HabitController(HabitRepository habitRepository,
                           HabitLogRepository habitLogRepository,
                           UserRepository userRepository) {
        this.habitRepository = habitRepository;
        this.habitLogRepository = habitLogRepository;
        this.userRepository = userRepository;
    }

    // 1. Получить все привычки пользователя (US-02)
    @GetMapping
    public ResponseEntity<?> getAllHabits(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        User user = getAuthenticatedUser(authHeader);
        if (user == null) return unauthorized();

        LocalDate today = LocalDate.now();
        List<Habit> habits = habitRepository.findByUserId(user.getId());

        List<Map<String, Object>> result = new ArrayList<>();
        for (Habit h : habits) {
            boolean completedToday = habitLogRepository
                    .findByHabitIdAndCompletedDate(h.getId(), today)
                    .isPresent();
            result.add(toHabitDto(h, completedToday));
        }
        return ResponseEntity.ok(result);
    }

    // 2. Получить список привычек только на сегодняшний день недели (US-03)
    @GetMapping("/today")
    public ResponseEntity<?> getTodayHabits(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        User user = getAuthenticatedUser(authHeader);
        if (user == null) return unauthorized();

        LocalDate today = LocalDate.now();
        // Номер дня недели: от "1" (Понедельник) до "7" (Воскресенье)
        String todayDayOfWeek = String.valueOf(today.getDayOfWeek().getValue());

        List<Habit> habits = habitRepository.findByUserId(user.getId());
        List<Map<String, Object>> todayList = new ArrayList<>();

        for (Habit h : habits) {
            List<String> days = Arrays.asList(h.getDaysOfWeek().split(","));
            if (days.contains(todayDayOfWeek)) {
                boolean completedToday = habitLogRepository
                        .findByHabitIdAndCompletedDate(h.getId(), today)
                        .isPresent();
                todayList.add(toHabitDto(h, completedToday));
            }
        }
        return ResponseEntity.ok(todayList);
    }

    // 3. Создать новую привычку (US-02)
    @PostMapping
    public ResponseEntity<?> createHabit(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, String> body) {

        User user = getAuthenticatedUser(authHeader);
        if (user == null) return unauthorized();

        String title = body.get("title");
        String description = body.getOrDefault("description", "");
        String daysOfWeek = body.getOrDefault("daysOfWeek", "1,2,3,4,5,6,7");

        if (title == null || title.isBlank()) {
            return ResponseEntity.status(400).body(Map.of("error", "Название привычки не может быть пустым"));
        }

        Habit habit = new Habit(title.trim(), description.trim(), daysOfWeek, user);
        habitRepository.save(habit);

        return ResponseEntity.ok(toHabitDto(habit, false));
    }

    // 4. Редактировать привычку (US-02)
    @PutMapping("/{id}")
    public ResponseEntity<?> updateHabit(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {

        User user = getAuthenticatedUser(authHeader);
        if (user == null) return unauthorized();

        Optional<Habit> habitOpt = habitRepository.findById(id);
        // Проверяем, существует ли привычка и принадлежит ли она текущему пользователю (защита от чужих привычек US-02)
        if (habitOpt.isEmpty() || !habitOpt.get().getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Привычка не найдена или нет доступа"));
        }

        String title = body.get("title");
        if (title == null || title.isBlank()) {
            return ResponseEntity.status(400).body(Map.of("error", "Название привычки не может быть пустым"));
        }

        Habit habit = habitOpt.get();
        habit.setTitle(title.trim());
        habit.setDescription(body.getOrDefault("description", "").trim());
        habit.setDaysOfWeek(body.getOrDefault("daysOfWeek", habit.getDaysOfWeek()));
        habitRepository.save(habit);

        boolean completedToday = habitLogRepository
                .findByHabitIdAndCompletedDate(habit.getId(), LocalDate.now())
                .isPresent();

        return ResponseEntity.ok(toHabitDto(habit, completedToday));
    }

    // 5. Удалить привычку (US-02)
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteHabit(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable Long id) {

        User user = getAuthenticatedUser(authHeader);
        if (user == null) return unauthorized();

        Optional<Habit> habitOpt = habitRepository.findById(id);
        if (habitOpt.isEmpty() || !habitOpt.get().getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Привычка не найдена или нет доступа"));
        }

        // Сначала удаляем отметки выполнения этой привычки, затем саму привычку
        habitLogRepository.deleteByHabitId(id);
        habitRepository.deleteById(id);

        return ResponseEntity.ok(Map.of("message", "Привычка удалена"));
    }

    // 6. Отметить выполнение привычки за сегодня или отменить отметку (US-04)
    @PostMapping("/{id}/toggle")
    public ResponseEntity<?> toggleHabitToday(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable Long id) {

        User user = getAuthenticatedUser(authHeader);
        if (user == null) return unauthorized();

        Optional<Habit> habitOpt = habitRepository.findById(id);
        if (habitOpt.isEmpty() || !habitOpt.get().getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Привычка не найдена или нет доступа"));
        }

        Habit habit = habitOpt.get();
        LocalDate today = LocalDate.now();
        Optional<HabitLog> existingLog = habitLogRepository.findByHabitIdAndCompletedDate(id, today);

        boolean completedNow;
        if (existingLog.isPresent()) {
            // Если отметка уже была — удаляем её (отмена отметки по US-04)
            habitLogRepository.delete(existingLog.get());
            completedNow = false;
        } else {
            // Если отметки не было — создаём новую запись в БД
            habitLogRepository.save(new HabitLog(habit, today));
            completedNow = true;
        }

        return ResponseEntity.ok(toHabitDto(habit, completedNow));
    }

    // 7. Статистика выполнения за последние 7 дней (US-05)
    @GetMapping("/stats/week")
    public ResponseEntity<?> getWeeklyStats(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        User user = getAuthenticatedUser(authHeader);
        if (user == null) return unauthorized();

        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(6); // 7 дней включая сегодня

        List<Habit> userHabits = habitRepository.findByUserId(user.getId());
        List<HabitLog> logs = habitLogRepository.findByHabitUserIdAndCompletedDateBetween(user.getId(), startDate, today);

        List<Map<String, Object>> daysStats = new ArrayList<>();
        Locale ruLocale = Locale.forLanguageTag("ru");

        for (int i = 0; i < 7; i++) {
            LocalDate date = startDate.plusDays(i);
            String dayOfWeekNum = String.valueOf(date.getDayOfWeek().getValue());

            // Сколько привычек было запланировано на этот день недели
            long plannedCount = userHabits.stream()
                    .filter(h -> Arrays.asList(h.getDaysOfWeek().split(",")).contains(dayOfWeekNum))
                    .count();

            // Сколько реально выполнено в эту дату
            long completedCount = logs.stream()
                    .filter(l -> l.getCompletedDate().equals(date))
                    .count();

            String dayLabel = date.getDayOfWeek().getDisplayName(TextStyle.SHORT, ruLocale);

            Map<String, Object> dayInfo = new LinkedHashMap<>();
            dayInfo.put("date", date.toString());
            dayInfo.put("dayLabel", dayLabel.toUpperCase());
            dayInfo.put("completed", completedCount);
            dayInfo.put("planned", plannedCount);
            daysStats.add(dayInfo);
        }

        return ResponseEntity.ok(daysStats);
    }

    // Вспомогательные методы
    private User getAuthenticatedUser(String authHeader) {
        Long userId = AuthController.getUserIdByToken(authHeader);
        if (userId == null) return null;
        return userRepository.findById(userId).orElse(null);
    }

    private ResponseEntity<Map<String, String>> unauthorized() {
        return ResponseEntity.status(401).body(Map.of("error", "Требуется авторизация"));
    }

    private Map<String, Object> toHabitDto(Habit habit, boolean completedToday) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", habit.getId());
        dto.put("title", habit.getTitle());
        dto.put("description", habit.getDescription());
        dto.put("daysOfWeek", habit.getDaysOfWeek());
        dto.put("completedToday", completedToday);
        return dto;
    }
}