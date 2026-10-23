package ru.hackathon.credit.product;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "product")
public class Product {

    public static final String ALWAYS_MANUAL = "ALWAYS_MANUAL";

    @Id
    private String code;

    private String name;
    private String description;

    /** Цели через запятую, значения из {@link Purpose}. */
    private String purposes;

    @Column(name = "min_amount")
    private BigDecimal minAmount;

    @Column(name = "max_amount")
    private BigDecimal maxAmount;

    @Column(name = "min_term_months")
    private int minTermMonths;

    @Column(name = "max_term_months")
    private int maxTermMonths;

    @Column(name = "spread_pct")
    private BigDecimal spreadPct;

    @Column(name = "requires_collateral")
    private boolean requiresCollateral;

    private String routing;

    @Column(name = "auto_limit_amount")
    private BigDecimal autoLimitAmount;

    @Column(name = "min_avg_monthly_turnover")
    private BigDecimal minAvgMonthlyTurnover;

    @Column(name = "sort_order")
    private int sortOrder;

    public Set<Purpose> purposeSet() {
        return Arrays.stream(purposes.split(","))
                .map(String::trim)
                .map(Purpose::valueOf)
                .collect(Collectors.toSet());
    }

    public boolean alwaysManual() {
        return ALWAYS_MANUAL.equals(routing);
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPurposes() {
        return purposes;
    }

    public void setPurposes(String purposes) {
        this.purposes = purposes;
    }

    public BigDecimal getMinAmount() {
        return minAmount;
    }

    public void setMinAmount(BigDecimal minAmount) {
        this.minAmount = minAmount;
    }

    public BigDecimal getMaxAmount() {
        return maxAmount;
    }

    public void setMaxAmount(BigDecimal maxAmount) {
        this.maxAmount = maxAmount;
    }

    public int getMinTermMonths() {
        return minTermMonths;
    }

    public void setMinTermMonths(int minTermMonths) {
        this.minTermMonths = minTermMonths;
    }

    public int getMaxTermMonths() {
        return maxTermMonths;
    }

    public void setMaxTermMonths(int maxTermMonths) {
        this.maxTermMonths = maxTermMonths;
    }

    public BigDecimal getSpreadPct() {
        return spreadPct;
    }

    public void setSpreadPct(BigDecimal spreadPct) {
        this.spreadPct = spreadPct;
    }

    public boolean isRequiresCollateral() {
        return requiresCollateral;
    }

    public void setRequiresCollateral(boolean requiresCollateral) {
        this.requiresCollateral = requiresCollateral;
    }

    public String getRouting() {
        return routing;
    }

    public void setRouting(String routing) {
        this.routing = routing;
    }

    public BigDecimal getAutoLimitAmount() {
        return autoLimitAmount;
    }

    public void setAutoLimitAmount(BigDecimal autoLimitAmount) {
        this.autoLimitAmount = autoLimitAmount;
    }

    public BigDecimal getMinAvgMonthlyTurnover() {
        return minAvgMonthlyTurnover;
    }

    public void setMinAvgMonthlyTurnover(BigDecimal minAvgMonthlyTurnover) {
        this.minAvgMonthlyTurnover = minAvgMonthlyTurnover;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
