package com.pokechampions.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "seasons")
public class Season {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String regulationName;

    @Column(nullable = false)
    private LocalDateTime startDate;

    @Column(nullable = false)
    private LocalDateTime endDate;

    private String sourceUrl;

    @OneToMany(mappedBy = "season", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnore
    private List<SeasonPokemon> seasonPokemon = new ArrayList<>();

    public Season() {}

    public Season(String regulationName, LocalDateTime startDate, LocalDateTime endDate, String sourceUrl) {
        this.regulationName = regulationName;
        this.startDate = startDate;
        this.endDate = endDate;
        this.sourceUrl = sourceUrl;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRegulationName() { return regulationName; }
    public void setRegulationName(String regulationName) { this.regulationName = regulationName; }

    public LocalDateTime getStartDate() { return startDate; }
    public void setStartDate(LocalDateTime startDate) { this.startDate = startDate; }

    public LocalDateTime getEndDate() { return endDate; }
    public void setEndDate(LocalDateTime endDate) { this.endDate = endDate; }

    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }

    public List<SeasonPokemon> getSeasonPokemon() { return seasonPokemon; }
    public void setSeasonPokemon(List<SeasonPokemon> seasonPokemon) { this.seasonPokemon = seasonPokemon; }

    @Override
    public String toString() {
        return "Season{regulationName='" + regulationName + "', start=" + startDate + ", end=" + endDate + "}";
    }
}
