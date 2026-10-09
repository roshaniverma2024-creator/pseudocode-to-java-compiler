import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

// Usage:  java Main [program.txt]
// Runs the whole compiler on a pseudocode file, printing what every stage produced:
//   source -> tokens -> AST (tree) -> semantic analysis + symbol table -> generated Java -> compiled + executed output
// With no argument it compiles a built-in demo (recursive factorial).
public class Main {

    private static final String DEMO_PROGRAM =
            "LET n : int = 5;\n"
            + "FUNCTION factorial(k : int) : int {\n"
            + "  IF (k <= 1) {\n"
            + "    RETURN 1;\n"
            + "  } ELSE {\n"
            + "    RETURN k * factorial(k - 1);\n"
            + "  }\n"
            + "}\n"
            + "LET result : int = factorial(n);\n"
            + "PRINT(result);\n";

    public static void main(String[] args) {
        String source;
        if (args.length > 0) {
            try {
                source = Files.readString(Path.of(args[0]), StandardCharsets.UTF_8);
            } catch (IOException e) {
                System.err.println("Cannot read file '" + args[0] + "': " + e.getMessage());
                System.exit(2);
                return;
            }
            System.out.println("(compiling file: " + args[0] + ")");
        } else {
            source = DEMO_PROGRAM;
            System.out.println("(no file given - compiling the built-in factorial demo; use: java Main <file.txt>)");
        }
        System.exit(compile(source));
    }

    // returns the process exit code: 0 = ok, 1 = compile error, 2 = runtime/internal error
    private static int compile(String source) {
        String[] sourceLines = source.split("\n", -1);

        banner("STAGE 0 - SOURCE (pseudocode)");
        for (int i = 0; i < sourceLines.length; i++) {
            if (i == sourceLines.length - 1 && sourceLines[i].isEmpty()) break;
            System.out.printf("  %3d | %s%n", i + 1, sourceLines[i]);
        }

        try {
            // ---- Stage 1: lexical analysis ----
            List<Token> tokens = new Lexer(source).tokenize();
            banner("STAGE 1 - TOKENS (Lexer)   [" + tokens.size() + " tokens]");
            printTokens(tokens);

            // ---- Stage 2: syntax analysis -> AST ----
            Block program = new Parser(tokens).parseProgram();
            banner("STAGE 2 - AST (Parser)   [" + program.statements.size() + " top-level statements]");
            AstPrinter.print(program, System.out);

            // ---- Stage 3: semantic analysis + symbol table ----
            SymbolTable symbols = new SymbolTable();
            new SemanticAnalyzer(symbols).analyze(program);
            banner("STAGE 3 - SEMANTIC ANALYSIS + SYMBOL TABLE   [scope and type checks passed]");
            printSymbolTable(symbols);

            // ---- Stage 4: code generation ----
            CodeGenerator generator = new CodeGenerator();
            String javaCode = generator.generateProgram(program, "GeneratedProgram", symbols);
            banner("STAGE 4 - GENERATED JAVA (Code Generator)");
            System.out.print(javaCode);
            try {
                Files.writeString(Path.of("GeneratedProgram.java"), javaCode, StandardCharsets.UTF_8);
                System.out.println("  (saved as GeneratedProgram.java in the current folder)");
            } catch (IOException e) {
                System.out.println("  (could not save GeneratedProgram.java: " + e.getMessage() + ")");
            }

            // ---- Stage 5: compile with javac and really run it ----
            banner("STAGE 5 - COMPILE + EXECUTE (real program output below)");
            Executor.Result result = Executor.runInteractive("GeneratedProgram", javaCode, 60);
            if (!result.compiled) {
                System.out.println("INTERNAL ERROR: the generated Java did not compile:\n" + result.compilerLog);
                return 2;
            }
            if (result.timedOut) {
                System.out.println("\nRUNTIME ERROR: the program was stopped after 60 seconds (infinite loop?)");
                return 2;
            }
            if (result.exitCode != 0) {
                System.out.println("\nRUNTIME ERROR: " + firstErrorLine(result.stderr));
                return 2;
            }
            System.out.println("----------------------------------------------");
            System.out.println("Done: compiled and executed successfully.");
            return 0;

        } catch (CompileError e) {
            printCompileError(e, sourceLines);
            return 1;
        } catch (Exception e) {
            System.out.println("\nINTERNAL ERROR: " + e);
            return 2;
        }
    }

    private static void banner(String title) {
        System.out.println();
        System.out.println("==============================================");
        System.out.println(title);
        System.out.println("==============================================");
    }

    private static void printTokens(List<Token> tokens) {
        StringBuilder line = new StringBuilder("  ");
        for (Token t : tokens) {
            String s = t.shortString() + " ";
            if (line.length() + s.length() > 100) {
                System.out.println(line.toString().stripTrailing());
                line = new StringBuilder("  ");
            }
            line.append(s);
        }
        System.out.println(line.toString().stripTrailing());
    }

    private static void printSymbolTable(SymbolTable symbols) {
        System.out.println("  Scope stack trace (a scope is pushed when a block/loop/function starts, popped when it ends):");
        for (String event : symbols.trace()) System.out.println("    " + event);

        System.out.println();
        System.out.println("  Variables (every declaration, in order):");
        List<String[]> vars = symbols.history();
        if (vars.isEmpty()) {
            System.out.println("    (none)");
        } else {
            TablePrinter.print(new String[]{"Name", "Type", "Scope", "Depth"}, vars, "    ", System.out);
        }

        System.out.println();
        System.out.println("  Function table:");
        List<String[]> rows = new ArrayList<>();
        for (SymbolTable.FunctionSignature f : symbols.functions()) {
            List<String> ps = new ArrayList<>();
            for (int i = 0; i < f.paramNames.size(); i++) ps.add(f.paramNames.get(i) + ":" + f.paramTypes.get(i));
            rows.add(new String[]{f.name, ps.isEmpty() ? "(none)" : String.join(", ", ps), f.returnType});
        }
        if (rows.isEmpty()) {
            System.out.println("    (none)");
        } else {
            TablePrinter.print(new String[]{"Function", "Parameters", "Returns"}, rows, "    ", System.out);
        }
    }

    private static void printCompileError(CompileError e, String[] sourceLines) {
        System.out.println();
        System.out.println("==============================================");
        System.out.println("COMPILATION STOPPED - " + e.stage.label + " error at line " + e.line + ", column " + e.column);
        System.out.println("==============================================");
        System.out.println("  " + e.getMessage());
        if (e.line >= 1 && e.line <= sourceLines.length) {
            String prefix = String.format("  %3d | ", e.line);
            System.out.println();
            System.out.println(prefix + sourceLines[e.line - 1]);
            System.out.println(" ".repeat(prefix.length() + Math.max(0, e.column - 1)) + "^");
        }
        System.out.println();
        System.out.println("(no code was generated or executed)");
    }

    private static String firstErrorLine(String stderr) {
        for (String line : stderr.split("\n")) {
            String t = line.trim();
            if (t.startsWith("Exception in thread")) {
                int idx = t.indexOf("\" ");
                return idx >= 0 ? t.substring(idx + 2) : t;
            }
        }
        return stderr.isBlank() ? "the program exited with a non-zero status" : stderr.trim();
    }
}
