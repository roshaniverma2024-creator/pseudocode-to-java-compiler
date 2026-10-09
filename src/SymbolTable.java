import java.util.*;

// Scope-stack symbol table.
//   enterScope(name) / exitScope()  push and pop a scope when a block, loop or function body starts/ends
//   declare(name, type)             inserts into the innermost scope (false if already declared there)
//   lookup(name)                    searches from the innermost scope outward to the global scope
// A separate function table maps function name -> signature, so calls are checked independently
// of variable scoping. Every declaration is also recorded in 'history' and 'trace' so the
// compiler can show how the scope stack grew and shrank.
public class SymbolTable {
    private final Deque<Map<String, String>> scopeStack = new ArrayDeque<>();
    private final Deque<String> scopeNames = new ArrayDeque<>();
    private final Map<String, FunctionSignature> functionTable = new LinkedHashMap<>();
    private final List<String[]> history = new ArrayList<>();   // {name, type, scope, depth}
    private final List<String> trace = new ArrayList<>();

    public static class FunctionSignature {
        public final String name;
        public final List<String> paramNames;
        public final List<String> paramTypes;
        public final String returnType;

        public FunctionSignature(String name, List<String> paramNames, List<String> paramTypes, String returnType) {
            this.name = name;
            this.paramNames = paramNames;
            this.paramTypes = paramTypes;
            this.returnType = returnType;
        }
    }

    public SymbolTable() {
        enterScope("global");
    }

    public void enterScope(String name) {
        scopeStack.push(new HashMap<>());
        scopeNames.push(name);
        trace.add("  ".repeat(depth() - 1) + "enter scope '" + name + "'   (depth " + depth() + ")");
    }

    public void exitScope() {
        if (scopeStack.size() > 1) {
            trace.add("  ".repeat(depth() - 1) + "exit scope '" + scopeNames.peek() + "'");
            scopeStack.pop();
            scopeNames.pop();
        }
    }

    public int depth() {
        return scopeStack.size();
    }

    // returns false if 'name' already exists in the innermost scope (a redeclaration)
    public boolean declare(String name, String type) {
        if (scopeStack.peek().containsKey(name)) {
            return false;
        }
        scopeStack.peek().put(name, type);
        history.add(new String[]{name, type, scopeNames.peek(), String.valueOf(depth())});
        trace.add("  ".repeat(depth()) + "declare " + name + " : " + type);
        return true;
    }

    public String lookup(String name) {
        for (Map<String, String> scope : scopeStack) {
            if (scope.containsKey(name)) {
                return scope.get(name);
            }
        }
        return null;
    }

    public boolean declareFunction(String name, List<String> paramNames, List<String> paramTypes, String returnType) {
        if (functionTable.containsKey(name)) return false;
        functionTable.put(name, new FunctionSignature(name, paramNames, paramTypes, returnType));
        trace.add("  ".repeat(depth()) + "declare function " + name + "(" + String.join(", ", paramTypes) + ") : " + returnType);
        return true;
    }

    public FunctionSignature lookupFunction(String name) {
        return functionTable.get(name);
    }

    public List<String[]> history() { return history; }
    public List<String> trace() { return trace; }
    public Collection<FunctionSignature> functions() { return functionTable.values(); }
}
