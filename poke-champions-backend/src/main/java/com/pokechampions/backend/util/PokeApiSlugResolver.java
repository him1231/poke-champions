package com.pokechampions.backend.util;

import java.util.Map;

/**
 * 將官方頁面的英文顯示名稱轉成 PokeAPI 可查詢的 slug。
 * <p>
 * 規則：
 * <ol>
 *   <li>基礎形態："Venusaur" → "venusaur"</li>
 *   <li>地區形態："Raichu (Alolan Form)" → "raichu-alola"</li>
 *   <li>Paldean Tauros breed："Tauros (Paldean Form (Blaze Breed))" → "tauros-paldea-blaze"</li>
 *   <li>Rotom 形態："Rotom (Wash Rotom)" → "rotom-wash"</li>
 *   <li>Meowstic 性別："Meowstic (Male)" → "meowstic-male"</li>
 *   <li>Gourgeist 大小："Gourgeist (Small Variety)" → "gourgeist-small"</li>
 *   <li>Lycanroc 形態："Lycanroc (Dusk Form)" → "lycanroc-dusk"</li>
 *   <li>Basculegion 性別："Basculegion (Male)" → "basculegion-male"</li>
 *   <li>超級進化由 PokemonNameMapper 處理</li>
 * </ol>
 */
public final class PokeApiSlugResolver {

    private static final Map<String, String> REGIONAL_TAG = Map.of(
            "Alolan", "alola",
            "Galarian", "galar",
            "Hisuian", "hisui",
            "Paldean", "paldea"
    );

    private static final Map<String, String> SPECIAL_OVERRIDES = Map.ofEntries(
            Map.entry("Mr. Rime", "mr-rime"),
            Map.entry("Kommo-o", "kommo-o"),
            Map.entry("Mime Jr.", "mime-jr"),
            Map.entry("Mr. Mime", "mr-mime"),
            Map.entry("Type: Null", "type-null"),
            Map.entry("Vivillon", "vivillon"),
            Map.entry("Floette", "floette"),
            Map.entry("Sinistcha", "sinistcha"),
            Map.entry("Aegislash", "aegislash-shield"),
            Map.entry("Mimikyu", "mimikyu-disguised"),
            Map.entry("Morpeko", "morpeko-full-belly"),
            Map.entry("Maushold", "maushold-family-of-four"),
            Map.entry("Palafin", "palafin-zero")
    );

    private PokeApiSlugResolver() {}

    /**
     * 將英文顯示名轉為 PokeAPI slug。
     * @param displayName 英文名，如 "Raichu (Alolan Form)"
     * @return PokeAPI slug，如 "raichu-alola"
     */
    public static String resolve(String displayName) {
        if (displayName == null || displayName.isBlank()) return null;
        String trimmed = displayName.trim();

        // 特殊名稱覆蓋
        for (var entry : SPECIAL_OVERRIDES.entrySet()) {
            if (trimmed.startsWith(entry.getKey()) && !trimmed.contains("(")) {
                return entry.getValue();
            }
        }

        int parenStart = trimmed.indexOf('(');
        if (parenStart == -1) {
            return slugify(trimmed);
        }

        String baseName = trimmed.substring(0, parenStart).trim();
        String baseSlug = SPECIAL_OVERRIDES.getOrDefault(baseName, slugify(baseName));
        String parenContent = trimmed.substring(parenStart + 1, trimmed.lastIndexOf(')')).trim();

        // 地區形態：含 "Alolan/Galarian/Hisuian/Paldean"
        for (var entry : REGIONAL_TAG.entrySet()) {
            if (parenContent.contains(entry.getKey())) {
                // Paldean Tauros 子品種：PokeAPI 格式為 tauros-paldea-{breed}-breed
                if (entry.getKey().equals("Paldean") && parenContent.contains("Breed")) {
                    String breed = extractBreedType(parenContent);
                    return baseSlug + "-paldea-" + breed + "-breed";
                }
                return baseSlug + "-" + entry.getValue();
            }
        }

        // Rotom 形態："Rotom (Heat Rotom)" → "rotom-heat"；"Rotom (Rotom)" → "rotom"
        if (baseName.equalsIgnoreCase("Rotom")) {
            if (parenContent.equalsIgnoreCase("Rotom")) return "rotom";
            String form = parenContent.replace("Rotom", "").trim().toLowerCase();
            return "rotom-" + form;
        }

        // Meowstic 性別
        if (baseName.equalsIgnoreCase("Meowstic")) {
            return parenContent.toLowerCase().contains("female") ? "meowstic-female" : "meowstic-male";
        }

        // Basculegion 性別
        if (baseName.equalsIgnoreCase("Basculegion")) {
            return parenContent.toLowerCase().contains("female") ? "basculegion-female" : "basculegion-male";
        }

        // Aegislash：Game8 等來源常寫「Aegislash (Shield Forme)」，不可走通用 fallback（會變成 aegislash-shield-shield）
        if (baseName.equalsIgnoreCase("Aegislash")) {
            String lower = parenContent.toLowerCase();
            if (lower.contains("blade") || lower.contains("attack")) return "aegislash-blade";
            if (lower.contains("shield") || lower.contains("defense")) return "aegislash-shield";
            return "aegislash-shield";
        }

        // Lycanroc 形態
        if (baseName.equalsIgnoreCase("Lycanroc")) {
            String form = parenContent.replace("Form", "").trim().toLowerCase();
            return "lycanroc-" + form;
        }

        // Gourgeist 大小：PokeAPI 用 average/small/large/super
        if (baseName.equalsIgnoreCase("Gourgeist")) {
            String size = parenContent.replace("Variety", "").trim().toLowerCase();
            return switch (size) {
                case "medium" -> "gourgeist-average";
                case "jumbo" -> "gourgeist-super";
                default -> "gourgeist-" + size;
            };
        }

        // 通用 fallback：取括號內第一個單字
        String firstWord = parenContent.split("\\s+")[0].toLowerCase();
        return baseSlug + "-" + firstWord;
    }

    private static String extractBreedType(String parenContent) {
        // "Paldean Form (Blaze Breed)" → "blaze"
        int breedStart = parenContent.lastIndexOf('(');
        if (breedStart == -1) return "combat";
        String inner = parenContent.substring(breedStart + 1).replace("Breed", "").replace(")", "").trim();
        return inner.toLowerCase();
    }

    private static String slugify(String name) {
        return name.toLowerCase()
                .replace("'", "")
                .replace("'", "")
                .replace(". ", "-")
                .replace(".", "")
                .replaceAll("[^a-z0-9-]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }
}
