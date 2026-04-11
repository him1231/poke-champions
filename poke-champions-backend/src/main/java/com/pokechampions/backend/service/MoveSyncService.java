package com.pokechampions.backend.service;

import com.pokechampions.backend.entity.Move;
import com.pokechampions.backend.entity.Move.MoveCategory;
import com.pokechampions.backend.entity.Move.MoveSource;
import com.pokechampions.backend.entity.Pokemon;
import com.pokechampions.backend.entity.PokemonMove;
import com.pokechampions.backend.entity.PokemonType;
import com.pokechampions.backend.repository.MoveRepository;
import com.pokechampions.backend.repository.PokemonMoveRepository;
import com.pokechampions.backend.repository.PokemonRepository;
import com.pokechampions.backend.repository.PokemonTypeRepository;
import com.pokechampions.backend.util.PokeApiSlugResolver;
import org.jsoup.Jsoup;
import org.springframework.web.client.RestClient;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MoveSyncService {

    private static final Logger log = LoggerFactory.getLogger(MoveSyncService.class);

    private static final String DEFAULT_GAME8_URL =
            "https://game8.co/games/Pokemon-Champions/archives/590397";
    private static final String POKEAPI_MOVE_URL = "https://pokeapi.co/api/v2/move/";
    private static final String GEMINI_API_BASE = "https://generativelanguage.googleapis.com/v1beta/models/";

    @Value("${scraper.timeout-ms:15000}")
    private int timeoutMs;

    @Value("${scraper.move-detail-delay-ms:1500}")
    private int detailDelayMs;

    @Value("${scraper.pokeapi-delay-ms:600}")
    private int pokeApiDelayMs;

    @Value("${gemini.api-key:}")
    private String geminiApiKey;

    @Value("${gemini.model:gemini-2.0-flash}")
    private String geminiModel;

    @Value("${gemini.batch-size:20}")
    private int batchSize;

    @Value("${gemini.delay-ms:1000}")
    private int geminiDelayMs;

    private final MoveRepository moveRepository;
    private final PokemonTypeRepository typeRepository;
    private final PokemonRepository pokemonRepository;
    private final PokemonMoveRepository pokemonMoveRepository;

    public MoveSyncService(MoveRepository moveRepository,
                           PokemonTypeRepository typeRepository,
                           PokemonRepository pokemonRepository,
                           PokemonMoveRepository pokemonMoveRepository) {
        this.moveRepository = moveRepository;
        this.typeRepository = typeRepository;
        this.pokemonRepository = pokemonRepository;
        this.pokemonMoveRepository = pokemonMoveRepository;
    }

    // ═══════════════════════════════════════════════════════════
    //  Phase 1: 從主頁同步招式列表（含 game8Url）
    // ═══════════════════════════════════════════════════════════

    @Transactional
    public MoveSyncReport syncFromGame8() {
        return syncFromGame8(DEFAULT_GAME8_URL);
    }

    @Transactional
    public MoveSyncReport syncFromGame8(String url) {
        log.info("========== MoveSyncService: 開始從 Game8 爬取招式 ==========");

        Document doc = fetchDocument(url);
        List<RawMoveEntry> rawMoves = parseMovesTable(doc);
        log.info("解析到 {} 筆招式資料", rawMoves.size());

        int created = 0, updated = 0, failed = 0;

        for (RawMoveEntry raw : rawMoves) {
            try {
                String slug = toSlug(raw.displayName());
                Optional<PokemonType> typeOpt = typeRepository.findByName(raw.typeName().toLowerCase());
                if (typeOpt.isEmpty()) {
                    log.warn("  ⚠ 找不到屬性 '{}' for move '{}', 跳過", raw.typeName(), raw.displayName());
                    failed++;
                    continue;
                }

                MoveCategory category = parseCategory(raw.category());
                if (category == null) {
                    log.warn("  ⚠ 無法解析分類 '{}' for move '{}', 跳過", raw.category(), raw.displayName());
                    failed++;
                    continue;
                }

                Integer power = parseIntOrNull(raw.power());
                int pp = parseIntOrZero(raw.pp());
                String desc = raw.description() != null ? truncate(raw.description(), 1000) : null;

                Optional<Move> existing = moveRepository.findByName(slug);
                if (existing.isPresent()) {
                    Move move = existing.get();
                    boolean changed = applyMoveUpdates(move, raw.displayName(), typeOpt.get(),
                            category, power, pp, desc, raw.game8Url());
                    if (changed) {
                        moveRepository.save(move);
                        updated++;
                    }
                } else {
                    Move move = new Move(slug, raw.displayName(), typeOpt.get(), category,
                            power, pp, desc, MoveSource.CHAMPIONS_GAME8);
                    move.setGame8Url(raw.game8Url());
                    moveRepository.save(move);
                    created++;
                    log.debug("  ✓ 新增 {} ({}, {}, Power={}, PP={})",
                            slug, typeOpt.get().getName(), category, power != null ? power : "-", pp);
                }
            } catch (Exception e) {
                log.warn("  ✗ 處理 '{}' 失敗: {}", raw.displayName(), e.getMessage());
                failed++;
            }
        }

        log.info("========== 招式同步完成: 新增 {}, 更新 {}, 失敗 {}, 共解析 {} ==========",
                created, updated, failed, rawMoves.size());
        return new MoveSyncReport(rawMoves.size(), created, updated, failed);
    }

    // ═══════════════════════════════════════════════════════════
    //  Phase 2: 爬取每招的詳情頁，取得 accuracy + 可學習寶可夢
    // ═══════════════════════════════════════════════════════════

    /**
     * 遍歷所有有 game8Url 的招式，爬取詳情頁取得：
     * 1. accuracy（命中率）
     * 2. 可學習該招式的寶可夢列表 → 寫入 pokemon_moves
     *
     * @param forceRefresh 若 true，重新爬取所有招式；若 false，只爬尚未有 pokemon_move 關聯的招式
     */
    public PokemonMoveSyncReport syncPokemonMovesFromGame8(boolean forceRefresh) {
        List<Move> moves = moveRepository.findByGame8UrlIsNotNull();
        log.info("========== 開始爬取招式詳情頁: {} 筆有 game8Url ==========", moves.size());

        int totalProcessed = 0, totalLinked = 0, totalSkipped = 0, totalFailed = 0;
        Set<String> unmatchedPokemon = new LinkedHashSet<>();

        for (int i = 0; i < moves.size(); i++) {
            Move move = moves.get(i);

            if (!forceRefresh) {
                List<PokemonMove> existingLinks = pokemonMoveRepository.findByMove(move);
                if (!existingLinks.isEmpty()) {
                    totalSkipped++;
                    continue;
                }
            }

            try {
                if (i > 0) {
                    Thread.sleep(detailDelayMs);
                }

                log.info("  [{}/{}] 爬取 {} → {}", i + 1, moves.size(),
                        move.getDisplayName(), move.getGame8Url());

                Document detailDoc = fetchDocument(move.getGame8Url());

                Integer accuracy = parseAccuracyFromDetail(detailDoc);
                if (accuracy != null && !accuracy.equals(move.getAccuracy())) {
                    move.setAccuracy(accuracy);
                    moveRepository.save(move);
                    log.debug("    ↻ 更新 accuracy = {}", accuracy);
                }

                List<String> pokemonNames = parsePokemonLearnersFromDetail(detailDoc);
                log.info("    可學習寶可夢: {} 隻", pokemonNames.size());

                int linked = linkPokemonToMove(move, pokemonNames, unmatchedPokemon);
                totalLinked += linked;
                totalProcessed++;

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("  ⚠ 中斷");
                break;
            } catch (Exception e) {
                log.warn("  ✗ {} 詳情頁爬取失敗: {}", move.getDisplayName(), e.getMessage());
                totalFailed++;
            }
        }

        if (!unmatchedPokemon.isEmpty()) {
            log.warn("========== 以下寶可夢名稱無法匹配到 DB（共 {} 個）==========", unmatchedPokemon.size());
            unmatchedPokemon.forEach(name -> log.warn("  ⚠ {}", name));
        }

        log.info("========== 詳情頁爬取完成: 已處理 {}, 新建關聯 {}, 跳過 {}, 失敗 {} ==========",
                totalProcessed, totalLinked, totalSkipped, totalFailed);
        return new PokemonMoveSyncReport(
                moves.size(), totalProcessed, totalLinked, totalSkipped, totalFailed,
                new ArrayList<>(unmatchedPokemon));
    }

    // ═══════════════════════════════════════════════════════════
    //  Phase 3: 從 PokeAPI 同步招式繁體中文名稱
    // ═══════════════════════════════════════════════════════════

    /**
     * 透過 PokeAPI /move/{slug} 取得繁體中文招式名稱，寫入 Move.chineseName。
     *
     * @param forceRefresh 若 true，重新查詢所有招式；若 false，只查 chineseName 為 null 的招式
     */
    public ChineseNameSyncReport syncChineseNames(boolean forceRefresh) {
        List<Move> moves = forceRefresh ? moveRepository.findAll() : moveRepository.findByChineseNameIsNull();
        log.info("========== 開始同步招式中文名: {} 筆待處理 ==========", moves.size());

        RestClient restClient = RestClient.builder().baseUrl(POKEAPI_MOVE_URL).build();

        int updated = 0, skipped = 0, notFound = 0;
        List<String> failedMoves = new ArrayList<>();

        for (int i = 0; i < moves.size(); i++) {
            Move move = moves.get(i);

            try {
                if (i > 0) {
                    Thread.sleep(pokeApiDelayMs);
                }

                log.info("  [{}/{}] 查詢 PokeAPI: {}", i + 1, moves.size(), move.getName());

                @SuppressWarnings("unchecked")
                Map<String, Object> body = restClient.get()
                        .uri(move.getName())
                        .retrieve()
                        .body(Map.class);

                if (body == null) {
                    log.warn("    ⚠ {} 回傳 null", move.getName());
                    failedMoves.add(move.getName());
                    notFound++;
                    continue;
                }

                String zhHant = extractName(body, "zh-hant");
                if (zhHant != null && !zhHant.equals(move.getChineseName())) {
                    move.setChineseName(zhHant);
                    moveRepository.save(move);
                    updated++;
                    log.info("    ✓ {} → {}", move.getName(), zhHant);
                } else if (zhHant == null) {
                    String zhHans = extractName(body, "zh-hans");
                    if (zhHans != null && !zhHans.equals(move.getChineseName())) {
                        move.setChineseName(zhHans);
                        moveRepository.save(move);
                        updated++;
                        log.info("    ✓ {} → {} (簡體)", move.getName(), zhHans);
                    } else {
                        skipped++;
                        log.debug("    ⏭ {} 無中文名", move.getName());
                    }
                } else {
                    skipped++;
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("  ⚠ 中斷");
                break;
            } catch (Exception e) {
                String msg = e.getMessage();
                if (msg != null && msg.contains("404")) {
                    log.warn("    ⚠ {} 在 PokeAPI 找不到 (404)", move.getName());
                    failedMoves.add(move.getName());
                    notFound++;
                } else {
                    log.warn("    ✗ {} 查詢失敗: {}", move.getName(), msg);
                    failedMoves.add(move.getName());
                    notFound++;
                }
            }
        }

        log.info("========== 中文名同步完成: 更新 {}, 跳過 {}, 找不到 {}, 共 {} ==========",
                updated, skipped, notFound, moves.size());
        return new ChineseNameSyncReport(moves.size(), updated, skipped, notFound, failedMoves);
    }

    @SuppressWarnings("unchecked")
    private String extractName(Map<String, Object> body, String langCode) {
        List<Map<String, Object>> names = (List<Map<String, Object>>) body.get("names");
        if (names == null) return null;
        for (Map<String, Object> entry : names) {
            Map<String, Object> lang = (Map<String, Object>) entry.get("language");
            if (lang != null && langCode.equals(lang.get("name"))) {
                return (String) entry.get("name");
            }
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════
    //  Phase 4: 用 Gemini 批次翻譯招式描述
    // ═══════════════════════════════════════════════════════════

    private static final String TRANSLATION_SYSTEM_PROMPT = """
            你是寶可夢遊戲翻譯專家。請將以下 JSON 中的寶可夢招式效果描述從英文翻譯成繁體中文。
            規則：
            1. 使用台灣官方寶可夢譯名和遊戲術語
            2. 保持簡潔，符合遊戲內描述風格
            3. 回傳格式必須是相同 key 的 JSON 物件，value 為翻譯後的繁體中文
            4. 只回傳 JSON，不要有其他文字""";

    private static final int MAX_RETRIES = 3;
    private static final long DEFAULT_RETRY_DELAY_MS = 60_000;
    private static final Pattern RETRY_DELAY_PATTERN = Pattern.compile("\"retryDelay\"\\s*:\\s*\"(\\d+)s?\"");

    /**
     * 透過 Gemini API 批次翻譯 Move.description → Move.chineseDescription。
     * 遇到 429 rate limit 時會自動等待並重試（最多 MAX_RETRIES 次）。
     *
     * @param forceRefresh 若 true，重新翻譯所有有英文描述的招式；若 false，只翻譯 chineseDescription 為 null 的
     */
    public TranslationReport translateDescriptions(boolean forceRefresh) {
        if (geminiApiKey == null || geminiApiKey.isBlank()) {
            throw new IllegalStateException("未設定 gemini.api-key，無法翻譯");
        }

        List<Move> moves = forceRefresh
                ? moveRepository.findAll().stream()
                    .filter(m -> m.getDescription() != null && !m.getDescription().isBlank())
                    .toList()
                : moveRepository.findByDescriptionIsNotNullAndChineseDescriptionIsNull();

        log.info("========== 開始 AI 翻譯招式描述: {} 筆待處理 ==========", moves.size());

        if (moves.isEmpty()) {
            return new TranslationReport(0, 0, 0, 0, List.of());
        }

        String geminiUrl = GEMINI_API_BASE + geminiModel + ":generateContent?key=" + geminiApiKey;
        RestClient restClient = RestClient.builder()
                .baseUrl(geminiUrl)
                .defaultHeader("Content-Type", "application/json")
                .build();

        ObjectMapper mapper = new ObjectMapper();
        int translated = 0, skipped = 0, failed = 0;
        List<String> failedMoves = new ArrayList<>();

        List<List<Move>> batches = partitionList(moves, batchSize);

        for (int batchIdx = 0; batchIdx < batches.size(); batchIdx++) {
            List<Move> batch = batches.get(batchIdx);

            try {
                if (batchIdx > 0) {
                    Thread.sleep(geminiDelayMs);
                }

                Map<String, String> inputMap = new LinkedHashMap<>();
                for (Move m : batch) {
                    inputMap.put(m.getName(), m.getDescription());
                }

                String inputJson = mapper.writeValueAsString(inputMap);
                log.info("  [批次 {}/{}] 翻譯 {} 筆: {}",
                        batchIdx + 1, batches.size(), batch.size(),
                        batch.stream().map(Move::getName).toList());

                Map<String, Object> requestBody = buildGeminiRequest(inputJson);
                String requestJson = mapper.writeValueAsString(requestBody);

                String responseJson = callGeminiWithRetry(restClient, requestJson, batchIdx + 1);

                Map<String, String> translations = parseGeminiResponse(mapper, responseJson);

                for (Move m : batch) {
                    String zhDesc = translations.get(m.getName());
                    if (zhDesc != null && !zhDesc.isBlank()) {
                        m.setChineseDescription(zhDesc);
                        moveRepository.save(m);
                        translated++;
                        log.debug("    ✓ {} → {}", m.getName(), truncate(zhDesc, 40));
                    } else {
                        skipped++;
                        log.warn("    ⏭ {} 翻譯結果為空", m.getName());
                    }
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("  ⚠ 翻譯中斷");
                batch.forEach(m -> failedMoves.add(m.getName()));
                failed += batch.size();
                break;
            } catch (Exception e) {
                log.warn("  ✗ 批次 {} 翻譯失敗（重試耗盡）: {}", batchIdx + 1, e.getMessage());
                batch.forEach(m -> failedMoves.add(m.getName()));
                failed += batch.size();
            }
        }

        log.info("========== AI 翻譯完成: 翻譯 {}, 跳過 {}, 失敗 {}, 共 {} ==========",
                translated, skipped, failed, moves.size());
        return new TranslationReport(moves.size(), translated, skipped, failed, failedMoves);
    }

    /**
     * 呼叫 Gemini API，遇到 429 時自動解析 retryDelay 等待後重試。
     */
    private String callGeminiWithRetry(RestClient restClient, String requestJson, int batchNum)
            throws InterruptedException {
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                return restClient.post()
                        .body(requestJson)
                        .retrieve()
                        .body(String.class);
            } catch (Exception e) {
                String msg = e.getMessage();
                if (msg != null && msg.contains("429") && attempt < MAX_RETRIES) {
                    long waitMs = parseRetryDelay(msg);
                    log.warn("    ⏳ 批次 {} 遇到 429 rate limit，等待 {}s 後重試 ({}/{})",
                            batchNum, waitMs / 1000, attempt + 1, MAX_RETRIES);
                    Thread.sleep(waitMs);
                } else {
                    throw e;
                }
            }
        }
        throw new RuntimeException("不應到達此處");
    }

    /**
     * 從 Gemini 429 錯誤訊息中解析 retryDelay 秒數。
     * 格式範例: "retryDelay": "58s"
     */
    private static long parseRetryDelay(String errorMessage) {
        Matcher matcher = RETRY_DELAY_PATTERN.matcher(errorMessage);
        if (matcher.find()) {
            long seconds = Long.parseLong(matcher.group(1));
            return (seconds + 2) * 1000;
        }
        return DEFAULT_RETRY_DELAY_MS;
    }

    private Map<String, Object> buildGeminiRequest(String userContent) {
        Map<String, Object> request = new LinkedHashMap<>();

        request.put("systemInstruction", Map.of(
                "parts", List.of(Map.of("text", TRANSLATION_SYSTEM_PROMPT))
        ));

        request.put("contents", List.of(
                Map.of("parts", List.of(Map.of("text", userContent)))
        ));

        request.put("generationConfig", Map.of(
                "temperature", 0.3,
                "responseMimeType", "application/json"
        ));

        return request;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> parseGeminiResponse(ObjectMapper mapper, String responseJson) throws Exception {
        Map<String, Object> response = mapper.readValue(responseJson, new TypeReference<>() {});

        List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
        if (candidates == null || candidates.isEmpty()) {
            throw new RuntimeException("Gemini 回應中無 candidates");
        }

        Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
        List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
        if (parts == null || parts.isEmpty()) {
            throw new RuntimeException("Gemini 回應中無 parts");
        }

        String text = (String) parts.get(0).get("text");
        return mapper.readValue(text, new TypeReference<Map<String, String>>() {});
    }

    private static <T> List<List<T>> partitionList(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }

    // ─── 主頁表格解析 ─────────────────────────────────────────

    private List<RawMoveEntry> parseMovesTable(Document doc) {
        List<RawMoveEntry> entries = new ArrayList<>();

        Elements tables = doc.select("table");
        for (Element table : tables) {
            Elements headerCells = table.select("thead tr th, thead tr td, tr:first-child th, tr:first-child td");
            if (headerCells.isEmpty()) continue;

            boolean isMoveTable = headerCells.stream()
                    .anyMatch(cell -> cell.text().trim().equalsIgnoreCase("Move"));
            if (!isMoveTable) continue;

            Elements rows = table.select("tbody tr, tr");
            for (Element row : rows) {
                Elements cells = row.select("td");
                if (cells.size() < 5) continue;

                String moveName = cells.get(0).text().trim();
                if (moveName.isEmpty() || moveName.equalsIgnoreCase("Move")) continue;

                Element link = cells.get(0).selectFirst("a[href]");
                String game8Url = link != null ? link.absUrl("href") : null;

                String typeName = cells.get(1).text().trim();
                String category = cells.get(2).text().trim();
                String power = cells.get(3).text().trim();
                String pp = cells.get(4).text().trim();
                String description = cells.size() > 5 ? cells.get(5).text().trim() : null;

                entries.add(new RawMoveEntry(moveName, typeName, category, power, pp, description, game8Url));
            }
        }

        return entries;
    }

    // ─── 詳情頁解析 ──────────────────────────────────────────

    /**
     * 從招式詳情頁解析 Accuracy 值。
     * HTML 結構: 表格中有 "Accuracy" 文字的儲存格，旁邊的儲存格就是值。
     */
    private Integer parseAccuracyFromDetail(Document doc) {
        Elements cells = doc.select("td, th");
        for (int i = 0; i < cells.size(); i++) {
            if (cells.get(i).text().trim().equalsIgnoreCase("Accuracy") && i + 1 < cells.size()) {
                return parseIntOrNull(cells.get(i + 1).text().trim());
            }
        }
        return null;
    }

    /**
     * 從招式詳情頁解析「Pokemon that Learn [Move]」區塊中的寶可夢名稱列表。
     * 尋找包含 "Pokemon that Learn" 的標題，然後取其後的表格或連結。
     */
    private List<String> parsePokemonLearnersFromDetail(Document doc) {
        List<String> names = new ArrayList<>();

        Elements headings = doc.select("h2, h3");
        Element learnerHeading = null;
        for (Element h : headings) {
            if (h.text().toLowerCase().contains("pokemon that learn")) {
                learnerHeading = h;
                break;
            }
        }

        if (learnerHeading == null) return names;

        Element sibling = learnerHeading.nextElementSibling();
        while (sibling != null) {
            String tag = sibling.tagName();
            if (tag.matches("h[1-6]")) break;

            Elements links = sibling.select("a[href*=/archives/]");
            for (Element link : links) {
                String pokeName = link.text().trim();
                if (!pokeName.isEmpty() && !pokeName.equalsIgnoreCase("List of All Moves")) {
                    names.add(pokeName);
                }
            }
            sibling = sibling.nextElementSibling();
        }

        return names;
    }

    // ─── 寶可夢名稱匹配 + 關聯建立 ──────────────────────────

    @Transactional
    protected int linkPokemonToMove(Move move, List<String> game8PokemonNames,
                                    Set<String> unmatchedCollector) {
        int linked = 0;
        for (String g8Name : game8PokemonNames) {
            Optional<Pokemon> pokemonOpt = matchPokemon(g8Name);

            if (pokemonOpt.isEmpty()) {
                unmatchedCollector.add(g8Name);
                continue;
            }

            Pokemon pokemon = pokemonOpt.get();
            Optional<PokemonMove> existing = pokemonMoveRepository.findByPokemonAndMove(pokemon, move);
            if (existing.isPresent()) continue;

            pokemonMoveRepository.save(new PokemonMove(pokemon, move, MoveSource.CHAMPIONS_GAME8, true));
            linked++;
            log.debug("    ✓ {} → {}", pokemon.getApiName(), move.getName());
        }
        return linked;
    }

    /**
     * 嘗試將 Game8 顯示的寶可夢名稱匹配到 DB 中的 Pokemon。
     * 策略：
     * 1. displayName 完全匹配
     * 2. 轉為 PokeAPI slug 後匹配 apiName
     * 3. 常見格式轉換（Mega X/Y、地區形態等）
     */
    private Optional<Pokemon> matchPokemon(String game8Name) {
        Optional<Pokemon> byDisplay = pokemonRepository.findByDisplayName(game8Name);
        if (byDisplay.isPresent()) return byDisplay;

        String slug = PokeApiSlugResolver.resolve(game8Name);
        if (slug != null && !slug.isBlank()) {
            Optional<Pokemon> byApi = pokemonRepository.findByApiName(slug);
            if (byApi.isPresent()) return byApi;
        }

        String manualSlug = manualNameToSlug(game8Name);
        if (manualSlug != null) {
            return pokemonRepository.findByApiName(manualSlug);
        }

        return Optional.empty();
    }

    /**
     * Game8 特有的命名格式手動轉換。
     */
    private static String manualNameToSlug(String name) {
        if (name == null) return null;

        if (name.startsWith("Mega ") && name.endsWith(" X")) {
            return toSlug(name.replace("Mega ", "").replace(" X", "")) + "-mega-x";
        }
        if (name.startsWith("Mega ") && name.endsWith(" Y")) {
            return toSlug(name.replace("Mega ", "").replace(" Y", "")) + "-mega-y";
        }
        if (name.startsWith("Mega ")) {
            return toSlug(name.replace("Mega ", "")) + "-mega";
        }

        if (name.contains("(Alolan Form)") || name.startsWith("Alolan ")) {
            String base = name.replace("(Alolan Form)", "").replace("Alolan ", "").trim();
            return toSlug(base) + "-alola";
        }
        if (name.contains("(Galarian Form)") || name.startsWith("Galarian ")) {
            String base = name.replace("(Galarian Form)", "").replace("Galarian ", "").trim();
            return toSlug(base) + "-galar";
        }
        if (name.contains("(Hisuian Form)") || name.startsWith("Hisuian ")) {
            String base = name.replace("(Hisuian Form)", "").replace("Hisuian ", "").trim();
            return toSlug(base) + "-hisui";
        }
        if (name.contains("(Paldean Form)") || name.startsWith("Paldean ")) {
            String base = name.replace("(Paldean Form)", "").replace("Paldean ", "").trim();
            return toSlug(base) + "-paldea";
        }

        return null;
    }

    // ─── 更新工具 ────────────────────────────────────────────

    private boolean applyMoveUpdates(Move move, String displayName, PokemonType type,
                                     MoveCategory category, Integer power, int pp,
                                     String desc, String game8Url) {
        boolean changed = false;
        if (!move.getDisplayName().equals(displayName)) {
            move.setDisplayName(displayName);
            changed = true;
        }
        if (move.getType() == null || !move.getType().getId().equals(type.getId())) {
            move.setType(type);
            changed = true;
        }
        if (move.getCategory() != category) {
            move.setCategory(category);
            changed = true;
        }
        if (!Objects.equals(move.getPower(), power)) {
            move.setPower(power);
            changed = true;
        }
        if (move.getPp() != pp) {
            move.setPp(pp);
            changed = true;
        }
        if (desc != null && !desc.equals(move.getDescription())) {
            move.setDescription(desc);
            changed = true;
        }
        if (game8Url != null && !game8Url.equals(move.getGame8Url())) {
            move.setGame8Url(game8Url);
            changed = true;
        }
        return changed;
    }

    // ─── 通用工具 ────────────────────────────────────────────

    private Document fetchDocument(String url) {
        try {
            return Jsoup.connect(url)
                    .timeout(timeoutMs)
                    .userAgent("Mozilla/5.0 (PokéChampions-Backend)")
                    .get();
        } catch (IOException e) {
            throw new RuntimeException("無法連線: " + url, e);
        }
    }

    static String toSlug(String displayName) {
        if (displayName == null) return null;
        return displayName
                .toLowerCase()
                .replaceAll("[''']", "")
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }

    private static MoveCategory parseCategory(String raw) {
        if (raw == null) return null;
        return switch (raw.trim().toUpperCase()) {
            case "PHYSICAL" -> MoveCategory.PHYSICAL;
            case "SPECIAL" -> MoveCategory.SPECIAL;
            case "STATUS" -> MoveCategory.STATUS;
            default -> null;
        };
    }

    private static Integer parseIntOrNull(String raw) {
        if (raw == null || raw.isBlank() || raw.equals("-") || raw.equals("—")) return null;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int parseIntOrZero(String raw) {
        Integer val = parseIntOrNull(raw);
        return val != null ? val : 0;
    }

    private static String truncate(String s, int maxLen) {
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }

    // ─── DTO ────────────────────────────────────────────────────

    public record MoveSyncReport(int totalParsed, int created, int updated, int failed) {}

    public record ChineseNameSyncReport(
            int total,
            int updated,
            int skipped,
            int notFound,
            List<String> failedMoves
    ) {}

    public record TranslationReport(
            int total,
            int translated,
            int skipped,
            int failed,
            List<String> failedMoves
    ) {}

    public record PokemonMoveSyncReport(
            int totalMoves,
            int processed,
            int linked,
            int skipped,
            int failed,
            List<String> unmatchedPokemon
    ) {}

    private record RawMoveEntry(
            String displayName,
            String typeName,
            String category,
            String power,
            String pp,
            String description,
            String game8Url
    ) {}
}
