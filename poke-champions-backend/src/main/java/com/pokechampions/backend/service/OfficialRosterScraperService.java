package com.pokechampions.backend.service;

import com.pokechampions.backend.entity.Pokemon;
import com.pokechampions.backend.entity.Season;
import com.pokechampions.backend.entity.SeasonPokemon;
import com.pokechampions.backend.repository.PokemonRepository;
import com.pokechampions.backend.repository.SeasonPokemonRepository;
import com.pokechampions.backend.repository.SeasonRepository;
import com.pokechampions.backend.util.PokeApiSlugResolver;
import com.pokechampions.backend.util.PokemonNameMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class OfficialRosterScraperService {

    private static final Logger log = LoggerFactory.getLogger(OfficialRosterScraperService.class);

    private static final String NEWS_BASE_URL = "https://news.pokemon-home.com/{lang}/page/{pageId}.html";

    @Value("${scraper.page-id:751}")
    private String defaultPageId;

    @Value("${scraper.timeout-ms:15000}")
    private int timeoutMs;

    @Value("${scraper.eligible-url:https://web-view.app.pokemonchampions.jp/battle/pages/events/rs177501629259kmzbny/{lang}/pokemon.html}")
    private String eligibleUrlTemplate;

    private final SeasonRepository seasonRepository;
    private final PokemonRepository pokemonRepository;
    private final SeasonPokemonRepository seasonPokemonRepository;

    public OfficialRosterScraperService(SeasonRepository seasonRepository,
                                        PokemonRepository pokemonRepository,
                                        SeasonPokemonRepository seasonPokemonRepository) {
        this.seasonRepository = seasonRepository;
        this.pokemonRepository = pokemonRepository;
        this.seasonPokemonRepository = seasonPokemonRepository;
    }

    public ScrapedResult scrape() {
        return scrape(defaultPageId);
    }

    @Transactional
    public ScrapedResult scrape(String pageId) {
        log.info("========== OfficialRosterScraper 開始 ==========");

        // ─── 1) 賽制頁：抓英文版取時間區間，同時抓中英文 Mega 名單做位置配對 ───
        Season season;
        List<String> megaApiNames;
        Map<String, String> megaZhToApi;

        try {
            Document newsEn = fetchDocument(NEWS_BASE_URL.replace("{lang}", "en").replace("{pageId}", pageId));
            Document newsTc = fetchDocument(NEWS_BASE_URL.replace("{lang}", "tc").replace("{pageId}", pageId));

            String regulationName = parseRegulationName(newsEn);
            log.info("賽制名稱: {}", regulationName);

            LocalDateTime[] period = parseEnglishPeriod(newsEn.selectFirst("div.main-text").html());
            log.info("使用期間: {} ～ {}", period[0], period[1]);

            List<String> megaNamesEn = parseMegaEvolutionNames(newsEn);
            List<String> megaNamesTc = parseMegaEvolutionNames(newsTc);
            log.info("Mega 名單: EN={} 隻, TC={} 隻", megaNamesEn.size(), megaNamesTc.size());

            megaApiNames = new ArrayList<>();
            megaZhToApi = new LinkedHashMap<>();

            for (int i = 0; i < megaNamesEn.size(); i++) {
                String enName = megaNamesEn.get(i);
                String tcName = i < megaNamesTc.size() ? megaNamesTc.get(i) : null;
                String api = PokemonNameMapper.toApiName(enName, "en");

                if (api != null) {
                    megaApiNames.add(api);
                    if (tcName != null) {
                        megaZhToApi.put(tcName, api);
                    }
                    log.info("  [Mega] {} / {} → {}", enName, tcName != null ? tcName : "?", api);
                } else {
                    log.warn("  [Mega] ⚠ 無法映射 EN: '{}'", enName);
                }
            }

            season = saveOrUpdateSeason(regulationName, period[0], period[1],
                    NEWS_BASE_URL.replace("{lang}", "en").replace("{pageId}", pageId));
            log.info("Season 儲存完成: {}", season);

        } catch (IOException e) {
            throw new RuntimeException("Failed to scrape news page", e);
        }

        // ─── 2) 可參賽名單：抓 en + tc 兩版，用 formId 配對建中文映射 ───
        List<Pokemon> allPokemon = new ArrayList<>();
        Set<String> megaSlugSet = new HashSet<>(megaApiNames);

        try {
            Document eligibleEn = fetchDocument(eligibleUrlTemplate.replace("{lang}", "en"));
            Document eligibleTc = fetchDocument(eligibleUrlTemplate.replace("{lang}", "tc"));

            List<RawPokemonEntry> entriesEn = parseEligiblePokemon(eligibleEn);
            List<RawPokemonEntry> entriesTc = parseEligiblePokemon(eligibleTc);
            log.info("可參賽名單: EN={} 隻, TC={} 隻", entriesEn.size(), entriesTc.size());

            Map<String, String> formIdToChineseName = new LinkedHashMap<>();
            for (RawPokemonEntry tc : entriesTc) {
                formIdToChineseName.put(tc.formId(), tc.displayName());
            }

            for (RawPokemonEntry entry : entriesEn) {
                String apiSlug = PokeApiSlugResolver.resolve(entry.displayName());
                if (apiSlug == null || apiSlug.isBlank()) {
                    log.warn("  ⚠ 無法解析 slug: {} ({})", entry.displayName(), entry.formId());
                    continue;
                }

                String zhName = formIdToChineseName.get(entry.formId());

                Pokemon pokemon = pokemonRepository.findByFormId(entry.formId())
                        .or(() -> pokemonRepository.findByApiName(apiSlug))
                        .orElseGet(() -> {
                            Pokemon p = new Pokemon(entry.dexNumber(), entry.formId(), entry.displayName(), apiSlug, false);
                            log.info("  [NEW] #{} {} / {} → {}", entry.dexNumber(), entry.displayName(),
                                    zhName != null ? zhName : "?", apiSlug);
                            return p;
                        });
                if (!apiSlug.equals(pokemon.getApiName())) {
                    log.info("  [FIX] {} → {} (formId={})", pokemon.getApiName(), apiSlug, entry.formId());
                    pokemon.setStatsSynced(false);
                }
                pokemon.setApiName(apiSlug);
                pokemon.setDisplayName(entry.displayName());
                pokemon.setFormId(entry.formId());
                pokemon.setNationalDexNumber(entry.dexNumber());
                if (zhName != null) {
                    pokemon.setChineseName(zhName);
                }
                allPokemon.add(pokemonRepository.save(pokemon));
            }

            Map<String, String> apiToMegaZh = new LinkedHashMap<>();
            for (var e : megaZhToApi.entrySet()) {
                apiToMegaZh.put(e.getValue(), e.getKey());
            }

            for (String megaSlug : megaApiNames) {
                Pokemon mega = pokemonRepository.findByApiName(megaSlug).orElseGet(() -> {
                    int dex = guessDexFromMegaSlug(megaSlug, allPokemon);
                    String megaDisplay = "Mega " + capitalizeFirst(megaSlug.replace("-mega", "").replace("-", " "));
                    Pokemon p = new Pokemon(dex, megaSlug, megaDisplay, megaSlug, true);
                    log.info("  [MEGA-NEW] #{} {} → {}", dex, megaDisplay, megaSlug);
                    return p;
                });
                mega.setMega(true);
                String megaZh = apiToMegaZh.get(megaSlug);
                if (megaZh != null) {
                    mega.setChineseName(megaZh);
                }
                allPokemon.add(pokemonRepository.save(mega));
            }

            log.info("========== 中英文映射統計 ==========");
            long withZh = allPokemon.stream().filter(p -> p.getChineseName() != null).count();
            log.info("  有中文名: {}/{}", withZh, allPokemon.size());

        } catch (IOException e) {
            throw new RuntimeException("Failed to scrape eligible list", e);
        }

        // ─── 3) 建立 Season ↔ Pokemon 關聯 (SeasonPokemon 中間表) ───
        log.info("========== 建立 Season-Pokemon 關聯 ==========");
        seasonPokemonRepository.deleteBySeasonId(season.getId());
        seasonPokemonRepository.flush();

        int linkedCount = 0;
        for (Pokemon pokemon : allPokemon) {
            boolean canMega = megaSlugSet.contains(pokemon.getApiName());
            seasonPokemonRepository.save(new SeasonPokemon(season, pokemon, canMega));
            linkedCount++;
        }
        log.info("  已建立 {} 筆 Season-Pokemon 關聯 (Season: {})", linkedCount, season.getRegulationName());

        log.info("========== 總計: {} 隻寶可夢已存入 DB ==========", allPokemon.size());
        List<String> allApiNames = allPokemon.stream().map(Pokemon::getApiName).toList();
        return new ScrapedResult(season, allApiNames, megaApiNames, allPokemon.size());
    }

    // ─── HTML 抓取 ──────────────────────────────────────────────

    private Document fetchDocument(String url) throws IOException {
        log.debug("Fetching: {}", url);
        return Jsoup.connect(url)
                .timeout(timeoutMs)
                .userAgent("Mozilla/5.0 (PokéChampions-Backend)")
                .get();
    }

    // ─── 賽制頁解析 ─────────────────────────────────────────────

    private String parseRegulationName(Document doc) {
        Element titleEl = doc.selectFirst("div.title");
        if (titleEl == null) return doc.title();
        return titleEl.text().trim()
                .replaceAll("^(關於|About)\\s*「?", "")
                .replaceAll("」$", "")
                .trim();
    }

    private LocalDateTime[] parseEnglishPeriod(String html) {
        Pattern p = Pattern.compile(
                "\\w+,\\s+(\\w+)\\s+(\\d{1,2}),\\s+(\\d{4}),?\\s+at\\s+(\\d{2}):(\\d{2})\\s+UTC" +
                "\\s+to\\s+" +
                "\\w+,\\s+(\\w+)\\s+(\\d{1,2}),\\s+(\\d{4}),?\\s+at\\s+(\\d{2}):(\\d{2})\\s+UTC"
        );
        Matcher m = p.matcher(html);
        if (!m.find()) {
            log.warn("無法解析英文日期區間，使用預設值");
            return new LocalDateTime[]{LocalDateTime.now(), LocalDateTime.now().plusMonths(2)};
        }
        int startMonth = java.time.Month.valueOf(m.group(1).toUpperCase()).getValue();
        int endMonth = java.time.Month.valueOf(m.group(6).toUpperCase()).getValue();
        LocalDateTime start = LocalDateTime.of(
                Integer.parseInt(m.group(3)), startMonth, Integer.parseInt(m.group(2)),
                Integer.parseInt(m.group(4)), Integer.parseInt(m.group(5))
        );
        LocalDateTime end = LocalDateTime.of(
                Integer.parseInt(m.group(8)), endMonth, Integer.parseInt(m.group(7)),
                Integer.parseInt(m.group(9)), Integer.parseInt(m.group(10))
        );
        return new LocalDateTime[]{start, end};
    }

    private List<String> parseMegaEvolutionNames(Document doc) {
        List<String> names = new ArrayList<>();
        Element h4 = doc.selectFirst("div.main-text h4");
        if (h4 == null) return names;
        for (Node child : h4.childNodes()) {
            if (child instanceof TextNode textNode) {
                String text = textNode.getWholeText().trim()
                        .replaceAll("\\u200B", "")
                        .replaceAll("\\u00A0", " ")
                        .replaceAll("\\s+", " ")
                        .trim();
                if (!text.isEmpty()) {
                    names.add(text);
                }
            }
        }
        return names;
    }

    // ─── 可參賽名單解析 ─────────────────────────────────────────

    private List<RawPokemonEntry> parseEligiblePokemon(Document doc) {
        for (Element script : doc.getElementsByTag("script")) {
            String data = script.data();
            if (!data.contains("const pokemons")) continue;
            int arrayStart = data.indexOf('[');
            int arrayEnd = data.lastIndexOf(']') + 1;
            if (arrayStart == -1 || arrayEnd == 0) continue;
            return parseJsArray(data.substring(arrayStart, arrayEnd));
        }
        return List.of();
    }

    private List<RawPokemonEntry> parseJsArray(String arrayStr) {
        List<RawPokemonEntry> entries = new ArrayList<>();
        Pattern entryPattern = Pattern.compile("\\[\"(\\d{4})-(\\d{3})\",(\\d+),\"([^\"]+)\"\\]");
        Matcher matcher = entryPattern.matcher(arrayStr);
        while (matcher.find()) {
            String formId = matcher.group(1) + "-" + matcher.group(2);
            int dexNumber = Integer.parseInt(matcher.group(1));
            String displayName = matcher.group(4);
            entries.add(new RawPokemonEntry(formId, dexNumber, displayName));
        }
        return entries;
    }

    // ─── 輔助 ───────────────────────────────────────────────────

    private Season saveOrUpdateSeason(String regulationName, LocalDateTime start, LocalDateTime end, String url) {
        Season season = seasonRepository.findByRegulationName(regulationName)
                .orElseGet(() -> new Season(regulationName, start, end, url));
        season.setStartDate(start);
        season.setEndDate(end);
        season.setSourceUrl(url);
        return seasonRepository.save(season);
    }

    private int guessDexFromMegaSlug(String megaSlug, List<Pokemon> allPokemon) {
        String baseName = megaSlug.split("-mega")[0];
        return allPokemon.stream()
                .filter(p -> p.getApiName().equals(baseName))
                .findFirst()
                .map(Pokemon::getNationalDexNumber)
                .orElse(0);
    }

    private String capitalizeFirst(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    // ─── DTO ────────────────────────────────────────────────────

    public record ScrapedResult(
            Season season,
            List<String> allApiNames,
            List<String> megaApiNames,
            int totalCount
    ) {}

    private record RawPokemonEntry(String formId, int dexNumber, String displayName) {}
}
