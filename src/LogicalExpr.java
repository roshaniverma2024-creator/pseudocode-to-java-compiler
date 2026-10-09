public class LogicalExpr extends ASTNode {
    public final ASTNode left;
    public final String operator; // "AND" or "OR"
    public final ASTNode right;

    public LogicalExpr(ASTNode left, String operator, ASTNode right) {
        this.left = left;
        this.operator = operator;
        this.right = right;
    }
}