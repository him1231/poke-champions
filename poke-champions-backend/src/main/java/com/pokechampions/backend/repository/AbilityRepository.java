package com.pokechampions.backend.repository;

import com.pokechampions.backend.entity.Ability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AbilityRepository extends JpaRepository<Ability, Long> {

    Optional<Ability> findByName(String name);

    List<Ability> findByDetailSyncedFalse();

    List<Ability> findByChineseNameIsNotNull();
}
