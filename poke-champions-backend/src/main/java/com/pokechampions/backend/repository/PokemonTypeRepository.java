package com.pokechampions.backend.repository;

import com.pokechampions.backend.entity.PokemonType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PokemonTypeRepository extends JpaRepository<PokemonType, Integer> {

    Optional<PokemonType> findByName(String name);
}
