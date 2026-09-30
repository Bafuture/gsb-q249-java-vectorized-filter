package com.example.gsb.vectorized;

import java.util.ArrayList;
import java.util.List;

/**
 * Reference row-wise filter: walks the table one row at a time, boxes the
 * column values through {@link RowView}, and evaluates the predicate tree
 * per row. This is the simple-but-slow implementation whose results the
 * vectorized engine must match exactly.
 */
public final class RowWiseFilter {

    /** Returns the three-valued result for every row, in order. */
    public List<TriState> evaluate(Table table, FilterPredicate predicate) {
        RowView row = new RowView(table);
        List<TriState> results = new ArrayList<>(table.rowCount());
        for (int i = 0; i < table.rowCount(); i++) {
            row.moveTo(i);
            results.add(predicate.evaluateRow(row));
        }
        return results;
    }

    /** Materializes rows whose result is TRUE, preserving source order. */
    public MaterializedTable filter(Table table, FilterPredicate predicate) {
        SelectionVector selected = selectionVector(table, predicate);
        return MaterializedTable.gather(table, selected);
    }

    /** Builds a full-table selection vector from the row-wise results. */
    public SelectionVector selectionVector(Table table, FilterPredicate predicate) {
        int n = table.rowCount();
        long[] match = new long[(n + 63) >>> 6];
        long[] known = new long[match.length];
        RowView row = new RowView(table);
        for (int i = 0; i < n; i++) {
            row.moveTo(i);
            TriState ts = predicate.evaluateRow(row);
            if (ts != TriState.UNKNOWN) {
                known[i >>> 6] |= 1L << (i & 63);
                if (ts == TriState.TRUE) {
                    match[i >>> 6] |= 1L << (i & 63);
                }
            }
        }
        return SelectionVector.fromBits(n, match, known);
    }
}
