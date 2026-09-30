package com.example.gsb.vectorized;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SelectionVectorTest {

    private static final TriState T = TriState.TRUE;
    private static final TriState F = TriState.FALSE;
    private static final TriState U = TriState.UNKNOWN;

    @Test
    void storesAndReadsAllThreeStates() {
        SelectionVector sv = TestVectors.of(T, F, U, T, U, F);
        assertThat(sv.get(0)).isEqualTo(T);
        assertThat(sv.get(1)).isEqualTo(F);
        assertThat(sv.get(2)).isEqualTo(U);
        assertThat(sv.get(3)).isEqualTo(T);
        assertThat(sv.get(4)).isEqualTo(U);
        assertThat(sv.get(5)).isEqualTo(F);
        assertThat(sv.trueCount()).isEqualTo(2);
        assertThat(sv.toString()).isEqualTo("10?1?0");
    }

    @Test
    void allFalseAndAllUnknownFactories() {
        SelectionVector f = SelectionVector.allFalse(70);
        SelectionVector u = SelectionVector.allUnknown(70);
        for (int i = 0; i < 70; i++) {
            assertThat(f.get(i)).isEqualTo(F);
            assertThat(u.get(i)).isEqualTo(U);
        }
        assertThat(f.trueCount()).isZero();
        assertThat(u.trueCount()).isZero();
    }

    @Test
    void andImplementsThreeValuedTruthTableAcrossWordBoundary() {
        // 65 rows: all 9 combinations of {T,F,U} x {T,F,U} on both sides of the
        // 64-bit word boundary, then the same again shifted by one.
        TriState[] vals = {T, F, U};
        List<TriState> left = new ArrayList<>();
        List<TriState> right = new ArrayList<>();
        for (int rep = 0; rep < 8; rep++) {
            for (TriState a : vals) {
                for (TriState b : vals) {
                    left.add(a);
                    right.add(b);
                }
            }
        }
        SelectionVector l = TestVectors.of(left.toArray(TriState[]::new));
        SelectionVector r = TestVectors.of(right.toArray(TriState[]::new));

        SelectionVector result = l.and(r);
        for (int i = 0; i < left.size(); i++) {
            assertThat(result.get(i))
                    .as("AND row %d: %s AND %s", i, left.get(i), right.get(i))
                    .isEqualTo(TriState.and(left.get(i), right.get(i)));
        }
    }

    @Test
    void orImplementsThreeValuedTruthTableAcrossWordBoundary() {
        TriState[] vals = {T, F, U};
        List<TriState> left = new ArrayList<>();
        List<TriState> right = new ArrayList<>();
        for (int rep = 0; rep < 8; rep++) {
            for (TriState a : vals) {
                for (TriState b : vals) {
                    left.add(a);
                    right.add(b);
                }
            }
        }
        SelectionVector l = TestVectors.of(left.toArray(TriState[]::new));
        SelectionVector r = TestVectors.of(right.toArray(TriState[]::new));

        SelectionVector result = l.or(r);
        for (int i = 0; i < left.size(); i++) {
            assertThat(result.get(i))
                    .as("OR row %d: %s OR %s", i, left.get(i), right.get(i))
                    .isEqualTo(TriState.or(left.get(i), right.get(i)));
        }
    }

    @Test
    void notIsThreeValuedAndInvolutiveForDefiniteValues() {
        SelectionVector sv = TestVectors.of(T, F, U, T, F);
        SelectionVector neg = sv.not();
        assertThat(neg.get(0)).isEqualTo(F);
        assertThat(neg.get(1)).isEqualTo(T);
        assertThat(neg.get(2)).isEqualTo(U);
        assertThat(neg.not().get(0)).isEqualTo(T);
        assertThat(neg.not().get(1)).isEqualTo(F);
        // NOT NOT UNKNOWN stays UNKNOWN
        assertThat(neg.not().get(2)).isEqualTo(U);
    }

    @Test
    void keyNullCases() {
        // FALSE AND UNKNOWN = FALSE ; UNKNOWN AND TRUE = UNKNOWN
        assertThat(TestVectors.of(F).and(TestVectors.of(U)).get(0)).isEqualTo(F);
        assertThat(TestVectors.of(U).and(TestVectors.of(T)).get(0)).isEqualTo(U);
        // TRUE OR UNKNOWN = TRUE  ; UNKNOWN OR FALSE = UNKNOWN
        assertThat(TestVectors.of(T).or(TestVectors.of(U)).get(0)).isEqualTo(T);
        assertThat(TestVectors.of(U).or(TestVectors.of(F)).get(0)).isEqualTo(U);
    }

    @Test
    void concatJoinsVectorsIncludingUnalignedOffsets() {
        SelectionVector a = TestVectors.of(T, F, U, T, T);            // len 5
        SelectionVector b = TestVectors.of(U, T, F, F, T, U, T, T);  // len 8
        SelectionVector c = TestVectors.of(F, T, U);                  // len 3
        SelectionVector all = SelectionVector.concat(List.of(a, b, c));

        assertThat(all.length()).isEqualTo(16);
        TriState[] expected = {T, F, U, T, T, U, T, F, F, T, U, T, T, F, T, U};
        for (int i = 0; i < expected.length; i++) {
            assertThat(all.get(i)).as("row %d", i).isEqualTo(expected[i]);
        }
        assertThat(all.trueCount()).isEqualTo(8);
    }

    @Test
    void concatOfLongVectorsSpansMultipleWords() {
        // three 65-row parts shifted by non-word-aligned 130/195 boundaries
        List<SelectionVector> parts = new ArrayList<>();
        TriState[] pattern = {T, F, U};
        for (int p = 0; p < 3; p++) {
            TriState[] states = new TriState[65];
            for (int i = 0; i < 65; i++) {
                states[i] = pattern[(i + p) % 3];
            }
            parts.add(TestVectors.of(states));
        }
        SelectionVector all = SelectionVector.concat(parts);
        assertThat(all.length()).isEqualTo(195);
        int offset = 0;
        for (SelectionVector part : parts) {
            for (int i = 0; i < part.length(); i++) {
                assertThat(all.get(offset + i)).isEqualTo(part.get(i));
            }
            offset += part.length();
        }
    }

    @Test
    void truePositionsAreAscendingAndComplete() {
        TriState[] states = {T, F, T, U, T, F, F, T};
        // add a second word of data
        TriState[] grown = new TriState[130];
        System.arraycopy(states, 0, grown, 0, states.length);
        java.util.Arrays.fill(grown, states.length, grown.length, F);
        grown[64] = T;
        grown[127] = T;
        SelectionVector sv = TestVectors.of(grown);

        List<Integer> positions = new ArrayList<>();
        sv.truePositions().forEachRemaining((int i) -> positions.add(i));
        assertThat(positions).containsExactly(0, 2, 4, 7, 64, 127);
    }

    @Test
    void rejectsMergesOfDifferentLengthsAndBadIndexes() {
        SelectionVector a = TestVectors.of(T, F);
        SelectionVector b = TestVectors.of(T, F, T);
        assertThatThrownBy(() -> a.and(b)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> a.or(b)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> a.get(2)).isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    void emptyVectorsAreSupported() {
        SelectionVector empty = SelectionVector.concat(List.of());
        assertThat(empty.length()).isZero();
        assertThat(empty.trueCount()).isZero();
        assertThat(empty.and(SelectionVector.allFalse(0)).length()).isZero();
    }
}
