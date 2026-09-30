package com.example.gsb.vectorized;

import java.util.Random;

/** Shared helpers for tests: build vectors from 3VL states and random tables. */
final class TestVectors {

    private TestVectors() {
    }

    static SelectionVector of(TriState... states) {
        int n = states.length;
        long[] match = new long[(n + 63) >>> 6];
        long[] known = new long[match.length];
        for (int i = 0; i < n; i++) {
            if (states[i] != TriState.UNKNOWN) {
                known[i >>> 6] |= 1L << (i & 63);
                if (states[i] == TriState.TRUE) {
                    match[i >>> 6] |= 1L << (i & 63);
                }
            }
        }
        return SelectionVector.fromBits(n, match, known);
    }

    /**
     * Deterministic random table with three columns:
     * {@code id} LONG, {@code score} DOUBLE, {@code name} STRING.
     * Nulls are injected on fixed cadences so tests exercise 3VL heavily.
     */
    static Table randomTable(int rows, long seed) {
        Random rnd = new Random(seed);
        String[] names = {"alpha", "beta", "gamma", "delta", "epsilon"};

        long[] ids = new long[rows];
        double[] scores = new double[rows];
        String[] nameValues = new String[rows];
        boolean[] idNull = new boolean[rows];
        boolean[] scoreNull = new boolean[rows];
        boolean[] nameNull = new boolean[rows];

        for (int i = 0; i < rows; i++) {
            ids[i] = rnd.nextInt(1_000_000);
            scores[i] = rnd.nextDouble() * 100.0;
            nameValues[i] = names[rnd.nextInt(names.length)];
            idNull[i] = i % 97 == 5;
            scoreNull[i] = i % 53 == 3;
            nameNull[i] = i % 31 == 7;
        }
        return Table.builder()
                .addColumn("id", ColumnVector.ofLong(ids, idNull))
                .addColumn("score", ColumnVector.ofDouble(scores, scoreNull))
                .addColumn("name", ColumnVector.ofString(nameValues, nameNull))
                .build();
    }
}
