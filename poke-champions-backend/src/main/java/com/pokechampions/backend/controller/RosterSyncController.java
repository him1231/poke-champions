package com.pokechampions.backend.controller;

import com.pokechampions.backend.entity.*;
import com.pokechampions.backend.repository.*;
import com.pokechampions.backend.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/roster")
@Tag(name = "名冊與資料", description = "爬蟲同步、寶可夢／屬性／招式／持有物品等查詢與維運端點。")
public class RosterSyncController {

    private final OfficialRosterScraperService scraperService;
    private final PokemonSyncService pokemonSyncService;
    private final TypeSyncService typeSyncService;
    private final TypeMatchupService typeMatchupService;
    private final MoveSyncService moveSyncService;
    private final ItemSyncService itemSyncService;
    private final AbilitySyncService abilitySyncService;
    private final PokemonRepository pokemonRepository;
    private final PokemonTypeRepository typeRepository;
    private final TypeEffectivenessRepository effectivenessRepository;
    private final PokemonTypeSlotRepository typeSlotRepository;
    private final SeasonPokemonRepository seasonPokemonRepository;
    private final MoveRepository moveRepository;
    private final PokemonMoveRepository pokemonMoveRepository;
    private final HeldItemRepository heldItemRepository;
    private final AbilityRepository abilityRepository;
    private final PokemonAbilityRepository pokemonAbilityRepository;

    public RosterSyncController(OfficialRosterScraperService scraperService,
                                PokemonSyncService pokemonSyncService,
                                TypeSyncService typeSyncService,
                                TypeMatchupService typeMatchupService,
                                MoveSyncService moveSyncService,
                                ItemSyncService itemSyncService,
                                AbilitySyncService abilitySyncService,
                                PokemonRepository pokemonRepository,
                                PokemonTypeRepository typeRepository,
                                TypeEffectivenessRepository effectivenessRepository,
                                PokemonTypeSlotRepository typeSlotRepository,
                                SeasonPokemonRepository seasonPokemonRepository,
                                MoveRepository moveRepository,
                                PokemonMoveRepository pokemonMoveRepository,
                                HeldItemRepository heldItemRepository,
                                AbilityRepository abilityRepository,
                                PokemonAbilityRepository pokemonAbilityRepository) {
        this.scraperService = scraperService;
        this.pokemonSyncService = pokemonSyncService;
        this.typeSyncService = typeSyncService;
        this.typeMatchupService = typeMatchupService;
        this.moveSyncService = moveSyncService;
        this.itemSyncService = itemSyncService;
        this.abilitySyncService = abilitySyncService;
        this.pokemonRepository = pokemonRepository;
        this.typeRepository = typeRepository;
        this.effectivenessRepository = effectivenessRepository;
        this.typeSlotRepository = typeSlotRepository;
        this.seasonPokemonRepository = seasonPokemonRepository;
        this.moveRepository = moveRepository;
        this.pokemonMoveRepository = pokemonMoveRepository;
        this.heldItemRepository = heldItemRepository;
        this.abilityRepository = abilityRepository;
        this.pokemonAbilityRepository = pokemonAbilityRepository;
    }

    @Operation(summary = "健康檢查", description = "確認後端服務是否正常回應。")
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    // ═══════════════════════════════════════════════════
    //  爬蟲 & 同步
    // ═══════════════════════════════════════════════════

    @Operation(summary = "爬取名冊頁", description = "爬取官方賽季／名冊 HTML，回傳解析結果（不寫入資料庫）。")
    @GetMapping("/scrape")
    public ResponseEntity<OfficialRosterScraperService.ScrapedResult> scrape(
            @Parameter(description = "官方網頁 ID", example = "751")
            @RequestParam(defaultValue = "751") String pageId
    ) {
        return ResponseEntity.ok(scraperService.scrape(pageId));
    }

