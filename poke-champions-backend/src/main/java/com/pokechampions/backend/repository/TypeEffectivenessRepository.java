package com.pokechampions.backend.repository;

import com.pokechampions.backend.entity.PokemonType;
import com.pokechampions.backend.entity.TypeEffectiveness;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TypeEffectivenessRepository extends JpaRepository<TypeEffectiveness, Long> {

    Optional<TypeEffectiveness> findByAttackingTypeAndDefendingType(PokemonType attacking, PokemonType defending);

    List<TypeEffectiveness> findByAttackingType(PokemonType attackingType);

    List<TypeEffectiveness> findByDefendingType(PokemonType defendingType);

    List<TypeEffectiveness> findByMultiplierNot(double multiplier);
}
