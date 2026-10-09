import java.util.*;

// Walks the (already type-checked) AST and emits a complete, compilable Java class.
//
// Design decisions worth knowing:
//  * Top-level LET variables become private static FIELDS of the generated class and are assigned in
//    main(). That is what lets functions read global variables, exactly like the pseudocode scoping says.
//  * Java forbids a local variable from shadowing another local, but the pseudocode allows shadowing.
//    So a declaration that hides a visible name is emitted under a fresh name (x -> x_1).
//  * string == string must compare CONTENT, so it is emitted as a.equals(b) (Java's == compares references).
//  * Loop conditions that are constants (WHILE (false)) would make javac report unreachable code,
//    so they are wrapped in Boolean.valueOf(...).
//  * Parentheses are only emitted where the precedence rules need them, so the output reads like hand-written Java.
public class CodeGenerator {
    private static final Set<String> RESERVED = new HashSet<>(Arrays.asList(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
            "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
            "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
            "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp",
            "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void",
            "volatile", "while", "true", "false", "null", "var", "record", "yield",
            // names the generated code itself relies on
            "System", "String", "Integer", "Double", "Boolean", "Scanner", "Math", "Object",
            "main", "scanner", "toString", "hashCode", "equals", "getClass", "clone", "finalize",
            "notify", "notifyAll", "wait"));

    private final Deque<Map<String, String>> scopes = new ArrayDeque<>(); // innermost scope first
    private final Set<String> usedNames = new HashSet<>();
    private boolean needsScanner = false;

    public boolean usesInput() {
        return needsScanner;
    }

    public String generateProgram(Block program, String className, SymbolTable symbols) {
        for (String[] declared : symbols.history()) usedNames.add(mangle(declared[0]));
        for (SymbolTable.FunctionSignature f : symbols.functions()) usedNames.add(mangle(f.name));

        Map<String, String> globals = new LinkedHashMap<>();
        scopes.push(globals);
        List<AssignStmt> globalDecls = new ArrayList<>();
        for (ASTNode stmt : program.statements) {
            if (stmt instanceof AssignStmt a && a.declaration) {
                globalDecls.add(a);
                globals.put(a.identifier, mangle(a.identifier));
            }
        }

        StringBuilder functionsCode = new StringBuilder();
        StringBuilder mainCode = new StringBuilder();
        for (ASTNode stmt : program.statements) {
            if (stmt instanceof FunctionDecl func) {
                functionsCode.append(generateFunction(func));
            } else {
                emitStatement(stmt, 2, mainCode);
            }
        }

        // A program with no PRINT at all would show nothing, so show the final value of each
        // top-level variable (this keeps the simple Phase 1 programs producing visible output).
        if (!containsPrint(program)) {
            for (AssignStmt a : globalDecls) {
                mainCode.append(indent(2)).append("System.out.println(\"").append(a.identifier)
                        .append(" = \" + ").append(globals.get(a.identifier)).append(");\n");
            }
        }

        StringBuilder code = new StringBuilder();
        if (needsScanner) code.append("import java.util.Scanner;\n\n");
        code.append("public class ").append(className).append(" {\n");
        if (needsScanner) code.append("    private static Scanner scanner = new Scanner(System.in);\n");
        for (AssignStmt a : globalDecls) {
            code.append("    private static ").append(javaType(a.resolvedType)).append(' ')
                    .append(globals.get(a.identifier)).append(";\n");
        }
        if (needsScanner || !globalDecls.isEmpty()) code.append("\n");
        code.append(functionsCode);
        code.append("    public static void main(String[] args) {\n");
        code.append(mainCode);
        code.append("    }\n");
        code.append("}\n");
        return code.toString();
    }

    private String generateFunction(FunctionDecl func) {
        scopes.push(new HashMap<>());
        List<String> params = new ArrayList<>();
        for (FunctionDecl.Param p : func.parameters) {
            params.add(javaType(p.type) + " " + declareLocal(p.name));
        }
        StringBuilder sb = new StringBuilder();
        sb.append("    private static ").append(javaType(func.returnType)).append(' ').append(mangle(func.name))
                .append('(').append(String.join(", ", params)).append(") {\n");
        for (ASTNode stmt : ((Block) func.body).statements) {
            emitStatement(stmt, 2, sb);
        }
        sb.append("    }\n\n");
        scopes.pop();
        return sb.toString();
    }

    // ---- statements ----

