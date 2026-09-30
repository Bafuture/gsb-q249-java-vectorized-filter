package com.example.gsb.vectorized;

import static com.example.gsb.vectorized.Predicates.greaterThan;
import static com.example.gsb.vectorized.Predicates.lessThan;
import static com.example.gsb.vectorized.SelectionVector.FALSE;
import static com.example.gsb.vectorized.SelectionVector.TRUE;
import static com.example.gsb.vectorized.SelectionVector.UNKNOWN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SelectionVectorTest {

    private static SelectionVector vector(byte... states) {
        return new SelectionVector(states);
    }

    @Test
    void andImplementsThreeValuedTruthTable() {
        // T AND x
        assertThat(vector(TRUE).and(vector(TRUE)).state(0)).isEqualTo(TRUE);
        assertThat(vector(TRUE).and(vector(FALSE)).state(0)).isEqualTo(FALSE);
        assertThat(vector(TRUE).and(vector(UNKNOWN)).state(0)).isEqualTo(UNKNOWN);
        // F AND x: false dominates
        assertThat(vector(FALSE).and(vector(TRUE)).state(0)).isEqualTo(FALSE);
        assertThat(vector(FALSE).and(vector(FALSE)).state(0)).isEqualTo(FALSE);
        assertThat(vector(FALSE).and(vector(UNKNOWN)).state(0)).isEqualTo(FALSE);
        // U AND x
        assertThat(vector(UNKNOWN).and(vector(TRUE)).state(0)).isEqualTo(UNKNOWN);
        assertThat(vector(UNKNOWN).and(vector(FALSE)).state(0)).isEqualTo(FALSE);
        assertThat(vector(UNKNOWN).and(vector(UNKNOWN)).state(0)).isEqualTo(UNKNOWN);
    }

    @Test
    void orImplementsThreeValuedTruthTable() {
        // T OR x: true dominates
        assertThat(vector(TRUE).or(vector(TRUE)).state(0)).isEqualTo(TRUE);
        assertThat(vector(TRUE).or(vector(FALSE)).state(0)).isEqualTo(TRUE);
        assertThat(vector(TRUE).or(vector(UNKNOWN)).state(0)).isEqualTo(TRUE);
        // F OR x
        assertThat(vector(FALSE).or(vector(TRUE)).state(0)).isEqualTo(TRUE);
        assertThat(vector(FALSE).or(vector(FALSE)).state(0)).isEqualTo(FALSE);
        assertThat(vector(FALSE).or(vector(UNKNOWN)).state(0)).isEqualTo(UNKNOWN);
        // U OR x
        assertThat(vector(UNKNOWN).or(vector(TRUE)).state(0)).isEqualTo(TRUE);
        assertThat(vector(UNKNOWN).or(vector(FALSE)).state(0)).isEqualTo(UNKNOWN);
        assertThat(vector(UNKNOWN).or(vector(UNKNOWN)).state(0)).isEqualTo(UNKNOWN);
    }

    @Test
    void notSwapsTrueAndFalseAndKeepsUnknown() {
        assertThat(vector(TRUE, FALSE, UNKNOWN).not().states())
                .containsExactly(FALSE, TRUE, UNKNOWN);
        assertThat(vector(TRUE, FALSE, UNKNOWN).not().not().states())
                .containsExactly(TRUE, FALSE, UNKNOWN);
    }

    @Test
    void mergesTwoPredicatesAcrossWholeColumn() {
        // SQL equivalent: WHERE value > 2 AND value < 8
        LongColumnVector column = LongColumnVector.of(1, 5, 9, 7, 3);
        SelectionVector lower = greaterThan(2L).evaluate(column);
        SelectionVector upper = lessThan(8L).evaluate(column);

        SelectionVector anded = lower.and(upper);
        assertThat(anded.states()).containsExactly(FALSE, TRUE, FALSE, TRUE, TRUE);
        assertThat(anded.toRowIndexes()).containsExactly(1, 3, 4);
        assertThat(anded.selectedCount()).isEqualTo(3);

        SelectionVector ored = lower.or(upper);
        assertThat(ored.states()).containsExactly(TRUE, TRUE, TRUE, TRUE, TRUE);
    }

    @Test
    void mergingWithNullsFollowsThreeValuedLogic() {
        // value > 2 -> [F, ?, T] ; value < 8 -> [T, ?, F]
        boolean[] nulls = {false, true, false};
        LongColumnVector column = new LongColumnVector(new long[]{1, 5, 9}, nulls);
        SelectionVector lower = greaterThan(2L).evaluate(column);
        SelectionVector upper = lessThan(8L).evaluate(column);

        // null AND anything-non-false stays unknown; null OR anything-non-true stays unknown
        assertThat(lower.and(upper).states()).containsExactly(FALSE, UNKNOWN, FALSE);
        assertThat(lower.or(upper).states()).containsExactly(TRUE, UNKNOWN, TRUE);
        // NOT of a null comparison is still unknown: NULL never sneaks into the result set
        assertThat(lower.not().states()).containsExactly(TRUE, UNKNOWN, FALSE);
        assertThat(lower.and(upper).toRowIndexes()).isEmpty();
    }

    @Test
    void mergingDifferentLengthsIsRejected() {
        SelectionVector a = vector(TRUE, FALSE);
        SelectionVector b = vector(TRUE, FALSE, UNKNOWN);
        assertThatThrownBy(() -> a.and(b)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> a.or(b)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void chainedMergesAreUnchanged() {
        SelectionVector a = vector(TRUE, FALSE, UNKNOWN);
        SelectionVector b = vector(UNKNOWN, UNKNOWN, UNKNOWN);
        SelectionVector c = vector(FALSE, TRUE, UNKNOWN);

        SelectionVector combined = a.and(b).or(c);
        // ((a AND b) OR c) = ([U, F, U] OR [F, T, U]) = [U, T, U]
        assertThat(combined.states()).containsExactly(UNKNOWN, TRUE, UNKNOWN);
    }
}
