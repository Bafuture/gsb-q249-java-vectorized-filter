package com.example.gsb.vectorized;

/**
 * {@code IS NULL} / {@code IS NOT NULL} predicate. Unlike comparisons, null
 * tests always produce a definite result ({@link TriState#TRUE} or
 * {@link TriState#FALSE}); they never produce {@link TriState#UNKNOWN}.
 */
public final class NullPredicate implements FilterPredicate {

    private final String column;
    private final boolean isNull;

    private NullPredicate(String column, boolean isNull) {
        this.column = column;
        this.isNull = isNull;
    }

    public static NullPredicate isNull(String column) {
        return new NullPredicate(column, true);
    }

    public static NullPredicate isNotNull(String column) {
        return new NullPredicate(column, false);
    }

    @Override
    public TriState evaluateRow(RowView row) {
        boolean nullAt = row.value(row.tableColumn(column)) == null;
        return (nullAt == isNull) ? TriState.TRUE : TriState.FALSE;
    }

    @Override
    public SelectionVector evaluateVector(ColumnBatch batch) {
        ColumnVector col = batch.column(batch.columnIndex(column));
        int n = col.size();
        long[] nullMap = col.rawNullMap();
        long[] match = new long[(n + 63) >>> 6];
        for (int i = 0; i < match.length; i++) {
            match[i] = isNull ? nullMap[i] : ~nullMap[i];
        }
        long[] known = new long[match.length];
        java.util.Arrays.fill(known, -1L);
        return SelectionVector.fromBits(n, match, known);
    }

    @Override
    public String toString() {
        return column + (isNull ? " IS NULL" : " IS NOT NULL");
    }
}
