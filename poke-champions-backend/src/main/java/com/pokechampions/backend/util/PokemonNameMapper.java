package com.pokechampions.backend.util;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 中文寶可夢名稱 → PokeAPI 英文 slug 映射。
 * <p>
 * 支援兩種格式：
 * <ul>
 *   <li>超級進化形態：「超級妙蛙花」→ "venusaur-mega"</li>
 *   <li>基礎形態：「妙蛙花」→ "venusaur"</li>
 * </ul>
 * 英文版直接輸入如 "Mega Venusaur" 也能對應。
 */
public final class PokemonNameMapper {

    private static final Map<String, String> ZH_TO_API;
    private static final Map<String, String> EN_TO_API;

    static {
        Map<String, String> zh = new LinkedHashMap<>();

        // pokemon-champions 賽制 M-A 完整超級進化名單 (59隻)
        // Gen 1
        zh.put("超級妙蛙花", "venusaur-mega");
        zh.put("超級噴火龍Ｘ", "charizard-mega-x");
        zh.put("超級噴火龍Ｙ", "charizard-mega-y");
        zh.put("超級水箭龜", "blastoise-mega");
        zh.put("超級大針蜂", "beedrill-mega");
        zh.put("超級大比鳥", "pidgeot-mega");
        zh.put("超級皮可西", "clefable-mega");
        zh.put("超級胡地", "alakazam-mega");
        zh.put("超級大食花", "victreebel-mega");
        zh.put("超級呆殼獸", "slowbro-mega");
        zh.put("超級耿鬼", "gengar-mega");
        zh.put("超級袋獸", "kangaskhan-mega");
        zh.put("超級寶石海星", "starmie-mega");
        zh.put("超級凱羅斯", "pinsir-mega");
        zh.put("超級暴鯉龍", "gyarados-mega");
        zh.put("超級化石翼龍", "aerodactyl-mega");
        zh.put("超級快龍", "dragonite-mega");

        // Gen 2
        zh.put("超級大竺葵", "meganium-mega");
        zh.put("超級大力鱷", "feraligatr-mega");
        zh.put("超級電龍", "ampharos-mega");
        zh.put("超級大鋼蛇", "steelix-mega");
        zh.put("超級巨鉗螳螂", "scizor-mega");
        zh.put("超級赫拉克羅斯", "heracross-mega");
        zh.put("超級盔甲鳥", "skarmory-mega");
        zh.put("超級黑魯加", "houndoom-mega");
        zh.put("超級班基拉斯", "tyranitar-mega");

        // Gen 3
        zh.put("超級沙奈朵", "gardevoir-mega");
        zh.put("超級勾魂眼", "sableye-mega");
        zh.put("超級波士可多拉", "aggron-mega");
        zh.put("超級恰雷姆", "medicham-mega");
        zh.put("超級雷電獸", "manectric-mega");
        zh.put("超級巨牙鯊", "sharpedo-mega");
        zh.put("超級噴火駝", "camerupt-mega");
        zh.put("超級七夕青鳥", "altaria-mega");
        zh.put("超級詛咒娃娃", "banette-mega");
        zh.put("超級風鈴鈴", "chimecho-mega");
        zh.put("超級阿勃梭魯", "absol-mega");
        zh.put("超級冰鬼護", "glalie-mega");

        // Gen 4
        zh.put("超級長耳兔", "lopunny-mega");
        zh.put("超級烈咬陸鯊", "garchomp-mega");
        zh.put("超級路卡利歐", "lucario-mega");
        zh.put("超級暴雪王", "abomasnow-mega");
        zh.put("超級艾路雷朵", "gallade-mega");
        zh.put("超級雪妖女", "froslass-mega");

        // Gen 5
        zh.put("超級炎武王", "emboar-mega");
        zh.put("超級龍頭地鼠", "excadrill-mega");
        zh.put("超級差不多娃娃", "audino-mega");
        zh.put("超級水晶燈火靈", "chandelure-mega");
        zh.put("超級泥偶巨人", "golurk-mega");

        // Gen 6
        zh.put("超級布里卡隆", "chesnaught-mega");
        zh.put("超級妖火紅狐", "delphox-mega");
        zh.put("超級甲賀忍蛙", "greninja-mega");
        zh.put("超級花葉蒂", "floette-mega");
        zh.put("超級超能妙喵", "meowstic-mega");
        zh.put("超級摔角鷹人", "hawlucha-mega");

        // Gen 7
        zh.put("超級好勝毛蟹", "crabominable-mega");
        zh.put("超級老翁龍", "drampa-mega");

        // Gen 9
        zh.put("超級狠辣椒", "scovillain-mega");
        zh.put("超級晶光花", "glimmora-mega");

        // 基礎形態（用於一般可參賽名單）
        zh.put("妙蛙花", "venusaur");
        zh.put("噴火龍", "charizard");
        zh.put("水箭龜", "blastoise");
        zh.put("大針蜂", "beedrill");
        zh.put("大比鳥", "pidgeot");
        zh.put("皮可西", "clefable");
        zh.put("胡地", "alakazam");
        zh.put("大食花", "victreebel");
        zh.put("呆殼獸", "slowbro");
        zh.put("耿鬼", "gengar");
        zh.put("袋獸", "kangaskhan");
        zh.put("寶石海星", "starmie");
        zh.put("凱羅斯", "pinsir");
        zh.put("暴鯉龍", "gyarados");
        zh.put("化石翼龍", "aerodactyl");
        zh.put("快龍", "dragonite");
        zh.put("大竺葵", "meganium");
        zh.put("大力鱷", "feraligatr");
        zh.put("電龍", "ampharos");
        zh.put("大鋼蛇", "steelix");
        zh.put("巨鉗螳螂", "scizor");
        zh.put("赫拉克羅斯", "heracross");
        zh.put("盔甲鳥", "skarmory");
        zh.put("黑魯加", "houndoom");
        zh.put("班基拉斯", "tyranitar");
        zh.put("沙奈朵", "gardevoir");
        zh.put("勾魂眼", "sableye");
        zh.put("波士可多拉", "aggron");
        zh.put("恰雷姆", "medicham");
        zh.put("雷電獸", "manectric");
        zh.put("巨牙鯊", "sharpedo");
        zh.put("噴火駝", "camerupt");
        zh.put("七夕青鳥", "altaria");
        zh.put("詛咒娃娃", "banette");
        zh.put("風鈴鈴", "chimecho");
        zh.put("阿勃梭魯", "absol");
        zh.put("冰鬼護", "glalie");
        zh.put("長耳兔", "lopunny");
        zh.put("烈咬陸鯊", "garchomp");
        zh.put("路卡利歐", "lucario");
        zh.put("暴雪王", "abomasnow");
        zh.put("艾路雷朵", "gallade");
        zh.put("雪妖女", "froslass");
        zh.put("炎武王", "emboar");
        zh.put("龍頭地鼠", "excadrill");
        zh.put("差不多娃娃", "audino");
        zh.put("水晶燈火靈", "chandelure");
        zh.put("泥偶巨人", "golurk");
        zh.put("布里卡隆", "chesnaught");
        zh.put("妖火紅狐", "delphox");
        zh.put("甲賀忍蛙", "greninja");
        zh.put("花葉蒂", "floette");
        zh.put("超能妙喵", "meowstic");
        zh.put("摔角鷹人", "hawlucha");
        zh.put("好勝毛蟹", "crabominable");
        zh.put("老翁龍", "drampa");
        zh.put("狠辣椒", "scovillain");
        zh.put("晶光花", "glimmora");

        ZH_TO_API = Collections.unmodifiableMap(zh);

        // 英文版映射（"Mega Venusaur" → "venusaur-mega"）
        Map<String, String> en = new LinkedHashMap<>();
        en.put("Mega Venusaur", "venusaur-mega");
        en.put("Mega Charizard X", "charizard-mega-x");
        en.put("Mega Charizard Y", "charizard-mega-y");
        en.put("Mega Blastoise", "blastoise-mega");
        en.put("Mega Beedrill", "beedrill-mega");
        en.put("Mega Pidgeot", "pidgeot-mega");
        en.put("Mega Clefable", "clefable-mega");
        en.put("Mega Alakazam", "alakazam-mega");
        en.put("Mega Victreebel", "victreebel-mega");
        en.put("Mega Slowbro", "slowbro-mega");
        en.put("Mega Gengar", "gengar-mega");
        en.put("Mega Kangaskhan", "kangaskhan-mega");
        en.put("Mega Starmie", "starmie-mega");
        en.put("Mega Pinsir", "pinsir-mega");
        en.put("Mega Gyarados", "gyarados-mega");
        en.put("Mega Aerodactyl", "aerodactyl-mega");
        en.put("Mega Dragonite", "dragonite-mega");
        en.put("Mega Meganium", "meganium-mega");
        en.put("Mega Feraligatr", "feraligatr-mega");
        en.put("Mega Ampharos", "ampharos-mega");
        en.put("Mega Steelix", "steelix-mega");
        en.put("Mega Scizor", "scizor-mega");
        en.put("Mega Heracross", "heracross-mega");
        en.put("Mega Skarmory", "skarmory-mega");
        en.put("Mega Houndoom", "houndoom-mega");
        en.put("Mega Tyranitar", "tyranitar-mega");
        en.put("Mega Gardevoir", "gardevoir-mega");
        en.put("Mega Sableye", "sableye-mega");
        en.put("Mega Aggron", "aggron-mega");
        en.put("Mega Medicham", "medicham-mega");
        en.put("Mega Manectric", "manectric-mega");
        en.put("Mega Sharpedo", "sharpedo-mega");
        en.put("Mega Camerupt", "camerupt-mega");
        en.put("Mega Altaria", "altaria-mega");
        en.put("Mega Banette", "banette-mega");
        en.put("Mega Chimecho", "chimecho-mega");
        en.put("Mega Absol", "absol-mega");
        en.put("Mega Glalie", "glalie-mega");
        en.put("Mega Lopunny", "lopunny-mega");
        en.put("Mega Garchomp", "garchomp-mega");
        en.put("Mega Lucario", "lucario-mega");
        en.put("Mega Abomasnow", "abomasnow-mega");
        en.put("Mega Gallade", "gallade-mega");
        en.put("Mega Froslass", "froslass-mega");
        en.put("Mega Emboar", "emboar-mega");
        en.put("Mega Excadrill", "excadrill-mega");
        en.put("Mega Audino", "audino-mega");
        en.put("Mega Chandelure", "chandelure-mega");
        en.put("Mega Golurk", "golurk-mega");
        en.put("Mega Chesnaught", "chesnaught-mega");
        en.put("Mega Delphox", "delphox-mega");
        en.put("Mega Greninja", "greninja-mega");
        en.put("Mega Floette", "floette-mega");
        en.put("Mega Meowstic", "meowstic-mega");
        en.put("Mega Hawlucha", "hawlucha-mega");
        en.put("Mega Crabominable", "crabominable-mega");
        en.put("Mega Drampa", "drampa-mega");
        en.put("Mega Scovillain", "scovillain-mega");
        en.put("Mega Glimmora", "glimmora-mega");

        EN_TO_API = Collections.unmodifiableMap(en);
    }

    private PokemonNameMapper() {}

    public static Map<String, String> getZhMap() {
        return ZH_TO_API;
    }

    public static Map<String, String> getEnMap() {
        return EN_TO_API;
    }

    /**
     * 根據語言自動選擇映射表，將顯示名稱轉為 PokeAPI slug。
     * @param displayName 頁面上顯示的名稱（中文或英文）
     * @param lang "tc" 或 "en"
     * @return PokeAPI slug，若找不到則回傳 null
     */
    public static String toApiName(String displayName, String lang) {
        if (displayName == null) return null;
        String trimmed = displayName.trim();
        if ("en".equalsIgnoreCase(lang)) {
            return EN_TO_API.get(trimmed);
        }
        return ZH_TO_API.get(trimmed);
    }

    /**
     * 嘗試所有映射表，自動偵測語言。
     */
    public static String toApiName(String displayName) {
        if (displayName == null) return null;
        String trimmed = displayName.trim();
        String result = ZH_TO_API.get(trimmed);
        if (result != null) return result;
        return EN_TO_API.get(trimmed);
    }
}
