package com.pokechampions.backend.repository;

import com.pokechampions.backend.entity.Pokemon;
import com.pokechampions.backend.entity.PokemonAbility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PokemonAbilityRepository extends JpaRepository<PokemonAbility, Long> {

    List<PokemonAbility> findByPokemon(Pokemon pokemon);

    List<PokemonAbility> findByPokemonOrderBySlot(Pokemon pokemon);

    void deleteByPokemon(Pokemon pokemon);
}
