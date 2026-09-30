package com.example.gsb.vectorized;

import static com.example.gsb.vectorized.Predicates.greaterThan;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MaterializationTest {

    @Test
    void materializesOnlySelectedRowsInOriginalOrder() {
        LongColumnVector id = LongColumnVector.of(10, 11, 12, 13, 14);
        LongColumnVector value = LongColumnVector.of(5, 3, 8, 1, 9);
        StringColumnVector label = StringColumnVector.of("e", "c", "h", "a", "i");
        RowBatch batch = batch(id, value, label);

        RowBatch result = VectorizedFilter.filter(batch, "value", greaterThan(2L));

        assertThat(result.rowCount()).isEqualTo(4);
        assertThat(result.column("id")).isEqualTo(LongColumnVector.of(10, 11, 12, 14));
        assertThat(result.column("value")).isEqualTo(LongColumnVector.of(5, 3, 8, 9));
        assertThat(result.column("label")).isEqualTo(StringColumnVector.of("e", "c", "h", "i"));
    }

    @Test
    void materializationPreservesNullsAcrossAllColumns() {
        boolean[] valueNulls = {false, true, false};
        LongColumnVector id = LongColumnVector.of(0, 1, 2);
        LongColumnVector value = new LongColumnVector(new long[]{7, 100, 2}, valueNulls);
        RowBatch batch = batch(id, value);

        // NULL row evaluates to UNKNOWN and is therefore not materialized
        RowBatch result = VectorizedFilter.filter(batch, "value", greaterThan(5L));

        assertThat(result.rowCount()).isEqualTo(1);
        assertThat(result.column("id")).isEqualTo(LongColumnVector.of(0));
        LongColumnVector filteredValue = (LongColumnVector) result.column("value");
        assertThat(filteredValue.value(0)).isEqualTo(7L);
        assertThat(filteredValue.isNull(0)).isFalse();
    }

    @Test
    void emptySelectionYieldsEmptyBatchWithSchemaIntact() {
        RowBatch batch = batch(LongColumnVector.of(1, 2), StringColumnVector.of("a", "b"));
        RowBatch result = VectorizedFilter.filter(batch, "id", greaterThan(100L));

        assertThat(result.rowCount()).isZero();
        assertThat(result.columnNames()).containsExactly("id", "value");
        assertThat(result.column("id").size()).isZero();
        assertThat(result.column("value").size()).isZero();
    }

    private static RowBatch batch(ColumnVector... columns) {
        String[] names = {"id", "value", "label"};
        Map<String, ColumnVector> map = new LinkedHashMap<>();
        for (int i = 0; i < columns.length; i++) {
            map.put(names[i], columns[i]);
        }
        return new RowBatch(map);
    }
}
