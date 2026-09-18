import java.io.*;
import java.nio.file.*;
import java.util.List;

public class Main {

    public static void main(String[] args) throws Exception {
        // ---- Custom input support: pass a file path as the first argument ----
        //   java Main my_program.txt
        // If no argument is given, falls back to a built-in sample program,
        // so the tool still runs out of the box for a quick demo.
        String source;
        if (args.length > 0) {
            source = Files.readString(Path.of(args[0]));
            System.out.println("(reading pseudocode from file: " + args[0] + ")\n");
        } else {
            source = """
                    LET a = 5;
                    LET b = 3;
                    LET c = a + b * 2;
                    LET d = c - a / a;
                    """;
            System.out.println("(no file given, using built-in sample program - "
                    + "run 'java Main <yourfile.txt>' for custom input)\n");
        }

        compileAndRun(source);
    }

    // Runs every compiler stage on the given source, printing each stage's
    // output in turn, then actually compiles and executes the result.
    private static void compileAndRun(String source) throws Exception {
        System.out.println("=======================================");
        System.out.println("STAGE 0 - SOURCE (pseudocode)");
        System.out.println(source.stripTrailing());

        // ---- Stage 1: Lexical Analysis ----
        List<Token> tokens = new Lexer(source).tokenize();
        System.out.println("\nSTAGE 1 - TOKENS (Lexer)");
        System.out.println(tokens);

        // ---- Stage 2: Syntax Analysis -> AST ----
        List<AssignStmt> program = new Parser(tokens).parseProgram();
        System.out.println("\nSTAGE 2 - AST (Parser)");
        for (int i = 0; i < program.size(); i++) {
            AssignStmt s = program.get(i);
            System.out.println("  [" + (i + 1) + "] AssignStmt(" + s.identifier + " = " + describe(s.expression) + ")");
        }

        // ---- Stage 3: Semantic Analysis + Symbol Table ----
        SymbolTable symbols = new SymbolTable();
        SemanticAnalyzer analyzer = new SemanticAnalyzer(symbols);
        System.out.println("\nSTAGE 3 - SEMANTIC ANALYSIS + SYMBOL TABLE");
        for (AssignStmt stmt : program) {
            analyzer.analyze(stmt);
            System.out.println("  declared '" + stmt.identifier + "'  ->  symbol table now: " + symbols.declaredNames());
        }

        // ---- Stage 4: Code Generation ----
        String className = "GeneratedProgram";
        String javaCode = new CodeGenerator().generateProgram(className, program);
        System.out.println("\nSTAGE 4 - GENERATED JAVA (Code Generator)");
        System.out.println(javaCode);

        // ---- Stage 5: Actually compile and run the generated Java ----
        System.out.println("STAGE 5 - COMPILING AND EXECUTING GENERATED CODE...");
        String output = execute(className, javaCode);
        System.out.println("\nSTAGE 5 - PROGRAM OUTPUT (real, executed result)");
        System.out.println(output.stripTrailing());
        System.out.println("=======================================");

        // ---- Bonus: a deliberately invalid statement, to show error handling ----
        System.out.println();
        runSemanticErrorCase();
    }

    private static void runSemanticErrorCase() {
        String badSource = "LET bad = undeclared + 1;";
        System.out.println("EXTRA CHECK - deliberately invalid input: " + badSource);
        try {
            List<Token> tokens = new Lexer(badSource).tokenize();
            AssignStmt stmt = new Parser(tokens).parseStatement();
            new SemanticAnalyzer(new SymbolTable()).analyze(stmt);
        } catch (RuntimeException e) {
            System.out.println("ERROR (correctly caught before code generation): " + e.getMessage());
        }
    }

    private static String describe(ASTNode node) {
        if (node instanceof IntegerLiteral lit) return lit.value;
        if (node instanceof IdentifierExpr id) return id.name;
        if (node instanceof BinaryExpr bin) return "(" + describe(bin.left) + " " + bin.operator + " " + describe(bin.right) + ")";
        return String.valueOf(node);
    }

    // Writes the generated source to a temp folder, compiles it with javac,
    // runs it with java, and returns whatever the program printed to stdout.
    private static String execute(String className, String javaCode) throws Exception {
        Path tempDir = Files.createTempDirectory("compiler-demo");
        Path javaFile = tempDir.resolve(className + ".java");
        Files.writeString(javaFile, javaCode);

        runCommand(new String[]{"javac", javaFile.toString()}, tempDir);
        Process runProcess = new ProcessBuilder("java", "-cp", tempDir.toString(), className)
                .directory(tempDir.toFile())
                .start();

        String stdout = new String(runProcess.getInputStream().readAllBytes());
        String stderr = new String(runProcess.getErrorStream().readAllBytes());
        runProcess.waitFor();
        if (!stderr.isBlank()) {
            throw new RuntimeException("Generated program failed to run:\n" + stderr);
        }
        return stdout;
    }

    private static void runCommand(String[] command, Path workingDir) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workingDir.toFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String log = new String(p.getInputStream().readAllBytes());
        int exit = p.waitFor();
        if (exit != 0) {
            throw new RuntimeException("Command failed: " + String.join(" ", command) + "\n" + log);
        }
    }
}
