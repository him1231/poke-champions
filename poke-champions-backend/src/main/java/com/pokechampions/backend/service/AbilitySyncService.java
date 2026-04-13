package com.pokechampions.backend.service;

import com.pokechampions.backend.entity.Ability;
import com.pokechampions.backend.repository.AbilityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
public class AbilitySyncService {

    private static final Logger log = LoggerFactory.getLogger(AbilitySyncService.class);
    private static final String POKEAPI_ABILITY = "https://pokeapi.co/api/v2/ability/";

    private final AbilityRepository abilityRepository;
    private final RestClient restClient;

    public AbilitySyncService(AbilityRepository abilityRepository) {
        this.abilityRepository = abilityRepository;
        this.restClient = RestClient.builder()
                .baseUrl(POKEAPI_ABILITY)
                .build();
    }

    @Transactional
    public SyncReport syncAbilityDetails(boolean forceRefresh) {
        List<Ability> targets = forceRefresh
                ? abilityRepository.findAll()
                : abilityRepository.findByDetailSyncedFalse();

        log.info("========== AbilitySyncService: 開始同步 {} 個特性的詳情 ==========", targets.size());
        int success = 0, failed = 0;

        for (Ability ability : targets) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> body = restClient.get()
                        .uri(ability.getName())
                        .retrieve()
                        .body(Map.class);

                if (body == null) {
                    log.warn("  ⚠ {} 回傳 null", ability.getName());
                    failed++;
                    continue;
                }

                applyDetails(ability, body);
                abilityRepository.save(ability);
                success++;
                log.info("  ✓ {} → {}", ability.getName(),
                        ability.getChineseName() != null ? ability.getChineseName() : "(no zh name)");

            } catch (Exception e) {
                log.warn("  ✗ {} 同步失敗: {}", ability.getName(), e.getMessage());
                failed++;
            }
        }

        log.info("========== 特性詳情同步完成: 成功 {}, 失敗 {}, 共 {} ==========", success, failed, targets.size());
        return new SyncReport(targets.size(), success, failed);
    }

    @SuppressWarnings("unchecked")
    private void applyDetails(Ability ability, Map<String, Object> body) {
        List<Map<String, Object>> names = (List<Map<String, Object>>) body.get("names");
        if (names != null) {
            String zhName = findLocalizedName(names, "zh-hant");
            if (zhName == null) zhName = findLocalizedName(names, "zh-hans");
            if (zhName != null) ability.setChineseName(zhName);

            String jaName = findLocalizedName(names, "ja");
            if (jaName != null) ability.setJapaneseName(jaName);
        }

        List<Map<String, Object>> effectEntries = (List<Map<String, Object>>) body.get("effect_entries");
        if (effectEntries != null) {
            String enEffect = findEffectText(effectEntries, "en");
            if (enEffect != null) ability.setDescription(truncate(enEffect, 1000));
        }

        List<Map<String, Object>> flavorEntries = (List<Map<String, Object>>) body.get("flavor_text_entries");
        if (flavorEntries != null) {
            String zhFlavor = findFlavorText(flavorEntries, "zh-hant");
            if (zhFlavor == null) zhFlavor = findFlavorText(flavorEntries, "zh-hans");
            if (zhFlavor != null) ability.setChineseDescription(truncate(zhFlavor, 1000));

            String jaFlavor = findFlavorText(flavorEntries, "ja");
            if (jaFlavor != null) ability.setJapaneseDescription(truncate(jaFlavor, 1000));
        }

        ability.setDetailSynced(true);
    }

    @SuppressWarnings("unchecked")
    private String findLocalizedName(List<Map<String, Object>> names, String lang) {
        for (Map<String, Object> entry : names) {
            Map<String, Object> language = (Map<String, Object>) entry.get("language");
            if (lang.equals(language.get("name"))) {
                return (String) entry.get("name");
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String findEffectText(List<Map<String, Object>> entries, String lang) {
        for (Map<String, Object> entry : entries) {
            Map<String, Object> language = (Map<String, Object>) entry.get("language");
            if (lang.equals(language.get("name"))) {
                String text = (String) entry.get("short_effect");
                if (text == null || text.isBlank()) text = (String) entry.get("effect");
                return text;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String findFlavorText(List<Map<String, Object>> entries, String lang) {
        String latest = null;
        for (Map<String, Object> entry : entries) {
            Map<String, Object> language = (Map<String, Object>) entry.get("language");
            if (lang.equals(language.get("name"))) {
                latest = (String) entry.get("flavor_text");
            }
        }
        return latest != null ? latest.replaceAll("\\s+", " ").trim() : null;
    }

    private String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) : s;
    }

    public record SyncReport(int total, int success, int failed) {}
}
