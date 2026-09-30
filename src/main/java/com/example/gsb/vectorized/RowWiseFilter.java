package com.example.gsb.vectorized;

import java.util.ArrayList;
import java.util.List;

/**
 * Reference, deliberately non-vectorized implementation: iterates rows one at a time,
 * boxes each value and evaluates the predicate per row. It exists as the ground-truth
 * oracle that the vectorized implementation is tested against.
 */
public final class RowWiseFilter {

    private RowWiseFilter() {
    }

    public static SelectionVector select(RowBatch batch, String column, ComparisonPredicate predicate) {
        ColumnVector input = batch.column(column);
        int n = batch.rowCount();
        byte[] states = new byte[n];
        for (int i = 0; i < n; i++) {
            if (input.isNull(i)) {
                states[i] = SelectionVector.UNKNOWN;
            } else {
                states[i] = predicate.test(input.valueAt(i))
                        ? SelectionVector.TRUE
                        : SelectionVector.FALSE;
            }
        }
        return new SelectionVector(states);
    }

    public static RowBatch filter(RowBatch batch, String column, ComparisonPredicate predicate) {
        return batch.materialize(select(batch, column, predicate));
    }

    public static List<RowBatch> filterBatches(List<RowBatch> batches, String column, ComparisonPredicate predicate) {
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
