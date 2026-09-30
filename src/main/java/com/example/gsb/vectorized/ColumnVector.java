package com.example.gsb.vectorized;

/**
 * A columnar vector holding one column's values for a single batch.
 *
 * <p>Fixed-width numeric columns store primitive {@code long[]} /
 * {@code double[]} arrays; string columns store a {@code String[]} array.
 * Nullability is tracked out-of-band by a packed {@code nullMap}: bit
 * {@code i} is set iff row {@code i} is SQL {@code NULL}. Keeping nulls out of
 * the value arrays lets predicate kernels scan the value arrays with plain
 * numeric comparisons and overlay null semantics afterwards.
 */
public final class ColumnVector {

    private final ColumnType type;
    private final int size;
    private final long[] longs;
    private final double[] doubles;
    private final String[] strings;
    private final long[] nullMap;

    private ColumnVector(ColumnType type, int size, long[] longs, double[] doubles,
                         String[] strings, long[] nullMap) {
        this.type = type;
        this.size = size;
        this.longs = longs;
        this.doubles = doubles;
        this.strings = strings;
        this.nullMap = nullMap;
    }

    public static ColumnVector ofLong(long[] values, boolean[] isNull) {
        long[] nulls = packNulls(values.length, isNull);
        return new ColumnVector(ColumnType.LONG, values.length, values.clone(), null, null, nulls);
    }

    public static ColumnVector ofDouble(double[] values, boolean[] isNull) {
        long[] nulls = packNulls(values.length, isNull);
        return new ColumnVector(ColumnType.DOUBLE, values.length, null, values.clone(), null, nulls);
    }

    public static ColumnVector ofString(String[] values, boolean[] isNull) {
        long[] nulls = packNulls(values.length, isNull);
        return new ColumnVector(ColumnType.STRING, values.length, null, null, values.clone(), nulls);
    }

    static ColumnVector wrap(ColumnType type, int size, long[] longs, double[] doubles,
                             String[] strings, long[] nullMap) {
        return new ColumnVector(type, size, longs, doubles, strings, nullMap);
    }

    private static long[] packNulls(int length, boolean[] isNull) {
        long[] nulls = new long[(length + 63) >>> 6];
        if (isNull != null) {
            for (int i = 0; i < length; i++) {
                if (isNull[i]) {
                    nulls[i >>> 6] |= 1L << (i & 63);
                }
            }
        }
        return nulls;
    }

    public ColumnType type() {
        return type;
    }

    public int size() {
        return size;
    }

    public boolean isNullAt(int index) {
        return (nullMap[index >>> 6] & (1L << (index & 63))) != 0;
    }

    public long getLong(int index) {
        checkType(ColumnType.LONG);
        return longs[index];
    }

    public double getDouble(int index) {
        checkType(ColumnType.DOUBLE);
        return doubles[index];
    }

    public String getString(int index) {
        checkType(ColumnType.STRING);
        return strings[index];
    }

    /**
     * Returns the typed value at {@code index}, or {@code null} when the row is null.
     * Boxed accessor used by the row-wise reference path; predicate kernels use
     * the primitive accessors instead.
     */
    public Object getValue(int index) {
        if (isNullAt(index)) {
            return null;
        }
        return switch (type) {
            case LONG -> longs[index];
            case DOUBLE -> doubles[index];
            case STRING -> strings[index];
        };
    }

    long[] rawLongs() {
        return longs;
    }

    double[] rawDoubles() {
        return doubles;
    }

    String[] rawStrings() {
        return strings;
    }

    long[] rawNullMap() {
        return nullMap;
    }

    private void checkType(ColumnType expected) {
        if (type != expected) {
            throw new IllegalStateException("Column is " + type + ", not " + expected);
        }
    }
}
