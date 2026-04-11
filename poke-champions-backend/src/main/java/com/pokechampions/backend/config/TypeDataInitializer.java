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
            {"normal",   "一般"},
            {"fire",     "火"},
            {"water",    "水"},
            {"electric", "電"},
            {"grass",    "草"},
            {"ice",      "冰"},
            {"fighting", "格鬥"},
            {"poison",   "毒"},
            {"ground",   "地面"},
            {"flying",   "飛行"},
            {"psychic",  "超能力"},
            {"bug",      "蟲"},
            {"rock",     "岩石"},
            {"ghost",    "幽靈"},
            {"dragon",   "龍"},
            {"dark",     "惡"},
            {"steel",    "鋼"},
            {"fairy",    "妖精"},
    };

    private final PokemonTypeRepository typeRepository;

    public TypeDataInitializer(PokemonTypeRepository typeRepository) {
        this.typeRepository = typeRepository;
    }

    @Override
    public void run(String... args) {
        if (typeRepository.count() >= TYPES.length) {
            log.info("屬性資料已存在 ({} 筆)，跳過初始化", typeRepository.count());
            return;
        }

        log.info("========== 初始化 18 種屬性資料 ==========");
        for (String[] type : TYPES) {
            typeRepository.findByName(type[0]).orElseGet(() -> {
                PokemonType t = typeRepository.save(new PokemonType(type[0], type[1]));
                log.info("  ✓ {} / {}", t.getName(), t.getChineseName());
                return t;
            });
        }
        log.info("========== 屬性初始化完成 ==========");
    }
}
