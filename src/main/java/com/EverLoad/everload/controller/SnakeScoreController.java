package com.EverLoad.everload.controller;

import com.EverLoad.everload.model.SnakeScore;
import com.EverLoad.everload.model.User;
import com.EverLoad.everload.repository.SnakeScoreRepository;
import com.EverLoad.everload.repository.UserRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Tag(name = "Snake", description = "Puntuaciones del juego Snake")
@RestController
@RequestMapping("/api/snake")
@RequiredArgsConstructor
public class SnakeScoreController {

    private final SnakeScoreRepository snakeScoreRepository;
    private final UserRepository userRepository;

    @PostMapping("/score")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> submitScore(@RequestBody Map<String, Integer> body, Authentication auth) {
        Integer score = body.get("score");
        if (score == null || score < 0 || score > 100_000) {
            return ResponseEntity.badRequest().body(Map.of("error", "Puntuación inválida"));
        }
        User user = userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        SnakeScore entry = SnakeScore.builder().user(user).score(score).build();
        snakeScoreRepository.save(entry);
        return ResponseEntity.ok(Map.of("saved", true));
    }

    @GetMapping("/leaderboard")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Map<String, Object>>> getLeaderboard() {
        List<Object[]> rows = snakeScoreRepository.findLeaderboard(PageRequest.of(0, 10));
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Object[] row = rows.get(i);
            String username = (String) row[0];
            int score = ((Number) row[1]).intValue();
            String avatarFilename = (String) row[2];
            String avatarUrl = avatarFilename != null ? "/api/user/avatar/img/" + avatarFilename : null;
            result.add(Map.of(
                "rank", i + 1,
                "username", username,
                "score", score,
                "avatarUrl", avatarUrl != null ? avatarUrl : ""
            ));
        }
        return ResponseEntity.ok(result);
    }
}
