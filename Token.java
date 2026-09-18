public class Token {
    public final TokenType type;
    public final String text;
    public final int position; // character offset in source, used for error messages

    public Token(TokenType type, String text, int position) {
        this.type = type;
        this.text = text;
        this.position = position;
    }

    @Override
    public String toString() {
        return type + "(\"" + text + "\")";
    }
}
