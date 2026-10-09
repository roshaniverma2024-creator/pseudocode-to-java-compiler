// Base class for every node in the Abstract Syntax Tree.
// Carries the source position (set by the Parser) so semantic errors can point at the exact
// line/column, and the resolved type (set by the SemanticAnalyzer) so the CodeGenerator
// doesn't have to re-infer types.
public abstract class ASTNode {
    public int line;
    public int column;
    public String resolvedType; // "int" | "float" | "boolean" | "string" | "void"

    @SuppressWarnings("unchecked")
    public <T extends ASTNode> T at(Token t) {
        this.line = t.line;
        this.column = t.column;
        return (T) this;
    }
}
