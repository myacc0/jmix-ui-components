package uz.kapitalbank.umida.dto.dq;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.lang.Nullable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a check of a {@code CROSS_SOURCE_AGGREGATED} rule saw: the row each data source returned, and
 * the configured column pairs with the two values compared. Stored on the check result as JSON, so the
 * reconciliation can be shown later exactly as it was — the data sources may have moved on since.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class DqReconciliation {

    private Source primary;
    private Source secondary;
    private List<Pair> pairs = new ArrayList<>();

    public Source getPrimary() {
        return primary;
    }

    public void setPrimary(Source primary) {
        this.primary = primary;
    }

    public Source getSecondary() {
        return secondary;
    }

    public void setSecondary(Source secondary) {
        this.secondary = secondary;
    }

    public List<Pair> getPairs() {
        return pairs;
    }

    public void setPairs(List<Pair> pairs) {
        this.pairs = pairs;
    }

    /** The pairs whose two values differ. */
    public List<Pair> mismatches() {
        return pairs.stream().filter(pair -> !pair.isMatched()).toList();
    }

    /** One data source and the single row its query returned, column label to value. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Source {
        private String dataSource;
        private Map<String, Object> row = new LinkedHashMap<>();

        public Source() {
        }

        public Source(String dataSource, Map<String, Object> row) {
            this.dataSource = dataSource;
            this.row = row;
        }

        public String getDataSource() {
            return dataSource;
        }

        public void setDataSource(String dataSource) {
            this.dataSource = dataSource;
        }

        public Map<String, Object> getRow() {
            return row;
        }

        public void setRow(Map<String, Object> row) {
            this.row = row;
        }
    }

    /**
     * A configured pair of columns with the values they held. The values match when both are equal
     * numbers, scale aside ({@code 100} equals {@code 100.00}), or when both are NULL.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Pair {
        private String primaryAlias;
        private BigDecimal primaryValue;
        private String secondaryAlias;
        private BigDecimal secondaryValue;
        private boolean matched;

        public Pair() {
        }

        public Pair(String primaryAlias, @Nullable BigDecimal primaryValue,
                    String secondaryAlias, @Nullable BigDecimal secondaryValue) {
            this.primaryAlias = primaryAlias;
            this.primaryValue = primaryValue;
            this.secondaryAlias = secondaryAlias;
            this.secondaryValue = secondaryValue;
            this.matched = primaryValue == null
                    ? secondaryValue == null
                    : secondaryValue != null && primaryValue.compareTo(secondaryValue) == 0;
        }

        public String getPrimaryAlias() {
            return primaryAlias;
        }

        public void setPrimaryAlias(String primaryAlias) {
            this.primaryAlias = primaryAlias;
        }

        public BigDecimal getPrimaryValue() {
            return primaryValue;
        }

        public void setPrimaryValue(BigDecimal primaryValue) {
            this.primaryValue = primaryValue;
        }

        public String getSecondaryAlias() {
            return secondaryAlias;
        }

        public void setSecondaryAlias(String secondaryAlias) {
            this.secondaryAlias = secondaryAlias;
        }

        public BigDecimal getSecondaryValue() {
            return secondaryValue;
        }

        public void setSecondaryValue(BigDecimal secondaryValue) {
            this.secondaryValue = secondaryValue;
        }

        public boolean isMatched() {
            return matched;
        }

        public void setMatched(boolean matched) {
            this.matched = matched;
        }

        /** The first value less the second, or null when either is NULL. */
        @Nullable
        public BigDecimal difference() {
            return primaryValue == null || secondaryValue == null ? null : primaryValue.subtract(secondaryValue);
        }
    }
}
