package com.example.gsb.vectorized;

/**
 * A predicate that can be evaluated both row-wise (reference semantics) and
 * column-wise over a {@link ColumnBatch} (vectorized fast path). The two paths
 * must agree exactly on every row, including three-valued null handling.
 *
 * <p>Build predicate trees with the static factories in {@link Predicates}.
 */
public interface FilterPredicate {

    /** Row-wise three-valued evaluation at the row view's current position. */
    TriState evaluateRow(RowView row);

    /** Vectorized evaluation over every row of the batch. */
    SelectionVector evaluateVector(ColumnBatch batch);
}
