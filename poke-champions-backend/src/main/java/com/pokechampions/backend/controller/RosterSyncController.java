package com.pokechampions.backend.controller;

import com.pokechampions.backend.entity.*;
import com.pokechampions.backend.repository.*;
import com.pokechampions.backend.service.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/roster")
public class RosterSyncController {

    private final OfficialRosterScraperService scraperService;
    private final PokemonSyncService pokemonSyncService;
    private final TypeSyncService typeSyncService;
    private final TypeMatchupService typeMatchupService;
    private final MoveSyncService moveSyncService;
    private final PokemonRepository pokemonRepository;
    private final PokemonTypeRepository typeRepository;
    private final TypeEffectivenessRepository effectivenessRepository;
    private final PokemonTypeSlotRepository typeSlotRepository;
    private final SeasonPokemonRepository seasonPokemonRepository;
    private final MoveRepository moveRepository;
    private final PokemonMoveRepository pokemonMoveRepository;

    public RosterSyncController(OfficialRosterScraperService scraperService,
                                PokemonSyncService pokemonSyncService,
                                TypeSyncService typeSyncService,
                                TypeMatchupService typeMatchupService,
                                MoveSyncService moveSyncService,
                                PokemonRepository pokemonRepository,
                                PokemonTypeRepository typeRepository,
                                TypeEffectivenessRepository effectivenessRepository,
                                PokemonTypeSlotRepository typeSlotRepository,
                                SeasonPokemonRepository seasonPokemonRepository,
                                MoveRepository moveRepository,
                                PokemonMoveRepository pokemonMoveRepository) {
        this.scraperService = scraperService;
        this.pokemonSyncService = pokemonSyncService;
        this.typeSyncService = typeSyncService;
        this.typeMatchupService = typeMatchupService;
        this.moveSyncService = moveSyncService;
        this.pokemonRepository = pokemonRepository;
        this.typeRepository = typeRepository;
        this.effectivenessRepository = effectivenessRepository;
        this.typeSlotRepository = typeSlotRepository;
        this.seasonPokemonRepository = seasonPokemonRepository;
        this.moveRepository = moveRepository;
        this.pokemonMoveRepository = pokemonMoveRepository;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    // ═══════════════════════════════════════════════════
    //  爬蟲 & 同步
    // ═══════════════════════════════════════════════════

    @GetMapping("/scrape")
    public ResponseEntity<OfficialRosterScraperService.ScrapedResult> scrape(
            @RequestParam(defaultValue = "751") String pageId
    ) {
        return ResponseEntity.ok(scraperService.scrape(pageId));
    }

    @PostMapping("/sync-stats")
    public ResponseEntity<PokemonSyncService.SyncReport> syncStats() {
        return ResponseEntity.ok(pokemonSyncService.syncUnsyncedOnly());
    }

    @PostMapping("/full-sync")
    public ResponseEntity<Map<String, Object>> fullSync(
            @RequestParam(defaultValue = "751") String pageId
    ) {
        OfficialRosterScraperService.ScrapedResult scrapeResult = scraperService.scrape(pageId);
        PokemonSyncService.SyncReport syncReport = pokemonSyncService.syncUnsyncedOnly();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("scrape", scrapeResult);
        response.put("sync", syncReport);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/sync-types")
    public ResponseEntity<TypeSyncService.SyncReport> syncTypeEffectiveness() {
        return ResponseEntity.ok(typeSyncService.syncTypeEffectiveness());
    }

    // ═══════════════════════════════════════════════════
    //  寶可夢查詢
    // ═══════════════════════════════════════════════════

    @GetMapping("/pokemon")
    public ResponseEntity<List<Pokemon>> listAll() {
        return ResponseEntity.ok(pokemonRepository.findAll());
    }

    @GetMapping("/pokemon/mega")
    public ResponseEntity<List<Pokemon>> listMega() {
        return ResponseEntity.ok(pokemonRepository.findByIsMegaTrue());
    }

    @GetMapping("/pokemon/{apiName}/types")
    public ResponseEntity<List<Map<String, Object>>> pokemonTypes(@PathVariable String apiName) {
        return pokemonRepository.findByApiName(apiName)
                .map(pokemon -> {
                    List<Map<String, Object>> types = typeSlotRepository.findByPokemon(pokemon).stream()
                            .map(slot -> {
                                Map<String, Object> m = new LinkedHashMap<>();
                                m.put("slot", slot.getSlot());
                                m.put("type", slot.getType().getName());
                                m.put("typeChinese", slot.getType().getChineseName());
                                return m;
                            })
                            .collect(Collectors.toList());
                    return ResponseEntity.ok(types);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ═══════════════════════════════════════════════════
    //  屬性 & 相剋
    // ═══════════════════════════════════════════════════

    @GetMapping("/types")
    public ResponseEntity<List<PokemonType>> listTypes() {
        return ResponseEntity.ok(typeRepository.findAll());
    }

    @GetMapping("/types/{typeName}/effectiveness")
    public ResponseEntity<Map<String, Object>> typeEffectiveness(@PathVariable String typeName) {
        return typeRepository.findByName(typeName.toLowerCase())
                .map(type -> {
                    List<TypeEffectiveness> attacking = effectivenessRepository.findByAttackingType(type);
                    List<TypeEffectiveness> defending = effectivenessRepository.findByDefendingType(type);

                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("type", type.getName());
                    result.put("chineseName", type.getChineseName());

                    result.put("attackingEffects", attacking.stream().map(te -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("defendingType", te.getDefendingType().getName());
                        m.put("multiplier", te.getMultiplier());
                        return m;
                    }).collect(Collectors.toList()));

                    result.put("defensiveEffects", defending.stream().map(te -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("attackingType", te.getAttackingType().getName());
                        m.put("multiplier", te.getMultiplier());
                        return m;
                    }).collect(Collectors.toList()));

                    return ResponseEntity.ok(result);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ═══════════════════════════════════════════════════
    //  防禦相性（含 4x 弱點、0x 無效等複合計算）
    // ═══════════════════════════════════════════════════

    /** 查某寶可夢的完整防禦相性表 */
    @GetMapping("/pokemon/{apiName}/matchup")
    public ResponseEntity<TypeMatchupService.DefensiveProfile> pokemonMatchup(@PathVariable String apiName) {
        return typeMatchupService.getDefensiveProfile(apiName)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** 查純屬性組合的防禦相性（不需指定寶可夢） */
    @GetMapping("/types/matchup")
    public ResponseEntity<TypeMatchupService.DefensiveProfile> typeMatchup(
            @RequestParam String type1,
            @RequestParam(required = false) String type2
    ) {
        return typeMatchupService.getDefensiveProfileByTypes(type1, type2)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ═══════════════════════════════════════════════════
    //  賽季寶可夢
    // ═══════════════════════════════════════════════════

    @GetMapping("/seasons/{seasonId}/pokemon")
    public ResponseEntity<List<Map<String, Object>>> seasonPokemon(@PathVariable Long seasonId) {
        List<SeasonPokemon> list = seasonPokemonRepository.findBySeasonId(seasonId);
        if (list.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        List<Map<String, Object>> result = list.stream().map(sp -> {
            Map<String, Object> m = new LinkedHashMap<>();
            Pokemon p = sp.getPokemon();
            m.put("apiName", p.getApiName());
            m.put("displayName", p.getDisplayName());
            m.put("chineseName", p.getChineseName());
            m.put("nationalDexNumber", p.getNationalDexNumber());
            m.put("canMegaEvolve", sp.isCanMegaEvolve());
            m.put("isMega", p.isMega());
            if (p.isStatsSynced()) {
                m.put("baseStatTotal", p.getBaseStatTotal());
            }
            return m;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/seasons/{seasonId}/pokemon/mega")
    public ResponseEntity<List<Map<String, Object>>> seasonMegaPokemon(@PathVariable Long seasonId) {
        List<SeasonPokemon> list = seasonPokemonRepository.findBySeasonIdAndCanMegaEvolveTrue(seasonId);
        List<Map<String, Object>> result = list.stream().map(sp -> {
            Map<String, Object> m = new LinkedHashMap<>();
            Pokemon p = sp.getPokemon();
            m.put("apiName", p.getApiName());
            m.put("displayName", p.getDisplayName());
            m.put("chineseName", p.getChineseName());
            return m;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    // ═══════════════════════════════════════════════════
    //  招式
    // ═══════════════════════════════════════════════════

    @PostMapping("/sync-moves")
    public ResponseEntity<MoveSyncService.MoveSyncReport> syncMoves() {
        return ResponseEntity.ok(moveSyncService.syncFromGame8());
    }

    /** 爬取每招的 Game8 詳情頁，同步 accuracy + 可學習寶可夢 */
    @PostMapping("/sync-pokemon-moves")
    public ResponseEntity<MoveSyncService.PokemonMoveSyncReport> syncPokemonMoves(
            @RequestParam(defaultValue = "false") boolean forceRefresh
    ) {
        return ResponseEntity.ok(moveSyncService.syncPokemonMovesFromGame8(forceRefresh));
    }

    /** 用 OpenAI 批次翻譯招式描述為繁體中文 */
    @PostMapping("/translate-move-descriptions")
    public ResponseEntity<MoveSyncService.TranslationReport> translateMoveDescriptions(
            @RequestParam(defaultValue = "false") boolean forceRefresh
    ) {
        return ResponseEntity.ok(moveSyncService.translateDescriptions(forceRefresh));
    }

    /** 從 PokeAPI 同步招式繁體中文名稱 */
    @PostMapping("/sync-move-names")
    public ResponseEntity<MoveSyncService.ChineseNameSyncReport> syncMoveChineseNames(
            @RequestParam(defaultValue = "false") boolean forceRefresh
    ) {
        return ResponseEntity.ok(moveSyncService.syncChineseNames(forceRefresh));
    }

    @GetMapping("/moves")
    public ResponseEntity<List<Move>> listMoves() {
        return ResponseEntity.ok(moveRepository.findAll());
    }

    @GetMapping("/moves/{name}")
    public ResponseEntity<Move> getMove(@PathVariable String name) {
        return moveRepository.findByName(name.toLowerCase())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/moves/type/{typeName}")
    public ResponseEntity<List<Move>> movesByType(@PathVariable String typeName) {
        return typeRepository.findByName(typeName.toLowerCase())
                .map(type -> ResponseEntity.ok(moveRepository.findByType(type)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/moves/category/{category}")
    public ResponseEntity<List<Move>> movesByCategory(@PathVariable String category) {
        try {
            Move.MoveCategory cat = Move.MoveCategory.valueOf(category.toUpperCase());
            return ResponseEntity.ok(moveRepository.findByCategory(cat));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /** 查詢某寶可夢可學習的全部招式 */
    @GetMapping("/pokemon/{apiName}/moves")
    public ResponseEntity<List<Map<String, Object>>> pokemonMoves(@PathVariable String apiName) {
        return pokemonRepository.findByApiName(apiName)
                .map(pokemon -> {
                    List<Map<String, Object>> moves = pokemonMoveRepository.findByPokemon(pokemon).stream()
                            .map(pm -> {
                                Map<String, Object> m = new LinkedHashMap<>();
                                Move move = pm.getMove();
                                m.put("name", move.getName());
                                m.put("displayName", move.getDisplayName());
                                m.put("type", move.getType().getName());
                                m.put("typeChinese", move.getType().getChineseName());
                                m.put("category", move.getCategory().name());
                                m.put("power", move.getPower());
                                m.put("accuracy", move.getAccuracy());
                                m.put("pp", move.getPp());
                                m.put("description", move.getDescription());
                                m.put("chineseDescription", move.getChineseDescription());
                                m.put("source", pm.getSource().name());
                                m.put("verified", pm.isVerified());
                                return m;
                            })
                            .collect(Collectors.toList());
                    return ResponseEntity.ok(moves);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /** 查詢哪些寶可夢可以學習某招式 */
    @GetMapping("/moves/{name}/pokemon")
    public ResponseEntity<List<Map<String, Object>>> moveLearners(@PathVariable String name) {
        return moveRepository.findByName(name.toLowerCase())
                .map(move -> {
                    List<Map<String, Object>> pokemon = pokemonMoveRepository.findByMove(move).stream()
                            .map(pm -> {
                                Map<String, Object> m = new LinkedHashMap<>();
                                Pokemon p = pm.getPokemon();
                                m.put("apiName", p.getApiName());
                                m.put("displayName", p.getDisplayName());
                                m.put("chineseName", p.getChineseName());
                                m.put("isMega", p.isMega());
                                return m;
                            })
                            .collect(Collectors.toList());
                    return ResponseEntity.ok(pokemon);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ═══════════════════════════════════════════════════
    //  名稱映射
    // ═══════════════════════════════════════════════════

    @GetMapping("/name-mapping")
    public ResponseEntity<List<Map<String, String>>> nameMapping() {
        return ResponseEntity.ok(
                pokemonRepository.findByChineseNameIsNotNull().stream()
                        .map(p -> {
                            Map<String, String> m = new LinkedHashMap<>();
                            m.put("apiName", p.getApiName());
                            m.put("englishName", p.getDisplayName());
                            m.put("chineseName", p.getChineseName());
                            m.put("mega", String.valueOf(p.isMega()));
                            return m;
                        })
                        .collect(Collectors.toList())
        );
    }
}
