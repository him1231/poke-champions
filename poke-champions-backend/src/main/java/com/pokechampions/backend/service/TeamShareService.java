package com.pokechampions.backend.service;

import com.pokechampions.backend.entity.SharedTeam;
import com.pokechampions.backend.repository.SharedTeamRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class TeamShareService {

    private final SharedTeamRepository repo;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public TeamShareService(SharedTeamRepository repo) {
        this.repo = repo;
    }

    public String hashPin(String rawPin) {
        return encoder.encode(rawPin);
    }

    public boolean verifyPin(String rawPin, String hash) {
        return encoder.matches(rawPin, hash);
    }

    @Transactional
    public SharedTeam create(SharedTeam team, String rawPin) {
        team.setPinHash(hashPin(rawPin));
        return repo.save(team);
    }

    public Optional<SharedTeam> findByRentalCode(String rentalCode) {
        return repo.findByRentalCode(rentalCode);
    }

    public boolean existsByRentalCode(String rentalCode) {
        return repo.existsByRentalCode(rentalCode);
    }

    public Page<SharedTeam> listPublic(String sort, int page, int size) {
        Sort ordering = "popular".equalsIgnoreCase(sort)
                ? Sort.by(Sort.Direction.DESC, "viewCount")
                : Sort.by(Sort.Direction.DESC, "createdAt");
        Pageable pageable = PageRequest.of(page, size, ordering);
        return repo.findByIsPublicTrueAndExpiredFalse(pageable);
    }

    @Transactional
    public void incrementViewCount(String rentalCode) {
        repo.incrementViewCount(rentalCode);
    }

    @Transactional
    public void incrementReportCount(String rentalCode) {
        repo.incrementReportCount(rentalCode);
    }

    @Transactional
    public SharedTeam update(SharedTeam team) {
        return repo.save(team);
    }

    @Transactional
    public void delete(SharedTeam team) {
        repo.delete(team);
    }
}
