package com.pokechampions.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI pokeChampionsOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Poké Champions 後端 API")
                        .description("""
                                提供 Pokémon Champions 相關資料：官方名冊爬取、寶可夢與能力值同步、屬性與相剋、賽季名單、招式與可學習者、持有物品等。\
                                多數寫入型端點會觸發外部網站爬蟲或第三方 API，請謹慎呼叫。""")
                        .version("0.0.1"));
    }
}
