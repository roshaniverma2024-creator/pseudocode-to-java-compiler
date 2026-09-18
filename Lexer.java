import java.util.ArrayList;
import java.util.List;

public class Lexer {
    private final String source;
    private int pos = 0;

    public Lexer(String source) {
        this.source = source;
    }

    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();
        while (pos < source.length()) {
            char c = source.charAt(pos);

            if (Character.isWhitespace(c)) {
                pos++;
                continue;
            }

            if (Character.isLetter(c)) {
                tokens.add(readIdentifierOrKeyword());
                continue;
            }

            if (Character.isDigit(c)) {
                tokens.add(readInteger());
                continue;
            }

            switch (c) {
                case '=' -> { tokens.add(new Token(TokenType.ASSIGN, "=", pos)); pos++; }
                case '+' -> { tokens.add(new Token(TokenType.PLUS, "+", pos)); pos++; }
                case '-' -> { tokens.add(new Token(TokenType.MINUS, "-", pos)); pos++; }
                case '*' -> { tokens.add(new Token(TokenType.STAR, "*", pos)); pos++; }
                case '/' -> { tokens.add(new Token(TokenType.SLASH, "/", pos)); pos++; }
                case ';' -> { tokens.add(new Token(TokenType.SEMICOLON, ";", pos)); pos++; }
                default -> throw new RuntimeException(
                        "Lexical error at position " + pos + ": unexpected character '" + c + "'");
            }
        }
        tokens.add(new Token(TokenType.EOF, "", pos));
        return tokens;
    }

    private Token readIdentifierOrKeyword() {
        int start = pos;
        while (pos < source.length() && Character.isLetterOrDigit(source.charAt(pos))) {
            pos++;
        }
        String text = source.substring(start, pos);
        if (text.equals("LET")) {
            return new Token(TokenType.LET, text, start);
        }
        return new Token(TokenType.IDENTIFIER, text, start);
    }

    private Token readInteger() {
        int start = pos;
        while (pos < source.length() && Character.isDigit(source.charAt(pos))) {
            pos++;
        }
        return new Token(TokenType.INTEGER, source.substring(start, pos), start);
    }
}
