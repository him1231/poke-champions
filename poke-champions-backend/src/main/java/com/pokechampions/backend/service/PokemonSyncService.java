package com.pokechampions.backend.service;

import com.pokechampions.backend.entity.Pokemon;
import com.pokechampions.backend.entity.PokemonType;
import com.pokechampions.backend.entity.PokemonTypeSlot;
import com.pokechampions.backend.repository.PokemonRepository;
import com.pokechampions.backend.repository.PokemonTypeRepository;
import com.pokechampions.backend.repository.PokemonTypeSlotRepository;
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
    private final RestClient restClient;

    public PokemonSyncService(OfficialRosterScraperService scraperService,
                              PokemonRepository pokemonRepository,
                              PokemonTypeRepository typeRepository,
                              PokemonTypeSlotRepository typeSlotRepository) {
        this.scraperService = scraperService;
        this.pokemonRepository = pokemonRepository;
        this.typeRepository = typeRepository;
        this.typeSlotRepository = typeSlotRepository;
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

    public record SyncReport(int total, int success, int failed) {}
}
