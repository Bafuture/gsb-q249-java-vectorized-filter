package com.example.gsb.vectorized;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Requirement: predicates whose selected range crosses batch boundaries must
 * be sliced correctly for every batch size — no rows lost, none duplicated,
 * boundary rows included exactly once, regardless of where the cut falls.
 */
class BatchBoundaryTest {

    private static Table sortedLongs(int rows) {
        long[] values = new long[rows];
        for (int i = 0; i < rows; i++) {
            values[i] = i;
        }
        return Table.builder().addColumn("v", ColumnVector.ofLong(values, null)).build();
    }

    private static List<Integer> selectedRows(SelectionVector sv) {
        List<Integer> rows = new ArrayList<>();
        sv.truePositions().forEachRemaining((int i) -> rows.add(i));
        return rows;
    }

    @Test
    void betweenBoundaryRowsAreIncludedOnceForEveryBatchSize() {
        int rows = 200;
        long low = 30;
        long high = 149;
        Table t = sortedLongs(rows);
        FilterPredicate p = Predicates.betweenLong("v", low, high);

        List<Integer> expected = new ArrayList<>();
        for (long i = low; i <= high; i++) {
            expected.add((int) i);
        }
        // compare against the row-wise reference too
        SelectionVector reference = new RowWiseFilter().selectionVector(t, p);

        int[] batchSizes = {1, 2, 3, 5, 7, 16, 31, 32, 63, 64, 65, 66, 127, 128, 129, 199, 200, 201};
        for (int batchSize : batchSizes) {
            VectorizedFilter filter = new VectorizedFilter(batchSize);
            SelectionVector sv = filter.evaluate(t, p);
            assertThat(sv.length())
                    .as("total length for batchSize %d", batchSize)
                    .isEqualTo(rows);
            assertThat(sv.trueCount())
                    .as("no dup/miss for batchSize %d", batchSize)
                    .isEqualTo((int) (high - low + 1));
            assertThat(selectedRows(sv))
                    .as("selected rows for batchSize %d", batchSize)
                    .isEqualTo(expected);
            assertThat(sv)
                    .as("matches row-wise reference for batchSize %d", batchSize)
                    .isEqualTo(reference);
        }
    }

    @Test
    void batchCutExactlyOnRangeEdges() {
        // range [10, 19], cuts chosen so 9|10 and 19|20 land on various boundaries
        Table t = sortedLongs(50);
        FilterPredicate p = Predicates.betweenLong("v", 10, 19);
        for (int batchSize : new int[]{1, 2, 5, 10, 11, 20, 25}) {
            List<Integer> rows = selectedRows(new VectorizedFilter(batchSize).evaluate(t, p));
            assertThat(rows)
                    .as("batchSize %d", batchSize)
                    .containsExactly(10, 11, 12, 13, 14, 15, 16, 17, 18, 19);
        }
    }

    @Test
    void nonDividingBatchSizeNeverLosesTailRows() {
        // 77 rows with prime-ish batch sizes: last partial batch must be evaluated
        Table t = sortedLongs(77);
        FilterPredicate p = Predicates.gtLong("v", 70);
        for (int batchSize : new int[]{8, 9, 10, 13, 37}) {
            SelectionVector sv = new VectorizedFilter(batchSize).evaluate(t, p);
            assertThat(sv.trueCount()).as("batchSize %d", batchSize).isEqualTo(6);
            assertThat(selectedRows(sv)).as("batchSize %d", batchSize)
                    .containsExactly(71, 72, 73, 74, 75, 76);
        }
    }

    @Test
    void batchPartitionsAreContiguousAndInOrder() {
        Table t = sortedLongs(100);
        List<ColumnBatch> batches = t.batches(32);
        assertThat(batches).hasSize(4);
        assertThat(batches.get(0).size()).isEqualTo(32);
        assertThat(batches.get(3).size()).isEqualTo(4);
        // concatenating batch values reproduces the original column
        long first = batches.get(0).column(0).getLong(0);
        long last = batches.get(3).column(0).getLong(3);
        assertThat(first).isZero();
        assertThat(last).isEqualTo(99);
    }

    @Test
    void crossBatchRangesWithNullsAtBoundaries() {
        int rows = 128;
        long[] values = new long[rows];
        boolean[] isNull = new boolean[rows];
        for (int i = 0; i < rows; i++) {
            values[i] = i;
        }
        // nulls right at/around the range edges, which fall on word/batch edges
        isNull[63] = true;
        isNull[64] = true;
        isNull[65] = true;
        Table t = Table.builder().addColumn("v", ColumnVector.ofLong(values, isNull)).build();

        FilterPredicate p = Predicates.betweenLong("v", 60, 70);
        SelectionVector reference = new RowWiseFilter().selectionVector(t, p);
        for (int batchSize : new int[]{63, 64, 65, 32, 128}) {
            SelectionVector sv = new VectorizedFilter(batchSize).evaluate(t, p);
            assertThat(sv).as("batchSize %d", batchSize).isEqualTo(reference);
            assertThat(sv.get(63)).as("null row 63").isEqualTo(TriState.UNKNOWN);
            assertThat(sv.get(64)).as("null row 64").isEqualTo(TriState.UNKNOWN);
            assertThat(sv.get(65)).as("null row 65").isEqualTo(TriState.UNKNOWN);
            assertThat(sv.trueCount()).as("batchSize %d", batchSize).isEqualTo(8);
        }
    }
}
