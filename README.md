# Pseudocode-to-Java Compiler — Phase 1 Initial Prototype

This is the minimal end-to-end slice referenced in the Phase 1 proposal.
It proves the full pipeline works — and doesn't stop at printing
translated source text: it actually **compiles and runs** the generated
Java, so you can show a real, executed result.

Pipeline: **Lexer → Parser → AST → Semantic Analyzer → Code Generator →
javac → java (actual execution)**

It handles a small multi-line program of statements of the form:

```
LET <identifier> = <expr>;
```

where `<expr>` supports `+ - * /` over integers and previously-declared
identifiers, and any number of such lines in sequence.

## Files

| File | Compiler stage | What it does |
|---|---|---|
| `TokenType.java` | — | Enum of every token kind the lexer can produce |
| `Token.java` | — | A single token: type, text, source position |
| `Lexer.java` | Lexical Analysis | Hand-written scanner: source string → list of `Token` |
| `ASTNode.java` | — | Marker interface for all AST node types |
| `AssignStmt.java`, `BinaryExpr.java`, `IntegerLiteral.java`, `IdentifierExpr.java` | Syntax Analysis | AST node classes |
| `Parser.java` | Syntax Analysis | Recursive-descent parser: tokens → AST. `parseProgram()` parses a whole multi-line program, not just one line |
| `SymbolTable.java` | Semantic Analysis | Tracks which identifiers have been declared |
| `SemanticAnalyzer.java` | Semantic Analysis | Walks the AST, checks for use-before-declaration |
| `CodeGenerator.java` | Code Generation | Walks the AST, emits Java source text. `generateProgram()` wraps the whole program into one compilable Java class |
| `Main.java` | Driver | Runs the full pipeline, then **actually compiles and executes** the generated Java via `javac`/`java`, and prints its real output |

## What to build / how to build it

You don't need to build anything yourself — this is already built. To
run it and see it work:

1. **Requires a JDK** (17+; this was tested on JDK 21). A JDK is not the
   same as a JRE — check with `javac -version`; if that command isn't
   found, you have a JRE only and need to install the full JDK.
2. Unzip this folder, then in a terminal:
   ```bash
   cd src
   javac *.java -d ../out
   cd ../out
   java Main
   ```
3. That runs a built-in sample program. **To compile your own pseudocode
   instead**, write it to a `.txt` file and pass it as an argument:
   ```bash
   java Main my_program.txt
   ```
   where `my_program.txt` contains lines like:
   ```
   LET price = 100;
   LET qty = 4;
   LET total = price * qty;
   ```
   Any `.txt` file with `LET` statements in this grammar works — this is
   what makes it behave like a real compiler (custom input in, compiled
   and executed output out) rather than a fixed demo. A ready-made
   example, `sample_program.txt`, is included in this folder — try
   `java Main ../../sample_program.txt` (adjust the path to wherever
   you unzipped it) to see a different program run.

## What you'll see when you run it

Every stage of the pipeline prints its own output, in order:

1. **STAGE 0 — SOURCE**: the pseudocode you gave it (or the built-in sample).
2. **STAGE 1 — TOKENS**: the full token stream from the lexer.
3. **STAGE 2 — AST**: each statement's parsed tree, printed as a parenthesized expression.
4. **STAGE 3 — SEMANTIC ANALYSIS + SYMBOL TABLE**: the symbol table's contents growing as each variable is declared.
5. **STAGE 4 — GENERATED JAVA**: the full, real `GeneratedProgram.java` source.
6. **STAGE 5 — PROGRAM OUTPUT**: `javac`/`java` actually compiling and running that generated class, showing the real executed result — not just printed source text.
7. **EXTRA CHECK**: a deliberately invalid statement (`LET bad = undeclared + 1;`) showing the semantic error being caught *before* any code is generated or compiled.

## Grammar covered by this prototype

```
program    -> statement*
statement  -> "LET" IDENTIFIER "=" expr ";"
expr       -> term (("+" | "-") term)*
term       -> factor (("*" | "/") factor)*
factor     -> INTEGER | IDENTIFIER
```

## What Phase 2 adds on top of this

- `if/else`, `while`, `for` statements and blocks (multiple statements, not just one)
- Functions with parameters and return values
- Data types beyond plain integers (float, string, bool) and type checking
- Nested scopes in the symbol table (currently one flat global scope)
- Proper error recovery (currently stops at the first error)
- The interactive visualizer described in the proposal, rendering these
  same stages live as the user types
