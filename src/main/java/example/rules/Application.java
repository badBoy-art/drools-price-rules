package example.rules;

import com.fasterxml.jackson.databind.DeserializationFeature;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class Application {
    /** 注册保留BigDecimal精度的Jackson数值配置。 */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer decimalJson() {
        return builder ->
                builder.featuresToEnable(
                        new Object[] {DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS});
    }

    /**
     * 启动QLExpress规则示例应用。
     *
     * @param args 启动应用时传入的命令行参数。
     */
    public static void main(String[] args) {
        SpringApplication.run(Application.class, (String[]) args);
    }
}
