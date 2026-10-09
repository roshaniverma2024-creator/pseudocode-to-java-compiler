// A compile-time error that remembers which stage found it and where in the source it happened.
public class CompileError extends RuntimeException {
    public enum Stage {
        LEXICAL("Lexical"), SYNTAX("Syntax"), SEMANTIC("Semantic");

        public final String label;

        Stage(String label) {
            this.label = label;
        }
    }

    public final Stage stage;
    public final int line;
    public final int column;

    public CompileError(Stage stage, String message, int line, int column) {
        super(message);
        this.stage = stage;
        this.line = line;
        this.column = column;
    }

    public String format() {
        return stage.label + " error at line " + line + ", column " + column + ": " + getMessage();
    }
}
