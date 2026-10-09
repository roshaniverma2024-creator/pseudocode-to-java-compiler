public class Token {
    public final TokenType type;
    public final String text;
    public final int line;
    public final int column;

    public Token(TokenType type, String text, int line, int column) {
        this.type = type;
        this.text = text;
        this.line = line;
        this.column = column;
    }

    // Compact form used by the stage-1 display: LET, IDENTIFIER(x), INTEGER_LITERAL(5) ...
    public String shortString() {
        switch (type) {
            case IDENTIFIER: case INTEGER_LITERAL: case FLOAT_LITERAL: case BOOLEAN_LITERAL:
                return type + "(" + text + ")";
            case STRING_LITERAL:
                return type + "(\"" + text + "\")";
            default:
                return type.toString();
        }
    }

    @Override
    public String toString() {
        return String.format("Token(%s, '%s', L%d:C%d)", type, text, line, column);
    }
}
