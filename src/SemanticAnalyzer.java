import java.util.ArrayList;
import java.util.List;

// Walks the AST once, in source order, and performs:
//   - scope checking   (use before declaration, redeclaration in the same scope)
//   - type checking    (declarations, assignments, operators, conditions, calls, returns)
//   - function checks  (argument count/types, return type, missing RETURN, unreachable code)
// Every error is a CompileError carrying the line/column of the offending node.
// Types are plain strings: "int", "float", "boolean", "string", "void".
public class SemanticAnalyzer {
    private final SymbolTable symbolTable;
    private FunctionDecl currentFunction = null;

    public SemanticAnalyzer() {
        this(new SymbolTable());
    }

    public SemanticAnalyzer(SymbolTable symbolTable) {
        this.symbolTable = symbolTable;
    }

    public SymbolTable getSymbolTable() {
        return symbolTable;
    }

    public void analyze(Block program) {
        // Pass 1: register every top-level function signature so calls may appear before the declaration
        // (and so mutually recursive functions work).
        for (ASTNode stmt : program.statements) {
            if (stmt instanceof FunctionDecl func) {
                List<String> names = new ArrayList<>();
                List<String> types = new ArrayList<>();
                for (FunctionDecl.Param p : func.parameters) {
                    names.add(p.name);
                    types.add(p.type);
                }
                if (!symbolTable.declareFunction(func.name, names, types, func.returnType)) {
                    throw error(func, "Function '" + func.name + "' is already declared");
                }
            }
        }
        // Pass 2: check the statements. The program itself lives directly in the global scope.
        visitStatements(program.statements);
    }

    // ---- statements ----

    private void visitStatements(List<ASTNode> statements) {
        boolean returned = false;
        for (ASTNode stmt : statements) {
            if (returned) throw error(stmt, "Unreachable statement: it follows a RETURN that always executes");
            visit(stmt);
            if (alwaysReturns(stmt)) returned = true;
        }
    }

    private void visitBlock(ASTNode blockNode, String scopeName) {
        Block block = (Block) blockNode;
        symbolTable.enterScope(scopeName);
        visitStatements(block.statements);
        symbolTable.exitScope();
    }

    private String visit(ASTNode node) {
        String type = visitInner(node);
        node.resolvedType = type;
        return type;
    }

