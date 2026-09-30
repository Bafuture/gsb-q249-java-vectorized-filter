package com.example.gsb.vectorized;

/**
 * Fluent factory for building predicate trees. Example:
 *
 * <pre>{@code
 * FilterPredicate p = Predicates.and(
 *         Predicates.gtLong("age", 18),
 *         Predicates.betweenString("city", "A", "M"));
 * }</pre>
 */
public final class Predicates {

    private Predicates() {
    }

    public static ComparisonPredicate gtLong(String column, long bound) {
        return ComparisonPredicate.gtLong(column, bound);
    }

    public static ComparisonPredicate ltLong(String column, long bound) {
        return ComparisonPredicate.ltLong(column, bound);
    }

    public static ComparisonPredicate eqLong(String column, long bound) {
        return ComparisonPredicate.eqLong(column, bound);
    }

    public static ComparisonPredicate betweenLong(String column, long low, long high) {
        return ComparisonPredicate.betweenLong(column, low, high);
    }

    public static ComparisonPredicate gtDouble(String column, double bound) {
        return ComparisonPredicate.gtDouble(column, bound);
    }

    public static ComparisonPredicate ltDouble(String column, double bound) {
        return ComparisonPredicate.ltDouble(column, bound);
    }

    public static ComparisonPredicate eqDouble(String column, double bound) {
        return ComparisonPredicate.eqDouble(column, bound);
    }

    public static ComparisonPredicate betweenDouble(String column, double low, double high) {
        return ComparisonPredicate.betweenDouble(column, low, high);
    }

    public static ComparisonPredicate gtString(String column, String bound) {
        return ComparisonPredicate.gtString(column, bound);
    }

    public static ComparisonPredicate ltString(String column, String bound) {
        return ComparisonPredicate.ltString(column, bound);
    }

    public static ComparisonPredicate eqString(String column, String bound) {
        return ComparisonPredicate.eqString(column, bound);
    }

    public static ComparisonPredicate betweenString(String column, String low, String high) {
        return ComparisonPredicate.betweenString(column, low, high);
    }

    public static NullPredicate isNull(String column) {
        return NullPredicate.isNull(column);
    }

    public static NullPredicate isNotNull(String column) {
        return NullPredicate.isNotNull(column);
    }

    public static LogicalPredicate and(FilterPredicate a, FilterPredicate b) {
        return LogicalPredicate.and(a, b);
    }

    public static LogicalPredicate or(FilterPredicate a, FilterPredicate b) {
        return LogicalPredicate.or(a, b);
    }

    public static LogicalPredicate not(FilterPredicate a) {
        return LogicalPredicate.not(a);
    }
}
