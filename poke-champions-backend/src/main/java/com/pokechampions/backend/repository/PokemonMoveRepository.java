package com.pokechampions.backend.repository;

import com.pokechampions.backend.entity.Move;
import com.pokechampions.backend.entity.Pokemon;
import com.pokechampions.backend.entity.PokemonMove;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PokemonMoveRepository extends JpaRepository<PokemonMove, Long> {

    List<PokemonMove> findByPokemon(Pokemon pokemon);

    List<PokemonMove> findByPokemonId(Long pokemonId);

    List<PokemonMove> findByMove(Move move);

    Optional<PokemonMove> findByPokemonAndMove(Pokemon pokemon, Move move);

    List<PokemonMove> findByPokemonAndVerifiedTrue(Pokemon pokemon);

    void deleteByPokemon(Pokemon pokemon);
}
