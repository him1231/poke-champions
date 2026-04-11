package com.pokechampions.backend.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "type_effectiveness",
       uniqueConstraints = @UniqueConstraint(columnNames = {"attacking_type_id", "defending_type_id"}))
public class TypeEffectiveness {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "attacking_type_id", nullable = false)
    private PokemonType attackingType;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "defending_type_id", nullable = false)
    private PokemonType defendingType;

    @Column(nullable = false)
    private double multiplier;

    public TypeEffectiveness() {}

    public TypeEffectiveness(PokemonType attackingType, PokemonType defendingType, double multiplier) {
        this.attackingType = attackingType;
        this.defendingType = defendingType;
        this.multiplier = multiplier;
    }

    public Long getId() { return id; }

    public PokemonType getAttackingType() { return attackingType; }
    public void setAttackingType(PokemonType attackingType) { this.attackingType = attackingType; }

    public PokemonType getDefendingType() { return defendingType; }
    public void setDefendingType(PokemonType defendingType) { this.defendingType = defendingType; }

    public double getMultiplier() { return multiplier; }
    public void setMultiplier(double multiplier) { this.multiplier = multiplier; }
}
