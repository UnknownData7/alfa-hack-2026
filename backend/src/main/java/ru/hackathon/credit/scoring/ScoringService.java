package ru.hackathon.credit.scoring;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import ru.hackathon.credit.application.LoanApplication;
import ru.hackathon.credit.common.Json;
import ru.hackathon.credit.company.Company;
import ru.hackathon.credit.company.CompanyFinancials;
import ru.hackathon.credit.company.CompanyService;
import ru.hackathon.credit.integration.CompanyRegistryClient;
import ru.hackathon.credit.integration.CreditBureauClient;
import ru.hackathon.credit.integration.ExternalSourceUnavailableException;
import ru.hackathon.credit.integration.TaxServiceClient;
import ru.hackathon.credit.pricing.PricingCalculator;
import ru.hackathon.credit.product.Product;

/**
 * Запуск скоринга по заявке: обогащение данными внешних источников, расчёт, сохранение истории запусков.
 */
@Service
public class ScoringService {

    private static final Logger log = LoggerFactory.getLogger(ScoringService.class);

    public static final String INITIAL = "INITIAL";
    public static final String RECALC = "RECALC";

    private final CompanyService companies;
    private final CompanyRegistryClient registry;
    private final CreditBureauClient bureau;
    private final TaxServiceClient tax;
    private final ScoringEngine engine;
    private final PricingCalculator pricing;
    private final ScoringRunRepository runs;
    private final ExternalCheckResultRepository checks;
    private final Json json;

    public ScoringService(CompanyService companies, CompanyRegistryClient registry, CreditBureauClient bureau,
                          TaxServiceClient tax, ScoringEngine engine, PricingCalculator pricing,
                          ScoringRunRepository runs, ExternalCheckResultRepository checks, Json json) {
        this.companies = companies;
        this.registry = registry;
        this.bureau = bureau;
        this.tax = tax;
        this.engine = engine;
        this.pricing = pricing;
        this.runs = runs;
        this.checks = checks;
        this.json = json;
    }

    /** Корректировки андеррайтера при пересчёте. Пустое поле означает «как в заявке». */
    public record Overrides(BigDecimal confirmedAnnualTurnover, BigDecimal amount, Integer termMonths) {
        public static final Overrides NONE = new Overrides(null, null, null);
    }

