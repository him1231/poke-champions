package com.pokechampions.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
@Table(name = "pokemon_moves",
       uniqueConstraints = @UniqueConstraint(columnNames = {"pokemon_id", "move_id"}))
public class PokemonMove {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pokemon_id", nullable = false)
    @JsonIgnore
    private Pokemon pokemon;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "move_id", nullable = false)
    private Move move;

    /** 資料來源（與 Move 本體的 source 獨立，因為同一招式不同寶可夢的來源可能不同） */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Move.MoveSource source;

    /** 是否已在 Champions 中確認此寶可夢可學此招式 */
    @Column(nullable = false)
    private boolean verified;

    public PokemonMove() {}

    public PokemonMove(Pokemon pokemon, Move move, Move.MoveSource source, boolean verified) {
        this.pokemon = pokemon;
        this.move = move;
        this.source = source;
        this.verified = verified;
    }

    public Long getId() { return id; }

    public Pokemon getPokemon() { return pokemon; }
    public void setPokemon(Pokemon pokemon) { this.pokemon = pokemon; }

    public Move getMove() { return move; }
    public void setMove(Move move) { this.move = move; }

    public Move.MoveSource getSource() { return source; }
    public void setSource(Move.MoveSource source) { this.source = source; }

    public boolean isVerified() { return verified; }
    public void setVerified(boolean verified) { this.verified = verified; }
}
