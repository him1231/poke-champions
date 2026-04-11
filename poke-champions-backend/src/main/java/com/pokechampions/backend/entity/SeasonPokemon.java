package com.pokechampions.backend.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "season_pokemon",
       uniqueConstraints = @UniqueConstraint(columnNames = {"season_id", "pokemon_id"}))
public class SeasonPokemon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "season_id", nullable = false)
    private Season season;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "pokemon_id", nullable = false)
    private Pokemon pokemon;

    @Column(nullable = false)
    private boolean canMegaEvolve;

    public SeasonPokemon() {}

    public SeasonPokemon(Season season, Pokemon pokemon, boolean canMegaEvolve) {
        this.season = season;
        this.pokemon = pokemon;
        this.canMegaEvolve = canMegaEvolve;
    }

    public Long getId() { return id; }

    public Season getSeason() { return season; }
    public void setSeason(Season season) { this.season = season; }

    public Pokemon getPokemon() { return pokemon; }
    public void setPokemon(Pokemon pokemon) { this.pokemon = pokemon; }

    public boolean isCanMegaEvolve() { return canMegaEvolve; }
    public void setCanMegaEvolve(boolean canMegaEvolve) { this.canMegaEvolve = canMegaEvolve; }
}
