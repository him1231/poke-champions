package com.pokechampions.backend.config;

import com.pokechampions.backend.entity.PokemonType;
import com.pokechampions.backend.repository.PokemonTypeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class TypeDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TypeDataInitializer.class);

    private static final String[][] TYPES = {
            {"normal",   "一般",   "ノーマル"},
            {"fire",     "火",     "ほのお"},
            {"water",    "水",     "みず"},
            {"electric", "電",     "でんき"},
            {"grass",    "草",     "くさ"},
            {"ice",      "冰",     "こおり"},
            {"fighting", "格鬥",   "かくとう"},
            {"poison",   "毒",     "どく"},
            {"ground",   "地面",   "じめん"},
            {"flying",   "飛行",   "ひこう"},
            {"psychic",  "超能力", "エスパー"},
            {"bug",      "蟲",     "むし"},
            {"rock",     "岩石",   "いわ"},
            {"ghost",    "幽靈",   "ゴースト"},
            {"dragon",   "龍",     "ドラゴン"},
            {"dark",     "惡",     "あく"},
            {"steel",    "鋼",     "はがね"},
            {"fairy",    "妖精",   "フェアリー"},
    };

    private final PokemonTypeRepository typeRepository;

    public TypeDataInitializer(PokemonTypeRepository typeRepository) {
        this.typeRepository = typeRepository;
    }

    @Override
    public void run(String... args) {
        log.info("========== 初始化 / 更新 18 種屬性資料 ==========");
        for (String[] type : TYPES) {
            PokemonType existing = typeRepository.findByName(type[0]).orElse(null);
            if (existing != null) {
                if (existing.getJapaneseName() == null || existing.getJapaneseName().isBlank()) {
                    existing.setJapaneseName(type[2]);
                    typeRepository.save(existing);
                    log.info("  ↻ {} 補上日文名 {}", type[0], type[2]);
                }
            } else {
                PokemonType t = typeRepository.save(new PokemonType(type[0], type[1], type[2]));
                log.info("  ✓ {} / {} / {}", t.getName(), t.getChineseName(), t.getJapaneseName());
            }
        }
        log.info("========== 屬性初始化完成 ==========");
    }
}
