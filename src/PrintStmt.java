public class PrintStmt extends ASTNode {
    public final ASTNode expression;

    public PrintStmt(ASTNode expression) {
        this.expression = expression;
    }
}