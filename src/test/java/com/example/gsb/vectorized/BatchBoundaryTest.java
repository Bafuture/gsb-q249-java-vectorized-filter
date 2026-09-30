package com.example.gsb.vectorized;

import static com.example.gsb.vectorized.Predicates.between;
import static com.example.gsb.vectorized.Predicates.greaterThan;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BatchBoundaryTest {

    /** Deliberately odd row count so no "nice" batch size divides it evenly. */
    private static final int ROW_COUNT = 100_003;

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 7, 333, 1024, 8192, ROW_COUNT, ROW_COUNT + 1})
    void betweenAcrossBatchBoundariesMatchesSingleBatch(int batchSize) {
        RowBatch whole = buildTable(ROW_COUNT);

        List<RowBatch> batches = whole.split(batchSize);
        List<RowBatch> filtered =
                VectorizedFilter.filterBatches(batches, "value", between(40_000L, 60_000L));

        // Ground truth: vectorized and row-wise on the unsplit table.
        RowBatch expectedVectorized = VectorizedFilter.filter(whole, "value", between(40_000L, 60_000L));
        RowBatch expectedRowWise = RowWiseFilter.filter(whole, "value", between(40_000L, 60_000L));

        long[] actualIds = concatLongColumn(filtered, "id", expectedVectorized.rowCount());
        long[] expectedIds = ((LongColumnVector) expectedVectorized.column("id")).values();

        assertThat(actualIds).containsExactly(expectedIds);
        assertThat(expectedVectorized).isEqualTo(expectedRowWise);

        // No duplicates: every gathered id is unique (ids equal row indexes).
        Set<Long> unique = new HashSet<>(actualIds.length);
        for (long actualId : actualIds) {
            assertThat(unique.add(actualId)).as("duplicate row id %s", actualId).isTrue();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 3, 1024})
    void splitCoversEveryRowExactlyOnce(int batchSize) {
        RowBatch whole = buildTable(1009);
        List<RowBatch> slices = whole.split(batchSize);

        int totalRows = slices.stream().mapToInt(RowBatch::rowCount).sum();
        assertThat(totalRows).isEqualTo(1009);
        assertThat(slices).allSatisfy(slice -> assertThat(slice.rowCount()).isLessThanOrEqualTo(batchSize));

        // Each slice must equal the corresponding contiguous slice of the original table.
        int offset = 0;
        for (RowBatch slice : slices) {
            int length = slice.rowCount();
            assertThat(slice).isEqualTo(new RowBatch(sliceMap(whole, offset, length)));
            offset += length;
        }
        assertThat(offset).isEqualTo(1009);
    }

    @Test
    void rowsOnExactBatchBoundaryAreNotDroppedOrRepeated() {
        RowBatch whole = buildTable(10);
        List<RowBatch> filtered =
                VectorizedFilter.filterBatches(whole.split(5), "value", greaterThan(-1L));
        long[] ids = concatLongColumn(filtered, "id", 10);
        assertThat(ids).containsExactly(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
    }

    private static RowBatch buildTable(int n) {
        long[] ids = new long[n];
        long[] values = new long[n];
        boolean[] nulls = new boolean[n];
        java.util.Random random = new java.util.Random(123L);
        for (int i = 0; i < n; i++) {
            ids[i] = i;
            if (i % 997 == 13) {
                nulls[i] = true;
            } else {
                values[i] = random.nextInt(n);
            }
        }
        Map<String, ColumnVector> map = new LinkedHashMap<>();
        map.put("id", new LongColumnVector(ids, new boolean[n]));
        map.put("value", new LongColumnVector(values, nulls));
        return new RowBatch(map);
    }

    private static Map<String, ColumnVector> sliceMap(RowBatch source, int offset, int length) {
        Map<String, ColumnVector> map = new LinkedHashMap<>();
        for (String name : source.columnNames()) {
            map.put(name, source.column(name).slice(offset, length));
        }
        return map;
    }

    private static long[] concatLongColumn(List<RowBatch> batches, String column, int expectedLength) {
        long[] out = new long[expectedLength];
        int write = 0;
        for (RowBatch batch : batches) {
            long[] values = ((LongColumnVector) batch.column(column)).values();
            System.arraycopy(values, 0, out, write, values.length);
            write += values.length;
        }
        assertThat(write).as("concatenated row count").isEqualTo(expectedLength);
        return out;
    }
}
