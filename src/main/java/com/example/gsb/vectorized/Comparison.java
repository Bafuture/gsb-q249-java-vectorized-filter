package com.example.gsb.vectorized;

/** Comparison operators supported by {@link ComparisonPredicate}. */
public enum Comparison {
    /** value &gt; bound */
    GT,
    /** value &lt; bound */
    LT,
    /** value == bound */
    EQ,
    /** low &lt;= value &lt;= high (inclusive on both ends) */
    BETWEEN
}
