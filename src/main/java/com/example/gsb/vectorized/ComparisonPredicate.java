package com.example.gsb.vectorized;

/**
 * A comparison predicate over one column: {@code GT}, {@code LT}, {@code EQ}
 * or inclusive {@code BETWEEN} against constant bound(s).
 *
 * <p>Vectorized evaluation scans the column's primitive value array in a
 * tight loop, writing one match bit per row, then clears the {@code known}
 * bit for every null row so nulls evaluate to {@link TriState#UNKNOWN} — the
 * exact same result as the row-wise path, which boxes each cell and compares
 * individually.
 */
public final class ComparisonPredicate implements FilterPredicate {

    private final String column;
    private final Comparison op;
    private final long longLow;
    private final long longHigh;
    private final double doubleLow;
    private final double doubleHigh;
    private final String stringLow;
    private final String stringHigh;
    private final ColumnType operandType;

    private ComparisonPredicate(String column, Comparison op,
                                long longLow, long longHigh,
                                double doubleLow, double doubleHigh,
                                String stringLow, String stringHigh,
                                ColumnType operandType) {
        this.column = column;
        this.op = op;
        this.longLow = longLow;
        this.longHigh = longHigh;
        this.doubleLow = doubleLow;
        this.doubleHigh = doubleHigh;
        this.stringLow = stringLow;
        this.stringHigh = stringHigh;
        this.operandType = operandType;
    }

    public static ComparisonPredicate gtLong(String column, long bound) {
        return new ComparisonPredicate(column, Comparison.GT, bound, 0, 0, 0, null, null, ColumnType.LONG);
    }

    public static ComparisonPredicate ltLong(String column, long bound) {
        return new ComparisonPredicate(column, Comparison.LT, bound, 0, 0, 0, null, null, ColumnType.LONG);
    }

    public static ComparisonPredicate eqLong(String column, long bound) {
        return new ComparisonPredicate(column, Comparison.EQ, bound, 0, 0, 0, null, null, ColumnType.LONG);
    }

    public static ComparisonPredicate betweenLong(String column, long low, long high) {
        return new ComparisonPredicate(column, Comparison.BETWEEN, low, high, 0, 0, null, null, ColumnType.LONG);
    }

    public static ComparisonPredicate gtDouble(String column, double bound) {
        return new ComparisonPredicate(column, Comparison.GT, 0, 0, bound, 0, null, null, ColumnType.DOUBLE);
    }

    public static ComparisonPredicate ltDouble(String column, double bound) {
        return new ComparisonPredicate(column, Comparison.LT, 0, 0, bound, 0, null, null, ColumnType.DOUBLE);
    }

    public static ComparisonPredicate eqDouble(String column, double bound) {
        return new ComparisonPredicate(column, Comparison.EQ, 0, 0, bound, 0, null, null, ColumnType.DOUBLE);
    }

    public static ComparisonPredicate betweenDouble(String column, double low, double high) {
        return new ComparisonPredicate(column, Comparison.BETWEEN, 0, 0, low, high, null, null, ColumnType.DOUBLE);
    }

    public static ComparisonPredicate gtString(String column, String bound) {
        return new ComparisonPredicate(column, Comparison.GT, 0, 0, 0, 0, bound, null, ColumnType.STRING);
    }

    public static ComparisonPredicate ltString(String column, String bound) {
        return new ComparisonPredicate(column, Comparison.LT, 0, 0, 0, 0, bound, null, ColumnType.STRING);
    }

    public static ComparisonPredicate eqString(String column, String bound) {
        return new ComparisonPredicate(column, Comparison.EQ, 0, 0, 0, 0, bound, null, ColumnType.STRING);
    }

    public static ComparisonPredicate betweenString(String column, String low, String high) {
        return new ComparisonPredicate(column, Comparison.BETWEEN, 0, 0, 0, 0, low, high, ColumnType.STRING);
    }

    public String column() {
        return column;
    }

    public Comparison op() {
        return op;
    }

    @Override
    public TriState evaluateRow(RowView row) {
        int idx = row.tableColumn(column);
        Object value = row.value(idx);
        if (value == null) {
            return TriState.UNKNOWN;
        }
        return compareRow(value) ? TriState.TRUE : TriState.FALSE;
    }

    private boolean compareRow(Object value) {
        return switch (operandType) {
            case LONG -> compareLong((Long) value);
            case DOUBLE -> compareDouble((Double) value);
            case STRING -> compareString((String) value);
        };
    }

    private boolean compareLong(long v) {
        return switch (op) {
            case GT -> v > longLow;
            case LT -> v < longLow;
            case EQ -> v == longLow;
            case BETWEEN -> v >= longLow && v <= longHigh;
        };
    }

    private boolean compareDouble(double v) {
        return switch (op) {
            case GT -> v > doubleLow;
            case LT -> v < doubleLow;
            case EQ -> v == doubleLow;
            case BETWEEN -> v >= doubleLow && v <= doubleHigh;
        };
    }

