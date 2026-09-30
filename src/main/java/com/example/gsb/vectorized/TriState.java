package com.example.gsb.vectorized;

/**
 * Three-valued-logic result of a predicate evaluation on a row.
 *
 * <p>This implements SQL-style three-valued logic (3VL) used when data may be
 * {@code null}:
 * <ul>
 *   <li>{@link #TRUE} / {@link #FALSE}: the predicate evaluated to a definite result.</li>
 *   <li>{@link #UNKNOWN}: at least one operand was null, so the result is unknown.</li>
 * </ul>
 *
 * <p>Combination rules:
 * <pre>{@code
 * AND: UNKNOWN AND TRUE = UNKNOWN ; UNKNOWN AND FALSE = FALSE
 * OR : UNKNOWN OR  FALSE = UNKNOWN ; UNKNOWN OR  TRUE = TRUE
 * NOT: NOT UNKNOWN = UNKNOWN
 * }</pre>
 * Only {@link #TRUE} rows survive materialization (SQL WHERE semantics);
 * {@code FALSE} and {@code UNKNOWN} rows are both dropped.
 */
public enum TriState {
    TRUE,
    FALSE,
    UNKNOWN;

    public boolean isTrue() {
        return this == TRUE;
    }

    /** SQL three-valued AND. */
    public static TriState and(TriState a, TriState b) {
        if (a == FALSE || b == FALSE) {
            return FALSE;
        }
        if (a == TRUE && b == TRUE) {
            return TRUE;
        }
        return UNKNOWN;
    }

    /** SQL three-valued OR. */
    public static TriState or(TriState a, TriState b) {
        if (a == TRUE || b == TRUE) {
            return TRUE;
        }
        if (a == FALSE && b == FALSE) {
            return FALSE;
        }
        return UNKNOWN;
    }

    /** SQL three-valued NOT. */
    public static TriState not(TriState a) {
        return switch (a) {
            case TRUE -> FALSE;
            case FALSE -> TRUE;
            case UNKNOWN -> UNKNOWN;
        };
    }
}
