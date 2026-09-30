package com.example.gsb.vectorized;

/**
 * Bit-packed selection vector produced by evaluating a predicate over a
 * {@link ColumnVector batch}.
 *
 * <p>Two bits are tracked per row position to support SQL three-valued logic:
 * <ul>
 *   <li>{@code match[i]}: the predicate is true at position {@code i};</li>
 *   <li>{@code known[i]}: the predicate result is definite (TRUE or FALSE) at
 *       position {@code i}. When {@code known[i] == 0} the result is UNKNOWN,
 *       which happens when the evaluated value is null.</li>
 * </ul>
 *
 * <p>Decoding:
 * <pre>{@code
 * match=1, known=1 -> TRUE
 * match=0, known=1 -> FALSE
 * known=0          -> UNKNOWN (match bit is always 0)
 * }</pre>
 *
 * <p>All merge operations ({@link #and}, {@link #or}, {@link #not}) implement
 * the 3VL truth tables documented on {@link TriState} using purely bitwise
 * word operations, so whole vectors are combined without per-row branches.
 * {@link #get(int)} converts back to a {@link TriState} and is mainly useful
 * for the row-wise reference implementation and for tests.
 *
 * <p>Instances are immutable with respect to merge operations: every
 * combinator allocates and returns a new vector. Vectors of different lengths
 * cannot be merged.
 */
public final class SelectionVector {

    private final int length;
    private final long[] match;
    private final long[] known;

    private SelectionVector(int length, long[] match, long[] known) {
        this.length = length;
        this.match = match;
        this.known = known;
    }

    /** Creates an all-{@code FALSE} (every position definite) vector of the given length. */
    public static SelectionVector allFalse(int length) {
        int words = words(length);
        return new SelectionVector(length, new long[words], ones(words, length));
    }

    /** Creates an all-{@code UNKNOWN} vector of the given length. */
    public static SelectionVector allUnknown(int length) {
        return new SelectionVector(length, new long[words(length)], new long[words(length)]);
    }

    /**
     * Package-private construction from raw bit words. {@code match} and
     * {@code known} must have exactly {@code words(length)} entries; tail bits
     * beyond {@code length} are cleared.
     */
    static SelectionVector fromBits(int length, long[] match, long[] known) {
        SelectionVector v = new SelectionVector(length, match, known);
        v.cleanTail(match);
        v.cleanTail(known);
        return v;
    }

    private static int words(int length) {
        return (length + 63) >>> 6;
    }

    private static long[] ones(int words, int length) {
        long[] bits = new long[words];
        if (words == 0) {
            return bits;
        }
        java.util.Arrays.fill(bits, -1L);
        int rem = length & 63;
        if (rem != 0) {
            bits[words - 1] &= (1L << rem) - 1L;
        }
        return bits;
    }

    public int length() {
        return length;
    }

    /** Returns the three-valued result at {@code index}. */
    public TriState get(int index) {
        checkIndex(index);
        int word = index >>> 6;
        long mask = 1L << (index & 63);
        if ((known[word] & mask) == 0) {
            return TriState.UNKNOWN;
        }
        return (match[word] & mask) != 0 ? TriState.TRUE : TriState.FALSE;
    }

    /** Returns {@code true} iff the predicate is definitely true at {@code index}. */
    public boolean isTrue(int index) {
        checkIndex(index);
        int word = index >>> 6;
        long mask = 1L << (index & 63);
        return (known[word] & mask) != 0 && (match[word] & mask) != 0;
    }

    /** Number of positions whose result is definitely TRUE. */
    public int trueCount() {
        int count = 0;
        for (long w : match) {
            count += Long.bitCount(w);
        }
        return count;
    }

    /**
     * Three-valued AND of two vectors.
     *
     * <p>Per-row truth table (word level):
     * <pre>{@code
     * known = (knownA & knownB) | (knownA & ~matchA) | (knownB & ~matchB)
     * match = matchA & matchB
     * }</pre>
     * Rationale: a definite FALSE on either side forces FALSE even if the other
     * side is UNKNOWN ({@code known & ~match} detects a definite FALSE);
     * otherwise the row stays UNKNOWN while any side is UNKNOWN.
     */
    public SelectionVector and(SelectionVector other) {
        checkSameLength(other);
        long[] m = new long[match.length];
        long[] k = new long[known.length];
        for (int i = 0; i < m.length; i++) {
            long ma = match[i];
            long mb = other.match[i];
            long ka = known[i];
            long kb = other.known[i];
            m[i] = ma & mb;
            k[i] = (ka & kb) | (ka & ~ma) | (kb & ~mb);
        }
        return new SelectionVector(length, m, cleanTail(k));
    }

