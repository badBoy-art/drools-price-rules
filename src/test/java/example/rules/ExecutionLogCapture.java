package example.rules;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.LoggerFactory;

/** 在单次测试范围内捕获真实执行日志，验证原始入参和结果不依赖执行日志表。 */
final class ExecutionLogCapture implements AutoCloseable {
    private final Logger logger = (Logger) LoggerFactory.getLogger(RuleService.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    ExecutionLogCapture() {
        appender.start();
        logger.addAppender(appender);
    }

    JsonNode execution(String executionId) {
        for (ILoggingEvent event : appender.list) {
            if (event.getLevel() != Level.INFO || !"规则执行完成 {}".equals(event.getMessage())) continue;
            JsonNode record = RuleJson.parse((String) event.getArgumentArray()[0]);
            if (executionId.equals(record.path("executionId").asText())) return record;
        }
        throw new AssertionError("缺少执行INFO日志：" + executionId);
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
        appender.stop();
    }
}
