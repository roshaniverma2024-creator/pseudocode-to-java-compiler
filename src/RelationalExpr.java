public class RelationalExpr extends ASTNode {
    public final ASTNode left;
    public final String operator; // "<", ">", "<=", ">=", "==", "!="
    public final ASTNode right;

    public RelationalExpr(ASTNode left, String operator, ASTNode right) {
        this.left = left;
        this.operator = operator;
        this.right = right;
    }
}