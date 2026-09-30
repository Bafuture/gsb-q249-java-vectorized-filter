package com.example.gsb.vectorized;

/**
 * Row-oriented, zero-copy view over a table, used by the reference row-wise
 * evaluator. {@link #moveTo(int)} positions the cursor; typed getters then
 * return the value of the referenced cell or {@code null} for SQL NULL.
 */
public final class RowView {

    private final Table table;
    private int row = -1;

    public RowView(Table table) {
        this.table = table;
    }

    public void moveTo(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= table.rowCount()) {
            throw new IndexOutOfBoundsException(rowIndex);
        }
        this.row = rowIndex;
    }

    public int tableColumn(String name) {
        return table.columnIndex(name);
    }

    public Object value(int columnIndex) {
        return table.column(columnIndex).getValue(row);
    }

    public Long getLong(int columnIndex) {
        ColumnVector col = table.column(columnIndex);
        return col.isNullAt(row) ? null : col.getLong(row);
    }

    public Double getDouble(int columnIndex) {
        ColumnVector col = table.column(columnIndex);
        return col.isNullAt(row) ? null : col.getDouble(row);
    }

    public String getString(int columnIndex) {
        ColumnVector col = table.column(columnIndex);
        return col.isNullAt(row) ? null : col.getString(row);
    }
}
