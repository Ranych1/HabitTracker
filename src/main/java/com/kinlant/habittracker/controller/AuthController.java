package com.kinlant.habittracker.controller;

import com.kinlant.habittracker.model.User;
import com.kinlant.habittracker.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;

    // Хранилище активных токенов: Токен -> ID пользователя
    public static final Map<String, Long> TOKEN_STORAGE = new ConcurrentHashMap<>();

    // Spring сам передаст готовый UserRepository через конструктор (Dependency Injection)
    public AuthController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // Эндпоинт регистрации: POST /api/auth/register
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String password = body.get("password");

        // Валидация пустых полей -> ошибка 400
        if (email == null || email.isBlank() || password == null || password.length() < 4) {
            return ResponseEntity.status(400)
                    .body(Map.of("error", "Введите корректный email и пароль (минимум 4 символа)"));
        }

        email = email.trim().toLowerCase();

        // Проверка дубликата email (критерий US-01)
        if (userRepository.existsByEmail(email)) {
            return ResponseEntity.status(400)
                    .body(Map.of("error", "Пользователь с таким email уже существует"));
        }

        // Сохраняем пользователя с хэшем пароля в БД
        User newUser = new User(email, hashPassword(password));
        userRepository.save(newUser);

        // Выдаём токен авторизации
        String token = UUID.randomUUID().toString();
        TOKEN_STORAGE.put(token, newUser.getId());

        return ResponseEntity.ok(Map.of(
                "token", token,
                "email", newUser.getEmail(),
                "userId", newUser.getId()
        ));
    }

    // Эндпоинт входа: POST /api/auth/login
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String password = body.get("password");

        if (email == null || password == null) {
            return ResponseEntity.status(400).body(Map.of("error", "Email и пароль обязательны"));
        }

        Optional<User> userOpt = userRepository.findByEmail(email.trim().toLowerCase());

        // Если email не найден или хэш пароля не совпал -> ошибка 401
        if (userOpt.isEmpty() || !userOpt.get().getPasswordHash().equals(hashPassword(password))) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "Неверный email или пароль"));
        }

        User user = userOpt.get();
        String token = UUID.randomUUID().toString();
        TOKEN_STORAGE.put(token, user.getId());

        return ResponseEntity.ok(Map.of(
                "token", token,
                "email", user.getEmail(),
                "userId", user.getId()
        ));
    }

    // Вспомогательный метод хэширования пароля алгоритмом SHA-256
    public static String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Ошибка хэширования", e);
        }
    }

    // Вспомогательный метод для проверки токена из других контроллеров
    public static Long getUserIdByToken(String authHeader) {
        if (authHeader == null || authHeader.isBlank()) return null;
        String token = authHeader.replace("Bearer ", "").trim();
        return TOKEN_STORAGE.get(token);
    }
}