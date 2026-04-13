package com.pokechampions.backend.service;

import com.pokechampions.backend.entity.*;
import com.pokechampions.backend.repository.*;
import com.pokechampions.backend.service.OfficialRosterScraperService.ScrapedResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class PokemonSyncService {

    private static final Logger log = LoggerFactory.getLogger(PokemonSyncService.class);
    private static final String POKEAPI_BASE = "https://pokeapi.co/api/v2/pokemon/";

    private final OfficialRosterScraperService scraperService;
    private final PokemonRepository pokemonRepository;
    private final PokemonTypeRepository typeRepository;
    private final PokemonTypeSlotRepository typeSlotRepository;
    private final AbilityRepository abilityRepository;
    private final PokemonAbilityRepository pokemonAbilityRepository;
    private final RestClient restClient;

    public PokemonSyncService(OfficialRosterScraperService scraperService,
                              PokemonRepository pokemonRepository,
                              PokemonTypeRepository typeRepository,
                              PokemonTypeSlotRepository typeSlotRepository,
                              AbilityRepository abilityRepository,
                              PokemonAbilityRepository pokemonAbilityRepository) {
        this.scraperService = scraperService;
        this.pokemonRepository = pokemonRepository;
        this.typeRepository = typeRepository;
        this.typeSlotRepository = typeSlotRepository;
        this.abilityRepository = abilityRepository;
        this.pokemonAbilityRepository = pokemonAbilityRepository;
        this.restClient = RestClient.builder()
                .baseUrl(POKEAPI_BASE)
                .build();
    }

    public SyncReport fullSync() {
        ScrapedResult result = scraperService.scrape();
        return syncStats(result.allApiNames());
    }

    public SyncReport fullSync(String pageId) {
        ScrapedResult result = scraperService.scrape(pageId);
        return syncStats(result.allApiNames());
    }

    public SyncReport syncUnsyncedOnly() {
        List<Pokemon> unsynced = pokemonRepository.findByStatsSyncedFalse();
        log.info("找到 {} 隻尚未同步種族值的寶可夢", unsynced.size());
        List<String> names = unsynced.stream().map(Pokemon::getApiName).toList();
        return syncStats(names);
    }

    /**
     * 從 PokeAPI 為所有已入庫的寶可夢同步特性（不重新抓種族值）。
     *
     * @param forceRefresh {@code false}（預設）時，若該寶可夢在 {@code pokemon_abilities} 已有任何列則跳過（舊行為）。
     *                     {@code true} 時一律重抓並覆寫，用於修正早期只寫入單一特性等不完整資料。
     */
    @Transactional
    public SyncReport syncAbilities(boolean forceRefresh) {
        List<Pokemon> all = pokemonRepository.findAll();
        log.info("========== PokemonSyncService: 開始為 {} 隻寶可夢同步特性（forceRefresh={}） ==========",
                all.size(), forceRefresh);
        int success = 0, failed = 0;

        for (Pokemon pokemon : all) {
            List<PokemonAbility> existing = pokemonAbilityRepository.findByPokemon(pokemon);
            if (!forceRefresh && !existing.isEmpty()) {
                success++;
                continue;
            }

            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> body = restClient.get()
                        .uri(pokemon.getApiName())
                        .retrieve()
                        .body(Map.class);

                if (body == null) {
                    failed++;
                    continue;
                }

                applyAbilitiesFromApi(pokemon, body);
                success++;
                log.info("  ✓ {} 特性已同步", pokemon.getApiName());

            } catch (Exception e) {
                log.warn("  ✗ {} 特性同步失敗: {}", pokemon.getApiName(), e.getMessage());
                failed++;
            }
        }

        log.info("========== 特性同步完成: 成功 {}, 失敗 {}, 共 {} ==========", success, failed, all.size());
        return new SyncReport(all.size(), success, failed);
    }

    @Transactional
    public SyncReport syncStats(List<String> apiNames) {
        log.info("========== PokemonSyncService: 開始查詢 {} 隻寶可夢種族值+屬性 ==========", apiNames.size());
        int success = 0;
        int failed = 0;

        for (String name : apiNames) {
            Optional<Pokemon> opt = pokemonRepository.findByApiName(name);
            if (opt.isEmpty()) {
                log.warn("  ⚠ DB 中找不到 {}, 跳過", name);
                failed++;
                continue;
            }

            Pokemon pokemon = opt.get();
            if (pokemon.isStatsSynced()) {
                log.debug("  ⏭ {} 已有種族值，跳過", name);
                success++;
                continue;
            }

            try {
                log.info("  查詢 PokeAPI: {}", name);

                @SuppressWarnings("unchecked")
                Map<String, Object> body = restClient.get()
                        .uri(name)
                        .retrieve()
                        .body(Map.class);

                if (body == null) {
                    log.warn("  ⚠ {} 回傳 null", name);
                    failed++;
                    continue;
                }

                applyStatsFromApi(pokemon, body);
                pokemonRepository.save(pokemon);

                applyTypesFromApi(pokemon, body);
                applyAbilitiesFromApi(pokemon, body);

                success++;

                log.info("  ✓ {} → HP:{} Atk:{} Def:{} SpA:{} SpD:{} Spe:{} (BST:{})",
                        name, pokemon.getHp(), pokemon.getAttack(), pokemon.getDefense(),
                        pokemon.getSpecialAttack(), pokemon.getSpecialDefense(),
                        pokemon.getSpeed(), pokemon.getBaseStatTotal());

            } catch (Exception e) {
                log.warn("  ✗ {} 查詢失敗: {}", name, e.getMessage());
                failed++;
            }
        }

        log.info("========== 種族值+屬性同步完成: 成功 {}, 失敗 {}, 共 {} ==========", success, failed, apiNames.size());
        return new SyncReport(apiNames.size(), success, failed);
    }

    @SuppressWarnings("unchecked")
    private void applyStatsFromApi(Pokemon pokemon, Map<String, Object> body) {
        List<Map<String, Object>> statsList = (List<Map<String, Object>>) body.get("stats");
        int hp = 0, atk = 0, def = 0, spa = 0, spd = 0, spe = 0;

        for (Map<String, Object> stat : statsList) {
            int val = ((Number) stat.get("base_stat")).intValue();
            Map<String, Object> statInfo = (Map<String, Object>) stat.get("stat");
            String statName = (String) statInfo.get("name");
            switch (statName) {
                case "hp"              -> hp = val;
                case "attack"          -> atk = val;
                case "defense"         -> def = val;
                case "special-attack"  -> spa = val;
                case "special-defense" -> spd = val;
                case "speed"           -> spe = val;
            }
        }
        pokemon.applyStats(hp, atk, def, spa, spd, spe);
    }

    @SuppressWarnings("unchecked")
    private void applyTypesFromApi(Pokemon pokemon, Map<String, Object> body) {
        List<Map<String, Object>> typesList = (List<Map<String, Object>>) body.get("types");

        List<PokemonTypeSlot> existingSlots = typeSlotRepository.findByPokemon(pokemon);
        if (!existingSlots.isEmpty()) {
            typeSlotRepository.deleteByPokemon(pokemon);
            typeSlotRepository.flush();
        }

        for (Map<String, Object> typeEntry : typesList) {
            int slot = ((Number) typeEntry.get("slot")).intValue();
            Map<String, Object> typeInfo = (Map<String, Object>) typeEntry.get("type");
            String typeName = (String) typeInfo.get("name");

            Optional<PokemonType> typeOpt = typeRepository.findByName(typeName);
            if (typeOpt.isEmpty()) {
                log.warn("    ⚠ 找不到屬性 '{}' for {}", typeName, pokemon.getApiName());
                continue;
            }

            typeSlotRepository.save(new PokemonTypeSlot(pokemon, typeOpt.get(), slot));
            log.debug("    {} slot {} = {}", pokemon.getApiName(), slot, typeName);
        }
    }

    @SuppressWarnings("unchecked")
    private void applyAbilitiesFromApi(Pokemon pokemon, Map<String, Object> body) {
        List<Map<String, Object>> abilitiesList = (List<Map<String, Object>>) body.get("abilities");
        if (abilitiesList == null || abilitiesList.isEmpty()) return;

        List<PokemonAbility> existing = pokemonAbilityRepository.findByPokemon(pokemon);
        if (!existing.isEmpty()) {
            pokemonAbilityRepository.deleteByPokemon(pokemon);
            pokemonAbilityRepository.flush();
        }

        for (Map<String, Object> entry : abilitiesList) {
            Map<String, Object> abilityInfo = (Map<String, Object>) entry.get("ability");
            String abilityName = (String) abilityInfo.get("name");
            boolean isHidden = Boolean.TRUE.equals(entry.get("is_hidden"));
            int slot = ((Number) entry.get("slot")).intValue();

            String rawName = abilityName.substring(0, 1).toUpperCase()
                    + abilityName.substring(1).replace("-", " ");
            final String abilityDisplayName = capitalizeWords(rawName);

            Ability ability = abilityRepository.findByName(abilityName)
                    .orElseGet(() -> abilityRepository.save(new Ability(abilityName, abilityDisplayName)));

            pokemonAbilityRepository.save(new PokemonAbility(pokemon, ability, slot, isHidden));
            log.debug("    {} ability slot {} = {} (hidden={})", pokemon.getApiName(), slot, abilityName, isHidden);
        }
    }

    /**
     * 從 PokeAPI /pokemon-species/{dexNumber} 取得日文名稱，寫入 Pokemon.japaneseName。
     */
    @Transactional
    public SyncReport syncJapaneseNames(boolean forceRefresh) {
        List<Pokemon> targets = forceRefresh
                ? pokemonRepository.findAll()
                : pokemonRepository.findAll().stream()
                    .filter(p -> p.getJapaneseName() == null)
                    .toList();

        log.info("========== PokemonSyncService: 開始同步 {} 隻寶可夢日文名 ==========", targets.size());

        RestClient speciesClient = RestClient.builder()
                .baseUrl("https://pokeapi.co/api/v2/pokemon-species/")
                .build();

        int success = 0, failed = 0;
        java.util.Set<Integer> visited = new java.util.HashSet<>();

        for (Pokemon pokemon : targets) {
            int dex = pokemon.getNationalDexNumber();
            if (visited.contains(dex)) {
                applyJapaneseFromSiblings(pokemon);
                success++;
                continue;
            }

            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> body = speciesClient.get()
                        .uri(String.valueOf(dex))
                        .retrieve()
                        .body(Map.class);

                if (body == null) { failed++; continue; }

                String jaName = extractSpeciesName(body, "ja");
                if (jaName != null) {
                    pokemon.setJapaneseName(jaName);
                    pokemonRepository.save(pokemon);
                    success++;
                    log.info("  ✓ {} → {}", pokemon.getApiName(), jaName);
                } else {
                    failed++;
                }
                visited.add(dex);

            } catch (Exception e) {
                log.warn("  ✗ {} 日文名同步失敗: {}", pokemon.getApiName(), e.getMessage());
                failed++;
                visited.add(dex);
            }
        }

        log.info("========== 日文名同步完成: 成功 {}, 失敗 {}, 共 {} ==========", success, failed, targets.size());
        return new SyncReport(targets.size(), success, failed);
    }

    private void applyJapaneseFromSiblings(Pokemon pokemon) {
        pokemonRepository.findAll().stream()
                .filter(p -> p.getNationalDexNumber() == pokemon.getNationalDexNumber()
                        && p.getJapaneseName() != null)
                .findFirst()
                .ifPresent(sibling -> {
                    pokemon.setJapaneseName(sibling.getJapaneseName());
                    pokemonRepository.save(pokemon);
                });
    }

    @SuppressWarnings("unchecked")
    private String extractSpeciesName(Map<String, Object> body, String langCode) {
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

    private String capitalizeWords(String s) {
        String[] words = s.split(" ");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (i > 0) sb.append(' ');
            if (!words[i].isEmpty()) {
                sb.append(Character.toUpperCase(words[i].charAt(0)));
                if (words[i].length() > 1) sb.append(words[i].substring(1));
            }
        }
        return sb.toString();
    }

    public record SyncReport(int total, int success, int failed) {}
}
