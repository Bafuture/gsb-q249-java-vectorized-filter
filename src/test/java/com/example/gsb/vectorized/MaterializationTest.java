package com.example.gsb.vectorized;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MaterializationTest {

    private Table fixture() {
        return Table.builder()
                .addColumn("id", ColumnVector.ofLong(
                        new long[]{1, 2, 3, 4, 5, 6},
                        new boolean[]{false, false, false, false, false, false}))
                .addColumn("city", ColumnVector.ofString(
                        new String[]{"bj", "sh", "gz", "sz", "hz", "cd"},
                        new boolean[]{false, false, true, false, false, false}))
                .build();
    }

    @Test
    void materializesOnlyTrueRowsInOriginalOrder() {
        Table t = fixture();
        MaterializedTable out = new VectorizedFilter(2)
                .filter(t, Predicates.gtLong("id", 2));

        assertThat(out.rowCount()).isEqualTo(4);
        // source row indexes must be strictly increasing and correct
        assertThat(out.sourceRow(0)).isEqualTo(2);
        assertThat(out.sourceRow(1)).isEqualTo(3);
        assertThat(out.sourceRow(2)).isEqualTo(4);
        assertThat(out.sourceRow(3)).isEqualTo(5);
        // values in original order
        assertThat(out.valueAt(0, 0)).isEqualTo(3L);
        assertThat(out.valueAt(1, 0)).isEqualTo(4L);
        assertThat(out.valueAt(2, 0)).isEqualTo(5L);
        assertThat(out.valueAt(3, 0)).isEqualTo(6L);
        assertThat(out.valueAt(0, 1)).isNull(); // city of id=3 is null in the fixture
        assertThat(out.valueAt(3, 1)).isEqualTo("cd");
    }

    @Test
    void unknownRowsAreDroppedLikeFalseRows() {
        Table t = fixture();
        // city is null at row 2 -> UNKNOWN for any comparison -> dropped
        MaterializedTable out = new VectorizedFilter(3)
                .filter(t, Predicates.betweenString("city", "a", "zzzz"));
        List<Object> cities = new ArrayList<>();
        for (int i = 0; i < out.rowCount(); i++) {
            cities.add(out.valueAt(i, 1));
        }
        assertThat(cities).containsExactly("bj", "sh", "sz", "hz", "cd");
        assertThat(cities).doesNotContainNull();
    }

    @Test
    void isNullPredicateMaterializesNullRowsWithNullPreserved() {
        Table t = fixture();
        MaterializedTable out = new VectorizedFilter(2)
                .filter(t, Predicates.isNull("city"));
        assertThat(out.rowCount()).isEqualTo(1);
        assertThat(out.valueAt(0, 0)).isEqualTo(3L);
        assertThat(out.valueAt(0, 1)).isNull();
        assertThat(out.sourceRow(0)).isEqualTo(2);
    }

    @Test
    void emptyResultIsSupported() {
        Table t = fixture();
        MaterializedTable out = new VectorizedFilter(2)
                .filter(t, Predicates.gtLong("id", 1_000));
        assertThat(out.rowCount()).isZero();
    }

    @Test
    void allRowsSelectedKeepsEverything() {
        Table t = fixture();
        MaterializedTable out = new VectorizedFilter(4)
                .filter(t, Predicates.gtLong("id", 0));
        assertThat(out.rowCount()).isEqualTo(6);
        for (int i = 0; i < 6; i++) {
            assertThat(out.sourceRow(i)).isEqualTo(i);
            assertThat(out.valueAt(i, 0)).isEqualTo((long) (i + 1));
        }
    }

    @Test
    void materializedNullsRoundTripThroughColumnVector() {
        Table t = fixture();
        MaterializedTable out = new VectorizedFilter(6)
                .filter(t, Predicates.isNull("city"));
        assertThat(out.columnNames()).containsExactly("id", "city");
        assertThat(out.valueAt(0, 1)).isNull();
    }
}
