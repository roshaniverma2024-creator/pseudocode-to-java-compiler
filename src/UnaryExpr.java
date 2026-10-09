public class UnaryExpr extends ASTNode {
    public final String operator; // e.g., "-"
    public final ASTNode expr;

    public UnaryExpr(String operator, ASTNode expr) {
        this.operator = operator;
        this.expr = expr;
    }
}