package com.example.gsb.vectorized;

/** A predicate that can be applied to an entire column in one vectorized pass. */
public interface ColumnPredicate {

    /** Evaluates the predicate for every row, including NULL-aware UNKNOWN states. */
    SelectionVector evaluate(ColumnVector column);
}
