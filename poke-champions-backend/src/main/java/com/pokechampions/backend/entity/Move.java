package com.pokechampions.backend.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "moves")
public class Move {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** slug 形式的唯一名稱，如 "thunder-punch"、"swords-dance" */
    @Column(nullable = false, unique = true)
    private String name;

    /** 英文顯示名稱，如 "Thunder Punch"、"Swords Dance" */
    @Column(nullable = false)
    private String displayName;

    private String chineseName;

    private String japaneseName;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "type_id", nullable = false)
    private PokemonType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MoveCategory category;

    /** 威力，變化技為 null */
    private Integer power;

    /** 命中率（Game8 無此欄位，可由 PokeAPI 補充） */
    private Integer accuracy;

    @Column(nullable = false)
    private int pp;

    /** 效果描述（英文） */
    @Column(length = 1000)
    private String description;

    /** 效果描述（繁體中文，由 AI 翻譯） */
    @Column(length = 1000)
    private String chineseDescription;

    /** 效果描述（日文） */
    @Column(length = 1000)
    private String japaneseDescription;

    /** Game8 招式詳情頁 URL，用於爬取可學習寶可夢列表 */
    private String game8Url;

    /** 資料來源 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MoveSource source;

    public Move() {}

    public Move(String name, String displayName, PokemonType type, MoveCategory category,
                Integer power, int pp, String description, MoveSource source) {
        this.name = name;
        this.displayName = displayName;
        this.type = type;
        this.category = category;
        this.power = power;
        this.pp = pp;
        this.description = description;
        this.source = source;
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

    public PokemonType getType() { return type; }
    public void setType(PokemonType type) { this.type = type; }

    public MoveCategory getCategory() { return category; }
    public void setCategory(MoveCategory category) { this.category = category; }

    public Integer getPower() { return power; }
    public void setPower(Integer power) { this.power = power; }

    public Integer getAccuracy() { return accuracy; }
    public void setAccuracy(Integer accuracy) { this.accuracy = accuracy; }

    public int getPp() { return pp; }
    public void setPp(int pp) { this.pp = pp; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getChineseDescription() { return chineseDescription; }
    public void setChineseDescription(String chineseDescription) { this.chineseDescription = chineseDescription; }

    public String getJapaneseDescription() { return japaneseDescription; }
    public void setJapaneseDescription(String japaneseDescription) { this.japaneseDescription = japaneseDescription; }

    public String getGame8Url() { return game8Url; }
    public void setGame8Url(String game8Url) { this.game8Url = game8Url; }

    public MoveSource getSource() { return source; }
    public void setSource(MoveSource source) { this.source = source; }

    /** JSON 序列化時以字串顯示屬性名稱 */
    public String getTypeName() {
        return type != null ? type.getName() : null;
    }

    @Override
    public String toString() {
        return "Move{" + displayName + " (" + name + "), " + category + ", " +
                (type != null ? type.getName() : "?") +
                ", Power=" + (power != null ? power : "-") +
                ", PP=" + pp + "}";
    }

    public enum MoveCategory {
        PHYSICAL, SPECIAL, STATUS
    }

    public enum MoveSource {
        CHAMPIONS_GAME8,
        POKEAPI_DEFAULT,
        COMMUNITY,
        MANUAL
    }
}
