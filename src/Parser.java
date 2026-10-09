import java.util.ArrayList;
import java.util.List;

public class Parser {
    private final List<Token> tokens;
    private int current = 0;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public Block parseProgram() {
        List<ASTNode> statements = new ArrayList<>();
        while (!isAtEnd()) {
            statements.add(parseStatement());
        }
        return new Block(statements);
    }

    private ASTNode parseStatement() {
        Token start = peek();
        if (match(TokenType.LET)) return parseLetStmt(true);
        if (match(TokenType.IF)) return parseIfStmt().at(start);
        if (match(TokenType.WHILE)) return parseWhileStmt().at(start);
        if (match(TokenType.FOR)) return parseForStmt().at(start);
        if (match(TokenType.FUNCTION)) return parseFunctionDecl().at(start);
        if (match(TokenType.RETURN)) return parseReturnStmt().at(start);
        if (match(TokenType.PRINT)) return parsePrintStmt().at(start);
        if (match(TokenType.INPUT)) return parseInputStmt().at(start);
        if (check(TokenType.LBRACE)) return parseBlock();

        // An identifier starts either an assignment (x = ...;) or a call statement (f(...);)
        if (check(TokenType.IDENTIFIER)) {
            if (peekNext().type == TokenType.LPAREN) {
                ASTNode call = parseExpr();
                expect(TokenType.SEMICOLON, "Expected ';' after function call statement");
                return call;
            }
            return parseAssignStmt(true);
        }

        throw error(peek(), "Expected a statement (LET, IF, WHILE, FOR, FUNCTION, RETURN, PRINT, INPUT, an assignment or a call)");
    }

    // LET name [: type] = expr ;      (type is optional - if omitted it is inferred from the expression)
    private ASTNode parseLetStmt(boolean needSemicolon) {
        Token nameToken = expect(TokenType.IDENTIFIER, "Expected identifier after LET");
        String type = null;
        if (match(TokenType.COLON)) {
            type = parseType();
        }
        expect(TokenType.ASSIGN, "Expected '=' in LET declaration");
        ASTNode initializer = parseExpr();
        if (needSemicolon) expect(TokenType.SEMICOLON, "Expected ';' after LET statement");
        return new AssignStmt(nameToken.text, type, initializer, true).at(nameToken);
    }

    private ASTNode parseAssignStmt(boolean needSemicolon) {
        Token nameToken = expect(TokenType.IDENTIFIER, "Expected identifier");
        if (!check(TokenType.ASSIGN)) {
            String hint = Lexer.keywordSuggestion(nameToken.text);
            String msg = "Expected '=' after '" + nameToken.text + "' (an assignment looks like: x = 5;)";
            if (hint != null) msg = "Unknown statement '" + nameToken.text + "' - did you mean '" + hint + "'? Keywords are case-sensitive";
            throw error(peek(), msg);
        }
        advance(); // '='
        ASTNode value = parseExpr();
        if (needSemicolon) expect(TokenType.SEMICOLON, "Expected ';' after assignment");
        return new AssignStmt(nameToken.text, null, value, false).at(nameToken);
    }

    // one of: int | float | boolean | string
    private String parseType() {
        Token t = peek();
        if (match(TokenType.TYPE_INT, TokenType.TYPE_FLOAT, TokenType.TYPE_BOOLEAN, TokenType.TYPE_STRING)) {
            return t.text;
        }
        throw error(t, "Expected a type (int, float, boolean or string)");
    }

    private ASTNode parseIfStmt() {
        expect(TokenType.LPAREN, "Expected '(' after IF");
        ASTNode condition = parseExpr();
        expect(TokenType.RPAREN, "Expected ')' after IF condition");
        ASTNode thenBlock = parseBlock();
        ASTNode elseBlock = null;
        if (check(TokenType.ELSE)) {
            advance();
            if (check(TokenType.IF)) {
                Token ifTok = advance();
                elseBlock = parseIfStmt().at(ifTok); // ELSE IF chain
            } else {
                elseBlock = parseBlock();
            }
        }
        return new IfStmt(condition, thenBlock, elseBlock);
    }

