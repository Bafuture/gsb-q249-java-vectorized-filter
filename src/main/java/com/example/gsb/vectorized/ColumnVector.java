package com.example.gsb.vectorized;

/**
 * A single column of a columnar batch.
 *
 * <p>Implementations are fixed-width primitive arrays ({@link LongColumnVector},
 * {@link DoubleColumnVector}) or an object array ({@link StringColumnVector}) together
 * with an explicit null bitmap. The raw-array accessors are intentionally exposed so
 * that vectorized predicate evaluation can loop over contiguous memory without boxing.
 */
public interface ColumnVector {

    int size();

    boolean isNull(int row);

    /** Boxed view used by the row-wise reference implementation; avoid in hot loops. */
    Comparable<?> valueAt(int row);

    /** Copies the given source row indexes in order into a new vector (materialization). */
    ColumnVector copyRows(int[] rowIndexes);

    /** Copies a contiguous half-open range {@code [offset, offset + length)} (batch split). */
    ColumnVector slice(int offset, int length);
}
