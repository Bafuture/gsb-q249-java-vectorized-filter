package com.example.gsb.vectorized;

import java.util.Arrays;

/** Fixed-width 64-bit floating point column backed by a primitive {@code double[]} plus a null bitmap. */
public final class DoubleColumnVector implements ColumnVector {

    private final double[] values;
    private final boolean[] nulls;

    public DoubleColumnVector(double[] values, boolean[] nulls) {
        if (values == null || nulls == null || values.length != nulls.length) {
            throw new IllegalArgumentException("values and nulls must be non-null arrays of equal length");
        }
        this.values = values;
        this.nulls = nulls;
    }

    public static DoubleColumnVector of(double... values) {
        return new DoubleColumnVector(values.clone(), new boolean[values.length]);
    }

    @Override
    public int size() {
        return values.length;
    }

    @Override
    public boolean isNull(int row) {
        return nulls[row];
    }

    public double value(int row) {
        return values[row];
    }

    public double[] values() {
        return values;
    }

    public boolean[] nulls() {
        return nulls;
    }

    @Override
    public Double valueAt(int row) {
        return nulls[row] ? null : values[row];
    }

    @Override
    public DoubleColumnVector copyRows(int[] rowIndexes) {
        double[] outValues = new double[rowIndexes.length];
        boolean[] outNulls = new boolean[rowIndexes.length];
        for (int i = 0; i < rowIndexes.length; i++) {
            int src = rowIndexes[i];
            outValues[i] = values[src];
            outNulls[i] = nulls[src];
        }
        return new DoubleColumnVector(outValues, outNulls);
    }

    @Override
    public DoubleColumnVector slice(int offset, int length) {
        double[] outValues = Arrays.copyOfRange(values, offset, offset + length);
        boolean[] outNulls = Arrays.copyOfRange(nulls, offset, offset + length);
        return new DoubleColumnVector(outValues, outNulls);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DoubleColumnVector that)) return false;
        return Arrays.equals(values, that.values) && Arrays.equals(nulls, that.nulls);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(values) + Arrays.hashCode(nulls);
    }

    @Override
    public String toString() {
        return "DoubleColumnVector{size=" + values.length + "}";
    }
}
