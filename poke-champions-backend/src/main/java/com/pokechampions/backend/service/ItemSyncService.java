package com.pokechampions.backend.service;

import com.pokechampions.backend.entity.HeldItem;
import com.pokechampions.backend.entity.HeldItem.ItemCategory;
import com.pokechampions.backend.repository.HeldItemRepository;
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

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ItemSyncService {

    private static final Logger log = LoggerFactory.getLogger(ItemSyncService.class);

    private static final String DEFAULT_GAME8_URL =
            "https://game8.co/games/Pokemon-Champions/archives/588871";
    private static final String GEMINI_API_BASE =
            "https://generativelanguage.googleapis.com/v1beta/models/";

    private static final String NAME_TRANSLATION_PROMPT = """
            你是寶可夢遊戲翻譯專家。請將以下 JSON 中的寶可夢持有物品英文名稱翻譯成繁體中文。
            規則：
            1. 使用台灣官方寶可夢譯名，例如 Focus Sash = 氣勢披帶、Leftovers = 吃剩的東西、Choice Band = 講究頭帶
            2. 如果是 Berry 系列，使用官方中文樹果名（如 Sitrus Berry = 文柚果）
            3. 如果是 Mega Stone，使用「XX石」的格式（如 Charizardite X = 噴火龍進化石X）
            4. 回傳格式必須是相同 key 的 JSON 物件，value 為翻譯後的繁體中文
            5. 只回傳 JSON，不要有其他文字""";

    private static final String EFFECT_TRANSLATION_PROMPT = """
            你是寶可夢遊戲翻譯專家。請將以下 JSON 中的寶可夢持有物品效果描述從英文翻譯成繁體中文。
            規則：
            1. 使用台灣官方寶可夢譯名和遊戲術語
            2. 保持簡潔，符合遊戲內描述風格
            3. 回傳格式必須是相同 key 的 JSON 物件，value 為翻譯後的繁體中文
            4. 只回傳 JSON，不要有其他文字""";

    private static final int MAX_RETRIES = 3;
    private static final long DEFAULT_RETRY_DELAY_MS = 60_000;
    private static final Pattern RETRY_DELAY_PATTERN =
            Pattern.compile("\"retryDelay\"\\s*:\\s*\"(\\d+)s?\"");

    @Value("${scraper.timeout-ms:15000}")
    private int timeoutMs;

    @Value("${gemini.api-key:}")
    private String geminiApiKey;

    @Value("${gemini.model:gemini-2.0-flash}")
    private String geminiModel;

    @Value("${gemini.batch-size:20}")
    private int batchSize;

    @Value("${gemini.delay-ms:1000}")
    private int geminiDelayMs;

    private final HeldItemRepository heldItemRepository;

    public ItemSyncService(HeldItemRepository heldItemRepository) {
        this.heldItemRepository = heldItemRepository;
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
    //  翻譯物品名稱（Gemini）
    // ═══════════════════════════════════════════════════════════

    public TranslationReport translateNames(boolean forceRefresh) {
        return translateWithGemini(
                forceRefresh
                        ? heldItemRepository.findAll()
                        : heldItemRepository.findByChineseNameIsNull(),
                NAME_TRANSLATION_PROMPT,
                HeldItem::getDisplayName,
                HeldItem::getChineseName,
                HeldItem::setChineseName,
                "物品名稱"
        );
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
