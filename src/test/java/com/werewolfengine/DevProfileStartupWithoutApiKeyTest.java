package com.werewolfengine;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.data.redis.autoconfigure.RedisAutoConfiguration"
})
@ActiveProfiles("dev")
class DevProfileStartupWithoutApiKeyTest {

    @Test
    void contextLoadsWithoutDeepSeekApiKey() {
    }
}
