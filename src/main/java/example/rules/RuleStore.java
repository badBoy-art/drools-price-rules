/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.fasterxml.jackson.annotation.JsonCreator
 *  com.fasterxml.jackson.annotation.JsonProperty
 *  org.springframework.dao.DuplicateKeyException
 *  org.springframework.dao.PessimisticLockingFailureException
 *  org.springframework.jdbc.core.JdbcTemplate
 *  org.springframework.stereotype.Repository
 *  org.springframework.transaction.PlatformTransactionManager
 *  org.springframework.transaction.support.TransactionTemplate
 */
package example.rules;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import example.rules.ApiModels;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class RuleStore {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate publicationTransaction;

    public RuleStore(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.publicationTransaction = new TransactionTemplate(transactionManager);
        this.publicationTransaction.setPropagationBehavior(3);
        this.publicationTransaction.setIsolationLevel(2);
    }

    public Version publish(String name, String configJson, String drl) {
        int attempt = 0;
        while (true) {
            try {
                return (Version)this.publicationTransaction.execute(status -> {
                    List versions = this.jdbc.query("SELECT version FROM rule_version WHERE rule_name=? ORDER BY version DESC LIMIT 1 FOR UPDATE", (rs, row) -> rs.getLong("version"), new Object[]{name});
                    long next = versions.isEmpty() ? 1L : Math.addExact((Long)versions.get(0), 1L);
                    this.jdbc.update("INSERT INTO rule_version(rule_name,version,config_json,drl_content) VALUES (?,?,?,?)", new Object[]{name, next, configJson, drl});
                    return new Version(name, next, configJson, drl);
                });
            }
            catch (DuplicateKeyException | PessimisticLockingFailureException conflict) {
                if (attempt >= 7) {
                    throw conflict;
                }
                ++attempt;
                continue;
            }
        }
    }

    public Version load(String name, Long requestedVersion) {
        Object[] objectArray;
        String sql;
        String string = sql = requestedVersion == null ? "SELECT * FROM rule_version WHERE rule_name=? ORDER BY version DESC LIMIT 1" : "SELECT * FROM rule_version WHERE rule_name=? AND version=?";
        if (requestedVersion == null) {
            Object[] objectArray2 = new Object[1];
            objectArray = objectArray2;
            objectArray2[0] = name;
        } else {
            Object[] objectArray3 = new Object[2];
            objectArray3[0] = name;
            objectArray = objectArray3;
            objectArray3[1] = requestedVersion;
        }
        Object[] args = objectArray;
        List rows = this.jdbc.query(sql, (rs, row) -> new Version(rs.getString("rule_name"), rs.getLong("version"), rs.getString("config_json"), rs.getString("drl_content")), args);
        if (rows.isEmpty()) {
            throw new NoSuchElementException("\u89c4\u5219\u6216\u7248\u672c\u4e0d\u5b58\u5728\uff1a" + name);
        }
        return (Version)rows.get(0);
    }

    public static final class Version
    extends ApiModels.RuleVersion {
        private final String configJson;
        private final String drl;

        @JsonCreator
        public Version(@JsonProperty(value="ruleName") String ruleName, @JsonProperty(value="version") long version, @JsonProperty(value="configJson") String configJson, @JsonProperty(value="drl") String drl) {
            super(ruleName, version);
            this.configJson = configJson;
            this.drl = drl;
        }

        @JsonProperty(value="configJson")
        public String getConfigJson() {
            return this.configJson;
        }

        @JsonProperty(value="drl")
        public String getDrl() {
            return this.drl;
        }

        @Override
        @JsonProperty(value="ruleName")
        public String getRuleName() {
            return super.getRuleName();
        }
    }
}