    private boolean compareString(String v) {
        return switch (op) {
            case GT -> v.compareTo(stringLow) > 0;
            case LT -> v.compareTo(stringLow) < 0;
            case EQ -> v.equals(stringLow);
            case BETWEEN -> v.compareTo(stringLow) >= 0 && v.compareTo(stringHigh) <= 0;
        };
    }

    @Override
    public SelectionVector evaluateVector(ColumnBatch batch) {
        ColumnVector col = batch.column(batch.columnIndex(column));
        if (col.type() != operandType) {
            throw new IllegalArgumentException(
                    "Predicate operand type " + operandType + " does not match column '"
                            + column + "' of type " + col.type());
        }
        int n = col.size();
        long[] match = new long[(n + 63) >>> 6];
        switch (operandType) {
            case LONG -> evalLong(col.rawLongs(), n, match);
            case DOUBLE -> evalDouble(col.rawDoubles(), n, match);
            case STRING -> evalString(col.rawStrings(), n, match);
        }
        long[] known = knownBits(col.rawNullMap(), n);
        // Enforce the invariant UNKNOWN => match=0: value slots backing null rows
        // can contain arbitrary leftovers that might otherwise satisfy a compare.
        for (int i = 0; i < match.length; i++) {
            match[i] &= known[i];
        }
        return SelectionVector.fromBits(n, match, known);
    }

    private static long[] knownBits(long[] nullMap, int n) {
        long[] known = new long[(n + 63) >>> 6];
        for (int i = 0; i < known.length; i++) {
            known[i] = ~nullMap[i];
        }
        return known;
    }

    private void evalLong(long[] values, int n, long[] match) {
        switch (op) {
            case GT -> {
                for (int i = 0; i < n; i++) {
                    if (values[i] > longLow) {
                        match[i >>> 6] |= 1L << (i & 63);
                    }
                }
            }
            case LT -> {
                for (int i = 0; i < n; i++) {
                    if (values[i] < longLow) {
                        match[i >>> 6] |= 1L << (i & 63);
                    }
                }
            }
            case EQ -> {
                for (int i = 0; i < n; i++) {
                    if (values[i] == longLow) {
                        match[i >>> 6] |= 1L << (i & 63);
                    }
                }
            }
            case BETWEEN -> {
                for (int i = 0; i < n; i++) {
                    long v = values[i];
                    if (v >= longLow && v <= longHigh) {
                        match[i >>> 6] |= 1L << (i & 63);
                    }
                }
            }
        }
    }

    private void evalDouble(double[] values, int n, long[] match) {
        switch (op) {
            case GT -> {
                for (int i = 0; i < n; i++) {
                    if (values[i] > doubleLow) {
                        match[i >>> 6] |= 1L << (i & 63);
                    }
                }
            }
            case LT -> {
                for (int i = 0; i < n; i++) {
                    if (values[i] < doubleLow) {
                        match[i >>> 6] |= 1L << (i & 63);
                    }
                }
            }
            case EQ -> {
                for (int i = 0; i < n; i++) {
                    if (values[i] == doubleLow) {
                        match[i >>> 6] |= 1L << (i & 63);
                    }
                }
            }
            case BETWEEN -> {
                for (int i = 0; i < n; i++) {
                    double v = values[i];
                    if (v >= doubleLow && v <= doubleHigh) {
                        match[i >>> 6] |= 1L << (i & 63);
                    }
                }
            }
        }
    }

    private void evalString(String[] values, int n, long[] match) {
        switch (op) {
            case GT -> {
                for (int i = 0; i < n; i++) {
                    String v = values[i];
                    if (v != null && v.compareTo(stringLow) > 0) {
                        match[i >>> 6] |= 1L << (i & 63);
                    }
                }
            }
            case LT -> {
                for (int i = 0; i < n; i++) {
                    String v = values[i];
                    if (v != null && v.compareTo(stringLow) < 0) {
                        match[i >>> 6] |= 1L << (i & 63);
                    }
                }
            }
            case EQ -> {
                for (int i = 0; i < n; i++) {
                    String v = values[i];
                    if (v != null && v.equals(stringLow)) {
                        match[i >>> 6] |= 1L << (i & 63);
                    }
                }
            }
            case BETWEEN -> {
                for (int i = 0; i < n; i++) {
                    String v = values[i];
                    if (v != null && v.compareTo(stringLow) >= 0 && v.compareTo(stringHigh) <= 0) {
                        match[i >>> 6] |= 1L << (i & 63);
                    }
                }
            }
        }
    }

    @Override
    public String toString() {
        String bounds = switch (operandType) {
            case LONG -> op == Comparison.BETWEEN ? "[" + longLow + ", " + longHigh + "]" : String.valueOf(longLow);
            case DOUBLE -> op == Comparison.BETWEEN ? "[" + doubleLow + ", " + doubleHigh + "]" : String.valueOf(doubleLow);
            case STRING -> op == Comparison.BETWEEN ? "[" + stringLow + ", " + stringHigh + "]" : String.valueOf(stringLow);
        };
        return column + " " + op + " " + bounds;
    }
}