    /** Результат запуска вместе с данными, которые нужны кредитной логике. */
    public record Run(ScoringRun entity, ScoringOutcome outcome, List<String> unavailableSources, double amount,
                      int termMonths, double avgMonthlyTurnover, double monthlyObligations, int maxDelinquencyDays,
                      int businessAgeMonths, double annualTurnover, String industryRisk) {
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Run run(LoanApplication app, Product product, String trigger, Overrides overrides, String createdBy) {
        Company company = companies.get(app.getCompanyId());
        CompanyFinancials fin = companies.financials(app.getCompanyId());
        List<String> unavailable = new ArrayList<>();

        boolean active = true;
        String statusText = "нет данных";
        try {
            CompanyRegistryClient.RegistryInfo info = registry.lookup(company.getInn());
            active = info.active();
            statusText = info.statusText();
            saveCheck(app.getId(), "REGISTRY", "OK", info);
        } catch (ExternalSourceUnavailableException e) {
            unavailable.add("реестр компаний");
            saveCheck(app.getId(), "REGISTRY", "UNAVAILABLE", Map.of("error", e.getMessage()));
        }

        boolean bureauOk = true;
        int delinquency = 0;
        try {
            CreditBureauClient.BureauReport report = bureau.report(company.getInn());
            delinquency = report.maxDelinquencyDays();
            saveCheck(app.getId(), "BUREAU", "OK", report);
        } catch (ExternalSourceUnavailableException e) {
            bureauOk = false;
            unavailable.add("бюро кредитных историй");
            saveCheck(app.getId(), "BUREAU", "UNAVAILABLE", Map.of("error", e.getMessage()));
        }

        boolean taxDebt = false;
        try {
            TaxServiceClient.TaxInfo info = tax.check(company.getInn());
            taxDebt = info.hasDebt();
            saveCheck(app.getId(), "TAX", "OK", info);
        } catch (ExternalSourceUnavailableException e) {
            unavailable.add("сведения ФНС");
            saveCheck(app.getId(), "TAX", "UNAVAILABLE", Map.of("error", e.getMessage()));
        }

        double annual = overrides.confirmedAnnualTurnover() != null
                ? overrides.confirmedAnnualTurnover().doubleValue() : fin.getAnnualTurnover12m().doubleValue();
        double avgMonthly = overrides.confirmedAnnualTurnover() != null
                ? annual / 12.0 : fin.getAvgMonthlyTurnover().doubleValue();
        double amount = overrides.amount() != null ? overrides.amount().doubleValue() : app.getRequestedAmount().doubleValue();
        int term = overrides.termMonths() != null ? overrides.termMonths() : app.getRequestedTermMonths();
        double obligations = monthlyObligations(app, fin);
        double estimatedPayment = PricingCalculator.annuity(amount, pricing.estimateRate(product.getSpreadPct().doubleValue()), term);
        int age = company.businessAgeMonths(LocalDate.now());

        ScoringInput input = new ScoringInput(age, amount, annual, avgMonthly, fin.getTurnoverGrowthPct().doubleValue(),
                obligations, estimatedPayment, delinquency, bureauOk, taxDebt, company.getIndustryRisk(), active,
                statusText);
        ScoringOutcome outcome = engine.score(input);

        ScoringRun entity = new ScoringRun();
        entity.setApplicationId(app.getId());
        entity.setRunNo(runs.findTopByApplicationIdOrderByRunNoDesc(app.getId()).map(r -> r.getRunNo() + 1).orElse(1));
        entity.setTotalScore(outcome.totalScore());
        entity.setGrade(outcome.grade().name());
        entity.setHardStop(outcome.hardStop());
        entity.setHardStopReason(outcome.hardStopReason());
        entity.setPaymentLoad(BigDecimal.valueOf(outcome.paymentLoad()).setScale(3, RoundingMode.HALF_UP));
        entity.setFactors(json.write(outcome.factors()));
        entity.setInputsSnapshot(json.write(snapshot(input, term, overrides, unavailable)));
        entity.setTrigger(trigger);
        entity.setCreatedBy(createdBy);
        runs.save(entity);
        log.info("scoring application={} run={} score={} grade={} hardStop={}", app.getId(), entity.getRunNo(),
                outcome.totalScore(), outcome.grade(), outcome.hardStop());

        return new Run(entity, outcome, List.copyOf(unavailable), amount, term, avgMonthly, obligations, delinquency,
                age, annual, company.getIndustryRisk());
    }

    /** Текущие платежи: берём большее из данных банка и заявленного клиентом. */
    public static double monthlyObligations(LoanApplication app, CompanyFinancials fin) {
        double existing = fin.getExistingMonthlyObligations().doubleValue();
        double declared = app.getMonthlyObligationsDeclared() == null ? 0 : app.getMonthlyObligationsDeclared().doubleValue();
        return Math.max(existing, declared);
    }

    public List<Factor> factors(ScoringRun run) {
        return json.read(run.getFactors(), new TypeReference<List<Factor>>() { });
    }

    public Map<String, Object> inputs(ScoringRun run) {
        return json.read(run.getInputsSnapshot(), new TypeReference<Map<String, Object>>() { });
    }

    private Map<String, Object> snapshot(ScoringInput in, int term, Overrides overrides, List<String> unavailable) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("businessAgeMonths", in.businessAgeMonths());
        m.put("requestedAmount", in.requestedAmount());
        m.put("termMonths", term);
        m.put("annualTurnover", in.annualTurnover());
        m.put("avgMonthlyTurnover", Math.round(in.avgMonthlyTurnover()));
        m.put("turnoverGrowthPct", in.turnoverGrowthPct());
        m.put("monthlyObligations", in.monthlyObligations());
        m.put("estimatedMonthlyPayment", in.estimatedMonthlyPayment());
        m.put("maxDelinquencyDays", in.maxDelinquencyDays());
        m.put("bureauAvailable", in.bureauAvailable());
        m.put("hasTaxDebt", in.hasTaxDebt());
        m.put("industryRisk", in.industryRisk());
        m.put("companyActive", in.companyActive());
        m.put("companyStatus", in.companyStatusText());
        m.put("turnoverConfirmedByUnderwriter", overrides.confirmedAnnualTurnover() != null);
        m.put("unavailableSources", unavailable);
        return m;
    }

    private void saveCheck(Long applicationId, String source, String status, Object payload) {
        ExternalCheckResult r = new ExternalCheckResult();
        r.setApplicationId(applicationId);
        r.setSource(source);
        r.setStatus(status);
        r.setPayload(json.write(payload));
        checks.save(r);
    }
}
