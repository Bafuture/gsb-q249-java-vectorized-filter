package com.example.gsb.vectorized;

/**
 * Logical combination of predicates. Both the row-wise and the vectorized
 * path combine their children's results with the same SQL three-valued logic
 * (see {@link TriState}), so the two paths always agree.
 */
public final class LogicalPredicate implements FilterPredicate {

    public enum Op { AND, OR, NOT }

    private final Op op;
    private final FilterPredicate left;
    private final FilterPredicate right;

    private LogicalPredicate(Op op, FilterPredicate left, FilterPredicate right) {
        this.op = op;
        this.left = left;
        this.right = right;
    }

    public static LogicalPredicate and(FilterPredicate a, FilterPredicate b) {
        return new LogicalPredicate(Op.AND, a, b);
    }

    public static LogicalPredicate or(FilterPredicate a, FilterPredicate b) {
        return new LogicalPredicate(Op.OR, a, b);
    }

    public static LogicalPredicate not(FilterPredicate a) {
        return new LogicalPredicate(Op.NOT, a, null);
    }

    @Override
    public TriState evaluateRow(RowView row) {
        return switch (op) {
            case AND -> TriState.and(left.evaluateRow(row), right.evaluateRow(row));
            case OR -> TriState.or(left.evaluateRow(row), right.evaluateRow(row));
            case NOT -> TriState.not(left.evaluateRow(row));
        };
    }

    @Override
    public SelectionVector evaluateVector(ColumnBatch batch) {
        return switch (op) {
            case AND -> left.evaluateVector(batch).and(right.evaluateVector(batch));
            case OR -> left.evaluateVector(batch).or(right.evaluateVector(batch));
            case NOT -> left.evaluateVector(batch).not();
        };
    }

    @Override
    public String toString() {
        return switch (op) {
            case AND -> "(" + left + " AND " + right + ")";
            case OR -> "(" + left + " OR " + right + ")";
            case NOT -> "(NOT " + left + ")";
        };
    }
}
