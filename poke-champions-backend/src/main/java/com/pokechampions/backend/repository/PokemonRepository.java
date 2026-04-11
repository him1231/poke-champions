package com.pokechampions.backend.repository;

import com.pokechampions.backend.entity.Pokemon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PokemonRepository extends JpaRepository<Pokemon, Long> {

    Optional<Pokemon> findByApiName(String apiName);

    Optional<Pokemon> findByFormId(String formId);

    List<Pokemon> findByIsMegaTrue();

    List<Pokemon> findByStatsSyncedFalse();

    Optional<Pokemon> findByChineseName(String chineseName);

    List<Pokemon> findByChineseNameIsNotNull();

    Optional<Pokemon> findByDisplayName(String displayName);
}
