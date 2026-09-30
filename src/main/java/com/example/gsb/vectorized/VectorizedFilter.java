package com.example.gsb.vectorized;

import java.util.ArrayList;
import java.util.List;

/**
 * Batch-oriented vectorized filter: applies a predicate to a whole column in one pass
 * and gathers the TRUE rows into a new batch.
 */
public final class VectorizedFilter {

    private VectorizedFilter() {
    }

    /** Evaluates a predicate against one column, producing a tri-state selection vector. */
    public static SelectionVector select(RowBatch batch, String column, ColumnPredicate predicate) {
        return predicate.evaluate(batch.column(column));
    }

    public static RowBatch filter(RowBatch batch, SelectionVector selection) {
        return batch.materialize(selection);
    }

    public static RowBatch filter(RowBatch batch, String column, ColumnPredicate predicate) {
        return batch.materialize(select(batch, column, predicate));
    }

    /**
     * Filters each batch independently. Predicates are row-local, so applying them per
     * batch and concatenating the outputs (in batch order) yields exactly the same result
     * as filtering one big batch: every source row is visited once, hence no gaps or
     * duplicates. Empty result batches are dropped.
     */
    public static List<RowBatch> filterBatches(List<RowBatch> batches, String column, ColumnPredicate predicate) {
        List<RowBatch> filtered = new ArrayList<>(batches.size());
        for (RowBatch batch : batches) {
            RowBatch result = filter(batch, column, predicate);
            if (result.rowCount() > 0) {
                filtered.add(result);
            }
        }
        return filtered;
    }
}
