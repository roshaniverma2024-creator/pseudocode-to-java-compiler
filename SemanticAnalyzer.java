public class SemanticAnalyzer {
    private final SymbolTable symbolTable;

    public SemanticAnalyzer(SymbolTable symbolTable) {
        this.symbolTable = symbolTable;
    }

    public void analyze(AssignStmt stmt) {
        checkExpr(stmt.expression);       // right-hand side must only reference declared names
        symbolTable.declare(stmt.identifier); // LET declares the identifier
    }

    private void checkExpr(ASTNode node) {
        if (node instanceof IdentifierExpr id) {
            if (!symbolTable.isDeclared(id.name)) {
                throw new RuntimeException("Semantic error: identifier '" + id.name + "' used before declaration");
            }
        } else if (node instanceof BinaryExpr bin) {
            checkExpr(bin.left);
            checkExpr(bin.right);
        }
        // IntegerLiteral needs no check
    }
}
