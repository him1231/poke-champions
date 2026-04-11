package com.pokechampions.backend.repository;

import com.pokechampions.backend.entity.Season;
import com.pokechampions.backend.entity.SeasonPokemon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SeasonPokemonRepository extends JpaRepository<SeasonPokemon, Long> {

    List<SeasonPokemon> findBySeason(Season season);

    List<SeasonPokemon> findBySeasonId(Long seasonId);

    List<SeasonPokemon> findBySeasonIdAndCanMegaEvolveTrue(Long seasonId);

    boolean existsBySeasonAndPokemonId(Season season, Long pokemonId);

    @Modifying
    @Query("DELETE FROM SeasonPokemon sp WHERE sp.season.id = :seasonId")
    void deleteBySeasonId(Long seasonId);
}
