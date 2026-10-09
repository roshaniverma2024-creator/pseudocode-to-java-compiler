public class IfStmt extends ASTNode {
    public final ASTNode condition;
    public final ASTNode thenBlock;
    public final ASTNode elseBlock; // Can be null if no ELSE branch exists

    public IfStmt(ASTNode condition, ASTNode thenBlock, ASTNode elseBlock) {
        this.condition = condition;
        this.thenBlock = thenBlock;
        this.elseBlock = elseBlock;
    }
}