    @Operation(summary = "同步寶可夢能力值", description = "僅針對尚未同步過的寶可夢，從 Game8 等來源補齊種族值等統計資料。")
    @PostMapping("/sync-stats")
    public ResponseEntity<PokemonSyncService.SyncReport> syncStats() {
        return ResponseEntity.ok(pokemonSyncService.syncUnsyncedOnly());
    }

    @Operation(summary = "完整同步名冊與能力值", description = "先執行名冊爬取（scrape），再執行能力值同步（sync-stats），一次完成兩步。")
    @PostMapping("/full-sync")
    public ResponseEntity<Map<String, Object>> fullSync(
            @Parameter(description = "Game8 文章頁 ID", example = "751")
            @RequestParam(defaultValue = "751") String pageId
    ) {
        OfficialRosterScraperService.ScrapedResult scrapeResult = scraperService.scrape(pageId);
        PokemonSyncService.SyncReport syncReport = pokemonSyncService.syncUnsyncedOnly();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("scrape", scrapeResult);
        response.put("sync", syncReport);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "同步屬性相剋表", description = "從來源同步各屬性對戰時的傷害倍率（進攻／防守）到資料庫。")
    @PostMapping("/sync-types")
    public ResponseEntity<TypeSyncService.SyncReport> syncTypeEffectiveness() {
        return ResponseEntity.ok(typeSyncService.syncTypeEffectiveness());
    }

    // ═══════════════════════════════════════════════════
    //  寶可夢查詢
    // ═══════════════════════════════════════════════════

    @Operation(summary = "列出全部寶可夢", description = "回傳資料庫中所有寶可夢實體（含 Mega 等）。")
    @GetMapping("/pokemon")
    public ResponseEntity<List<Pokemon>> listAll() {
        return ResponseEntity.ok(pokemonRepository.findAllByOrderByFormId());
    }

    @Operation(summary = "列出 Mega 寶可夢", description = "僅回傳標記為 Mega 形態的寶可夢。")
    @GetMapping("/pokemon/mega")
    public ResponseEntity<List<Pokemon>> listMega() {
        return ResponseEntity.ok(pokemonRepository.findByIsMegaTrue());
    }

