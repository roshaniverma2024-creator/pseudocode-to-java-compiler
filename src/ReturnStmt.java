public class ReturnStmt extends ASTNode {
    public final ASTNode value; // Can be null for void returns

    public ReturnStmt(ASTNode value) {
        this.value = value;
    }
}