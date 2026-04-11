package com.pokechampions.backend.repository;

import com.pokechampions.backend.entity.Move;
import com.pokechampions.backend.entity.PokemonType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MoveRepository extends JpaRepository<Move, Long> {

    Optional<Move> findByName(String name);

    Optional<Move> findByDisplayName(String displayName);

    List<Move> findByType(PokemonType type);

    List<Move> findByCategory(Move.MoveCategory category);

    List<Move> findByTypeAndCategory(PokemonType type, Move.MoveCategory category);

    List<Move> findBySource(Move.MoveSource source);

    List<Move> findByGame8UrlIsNotNull();

    List<Move> findByChineseNameIsNull();

    List<Move> findByDescriptionIsNotNullAndChineseDescriptionIsNull();
}
