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

    private String japaneseName;

    public PokemonType() {}

    public PokemonType(String name, String chineseName) {
        this.name = name;
        this.chineseName = chineseName;
    }

    public PokemonType(String name, String chineseName, String japaneseName) {
        this.name = name;
        this.chineseName = chineseName;
        this.japaneseName = japaneseName;
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getChineseName() { return chineseName; }
    public void setChineseName(String chineseName) { this.chineseName = chineseName; }

    public String getJapaneseName() { return japaneseName; }
    public void setJapaneseName(String japaneseName) { this.japaneseName = japaneseName; }

    @Override
    public String toString() {
        return "PokemonType{" + name + " / " + chineseName + "}";
    }
}
