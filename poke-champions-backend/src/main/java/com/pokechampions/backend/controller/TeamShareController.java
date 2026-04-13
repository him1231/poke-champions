package com.pokechampions.backend.controller;

import com.pokechampions.backend.entity.SharedTeam;
import com.pokechampions.backend.service.TeamShareService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/teams")
@Tag(name = "Team Share", description = "無會員制隊伍分享 API")
public class TeamShareController {

    private final TeamShareService service;

    public TeamShareController(TeamShareService service) {
        this.service = service;
    }

    // ──────────────────────────────────────────────
    //  POST /api/teams — 建立新隊伍
    // ──────────────────────────────────────────────
    @PostMapping
    @Operation(summary = "建立分享隊伍")
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body) {
        String rentalCode = str(body, "rentalCode");
        String pin        = str(body, "pin");
        String title      = str(body, "title");
        String description = str(body, "description");
        Object snapshot   = body.get("teamSnapshot");
        Object previewIds = body.get("previewPokemonIds");
        Boolean isPublic  = body.get("isPublic") instanceof Boolean b ? b : true;

        if (rentalCode == null || rentalCode.isBlank())
            return badRequest("rentalCode is required");
        if (pin == null || pin.length() < 4 || pin.length() > 8)
            return badRequest("pin must be 4-8 characters");
        if (title == null || title.isBlank())
            return badRequest("title is required");
        if (snapshot == null)
            return badRequest("teamSnapshot is required");

        if (service.existsByRentalCode(rentalCode))
            return ResponseEntity.status(409)
                    .body(Map.of("error", "Rental code already exists"));

        SharedTeam team = new SharedTeam();
        team.setRentalCode(rentalCode.trim());
        team.setTitle(title.trim());
        team.setDescription(description != null ? description.trim() : "");
        team.setTeamSnapshot(toJson(snapshot));
        team.setPreviewPokemonIds(previewIds != null ? toJson(previewIds) : "[]");
        team.setPublic(isPublic);

        SharedTeam saved = service.create(team, pin);
        return ResponseEntity.status(201).body(toPublicMap(saved));
    }

    // ──────────────────────────────────────────────
    //  GET /api/teams — 公開列表（分頁 + 排序）
    // ──────────────────────────────────────────────
    @GetMapping
    @Operation(summary = "取得公開隊伍列表")
    public ResponseEntity<?> list(
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        size = Math.min(size, 100);
        Page<SharedTeam> result = service.listPublic(sort, page, size);

        var items = result.getContent().stream().map(this::toListItem).toList();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("content", items);
        response.put("page", result.getNumber());
        response.put("size", result.getSize());
        response.put("totalElements", result.getTotalElements());
        response.put("totalPages", result.getTotalPages());

        return ResponseEntity.ok(response);
    }

    // ──────────────────────────────────────────────
    //  GET /api/teams/{rentalCode} — 隊伍詳情 + 瀏覽數 +1
    // ──────────────────────────────────────────────
    @GetMapping("/{rentalCode}")
    @Operation(summary = "取得隊伍詳情（自動累加瀏覽數）")
    public ResponseEntity<?> getOne(@PathVariable String rentalCode) {
        return service.findByRentalCode(rentalCode)
                .map(team -> {
                    service.incrementViewCount(rentalCode);
                    team.setViewCount(team.getViewCount() + 1);
                    return ResponseEntity.ok(toPublicMap(team));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ──────────────────────────────────────────────
    //  PUT /api/teams/{rentalCode} — 更新隊伍（需 PIN）
    // ──────────────────────────────────────────────
    @PutMapping("/{rentalCode}")
    @Operation(summary = "更新隊伍（需驗證 PIN）")
    public ResponseEntity<?> update(@PathVariable String rentalCode,
                                    @RequestBody Map<String, Object> body) {
        String pin = str(body, "pin");
        if (pin == null || pin.isBlank())
            return badRequest("pin is required");

        return service.findByRentalCode(rentalCode)
                .map(team -> {
                    if (!service.verifyPin(pin, team.getPinHash()))
                        return ResponseEntity.status(403)
                                .body((Object) Map.of("error", "Invalid PIN"));

                    if (body.containsKey("title")) {
                        String t = str(body, "title");
                        if (t != null && !t.isBlank()) team.setTitle(t.trim());
                    }
                    if (body.containsKey("description"))
                        team.setDescription(str(body, "description"));
                    if (body.containsKey("teamSnapshot"))
                        team.setTeamSnapshot(toJson(body.get("teamSnapshot")));
                    if (body.containsKey("previewPokemonIds"))
                        team.setPreviewPokemonIds(toJson(body.get("previewPokemonIds")));
                    if (body.get("isPublic") instanceof Boolean b)
                        team.setPublic(b);

                    SharedTeam updated = service.update(team);
                    return ResponseEntity.ok((Object) toPublicMap(updated));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ──────────────────────────────────────────────
    //  DELETE /api/teams/{rentalCode} — 刪除隊伍（需 PIN）
    // ──────────────────────────────────────────────
    @DeleteMapping("/{rentalCode}")
    @Operation(summary = "刪除隊伍（需驗證 PIN）")
    public ResponseEntity<?> delete(@PathVariable String rentalCode,
                                    @RequestBody Map<String, Object> body) {
        String pin = str(body, "pin");
        if (pin == null || pin.isBlank())
            return badRequest("pin is required");

        return service.findByRentalCode(rentalCode)
                .map(team -> {
                    if (!service.verifyPin(pin, team.getPinHash()))
                        return ResponseEntity.status(403)
                                .body((Object) Map.of("error", "Invalid PIN"));
                    service.delete(team);
                    return ResponseEntity.ok((Object) Map.of("message", "Deleted"));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ──────────────────────────────────────────────
    //  POST /api/teams/{rentalCode}/verify-pin
    // ──────────────────────────────────────────────
    @PostMapping("/{rentalCode}/verify-pin")
    @Operation(summary = "驗證 PIN 碼")
    public ResponseEntity<?> verifyPin(@PathVariable String rentalCode,
                                       @RequestBody Map<String, Object> body) {
        String pin = str(body, "pin");
        if (pin == null || pin.isBlank())
            return badRequest("pin is required");

        return service.findByRentalCode(rentalCode)
                .map(team -> {
                    boolean valid = service.verifyPin(pin, team.getPinHash());
                    return ResponseEntity.ok(Map.of("valid", valid));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ──────────────────────────────────────────────
    //  POST /api/teams/{rentalCode}/report — 回報代碼失效
    // ──────────────────────────────────────────────
    @PostMapping("/{rentalCode}/report")
    @Operation(summary = "回報隊伍代碼已失效")
    public ResponseEntity<?> report(@PathVariable String rentalCode) {
        return service.findByRentalCode(rentalCode)
                .map(team -> {
                    service.incrementReportCount(rentalCode);
                    return ResponseEntity.ok(Map.of(
                            "message", "Report submitted",
                            "reportCount", team.getReportCount() + 1));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ──────────────────────────────────────────────
    //  Helpers
    // ──────────────────────────────────────────────

    private Map<String, Object> toPublicMap(SharedTeam t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("rentalCode", t.getRentalCode());
        m.put("title", t.getTitle());
        m.put("description", t.getDescription());
        m.put("teamSnapshot", parseJson(t.getTeamSnapshot()));
        m.put("previewPokemonIds", parseJson(t.getPreviewPokemonIds()));
        m.put("isPublic", t.isPublic());
        m.put("viewCount", t.getViewCount());
        m.put("reportCount", t.getReportCount());
        m.put("expired", t.isExpired());
        m.put("createdAt", t.getCreatedAt());
        m.put("updatedAt", t.getUpdatedAt());
        return m;
    }

    private Map<String, Object> toListItem(SharedTeam t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("rentalCode", t.getRentalCode());
        m.put("title", t.getTitle());
        m.put("description", t.getDescription());
        m.put("previewPokemonIds", parseJson(t.getPreviewPokemonIds()));
        m.put("viewCount", t.getViewCount());
        m.put("createdAt", t.getCreatedAt());
        return m;
    }

    private static String str(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v instanceof String s ? s : null;
    }

    private static ResponseEntity<?> badRequest(String msg) {
        return ResponseEntity.badRequest().body(Map.of("error", msg));
    }

    private static String toJson(Object obj) {
        if (obj instanceof String s) return s;
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static Object parseJson(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, Object.class);
        } catch (Exception e) {
            return json;
        }
    }
}
