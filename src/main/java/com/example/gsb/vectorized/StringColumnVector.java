package com.example.gsb.vectorized;

import java.util.Arrays;

/** Variable-width string column; a {@code null} array element denotes SQL NULL. */
public final class StringColumnVector implements ColumnVector {

    private final String[] values;

    public StringColumnVector(String[] values) {
        if (values == null) {
            throw new IllegalArgumentException("values must be non-null");
        }
        this.values = values;
    }

    public static StringColumnVector of(String... values) {
        return new StringColumnVector(values.clone());
    }

    @Override
    public int size() {
        return values.length;
    }

    @Override
    public boolean isNull(int row) {
        return values[row] == null;
    }

    public String value(int row) {
        return values[row];
    }

    /** Zero-copy view of the backing values; {@code null} elements mean SQL NULL. */
    public String[] values() {
        return values;
    }

    @Override
    public String valueAt(int row) {
        return values[row];
    }

    @Override
    public StringColumnVector copyRows(int[] rowIndexes) {
        String[] out = new String[rowIndexes.length];
        for (int i = 0; i < rowIndexes.length; i++) {
            out[i] = values[rowIndexes[i]];
        }
        return new StringColumnVector(out);
    }

    @Override
    public StringColumnVector slice(int offset, int length) {
        return new StringColumnVector(Arrays.copyOfRange(values, offset, offset + length));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StringColumnVector that)) return false;
        return Arrays.equals(values, that.values);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(values);
    }

    @Override
    public String toString() {
        return "StringColumnVector{size=" + values.length + "}";
    }
}
