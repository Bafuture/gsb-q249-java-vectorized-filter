package com.example.gsb.vectorized;

import java.util.Arrays;

/**
 * Per-row truth state for every row of a batch: the output of vectorized predicate evaluation.
 *
 * <p><b>Three-valued logic (SQL/Kleene logic).</b> Because input columns may contain NULL,
 * each row is in one of three states instead of plain true/false:
 * <ul>
 *   <li>{@link #FALSE} (0) — the predicate is provably false;</li>
 *   <li>{@link #UNKNOWN} (1) — the predicate touches NULL, so the result is unknown;</li>
 *   <li>{@link #TRUE} (2) — the predicate is provably true.</li>
 * </ul>
 *
 * <p>The numeric encoding makes the boolean connectives element-wise min/max, i.e. a single
 * branch-free array pass with no per-row short-circuiting:
 * <ul>
 *   <li>{@code a AND b} = min(a, b): {@code FALSE AND UNKNOWN = FALSE},
 *       {@code TRUE AND UNKNOWN = UNKNOWN};</li>
 *   <li>{@code a OR b} = max(a, b): {@code TRUE OR UNKNOWN = TRUE},
 *       {@code FALSE OR UNKNOWN = UNKNOWN};</li>
 *   <li>{@code NOT a} = {@code 2 - a}: {@code NOT UNKNOWN = UNKNOWN}.</li>
 * </ul>
 *
 * <p>Materialization treats only {@link #TRUE} as selected: unknown rows are excluded,
 * exactly as in a SQL {@code WHERE} clause.
 */
public final class SelectionVector {

    public static final byte FALSE = 0;
    public static final byte UNKNOWN = 1;
    public static final byte TRUE = 2;

    private final byte[] states;

    /** Takes ownership of {@code states} (not copied); callers must not mutate it afterwards. */
    public SelectionVector(byte[] states) {
        if (states == null) {
            throw new IllegalArgumentException("states must be non-null");
        }
        this.states = states;
    }

    public int size() {
        return states.length;
    }

    public byte state(int row) {
        return states[row];
    }

    /** A row is selected only when its state is TRUE; FALSE and UNKNOWN are both excluded. */
    public boolean isSelected(int row) {
        return states[row] == TRUE;
    }

    public int selectedCount() {
        int count = 0;
        for (byte state : states) {
            if (state == TRUE) {
                count++;
            }
        }
        return count;
    }

    /** Row indexes (in ascending order) whose state is TRUE; used as the materialization gather list. */
    public int[] toRowIndexes() {
        int count = selectedCount();
        int[] indexes = new int[count];
        int write = 0;
        for (int i = 0; i < states.length; i++) {
            if (states[i] == TRUE) {
                indexes[write++] = i;
            }
        }
        return indexes;
    }

    /** Three-valued AND: element-wise min. Both vectors must describe the same batch. */
    public SelectionVector and(SelectionVector other) {
        checkSameSize(other);
        byte[] out = new byte[states.length];
        for (int i = 0; i < states.length; i++) {
            out[i] = (byte) Math.min(states[i], other.states[i]);
        }
        return new SelectionVector(out);
    }

    /** Three-valued OR: element-wise max. Both vectors must describe the same batch. */
    public SelectionVector or(SelectionVector other) {
        checkSameSize(other);
        byte[] out = new byte[states.length];
        for (int i = 0; i < states.length; i++) {
            out[i] = (byte) Math.max(states[i], other.states[i]);
        }
        return new SelectionVector(out);
    }

    /** Three-valued NOT: TRUE&lt;-&gt;FALSE, UNKNOWN stays UNKNOWN. */
    public SelectionVector not() {
        byte[] out = new byte[states.length];
        for (int i = 0; i < states.length; i++) {
            out[i] = (byte) (TRUE - states[i]);
        }
        return new SelectionVector(out);
    }

    private void checkSameSize(SelectionVector other) {
        if (states.length != other.states.length) {
            throw new IllegalArgumentException(
                    "selection vectors must have the same length: " + states.length + " vs " + other.states.length);
        }
    }

    /** Zero-copy view of the backing state array. */
    public byte[] states() {
        return states;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SelectionVector that)) return false;
        return Arrays.equals(states, that.states);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(states);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("SelectionVector[");
        for (int i = 0; i < states.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(states[i] == TRUE ? 'T' : states[i] == UNKNOWN ? '?' : 'F');
        }
        return sb.append(']').toString();
    }
}
