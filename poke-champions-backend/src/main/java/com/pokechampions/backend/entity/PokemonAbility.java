package com.pokechampions.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
@Table(name = "pokemon_abilities",
       uniqueConstraints = @UniqueConstraint(columnNames = {"pokemon_id", "ability_id", "slot"}))
public class PokemonAbility {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pokemon_id", nullable = false)
    @JsonIgnore
    private Pokemon pokemon;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "ability_id", nullable = false)
    private Ability ability;

    @Column(nullable = false)
    private int slot;

    @Column(nullable = false)
    private boolean hidden;

    public PokemonAbility() {}

    public PokemonAbility(Pokemon pokemon, Ability ability, int slot, boolean hidden) {
        this.pokemon = pokemon;
        this.ability = ability;
        this.slot = slot;
        this.hidden = hidden;
    }

    public Long getId() { return id; }

    public Pokemon getPokemon() { return pokemon; }
    public void setPokemon(Pokemon pokemon) { this.pokemon = pokemon; }

    public Ability getAbility() { return ability; }
    public void setAbility(Ability ability) { this.ability = ability; }

    public int getSlot() { return slot; }
    public void setSlot(int slot) { this.slot = slot; }

    public boolean isHidden() { return hidden; }
    public void setHidden(boolean hidden) { this.hidden = hidden; }
}
