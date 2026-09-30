package com.example.gsb.vectorized;

import java.util.ArrayList;
import java.util.List;

/**
 * Batch-oriented vectorized filter.
 *
 * <p>The table is sliced into {@link ColumnBatch}es of a configurable batch
 * size; the predicate is evaluated once per batch into a {@link SelectionVector}
 * (one compact bit operation per row, plus word-wise merges for AND/OR/NOT);
 * the per-batch vectors are concatenated in batch order into a full-table
 * vector and the {@code TRUE} rows are gathered into a {@link MaterializedTable}.
 *
 * <p>Cross-batch correctness: predicates are row-local comparisons against
 * constants, so each batch is evaluated independently on its own slice of the
 * data. Because batches partition {@code [0, rowCount)} contiguously, in
 * order, and {@link SelectionVector#concat(List)} concatenates the bit vectors
 * with exactly the same offsets, no row is skipped or duplicated — including
 * values equal to range boundaries that happen to straddle a batch cut.
 */
public final class VectorizedFilter {

    private final int batchSize;

    public VectorizedFilter(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive: " + batchSize);
        }
        this.batchSize = batchSize;
    }

    public int batchSize() {
        return batchSize;
    }

    /** Evaluates the predicate over every batch and concatenates per-batch vectors. */
    public SelectionVector evaluate(Table table, FilterPredicate predicate) {
        List<ColumnBatch> batches = table.batches(batchSize);
        List<SelectionVector> parts = new ArrayList<>(batches.size());
        for (ColumnBatch batch : batches) {
            parts.add(predicate.evaluateVector(batch));
        }
        return SelectionVector.concat(parts);
    }

    /** Evaluates and materializes the selected rows in source order. */
    public MaterializedTable filter(Table table, FilterPredicate predicate) {
        return MaterializedTable.gather(table, evaluate(table, predicate));
    }
}
