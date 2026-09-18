public class AssignStmt implements ASTNode {
    public final String identifier;
    public final ASTNode expression;

    public AssignStmt(String identifier, ASTNode expression) {
        this.identifier = identifier;
        this.expression = expression;
    }
}
