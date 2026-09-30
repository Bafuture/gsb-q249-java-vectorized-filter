package com.example.gsb.vectorized;

import java.util.List;

/**
 * An immutable horizontal slice of a {@link Table}: {@code size} rows stored
 * column-by-column. Batches are the unit of vectorized execution and carry the
 * table's column names so predicates can resolve columns without help.
 */
public final class ColumnBatch {

    private final int size;
    private final List<String> columnNames;
    private final List<ColumnVector> columns;

    ColumnBatch(int size, List<String> columnNames, List<ColumnVector> columns) {
        this.size = size;
        this.columnNames = List.copyOf(columnNames);
        this.columns = List.copyOf(columns);
    }

    public int size() {
        return size;
    }

    public int columnCount() {
        return columns.size();
    }

    public List<String> columnNames() {
        return columnNames;
    }

    public int columnIndex(String name) {
        int idx = columnNames.indexOf(name);
        if (idx < 0) {
            throw new IllegalArgumentException("Unknown column: " + name);
        }
        return idx;
    }

    public ColumnVector column(int index) {
        return columns.get(index);
    }

    public List<ColumnVector> columns() {
        return columns;
    }
}
