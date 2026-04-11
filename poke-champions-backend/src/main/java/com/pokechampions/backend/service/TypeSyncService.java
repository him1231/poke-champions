package com.pokechampions.backend.service;

import com.pokechampions.backend.entity.PokemonType;
import com.pokechampions.backend.entity.TypeEffectiveness;
import com.pokechampions.backend.repository.PokemonTypeRepository;
import com.pokechampions.backend.repository.TypeEffectivenessRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class TypeSyncService {

    private static final Logger log = LoggerFactory.getLogger(TypeSyncService.class);
    private static final String POKEAPI_TYPE_URL = "https://pokeapi.co/api/v2/type/";

    private final PokemonTypeRepository typeRepository;
    private final TypeEffectivenessRepository effectivenessRepository;
    private final RestClient restClient;

    public TypeSyncService(PokemonTypeRepository typeRepository,
                           TypeEffectivenessRepository effectivenessRepository) {
        this.typeRepository = typeRepository;
        this.effectivenessRepository = effectivenessRepository;
        this.restClient = RestClient.builder().baseUrl(POKEAPI_TYPE_URL).build();
    }

    /**
     * 從 PokeAPI 抓取全部 18 種屬性的相剋資料，存入 type_effectiveness 表。
     * 只存不等於 1.0 的倍率（0x, 0.5x, 2x），一般效果 (1x) 不存以節省空間。
     */
    public SyncReport syncTypeEffectiveness() {
        List<PokemonType> allTypes = typeRepository.findAll();
        if (allTypes.size() < 18) {
            throw new IllegalStateException("屬性資料尚未初始化，請先啟動應用讓 TypeDataInitializer 執行");
        }

        log.info("========== TypeSyncService: 開始同步屬性相剋 ==========");
        int created = 0;
        int skipped = 0;
        int failed = 0;

        for (PokemonType attackingType : allTypes) {
            try {
                log.info("  查詢 PokeAPI: type/{}", attackingType.getName());

                @SuppressWarnings("unchecked")
                Map<String, Object> body = restClient.get()
                        .uri(attackingType.getName())
                        .retrieve()
                        .body(Map.class);

                if (body == null) {
                    log.warn("  ⚠ {} 回傳 null", attackingType.getName());
                    failed++;
                    continue;
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> damageRelations = (Map<String, Object>) body.get("damage_relations");

                created += processDamageList(attackingType, damageRelations, "double_damage_to", 2.0);
                created += processDamageList(attackingType, damageRelations, "half_damage_to", 0.5);
                created += processDamageList(attackingType, damageRelations, "no_damage_to", 0.0);

            } catch (Exception e) {
                log.warn("  ✗ {} 查詢失敗: {}", attackingType.getName(), e.getMessage());
                failed++;
            }
        }

        log.info("========== 屬性相剋同步完成: 新增 {}, 失敗 {} ==========", created, failed);
        return new SyncReport(created, skipped, failed);
    }

    @SuppressWarnings("unchecked")
    private int processDamageList(PokemonType attackingType, Map<String, Object> damageRelations,
                                  String key, double multiplier) {
        List<Map<String, String>> targets = (List<Map<String, String>>) damageRelations.get(key);
        int count = 0;
        for (Map<String, String> target : targets) {
            String defName = target.get("name");
            Optional<PokemonType> defType = typeRepository.findByName(defName);
            if (defType.isEmpty()) {
                log.warn("    ⚠ 找不到屬性: {}", defName);
                continue;
            }

            Optional<TypeEffectiveness> existing =
                    effectivenessRepository.findByAttackingTypeAndDefendingType(attackingType, defType.get());
            if (existing.isPresent()) {
                TypeEffectiveness te = existing.get();
                if (te.getMultiplier() != multiplier) {
                    te.setMultiplier(multiplier);
                    effectivenessRepository.save(te);
                }
                continue;
            }

            effectivenessRepository.save(new TypeEffectiveness(attackingType, defType.get(), multiplier));
            log.debug("    {} → {} = {}x", attackingType.getName(), defName, multiplier);
            count++;
        }
        return count;
    }

    public record SyncReport(int created, int skipped, int failed) {}
}
