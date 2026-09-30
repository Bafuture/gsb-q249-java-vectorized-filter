package com.example.gsb.vectorized;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * An ordered collection of same-length columns forming one batch of rows.
 * Column order is preserved (backed by {@link LinkedHashMap}).
 */
public final class RowBatch {

    private final LinkedHashMap<String, ColumnVector> columns;
    private final int rowCount;

    public RowBatch(Map<String, ColumnVector> columns) {
        if (columns == null || columns.isEmpty()) {
            throw new IllegalArgumentException("at least one column is required");
        }
        LinkedHashMap<String, ColumnVector> copy = new LinkedHashMap<>(columns);
        int expectedSize = -1;
        for (Map.Entry<String, ColumnVector> entry : copy.entrySet()) {
            Objects.requireNonNull(entry.getKey(), "column name");
            Objects.requireNonNull(entry.getValue(), "column value for " + entry.getKey());
            int size = entry.getValue().size();
            if (expectedSize < 0) {
                expectedSize = size;
            } else if (size != expectedSize) {
                throw new IllegalArgumentException(
                        "column '" + entry.getKey() + "' has " + size + " rows, expected " + expectedSize);
            }
        }
        this.columns = copy;
        this.rowCount = expectedSize;
    }

    public int rowCount() {
        return rowCount;
    }

    public ColumnVector column(String name) {
        ColumnVector column = columns.get(name);
        if (column == null) {
            throw new IllegalArgumentException("unknown column: " + name);
        }
        return column;
    }

    public Set<String> columnNames() {
        return Collections.unmodifiableSet(columns.keySet());
    }

    /**
     * Materializes selected rows: gathers each column at the TRUE positions of
     * {@code selection}, preserving ascending source-row order.
     */
    public RowBatch materialize(SelectionVector selection) {
        Objects.requireNonNull(selection, "selection");
        if (selection.size() != rowCount) {
            throw new IllegalArgumentException(
                    "selection length " + selection.size() + " does not match row count " + rowCount);
        }
        int[] rowIndexes = selection.toRowIndexes();
        LinkedHashMap<String, ColumnVector> out = new LinkedHashMap<>();
        for (Map.Entry<String, ColumnVector> entry : columns.entrySet()) {
            out.put(entry.getKey(), entry.getValue().copyRows(rowIndexes));
        }
        return new RowBatch(out);
    }

    /**
     * Splits this batch into consecutive, disjoint, order-preserving batches of at most
     * {@code batchSize} rows. Used to feed batch-oriented pipelines; the slices together
     * contain every source row exactly once.
     */
    public List<RowBatch> split(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        List<RowBatch> batches = new ArrayList<>((rowCount + batchSize - 1) / Math.max(batchSize, 1) + 1);
        for (int offset = 0; offset < rowCount; offset += batchSize) {
            int length = Math.min(batchSize, rowCount - offset);
            LinkedHashMap<String, ColumnVector> sliced = new LinkedHashMap<>();
            for (Map.Entry<String, ColumnVector> entry : columns.entrySet()) {
                sliced.put(entry.getKey(), entry.getValue().slice(offset, length));
            }
            batches.add(new RowBatch(sliced));
        }
        return batches;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RowBatch rowBatch)) return false;
        return rowCount == rowBatch.rowCount && columns.equals(rowBatch.columns);
    }

    @Override
    public int hashCode() {
        return 31 * columns.hashCode() + rowCount;
    }

    @Override
    public String toString() {
        return "RowBatch{rows=" + rowCount + ", columns=" + columns.keySet() + "}";
    }
}