    private String visitInner(ASTNode node) {
        if (node instanceof Block block) {
            visitBlock(block, "block");
            return null;
        }
        if (node instanceof AssignStmt assign) {
            String exprType = valueType(assign.expression);
            if (assign.declaration) {
                String varType = assign.declaredType != null ? assign.declaredType : exprType;
                if (assign.declaredType != null) {
                    checkTypeMatch(assign.declaredType, exprType, "declaration of '" + assign.identifier + "'", assign.expression);
                }
                if (!symbolTable.declare(assign.identifier, varType)) {
                    throw error(assign, "Variable '" + assign.identifier + "' is already declared in this scope");
                }
                return varType;
            } else {
                String existingType = symbolTable.lookup(assign.identifier);
                if (existingType == null) {
                    throw error(assign, "Variable '" + assign.identifier + "' used before declaration");
                }
                checkTypeMatch(existingType, exprType, "assignment to '" + assign.identifier + "'", assign.expression);
                return existingType;
            }
        }
        if (node instanceof IfStmt ifStmt) {
            requireBoolean(ifStmt.condition, "IF");
            visitBlock(ifStmt.thenBlock, "if-then");
            if (ifStmt.elseBlock != null) {
                if (ifStmt.elseBlock instanceof IfStmt) visit(ifStmt.elseBlock); // ELSE IF chain
                else visitBlock(ifStmt.elseBlock, "if-else");
            }
            return null;
        }
        if (node instanceof WhileStmt whileStmt) {
            requireBoolean(whileStmt.condition, "WHILE");
            visitBlock(whileStmt.body, "while-body");
            return null;
        }
        if (node instanceof ForStmt forStmt) {
            symbolTable.enterScope("for");
            visit(forStmt.init);
            requireBoolean(forStmt.condition, "FOR");
            visit(forStmt.update);
            visitBlock(forStmt.body, "for-body");
            symbolTable.exitScope();
            return null;
        }
        if (node instanceof FunctionDecl func) {
            if (currentFunction != null || symbolTable.depth() != 1) {
                throw error(func, "Function declarations are only allowed at the top level of the program");
            }
            symbolTable.enterScope("function " + func.name);
            for (FunctionDecl.Param p : func.parameters) {
                if (!symbolTable.declare(p.name, p.type)) {
                    throw error(func, "Duplicate parameter '" + p.name + "' in function '" + func.name + "'");
                }
            }
            currentFunction = func;
            // parameters and the body's top-level statements share one scope
            visitStatements(((Block) func.body).statements);
            if (!func.returnType.equals("void") && !alwaysReturns(func.body)) {
                throw error(func, "Function '" + func.name + "' must return a '" + func.returnType + "' value on every path (missing RETURN)");
            }
            currentFunction = null;
            symbolTable.exitScope();
            return null;
        }
        if (node instanceof ReturnStmt ret) {
            if (currentFunction == null) {
                throw error(ret, "RETURN is only allowed inside a function");
            }
            String expected = currentFunction.returnType;
            if (ret.value == null) {
                if (!expected.equals("void")) {
                    throw error(ret, "RETURN needs a value: function '" + currentFunction.name + "' returns '" + expected + "'");
                }
            } else {
                if (expected.equals("void")) {
                    throw error(ret, "Function '" + currentFunction.name + "' returns no value, so RETURN cannot have an expression");
                }
                checkTypeMatch(expected, valueType(ret.value), "RETURN in function '" + currentFunction.name + "'", ret.value);
            }
            return null;
        }
        if (node instanceof PrintStmt print) {
            valueType(print.expression);
            return null;
        }
        if (node instanceof InputStmt input) {
            String t = symbolTable.lookup(input.identifier);
            if (t == null) {
                throw error(input, "INPUT variable '" + input.identifier + "' used before declaration");
            }
            return t;
        }

        // ---- expressions ----
        if (node instanceof BinaryExpr binary) {
            String leftT = valueType(binary.left);
            String rightT = valueType(binary.right);
            if (binary.operator.equals("+") && (leftT.equals("string") || rightT.equals("string"))) return "string";
            if (isNumeric(leftT) && isNumeric(rightT)) {
                return (leftT.equals("float") || rightT.equals("float")) ? "float" : "int";
            }
            throw error(binary, "Operator '" + binary.operator + "' cannot be applied to '" + leftT + "' and '" + rightT + "'");
        }
        if (node instanceof RelationalExpr rel) {
            String leftT = valueType(rel.left);
            String rightT = valueType(rel.right);
            boolean equality = rel.operator.equals("==") || rel.operator.equals("!=");
            if (equality) {
                if (!leftT.equals(rightT) && !(isNumeric(leftT) && isNumeric(rightT))) {
                    throw error(rel, "Cannot compare '" + leftT + "' with '" + rightT + "' using '" + rel.operator + "'");
                }
            } else if (!(isNumeric(leftT) && isNumeric(rightT))) {
                throw error(rel, "Operator '" + rel.operator + "' needs numeric operands but found '" + leftT + "' and '" + rightT + "'");
            }
            return "boolean";
        }
        if (node instanceof LogicalExpr log) {
            String leftT = valueType(log.left);
            String rightT = valueType(log.right);
            if (!"boolean".equals(leftT) || !"boolean".equals(rightT)) {
                throw error(log, "Operator '" + log.operator + "' needs boolean operands but found '" + leftT + "' and '" + rightT + "'");
            }
            return "boolean";
        }
        if (node instanceof UnaryExpr un) {
            String t = valueType(un.expr);
            if (!isNumeric(t)) throw error(un, "Unary '-' needs a numeric operand but found '" + t + "'");
            return t;
        }
        if (node instanceof CallExpr call) {
            SymbolTable.FunctionSignature sig = symbolTable.lookupFunction(call.functionName);
            if (sig == null) {
                String hint = Lexer.keywordSuggestion(call.functionName);
                throw error(call, "Function '" + call.functionName + "' is not declared"
                        + (hint != null ? " (did you mean the keyword '" + hint + "'? Keywords are case-sensitive)" : ""));
            }
            if (call.arguments.size() != sig.paramTypes.size()) {
                throw error(call, "Function '" + call.functionName + "' expects " + sig.paramTypes.size()
                        + " argument(s) but got " + call.arguments.size());
            }
            for (int i = 0; i < call.arguments.size(); i++) {
                String argType = valueType(call.arguments.get(i));
                checkTypeMatch(sig.paramTypes.get(i), argType, "argument " + (i + 1) + " of '" + call.functionName + "'", call.arguments.get(i));
            }
            return sig.returnType;
        }
        if (node instanceof IdentifierExpr id) {
            String t = symbolTable.lookup(id.name);
            if (t == null) throw error(id, "Variable '" + id.name + "' used before declaration");
            return t;
        }
        if (node instanceof IntegerLiteral) return "int";
        if (node instanceof FloatLiteral) return "float";
        if (node instanceof StringLiteral) return "string";
        if (node instanceof BooleanLiteral) return "boolean";

        throw new IllegalStateException("SemanticAnalyzer does not know node type " + node.getClass().getSimpleName());
    }

    // ---- helpers ----

    // the type of an expression that must produce a value (a call to a void function is not allowed here)
    private String valueType(ASTNode node) {
        String t = visit(node);
        if ("void".equals(t)) {
            throw error(node, "A function that returns no value cannot be used as a value");
        }
        return t;
    }

    private void requireBoolean(ASTNode condition, String what) {
        String t = valueType(condition);
        if (!"boolean".equals(t)) {
            throw error(condition, what + " condition must be boolean but found '" + t + "'");
        }
    }

    // true if every path through this statement ends in a RETURN
    private boolean alwaysReturns(ASTNode node) {
        if (node instanceof ReturnStmt) return true;
        if (node instanceof Block block) {
            for (ASTNode s : block.statements) if (alwaysReturns(s)) return true;
            return false;
        }
        if (node instanceof IfStmt ifStmt) {
            return ifStmt.elseBlock != null && alwaysReturns(ifStmt.thenBlock) && alwaysReturns(ifStmt.elseBlock);
        }
        return false;
    }

    private boolean isNumeric(String type) { return "int".equals(type) || "float".equals(type); }

    private void checkTypeMatch(String expected, String actual, String context, ASTNode where) {
        if (expected.equals(actual)) return;
        if ("float".equals(expected) && "int".equals(actual)) return; // implicit widening int -> float
        throw error(where, "Type mismatch in " + context + ": expected '" + expected + "' but found '" + actual + "'");
    }

    private CompileError error(ASTNode node, String message) {
        return new CompileError(CompileError.Stage.SEMANTIC, message, node.line, node.column);
    }
}