    @Operation(summary = "查詢寶可夢屬性", description = "依 PokeAPI 風格的 apiName（如 charizard）回傳該寶可夢的屬性槽位與中英文屬性名。")
    @GetMapping("/pokemon/{apiName}/types")
    public ResponseEntity<List<Map<String, Object>>> pokemonTypes(
            @Parameter(description = "寶可夢 apiName（小寫英文識別碼）", example = "charizard")
            @PathVariable String apiName) {
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

    @Operation(summary = "列出全部屬性", description = "回傳所有屬性類型（英文名、中文名等）。")
    @GetMapping("/types")
    public ResponseEntity<List<PokemonType>> listTypes() {
        return ResponseEntity.ok(typeRepository.findAll());
    }

    @Operation(summary = "查詢單一屬性相剋", description = "回傳該屬性作為攻擊方時對各防守屬性的倍率，以及作為防守方時受各攻擊屬性的倍率。")
    @GetMapping("/types/{typeName}/effectiveness")
    public ResponseEntity<Map<String, Object>> typeEffectiveness(
            @Parameter(description = "屬性英文名", example = "fire")
            @PathVariable String typeName) {
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
    @Operation(summary = "寶可夢防禦相性", description = "依該寶可夢的實際屬性組合，計算完整防禦相性（含 4 倍弱點、無效等）。")
    @GetMapping("/pokemon/{apiName}/matchup")
    public ResponseEntity<TypeMatchupService.DefensiveProfile> pokemonMatchup(
            @Parameter(description = "寶可夢 apiName", example = "charizard")
            @PathVariable String apiName) {
        return typeMatchupService.getDefensiveProfile(apiName)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** 查純屬性組合的防禦相性（不需指定寶可夢） */
    @Operation(summary = "純屬性防禦相性", description = "不指定寶可夢，僅依一或兩個屬性名稱計算該組合的防禦相性輪廓。")
    @GetMapping("/types/matchup")
    public ResponseEntity<TypeMatchupService.DefensiveProfile> typeMatchup(
            @Parameter(description = "第一屬性（必填）", example = "fire")
            @RequestParam String type1,
            @Parameter(description = "第二屬性（單屬性時可省略）", example = "flying")
            @RequestParam(required = false) String type2
    ) {
        return typeMatchupService.getDefensiveProfileByTypes(type1, type2)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ═══════════════════════════════════════════════════
    //  賽季寶可夢
    // ═══════════════════════════════════════════════════

    @Operation(summary = "賽季寶可夢清單", description = "回傳指定賽季 ID 下可使用的寶可夢摘要（含是否可 Mega 等）。")
    @GetMapping("/seasons/{seasonId}/pokemon")
    public ResponseEntity<List<Map<String, Object>>> seasonPokemon(
            @Parameter(description = "賽季主鍵 ID", example = "1")
            @PathVariable Long seasonId) {
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

    @Operation(summary = "賽季可 Mega 寶可夢", description = "回傳該賽季中標記為可 Mega 進化的寶可夢清單。")
    @GetMapping("/seasons/{seasonId}/pokemon/mega")
    public ResponseEntity<List<Map<String, Object>>> seasonMegaPokemon(
            @Parameter(description = "賽季主鍵 ID", example = "1")
            @PathVariable Long seasonId) {
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

    @Operation(summary = "同步招式主表", description = "從 Game8 招式列表頁爬取並 upsert 招式基本資料。")
    @PostMapping("/sync-moves")
    public ResponseEntity<MoveSyncService.MoveSyncReport> syncMoves() {
        return ResponseEntity.ok(moveSyncService.syncFromGame8());
    }

    /** 爬取每招的 Game8 詳情頁，同步 accuracy + 可學習寶可夢 */
    @Operation(summary = "同步招式詳情與可學習者", description = "逐招爬取 Game8 詳情頁，更新命中率，並建立／更新寶可夢與招式的關聯。")
    @PostMapping("/sync-pokemon-moves")
    public ResponseEntity<MoveSyncService.PokemonMoveSyncReport> syncPokemonMoves(
            @Parameter(description = "為 true 時強制重新爬取已處理過的招式")
            @RequestParam(defaultValue = "false") boolean forceRefresh
    ) {
        return ResponseEntity.ok(moveSyncService.syncPokemonMovesFromGame8(forceRefresh));
    }

    /** 用 OpenAI 批次翻譯招式描述為繁體中文 */
    @Operation(summary = "翻譯招式描述（繁中）", description = "使用設定的 Gemini 等服務，將招式英文描述批次翻成繁體中文。")
    @PostMapping("/translate-move-descriptions")
    public ResponseEntity<MoveSyncService.TranslationReport> translateMoveDescriptions(
            @Parameter(description = "為 true 時連已有中文描述的招式也重新翻譯")
            @RequestParam(defaultValue = "false") boolean forceRefresh
    ) {
        return ResponseEntity.ok(moveSyncService.translateDescriptions(forceRefresh));
    }

    /** 從 PokeAPI 同步招式多語名稱（中文 + 日文） */
    @Operation(summary = "同步招式多語名稱", description = "從 PokeAPI 取得招式繁體中文名與日文名並寫入資料庫。")
    @PostMapping("/sync-move-names")
    public ResponseEntity<MoveSyncService.LocalizedNameSyncReport> syncMoveLocalizedNames(
            @Parameter(description = "為 true 時連已有名稱的招式也重新抓取")
            @RequestParam(defaultValue = "false") boolean forceRefresh
    ) {
        return ResponseEntity.ok(moveSyncService.syncLocalizedNames(forceRefresh));
    }

    @Operation(summary = "列出全部招式", description = "回傳資料庫中所有招式實體。")
    @GetMapping("/moves")
    public ResponseEntity<List<Move>> listMoves() {
        return ResponseEntity.ok(moveRepository.findAll());
    }

    @Operation(summary = "單一招式詳情", description = "依招式 slug（小寫英文名）查詢單一招式。")
    @GetMapping("/moves/{name}")
    public ResponseEntity<Move> getMove(
            @Parameter(description = "招式 slug，如 flamethrower", example = "flamethrower")
            @PathVariable String name) {
        return moveRepository.findByName(name.toLowerCase())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "依屬性篩選招式", description = "回傳該屬性下的所有招式。")
    @GetMapping("/moves/type/{typeName}")
    public ResponseEntity<List<Move>> movesByType(
            @Parameter(description = "屬性英文名", example = "fire")
            @PathVariable String typeName) {
        return typeRepository.findByName(typeName.toLowerCase())
                .map(type -> ResponseEntity.ok(moveRepository.findByType(type)))
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "依招式分類篩選", description = "category 須為 PHYSICAL、SPECIAL 或 STATUS（不分大小寫）。")
    @GetMapping("/moves/category/{category}")
    public ResponseEntity<List<Move>> movesByCategory(
            @Parameter(description = "招式分類 enum 名稱", example = "SPECIAL")
            @PathVariable String category) {
        try {
            Move.MoveCategory cat = Move.MoveCategory.valueOf(category.toUpperCase());
            return ResponseEntity.ok(moveRepository.findByCategory(cat));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /** 查詢某寶可夢可學習的全部招式 */
    @Operation(summary = "寶可夢可學招式", description = "回傳該寶可夢在資料庫中已關聯的全部可學招式與來源、驗證狀態等。")
    @GetMapping("/pokemon/{apiName}/moves")
    public ResponseEntity<List<Map<String, Object>>> pokemonMoves(
            @Parameter(description = "寶可夢 apiName", example = "charizard")
            @PathVariable String apiName) {
        return pokemonRepository.findByApiName(apiName)
                .map(pokemon -> {
                    List<Map<String, Object>> moves = pokemonMoveRepository.findByPokemon(pokemon).stream()
                            .map(pm -> {
                                Map<String, Object> m = new LinkedHashMap<>();
                                Move move = pm.getMove();
                                m.put("name", move.getName());
                                m.put("displayName", move.getDisplayName());
                                m.put("chineseName", move.getChineseName());
                                m.put("japaneseName", move.getJapaneseName());
                                m.put("type", move.getType().getName());
                                m.put("typeChinese", move.getType().getChineseName());
                                m.put("typeJapanese", move.getType().getJapaneseName());
                                m.put("category", move.getCategory().name());
                                m.put("power", move.getPower());
                                m.put("accuracy", move.getAccuracy());
                                m.put("pp", move.getPp());
                                m.put("description", move.getDescription());
                                m.put("chineseDescription", move.getChineseDescription());
                                m.put("japaneseDescription", move.getJapaneseDescription());
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
    @Operation(summary = "可學習某招式的寶可夢", description = "依招式 slug 回傳可學習該招式的寶可夢清單。")
    @GetMapping("/moves/{name}/pokemon")
    public ResponseEntity<List<Map<String, Object>>> moveLearners(
            @Parameter(description = "招式 slug", example = "earthquake")
            @PathVariable String name) {
        return moveRepository.findByName(name.toLowerCase())
                .map(move -> {
                    List<Map<String, Object>> pokemon = pokemonMoveRepository.findByMove(move).stream()
                            .map(pm -> {
                                Map<String, Object> m = new LinkedHashMap<>();
                                Pokemon p = pm.getPokemon();
                                m.put("apiName", p.getApiName());
                                m.put("displayName", p.getDisplayName());
                                m.put("chineseName", p.getChineseName());
                                m.put("japaneseName", p.getJapaneseName());
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

    @Operation(summary = "寶可夢中英文對照", description = "回傳已有中文名的寶可夢之 apiName、英文名、中文名與是否 Mega。")
    @GetMapping("/name-mapping")
    public ResponseEntity<List<Map<String, String>>> nameMapping() {
        return ResponseEntity.ok(
                pokemonRepository.findByChineseNameIsNotNull().stream()
                        .map(p -> {
                            Map<String, String> m = new LinkedHashMap<>();
                            m.put("apiName", p.getApiName());
                            m.put("englishName", p.getDisplayName());
                            m.put("chineseName", p.getChineseName());
                            m.put("japaneseName", p.getJapaneseName());
                            m.put("mega", String.valueOf(p.isMega()));
                            return m;
                        })
                        .collect(Collectors.toList())
        );
    }

    // ═══════════════════════════════════════════════════
    //  持有物品
    // ═══════════════════════════════════════════════════

    @Operation(summary = "同步持有物品", description = "從 Game8 Pokémon Champions 物品列表頁爬取並 upsert 持有物品資料。")
    @PostMapping("/sync-items")
    public ResponseEntity<Map<String, Object>> syncItems() {
        return ResponseEntity.ok(itemSyncService.syncFromGame8());
    }

    @Operation(summary = "同步道具繁中名稱（PokeAPI）", description = "從 PokeAPI /item 讀取 zh-hant（無則 zh-hans）官方本地化名稱，寫入 chineseName。與主系列官方譯名一致。")
    @PostMapping("/sync-item-chinese-names")
    public ResponseEntity<ItemSyncService.ItemChineseNameSyncReport> syncItemChineseNames(
            @Parameter(description = "為 true 時連已有中文名的道具也重新從 PokeAPI 抓取")
            @RequestParam(defaultValue = "false") boolean forceRefresh
    ) {
        return ResponseEntity.ok(itemSyncService.syncChineseNamesFromPokeApi(forceRefresh));
    }

    @Operation(summary = "翻譯物品名稱（繁中，已改為 PokeAPI）", description = "與 POST /sync-item-chinese-names 相同；保留路徑供舊文件／腳本相容。")
    @PostMapping("/translate-item-names")
    public ResponseEntity<ItemSyncService.ItemChineseNameSyncReport> translateItemNames(
            @Parameter(description = "為 true 時連已有中文名的道具也重新從 PokeAPI 抓取")
            @RequestParam(defaultValue = "false") boolean forceRefresh
    ) {
        return ResponseEntity.ok(itemSyncService.syncChineseNamesFromPokeApi(forceRefresh));
    }

    @Operation(summary = "翻譯物品效果（繁中）", description = "使用 Gemini 將物品英文效果描述翻成繁體中文。")
    @PostMapping("/translate-item-effects")
    public ResponseEntity<ItemSyncService.TranslationReport> translateItemEffects(
            @Parameter(description = "為 true 時連已有中文效果描述的物品也重新翻譯")
            @RequestParam(defaultValue = "false") boolean forceRefresh
    ) {
        return ResponseEntity.ok(itemSyncService.translateEffects(forceRefresh));
    }

    @Operation(summary = "列出全部持有物品", description = "回傳資料庫中所有持有物品（含分類、取得方式、效果、Game8 連結等）。")
    @GetMapping("/items")
    public ResponseEntity<List<HeldItem>> listItems() {
        return ResponseEntity.ok(heldItemRepository.findAll());
    }

    @Operation(summary = "單一持有物品", description = "依物品 slug（小寫、連字號）查詢單一道具。")
    @GetMapping("/items/{name}")
    public ResponseEntity<HeldItem> getItem(
            @Parameter(description = "物品 slug，如 focus-sash", example = "focus-sash")
            @PathVariable String name) {
        return heldItemRepository.findByName(name.toLowerCase())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "依物品分類篩選", description = "category 須為 DEFENSE、RECOVERY、POWER_BOOST、STAT_BOOST、MEGA_STONE、OTHER 之一（不分大小寫）。")
    @GetMapping("/items/category/{category}")
    public ResponseEntity<List<HeldItem>> itemsByCategory(
            @Parameter(description = "ItemCategory enum 名稱", example = "MEGA_STONE")
            @PathVariable String category) {
        try {
            HeldItem.ItemCategory cat = HeldItem.ItemCategory.valueOf(category.toUpperCase());
            return ResponseEntity.ok(heldItemRepository.findByCategory(cat));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // ═══════════════════════════════════════════════════
    //  特性
    // ═══════════════════════════════════════════════════

    @Operation(summary = "同步寶可夢日文名稱", description = "從 PokeAPI /pokemon-species 取得日文名寫入 japaneseName。")
    @PostMapping("/sync-pokemon-japanese-names")
    public ResponseEntity<PokemonSyncService.SyncReport> syncPokemonJapaneseNames(
            @Parameter(description = "為 true 時連已有日文名的寶可夢也重新抓取")
            @RequestParam(defaultValue = "false") boolean forceRefresh
    ) {
        return ResponseEntity.ok(pokemonSyncService.syncJapaneseNames(forceRefresh));
    }

    @Operation(summary = "同步寶可夢特性", description = "從 PokeAPI 為所有寶可夢同步特性槽位。forceRefresh=true 時會覆寫既有列，用於補齊先前不完整同步。")
    @PostMapping("/sync-abilities")
    public ResponseEntity<PokemonSyncService.SyncReport> syncAbilities(
            @Parameter(description = "為 true 時連已有特性列的寶可夢也從 PokeAPI 重寫")
            @RequestParam(defaultValue = "false") boolean forceRefresh
    ) {
        return ResponseEntity.ok(pokemonSyncService.syncAbilities(forceRefresh));
    }

    @Operation(summary = "同步特性詳情", description = "從 PokeAPI 取得所有已登錄特性的中文名稱與描述。")
    @PostMapping("/sync-ability-details")
    public ResponseEntity<AbilitySyncService.SyncReport> syncAbilityDetails(
            @Parameter(description = "為 true 時連已同步過的特性也重新抓取")
            @RequestParam(defaultValue = "false") boolean forceRefresh
    ) {
        return ResponseEntity.ok(abilitySyncService.syncAbilityDetails(forceRefresh));
    }

    @Operation(summary = "列出全部特性", description = "回傳資料庫中所有已登錄的特性。")
    @GetMapping("/abilities")
    public ResponseEntity<List<Ability>> listAbilities() {
        return ResponseEntity.ok(abilityRepository.findAll());
    }

    @Operation(summary = "單一特性詳情", description = "依特性 slug（小寫英文名）查詢單一特性。")
    @GetMapping("/abilities/{name}")
    public ResponseEntity<Ability> getAbility(
            @Parameter(description = "特性 slug，如 overgrow", example = "overgrow")
            @PathVariable String name) {
        return abilityRepository.findByName(name.toLowerCase())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "寶可夢特性", description = "回傳該寶可夢的特性列表（含隱藏特性標記）。")
    @GetMapping("/pokemon/{apiName}/abilities")
    public ResponseEntity<List<Map<String, Object>>> pokemonAbilities(
            @Parameter(description = "寶可夢 apiName", example = "charizard")
            @PathVariable String apiName) {
        return pokemonRepository.findByApiName(apiName)
                .map(pokemon -> {
                    List<Map<String, Object>> abilities = pokemonAbilityRepository
                            .findByPokemonOrderBySlot(pokemon).stream()
                            .map(pa -> {
                                Map<String, Object> m = new LinkedHashMap<>();
                                Ability a = pa.getAbility();
                                m.put("name", a.getName());
                                m.put("displayName", a.getDisplayName());
                                m.put("chineseName", a.getChineseName());
                                m.put("japaneseName", a.getJapaneseName());
                                m.put("description", a.getDescription());
                                m.put("chineseDescription", a.getChineseDescription());
                                m.put("japaneseDescription", a.getJapaneseDescription());
                                m.put("slot", pa.getSlot());
                                m.put("hidden", pa.isHidden());
                                return m;
                            })
                            .collect(Collectors.toList());
                    return ResponseEntity.ok(abilities);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
