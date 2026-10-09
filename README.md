# Pseudocode-to-Java Compiler — Phase 2 (Review 2)

A hand-written compiler (no parser generators) that translates a small typed pseudocode language into
Java, then **compiles and runs the Java** so you see the real result. Every stage prints what it produced.

```
source -> Lexer -> tokens -> Parser -> AST -> Semantic Analyzer + Symbol Table -> Code Generator -> Java -> javac + java -> output
```

## Run it

Needs a **JDK 17+** (check with `javac -version`).

```bash
# from the project folder
javac -encoding UTF-8 -d out src/*.java        # build
java -cp out Main                               # built-in factorial demo
java -cp out Main examples/1_factorial.txt      # any pseudocode file
java -cp out TestRunner tests                   # run the whole test suite
```

`java -cp out Main yourprogram.txt` prints, in order: SOURCE, TOKENS, AST (tree), SEMANTIC ANALYSIS +
SYMBOL TABLE (scope trace + tables), GENERATED JAVA (also saved as `GeneratedProgram.java`), and the
program's real output. A compile error stops the pipeline and shows the line, column and a caret.

`INPUT(x);` reads one line from the keyboard, so run those programs in a normal terminal.

## The language

```
program      -> statement*
statement    -> letStmt | assignStmt | ifStmt | whileStmt | forStmt | printStmt | inputStmt
              | funcDecl | returnStmt | callStmt | block
block        -> "{" statement* "}"
letStmt      -> "LET" IDENTIFIER [ ":" type ] "=" expr ";"       // type optional: inferred when omitted
assignStmt   -> IDENTIFIER "=" expr ";"
ifStmt       -> "IF" "(" expr ")" block [ "ELSE" ( block | ifStmt ) ]
whileStmt    -> "WHILE" "(" expr ")" block
forStmt      -> "FOR" "(" letStmt expr ";" IDENTIFIER "=" expr ")" block
printStmt    -> "PRINT" "(" expr ")" ";"
inputStmt    -> "INPUT" "(" IDENTIFIER ")" ";"
funcDecl     -> "FUNCTION" IDENTIFIER "(" [ param { "," param } ] ")" [ ":" type ] block   // no type = returns nothing
param        -> IDENTIFIER ":" type
returnStmt   -> "RETURN" [ expr ] ";"
callStmt     -> IDENTIFIER "(" [ expr { "," expr } ] ")" ";"
type         -> "int" | "float" | "boolean" | "string"
expr         -> logicOr
logicOr      -> logicAnd { "OR" logicAnd }
logicAnd     -> equality { "AND" equality }
equality     -> relational { ( "==" | "!=" ) relational }
relational   -> addExpr { ( "<" | ">" | "<=" | ">=" ) addExpr }
addExpr      -> term { ( "+" | "-" ) term }
term         -> factor { ( "*" | "/" ) factor }
factor       -> INTEGER | FLOAT | STRING | "true" | "false" | IDENTIFIER [ "(" args ")" ] | "(" expr ")" | "-" factor
```

Keywords are case-sensitive (`PRINT`, not `print`; types are lowercase). `//` starts a comment. Strings support
`\n \t \" \\`. `int` converts to `float` automatically; `+` with a string concatenates.

## What the compiler checks

- **Scope**: use before declaration, redeclaration in the same scope; inner scopes may shadow outer variables;
  loop variables and block variables disappear when their scope ends; functions can read/change globals.
- **Types**: declarations, assignments, operators, conditions (must be boolean), function arguments and returns.
- **Functions**: argument count/types, return type, missing RETURN on some path, unreachable code after RETURN,
  functions only at top level, calls allowed before the declaration (so mutual recursion works).
- Every error carries the **stage** (Lexical / Syntax / Semantic), **line** and **column**.

## Files (`src/`)

| Stage | Files |
|---|---|
| Lexical analysis | `Lexer`, `Token`, `TokenType` |
| Syntax analysis | `Parser`, `ASTNode` + 19 node classes: `AssignStmt`, `IfStmt`, `WhileStmt`, `ForStmt`, `Block`, `FunctionDecl`, `ReturnStmt`, `PrintStmt`, `InputStmt`, `CallExpr`, `BinaryExpr`, `RelationalExpr`, `LogicalExpr`, `UnaryExpr`, `IdentifierExpr`, `IntegerLiteral`, `FloatLiteral`, `StringLiteral`, `BooleanLiteral` |
| Semantic analysis | `SymbolTable` (scope stack + function table), `SemanticAnalyzer`, `CompileError` |
| Code generation | `CodeGenerator` |
| Driver / display | `Main`, `AstPrinter` (tree), `TablePrinter` (tables), `Executor` (javac + run) |
| Testing | `TestRunner` |

## Tests (`tests/`)

100 end-to-end programs plus 46 unit tests — all passing. Each `tests/<category>/<name>.txt` has
`.expected` (exact output), `.error` (`Stage|line|text` that the error must match), `.runtime`
(text expected in a run-time crash) and optionally `.in` (keyboard input).

| Category | Programs | What it covers |
|---|---|---|
| `valid` | 29 | recursion, loops, functions, types, INPUT, logic, strings |
| `scope` | 11 | shadowing, nesting, loop/block/parameter variables going out of scope |
| `syntax_errors` | 17 | lexical errors and syntax errors (missing `;`, unmatched `{ }` `( )`, bad headers) |
| `semantic_errors` | 27 | type mismatches, bad calls, missing/unreachable RETURN, non-boolean conditions |
| `edge_cases` | 13 | empty blocks, escapes, precedence, constant loop conditions, runtime error |
| `regression` | 3 | the Phase 1 programs (no type annotations) still work |

## Design notes (good viva answers)

- **Globals are static fields.** Top-level `LET` variables become `private static` fields so functions can read them.
- **Shadowing is renamed.** Java forbids a local hiding a local, so a hiding declaration is emitted as `x_1`.
- **`string == string` uses `.equals()`** — Java's `==` would compare references, not text.
- **Constant loop conditions** (`WHILE (false)`) are wrapped in `Boolean.valueOf(...)`; otherwise javac reports unreachable code.
- **Minimal parentheses**: the generator only adds `( )` where precedence needs them, so the Java reads naturally.
- A program with **no `PRINT`** prints the final value of each top-level variable (keeps Phase 1 programs visible).
- Error recovery (reporting several errors in one run) is planned for Phase 3; Phase 2 stops at the first error.
