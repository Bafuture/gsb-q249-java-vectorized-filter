package com.example.gsb.vectorized;

import java.util.ArrayList;
import java.util.List;

/**
 * An immutable columnar table: all columns share the same row count and are
 * stored as {@link ColumnVector}s. A table can be split into
 * {@link ColumnBatch}es of a fixed batch size; the last batch may be smaller.
 * Batch boundaries never reorder, drop or duplicate rows: batches partition
 * the row index space {@code [0, rowCount)} contiguously and in order.
 */
public final class Table {

    private final List<String> columnNames;
    private final List<ColumnVector> columns;
    private final int rowCount;

    private Table(List<String> columnNames, List<ColumnVector> columns, int rowCount) {
        this.columnNames = List.copyOf(columnNames);
        this.columns = List.copyOf(columns);
        this.rowCount = rowCount;
    }

    public static Builder builder() {
        return new Builder();
    }

    public int rowCount() {
        return rowCount;
    }

    public int columnCount() {
        return columns.size();
    }

    public List<String> columnNames() {
        return columnNames;
    }

    public ColumnVector column(int index) {
        return columns.get(index);
    }

    public int columnIndex(String name) {
        int idx = columnNames.indexOf(name);
        if (idx < 0) {
            throw new IllegalArgumentException("Unknown column: " + name);
        }
        return idx;
    }

    /**
     * Splits the table into batches of at most {@code batchSize} rows, in row
     * order. {@code concat} of the batches' row indexes is exactly
     * {@code [0, rowCount)}.
     */
    public List<ColumnBatch> batches(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive: " + batchSize);
        }
        List<ColumnBatch> result = new ArrayList<>((rowCount + batchSize - 1) / batchSize);
        for (int start = 0; start < rowCount; start += batchSize) {
            int size = Math.min(batchSize, rowCount - start);
            List<ColumnVector> cols = new ArrayList<>(columns.size());
            for (ColumnVector col : columns) {
                cols.add(slice(col, start, size));
            }
            result.add(new ColumnBatch(size, columnNames, cols));
        }
        return result;
    }

    private static ColumnVector slice(ColumnVector col, int offset, int size) {
        long[] nulls = new long[(size + 63) >>> 6];
        copyBits(col.rawNullMap(), offset, size, nulls);
        switch (col.type()) {
            case LONG -> {
                long[] v = new long[size];
                System.arraycopy(col.rawLongs(), offset, v, 0, size);
                return ColumnVector.wrap(ColumnType.LONG, size, v, null, null, nulls);
            }
            case DOUBLE -> {
                double[] v = new double[size];
                System.arraycopy(col.rawDoubles(), offset, v, 0, size);
                return ColumnVector.wrap(ColumnType.DOUBLE, size, null, v, null, nulls);
            }
            case STRING -> {
                String[] v = new String[size];
                System.arraycopy(col.rawStrings(), offset, v, 0, size);
                return ColumnVector.wrap(ColumnType.STRING, size, null, null, v, nulls);
            }
            default -> throw new IllegalStateException("Unsupported type: " + col.type());
        }
    }

    private static void copyBits(long[] src, int srcOffset, int len, long[] dst) {
        if (len == 0) {
            return;
        }
        int wordShift = srcOffset >>> 6;
        int bitShift = srcOffset & 63;
        if (bitShift == 0) {
            System.arraycopy(src, wordShift, dst, 0, (len + 63) >>> 6);
            return;
        }
        int dstWords = (len + 63) >>> 6;
        for (int i = 0; i < dstWords; i++) {
            long low = src[wordShift + i] >>> bitShift;
            long high = (wordShift + i + 1 < src.length) ? src[wordShift + i + 1] << (64 - bitShift) : 0L;
            dst[i] = low | high;
        }
        int rem = len & 63;
        if (rem != 0) {
            dst[dstWords - 1] &= (1L << rem) - 1L;
        }
    }

    /** Builder for {@link Table}. All added columns must have equal length. */
    public static final class Builder {
        private final List<String> names = new ArrayList<>();
        private final List<ColumnVector> columns = new ArrayList<>();

        public Builder addColumn(String name, ColumnVector column) {
            if (names.contains(name)) {
                throw new IllegalArgumentException("Duplicate column name: " + name);
            }
            names.add(name);
            columns.add(column);
            return this;
        }

        public Table build() {
            if (columns.isEmpty()) {
                throw new IllegalStateException("Table needs at least one column");
            }
            int rows = columns.get(0).size();
            for (ColumnVector c : columns) {
                if (c.size() != rows) {
                    throw new IllegalStateException(
                            "Column length mismatch: expected " + rows + " but got " + c.size());
                }
            }
            return new Table(names, columns, rows);
        }
    }
}
