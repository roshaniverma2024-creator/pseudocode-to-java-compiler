import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

// Prototype-level symbol table: tracks which identifiers have been declared
// via LET, so the semantic pass can catch use-before-declaration.
// Phase 2 extends this with types and nested scopes.
public class SymbolTable {
    private final Set<String> declared = new HashSet<>();

    public void declare(String name) {
        declared.add(name);
    }

    public boolean isDeclared(String name) {
        return declared.contains(name);
    }

    // Returns declared names sorted alphabetically, purely for readable printing.
    public Set<String> declaredNames() {
        return new TreeSet<>(declared);
    }
}
