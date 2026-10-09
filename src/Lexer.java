import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Lexer {
    private final String input;
    private int pos = 0;
    private int line = 1;
    private int col = 1;

    private static final Map<String, TokenType> KEYWORDS = new HashMap<>();

    static {
        // statement keywords (UPPERCASE)
        KEYWORDS.put("LET", TokenType.LET);
        KEYWORDS.put("IF", TokenType.IF);
        KEYWORDS.put("ELSE", TokenType.ELSE);
        KEYWORDS.put("WHILE", TokenType.WHILE);
        KEYWORDS.put("FOR", TokenType.FOR);
        KEYWORDS.put("FUNCTION", TokenType.FUNCTION);
        KEYWORDS.put("RETURN", TokenType.RETURN);
        KEYWORDS.put("PRINT", TokenType.PRINT);
        KEYWORDS.put("INPUT", TokenType.INPUT);

        // type names (lowercase)
        KEYWORDS.put("int", TokenType.TYPE_INT);
        KEYWORDS.put("float", TokenType.TYPE_FLOAT);
        KEYWORDS.put("boolean", TokenType.TYPE_BOOLEAN);
        KEYWORDS.put("string", TokenType.TYPE_STRING);

        // boolean literals and logical operators
        KEYWORDS.put("true", TokenType.BOOLEAN_LITERAL);
        KEYWORDS.put("false", TokenType.BOOLEAN_LITERAL);
        KEYWORDS.put("TRUE", TokenType.BOOLEAN_LITERAL);
        KEYWORDS.put("FALSE", TokenType.BOOLEAN_LITERAL);
        KEYWORDS.put("AND", TokenType.AND);
        KEYWORDS.put("OR", TokenType.OR);
    }

    public Lexer(String input) {
        this.input = input;
    }

    // If someone wrote 'print' instead of 'PRINT' (or 'INT' instead of 'int'), tell them.
    public static String keywordSuggestion(String text) {
        String upper = text.toUpperCase();
        String lower = text.toLowerCase();
        if (!KEYWORDS.containsKey(text)) {
            if (KEYWORDS.containsKey(upper)) return upper;
            if (KEYWORDS.containsKey(lower)) return lower;
        }
        return null;
    }

    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();

        while (pos < input.length()) {
            char current = peek();

            if (Character.isWhitespace(current)) {
                advance();
                continue;
            }

            // single-line comment: // ... end of line
            if (current == '/' && peekAhead(1) == '/') {
                while (pos < input.length() && peek() != '\n') advance();
                continue;
            }

            if (current == '"') {
                tokens.add(scanString());
                continue;
            }

            if (Character.isDigit(current)) {
                tokens.add(scanNumber());
                continue;
            }

            if (Character.isLetter(current) || current == '_') {
                tokens.add(scanIdentifierOrKeyword());
                continue;
            }

            int startLine = line;
            int startCol = col;
            if (current == '=') {
                advance();
                if (peek() == '=') { advance(); tokens.add(new Token(TokenType.EQUAL, "==", startLine, startCol)); }
                else { tokens.add(new Token(TokenType.ASSIGN, "=", startLine, startCol)); }
            } else if (current == '!') {
                advance();
                if (peek() == '=') { advance(); tokens.add(new Token(TokenType.NOT_EQUAL, "!=", startLine, startCol)); }
                else { throw lexError("Unexpected character '!' (use != for not-equal)", startLine, startCol); }
            } else if (current == '<') {
                advance();
                if (peek() == '=') { advance(); tokens.add(new Token(TokenType.LESS_EQUAL, "<=", startLine, startCol)); }
                else { tokens.add(new Token(TokenType.LESS_THAN, "<", startLine, startCol)); }
            } else if (current == '>') {
                advance();
                if (peek() == '=') { advance(); tokens.add(new Token(TokenType.GREATER_EQUAL, ">=", startLine, startCol)); }
                else { tokens.add(new Token(TokenType.GREATER_THAN, ">", startLine, startCol)); }
            } else if (current == '+') { advance(); tokens.add(new Token(TokenType.PLUS, "+", startLine, startCol)); }
            else if (current == '-') { advance(); tokens.add(new Token(TokenType.MINUS, "-", startLine, startCol)); }
            else if (current == '*') { advance(); tokens.add(new Token(TokenType.STAR, "*", startLine, startCol)); }
            else if (current == '/') { advance(); tokens.add(new Token(TokenType.SLASH, "/", startLine, startCol)); }
            else if (current == ';') { advance(); tokens.add(new Token(TokenType.SEMICOLON, ";", startLine, startCol)); }
            else if (current == ':') { advance(); tokens.add(new Token(TokenType.COLON, ":", startLine, startCol)); }
            else if (current == ',') { advance(); tokens.add(new Token(TokenType.COMMA, ",", startLine, startCol)); }
            else if (current == '(') { advance(); tokens.add(new Token(TokenType.LPAREN, "(", startLine, startCol)); }
            else if (current == ')') { advance(); tokens.add(new Token(TokenType.RPAREN, ")", startLine, startCol)); }
            else if (current == '{') { advance(); tokens.add(new Token(TokenType.LBRACE, "{", startLine, startCol)); }
            else if (current == '}') { advance(); tokens.add(new Token(TokenType.RBRACE, "}", startLine, startCol)); }
            else if (current == '&' || current == '|') {
                throw lexError("Unexpected character '" + current + "' (use AND / OR for logical operators)", startLine, startCol);
            } else {
                throw lexError("Unexpected character '" + current + "'", startLine, startCol);
            }
        }

        tokens.add(new Token(TokenType.EOF, "", line, col));
        return tokens;
    }

    private Token scanNumber() {
        int startLine = line;
        int startCol = col;
        StringBuilder sb = new StringBuilder();
        while (Character.isDigit(peek())) sb.append(advance());

        boolean isFloat = false;
        // a '.' only belongs to the number if a digit follows it (so "5." is not a float)
        if (peek() == '.' && Character.isDigit(peekAhead(1))) {
            isFloat = true;
            sb.append(advance());
            while (Character.isDigit(peek())) sb.append(advance());
        }

        if (Character.isLetter(peek()) || peek() == '_') {
            throw lexError("Malformed number '" + sb + peek() + "...'", startLine, startCol);
        }

        String text = sb.toString();
        if (isFloat) {
            if (Double.isInfinite(Double.parseDouble(text))) {
                throw lexError("Float literal '" + text + "' is too large", startLine, startCol);
            }
            return new Token(TokenType.FLOAT_LITERAL, text, startLine, startCol);
        }
        try {
            Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw lexError("Integer literal '" + text + "' is too large for int", startLine, startCol);
        }
        return new Token(TokenType.INTEGER_LITERAL, text, startLine, startCol);
    }

    private Token scanString() {
        int startLine = line;
        int startCol = col;
        advance(); // opening quote
        StringBuilder sb = new StringBuilder();

        while (pos < input.length() && peek() != '"') {
            char c = peek();
            if (c == '\n') {
                throw lexError("Unterminated string literal", startLine, startCol);
            }
            if (c == '\\') {
                advance();
                if (pos >= input.length()) break;
                char e = advance();
                switch (e) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    default:
                        throw lexError("Unknown escape sequence '\\" + e + "' (supported: \\n \\t \\\" \\\\)", line, col - 2);
                }
            } else {
                sb.append(advance());
            }
        }

        if (pos >= input.length()) {
            throw lexError("Unterminated string literal", startLine, startCol);
        }

        advance(); // closing quote
        return new Token(TokenType.STRING_LITERAL, sb.toString(), startLine, startCol);
    }

    private Token scanIdentifierOrKeyword() {
        int startLine = line;
        int startCol = col;
        StringBuilder sb = new StringBuilder();

        while (pos < input.length() && (Character.isLetterOrDigit(peek()) || peek() == '_')) {
            sb.append(advance());
        }

        String text = sb.toString();
        TokenType type = KEYWORDS.getOrDefault(text, TokenType.IDENTIFIER);
        return new Token(type, text, startLine, startCol);
    }

    private CompileError lexError(String message, int atLine, int atCol) {
        return new CompileError(CompileError.Stage.LEXICAL, message, atLine, atCol);
    }

    private char peek() {
        if (pos >= input.length()) return '\0';
        return input.charAt(pos);
    }

    private char peekAhead(int n) {
        if (pos + n >= input.length()) return '\0';
        return input.charAt(pos + n);
    }

    private char advance() {
        char c = peek();
        pos++;
        if (c == '\n') {
            line++;
            col = 1;
        } else {
            col++;
        }
        return c;
    }
}
