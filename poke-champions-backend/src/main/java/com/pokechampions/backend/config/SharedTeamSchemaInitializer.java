package com.pokechampions.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * 生產環境若尚未執行手動 migration，{@code shared_team} 可能缺少 {@code battle_format}，
 * 會導致管理後台 / 公開 API 讀取隊伍時 Hibernate 失敗（500）。
 * 啟動時以 PostgreSQL 相容語法補欄位，可重複執行。
 */
@Component
@Order(0)
public class SharedTeamSchemaInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SharedTeamSchemaInitializer.class);

    private final DataSource dataSource;

    public SharedTeamSchemaInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) {
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            st.execute("""
                    ALTER TABLE shared_team
                    ADD COLUMN IF NOT EXISTS battle_format varchar(16) NOT NULL DEFAULT 'singles'
                    """);
            log.info("Ensured column shared_team.battle_format exists");
        } catch (Exception e) {
            log.warn("Could not align shared_team.battle_format (non-fatal): {}", e.getMessage());
        }
    }
}
