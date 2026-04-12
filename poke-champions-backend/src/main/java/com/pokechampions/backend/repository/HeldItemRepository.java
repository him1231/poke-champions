package com.pokechampions.backend.repository;

import com.pokechampions.backend.entity.HeldItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HeldItemRepository extends JpaRepository<HeldItem, Long> {

    Optional<HeldItem> findByName(String name);

    List<HeldItem> findByCategory(HeldItem.ItemCategory category);

    List<HeldItem> findByChineseNameIsNull();

    List<HeldItem> findByEffectIsNotNullAndChineseEffectIsNull();
}
