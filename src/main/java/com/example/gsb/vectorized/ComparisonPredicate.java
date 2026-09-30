package com.example.gsb.vectorized;

/**
 * Comparison predicate: {@code >}, {@code <}, {@code =} or inclusive range {@code BETWEEN}.
 *
 * <p>{@link #evaluate(ColumnVector)} dispatches to a primitive-array inner loop per concrete
 * column type: every row is handled in one contiguous scan with no boxing or per-row method
 * dispatch. NULL input rows produce {@link SelectionVector#UNKNOWN}; all other rows produce
 * TRUE/FALSE.
 */
public final class ComparisonPredicate implements ColumnPredicate {

    public enum Op {
        GT, LT, EQ, BETWEEN
    }

    private final Op op;
    private final Comparable<?> lower;
    private final Comparable<?> upper;

    public ComparisonPredicate(Op op, Comparable<?> lower, Comparable<?> upper) {
        if (op == null || lower == null) {
            throw new IllegalArgumentException("op and lower must be non-null");
        }
        if (op == Op.BETWEEN && upper == null) {
            throw new IllegalArgumentException("BETWEEN requires an upper bound");
        }
        if (op == Op.BETWEEN && compare(lower, upper) > 0) {
            throw new IllegalArgumentException("BETWEEN lower bound must not exceed upper bound");
        }
        this.op = op;
        this.lower = lower;
        this.upper = upper;
    }

    public Op op() {
        return op;
    }

    public Comparable<?> lower() {
        return lower;
    }

    public Comparable<?> upper() {
        return upper;
    }

    /** Single-row evaluation used by the row-wise reference implementation. */
    @SuppressWarnings("rawtypes")
    public boolean test(Comparable value) {
        int cmpLower = compare(value, lower);
        return switch (op) {
            case GT -> cmpLower > 0;
            case LT -> cmpLower < 0;
            case EQ -> cmpLower == 0;
            case BETWEEN -> cmpLower >= 0 && compare(value, upper) <= 0;
        };
    }

    @Override
    public SelectionVector evaluate(ColumnVector column) {
        if (column instanceof LongColumnVector longColumn) {
            return evaluateLong(longColumn);
        }
        if (column instanceof DoubleColumnVector doubleColumn) {
            return evaluateDouble(doubleColumn);
        }
        if (column instanceof StringColumnVector stringColumn) {
            return evaluateString(stringColumn);
        }
        int n = column.size();
        byte[] states = new byte[n];
        for (int i = 0; i < n; i++) {
            if (column.isNull(i)) {
                states[i] = SelectionVector.UNKNOWN;
            } else {
                states[i] = test(column.valueAt(i))
                        ? SelectionVector.TRUE
                        : SelectionVector.FALSE;
            }
        }
        return new SelectionVector(states);
    }

    private SelectionVector evaluateLong(LongColumnVector column) {
        long bound = ((Number) lower).longValue();
        long upperBound = op == Op.BETWEEN ? ((Number) upper).longValue() : 0L;
        long[] values = column.values();
        boolean[] nulls = column.nulls();
        int n = values.length;
        byte[] states = new byte[n];
        for (int i = 0; i < n; i++) {
            if (nulls[i]) {
                states[i] = SelectionVector.UNKNOWN;
                continue;
            }
            long value = values[i];
            boolean matches = switch (op) {
                case GT -> value > bound;
                case LT -> value < bound;
                case EQ -> value == bound;
                case BETWEEN -> value >= bound && value <= upperBound;
            };
            states[i] = matches ? SelectionVector.TRUE : SelectionVector.FALSE;
        }
        return new SelectionVector(states);
    }

    private SelectionVector evaluateDouble(DoubleColumnVector column) {
        double bound = ((Number) lower).doubleValue();
        double upperBound = op == Op.BETWEEN ? ((Number) upper).doubleValue() : 0.0;
        double[] values = column.values();
        boolean[] nulls = column.nulls();
        int n = values.length;
        byte[] states = new byte[n];
        for (int i = 0; i < n; i++) {
            if (nulls[i]) {
                states[i] = SelectionVector.UNKNOWN;
                continue;
            }
            double value = values[i];
            boolean matches = switch (op) {
                case GT -> value > bound;
                case LT -> value < bound;
                case EQ -> value == bound;
                case BETWEEN -> value >= bound && value <= upperBound;
            };
            states[i] = matches ? SelectionVector.TRUE : SelectionVector.FALSE;
        }
        return new SelectionVector(states);
    }

    private SelectionVector evaluateString(StringColumnVector column) {
        String[] values = column.values();
        String bound = (String) lower;
        String upperBound = op == Op.BETWEEN ? (String) upper : null;
        int n = values.length;
        byte[] states = new byte[n];
        for (int i = 0; i < n; i++) {
            String value = values[i];
            if (value == null) {
                states[i] = SelectionVector.UNKNOWN;
                continue;
            }
            int cmpLower = value.compareTo(bound);
            boolean matches = switch (op) {
                case GT -> cmpLower > 0;
                case LT -> cmpLower < 0;
                case EQ -> cmpLower == 0;
                case BETWEEN -> cmpLower >= 0 && value.compareTo(upperBound) <= 0;
            };
            states[i] = matches ? SelectionVector.TRUE : SelectionVector.FALSE;
        }
        return new SelectionVector(states);
    }

    /**
     * Null-safe cross-numeric comparison: two integral numbers compare by their long value,
     * otherwise by double value; same-type values compare via {@link Comparable#compareTo}.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    static int compare(Comparable a, Comparable b) {
        if (a instanceof Number na && b instanceof Number nb) {
            if (isIntegral(na) && isIntegral(nb)) {
                return Long.compare(na.longValue(), nb.longValue());
            }
            return Double.compare(na.doubleValue(), nb.doubleValue());
        }
        return a.compareTo(b);
    }

    private static boolean isIntegral(Number number) {
        return number instanceof Byte || number instanceof Short
                || number instanceof Integer || number instanceof Long;
    }

    @Override
    public String toString() {
        return op == Op.BETWEEN
                ? "BETWEEN(" + lower + ", " + upper + ")"
                : op + "(" + lower + ")";
    }
}
