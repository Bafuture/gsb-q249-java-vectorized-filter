package com.example.gsb.vectorized;

import static com.example.gsb.vectorized.Predicates.between;
import static com.example.gsb.vectorized.Predicates.equalTo;
import static com.example.gsb.vectorized.Predicates.greaterThan;
import static com.example.gsb.vectorized.Predicates.lessThan;
import static com.example.gsb.vectorized.SelectionVector.FALSE;
import static com.example.gsb.vectorized.SelectionVector.TRUE;
import static com.example.gsb.vectorized.SelectionVector.UNKNOWN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PredicateTest {

    @Test
    void allOperatorsOnLongColumn() {
        boolean[] nulls = {false, true, false, false, false};
        LongColumnVector column = new LongColumnVector(new long[]{1, 5, 10, 7, 3}, nulls);

        assertThat(greaterThan(3L).evaluate(column).states())
                .containsExactly(FALSE, UNKNOWN, TRUE, TRUE, FALSE);
        assertThat(lessThan(3L).evaluate(column).states())
                .containsExactly(TRUE, UNKNOWN, FALSE, FALSE, FALSE);
        assertThat(equalTo(7L).evaluate(column).states())
                .containsExactly(FALSE, UNKNOWN, FALSE, TRUE, FALSE);
        assertThat(between(3L, 7L).evaluate(column).states())
                .containsExactly(FALSE, UNKNOWN, FALSE, TRUE, TRUE);
    }

    @Test
    void doubleColumnComparisons() {
        boolean[] nulls = {false, false, true};
        DoubleColumnVector column = new DoubleColumnVector(new double[]{1.5, 4.25, 9.0}, nulls);

        assertThat(greaterThan(2.0).evaluate(column).states())
                .containsExactly(FALSE, TRUE, UNKNOWN);
        assertThat(between(1.5, 4.25).evaluate(column).states())
                .containsExactly(TRUE, TRUE, UNKNOWN);
    }

    @Test
    void stringComparisonsAreLexicographic() {
        StringColumnVector column = StringColumnVector.of("apple", "banana", null, "cherry", "date");

        assertThat(greaterThan("banana").evaluate(column).states())
                .containsExactly(FALSE, FALSE, UNKNOWN, TRUE, TRUE);
        assertThat(lessThan("cherry").evaluate(column).states())
                .containsExactly(TRUE, TRUE, UNKNOWN, FALSE, FALSE);
        assertThat(equalTo("banana").evaluate(column).states())
                .containsExactly(FALSE, TRUE, UNKNOWN, FALSE, FALSE);
        assertThat(between("banana", "date").evaluate(column).states())
                .containsExactly(FALSE, TRUE, UNKNOWN, TRUE, TRUE);
    }

    @Test
    void nullProducesUnknownNeverTrueOrFalse() {
        LongColumnVector allNull = new LongColumnVector(new long[2], new boolean[]{true, true});
        SelectionVector selection = between(0L, 10L).evaluate(allNull);

        assertThat(selection.states()).containsExactly(UNKNOWN, UNKNOWN);
        assertThat(selection.selectedCount()).isZero();
        assertThat(selection.isSelected(0)).isFalse();
    }

    @Test
    void rejectsInvalidBounds() {
        assertThatThrownBy(() -> between(10L, 1L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ComparisonPredicate(ComparisonPredicate.Op.EQ, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
