import java.util.List;

/*
 * Grammar handled by this prototype (subset — extended in Phase 2):
 *
 *   statement  -> "LET" IDENTIFIER "=" expr ";"
 *   expr       -> term (("+" | "-") term)*
 *   term       -> factor (("*" | "/") factor)*
 *   factor     -> INTEGER | IDENTIFIER
 */
public class Parser {
    private final List<Token> tokens;
    private int pos = 0;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public AssignStmt parseStatement() {
        expect(TokenType.LET);
        Token idToken = expect(TokenType.IDENTIFIER);
        expect(TokenType.ASSIGN);
        ASTNode expr = parseExpr();
        expect(TokenType.SEMICOLON);
        return new AssignStmt(idToken.text, expr);
    }

    // Parses a whole program: zero or more statements until EOF.
    // This is what lets the prototype handle multi-line pseudocode,
    // not just a single statement.
    public java.util.List<AssignStmt> parseProgram() {
        java.util.List<AssignStmt> statements = new java.util.ArrayList<>();
        while (peek().type != TokenType.EOF) {
            statements.add(parseStatement());
        }
        return statements;
    }

    private ASTNode parseExpr() {
        ASTNode left = parseTerm();
        while (peek().type == TokenType.PLUS || peek().type == TokenType.MINUS) {
            Token op = advance();
            ASTNode right = parseTerm();
            left = new BinaryExpr(left, op.text, right);
        }
        return left;
    }

    private ASTNode parseTerm() {
        ASTNode left = parseFactor();
        while (peek().type == TokenType.STAR || peek().type == TokenType.SLASH) {
            Token op = advance();
            ASTNode right = parseFactor();
            left = new BinaryExpr(left, op.text, right);
        }
        return left;
    }

    private ASTNode parseFactor() {
        Token t = peek();
        if (t.type == TokenType.INTEGER) {
            advance();
            return new IntegerLiteral(t.text);
        }
        if (t.type == TokenType.IDENTIFIER) {
            advance();
            return new IdentifierExpr(t.text);
        }
        throw new RuntimeException(
                "Syntax error at position " + t.position + ": expected INTEGER or IDENTIFIER, found " + t);
    }

    private Token peek() {
        return tokens.get(pos);
    }

    private Token advance() {
        return tokens.get(pos++);
    }

    private Token expect(TokenType type) {
        Token t = peek();
        if (t.type != type) {
            throw new RuntimeException(
                    "Syntax error at position " + t.position + ": expected " + type + ", found " + t);
        }
        return advance();
    }
}
