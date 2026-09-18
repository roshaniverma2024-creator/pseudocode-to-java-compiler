import java.util.List;

public class CodeGenerator {

    public String generate(AssignStmt stmt) {
        return "int " + stmt.identifier + " = " + generateExpr(stmt.expression) + ";";
    }

    // Wraps a full list of statements into a compilable, runnable Java class,
    // printing every declared variable at the end so the program actually
    // produces visible output when executed.
    public String generateProgram(String className, List<AssignStmt> statements) {
        StringBuilder sb = new StringBuilder();
        sb.append("public class ").append(className).append(" {\n");
        sb.append("    public static void main(String[] args) {\n");
        for (AssignStmt stmt : statements) {
            sb.append("        ").append(generate(stmt)).append("\n");
        }
        sb.append("\n");
        for (AssignStmt stmt : statements) {
            sb.append("        System.out.println(\"")
              .append(stmt.identifier).append(" = \" + ").append(stmt.identifier).append(");\n");
        }
        sb.append("    }\n");
        sb.append("}\n");
        return sb.toString();
    }

    private String generateExpr(ASTNode node) {
        if (node instanceof IntegerLiteral lit) {
            return lit.value;
        }
        if (node instanceof IdentifierExpr id) {
            return id.name;
        }
        if (node instanceof BinaryExpr bin) {
            return generateExpr(bin.left) + " " + bin.operator + " " + generateExpr(bin.right);
        }
        throw new RuntimeException("Code generation error: unknown AST node " + node);
    }
}