    private void emitStatement(ASTNode node, int level, StringBuilder sb) {
        String ind = indent(level);
        if (node instanceof Block block) {
            sb.append(ind).append("{\n");
            emitScopedBody(block, level + 1, sb);
            sb.append(ind).append("}\n");
        } else if (node instanceof AssignStmt assign) {
            String rhs = expr(assign.expression); // evaluated BEFORE the new name is registered
            if (assign.declaration && scopes.size() > 1) {
                String javaName = declareLocal(assign.identifier);
                sb.append(ind).append(javaType(assign.resolvedType)).append(' ').append(javaName)
                        .append(" = ").append(rhs).append(";\n");
            } else {
                // a plain assignment, or a top-level LET (the variable is a static field)
                sb.append(ind).append(resolve(assign.identifier)).append(" = ").append(rhs).append(";\n");
            }
        } else if (node instanceof IfStmt ifStmt) {
            emitIf(ifStmt, level, sb, false);
        } else if (node instanceof WhileStmt whileStmt) {
            sb.append(ind).append("while (").append(loopCondition(whileStmt.condition)).append(") {\n");
            emitScopedBody((Block) whileStmt.body, level + 1, sb);
            sb.append(ind).append("}\n");
        } else if (node instanceof ForStmt forStmt) {
            scopes.push(new HashMap<>());
            AssignStmt init = (AssignStmt) forStmt.init;
            String initRhs = expr(init.expression);
            String initCode = init.declaration
                    ? javaType(init.resolvedType) + " " + declareLocal(init.identifier) + " = " + initRhs
                    : resolve(init.identifier) + " = " + initRhs;
            AssignStmt update = (AssignStmt) forStmt.update;
            sb.append(ind).append("for (").append(initCode).append("; ")
                    .append(loopCondition(forStmt.condition)).append("; ")
                    .append(resolve(update.identifier)).append(" = ").append(expr(update.expression))
                    .append(") {\n");
            emitScopedBody((Block) forStmt.body, level + 1, sb);
            sb.append(ind).append("}\n");
            scopes.pop();
        } else if (node instanceof ReturnStmt ret) {
            sb.append(ind).append("return");
            if (ret.value != null) sb.append(' ').append(expr(ret.value));
            sb.append(";\n");
        } else if (node instanceof PrintStmt print) {
            sb.append(ind).append("System.out.println(").append(expr(print.expression)).append(");\n");
        } else if (node instanceof InputStmt input) {
            needsScanner = true;
            String read;
            switch (input.resolvedType) {
                case "int": read = "Integer.parseInt(scanner.nextLine().trim())"; break;
                case "float": read = "Double.parseDouble(scanner.nextLine().trim())"; break;
                case "boolean": read = "Boolean.parseBoolean(scanner.nextLine().trim())"; break;
                default: read = "scanner.nextLine()"; break;
            }
            sb.append(ind).append(resolve(input.identifier)).append(" = ").append(read).append(";\n");
        } else if (node instanceof CallExpr call) {
            sb.append(ind).append(expr(call)).append(";\n");
        }
    }

    private void emitIf(IfStmt s, int level, StringBuilder sb, boolean chained) {
        String ind = indent(level);
        if (!chained) sb.append(ind);
        sb.append("if (").append(expr(s.condition)).append(") {\n");
        emitScopedBody((Block) s.thenBlock, level + 1, sb);
        sb.append(ind).append("}");
        if (s.elseBlock == null) {
            sb.append("\n");
        } else if (s.elseBlock instanceof IfStmt elseIf) {
            sb.append(" else ");
            emitIf(elseIf, level, sb, true);
        } else {
            sb.append(" else {\n");
            emitScopedBody((Block) s.elseBlock, level + 1, sb);
            sb.append(ind).append("}\n");
        }
    }

    private void emitScopedBody(Block block, int level, StringBuilder sb) {
        scopes.push(new HashMap<>());
        for (ASTNode stmt : block.statements) emitStatement(stmt, level, sb);
        scopes.pop();
    }

    // ---- expressions ----
    // Java precedence levels: OR 1, AND 2, equality 3, relational 4, additive 5, multiplicative 6, unary 7, primary 9

