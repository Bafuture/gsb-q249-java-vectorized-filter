package com.example.gsb.vectorized;

import java.util.ArrayList;
import java.util.List;

/**
 * The result of materializing a filter: rows for which the predicate was
 * definitely {@link TriState#TRUE}, copied column by column and kept in the
 * exact order they had in the source table. Rows that were {@code FALSE} or
 * {@code UNKNOWN} (null involved) are excluded, following SQL WHERE semantics.
 */
public final class MaterializedTable {

    private final List<String> columnNames;
    private final List<ColumnVector> columns;
    private final int[] sourceRows;
    private final int rowCount;

    private MaterializedTable(List<String> columnNames, List<ColumnVector> columns,
                              int[] sourceRows, int rowCount) {
        this.columnNames = columnNames;
        this.columns = columns;
        this.sourceRows = sourceRows;
        this.rowCount = rowCount;
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

    /** Source-table row index of materialized row {@code i}; strictly increasing. */
    public int sourceRow(int i) {
        return sourceRows[i];
    }

    public Object valueAt(int row, int column) {
        return columns.get(column).getValue(row);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MaterializedTable that)) {
            return false;
        }
        if (rowCount != that.rowCount || !columnNames.equals(that.columnNames)) {
            return false;
        }
        for (int r = 0; r < rowCount; r++) {
            for (int c = 0; c < columns.size(); c++) {
                Object a = columns.get(c).getValue(r);
                Object b = that.columns.get(c).getValue(r);
                if (a == null ? b != null : !a.equals(b)) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int h = columnNames.hashCode();
        for (int c = 0; c < columns.size(); c++) {
            for (int r = 0; r < rowCount; r++) {
                Object v = valueAt(r, c);
                h = 31 * h + (v == null ? 0 : v.hashCode());
            }
        }
        return h;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("MaterializedTable{rows=").append(rowCount).append("}\n");
        sb.append(columnNames).append('\n');
        for (int r = 0; r < rowCount; r++) {
            List<Object> row = new ArrayList<>(columns.size());
            for (ColumnVector col : columns) {
                row.add(col.getValue(r));
            }
            sb.append(row).append(" <- row#").append(sourceRows[r]).append('\n');
        }
        return sb.toString();
    }

    /**
     * Builds a materialized table by gathering the {@code true} positions from
     * a full-table selection vector. True positions are iterated in ascending
     * order, which guarantees the output preserves the source ordering.
     */
    static MaterializedTable gather(Table source, SelectionVector selected) {
        if (selected.length() != source.rowCount()) {
            throw new IllegalArgumentException(
                    "Selection vector length " + selected.length()
                            + " does not match table rows " + source.rowCount());
        }
        int count = selected.trueCount();
        int[] sourceRows = new int[count];
        int[] targetRows = new int[count];
        int written = 0;
        var it = selected.truePositions();
        while (it.hasNext()) {
            int src = it.nextInt();
            sourceRows[written] = src;
            targetRows[written] = written;
            written++;
        }

        List<ColumnVector> out = new ArrayList<>(source.columnCount());
        for (int c = 0; c < source.columnCount(); c++) {
            out.add(gatherColumn(source.column(c), sourceRows, count));
        }
        return new MaterializedTable(source.columnNames(), out, sourceRows, count);
    }

    private static ColumnVector gatherColumn(ColumnVector col, int[] rows, int count) {
        boolean[] isNull = new boolean[count];
        for (int i = 0; i < count; i++) {
            isNull[i] = col.isNullAt(rows[i]);
        }
        return switch (col.type()) {
            case LONG -> {
                long[] values = new long[count];
                for (int i = 0; i < count; i++) {
                    values[i] = col.getLong(rows[i]);
                }
                yield ColumnVector.ofLong(values, isNull);
            }
            case DOUBLE -> {
                double[] values = new double[count];
                for (int i = 0; i < count; i++) {
                    values[i] = col.getDouble(rows[i]);
                }
                yield ColumnVector.ofDouble(values, isNull);
            }
            case STRING -> {
                String[] values = new String[count];
                for (int i = 0; i < count; i++) {
                    values[i] = col.getString(rows[i]);
                }
                yield ColumnVector.ofString(values, isNull);
            }
        };
    }
}
