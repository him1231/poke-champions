package com.pokechampions.backend.service;

import com.pokechampions.backend.entity.Pokemon;
import com.pokechampions.backend.entity.PokemonType;
import com.pokechampions.backend.entity.PokemonTypeSlot;
import com.pokechampions.backend.entity.TypeEffectiveness;
import com.pokechampions.backend.repository.PokemonRepository;
import com.pokechampions.backend.repository.PokemonTypeRepository;
import com.pokechampions.backend.repository.TypeEffectivenessRepository;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class TypeMatchupService {

    private final PokemonTypeRepository typeRepository;
    private final TypeEffectivenessRepository effectivenessRepository;
    private final PokemonRepository pokemonRepository;

    public TypeMatchupService(PokemonTypeRepository typeRepository,
                              TypeEffectivenessRepository effectivenessRepository,
                              PokemonRepository pokemonRepository) {
        this.typeRepository = typeRepository;
        this.effectivenessRepository = effectivenessRepository;
        this.pokemonRepository = pokemonRepository;
    }

    /**
     * 計算某攻擊屬性對某防禦寶可夢的實際倍率（考慮雙屬性）。
     * 例：Rock → Charizard(Fire/Flying) = 2.0 × 2.0 = 4.0
     */
    public double calculateMultiplier(PokemonType attackingType, List<PokemonType> defendingTypes) {
        double multiplier = 1.0;
        for (PokemonType defType : defendingTypes) {
            multiplier *= getSingleTypeMultiplier(attackingType, defType);
        }
        return multiplier;
    }

    private double getSingleTypeMultiplier(PokemonType attacking, PokemonType defending) {
        return effectivenessRepository
                .findByAttackingTypeAndDefendingType(attacking, defending)
                .map(TypeEffectiveness::getMultiplier)
                .orElse(1.0);
    }

    /**
     * 取得某寶可夢的完整防禦相性表：
     * 回傳每種攻擊屬性對此寶可夢的實際倍率（含 4x, 0.25x, 0x 等複合結果）。
     */
    public DefensiveProfile getDefensiveProfile(Pokemon pokemon) {
        List<PokemonType> defTypes = pokemon.getTypeSlots().stream()
                .sorted(Comparator.comparingInt(PokemonTypeSlot::getSlot))
                .map(PokemonTypeSlot::getType)
                .toList();

        List<PokemonType> allTypes = typeRepository.findAll();

        List<TypeMultiplier> immunities = new ArrayList<>();
        List<TypeMultiplier> quadWeaknesses = new ArrayList<>();
        List<TypeMultiplier> weaknesses = new ArrayList<>();
        List<TypeMultiplier> resistances = new ArrayList<>();
        List<TypeMultiplier> quadResistances = new ArrayList<>();
        List<TypeMultiplier> neutral = new ArrayList<>();

        for (PokemonType atkType : allTypes) {
            double mult = calculateMultiplier(atkType, defTypes);
            TypeMultiplier tm = new TypeMultiplier(atkType.getName(), atkType.getChineseName(), mult);

            if (mult == 0.0) {
                immunities.add(tm);
            } else if (mult >= 4.0) {
                quadWeaknesses.add(tm);
            } else if (mult >= 2.0) {
                weaknesses.add(tm);
            } else if (mult <= 0.25) {
                quadResistances.add(tm);
            } else if (mult < 1.0) {
                resistances.add(tm);
            } else {
                neutral.add(tm);
            }
        }

        List<String> typeNames = defTypes.stream().map(PokemonType::getName).toList();
        List<String> typeChineseNames = defTypes.stream().map(PokemonType::getChineseName).toList();

        return new DefensiveProfile(
                pokemon.getApiName(),
                pokemon.getDisplayName(),
                pokemon.getChineseName(),
                typeNames,
                typeChineseNames,
                immunities,
                quadWeaknesses,
                weaknesses,
                neutral,
                resistances,
                quadResistances
        );
    }

    public Optional<DefensiveProfile> getDefensiveProfile(String apiName) {
        return pokemonRepository.findByApiName(apiName)
                .map(this::getDefensiveProfile);
    }

    /**
     * 計算純屬性組合的防禦相性（不需要具體寶可夢）。
     * 例：查 Fire/Flying 的所有弱點。
     */
    public Optional<DefensiveProfile> getDefensiveProfileByTypes(String type1Name, String type2Name) {
        Optional<PokemonType> t1 = typeRepository.findByName(type1Name.toLowerCase());
        if (t1.isEmpty()) return Optional.empty();

        List<PokemonType> defTypes = new ArrayList<>();
        defTypes.add(t1.get());

        if (type2Name != null && !type2Name.isBlank()) {
            Optional<PokemonType> t2 = typeRepository.findByName(type2Name.toLowerCase());
            if (t2.isEmpty()) return Optional.empty();
            defTypes.add(t2.get());
        }

        List<PokemonType> allTypes = typeRepository.findAll();

        List<TypeMultiplier> immunities = new ArrayList<>();
        List<TypeMultiplier> quadWeaknesses = new ArrayList<>();
        List<TypeMultiplier> weaknesses = new ArrayList<>();
        List<TypeMultiplier> resistances = new ArrayList<>();
        List<TypeMultiplier> quadResistances = new ArrayList<>();
        List<TypeMultiplier> neutral = new ArrayList<>();

        for (PokemonType atkType : allTypes) {
            double mult = calculateMultiplier(atkType, defTypes);
            TypeMultiplier tm = new TypeMultiplier(atkType.getName(), atkType.getChineseName(), mult);

            if (mult == 0.0) {
                immunities.add(tm);
            } else if (mult >= 4.0) {
                quadWeaknesses.add(tm);
            } else if (mult >= 2.0) {
                weaknesses.add(tm);
            } else if (mult <= 0.25) {
                quadResistances.add(tm);
            } else if (mult < 1.0) {
                resistances.add(tm);
            } else {
                neutral.add(tm);
            }
        }

        List<String> typeNames = defTypes.stream().map(PokemonType::getName).toList();
        List<String> typeChineseNames = defTypes.stream().map(PokemonType::getChineseName).toList();

        return Optional.of(new DefensiveProfile(
                null,
                type1Name + (type2Name != null ? "/" + type2Name : ""),
                null,
                typeNames,
                typeChineseNames,
                immunities,
                quadWeaknesses,
                weaknesses,
                neutral,
                resistances,
                quadResistances
        ));
    }

    // ─── DTO ────────────────────────────────────────────────────

    public record TypeMultiplier(String type, String chineseName, double multiplier) {}

    public record DefensiveProfile(
            String apiName,
            String displayName,
            String chineseName,
            List<String> types,
            List<String> typesChinese,
            List<TypeMultiplier> immunities,
            List<TypeMultiplier> quadWeaknesses,
            List<TypeMultiplier> weaknesses,
            List<TypeMultiplier> neutral,
            List<TypeMultiplier> resistances,
            List<TypeMultiplier> quadResistances
    ) {}
}