    private ASTNode parseWhileStmt() {
        expect(TokenType.LPAREN, "Expected '(' after WHILE");
        ASTNode condition = parseExpr();
        expect(TokenType.RPAREN, "Expected ')' after WHILE condition");
        ASTNode body = parseBlock();
        return new WhileStmt(condition, body);
    }

    private ASTNode parseForStmt() {
        expect(TokenType.LPAREN, "Expected '(' after FOR");
        ASTNode init;
        if (match(TokenType.LET)) init = parseLetStmt(false);
        else init = parseAssignStmt(false);
        expect(TokenType.SEMICOLON, "Expected ';' after FOR init clause");
        ASTNode condition = parseExpr();
        expect(TokenType.SEMICOLON, "Expected ';' after FOR condition clause");
        ASTNode update = parseAssignStmt(false);
        expect(TokenType.RPAREN, "Expected ')' after FOR clauses");
        ASTNode body = parseBlock();
        return new ForStmt(init, condition, update, body);
    }

    private Block parseBlock() {
        Token open = expect(TokenType.LBRACE, "Expected '{' to start block");
        List<ASTNode> statements = new ArrayList<>();
        while (!check(TokenType.RBRACE) && !isAtEnd()) {
            statements.add(parseStatement());
        }
        if (isAtEnd()) {
            throw error(peek(), "Expected '}' to close the block opened at line " + open.line + ", column " + open.column);
        }
        expect(TokenType.RBRACE, "Expected '}' to close block");
        return new Block(statements).at(open);
    }

    // FUNCTION name ( params ) [: returnType] { body }    - no return type means the function returns nothing (void)
    private ASTNode parseFunctionDecl() {
        Token nameToken = expect(TokenType.IDENTIFIER, "Expected function name");
        expect(TokenType.LPAREN, "Expected '(' after function name");
        List<FunctionDecl.Param> params = new ArrayList<>();
        if (!check(TokenType.RPAREN)) {
            do {
                Token pName = expect(TokenType.IDENTIFIER, "Expected parameter name");
                expect(TokenType.COLON, "Expected ':' after parameter name");
                String pType = parseType();
                params.add(new FunctionDecl.Param(pName.text, pType));
            } while (match(TokenType.COMMA));
        }
        expect(TokenType.RPAREN, "Expected ')' after parameters");
        String returnType = "void";
        if (match(TokenType.COLON)) {
            if (check(TokenType.IDENTIFIER) && peek().text.equals("void")) {
                advance();
            } else {
                returnType = parseType();
            }
        }
        Block body = parseBlock();
        return new FunctionDecl(nameToken.text, params, returnType, body);
    }

    private ASTNode parseReturnStmt() {
        ASTNode value = check(TokenType.SEMICOLON) ? null : parseExpr();
        expect(TokenType.SEMICOLON, "Expected ';' after RETURN");
        return new ReturnStmt(value);
    }

    private ASTNode parsePrintStmt() {
        expect(TokenType.LPAREN, "Expected '(' after PRINT");
        ASTNode expr = parseExpr();
        expect(TokenType.RPAREN, "Expected ')' after PRINT expression");
        expect(TokenType.SEMICOLON, "Expected ';' after PRINT");
        return new PrintStmt(expr);
    }

    private ASTNode parseInputStmt() {
        expect(TokenType.LPAREN, "Expected '(' after INPUT");
        Token id = expect(TokenType.IDENTIFIER, "Expected identifier inside INPUT");
        expect(TokenType.RPAREN, "Expected ')' after INPUT identifier");
        expect(TokenType.SEMICOLON, "Expected ';' after INPUT");
        return new InputStmt(id.text);
    }

    // ---- Expression precedence chain (lowest to highest) ----
    //  logicOr -> logicAnd -> equality -> relational -> addExpr -> term -> factor
    private ASTNode parseExpr() { return parseLogicOr(); }

    private ASTNode parseLogicOr() {
        ASTNode expr = parseLogicAnd();
        while (match(TokenType.OR)) {
            Token op = previous();
            ASTNode right = parseLogicAnd();
            expr = new LogicalExpr(expr, op.text, right).at(op);
        }
        return expr;
    }

