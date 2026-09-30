package com.example.gsb.vectorized;

import java.util.Arrays;

/** Fixed-width 64-bit integer column backed by a primitive {@code long[]} plus a null bitmap. */
public final class LongColumnVector implements ColumnVector {

    private final long[] values;
    private final boolean[] nulls;

    public LongColumnVector(long[] values, boolean[] nulls) {
        if (values == null || nulls == null || values.length != nulls.length) {
            throw new IllegalArgumentException("values and nulls must be non-null arrays of equal length");
        }
        this.values = values;
        this.nulls = nulls;
    }

    public static LongColumnVector of(long... values) {
        return new LongColumnVector(values.clone(), new boolean[values.length]);
    }

    @Override
    public int size() {
        return values.length;
    }

    @Override
    public boolean isNull(int row) {
        return nulls[row];
    }

    public long value(int row) {
        return values[row];
    }

    /** Zero-copy view of the backing values; nulls must be consulted separately. */
    public long[] values() {
        return values;
    }

    /** Zero-copy view of the backing null bitmap. */
    public boolean[] nulls() {
        return nulls;
    }

    @Override
    public Long valueAt(int row) {
        return nulls[row] ? null : values[row];
    }

    @Override
    public LongColumnVector copyRows(int[] rowIndexes) {
        long[] outValues = new long[rowIndexes.length];
        boolean[] outNulls = new boolean[rowIndexes.length];
        for (int i = 0; i < rowIndexes.length; i++) {
            int src = rowIndexes[i];
            outValues[i] = values[src];
            outNulls[i] = nulls[src];
        }
        return new LongColumnVector(outValues, outNulls);
    }

    @Override
    public LongColumnVector slice(int offset, int length) {
        long[] outValues = Arrays.copyOfRange(values, offset, offset + length);
        boolean[] outNulls = Arrays.copyOfRange(nulls, offset, offset + length);
        return new LongColumnVector(outValues, outNulls);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LongColumnVector that)) return false;
        return Arrays.equals(values, that.values) && Arrays.equals(nulls, that.nulls);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(values) + Arrays.hashCode(nulls);
    }

    @Override
    public String toString() {
        return "LongColumnVector{size=" + values.length + "}";
    }
}
