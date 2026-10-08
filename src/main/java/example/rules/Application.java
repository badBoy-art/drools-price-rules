/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.fasterxml.jackson.databind.DeserializationFeature
 *  org.springframework.boot.SpringApplication
 *  org.springframework.boot.autoconfigure.SpringBootApplication
 *  org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer
 *  org.springframework.context.annotation.Bean
 */
package example.rules;

import com.fasterxml.jackson.databind.DeserializationFeature;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class Application {
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer decimalJson() {
        return builder -> builder.featuresToEnable(new Object[]{DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS});
    }

    public static void main(String[] args) {
        SpringApplication.run(Application.class, (String[])args);
    }
}

