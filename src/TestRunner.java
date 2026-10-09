import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// Dependency-free test runner (needs only a JDK).
//   java TestRunner [testsFolder]        (default folder: ../tests)
// Part 1: unit tests for the Lexer, Parser, SymbolTable, SemanticAnalyzer and CodeGenerator.
// Part 2: end-to-end tests - every tests/<category>/<name>.txt is compiled, and
//           <name>.expected  -> the program must print exactly this
//           <name>.error     -> compilation must fail:  Stage|line|text that must appear in the message
//           <name>.runtime   -> the program must crash at run time with this text in stderr
//           <name>.in        -> (optional) text fed to the program's standard input
public class TestRunner {
    private static int passed = 0;
    private static int failed = 0;
    private static final List<String> failures = new ArrayList<>();
    private static final Map<String, int[]> perCategory = new LinkedHashMap<>(); // {passed, total}

    public static void main(String[] args) throws Exception {
        Path testsDir = Path.of(args.length > 0 ? args[0] : "../tests");

        System.out.println("=== PART 1: UNIT TESTS ===");
        int before = passed + failed;
        unitTests();
        int unitTotal = passed + failed - before;
        System.out.println("unit tests run: " + unitTotal);

        System.out.println();
        System.out.println("=== PART 2: END-TO-END TESTS (" + testsDir + ") ===");
        if (Files.isDirectory(testsDir)) {
            endToEndTests(testsDir);
        } else {
            System.out.println("tests folder not found: " + testsDir);
        }

        System.out.println();
        System.out.println("=== SUMMARY ===");
        System.out.printf("  %-18s %s%n", "unit tests", unitTotal);
        for (Map.Entry<String, int[]> e : perCategory.entrySet()) {
            System.out.printf("  %-18s %d / %d passed%n", e.getKey(), e.getValue()[0], e.getValue()[1]);
        }
        System.out.println("  ------------------------------------");
        System.out.printf("  TOTAL: %d passed, %d failed%n", passed, failed);
        if (!failures.isEmpty()) {
            System.out.println();
            System.out.println("FAILURES:");
            for (String f : failures) System.out.println("  - " + f);
        }
        System.exit(failed == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------------ helpers

    private static void check(String name, boolean ok, String detail) {
        if (ok) {
            passed++;
        } else {
            failed++;
            failures.add(name + (detail == null ? "" : "  [" + detail + "]"));
            System.out.println("  FAIL  " + name + (detail == null ? "" : "  [" + detail + "]"));
        }
    }

    private static void check(String name, boolean ok) {
        check(name, ok, null);
    }

    private static List<Token> lex(String src) {
        return new Lexer(src).tokenize();
    }

    private static Block parse(String src) {
        return new Parser(lex(src)).parseProgram();
    }

    private static SymbolTable analyze(Block program) {
        SymbolTable st = new SymbolTable();
        new SemanticAnalyzer(st).analyze(program);
        return st;
    }

    private static String generate(String src) {
        Block p = parse(src);
        SymbolTable st = analyze(p);
        return new CodeGenerator().generateProgram(p, "T", st);
    }

    private static CompileError errorOf(String src) {
        try {
            analyze(parse(src));
        } catch (CompileError e) {
            return e;
        }
        return null;
    }

    private static List<TokenType> types(String src) {
        return lex(src).stream().map(t -> t.type).collect(Collectors.toList());
    }

    // ------------------------------------------------------------------ unit tests

    private static void unitTests() {
        // ---- Lexer ----
        check("lexer: statement keywords", types("LET IF ELSE WHILE FOR FUNCTION RETURN PRINT INPUT AND OR").equals(List.of(
                TokenType.LET, TokenType.IF, TokenType.ELSE, TokenType.WHILE, TokenType.FOR, TokenType.FUNCTION,
                TokenType.RETURN, TokenType.PRINT, TokenType.INPUT, TokenType.AND, TokenType.OR, TokenType.EOF)));
        check("lexer: type names and booleans", types("int float boolean string true false").equals(List.of(
                TokenType.TYPE_INT, TokenType.TYPE_FLOAT, TokenType.TYPE_BOOLEAN, TokenType.TYPE_STRING,
                TokenType.BOOLEAN_LITERAL, TokenType.BOOLEAN_LITERAL, TokenType.EOF)));
        check("lexer: integer and float literals", types("42 3.14").equals(
                List.of(TokenType.INTEGER_LITERAL, TokenType.FLOAT_LITERAL, TokenType.EOF)));
        check("lexer: two-character operators", types("== != <= >= < > =").equals(List.of(
                TokenType.EQUAL, TokenType.NOT_EQUAL, TokenType.LESS_EQUAL, TokenType.GREATER_EQUAL,
                TokenType.LESS_THAN, TokenType.GREATER_THAN, TokenType.ASSIGN, TokenType.EOF)));
        check("lexer: string escapes are decoded", lex("\"a\\\"b\\n\"").get(0).text.equals("a\"b\n"));
        check("lexer: comments are skipped", types("1 // note\n2").equals(
                List.of(TokenType.INTEGER_LITERAL, TokenType.INTEGER_LITERAL, TokenType.EOF)));
        Token eq = lex("LET x\n  = 5;").get(2);
        check("lexer: line and column tracking", eq.type == TokenType.ASSIGN && eq.line == 2 && eq.column == 3,
                eq.line + ":" + eq.column);
        try {
            lex("a @ b");
            check("lexer: unexpected character is rejected", false);
        } catch (CompileError e) {
            check("lexer: unexpected character is rejected", e.stage == CompileError.Stage.LEXICAL && e.column == 3);
        }
        try {
            lex("\"open");
            check("lexer: unterminated string is rejected", false);
        } catch (CompileError e) {
            check("lexer: unterminated string is rejected", e.getMessage().contains("Unterminated"));
        }
        try {
            lex("99999999999");
            check("lexer: oversized integer is rejected", false);
        } catch (CompileError e) {
            check("lexer: oversized integer is rejected", e.getMessage().contains("too large"));
        }

        // ---- Parser ----
        ASTNode e1 = ((AssignStmt) parse("LET x = 1 + 2 * 3;").statements.get(0)).expression;
        check("parser: * binds tighter than +", e1 instanceof BinaryExpr b && b.operator.equals("+")
                && b.right instanceof BinaryExpr r && r.operator.equals("*"));
        ASTNode e2 = ((AssignStmt) parse("LET x = (1 + 2) * 3;").statements.get(0)).expression;
        check("parser: parentheses override precedence", e2 instanceof BinaryExpr b && b.operator.equals("*")
                && b.left instanceof BinaryExpr);
        ASTNode e3 = ((AssignStmt) parse("LET x = a OR b AND c;").statements.get(0)).expression;
        check("parser: AND binds tighter than OR", e3 instanceof LogicalExpr o && o.operator.equals("OR")
                && o.right instanceof LogicalExpr a && a.operator.equals("AND"));
        ASTNode e4 = ((AssignStmt) parse("LET x = a + 1 < b * 2;").statements.get(0)).expression;
        check("parser: relational is below arithmetic", e4 instanceof RelationalExpr r && r.operator.equals("<")
                && r.left instanceof BinaryExpr && r.right instanceof BinaryExpr);
        ASTNode e5 = ((AssignStmt) parse("LET x = -a;").statements.get(0)).expression;
        check("parser: unary minus", e5 instanceof UnaryExpr);
        IfStmt chain = (IfStmt) parse("IF (a) {} ELSE IF (b) {} ELSE {}").statements.get(0);
        check("parser: ELSE IF chain", chain.elseBlock instanceof IfStmt inner && inner.elseBlock instanceof Block);
        ForStmt f = (ForStmt) parse("FOR (LET i : int = 0; i < 3; i = i + 1) { }").statements.get(0);
        check("parser: FOR has init/condition/update/body", f.init instanceof AssignStmt a && a.declaration
                && f.condition instanceof RelationalExpr && f.update instanceof AssignStmt u && !u.declaration);
        FunctionDecl fd = (FunctionDecl) parse("FUNCTION add(a : int, b : float) : float { RETURN a + b; }").statements.get(0);
        check("parser: function parameters and return type", fd.parameters.size() == 2
                && fd.parameters.get(1).type.equals("float") && fd.returnType.equals("float"));
        FunctionDecl voidFn = (FunctionDecl) parse("FUNCTION hi() { }").statements.get(0);
        check("parser: missing return type means void", voidFn.returnType.equals("void"));
        AssignStmt inferred = (AssignStmt) parse("LET x = 5;").statements.get(0);
        check("parser: LET without a type annotation", inferred.declaration && inferred.declaredType == null);
        CallExpr call = (CallExpr) ((AssignStmt) parse("LET x = f(1, 2, 3);").statements.get(0)).expression;
        check("parser: call arguments", call.arguments.size() == 3);
        try {
            parse("LET x = 5\nPRINT(x);");
            check("parser: missing semicolon is reported", false);
        } catch (CompileError e) {
            check("parser: missing semicolon is reported with its position",
                    e.getMessage().contains("Expected ';'") && e.line == 2 && e.column == 1, e.line + ":" + e.column);
        }

        // ---- SymbolTable ----
        SymbolTable st = new SymbolTable();
        check("symbol table: declare and lookup", st.declare("x", "int") && "int".equals(st.lookup("x")));
        check("symbol table: redeclaration in the same scope is refused", !st.declare("x", "float"));
        st.enterScope("inner");
        check("symbol table: inner scope sees outer variables", "int".equals(st.lookup("x")));
        check("symbol table: shadowing is allowed in an inner scope", st.declare("x", "string") && "string".equals(st.lookup("x")));
        st.exitScope();
        check("symbol table: exiting a scope restores the outer binding", "int".equals(st.lookup("x")));
        st.enterScope("another");
        st.declare("y", "int");
        st.exitScope();
        check("symbol table: inner variables vanish after the scope ends", st.lookup("y") == null);
        check("symbol table: depth returns to 1", st.depth() == 1);

        // ---- SemanticAnalyzer ----
        Block typed = parse("LET a = 1.5; LET b : float = 2; LET c = \"x\" + 1; LET d = 1 < 2;");
        analyze(typed);
        check("semantic: type inference (float)", "float".equals(((AssignStmt) typed.statements.get(0)).resolvedType));
        check("semantic: int widens to float", "float".equals(((AssignStmt) typed.statements.get(1)).resolvedType));
        check("semantic: string + int is string", "string".equals(((AssignStmt) typed.statements.get(2)).resolvedType));
        check("semantic: comparison is boolean", "boolean".equals(((AssignStmt) typed.statements.get(3)).resolvedType));
        CompileError narrowing = errorOf("LET x : int = 2.5;");
        check("semantic: float does not narrow to int", narrowing != null && narrowing.getMessage().contains("expected 'int'"));
        CompileError noReturn = errorOf("FUNCTION f() : int { }");
        check("semantic: missing RETURN is detected", noReturn != null && noReturn.getMessage().contains("missing RETURN"));
        SymbolTable fsym = analyze(parse("FUNCTION f(a : int, b : string) : boolean { RETURN true; }"));
        SymbolTable.FunctionSignature sig = fsym.lookupFunction("f");
        check("semantic: function table records the signature", sig != null && sig.paramTypes.equals(List.of("int", "string"))
                && sig.returnType.equals("boolean"));
        CompileError pos = errorOf("LET a : int = 1;\nPRINT(a + zz);");
        check("semantic: errors carry line and column", pos != null && pos.line == 2 && pos.column == 11, pos == null ? "no error" : pos.line + ":" + pos.column);

        // ---- CodeGenerator ----
        String g1 = generate("LET f : float = 1; PRINT(f);");
        check("codegen: float becomes double", g1.contains("private static double f;"));
        check("codegen: top-level variables are static fields", g1.contains("f = 1;"));
        String g2 = generate("LET a = 1; LET b = 2; LET c = 3; PRINT((a + b) * c); PRINT(a + b * c);");
        check("codegen: keeps needed parentheses", g2.contains("System.out.println((a + b) * c);"));
        check("codegen: drops unneeded parentheses", g2.contains("System.out.println(a + b * c);"));
        String g3 = generate("LET s : string = \"a\"; PRINT(s == \"a\"); PRINT(s != \"a\");");
        check("codegen: string equality uses equals()", g3.contains("s.equals(\"a\")") && g3.contains("!s.equals(\"a\")"));
        String g4 = generate("LET x : int = 1; { LET x : int = 2; PRINT(x); }");
        check("codegen: shadowing variable gets a fresh Java name", g4.contains("int x_1 = 2;"));
        String g5 = generate("WHILE (false) { PRINT(1); }");
        check("codegen: constant loop condition is wrapped", g5.contains("while (Boolean.valueOf(false))"));
        String g6 = generate("LET a = 1;");
        check("codegen: programs without PRINT show variable values", g6.contains("System.out.println(\"a = \" + a);"));
        String g7 = generate("IF (true) { PRINT(1); } ELSE IF (false) { PRINT(2); } ELSE { PRINT(3); }");
        check("codegen: ELSE IF is emitted as else if", g7.contains("} else if (false) {"));
    }

    // ------------------------------------------------------------------ end-to-end tests

    private static void endToEndTests(Path testsDir) throws Exception {
        List<Path> categories;
        try (Stream<Path> s = Files.list(testsDir)) {
            categories = s.filter(Files::isDirectory).sorted().collect(Collectors.toList());
        }
        for (Path category : categories) {
            String catName = category.getFileName().toString();
            List<Path> programs;
            try (Stream<Path> s = Files.list(category)) {
                programs = s.filter(p -> p.toString().endsWith(".txt")).sorted().collect(Collectors.toList());
            }
            int[] counts = new int[2];
            perCategory.put(catName, counts);
            for (Path program : programs) {
                String name = program.getFileName().toString().replace(".txt", "");
                boolean ok = runOne(catName + "/" + name, program);
                counts[1]++;
                if (ok) counts[0]++;
            }
            System.out.printf("  %-18s %d / %d passed%n", catName, counts[0], counts[1]);
        }
    }

    private static String read(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.UTF_8);
    }

    private static String normalize(String s) {
        return s.replace("\r\n", "\n").stripTrailing();
    }

    private static boolean runOne(String label, Path program) throws Exception {
        String base = program.toString().replaceAll("\\.txt$", "");
        Path expectedOut = Path.of(base + ".expected");
        Path expectedErr = Path.of(base + ".error");
        Path expectedRuntime = Path.of(base + ".runtime");
        Path stdinFile = Path.of(base + ".in");
        String stdin = Files.exists(stdinFile) ? read(stdinFile) : "";
        String source = read(program);

        int failedBefore = failed;
        try {
            Block ast = parse(source);
            SymbolTable symbols = analyze(ast);
            String java = new CodeGenerator().generateProgram(ast, "GeneratedProgram", symbols);

            if (Files.exists(expectedErr)) {
                check(label, false, "expected a compile error but compilation succeeded");
                return failed == failedBefore;
            }
            Executor.Result r = Executor.runCaptured("GeneratedProgram", java, stdin, 15);
            if (!r.compiled) {
                check(label, false, "generated Java did not compile: " + r.compilerLog.trim());
            } else if (r.timedOut) {
                check(label, false, "timed out");
            } else if (Files.exists(expectedRuntime)) {
                String want = normalize(read(expectedRuntime));
                check(label, r.exitCode != 0 && r.stderr.contains(want), "stderr: " + r.stderr.trim());
            } else if (Files.exists(expectedOut)) {
                String want = normalize(read(expectedOut));
                String got = normalize(r.stdout);
                check(label, r.exitCode == 0 && want.equals(got), "expected <" + want.replace("\n", "|") + "> got <" + got.replace("\n", "|") + "> " + r.stderr.trim());
            } else {
                check(label, false, "no .expected/.error/.runtime file");
            }
        } catch (CompileError e) {
            if (!Files.exists(expectedErr)) {
                check(label, false, "unexpected compile error: " + e.format());
            } else {
                String[] want = normalize(read(expectedErr)).split("\\|", 3);
                boolean ok = e.stage.label.equals(want[0]) && String.valueOf(e.line).equals(want[1]) && e.getMessage().contains(want[2]);
                check(label, ok, "expected " + String.join("|", want) + " but got " + e.stage.label + "|" + e.line + "|" + e.getMessage());
            }
        }
        return failed == failedBefore;
    }
}
