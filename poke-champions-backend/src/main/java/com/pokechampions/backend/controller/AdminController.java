package com.pokechampions.backend.controller;

import com.pokechampions.backend.entity.SharedTeam;
import com.pokechampions.backend.repository.SharedTeamRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin", description = "後台管理 API")
public class AdminController {

    private final SharedTeamRepository repo;
    private final String adminUsername;
    private final String adminPassword;

    public AdminController(SharedTeamRepository repo,
                           @Value("${admin.username:}") String adminUsername,
                           @Value("${admin.password:}") String adminPassword) {
        this.repo = repo;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    // ── Login ──────────────────────────────────────

    @PostMapping("/login")
    @Operation(summary = "管理員登入")
    public ResponseEntity<?> login(@RequestBody Map<String, Object> body) {
        if (adminUsername == null || adminUsername.isBlank()
                || adminPassword == null || adminPassword.isBlank()) {
            return ResponseEntity.status(503)
                    .body(Map.of("error", "Admin not configured"));
        }
        String username = body.get("username") instanceof String s ? s : "";
        String password = body.get("password") instanceof String s ? s : "";
        if (!adminUsername.equals(username) || !adminPassword.equals(password)) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "Invalid credentials"));
        }
        String token = generateToken();
        return ResponseEntity.ok(Map.of("token", token));
    }

    // ── Stats ──────────────────────────────────────

    @GetMapping("/stats")
    @Operation(summary = "取得統計數據")
    public ResponseEntity<?> stats(@RequestHeader("Authorization") String auth) {
        ResponseEntity<?> err = checkAuth(auth);
        if (err != null) return err;

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalTeams", repo.count());
        stats.put("publicTeams", repo.countByIsPublicTrue());
        stats.put("reportedTeams", repo.countByReportCountGreaterThan(0));
        stats.put("expiredTeams", repo.countByExpiredTrue());
        stats.put("totalViews", repo.sumViewCount());
        return ResponseEntity.ok(stats);
    }

    // ── Teams list ─────────────────────────────────

    @GetMapping("/teams")
    @Operation(summary = "取得全部隊伍列表（含篩選排序）")
    public ResponseEntity<?> teams(
            @RequestHeader("Authorization") String auth,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "all") String filter,
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        ResponseEntity<?> err = checkAuth(auth);
        if (err != null) return err;

        size = Math.min(size, 100);

        Sort ordering = switch (sort) {
            case "popular" -> Sort.by(Sort.Direction.DESC, "view_count");
            case "mostReported" -> Sort.by(Sort.Direction.DESC, "report_count");
            default -> Sort.by(Sort.Direction.DESC, "created_at");
        };

        String searchParam = search.isBlank() ? null : search.trim();
        Page<SharedTeam> result = repo.findAllForAdmin(searchParam, filter,
                PageRequest.of(page, size, ordering));

        var items = result.getContent().stream().map(this::toAdminItem).toList();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("content", items);
        response.put("page", result.getNumber());
        response.put("size", result.getSize());
        response.put("totalElements", result.getTotalElements());
        response.put("totalPages", result.getTotalPages());
        return ResponseEntity.ok(response);
    }

    // ── Mark expired ───────────────────────────────

    @PutMapping("/teams/{rentalCode}/mark-expired")
    @Operation(summary = "標記/取消標記隊伍失效")
    @Transactional
    public ResponseEntity<?> markExpired(
            @RequestHeader("Authorization") String auth,
            @PathVariable String rentalCode,
            @RequestBody(required = false) Map<String, Object> body) {

        ResponseEntity<?> err = checkAuth(auth);
        if (err != null) return err;

        return repo.findByRentalCode(rentalCode)
                .map(team -> {
                    boolean expired = true;
                    if (body != null && body.get("expired") instanceof Boolean b) {
                        expired = b;
                    }
                    team.setExpired(expired);
                    repo.save(team);
                    return ResponseEntity.ok(Map.of(
                            "rentalCode", rentalCode,
                            "expired", team.isExpired()));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Force delete ───────────────────────────────

    @DeleteMapping("/teams/{rentalCode}")
    @Operation(summary = "強制刪除隊伍（不需 PIN）")
    @Transactional
    public ResponseEntity<?> forceDelete(
            @RequestHeader("Authorization") String auth,
            @PathVariable String rentalCode) {

        ResponseEntity<?> err = checkAuth(auth);
        if (err != null) return err;

        return repo.findByRentalCode(rentalCode)
                .map(team -> {
                    repo.delete(team);
                    return ResponseEntity.ok(Map.of("message", "Deleted"));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Helpers ────────────────────────────────────

    private Map<String, Object> toAdminItem(SharedTeam t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("rentalCode", t.getRentalCode());
        m.put("title", t.getTitle());
        m.put("description", t.getDescription());
        m.put("isPublic", t.isPublic());
        m.put("viewCount", t.getViewCount());
        m.put("reportCount", t.getReportCount());
        m.put("expired", t.isExpired());
        m.put("createdAt", t.getCreatedAt());
        m.put("updatedAt", t.getUpdatedAt());
        return m;
    }

    private String generateToken() {
        long ts = System.currentTimeMillis();
        String payload = ts + ":" + adminPassword;
        String sig = hmacSha256(payload, adminPassword);
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((ts + ":" + sig).getBytes(StandardCharsets.UTF_8));
    }

    private ResponseEntity<?> checkAuth(String auth) {
        if (adminPassword == null || adminPassword.isBlank()) {
            return ResponseEntity.status(503)
                    .body(Map.of("error", "Admin not configured"));
        }
        if (auth == null || !auth.startsWith("Bearer ")) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "Unauthorized"));
        }
        String token = auth.substring(7);
        if (!verifyToken(token)) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "Invalid or expired token"));
        }
        return null;
    }

    private boolean verifyToken(String token) {
        try {
            String decoded = new String(
                    Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            String[] parts = decoded.split(":", 2);
            if (parts.length != 2) return false;

            long ts = Long.parseLong(parts[0]);
            long now = System.currentTimeMillis();
            if (now - ts > 24 * 60 * 60 * 1000) return false; // 24h expiry

            String expectedSig = hmacSha256(ts + ":" + adminPassword, adminPassword);
            return expectedSig.equals(parts[1]);
        } catch (Exception e) {
            return false;
        }
    }

    private static String hmacSha256(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
