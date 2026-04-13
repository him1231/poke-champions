package com.pokechampions.backend.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "abilities")
public class Ability {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** PokeAPI slug，如 "overgrow"、"thick-fat" */
    @Column(nullable = false, unique = true)
    private String name;

    /** 英文顯示名稱，如 "Overgrow"、"Thick Fat" */
    @Column(nullable = false)
    private String displayName;

    /** 繁體中文名，如 "茂盛"、"厚脂肪" */
    private String chineseName;

    private String japaneseName;

    /** 英文效果描述 */
    @Column(length = 1000)
    private String description;

    /** 繁體中文效果描述 */
    @Column(length = 1000)
    private String chineseDescription;

    @Column(length = 1000)
    private String japaneseDescription;

    /** 是否已從 PokeAPI 同步過詳情（中文名、描述等） */
    @Column(nullable = false)
    private boolean detailSynced;

    public Ability() {}

    public Ability(String name, String displayName) {
        this.name = name;
        this.displayName = displayName;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getChineseName() { return chineseName; }
    public void setChineseName(String chineseName) { this.chineseName = chineseName; }

    public String getJapaneseName() { return japaneseName; }
    public void setJapaneseName(String japaneseName) { this.japaneseName = japaneseName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getChineseDescription() { return chineseDescription; }
    public void setChineseDescription(String chineseDescription) { this.chineseDescription = chineseDescription; }

    public String getJapaneseDescription() { return japaneseDescription; }
    public void setJapaneseDescription(String japaneseDescription) { this.japaneseDescription = japaneseDescription; }

    public boolean isDetailSynced() { return detailSynced; }
    public void setDetailSynced(boolean detailSynced) { this.detailSynced = detailSynced; }

    @Override
    public String toString() {
        return "Ability{" + displayName +
                (chineseName != null ? " / " + chineseName : "") +
                " (" + name + ")}";
    }
}
