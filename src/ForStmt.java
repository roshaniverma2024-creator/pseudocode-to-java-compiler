public class ForStmt extends ASTNode {
    public final ASTNode init;      // e.g., LET i : int = 0;
    public final ASTNode condition; // e.g., i < 10
    public final ASTNode update;    // e.g., i = i + 1
    public final ASTNode body;

    public ForStmt(ASTNode init, ASTNode condition, ASTNode update, ASTNode body) {
        this.init = init;
        this.condition = condition;
        this.update = update;
        this.body = body;
    }
}