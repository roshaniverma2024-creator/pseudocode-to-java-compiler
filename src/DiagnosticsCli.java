import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Collects independent semantic errors by rechecking the program after removing the failing top-level construct. */
public class DiagnosticsCli {
    public static List<CompileError> analyze(String source) {
        Block parsed=new Parser(new Lexer(source).tokenize()).parseProgram();
        List<ASTNode> remaining=new ArrayList<>(parsed.statements); List<CompileError> errors=new ArrayList<>();
        for(int attempts=0; attempts<parsed.statements.size() && !remaining.isEmpty(); attempts++){
            try { new SemanticAnalyzer().analyze(new Block(new ArrayList<>(remaining))); break; }
            catch(CompileError e){ errors.add(e); ASTNode failed=remaining.stream().filter(n->n.line<=e.line).max(Comparator.comparingInt(n->n.line)).orElse(remaining.get(0)); remaining.remove(failed); }
        }
        return errors;
    }
    public static void main(String[] args) throws Exception {
        if(args.length==0){System.err.println("Usage: java DiagnosticsCli <program.txt>");System.exit(2);}
        String source=Files.readString(Path.of(args[0]), StandardCharsets.UTF_8);
        try { List<CompileError> errors=analyze(source); if(errors.isEmpty()) System.out.println("No semantic errors found."); else {System.out.println(errors.size()+" semantic error(s):");for(CompileError e:errors)System.out.println("  "+e.format());System.out.println("Recovery is top-level: errors inside one top-level construct are reported once.");} }
        catch(CompileError e){System.out.println("Recovery could not continue past the lexical or syntax error: "+e.format());System.exit(1);}
    }
}
