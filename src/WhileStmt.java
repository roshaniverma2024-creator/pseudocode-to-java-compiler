public class WhileStmt extends ASTNode {
    public final ASTNode condition;
    public final ASTNode body;

    public WhileStmt(ASTNode condition, ASTNode body) {
        this.condition = condition;
        this.body = body;
    }
}