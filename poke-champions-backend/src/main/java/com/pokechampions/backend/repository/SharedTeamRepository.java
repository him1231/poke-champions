package com.pokechampions.backend.repository;

import com.pokechampions.backend.entity.SharedTeam;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import org.springframework.data.repository.query.Param;
import java.util.Optional;

@Repository
public interface SharedTeamRepository extends JpaRepository<SharedTeam, Long> {

    Optional<SharedTeam> findByRentalCode(String rentalCode);

    Page<SharedTeam> findByIsPublicTrue(Pageable pageable);

    Page<SharedTeam> findByIsPublicTrueAndExpiredFalse(Pageable pageable);

    boolean existsByRentalCode(String rentalCode);

    long countByIsPublicTrue();
    long countByReportCountGreaterThan(int threshold);
    long countByExpiredTrue();

    @Query("SELECT COALESCE(SUM(t.viewCount), 0) FROM SharedTeam t")
    long sumViewCount();

    @Query(value = "SELECT * FROM shared_team t WHERE " +
           "(CAST(:search AS text) IS NULL OR LOWER(t.rental_code) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%')) " +
           "OR LOWER(t.title) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%'))) " +
           "AND (CAST(:filter AS text) = 'all' OR (CAST(:filter AS text) = 'reported' AND t.report_count > 0) " +
           "OR (CAST(:filter AS text) = 'expired' AND t.expired = true))",
           countQuery = "SELECT COUNT(*) FROM shared_team t WHERE " +
           "(CAST(:search AS text) IS NULL OR LOWER(t.rental_code) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%')) " +
           "OR LOWER(t.title) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%'))) " +
           "AND (CAST(:filter AS text) = 'all' OR (CAST(:filter AS text) = 'reported' AND t.report_count > 0) " +
           "OR (CAST(:filter AS text) = 'expired' AND t.expired = true))",
           nativeQuery = true)
    Page<SharedTeam> findAllForAdmin(@Param("search") String search,
                                     @Param("filter") String filter,
                                     Pageable pageable);

    @Modifying
    @Query("UPDATE SharedTeam t SET t.viewCount = t.viewCount + 1 WHERE t.rentalCode = :rentalCode")
    void incrementViewCount(String rentalCode);

    @Modifying
    @Query("UPDATE SharedTeam t SET t.reportCount = t.reportCount + 1 WHERE t.rentalCode = :rentalCode")
    void incrementReportCount(String rentalCode);
}
