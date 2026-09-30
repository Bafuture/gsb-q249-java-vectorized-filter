package com.example.gsb.vectorized;

import static com.example.gsb.vectorized.Predicates.between;
import static com.example.gsb.vectorized.Predicates.greaterThan;
import static com.example.gsb.vectorized.Predicates.lessThan;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Same-work timing comparison of the vectorized filter and the row-wise reference.
 * Timings are reported at two levels — predicate evaluation only, and end-to-end
 * filtering including materialization. Only result equality is asserted; timings are
 * printed (never thresholded) so the test stays deterministic across machines.
 */
class PerformanceTest {

    private static final int ROWS = 2_000_000;
    private static final int ITERATIONS = 5;

    @Test
    void compareSinglePredicateTimings() {
        RowBatch batch = buildBatch(ROWS);
        ComparisonPredicate predicate = between(300_000L, 1_700_000L);

        for (int i = 0; i < 3; i++) {
            predicate.evaluate(batch.column("value"));
            RowWiseFilter.select(batch, "value", predicate);
            VectorizedFilter.filter(batch, "value", predicate);
            RowWiseFilter.filter(batch, "value", predicate);
        }

        // Timed runs keep evaluate-only and end-to-end phases separate.
        long vectorEvalNanos = measure(() -> predicate.evaluate(batch.column("value")));
        long rowEvalNanos = measure(() -> RowWiseFilter.select(batch, "value", predicate));
        long vectorTotalNanos = measure(() -> VectorizedFilter.filter(batch, "value", predicate));
        long rowTotalNanos = measure(() -> RowWiseFilter.filter(batch, "value", predicate));
        int selected = VectorizedFilter.filter(batch, "value", predicate).rowCount();

        printReport("BETWEEN (single predicate)", selected,
                vectorEvalNanos, rowEvalNanos, vectorTotalNanos, rowTotalNanos);
    }

    @Test
    void compareMergedPredicatesTimings() {
        RowBatch batch = buildBatch(ROWS);

        for (int i = 0; i < 3; i++) {
            runVectorizedMerged(batch);
            runRowWiseMerged(batch);
        }

        long vectorEvalNanos = measure(() -> runVectorizedSelection(batch));
        long rowEvalNanos = measure(() -> runRowWiseSelection(batch));
        long vectorTotalNanos = measure(() -> runVectorizedMerged(batch));
        long rowTotalNanos = measure(() -> runRowWiseMerged(batch));

        assertThat(runVectorizedMerged(batch)).isEqualTo(runRowWiseMerged(batch));

        printReport("value > lo AND value < hi (merged)", runVectorizedMerged(batch).rowCount(),
                vectorEvalNanos, rowEvalNanos, vectorTotalNanos, rowTotalNanos);
    }

    private interface Timed {
        void run();
    }

    private static long measure(Timed timed) {
        long best = Long.MAX_VALUE;
        for (int i = 0; i < ITERATIONS; i++) {
            long start = System.nanoTime();
            timed.run();
            best = Math.min(best, System.nanoTime() - start);
        }
        return best;
    }

    private static void printReport(String label, int selected,
            long vectorEvalNanos, long rowEvalNanos, long vectorTotalNanos, long rowTotalNanos) {
        double vectorEvalMs = vectorEvalNanos / 1_000_000.0;
        double rowEvalMs = rowEvalNanos / 1_000_000.0;
        double vectorTotalMs = vectorTotalNanos / 1_000_000.0;
        double rowTotalMs = rowTotalNanos / 1_000_000.0;
        System.out.printf(
                "%n[perf] %s, rows=%d, selected=%d%n"
                        + "[perf] evaluate only  -> vectorized: %7.2f ms (%6.1f M rows/s) | "
                        + "row-wise: %7.2f ms (%6.1f M rows/s) | speedup %.2fx%n"
                        + "[perf] incl. material -> vectorized: %7.2f ms (%6.1f M rows/s) | "
                        + "row-wise: %7.2f ms (%6.1f M rows/s) | speedup %.2fx%n",
                label, ROWS, selected,
                vectorEvalMs, ROWS / vectorEvalMs / 1000.0,
                rowEvalMs, ROWS / rowEvalMs / 1000.0, rowEvalMs / vectorEvalMs,
                vectorTotalMs, ROWS / vectorTotalMs / 1000.0,
                rowTotalMs, ROWS / rowTotalMs / 1000.0, rowTotalMs / vectorTotalMs);
    }

    private static SelectionVector runVectorizedSelection(RowBatch batch) {
        SelectionVector low = greaterThan(200_000L).evaluate(batch.column("value"));
        SelectionVector high = lessThan(1_800_000L).evaluate(batch.column("value"));
        return low.and(high);
    }

    private static SelectionVector runRowWiseSelection(RowBatch batch) {
        ComparisonPredicate low = greaterThan(200_000L);
        ComparisonPredicate high = lessThan(1_800_000L);
        return RowWiseFilter.select(batch, "value", low)
                .and(RowWiseFilter.select(batch, "value", high));
    }

    private static RowBatch runVectorizedMerged(RowBatch batch) {
        return VectorizedFilter.filter(batch, runVectorizedSelection(batch));
    }

    private static RowBatch runRowWiseMerged(RowBatch batch) {
        return VectorizedFilter.filter(batch, runRowWiseSelection(batch));
    }

    private static RowBatch buildBatch(int n) {
        Random random = new Random(2024L);
        long[] ids = new long[n];
        long[] values = new long[n];
        boolean[] nulls = new boolean[n];
        for (int i = 0; i < n; i++) {
            ids[i] = i;
            if (i % 1000 == 0) {
                nulls[i] = true;
            } else {
                values[i] = random.nextInt(2_000_000);
            }
        }
        Map<String, ColumnVector> columns = new LinkedHashMap<>();
        columns.put("id", new LongColumnVector(ids, new boolean[n]));
        columns.put("value", new LongColumnVector(values, nulls));
        return new RowBatch(columns);
    }
}
