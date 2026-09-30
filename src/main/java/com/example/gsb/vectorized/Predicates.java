package com.example.gsb.vectorized;

/** Factories for the built-in comparison predicates. */
public final class Predicates {

    private Predicates() {
    }

    public static ComparisonPredicate greaterThan(Comparable<?> value) {
        return new ComparisonPredicate(ComparisonPredicate.Op.GT, value, null);
    }

    public static ComparisonPredicate lessThan(Comparable<?> value) {
        return new ComparisonPredicate(ComparisonPredicate.Op.LT, value, null);
    }

    public static ComparisonPredicate equalTo(Comparable<?> value) {
        return new ComparisonPredicate(ComparisonPredicate.Op.EQ, value, null);
    }

    /** Inclusive range predicate: {@code lower <= value <= upper}. */
    public static ComparisonPredicate between(Comparable<?> lower, Comparable<?> upper) {
        return new ComparisonPredicate(ComparisonPredicate.Op.BETWEEN, lower, upper);
    }
}
