package com.pokechampions.backend.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "held_items")
public class HeldItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** slug 形式的唯一名稱，如 "focus-sash" */
    @Column(nullable = false, unique = true)
    private String name;

    /** 英文顯示名稱，如 "Focus Sash" */
    @Column(nullable = false)
    private String displayName;

    /** 繁體中文名稱（後續翻譯用） */
    private String chineseName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ItemCategory category;

    /** 取得方式，如 "Shop: 1000 VP"、"Available from the start" */
    @Column(length = 500)
    private String howToGet;

    /** 效果說明（英文） */
    @Column(length = 1000)
    private String effect;

    /** 效果說明（繁體中文，由 AI 翻譯） */
    @Column(length = 1000)
    private String chineseEffect;

    /** Game8 物品詳情頁 URL */
    private String game8Url;

    public HeldItem() {}

    public HeldItem(String name, String displayName, ItemCategory category,
                    String howToGet, String effect, String game8Url) {
        this.name = name;
        this.displayName = displayName;
        this.category = category;
        this.howToGet = howToGet;
        this.effect = effect;
        this.game8Url = game8Url;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getChineseName() { return chineseName; }
    public void setChineseName(String chineseName) { this.chineseName = chineseName; }

    public ItemCategory getCategory() { return category; }
    public void setCategory(ItemCategory category) { this.category = category; }

    public String getHowToGet() { return howToGet; }
    public void setHowToGet(String howToGet) { this.howToGet = howToGet; }

    public String getEffect() { return effect; }
    public void setEffect(String effect) { this.effect = effect; }

    public String getChineseEffect() { return chineseEffect; }
    public void setChineseEffect(String chineseEffect) { this.chineseEffect = chineseEffect; }

    public String getGame8Url() { return game8Url; }
    public void setGame8Url(String game8Url) { this.game8Url = game8Url; }

    @Override
    public String toString() {
        return "HeldItem{" + displayName + " (" + name + "), " + category + "}";
    }

    public enum ItemCategory {
        DEFENSE,
        RECOVERY,
        POWER_BOOST,
        STAT_BOOST,
        MEGA_STONE,
        OTHER
    }
}
