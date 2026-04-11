package com.pokechampions.backend.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pokemon", uniqueConstraints = @UniqueConstraint(columnNames = {"apiName", "formId"}))
public class Pokemon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int nationalDexNumber;

    /** 表單 ID，如 "0003-000"、"0026-001" */
    @Column(nullable = false)
    private String formId;

    /** 顯示名稱（英文），如 "Venusaur"、"Raichu (Alolan Form)" */
    @Column(nullable = false)
    private String displayName;

    /** PokeAPI slug，如 "venusaur"、"raichu-alola" */
    @Column(nullable = false)
    private String apiName;

    /** 中文顯示名稱，如 "妙蛙花"、"雷丘 (阿羅拉的樣子)" */
    private String chineseName;

    private boolean isMega;

    // 種族值
    private int hp;
    private int attack;
    private int defense;
    private int specialAttack;
    private int specialDefense;
    private int speed;
    private int baseStatTotal;

    private boolean statsSynced;

    @OneToMany(mappedBy = "pokemon", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<PokemonTypeSlot> typeSlots = new ArrayList<>();

    public Pokemon() {}

    public Pokemon(int nationalDexNumber, String formId, String displayName, String apiName, boolean isMega) {
        this.nationalDexNumber = nationalDexNumber;
        this.formId = formId;
        this.displayName = displayName;
        this.apiName = apiName;
        this.isMega = isMega;
    }

    public void applyStats(int hp, int attack, int defense, int specialAttack, int specialDefense, int speed) {
        this.hp = hp;
        this.attack = attack;
        this.defense = defense;
        this.specialAttack = specialAttack;
        this.specialDefense = specialDefense;
        this.speed = speed;
        this.baseStatTotal = hp + attack + defense + specialAttack + specialDefense + speed;
        this.statsSynced = true;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public int getNationalDexNumber() { return nationalDexNumber; }
    public void setNationalDexNumber(int nationalDexNumber) { this.nationalDexNumber = nationalDexNumber; }

    public String getFormId() { return formId; }
    public void setFormId(String formId) { this.formId = formId; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getApiName() { return apiName; }
    public void setApiName(String apiName) { this.apiName = apiName; }

    public String getChineseName() { return chineseName; }
    public void setChineseName(String chineseName) { this.chineseName = chineseName; }

    public boolean isMega() { return isMega; }
    public void setMega(boolean mega) { isMega = mega; }

    public int getHp() { return hp; }
    public int getAttack() { return attack; }
    public int getDefense() { return defense; }
    public int getSpecialAttack() { return specialAttack; }
    public int getSpecialDefense() { return specialDefense; }
    public int getSpeed() { return speed; }
    public int getBaseStatTotal() { return baseStatTotal; }
    public boolean isStatsSynced() { return statsSynced; }
    public void setStatsSynced(boolean statsSynced) { this.statsSynced = statsSynced; }

    public List<PokemonTypeSlot> getTypeSlots() { return typeSlots; }
    public void setTypeSlots(List<PokemonTypeSlot> typeSlots) { this.typeSlots = typeSlots; }

    @JsonProperty("types")
    public List<String> getTypeNames() {
        return typeSlots.stream()
                .sorted((a, b) -> a.getSlot() - b.getSlot())
                .map(slot -> slot.getType().getName())
                .toList();
    }

    @Override
    public String toString() {
        return "Pokemon{#" + nationalDexNumber + " " + displayName +
                (chineseName != null ? " / " + chineseName : "") +
                " (" + apiName + ")" +
                (isMega ? " [MEGA]" : "") +
                (statsSynced ? " BST=" + baseStatTotal : " [no stats]") + "}";
    }
}
