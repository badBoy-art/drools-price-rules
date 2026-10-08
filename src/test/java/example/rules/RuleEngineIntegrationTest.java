package example.rules;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import org.springframework.test.web.servlet.MvcResult;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:rules;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.sql.init.mode=always"
})
@AutoConfigureMockMvc
class RuleEngineIntegrationTest {
    @Autowired ObjectMapper json;
    @Autowired DrlGenerator generator;
    @Autowired PriceRuleAdapter adapter;
    @Autowired DroolsEngine engine;
    @Autowired RuleService service;
    @Autowired RuleStore store;
    @Autowired PricePolicyResolver resolver;
    @Autowired MockMvc http;
    @Autowired PriceValidationService prices;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    private String readFile(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
    private void writeFile(String path, String value) throws Exception {
        Files.write(Paths.get(path), value.getBytes(StandardCharsets.UTF_8));
    }

    private PriceRuleConfig config(String type) throws Exception {
        return json.readValue(readFile("examples/" + type + "-config.json"), PriceRuleConfig.class);
    }
    private String priceDrl(PriceRuleConfig config) { return generator.generate(adapter.toGeneric(config)); }
    private ApprovalPlan.Result run(String type, String value) throws Exception {
        PriceRuleConfig c = config(type);
        return engine.execute(priceDrl(c), new DocumentFact(c.getBusinessType().name(), "default", new BigDecimal(value)));
    }

    @ParameterizedTest
    @CsvSource({
        "-50,MANUAL_APPROVAL,2,2,2", "-49.999,MANUAL_APPROVAL,2,0,1",
        "0,MANUAL_APPROVAL,2,0,1", "0.001,MANUAL_APPROVAL,1,0,1",
        "5,MANUAL_APPROVAL,1,0,1", "5.001,AUTO_APPROVE,0,0,1",
        "10,AUTO_APPROVE,0,0,1", "10.001,MANUAL_APPROVAL,2,0,1",
        "49.999,MANUAL_APPROVAL,2,0,1", "50,MANUAL_APPROVAL,3,2,2",
        "50.001,MANUAL_APPROVAL,3,2,2"
    })
    void salesBoundaries(String value, String decision, int steps, int cc, int matched) throws Exception {
        ApprovalPlan.Result plan = run("sales", value);
        assertThat(plan.getDecision()).isEqualTo(decision);
        assertThat(plan.getApprovalSteps()).hasSize(steps);
        assertThat(plan.getCcAfterApproval()).hasSize(cc);
        assertThat(plan.getMatchedRules()).hasSize(matched);
        if (steps == 3) {
            assertThat(plan.getApprovalSteps().stream().flatMap(s -> s.getApprovers().stream()))
                    .containsExactly("ROLE:PURCHASE_PLUS_1", "USER:DEMO_APPROVER", "ROLE:PURCHASE_PLUS_2");
        }
    }

    @ParameterizedTest
    @CsvSource({"4.999,NO_MATCH,0", "5,MANUAL_APPROVAL,0", "49.999,MANUAL_APPROVAL,0", "50,MANUAL_APPROVAL,2", "50.001,MANUAL_APPROVAL,2"})
    void costBoundaries(String value, String decision, int cc) throws Exception {
        ApprovalPlan.Result plan = run("cost", value);
        assertThat(plan.getDecision()).isEqualTo(decision);
        assertThat(plan.getCcAfterApproval()).hasSize(cc);
    }

    @Test
    void categorySelectsExactlyOnePolicyAndFallsBack() throws Exception {
        PriceRuleConfig c = config("sales");
        assertThat(resolver.resolve(c, Arrays.asList("CAT_1_1", "CAT_1_2", "CAT_1_3")).getId()).isEqualTo("custom1");
        assertThat(resolver.resolve(c, Arrays.asList("UNKNOWN")).getId()).isEqualTo("default");
        ApprovalPlan.Result plan = engine.execute(priceDrl(c), new DocumentFact("SALES_PRICE", "custom1", new BigDecimal("-50")));
        assertThat(plan.getMatchedRules()).containsExactly("sales_price:custom1:band1", "sales_price:custom1:risk1");
    }

    @Test
    void overlappingIntervalsAndRepeatedCategoriesAreRejected() throws Exception {
        PriceRuleConfig c = config("sales");
        PriceRuleConfig.Policy p = c.getPolicies().get(0);
        PriceRuleConfig.Band b = p.getBands().get(1);
        PriceRuleConfig.Band overlapping = new PriceRuleConfig.Band(b.getId(), b.getMin(), true, b.getMax(), b.getMaxInclusive(), b.getAutoApprove(), b.getMode(), b.getApprovers());
        ArrayList<PriceRuleConfig.Band> bands = new java.util.ArrayList<>(p.getBands());
        bands.set(1, overlapping);
        PriceRuleConfig.Policy bad = new PriceRuleConfig.Policy(p.getId(), p.getName(), true, Collections.emptyList(), bands, p.getRisks());
        assertThatThrownBy(() -> priceDrl(new PriceRuleConfig(c.getRuleName(), c.getBusinessType(), Arrays.asList(bad))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("重叠");
        PriceRuleConfig.Policy custom = c.getPolicies().get(1);
        PriceRuleConfig.Policy duplicate = new PriceRuleConfig.Policy("duplicate", "重复", false, custom.getCategoryIds(), custom.getBands(), custom.getRisks());
        assertThatThrownBy(() -> priceDrl(new PriceRuleConfig(c.getRuleName(), c.getBusinessType(), Arrays.asList(p, custom, duplicate))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("同一品类");
    }

    @Test
    void riskRequiresManualApprovalEvenForAnAutoApprovedBase() {
        ApprovalPlan plan = new ApprovalPlan();
        // 故意让风控先触发，证明最终节点顺序与触发顺序无关。
        plan.risk("risk", 0, "SERIAL", new String[] {"ROLE:EXTRA"}, new String[] {"ROLE:CC"});
        plan.base("base", true, "SERIAL", new String[] {});
        assertThat(plan.finish().getDecision()).isEqualTo("MANUAL_APPROVAL");
        assertThat(plan.finish().getMatchedRules()).containsExactly("base", "risk");
    }

    @Test
    void parallelNodesRemainOneStep() {
        ApprovalPlan plan = new ApprovalPlan();
        plan.base("base", false, "PARALLEL", new String[] {"ROLE:A", "ROLE:B"});
        assertThat(plan.finish().getApprovalSteps()).hasSize(1);
        assertThat(plan.finish().getApprovalSteps().get(0).getApprovers()).containsExactly("ROLE:A", "ROLE:B");
    }

    @Test
    void httpPublishesGeneratesDrlExecutesAndKeepsHistoricalVersion() throws Exception {
        String content = readFile("examples/sales-config.json");
        MvcResult published = http.perform(post("/api/rules/sales_price/publish").contentType(MediaType.APPLICATION_JSON).content(content))
                .andExpect(status().isOk()).andExpect(jsonPath("drl").isNotEmpty()).andReturn();
        long version = json.readTree(published.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("version").asLong();
        http.perform(post("/api/rules/execute").contentType(MediaType.APPLICATION_JSON)
                .content(readFile("examples/execute-sales.json")))
                .andExpect(status().isOk()).andExpect(jsonPath("version").value(version))
                .andExpect(jsonPath("plan.decision").value("MANUAL_APPROVAL"))
                .andExpect(jsonPath("plan.approvalSteps.length()").value(3))
                .andExpect(jsonPath("plan.ccAfterApproval.length()").value(2));
        assertThat(service.publish("sales_price", config("sales")).getVersion()).isEqualTo(version + 1);
        http.perform(get("/api/rules/sales_price").param("version", String.valueOf(version)))
                .andExpect(status().isOk()).andExpect(jsonPath("version").value(version));
        Map<String, JsonNode> historicalData = new LinkedHashMap<>();
        historicalData.put("salePrice", json.getNodeFactory().numberNode(100));
        historicalData.put("costPrice", json.getNodeFactory().numberNode(90));
        RuleService.ExecutionResult historical = service.execute(new RuleService.ExecutionRequest("sales_price", version,
                new RuleService.Document("OLD-1", Collections.emptyList(), null, null, historicalData)));
        assertThat(historical.getVersion()).isEqualTo(version);
        assertThat(historical.getPlan().getDecision()).isEqualTo("AUTO_APPROVE");
    }

    @Test
    void malformedDrlDoesNotCompile() {
        assertThatThrownBy(() -> engine.compile("rule \"broken\" when invalid syntax then end"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("编译失败");
    }

    @Test
    void concurrentSessionsDoNotShareDocumentOrPlan() throws Exception {
        String drl = priceDrl(config("sales"));
        engine.compile(drl);
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Future<Boolean>> tasks = IntStream.range(0, 20).mapToObj(i -> executor.submit(() -> {
                String value = i % 2 == 0 ? "10" : "50";
                ApprovalPlan.Result plan = engine.execute(drl, new DocumentFact("SALES_PRICE", "default", new BigDecimal(value)));
                assertThat(plan.getApprovalSteps()).hasSize(i % 2 == 0 ? 0 : 3);
                return true;
            })).collect(Collectors.toList());
            for (Future<Boolean> task : tasks) assertThat(task.get()).isTrue();
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void concurrentPublishingAllocatesDistinctVersions() throws Exception {
        PriceRuleConfig original = config("cost");
        PriceRuleConfig config = new PriceRuleConfig("concurrent_cost", original.getBusinessType(), original.getPolicies());
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Future<Long>> tasks = IntStream.range(0, 4).mapToObj(i -> executor.submit(() -> service.publish(config.getRuleName(), config).getVersion())).collect(Collectors.toList());
            java.util.Set<Long> versions = new java.util.HashSet<Long>();
            for (Future<Long> task : tasks) versions.add(task.get());
            assertThat(versions).containsExactlyInAnyOrder(1L, 2L, 3L, 4L);
            assertThat(store.load(config.getRuleName(), null).getVersion()).isEqualTo(4);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void invalidInputReturns400AndMissingRuleReturns404() throws Exception {
        http.perform(post("/api/rules/execute").contentType(MediaType.APPLICATION_JSON)
                .content("{\"rule_name\":\"missing\",\"document\":{\"id\":\"X\",\"categoryPath\":[]}}"))
                .andExpect(status().isNotFound());
        service.publish("cost_price", config("cost"));
        http.perform(post("/api/rules/execute").contentType(MediaType.APPLICATION_JSON)
                .content("{\"rule_name\":\"cost_price\",\"document\":{\"id\":\"X\",\"categoryPath\":[]}}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void exportGeneratedRulesAndExampleResult() throws Exception {
        for (String type : Arrays.asList("sales", "cost")) {
            String drl = priceDrl(config(type));
            engine.compile(drl);
            writeFile("examples/" + type + ".drl", drl);
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("rule_name", "sales_price");
        result.put("policyId", "default");
        result.put("plan", run("sales", "50"));
        writeFile("examples/result-sales-50.json", json.writerWithDefaultPrettyPrinter().writeValueAsString(result) + "\n");
    }

    @Test
    void singleProductHttpCalculatesMarginAndSelectsCategory() throws Exception {
        http.perform(post("/api/prices/validate").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
    }
    @Test
    void batchHttpPreservesOrderAndContinuesAfterInvalidProduct() throws Exception {
        http.perform(post("/api/prices/validate-batch").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
    }
    @Test
    void costPriceHttpCalculatesIncreaseAndReturnsRiskCc() throws Exception {
        http.perform(post("/api/prices/validate").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
    }
    @Test
    void exactPriceComparisonDoesNotRoundAcrossBoundary() throws Exception {
        PriceRuleConfig base = config("sales");
        PriceRuleConfig fractional = new PriceRuleConfig("fractional_sales", base.getBusinessType(), base.getPolicies());
        service.publish(fractional.getRuleName(), fractional);
        // 真正毛利率略大于10%，展示值保留12位小数时为10%，仍需人工审批。
        PriceValidationApi.Product product = new PriceValidationApi.Product("FRACTIONAL", Collections.emptyList(),
                new BigDecimal("1000"), new BigDecimal("899.999999999999"), null, null);
        PriceValidationApi.ProductResult result = prices.validate(new PriceValidationApi.SingleRequest(
                fractional.getRuleName(), null, "PRECISION-1", product));
        assertThat(result.getGrossMarginPct()).isEqualByComparingTo("10");
        assertThat(result.getEvaluation().getPlan().getDecision()).isEqualTo("MANUAL_APPROVAL");
    }

    @Test
    void batchPinsOneVersionDuringConcurrentPublication() throws Exception {
        PriceRuleConfig original = config("sales");
        PriceRuleConfig batchConfig = new PriceRuleConfig("batch_sales", original.getBusinessType(), original.getPolicies());
        long first = service.publish(batchConfig.getRuleName(), batchConfig).getVersion();
        PriceValidationApi.Product product = new PriceValidationApi.Product("SKU", Collections.emptyList(),
                new BigDecimal("100"), new BigDecimal("90"), null, null);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        try {
            Future<PriceValidationApi.BatchResult> batch = executor.submit(() -> {
                start.await();
                return prices.validateBatch(new PriceValidationApi.BatchRequest(batchConfig.getRuleName(), null,
                        "BATCH-VERSION", Collections.nCopies(100, product)));
            });
            Future<Long> published = executor.submit(() -> {
                start.await();
                return service.publish(batchConfig.getRuleName(), batchConfig).getVersion();
            });
            start.countDown();
            PriceValidationApi.BatchResult result = batch.get();
            assertThat(published.get()).isEqualTo(first + 1);
            assertThat(result.getVersion()).isIn(first, first + 1);
            assertThat(result.getItems()).hasSize(100);
            assertThat(result.getItems()).allSatisfy(item ->
                    assertThat(item.getResult().getEvaluation().getVersion()).isEqualTo(result.getVersion()));
        } finally { executor.shutdown(); }
    }

    @Test
    void batchHistoricalVersionAndAllInvalidItemsAreHandled() throws Exception {
        long version = service.publish("cost_price", config("cost")).getVersion();
        service.publish("cost_price", config("cost"));
        PriceValidationApi.Product zero = new PriceValidationApi.Product("BAD", Collections.emptyList(), null, null,
                BigDecimal.ZERO, BigDecimal.TEN);
        PriceValidationApi.BatchResult result = prices.validateBatch(new PriceValidationApi.BatchRequest(
                "cost_price", version, "INVALID-BATCH", Arrays.asList(null, zero)));
        assertThat(result.getVersion()).isEqualTo(version);
        assertThat(result.getSuccessCount()).isZero();
        assertThat(result.getErrorCount()).isEqualTo(2);
        assertThat(result.getItems().get(0).getProductId()).isNull();
        assertThat(result.getItems().get(1).getError().getMessage()).contains("originalCostPrice");
    }

    @Test
    void invalidBatchEnvelopeAndSinglePricesReturnHttpErrors() throws Exception {
        http.perform(post("/api/prices/validate-batch").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
    }
    @ParameterizedTest
    @CsvSource({"150,-50,2,2", "95,5,1,0", "0,100,3,2"})
    void rawSalesPricesHandleLossThresholdAndZeroCost(String cost, String margin, int steps, int cc) throws Exception {
        service.publish("sales_price", config("sales"));
        PriceValidationApi.Product product = new PriceValidationApi.Product("SKU", Collections.emptyList(),
                new BigDecimal("100"), new BigDecimal(cost), null, null);
        PriceValidationApi.ProductResult result = prices.validate(new PriceValidationApi.SingleRequest(
                "sales_price", null, "RAW-SALES", product));
        assertThat(result.getGrossMarginPct()).isEqualByComparingTo(margin);
        assertThat(result.getEvaluation().getPlan().getApprovalSteps()).hasSize(steps);
        assertThat(result.getEvaluation().getPlan().getCcAfterApproval()).hasSize(cc);
    }

    @Test
    void rawCostBelowConfiguredThresholdReturnsNoMatch() throws Exception {
        service.publish("cost_price", config("cost"));
        PriceValidationApi.Product product = new PriceValidationApi.Product("SKU", Collections.emptyList(), null, null,
                new BigDecimal("100"), new BigDecimal("104.999"));
        PriceValidationApi.ProductResult result = prices.validate(new PriceValidationApi.SingleRequest(
                "cost_price", null, "RAW-COST", product));
        assertThat(result.getCostIncreasePct()).isEqualByComparingTo("4.999");
        assertThat(result.getEvaluation().getPlan().getDecision()).isEqualTo("NO_MATCH");
    }

    @Test
    void onlyRuleVersionTableIsRequiredAndItsAuditFieldsStillWork() throws Exception {
        PriceRuleConfig original = config("cost");
        PriceRuleConfig auditConfig = new PriceRuleConfig("audit_cost", original.getBusinessType(), original.getPolicies());
        RuleStore.Version version = service.publish(auditConfig.getRuleName(), auditConfig);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE LOWER(table_name)='rule_execution'", Integer.class)).isZero();
        RuleService.ExecutionResult execution = service.execute(new RuleService.ExecutionRequest(auditConfig.getRuleName(),
                version.getVersion(), new RuleService.Document("AUDIT-1", Collections.emptyList(), null, BigDecimal.TEN)));
        assertThat(execution.getExecutionId()).isNotBlank();
        jdbc.query("SELECT creator_id,created_date,updater_id,updated_date FROM rule_version WHERE rule_name=? AND version=?",
                rs -> {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("creator_id")).isEmpty();
                    assertThat(rs.getString("updater_id")).isEmpty();
                    assertThat(rs.getTimestamp("created_date")).isNotNull();
                    assertThat(rs.getTimestamp("updated_date")).isNotNull();
                    return null;
                }, version.getRuleName(), version.getVersion());
        jdbc.update("UPDATE rule_version SET created_date='2000-01-01 00:00:00',updated_date='2000-01-01 00:00:00' WHERE rule_name=? AND version=?",
                version.getRuleName(), version.getVersion());
        jdbc.update("UPDATE rule_version SET updater_id='user-001' WHERE rule_name=? AND version=?", version.getRuleName(), version.getVersion());
        jdbc.query("SELECT creator_id,updater_id,created_date,updated_date FROM rule_version WHERE rule_name=? AND version=?", rs -> {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("creator_id")).isEmpty();
            assertThat(rs.getString("updater_id")).isEqualTo("user-001");
            assertThat(rs.getTimestamp("created_date")).isEqualTo(java.sql.Timestamp.valueOf("2000-01-01 00:00:00"));
            assertThat(rs.getTimestamp("updated_date")).isAfter(rs.getTimestamp("created_date"));
            return null;
        }, version.getRuleName(), version.getVersion());
    }

    @Test
    void failedPublicationLeavesLatestVersionAndHistoryIntact() throws Exception {
        PriceRuleConfig original = config("cost");
        PriceRuleConfig rollbackConfig = new PriceRuleConfig("rollback_cost", original.getBusinessType(), original.getPolicies());
        RuleStore.Version previous = service.publish(rollbackConfig.getRuleName(), rollbackConfig);
        assertThatThrownBy(() -> store.publish(rollbackConfig.getRuleName(), null, previous.getDrl()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(store.load(rollbackConfig.getRuleName(), null).getVersion()).isEqualTo(previous.getVersion());
        assertThat(store.load(rollbackConfig.getRuleName(), previous.getVersion()).getDrl()).isEqualTo(previous.getDrl());
        assertThat(service.publish(rollbackConfig.getRuleName(), rollbackConfig).getVersion()).isEqualTo(previous.getVersion() + 1);
    }
}
