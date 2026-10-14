package example.rules.dao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import example.rules.model.RuleConfig;
import example.rules.model.RuleConfig.Condition;
import example.rules.model.RuleConfig.Assignment;
import example.rules.result.RuleState;
import example.rules.service.ConfigValidator;
import example.rules.service.RuleJson;
import example.rules.service.RuleValidator;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** 主表和条件表保存当前配置；QLExpress脚本由当前条件实时生成。 */
@Repository
public class RuleStore {
    /** Columns follow src/main/resources/schema.sql; public JSON names remain API aliases. */
    private static final List<String> COLUMNS = Arrays.asList("rule_id", "rule_name", "is_def",
            "category_id1", "category_id2", "category_id3", "rule_sort_no", "cond_code",
            "cond_type_name", "value_field_name", "judge_type_name", "lowbound_val",
            "is_inc_lowbound", "upbound_val", "is_inc_upbound", "cmpr_type", "thres",
            "rslt_assign_info", "script_cont", "cond_sort_no", "del_flag");
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final RuleValidator validator;

    public RuleStore(JdbcTemplate jdbc, PlatformTransactionManager manager, RuleValidator validator) {
        this.jdbc = jdbc;
        this.validator = validator;
        this.transaction = new TransactionTemplate(manager);
        this.transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    public RuleState create(RuleConfig config, Function<RuleConfig, Map<String, String>> generateScripts) {
        validator.validate(config);
        try {
            return transaction.execute(status -> {
                setScripts(config, generateScripts.apply(config));
                jdbc.update("INSERT INTO ac_audit_rule(rule_code,rule_name) VALUES (?,?)",
                        config.businessCode, config.businessName);
                RuleState header = readLocked(config.businessCode);
                sync(header.id, Collections.emptyList(), config.conditions);
                return readLocked(config.businessCode);
            });
        } catch (DuplicateKeyException duplicate) {
            throw new IllegalArgumentException("businessCode已存在", duplicate);
        }
    }

    public RuleState read(String businessCode) {
        validator.businessCode(businessCode);
        return transaction.execute(status -> readLocked(businessCode));
    }

    public RuleState update(String businessCode, RuleConfig next,
            Function<RuleConfig, Map<String, String>> generateScripts) {
        validator.businessCode(businessCode);
        ConfigValidator.require(businessCode.equals(next.businessCode), "URL与businessCode必须一致");
        return transaction.execute(status -> {
            RuleState old = readLocked(businessCode);
            validator.validate(next);
            setScripts(next, generateScripts.apply(next));
            if (equivalent(old.config, next)) return old;
            sync(old.id, old.config.conditions, next.conditions);
            jdbc.update("UPDATE ac_audit_rule SET rule_name=? WHERE id=? AND del_flag=0",
                    next.businessName, old.id);
            return readLocked(businessCode);
        });
    }

    public RuleState insertCondition(String businessCode, Condition condition,
            Function<RuleConfig, Map<String, String>> generateScripts) {
        validator.businessCode(businessCode);
        return transaction.execute(status -> {
            RuleState old = readLocked(businessCode);
            RuleConfig next = copy(old.config);
            next.conditions.add(condition);
            validator.validate(next);
            setScripts(next, generateScripts.apply(next));
            sync(old.id, old.config.conditions, next.conditions);
            return readLocked(businessCode);
        });
    }

    public RuleState updateCondition(String businessCode, String ruleName, String conditionCode,
            Condition condition, Function<RuleConfig, Map<String, String>> generateScripts) {
        validator.businessCode(businessCode);
        return transaction.execute(status -> {
            RuleState old = readLocked(businessCode);
            RuleConfig next = copy(old.config);
            boolean found = false;
            for (int i = 0; i < next.conditions.size(); i++) {
                Condition current = next.conditions.get(i);
                if (ruleName.equals(current.ruleName) && conditionCode.equals(current.conditionCode)) {
                    next.conditions.set(i, condition);
                    found = true;
                    break;
                }
            }
            if (!found) throw new NoSuchElementException("条件不存在：" + ruleName + "/" + conditionCode);
            validator.validate(next);
            setScripts(next, generateScripts.apply(next));
            sync(old.id, old.config.conditions, next.conditions);
            return readLocked(businessCode);
        });
    }

    private RuleConfig copy(RuleConfig current) {
        RuleConfig copy = new RuleConfig();
        copy.businessCode = current.businessCode;
        copy.businessName = current.businessName;
        copy.conditions = new ArrayList<>(current.conditions);
        return copy;
    }

    private void setScripts(RuleConfig config, Map<String, String> scripts) {
        for (Condition condition : config.conditions) {
            String script = scripts.get(key(condition));
            if (script == null) throw new IllegalStateException("规则条件未生成脚本：" + key(condition));
            condition.scriptContent = script;
        }
    }

    private RuleState readLocked(String businessCode) {
        List<Map<String, Object>> headers = jdbc.queryForList("SELECT id,rule_name FROM ac_audit_rule "
                + "WHERE rule_code=? AND del_flag=0 FOR UPDATE", businessCode);
        if (headers.isEmpty()) throw new NoSuchElementException("规则主表不存在：" + businessCode);
        Map<String, Object> header = headers.get(0);
        long id = ((Number) header.get("id")).longValue();
        RuleConfig config = new RuleConfig();
        config.businessCode = businessCode;
        config.businessName = (String) header.get("rule_name");
        config.conditions = jdbc.query("SELECT * FROM ac_audit_rule_cond WHERE rule_id=? AND del_flag=0 "
                        + "ORDER BY rule_sort_no,CASE judge_type_name WHEN 'INTERVAL' THEN 0 ELSE 1 END,"
                        + "cond_sort_no,cond_code",
                (rs, row) -> condition(rs), id);
        return new RuleState(id, config);
    }

    private Condition condition(ResultSet rs) throws SQLException {
        Condition c = new Condition();
        c.id = rs.getLong("id");
        c.ruleName = rs.getString("rule_name");
        c.isDefault = rs.getInt("is_def") != 0;
        c.categoryId1 = rs.getLong("category_id1");
        c.categoryId2 = rs.getLong("category_id2");
        c.categoryId3 = rs.getLong("category_id3");
        c.ruleSort = rs.getInt("rule_sort_no");
        c.conditionCode = rs.getString("cond_code");
        c.conditionType = rs.getString("cond_type_name");
        c.valueField = rs.getString("value_field_name");
        c.predicateType = rs.getString("judge_type_name");
        c.lowerBound = rs.getBigDecimal("lowbound_val");
        c.lowerInclusive = rs.getInt("is_inc_lowbound") != 0;
        c.upperBound = rs.getBigDecimal("upbound_val");
        c.upperInclusive = rs.getInt("is_inc_upbound") != 0;
        c.compType = rs.getString("cmpr_type");
        c.thresholdVal = rs.getBigDecimal("thres");
        try {
            c.resultAssignments = RuleJson.MAPPER.readValue(rs.getString("rslt_assign_info"),
                    new TypeReference<List<Assignment>>() {});
        } catch (IOException invalid) { throw new IllegalStateException("规则结果JSON无效", invalid); }
        c.conditionSort = rs.getInt("cond_sort_no");
        c.scriptContent = rs.getString("script_cont");
        return c;
    }

    private void sync(long ruleSetId, List<Condition> old, List<Condition> next) {
        Map<String, Condition> existing = new HashMap<>();
        for (Condition c : old) existing.put(c.conditionCode, c);
        Map<String, Long> ids = new HashMap<>();
        for (Map<String, Object> row : jdbc.queryForList(
                "SELECT id,cond_code FROM ac_audit_rule_cond WHERE rule_id=?", ruleSetId))
            ids.put((String) row.get("cond_code"), ((Number) row.get("id")).longValue());
        String insertSql = "INSERT INTO ac_audit_rule_cond(" + String.join(",", COLUMNS) + ") VALUES ("
                + String.join(",", Collections.nCopies(COLUMNS.size(), "?")) + ")";
        String updateSql = "UPDATE ac_audit_rule_cond SET " + COLUMNS.subList(1, COLUMNS.size()).stream()
                .map(column -> column + "=?").collect(Collectors.joining(",")) + " WHERE id=?";
        for (Condition c : next) {
            Condition prior = existing.remove(c.conditionCode);
            Long id = ids.get(c.conditionCode);
            Object[] values = values(ruleSetId, c);
            if (id == null) jdbc.update(insertSql, values);
            else if (prior == null || !equivalent(prior, c)) {
                Object[] changes = Arrays.copyOfRange(values, 1, values.length + 1);
                changes[changes.length - 1] = id;
                jdbc.update(updateSql, changes);
            }
        }
        for (Condition removed : existing.values())
            jdbc.update("UPDATE ac_audit_rule_cond SET del_flag=1 WHERE id=?", removed.id);
    }

    private Object[] values(long ruleSetId, Condition c) {
        return new Object[] {ruleSetId, c.ruleName, c.isDefault ? 1 : 0,
                categoryOrZero(c.categoryId1), categoryOrZero(c.categoryId2), categoryOrZero(c.categoryId3),
                c.ruleSort, c.conditionCode, c.conditionType, c.valueField, c.predicateType,
                c.lowerBound, c.lowerInclusive ? 1 : 0, c.upperBound, c.upperInclusive ? 1 : 0,
                c.compType, c.thresholdVal, RuleJson.encode(c.resultAssignments),
                c.scriptContent, c.conditionSort, 0};
    }

    private String key(Condition c) { return c.ruleName + "\u0000" + c.conditionCode; }

    private long categoryOrZero(Long id) { return id == null ? 0L : id; }

    private boolean equivalent(RuleConfig a, RuleConfig b) {
        if (!a.businessName.equals(b.businessName) || a.conditions.size() != b.conditions.size()) return false;
        Map<String, JsonNode> before = new LinkedHashMap<>();
        for (Condition c : a.conditions) before.put(key(c), content(c));
        for (Condition c : b.conditions) if (!content(c).equals(before.remove(key(c)))) return false;
        return before.isEmpty();
    }

    private boolean equivalent(Condition a, Condition b) { return content(a).equals(content(b)); }

    private JsonNode content(Condition condition) {
        ObjectNode node = RuleJson.MAPPER.valueToTree(condition);
        node.remove("id");
        return node;
    }
}
