// Covers both "LET x : int = 5;" (declaration) and "x = 5;" (plain assignment).
public class AssignStmt extends ASTNode {
    public final String identifier;
    public final String declaredType; // may be null for "LET x = 5;" - the type is then inferred
    public final ASTNode expression;
    public final boolean declaration; // true for LET, false for plain assignment

    public AssignStmt(String identifier, String declaredType, ASTNode expression, boolean declaration) {
        this.identifier = identifier;
        this.declaredType = declaredType;
        this.expression = expression;
        this.declaration = declaration;
    }
}
