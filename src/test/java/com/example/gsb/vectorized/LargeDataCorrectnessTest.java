package com.example.gsb.vectorized;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Requirement: vectorized results must be identical to the row-wise reference
 * on large data, including all three states and complex nested predicates.
 */
class LargeDataCorrectnessTest {

    private static final int ROWS = 200_001;

    private List<FilterPredicate> predicates() {
        return List.of(
                Predicates.gtLong("id", 500_000),
                Predicates.betweenLong("id", 250_000, 750_000),
                Predicates.eqLong("id", 424_242),
                Predicates.betweenDouble("score", 25.0, 75.0),
                Predicates.eqString("name", "gamma"),
                Predicates.or(
                        Predicates.and(
                                Predicates.gtLong("id", 300_000),
                                Predicates.ltDouble("score", 60.0)),
                        Predicates.eqString("name", "delta")),
                Predicates.and(
                        Predicates.or(
                                Predicates.isNull("id"),
                                Predicates.isNull("score")),
                        Predicates.isNotNull("name")),
                Predicates.not(Predicates.betweenLong("id", 100_000, 900_000)),
                Predicates.and(
                        Predicates.betweenLong("id", 1, 999_999),
                        Predicates.or(
                                Predicates.gtDouble("score", 90.0),
                                Predicates.ltDouble("score", 10.0))));
    }

    @Test
    void vectorizedMatchesRowWiseOnLargeData() {
        Table table = TestVectors.randomTable(ROWS, 20240917L);
        RowWiseFilter rowWise = new RowWiseFilter();

        for (FilterPredicate predicate : predicates()) {
            SelectionVector reference = rowWise.selectionVector(table, predicate);

            for (int batchSize : new int[]{1024, 4096, 10_000, ROWS / 3, ROWS}) {
                SelectionVector vectorized = new VectorizedFilter(batchSize).evaluate(table, predicate);

                assertThat(vectorized.length())
                        .as("%s length (batch %d)", predicate, batchSize)
                        .isEqualTo(ROWS);
                assertThat(vectorized)
                        .as("%s full 3VL vector (batch %d)", predicate, batchSize)
                        .isEqualTo(reference);

                // materialization must also agree, and preserve source order
                MaterializedTable a = MaterializedTable.gather(table, vectorized);
                MaterializedTable b = rowWise.filter(table, predicate);
                assertThat(a).as("%s materialized (batch %d)", predicate, batchSize).isEqualTo(b);
                for (int r = 1; r < a.rowCount(); r++) {
                    assertThat(a.sourceRow(r))
                            .as("%s ascending at %d", predicate, r)
                            .isGreaterThan(a.sourceRow(r - 1));
                }
            }
        }
    }

    @Test
    void unknownAndTrueCountsAreConsistent() {
        Table table = TestVectors.randomTable(ROWS, 7L);
        FilterPredicate p = Predicates.and(
                Predicates.gtLong("id", 100_000),
                Predicates.isNotNull("score"));

        List<TriState> states = new RowWiseFilter().evaluate(table, p);
        long trueCount = states.stream().filter(TriState.TRUE::equals).count();
        long unknownCount = states.stream().filter(TriState.UNKNOWN::equals).count();
        long falseCount = states.stream().filter(TriState.FALSE::equals).count();
        assertThat(trueCount + unknownCount + falseCount).isEqualTo(ROWS);

        SelectionVector sv = new VectorizedFilter(2048).evaluate(table, p);
        assertThat(sv.trueCount()).isEqualTo((int) trueCount);
        // every null-id or null-score row outside a definite-FALSE short circuit is UNKNOWN
        int countedUnknown = 0;
        for (int i = 0; i < ROWS; i++) {
            if (sv.get(i) == TriState.UNKNOWN) {
                countedUnknown++;
            }
        }
        assertThat(countedUnknown).isEqualTo((int) unknownCount);
    }
}