    private String expr(ASTNode node) {
        if (node instanceof IntegerLiteral lit) return String.valueOf(lit.value);
        if (node instanceof FloatLiteral lit) return String.valueOf(lit.value);
        if (node instanceof StringLiteral lit) return quote(lit.value);
        if (node instanceof BooleanLiteral lit) return String.valueOf(lit.value);
        if (node instanceof IdentifierExpr id) return resolve(id.name);
        if (node instanceof BinaryExpr b) {
            int p = prec(b);
            return operand(b.left, p, false) + " " + b.operator + " " + operand(b.right, p, true);
        }
        if (node instanceof RelationalExpr r) {
            if (isStringEquality(r)) {
                String call = operand(r.left, 9, false) + ".equals(" + expr(r.right) + ")";
                return r.operator.equals("!=") ? "!" + call : call;
            }
            int p = prec(r);
            return operand(r.left, p, false) + " " + r.operator + " " + operand(r.right, p, true);
        }
        if (node instanceof LogicalExpr l) {
            String op = l.operator.equals("AND") ? "&&" : "||";
            int p = prec(l);
            return operand(l.left, p, false) + " " + op + " " + operand(l.right, p, true);
        }
        if (node instanceof UnaryExpr u) {
            String inner = expr(u.expr);
            boolean paren = prec(u.expr) < 7 || u.expr instanceof UnaryExpr;
            return u.operator + (paren ? "(" + inner + ")" : inner);
        }
        if (node instanceof CallExpr c) {
            List<String> args = new ArrayList<>();
            for (ASTNode a : c.arguments) args.add(expr(a));
            return mangle(c.functionName) + "(" + String.join(", ", args) + ")";
        }
        throw new IllegalStateException("CodeGenerator does not know node type " + node.getClass().getSimpleName());
    }

    private boolean isStringEquality(RelationalExpr r) {
        boolean eq = r.operator.equals("==") || r.operator.equals("!=");
        return eq && "string".equals(r.left.resolvedType);
    }

    private int prec(ASTNode n) {
        if (n instanceof LogicalExpr l) return l.operator.equals("OR") ? 1 : 2;
        if (n instanceof RelationalExpr r) {
            if (isStringEquality(r)) return r.operator.equals("!=") ? 7 : 9;
            boolean eq = r.operator.equals("==") || r.operator.equals("!=");
            return eq ? 3 : 4;
        }
        if (n instanceof BinaryExpr b) return (b.operator.equals("+") || b.operator.equals("-")) ? 5 : 6;
        if (n instanceof UnaryExpr) return 7;
        return 9;
    }

    // wrap a child in parentheses only if its precedence requires it (all binary operators are left-associative)
    private String operand(ASTNode child, int parentPrec, boolean isRight) {
        String s = expr(child);
        int p = prec(child);
        boolean needParens = p < parentPrec || (isRight && p == parentPrec);
        return needParens ? "(" + s + ")" : s;
    }

    private String loopCondition(ASTNode cond) {
        String s = expr(cond);
        return isConstant(cond) ? "Boolean.valueOf(" + s + ")" : s;
    }

    private boolean isConstant(ASTNode n) {
        if (n instanceof IdentifierExpr || n instanceof CallExpr) return false;
        if (n instanceof BinaryExpr b) return isConstant(b.left) && isConstant(b.right);
        if (n instanceof RelationalExpr r) return isConstant(r.left) && isConstant(r.right);
        if (n instanceof LogicalExpr l) return isConstant(l.left) && isConstant(l.right);
        if (n instanceof UnaryExpr u) return isConstant(u.expr);
        return true;
    }

    // ---- names and scopes ----

    private String declareLocal(String name) {
        String candidate = mangle(name);
        if (isVisible(name)) { // would shadow something: pick a fresh Java name
            int k = 1;
            String base = candidate;
            do {
                candidate = base + "_" + k++;
            } while (usedNames.contains(candidate));
        }
        usedNames.add(candidate);
        scopes.peek().put(name, candidate);
        return candidate;
    }

    private boolean isVisible(String name) {
        for (Map<String, String> scope : scopes) if (scope.containsKey(name)) return true;
        return false;
    }

    private String resolve(String name) {
        for (Map<String, String> scope : scopes) {
            if (scope.containsKey(name)) return scope.get(name);
        }
        return mangle(name);
    }

    private String mangle(String name) {
        return RESERVED.contains(name) ? name + "_" : name;
    }

    private boolean containsPrint(ASTNode node) {
        if (node instanceof PrintStmt) return true;
        if (node instanceof Block b) { for (ASTNode s : b.statements) if (containsPrint(s)) return true; return false; }
        if (node instanceof IfStmt i) return containsPrint(i.thenBlock) || (i.elseBlock != null && containsPrint(i.elseBlock));
        if (node instanceof WhileStmt w) return containsPrint(w.body);
        if (node instanceof ForStmt f) return containsPrint(f.body);
        if (node instanceof FunctionDecl fn) return containsPrint(fn.body);
        return false;
    }

    private String javaType(String type) {
        switch (type) {
            case "int": return "int";
            case "float": return "double";
            case "boolean": return "boolean";
            case "string": return "String";
            default: return "void";
        }
    }

    private String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\t': sb.append("\\t"); break;
                case '\r': sb.append("\\r"); break;
                default:
                    if (c < 32 || c > 126) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    private String indent(int level) {
        return "    ".repeat(level);
    }
}
