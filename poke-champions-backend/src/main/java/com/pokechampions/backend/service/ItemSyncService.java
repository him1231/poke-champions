package com.pokechampions.backend.service;

import com.pokechampions.backend.entity.HeldItem;
import com.pokechampions.backend.entity.HeldItem.ItemCategory;
import com.pokechampions.backend.entity.Pokemon;
import com.pokechampions.backend.repository.HeldItemRepository;
import com.pokechampions.backend.repository.PokemonRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ItemSyncService {

    private static final Logger log = LoggerFactory.getLogger(ItemSyncService.class);

    private static final String DEFAULT_GAME8_URL =
            "https://game8.co/games/Pokemon-Champions/archives/588871";
    private static final String POKEAPI_ITEM_URL = "https://pokeapi.co/api/v2/item/";
    private static final String GEMINI_API_BASE =
            "https://generativelanguage.googleapis.com/v1beta/models/";

    private static final String EFFECT_TRANSLATION_PROMPT = """
            你是寶可夢遊戲翻譯專家。請將以下 JSON 中的寶可夢持有物品效果描述從英文翻譯成繁體中文。
            規則：
            1. 使用台灣官方寶可夢譯名和遊戲術語
            2. 保持簡潔，符合遊戲內描述風格
            3. 回傳格式必須是相同 key 的 JSON 物件，value 為翻譯後的繁體中文
            4. 只回傳 JSON，不要有其他文字""";

    /**
     * Game8 slug 與 PokeAPI／慣用 mega api 不一致時的手動對照（其餘由 {@link #resolveMegaApiForStoneSlug} 以資料庫 Mega 形態反推）。
     */
    private static final Map<String, String> CHAMPIONS_MEGA_STONE_SLUG_TO_MEGA_API = Map.ofEntries(
            Map.entry("clefablite", "clefable-mega"),
            Map.entry("drampanite", "drampa-mega"),
            Map.entry("excadrite", "excadrill-mega"),
            Map.entry("chandelurite", "chandelure-mega"),
            Map.entry("meganiumite", "meganium-mega"),
            Map.entry("feraligite", "feraligatr-mega"),
            Map.entry("emboarite", "emboar-mega"),
            Map.entry("victreebelite", "victreebel-mega"),
            Map.entry("hawluchanite", "hawlucha-mega"),
            Map.entry("dragoninite", "dragonite-mega"),
            Map.entry("froslassite", "froslass-mega"),
            Map.entry("starminite", "starmie-mega"),
            Map.entry("skarmorite", "skarmory-mega"),
            Map.entry("chimechite", "chimecho-mega"),
            Map.entry("crabominite", "crabominable-mega"),
            Map.entry("glimmoranite", "glimmora-mega"),
            Map.entry("golurkite", "golurk-mega"),
            Map.entry("meowsticite", "meowstic-mega"),
            Map.entry("chesnaughtite", "chesnaught-mega"),
            Map.entry("delphoxite", "delphox-mega"),
            Map.entry("scovillainite", "scovillain-mega"),
            Map.entry("floettite", "floette-mega"),
            Map.entry("greninjite", "greninja-mega")
    );

    /** 僅在「資料庫無法反推 Mega 形態」時使用 Gemini。 */
    private static final String MEGA_STONE_NAME_FALLBACK_PROMPT = """
            你是寶可夢繁體中文（台灣）官方用語專家。下列 JSON 的 key 為資料庫物品 slug，value 為遊戲內 Mega 進化石的英文顯示名稱。
            請依英文名稱判斷對應寶可夢，輸出與主系列道具命名一致的繁中名稱。
            規則：
            1. 格式為「官方寶可夢中文名＋進化石」，例如 Charizardite X → 噴火龍進化石X（X/Y 接在進化石後）。
            2. value 僅為簡潔道具名，不要說明文字。
            3. 回傳與輸入相同 key 的 JSON 物件。
            4. 只回傳 JSON。""";

    private static final int MAX_RETRIES = 3;
    private static final long DEFAULT_RETRY_DELAY_MS = 60_000;
    private static final Pattern RETRY_DELAY_PATTERN =
            Pattern.compile("\"retryDelay\"\\s*:\\s*\"(\\d+)s?\"");

    @Value("${scraper.timeout-ms:15000}")
    private int timeoutMs;

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

    private final HeldItemRepository heldItemRepository;
    private final PokemonRepository pokemonRepository;

    public ItemSyncService(HeldItemRepository heldItemRepository, PokemonRepository pokemonRepository) {
        this.heldItemRepository = heldItemRepository;
        this.pokemonRepository = pokemonRepository;
    }

    /**
     * 從 Game8 Items List 頁面同步全部持有物品。
     */
    @Transactional
    public Map<String, Object> syncFromGame8() {
        return syncFromGame8(DEFAULT_GAME8_URL);
    }

    @Transactional
    public Map<String, Object> syncFromGame8(String url) {
        log.info("開始從 Game8 同步持有物品: {}", url);

        Document doc = fetchDocument(url);
        List<RawItemEntry> entries = parseItemsTable(doc);
        log.info("解析到 {} 個物品", entries.size());

        int created = 0, updated = 0, skipped = 0;

        for (RawItemEntry entry : entries) {
            String slug = toSlug(entry.displayName);
            if (slug == null || slug.isBlank()) {
                log.warn("無法產生 slug: {}", entry.displayName);
                skipped++;
                continue;
            }

            ItemCategory category = parseCategory(entry.typeName);
            if (category == null) {
                log.warn("無法辨識分類 '{}' for item '{}'", entry.typeName, entry.displayName);
                category = ItemCategory.OTHER;
            }

            Optional<HeldItem> existing = heldItemRepository.findByName(slug);

            if (existing.isPresent()) {
                HeldItem item = existing.get();
                boolean changed = applyUpdates(item, entry.displayName, category,
                        entry.howToGet, entry.effect, entry.game8Url);
                if (changed) {
                    heldItemRepository.save(item);
                    updated++;
                    log.debug("  更新: {}", slug);
                } else {
                    skipped++;
                }
            } else {
                HeldItem item = new HeldItem(slug, entry.displayName, category,
                        entry.howToGet, entry.effect, entry.game8Url);
                heldItemRepository.save(item);
                created++;
                log.debug("  新增: {}", slug);
            }
        }

        log.info("同步完成: 新增={}, 更新={}, 跳過={}", created, updated, skipped);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("parsed", entries.size());
        result.put("created", created);
        result.put("updated", updated);
        result.put("skipped", skipped);
        return result;
    }

    // ═══════════════════════════════════════════════════════════
    //  道具繁中名稱：從 PokeAPI 官方本地化（與主系列譯名一致）
    // ═══════════════════════════════════════════════════════════

    /**
     * 透過 PokeAPI /item/{slug} 取得繁體中文道具名稱，寫入 HeldItem.chineseName。
     * slug 須與 PokeAPI 物品識別碼一致（本專案由 Game8 英文名經 {@link ItemSyncService#toSlug(String)} 產生）。
     *
     * @param forceRefresh true 時重抓所有道具；false 時只處理 chineseName 為 null 者
     */
    @Transactional
    public ItemChineseNameSyncReport syncChineseNamesFromPokeApi(boolean forceRefresh) {
        List<HeldItem> items = forceRefresh
                ? heldItemRepository.findAll()
                : heldItemRepository.findByChineseNameIsNull();
        log.info("========== 從 PokeAPI 同步道具繁中名稱: {} 筆待處理 ==========", items.size());

        RestClient restClient = RestClient.builder().baseUrl(POKEAPI_ITEM_URL).build();
        List<Pokemon> megaForms = pokemonRepository.findByIsMegaTrue();

        int updated = 0, skipped = 0, notFound = 0;
        List<String> failedItems = new ArrayList<>();
        List<HeldItem> megaStonesNeedGemini = new ArrayList<>();

        for (int i = 0; i < items.size(); i++) {
            HeldItem item = items.get(i);
            try {
                if (i > 0) {
                    Thread.sleep(pokeApiDelayMs);
                }
                log.debug("  [{}/{}] PokeAPI item: {}", i + 1, items.size(), item.getName());

                @SuppressWarnings("unchecked")
                Map<String, Object> body = restClient.get()
                        .uri(item.getName())
                        .retrieve()
                        .body(Map.class);

                if (body == null) {
                    log.warn("    ⚠ {} 回傳 null", item.getName());
                    failedItems.add(item.getName());
                    notFound++;
                    continue;
                }

                boolean changed = false;

                String zhHant = extractLocalizedName(body, "zh-hant");
                String chosen = zhHant;
                if (chosen == null || chosen.isBlank()) {
                    chosen = extractLocalizedName(body, "zh-hans");
                }
                if (chosen != null && !chosen.isBlank() && !chosen.equals(item.getChineseName())) {
                    item.setChineseName(chosen);
                    changed = true;
                }

                String ja = extractLocalizedName(body, "ja");
                if (ja != null && !ja.isBlank() && !ja.equals(item.getJapaneseName())) {
                    item.setJapaneseName(ja);
                    changed = true;
                }

                if (!changed) {
                    skipped++;
                    continue;
                }
                heldItemRepository.save(item);
                updated++;
                log.info("    ✓ {} → zh:{} / ja:{}", item.getName(), item.getChineseName(), item.getJapaneseName());

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("  ⚠ 同步中斷");
                break;
            } catch (Exception e) {
                if (isHttp404(e) && item.getCategory() == ItemCategory.MEGA_STONE) {
                    Optional<String> fromMega = inferMegaStoneChineseFromMegaPokemon(item.getName(), megaForms);
                    if (fromMega.isPresent()) {
                        String zh = fromMega.get();
                        if (!zh.equals(item.getChineseName())) {
                            item.setChineseName(zh);
                            heldItemRepository.save(item);
                            updated++;
                            log.info("    ✓ {} → {}（由 Mega 形態反推）", item.getName(), zh);
                        } else {
                            skipped++;
                        }
                    } else {
                        log.info("    Mega 石 {} PokeAPI 無條目且無法對應 Mega 形態，將以 Gemini 備援", item.getName());
                        megaStonesNeedGemini.add(item);
                    }
                } else {
                    String msg = e.getMessage();
                    if (isHttp404(e)) {
                        log.warn("    ⚠ {} 在 PokeAPI 找不到 (404)", item.getName());
                    } else {
                        log.warn("    ✗ {} 查詢失敗: {}", item.getName(), msg);
                    }
                    failedItems.add(item.getName());
                    notFound++;
                }
            }
        }

        if (!megaStonesNeedGemini.isEmpty()) {
            if (geminiApiKey != null && !geminiApiKey.isBlank()) {
                log.info("========== Mega 石 {} 筆改以 Gemini 備援翻譯中文名 ==========", megaStonesNeedGemini.size());
                TranslationReport tr = translateWithGemini(
                        megaStonesNeedGemini,
                        MEGA_STONE_NAME_FALLBACK_PROMPT,
                        HeldItem::getDisplayName,
                        HeldItem::getChineseName,
                        HeldItem::setChineseName,
                        "Mega石中文(備援)"
                );
                updated += tr.translated();
                skipped += tr.skipped();
                notFound += tr.failed();
                failedItems.addAll(tr.failedItems());
                for (HeldItem m : megaStonesNeedGemini) {
                    if ((m.getChineseName() == null || m.getChineseName().isBlank())
                            && !failedItems.contains(m.getName())) {
                        failedItems.add(m.getName());
                        notFound++;
                    }
                }
            } else {
                log.warn("未設定 gemini.api-key，{} 顆 Mega 石無法備援翻譯，仍缺中文名", megaStonesNeedGemini.size());
                for (HeldItem m : megaStonesNeedGemini) {
                    failedItems.add(m.getName());
                    notFound++;
                }
            }
        }

        log.info("========== 道具繁中名稱同步完成: 更新 {}, 跳過 {}, 找不到/失敗 {}, 共 {} ==========",
                updated, skipped, notFound, items.size());
        return new ItemChineseNameSyncReport(items.size(), updated, skipped, notFound, failedItems);
    }

    @SuppressWarnings("unchecked")
    private static String extractLocalizedName(Map<String, Object> body, String langCode) {
        List<Map<String, Object>> names = (List<Map<String, Object>>) body.get("names");
        if (names == null) {
            return null;
        }
        for (Map<String, Object> entry : names) {
            Map<String, Object> lang = (Map<String, Object>) entry.get("language");
            if (lang != null && langCode.equalsIgnoreCase(String.valueOf(lang.get("name")))) {
                return (String) entry.get("name");
            }
        }
        return null;
    }

    private static boolean isHttp404(Throwable e) {
        Throwable cur = e;
        while (cur != null) {
            if (cur instanceof RestClientResponseException rce) {
                return rce.getStatusCode().value() == 404;
            }
            cur = cur.getCause();
        }
        String msg = e.getMessage();
        return msg != null && (msg.contains("404") || msg.contains("Not Found"));
    }

    /**
     * 依進化石 slug 對應資料庫中 Mega 形態（apiName），將 Mega 繁中名轉成「○○進化石」/「○○進化石X」。
     */
    private Optional<String> inferMegaStoneChineseFromMegaPokemon(String itemSlug, List<Pokemon> megaForms) {
        return resolveMegaApiForStoneSlug(itemSlug, megaForms)
                .flatMap(api -> megaForms.stream()
                        .filter(p -> api.equals(p.getApiName()))
                        .findFirst()
                        .map(p -> formatMegaStoneChineseName(p, api)));
    }

    private static Optional<String> resolveMegaApiForStoneSlug(String slug, List<Pokemon> megaForms) {
        if (slug == null || slug.length() < 4 || !slug.endsWith("ite")) {
            return Optional.empty();
        }
        String mapped = CHAMPIONS_MEGA_STONE_SLUG_TO_MEGA_API.get(slug);
        if (mapped != null) {
            boolean ok = megaForms.stream().anyMatch(p -> mapped.equals(p.getApiName()));
            return ok ? Optional.of(mapped) : Optional.empty();
        }
        String prefix = slug.substring(0, slug.length() - 3);
        List<ScoredApi> matches = new ArrayList<>();
        for (Pokemon p : megaForms) {
            String api = p.getApiName();
            if (api == null || !api.contains("-mega")) {
                continue;
            }
            int megaIdx = api.indexOf("-mega");
            String base = api.substring(0, megaIdx);
            int score = scoreStonePrefixToBase(prefix, base);
            if (score >= 0) {
                matches.add(new ScoredApi(api, score));
            }
        }
        return matches.stream()
                .max(Comparator.comparingInt(ScoredApi::score).thenComparing(s -> s.api.length()))
                .map(ScoredApi::api);
    }

    /**
     * 將 Game8／慣用進化石 slug 前綴與 Mega 形態的種族名（api 中 -mega 前綴）對齊。
     * 含「Lucarionite」這類尾端多一字母的常見拼法（與種族名僅最末字不同）。
     */
    private static int scoreStonePrefixToBase(String prefix, String base) {
        if (prefix.equals(base)) {
            return 10_000 + base.length();
        }
        if (base.startsWith(prefix)) {
            return 5_000 + prefix.length() * 100 + (base.length() - prefix.length());
        }
        if (prefix.startsWith(base)) {
            return 2_000 + base.length();
        }
        if (prefix.length() == base.length()
                && prefix.length() >= 4
                && prefix.regionMatches(0, base, 0, prefix.length() - 1)) {
            return 1_500 + base.length();
        }
        return -1;
    }

    private record ScoredApi(String api, int score) {}

    private static String formatMegaStoneChineseName(Pokemon mega, String megaApiName) {
        String zh = mega.getChineseName();
        if (zh == null || zh.isBlank()) {
            return null;
        }
        zh = zh.strip();
        if (zh.startsWith("超級")) {
            zh = zh.substring(2).strip();
        }
        String xy = "";
        if (megaApiName.endsWith("-mega-x")) {
            zh = stripTrailingCharVariant(zh, 'Ｘ', 'X');
            xy = "X";
        } else if (megaApiName.endsWith("-mega-y")) {
            zh = stripTrailingCharVariant(zh, 'Ｙ', 'Y');
            xy = "Y";
        }
        return zh + "進化石" + xy;
    }

    private static String stripTrailingCharVariant(String s, char wide, char ascii) {
        if (s.endsWith(String.valueOf(wide))) {
            return s.substring(0, s.length() - 1).strip();
        }
        if (s.length() > 1 && s.charAt(s.length() - 1) == ascii) {
            return s.substring(0, s.length() - 1).strip();
        }
        return s;
    }

    // ═══════════════════════════════════════════════════════════
    //  翻譯物品效果描述（Gemini）
    // ═══════════════════════════════════════════════════════════

    public TranslationReport translateEffects(boolean forceRefresh) {
        List<HeldItem> items = forceRefresh
                ? heldItemRepository.findAll().stream()
                    .filter(i -> i.getEffect() != null && !i.getEffect().isBlank())
                    .toList()
                : heldItemRepository.findByEffectIsNotNullAndChineseEffectIsNull();
        return translateWithGemini(
                items,
                EFFECT_TRANSLATION_PROMPT,
                HeldItem::getEffect,
                HeldItem::getChineseEffect,
                HeldItem::setChineseEffect,
                "物品效果"
        );
    }

    // ─── Gemini 通用翻譯 ─────────────────────────────────

    @FunctionalInterface
    private interface ItemGetter { String apply(HeldItem item); }

    @FunctionalInterface
    private interface ItemSetter { void apply(HeldItem item, String value); }

    private TranslationReport translateWithGemini(
            List<HeldItem> items, String systemPrompt,
            ItemGetter sourceGetter, ItemGetter existingGetter, ItemSetter targetSetter,
            String label) {

        if (geminiApiKey == null || geminiApiKey.isBlank()) {
            throw new IllegalStateException("未設定 gemini.api-key，無法翻譯");
        }
        log.info("========== 開始 AI 翻譯{}: {} 筆待處理 ==========", label, items.size());
        if (items.isEmpty()) return new TranslationReport(0, 0, 0, 0, List.of());

        String geminiUrl = GEMINI_API_BASE + geminiModel + ":generateContent?key=" + geminiApiKey;
        RestClient restClient = RestClient.builder()
                .baseUrl(geminiUrl)
                .defaultHeader("Content-Type", "application/json")
                .build();

        ObjectMapper mapper = new ObjectMapper();
        int translated = 0, skipped = 0, failed = 0;
        List<String> failedItems = new ArrayList<>();

        List<List<HeldItem>> batches = partitionList(items, batchSize);

        for (int bi = 0; bi < batches.size(); bi++) {
            List<HeldItem> batch = batches.get(bi);
            try {
                if (bi > 0) Thread.sleep(geminiDelayMs);

                Map<String, String> inputMap = new LinkedHashMap<>();
                for (HeldItem item : batch) {
                    inputMap.put(item.getName(), sourceGetter.apply(item));
                }
                String inputJson = mapper.writeValueAsString(inputMap);
                log.info("  [批次 {}/{}] 翻譯{} {} 筆", bi + 1, batches.size(), label, batch.size());

                Map<String, Object> requestBody = buildGeminiRequest(systemPrompt, inputJson);
                String requestJson = mapper.writeValueAsString(requestBody);
                String responseJson = callGeminiWithRetry(restClient, requestJson, bi + 1);
                Map<String, String> translations = parseGeminiResponse(mapper, responseJson);

                for (HeldItem item : batch) {
                    String zhText = translations.get(item.getName());
                    if (zhText != null && !zhText.isBlank()) {
                        targetSetter.apply(item, zhText);
                        heldItemRepository.save(item);
                        translated++;
                        log.debug("    ✓ {} → {}", item.getName(), zhText);
                    } else {
                        skipped++;
                        log.warn("    ⏭ {} 翻譯結果為空", item.getName());
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("  ⚠ 翻譯中斷");
                batch.forEach(i -> failedItems.add(i.getName()));
                failed += batch.size();
                break;
            } catch (Exception e) {
                log.warn("  ✗ 批次 {} 翻譯失敗: {}", bi + 1, e.getMessage());
                batch.forEach(i -> failedItems.add(i.getName()));
                failed += batch.size();
            }
        }

        log.info("========== AI 翻譯{}完成: 翻譯 {}, 跳過 {}, 失敗 {}, 共 {} ==========",
                label, translated, skipped, failed, items.size());
        return new TranslationReport(items.size(), translated, skipped, failed, failedItems);
    }

    // ─── Gemini 工具 ─────────────────────────────────────

    private Map<String, Object> buildGeminiRequest(String systemPrompt, String userContent) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("systemInstruction", Map.of(
                "parts", List.of(Map.of("text", systemPrompt))
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

    private static long parseRetryDelay(String errorMessage) {
        Matcher m = RETRY_DELAY_PATTERN.matcher(errorMessage);
        if (m.find()) {
            long seconds = Long.parseLong(m.group(1));
            return (seconds + 2) * 1000;
        }
        return DEFAULT_RETRY_DELAY_MS;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> parseGeminiResponse(ObjectMapper mapper, String responseJson)
            throws Exception {
        Map<String, Object> response = mapper.readValue(responseJson, new TypeReference<>() {});
        List<Map<String, Object>> candidates =
                (List<Map<String, Object>>) response.get("candidates");
        if (candidates == null || candidates.isEmpty()) {
            throw new RuntimeException("Gemini 回應中無 candidates");
        }
        Map<String, Object> content =
                (Map<String, Object>) candidates.get(0).get("content");
        List<Map<String, Object>> parts =
                (List<Map<String, Object>>) content.get("parts");
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

    // ─── 翻譯報告 DTO ────────────────────────────────────

    public record TranslationReport(
            int total, int translated, int skipped, int failed,
            List<String> failedItems
    ) {}

    /** 與 {@link com.pokechampions.backend.service.MoveSyncService.LocalizedNameSyncReport} 欄位語意對齊 */
    public record ItemChineseNameSyncReport(
            int total,
            int updated,
            int skipped,
            int notFound,
            List<String> failedItems
    ) {}

    // ─── 表格解析 ─────────────────────────────────────────

    private List<RawItemEntry> parseItemsTable(Document doc) {
        List<RawItemEntry> entries = new ArrayList<>();

        Elements tables = doc.select("table");
        for (Element table : tables) {
            Elements headerCells = table.select(
                    "thead tr th, thead tr td, tr:first-child th, tr:first-child td");
            if (headerCells.isEmpty()) continue;

            boolean isItemTable = headerCells.stream()
                    .anyMatch(cell -> cell.text().trim().equalsIgnoreCase("Item"));
            if (!isItemTable) continue;

            Elements rows = table.select("tbody tr, tr");
            for (Element row : rows) {
                Elements cells = row.select("td");
                if (cells.size() < 3) continue;

                String itemName = cells.get(0).text().trim();
                if (itemName.isEmpty() || itemName.equalsIgnoreCase("Item")) continue;

                Element link = cells.get(0).selectFirst("a[href]");
                String game8Url = link != null ? link.absUrl("href") : null;

                String typeName = cells.get(1).text().trim();
                String rawThirdCol = cells.get(2).text().trim();

                String[] parts = splitHowToGetAndEffect(rawThirdCol);

                entries.add(new RawItemEntry(itemName, typeName, parts[0], parts[1], game8Url));
            }
        }

        return entries;
    }

    /**
     * Game8 第三欄格式範例：
     *   "Shop: 700 VP Boosts the power of the holder's Fire-type moves by 20%."
     *   "Available from the start Restores 1/16 of the holder's max HP..."
     *   "Battle Tutorial: Mega Evolution A held item that allows..."
     *
     * 拆分策略：找第一個句子邊界（大寫字母開頭 + 前面是空格 + 前前面不是冒號/數字）
     * 如果包含已知關鍵字就用那個切。
     */
    private static final Pattern HOW_TO_GET_PATTERN = Pattern.compile(
            "^((?:Shop:\\s*\\d+\\s*VP|Available from the start|Battle Tutorial:\\s*[^.]*?" +
            "|Battle Pass:\\s*[^.]*?|Transfer Gifts:\\s*[^.]*?)(?:\\s*))\\s*",
            Pattern.CASE_INSENSITIVE
    );

    private String[] splitHowToGetAndEffect(String raw) {
        if (raw == null || raw.isBlank()) return new String[]{ null, null };

        Matcher m = HOW_TO_GET_PATTERN.matcher(raw);
        if (m.find()) {
            String howToGet = m.group(1).trim();
            String effect = raw.substring(m.end()).trim();
            if (effect.isEmpty()) effect = null;
            return new String[]{ howToGet, effect };
        }

        return new String[]{ null, raw };
    }

    // ─── 分類對應 ─────────────────────────────────────────

    private static ItemCategory parseCategory(String raw) {
        if (raw == null) return null;
        String lower = raw.toLowerCase().trim();
        return switch (lower) {
            case "defense" -> ItemCategory.DEFENSE;
            case "recovery" -> ItemCategory.RECOVERY;
            case "power boost" -> ItemCategory.POWER_BOOST;
            case "stat boost" -> ItemCategory.STAT_BOOST;
            case "mega stone" -> ItemCategory.MEGA_STONE;
            case "other" -> ItemCategory.OTHER;
            default -> null;
        };
    }

    // ─── 更新工具 ─────────────────────────────────────────

    private boolean applyUpdates(HeldItem item, String displayName, ItemCategory category,
                                 String howToGet, String effect, String game8Url) {
        boolean changed = false;
        if (!item.getDisplayName().equals(displayName)) {
            item.setDisplayName(displayName);
            changed = true;
        }
        if (item.getCategory() != category) {
            item.setCategory(category);
            changed = true;
        }
        if (howToGet != null && !howToGet.equals(item.getHowToGet())) {
            item.setHowToGet(howToGet);
            changed = true;
        }
        if (effect != null && !effect.equals(item.getEffect())) {
            item.setEffect(effect);
            changed = true;
        }
        if (game8Url != null && !game8Url.equals(item.getGame8Url())) {
            item.setGame8Url(game8Url);
            changed = true;
        }
        return changed;
    }

    // ─── 通用工具 ─────────────────────────────────────────

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

    // ─── 原始資料載體 ────────────────────────────────────

    private record RawItemEntry(
            String displayName,
            String typeName,
            String howToGet,
            String effect,
            String game8Url
    ) {}
}
