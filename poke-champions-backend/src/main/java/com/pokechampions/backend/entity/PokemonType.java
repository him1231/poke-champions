package com.pokechampions.backend.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "types")
public class PokemonType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private String chineseName;

    public PokemonType() {}

    public PokemonType(String name, String chineseName) {
        this.name = name;
        this.chineseName = chineseName;
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getChineseName() { return chineseName; }
    public void setChineseName(String chineseName) { this.chineseName = chineseName; }

    @Override
    public String toString() {
        return "PokemonType{" + name + " / " + chineseName + "}";
    }
}
