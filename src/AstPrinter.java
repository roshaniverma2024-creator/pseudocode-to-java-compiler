import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

// Draws the AST as a real tree with ASCII branches, e.g.
//   [2] FunctionDecl factorial(k:int) : int
//       +-- Block
//           +-- IfStmt
//               |-- condition: RelationalExpr (<=)
public class AstPrinter {

    private static class Child {
        final String role;
        final ASTNode node;

        Child(String role, ASTNode node) {
            this.role = role;
            this.node = node;
        }
    }

    public static void print(Block program, PrintStream out) {
        int i = 1;
        for (ASTNode stmt : program.statements) {
            out.println("  [" + i++ + "] " + label(stmt));
            printChildren(stmt, "      ", out);
        }
    }

    private static void printChildren(ASTNode node, String prefix, PrintStream out) {
        List<Child> children = children(node);
        for (int i = 0; i < children.size(); i++) {
            boolean last = i == children.size() - 1;
            Child c = children.get(i);
            String role = c.role.isEmpty() ? "" : c.role + ": ";
            out.println(prefix + (last ? "+-- " : "|-- ") + role + label(c.node));
            printChildren(c.node, prefix + (last ? "    " : "|   "), out);
        }
    }

    private static String label(ASTNode n) {
        if (n instanceof AssignStmt a) {
            if (!a.declaration) return "Assign " + a.identifier;
            return "LET " + a.identifier + (a.declaredType != null ? " : " + a.declaredType : "");
        }
        if (n instanceof IntegerLiteral l) return "IntegerLiteral " + l.value;
        if (n instanceof FloatLiteral l) return "FloatLiteral " + l.value;
        if (n instanceof StringLiteral l) return "StringLiteral \"" + l.value.replace("\n", "\\n").replace("\t", "\\t") + "\"";
        if (n instanceof BooleanLiteral l) return "BooleanLiteral " + l.value;
        if (n instanceof IdentifierExpr id) return "Identifier " + id.name;
        if (n instanceof BinaryExpr b) return "BinaryExpr (" + b.operator + ")";
        if (n instanceof RelationalExpr r) return "RelationalExpr (" + r.operator + ")";
        if (n instanceof LogicalExpr l) return "LogicalExpr (" + l.operator + ")";
        if (n instanceof UnaryExpr u) return "UnaryExpr (" + u.operator + ")";
        if (n instanceof CallExpr c) return "CallExpr " + c.functionName + "()";
        if (n instanceof IfStmt) return "IfStmt";
        if (n instanceof WhileStmt) return "WhileStmt";
        if (n instanceof ForStmt) return "ForStmt";
        if (n instanceof Block) return "Block";
        if (n instanceof ReturnStmt) return "ReturnStmt";
        if (n instanceof PrintStmt) return "PrintStmt";
        if (n instanceof InputStmt in) return "InputStmt " + in.identifier;
        if (n instanceof FunctionDecl f) {
            List<String> ps = new ArrayList<>();
            for (FunctionDecl.Param p : f.parameters) ps.add(p.name + ":" + p.type);
            return "FunctionDecl " + f.name + "(" + String.join(", ", ps) + ") : " + f.returnType;
        }
        return n.getClass().getSimpleName();
    }

    private static List<Child> children(ASTNode n) {
        List<Child> list = new ArrayList<>();
        if (n instanceof AssignStmt a) list.add(new Child("", a.expression));
        else if (n instanceof BinaryExpr b) { list.add(new Child("", b.left)); list.add(new Child("", b.right)); }
        else if (n instanceof RelationalExpr r) { list.add(new Child("", r.left)); list.add(new Child("", r.right)); }
        else if (n instanceof LogicalExpr l) { list.add(new Child("", l.left)); list.add(new Child("", l.right)); }
        else if (n instanceof UnaryExpr u) list.add(new Child("", u.expr));
        else if (n instanceof CallExpr c) { int i = 1; for (ASTNode a : c.arguments) list.add(new Child("arg" + i++, a)); }
        else if (n instanceof IfStmt s) {
            list.add(new Child("condition", s.condition));
            list.add(new Child("then", s.thenBlock));
            if (s.elseBlock != null) list.add(new Child("else", s.elseBlock));
        } else if (n instanceof WhileStmt w) {
            list.add(new Child("condition", w.condition));
            list.add(new Child("body", w.body));
        } else if (n instanceof ForStmt f) {
            list.add(new Child("init", f.init));
            list.add(new Child("condition", f.condition));
            list.add(new Child("update", f.update));
            list.add(new Child("body", f.body));
        } else if (n instanceof Block b) { for (ASTNode s : b.statements) list.add(new Child("", s)); }
        else if (n instanceof FunctionDecl f) list.add(new Child("", f.body));
        else if (n instanceof ReturnStmt r && r.value != null) list.add(new Child("", r.value));
        else if (n instanceof PrintStmt p) list.add(new Child("", p.expression));
        return list;
    }
}