    private ASTNode parseLogicAnd() {
        ASTNode expr = parseEquality();
        while (match(TokenType.AND)) {
            Token op = previous();
            ASTNode right = parseEquality();
            expr = new LogicalExpr(expr, op.text, right).at(op);
        }
        return expr;
    }

    private ASTNode parseEquality() {
        ASTNode expr = parseRelational();
        while (match(TokenType.EQUAL, TokenType.NOT_EQUAL)) {
            Token op = previous();
            ASTNode right = parseRelational();
            expr = new RelationalExpr(expr, op.text, right).at(op);
        }
        return expr;
    }

    private ASTNode parseRelational() {
        ASTNode expr = parseAddExpr();
        while (match(TokenType.LESS_THAN, TokenType.GREATER_THAN, TokenType.LESS_EQUAL, TokenType.GREATER_EQUAL)) {
            Token op = previous();
            ASTNode right = parseAddExpr();
            expr = new RelationalExpr(expr, op.text, right).at(op);
        }
        return expr;
    }

    private ASTNode parseAddExpr() {
        ASTNode expr = parseTerm();
        while (match(TokenType.PLUS, TokenType.MINUS)) {
            Token op = previous();
            ASTNode right = parseTerm();
            expr = new BinaryExpr(expr, op.text, right).at(op);
        }
        return expr;
    }

    private ASTNode parseTerm() {
        ASTNode expr = parseFactor();
        while (match(TokenType.STAR, TokenType.SLASH)) {
            Token op = previous();
            ASTNode right = parseFactor();
            expr = new BinaryExpr(expr, op.text, right).at(op);
        }
        return expr;
    }

    private ASTNode parseFactor() {
        Token start = peek();
        if (match(TokenType.MINUS)) {
            return new UnaryExpr("-", parseFactor()).at(start);
        }
        if (match(TokenType.INTEGER_LITERAL)) {
            return new IntegerLiteral(Integer.parseInt(previous().text)).at(start);
        }
        if (match(TokenType.FLOAT_LITERAL)) {
            return new FloatLiteral(Double.parseDouble(previous().text)).at(start);
        }
        if (match(TokenType.STRING_LITERAL)) {
            return new StringLiteral(previous().text).at(start);
        }
        if (match(TokenType.BOOLEAN_LITERAL)) {
            return new BooleanLiteral(Boolean.parseBoolean(previous().text)).at(start);
        }
        if (match(TokenType.IDENTIFIER)) {
            Token id = previous();
            if (match(TokenType.LPAREN)) {
                List<ASTNode> args = new ArrayList<>();
                if (!check(TokenType.RPAREN)) {
                    do {
                        args.add(parseExpr());
                    } while (match(TokenType.COMMA));
                }
                expect(TokenType.RPAREN, "Expected ')' after call arguments");
                return new CallExpr(id.text, args).at(id);
            }
            return new IdentifierExpr(id.text).at(id);
        }
        if (match(TokenType.LPAREN)) {
            ASTNode expr = parseExpr();
            expect(TokenType.RPAREN, "Expected ')' after expression");
            return expr;
        }
        throw error(peek(), "Expected an expression");
    }

    private boolean match(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }
        return false;
    }

    private boolean check(TokenType type) {
        if (isAtEnd()) return false;
        return peek().type == type;
    }

    private Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    private boolean isAtEnd() { return peek().type == TokenType.EOF; }
    private Token peek() { return tokens.get(current); }
    private Token peekNext() { return current + 1 < tokens.size() ? tokens.get(current + 1) : tokens.get(tokens.size() - 1); }
    private Token previous() { return tokens.get(current - 1); }

    private Token expect(TokenType type, String message) {
        if (check(type)) return advance();
        throw error(peek(), message);
    }

    private CompileError error(Token token, String message) {
        String found = token.type == TokenType.EOF ? "end of input" : "'" + token.text + "'";
        return new CompileError(CompileError.Stage.SYNTAX, message + " (found " + found + ")", token.line, token.column);
    }
}
