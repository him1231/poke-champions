package com.pokechampions.backend.repository;

import com.pokechampions.backend.entity.Pokemon;
import com.pokechampions.backend.entity.PokemonTypeSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PokemonTypeSlotRepository extends JpaRepository<PokemonTypeSlot, Long> {

    List<PokemonTypeSlot> findByPokemon(Pokemon pokemon);

    List<PokemonTypeSlot> findByPokemonId(Long pokemonId);

    void deleteByPokemon(Pokemon pokemon);
}
