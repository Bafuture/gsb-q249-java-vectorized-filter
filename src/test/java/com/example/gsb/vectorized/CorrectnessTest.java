package com.example.gsb.vectorized;

import static com.example.gsb.vectorized.Predicates.between;
import static com.example.gsb.vectorized.Predicates.equalTo;
import static com.example.gsb.vectorized.Predicates.greaterThan;
import static com.example.gsb.vectorized.Predicates.lessThan;
import static com.example.gsb.vectorized.SelectionVector.FALSE;
import static com.example.gsb.vectorized.SelectionVector.TRUE;
import static com.example.gsb.vectorized.SelectionVector.UNKNOWN;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class CorrectnessTest {

    private static final int ROWS = 1_000_000;
    private static final double NULL_RATE = 0.05;
    private static final String[] WORDS = {"alpha", "bravo", "charlie", "delta", "echo", null};

    @Test
    void vectorizedPredicatesMatchRowWiseOnLargeRandomData() {
        RowBatch batch = randomBatch(ROWS, 42L);

        String[] targetColumns = {"longValue", "longValue", "longValue", "longValue",
                "doubleValue", "doubleValue", "word", "word", "word"};
        List<ComparisonPredicate> predicates = List.of(
                greaterThan(100_000L),
                lessThan(900_000L),
                equalTo(555_555L),
                between(250_000L, 750_000L),
                greaterThan(0.5),
                between(-1.0, 0.25),
                equalTo("charlie"),
                greaterThan("bravo"),
                between("a", "d"));

        for (int i = 0; i < predicates.size(); i++) {
            ComparisonPredicate predicate = predicates.get(i);
            String column = targetColumns[i];

            SelectionVector vectorSelection = predicate.evaluate(batch.column(column));
            SelectionVector rowSelection = RowWiseFilter.select(batch, column, predicate);
            assertThat(vectorSelection)
                    .as("selection for %s on %s", predicate, column)
                    .isEqualTo(rowSelection);

            RowBatch vectorized = VectorizedFilter.filter(batch, column, predicate);
            RowBatch rowWise = RowWiseFilter.filter(batch, column, predicate);
            assertThat(vectorized)
                    .as("materialized result for %s on %s", predicate, column)
                    .isEqualTo(rowWise);
        }
    }

    @Test
    void mergedAndOrNotMatchesIndependentRowWiseOracle() {
        RowBatch batch = randomBatch(ROWS, 7L);
        LongColumnVector longColumn = (LongColumnVector) batch.column("longValue");
        StringColumnVector wordColumn = (StringColumnVector) batch.column("word");

        // Combined predicate:
        //   (longValue > 100000 AND longValue < 900000) OR NOT (word = "delta")
        SelectionVector lo = greaterThan(100_000L).evaluate(longColumn);
        SelectionVector hi = lessThan(900_000L).evaluate(longColumn);
        SelectionVector word = equalTo("delta").evaluate(wordColumn);
        SelectionVector combined = lo.and(hi).or(word.not());

        // Independent per-row oracle written directly against the raw data.
        boolean[] longNulls = longColumn.nulls();
        long[] longValues = longColumn.values();
        String[] words = wordColumn.values();
        byte[] expected = new byte[ROWS];
        for (int i = 0; i < ROWS; i++) {
            byte rangeState;
            if (longNulls[i]) {
                rangeState = UNKNOWN;
            } else {
                rangeState = (longValues[i] > 100_000L && longValues[i] < 900_000L) ? TRUE : FALSE;
            }
            byte wordState = words[i] == null ? UNKNOWN : ("delta".equals(words[i]) ? TRUE : FALSE);
            byte notWord = (byte) (TRUE - wordState);
            expected[i] = (byte) Math.max(rangeState, notWord);
        }
        assertThat(combined.states()).isEqualTo(expected);

        RowBatch materialized = VectorizedFilter.filter(batch, combined);
        assertThat(materialized.rowCount()).isEqualTo(combined.selectedCount());
    }

    @Test
    void perBatchVectorizedConcatenationMatchesRowWiseOverWholeTable() {
        RowBatch batch = randomBatch(ROWS, 99L);
        ComparisonPredicate predicate = between(300_000L, 700_000L);

        List<RowBatch> slices = batch.split(4096);
        List<RowBatch> filtered = VectorizedFilter.filterBatches(slices, "longValue", predicate);

        int[] expectedIndexes = RowWiseFilter.select(batch, "longValue", predicate).toRowIndexes();
        List<Long> actualIds = new ArrayList<>();
        for (RowBatch part : filtered) {
            for (long id : ((LongColumnVector) part.column("id")).values()) {
                actualIds.add(id);
            }
        }
        assertThat(actualIds).hasSize(expectedIndexes.length);
        for (int i = 0; i < expectedIndexes.length; i++) {
            assertThat(actualIds.get(i)).isEqualTo((long) expectedIndexes[i]);
        }
    }

    private static RowBatch randomBatch(int n, long seed) {
        Random random = new Random(seed);
        long[] ids = new long[n];
        long[] longs = new long[n];
        boolean[] longNulls = new boolean[n];
        double[] doubles = new double[n];
        boolean[] doubleNulls = new boolean[n];
        String[] words = new String[n];

        for (int i = 0; i < n; i++) {
            ids[i] = i;
            if (random.nextDouble() < NULL_RATE) {
                longNulls[i] = true;
            } else {
                longs[i] = random.nextInt(1_000_000);
            }
            if (random.nextDouble() < NULL_RATE) {
                doubleNulls[i] = true;
            } else {
                doubles[i] = random.nextDouble() * 2.0 - 1.0;
            }
            words[i] = WORDS[random.nextInt(WORDS.length)];
        }

        Map<String, ColumnVector> columns = new LinkedHashMap<>();
        columns.put("id", new LongColumnVector(ids, new boolean[n]));
        columns.put("longValue", new LongColumnVector(longs, longNulls));
        columns.put("doubleValue", new DoubleColumnVector(doubles, doubleNulls));
        columns.put("word", new StringColumnVector(words));
        return new RowBatch(columns);
    }
}
