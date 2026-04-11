package com.pokechampions.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
@Table(name = "pokemon_type_slots",
       uniqueConstraints = @UniqueConstraint(columnNames = {"pokemon_id", "slot"}))
public class PokemonTypeSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pokemon_id", nullable = false)
    @JsonIgnore
    private Pokemon pokemon;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "type_id", nullable = false)
    private PokemonType type;

    /** 1 = 主屬性, 2 = 副屬性 */
    @Column(nullable = false)
    private int slot;

    public PokemonTypeSlot() {}

    public PokemonTypeSlot(Pokemon pokemon, PokemonType type, int slot) {
        this.pokemon = pokemon;
        this.type = type;
        this.slot = slot;
    }

    public Long getId() { return id; }

    public Pokemon getPokemon() { return pokemon; }
    public void setPokemon(Pokemon pokemon) { this.pokemon = pokemon; }

    public PokemonType getType() { return type; }
    public void setType(PokemonType type) { this.type = type; }

    public int getSlot() { return slot; }
    public void setSlot(int slot) { this.slot = slot; }
}
