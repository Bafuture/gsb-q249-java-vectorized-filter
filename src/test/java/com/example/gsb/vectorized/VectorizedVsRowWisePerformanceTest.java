package com.example.gsb.vectorized;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Requirement: side-by-side timing of the vectorized batch filter versus the
 * row-wise reference filter on identical data, plus a hard equality check on
 * their outputs. Timings are printed and written to
 * {@code target/perf-results.txt}.
 *
 * <p>The test asserts <b>correctness</b> unconditionally. It records (but does
 * not hard-fail on) the speedup, since CI machines vary; inspect the printed
 * report for the expected order-of-magnitude advantage of the vectorized path.
 */
@TestMethodOrder(MethodOrderer.MethodName.class)
class VectorizedVsRowWisePerformanceTest {

    private static final int ROWS = 2_000_000;
    private static final int BATCH = 4096;
    private static final int WARMUP = 3;
    private static final int MEASURED = 6;

    private final Table table = TestVectors.randomTable(ROWS, 99L);

    private final List<FilterPredicate> predicates = List.of(
            Predicates.betweenLong("id", 200_000, 800_000),
            Predicates.and(
                    Predicates.or(
                            Predicates.betweenLong("id", 100_000, 900_000),
                            Predicates.isNull("id")),
                    Predicates.and(
                            Predicates.gtDouble("score", 30.0),
                            Predicates.not(Predicates.eqString("name", "delta")))));

    private static double runMs(Runnable task, int iterations) {
        long best = Long.MAX_VALUE;
        long total = 0;
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            task.run();
            long elapsed = System.nanoTime() - start;
            best = Math.min(best, elapsed);
            total += elapsed;
        }
        return (total / (double) iterations) / 1_000_000.0;
    }

    @Test
    void compareTimingsAndAssertEquivalentResults() throws IOException {
        VectorizedFilter vectorized = new VectorizedFilter(BATCH);
        RowWiseFilter rowWise = new RowWiseFilter();

        List<String> report = new ArrayList<>();
        String header = String.format(
                "rows=%d, batchSize=%d, warmup=%d, measured=%d, cpu=%s %s",
                ROWS, BATCH, WARMUP, MEASURED,
                System.getProperty("java.vm.name"), System.getProperty("java.version"));
        report.add(header);
        report.add("predicate | row-wise avg ms | vectorized avg ms | speedup x | vectorized rows | row-wise rows");

        for (FilterPredicate predicate : predicates) {
            // warmup both paths so JIT / GC state is comparable
            for (int i = 0; i < WARMUP; i++) {
                vectorized.filter(table, predicate);
                rowWise.filter(table, predicate);
            }

            // hard correctness gate: identical materialized results
            MaterializedTable vecResult = vectorized.filter(table, predicate);
            MaterializedTable rowResult = rowWise.filter(table, predicate);
            assertThat(vecResult).as(predicate + " results equal").isEqualTo(rowResult);
            assertThat(vectorized.evaluate(table, predicate))
                    .as(predicate + " full 3VL vector equal")
                    .isEqualTo(rowWise.selectionVector(table, predicate));

            double rowMs = runMs(() -> rowWise.filter(table, predicate), MEASURED);
            double vecMs = runMs(() -> vectorized.filter(table, predicate), MEASURED);
            double speedup = rowMs / vecMs;

            String line = String.format("%s | %.2f | %.2f | %.2fx | %d | %d",
                    predicate, rowMs, vecMs, speedup,
                    vecResult.rowCount(), rowResult.rowCount());
            report.add(line);
        }

        String text = String.join(System.lineSeparator(), report);
        System.out.println();
        System.out.println("==== vectorized vs row-wise filter timing ====");
        System.out.println(text);
        System.out.println("==============================================");

        Path out = Path.of("target", "perf-results.txt");
        Files.createDirectories(out.getParent());
        Files.writeString(out, text + System.lineSeparator());
    }
}
