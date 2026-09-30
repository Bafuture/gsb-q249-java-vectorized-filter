package com.example.gsb.vectorized;

/**
 * Supported physical column types for columnar storage.
 */
public enum ColumnType {
    /** Fixed-width 64-bit signed integer. */
    LONG,
    /** Fixed-width 64-bit IEEE-754 floating point. */
    DOUBLE,
    /** Variable-width string column. */
    STRING
}
