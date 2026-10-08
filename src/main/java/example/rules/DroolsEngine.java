/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.kie.api.KieBase
 *  org.kie.api.KieServices
 *  org.kie.api.KieServices$Factory
 *  org.kie.api.builder.KieBuilder
 *  org.kie.api.builder.KieFileSystem
 *  org.kie.api.builder.Message$Level
 *  org.kie.api.builder.ReleaseId
 *  org.kie.api.runtime.KieContainer
 *  org.kie.api.runtime.KieSession
 *  org.springframework.stereotype.Component
 */
package example.rules;

import example.rules.ApprovalPlan;
import example.rules.DocumentFact;
import example.rules.GenericFact;
import example.rules.GenericRuleResult;
import example.rules.PriceRuleAdapter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.kie.api.KieBase;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.Message;
import org.kie.api.builder.ReleaseId;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.springframework.stereotype.Component;

@Component
public class DroolsEngine {
    private final Map<String, KieBase> bases = new LinkedHashMap<String, KieBase>(64, 0.75f, true){

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, KieBase> eldest) {
            return this.size() > 64;
        }
    };

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public synchronized KieBase compile(String drl) {
        String key = this.hash(drl);
        KieBase existing = this.bases.get(key);
        if (existing != null) {
            return existing;
        }
        KieServices services = KieServices.Factory.get();
        ReleaseId releaseId = services.newReleaseId("example.generated", "rules-" + UUID.randomUUID(), "1.0.0");
        KieFileSystem files = services.newKieFileSystem();
        files.generateAndWritePomXML(releaseId);
        files.write("src/main/resources/example/generated/rules.drl", drl);
        try {
            KieBase kieBase;
            KieBuilder builder = services.newKieBuilder(files, DocumentFact.class.getClassLoader()).buildAll();
            List errors = builder.getResults().getMessages(new Message.Level[]{Message.Level.ERROR});
            if (!errors.isEmpty()) {
                throw new IllegalArgumentException("DRL\u7f16\u8bd1\u5931\u8d25\uff1a" + errors);
            }
            KieContainer container = services.newKieContainer(releaseId, DocumentFact.class.getClassLoader());
            try {
                KieBase base = container.getKieBase();
                this.bases.put(key, base);
                kieBase = base;
            }
            catch (Throwable throwable) {
                container.dispose();
                throw throwable;
            }
            container.dispose();
            return kieBase;
        }
        finally {
            services.getRepository().removeKieModule(releaseId);
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public ApprovalPlan.Result execute(String drl, DocumentFact document) {
        if (!drl.contains("global example.rules.ApprovalPlan plan;")) {
            return this.evaluate(drl, PriceRuleAdapter.fact(document)).getPlan();
        }
        KieSession session = this.compile(drl).newKieSession();
        ApprovalPlan plan = new ApprovalPlan();
        try {
            session.setGlobal("plan", (Object)plan);
            session.insert((Object)document);
            session.fireAllRules();
            ApprovalPlan.Result result = plan.finish();
            return result;
        }
        finally {
            session.dispose();
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public GenericRuleResult.Result evaluate(String drl, GenericFact document) {
        KieSession session = this.compile(drl).newKieSession();
        GenericRuleResult result = new GenericRuleResult();
        try {
            session.setGlobal("result", (Object)result);
            session.insert((Object)document);
            session.fireAllRules();
            GenericRuleResult.Result result2 = result.finish();
            return result2;
        }
        finally {
            session.dispose();
        }
    }

    private String hash(String drl) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(drl.getBytes(StandardCharsets.UTF_8));
            char[] digits = "0123456789abcdef".toCharArray();
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(digits[(value & 0xFF) >>> 4]).append(digits[value & 0xF]);
            }
            return hex.toString();
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

