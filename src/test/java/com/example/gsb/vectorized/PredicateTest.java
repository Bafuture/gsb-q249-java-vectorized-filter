package com.example.gsb.vectorized;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PredicateTest {

    private static final TriState T = TriState.TRUE;
    private static final TriState F = TriState.FALSE;
    private static final TriState U = TriState.UNKNOWN;

    private Table longTable() {
        // value 10 sits on a BETWEEN boundary; null mixed in
        return Table.builder()
                .addColumn("v", ColumnVector.ofLong(
                        new long[]{1, 5, 10, 11, 20},
                        new boolean[]{false, false, true, false, false}))
                .build();
    }

    private Table doubleTable() {
        return Table.builder()
                .addColumn("v", ColumnVector.ofDouble(
                        new double[]{1.5, 5.5, 10.0, 9.99, 20.0},
                        new boolean[]{false, false, true, false, false}))
                .build();
    }

    private Table stringTable() {
        return Table.builder()
                .addColumn("v", ColumnVector.ofString(
                        new String[]{"apple", "banana", null, "cherry", "date"},
                        new boolean[]{false, false, true, false, false}))
                .build();
    }

    private void assertAgreement(Table table, FilterPredicate predicate) {
        RowView row = new RowView(table);
        for (ColumnBatch batch : table.batches(2)) {
            SelectionVector sv = predicate.evaluateVector(batch);
            assertThat(sv.length()).isEqualTo(batch.size());
        }
        SelectionVector full = new VectorizedFilter(2).evaluate(table, predicate);
        for (int i = 0; i < table.rowCount(); i++) {
            row.moveTo(i);
            assertThat(full.get(i))
                    .as("row %d of %s", i, predicate)
                    .isEqualTo(predicate.evaluateRow(row));
        }
    }

    @Test
    void longOperatorsRespectBoundariesAndNulls() {
        Table t = longTable();
        assertAgreement(t, Predicates.gtLong("v", 10));
        assertAgreement(t, Predicates.ltLong("v", 5));
        assertAgreement(t, Predicates.eqLong("v", 11));
        assertAgreement(t, Predicates.betweenLong("v", 5, 11));

        // Explicit expectations: 10 is null -> UNKNOWN even though on boundary
        SelectionVector between = new VectorizedFilter(3).evaluate(t, Predicates.betweenLong("v", 5, 11));
        assertThat(between.get(0)).isEqualTo(F);   // 1
        assertThat(between.get(1)).isEqualTo(T);   // 5 inclusive
        assertThat(between.get(2)).isEqualTo(U);   // null
        assertThat(between.get(3)).isEqualTo(T);   // 11 inclusive
        assertThat(between.get(4)).isEqualTo(F);   // 20

        SelectionVector gt = new VectorizedFilter(3).evaluate(t, Predicates.gtLong("v", 10));
        assertThat(gt.get(2)).isEqualTo(U);
        assertThat(gt.get(3)).isEqualTo(T);
    }

    @Test
    void doubleOperatorsWork() {
        Table t = doubleTable();
        assertAgreement(t, Predicates.gtDouble("v", 5.0));
        assertAgreement(t, Predicates.ltDouble("v", 5.5));
        assertAgreement(t, Predicates.eqDouble("v", 10.0));
        assertAgreement(t, Predicates.betweenDouble("v", 5.5, 20.0));

        SelectionVector between = new VectorizedFilter(2).evaluate(t, Predicates.betweenDouble("v", 5.5, 20.0));
        assertThat(between.get(0)).isEqualTo(F);
        assertThat(between.get(1)).isEqualTo(T);
        assertThat(between.get(2)).isEqualTo(U);
        assertThat(between.get(3)).isEqualTo(T); // 9.99 is inside [5.5, 20]
        assertThat(between.get(4)).isEqualTo(T);
    }

    @Test
    void stringOperatorsUseLexicographicOrder() {
        Table t = stringTable();
        assertAgreement(t, Predicates.gtString("v", "banana"));
        assertAgreement(t, Predicates.ltString("v", "cherry"));
        assertAgreement(t, Predicates.eqString("v", "date"));
        assertAgreement(t, Predicates.betweenString("v", "banana", "date"));

        SelectionVector between = new VectorizedFilter(4).evaluate(t,
                Predicates.betweenString("v", "banana", "date"));
        assertThat(between.get(0)).isEqualTo(F); // apple
        assertThat(between.get(1)).isEqualTo(T); // banana inclusive
        assertThat(between.get(2)).isEqualTo(U); // null
        assertThat(between.get(3)).isEqualTo(T); // cherry
        assertThat(between.get(4)).isEqualTo(T); // date inclusive
    }

    @Test
    void comparisonsOnNullProduceUnknown() {
        Table t = longTable();
        SelectionVector eq = new VectorizedFilter(8).evaluate(t, Predicates.eqLong("v", 10));
        assertThat(eq.get(2)).isEqualTo(U);
        // a null is not equal to anything, nor not-equal: both are UNKNOWN
        SelectionVector gt = new VectorizedFilter(8).evaluate(t, Predicates.gtLong("v", 1_000_000));
        assertThat(gt.get(2)).isEqualTo(U);
    }

    @Test
    void isNullAndIsNotNullAreAlwaysDefinite() {
        Table t = stringTable();
        SelectionVector isNull = new VectorizedFilter(3).evaluate(t, Predicates.isNull("v"));
        SelectionVector isNotNull = new VectorizedFilter(3).evaluate(t, Predicates.isNotNull("v"));
        for (int i = 0; i < t.rowCount(); i++) {
            assertThat(isNull.get(i)).isNotEqualTo(U);
            assertThat(isNotNull.get(i)).isNotEqualTo(U);
        }
        assertThat(isNull.get(2)).isEqualTo(T);
        assertThat(isNotNull.get(2)).isEqualTo(F);
        assertThat(isNull.get(0)).isEqualTo(F);
        assertThat(isNotNull.get(0)).isEqualTo(T);
        // IS NOT NULL is exactly the definite negation of IS NULL
        for (int i = 0; i < t.rowCount(); i++) {
            assertThat(isNotNull.get(i)).isEqualTo(isNull.get(i) == T ? F : T);
        }
    }

    @Test
    void nestedLogicalPredicatesAgreeRowAndVector() {
        Table t = TestVectors.randomTable(200, 42);
        FilterPredicate p = Predicates.and(
                Predicates.or(
                        Predicates.betweenLong("id", 100_000, 900_000),
                        Predicates.isNull("id")),
                Predicates.not(
                        Predicates.and(
                                Predicates.gtDouble("score", 80.0),
                                Predicates.eqString("name", "gamma"))));
        assertAgreement(t, p);
    }

    @Test
    void operandTypeMismatchIsRejected() {
        Table t = longTable();
        ColumnBatch batch = t.batches(10).get(0);
        assertThatThrownBy(() -> Predicates.gtDouble("v", 1.0).evaluateVector(batch))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match column");
    }

    @Test
    void unknownColumnIsRejected() {
        Table t = longTable();
        ColumnBatch batch = t.batches(10).get(0);
        assertThatThrownBy(() -> Predicates.gtLong("missing", 1).evaluateVector(batch))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown column");
    }
}