    /**
     * Three-valued OR of two vectors.
     *
     * <p>Per-row truth table (word level):
     * <pre>{@code
     * known = (knownA & knownB) | (knownA & matchA) | (knownB & matchB)
     * match = matchA | matchB
     * }</pre>
     * Rationale: a definite TRUE on either side forces TRUE even if the other
     * side is UNKNOWN ({@code known & match} detects a definite TRUE);
     * otherwise the row stays UNKNOWN while any side is UNKNOWN.
     */
    public SelectionVector or(SelectionVector other) {
        checkSameLength(other);
        long[] m = new long[match.length];
        long[] k = new long[known.length];
        for (int i = 0; i < m.length; i++) {
            long ma = match[i];
            long mb = other.match[i];
            long ka = known[i];
            long kb = other.known[i];
            m[i] = ma | mb;
            k[i] = (ka & kb) | (ka & ma) | (kb & mb);
        }
        return new SelectionVector(length, m, cleanTail(k));
    }

    /**
     * Three-valued NOT. UNKNOWN stays UNKNOWN; TRUE and FALSE swap.
     *
     * <pre>{@code
     * match = ~match & known
     * known = known
     * }</pre>
     */
    public SelectionVector not() {
        long[] m = new long[match.length];
        for (int i = 0; i < m.length; i++) {
            m[i] = ~match[i] & known[i];
        }
        long[] k = known.clone();
        return new SelectionVector(length, m, k);
    }

    /**
     * Concatenates vectors coming from consecutive batches into one vector
     * covering all batches in order. Used to evaluate a predicate across batch
     * boundaries without losing or duplicating rows: every batch contributes
     * exactly its own {@code size} rows, in batch order.
     */
    public static SelectionVector concat(java.util.List<SelectionVector> parts) {
        int total = 0;
        for (SelectionVector part : parts) {
            total += part.length();
        }
        long[] m = new long[words(total)];
        long[] k = new long[words(total)];
        int offset = 0;
        for (SelectionVector part : parts) {
            copyShifted(part.match, part.length, m, offset);
            copyShifted(part.known, part.length, k, offset);
            offset += part.length();
        }
        if (total != 0) {
            int rem = total & 63;
            if (rem != 0) {
                m[m.length - 1] &= (1L << rem) - 1L;
                k[k.length - 1] &= (1L << rem) - 1L;
            }
        }
        return new SelectionVector(total, m, k);
    }

    private static void copyShifted(long[] src, int srcLen, long[] dst, int offset) {
        if (srcLen == 0) {
            return;
        }
        int wordShift = offset >>> 6;
        int bitShift = offset & 63;
        if (bitShift == 0) {
            System.arraycopy(src, 0, dst, wordShift, src.length);
            return;
        }
        for (int i = 0; i < src.length; i++) {
            long w = src[i];
            int target = wordShift + i;
            dst[target] |= w << bitShift;
            if (bitShift != 0 && target + 1 < dst.length) {
                dst[target + 1] |= w >>> (64 - bitShift);
            }
        }
    }

    /** Iterates the positions whose result is definitely TRUE in ascending order. */
    public java.util.PrimitiveIterator.OfInt truePositions() {
        return new java.util.PrimitiveIterator.OfInt() {
            private int wordIndex;
            private long pending = match.length == 0 ? 0L : match[0];
            private int base;

            {
                advanceToPending();
            }

            private void advanceToPending() {
                while (pending == 0L) {
                    wordIndex++;
                    if (wordIndex >= match.length) {
                        return;
                    }
                    pending = match[wordIndex];
                    base = wordIndex << 6;
                }
            }

            @Override
            public boolean hasNext() {
                return wordIndex < match.length && pending != 0L;
            }

            @Override
            public int nextInt() {
                if (!hasNext()) {
                    throw new java.util.NoSuchElementException();
                }
                int pos = base + Long.numberOfTrailingZeros(pending);
                pending &= pending - 1L;
                advanceToPending();
                return pos;
            }
        };
    }

    private long[] cleanTail(long[] bits) {
        if (bits.length != 0) {
            int rem = length & 63;
            if (rem != 0) {
                bits[bits.length - 1] &= (1L << rem) - 1L;
            }
        }
        return bits;
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= length) {
            throw new IndexOutOfBoundsException("Index " + index + " out of bounds for length " + length);
        }
    }

    private void checkSameLength(SelectionVector other) {
        if (length != other.length) {
            throw new IllegalArgumentException(
                    "Selection vector lengths differ: " + length + " vs " + other.length);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SelectionVector that)) {
            return false;
        }
        return length == that.length
                && java.util.Arrays.equals(match, that.match)
                && java.util.Arrays.equals(known, that.known);
    }

    @Override
    public int hashCode() {
        return 31 * length + java.util.Arrays.hashCode(match);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(switch (get(i)) {
                case TRUE -> '1';
                case FALSE -> '0';
                case UNKNOWN -> '?';
            });
        }
        return sb.toString();
    }
}
