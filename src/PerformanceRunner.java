import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Small reproducible benchmark for compiler stages and generated Java compile/run. */
public class PerformanceRunner {
    private static final String SAMPLE="LET n : int = 10; FUNCTION square(x : int) : int { RETURN x * x; } PRINT(square(n));";
    private static long pipeline(){long start=System.nanoTime();List<Token> t=new Lexer(SAMPLE).tokenize();Block b=new Parser(t).parseProgram();SymbolTable s=new SymbolTable();new SemanticAnalyzer(s).analyze(b);new CodeGenerator().generateProgram(b,"Bench",s);return System.nanoTime()-start;}
    private static long endToEnd() throws Exception{long start=System.nanoTime();Block b=new Parser(new Lexer(SAMPLE).tokenize()).parseProgram();SymbolTable s=new SymbolTable();new SemanticAnalyzer(s).analyze(b);String java=new CodeGenerator().generateProgram(b,"GeneratedProgram",s);Executor.Result r=Executor.runCaptured("GeneratedProgram",java,"",20);if(!r.compiled||r.exitCode!=0)throw new IllegalStateException("Generated program failed: "+r.compilerLog+" "+r.stderr);return System.nanoTime()-start;}
    private static void report(String label,List<Long> values){Collections.sort(values);double mean=values.stream().mapToLong(Long::longValue).average().orElse(0)/1_000_000.0;double median=values.get(values.size()/2)/1_000_000.0;System.out.printf("%-34s n=%d mean=%.3f ms median=%.3f ms min=%.3f ms max=%.3f ms%n",label,values.size(),mean,median,values.get(0)/1e6,values.get(values.size()-1)/1e6);}
    public static void main(String[] args) throws Exception{int n=args.length>0?Integer.parseInt(args[0]):10;for(int i=0;i<3;i++){pipeline();endToEnd();}List<Long> p=new ArrayList<>(),e=new ArrayList<>();for(int i=0;i<n;i++){p.add(pipeline());e.add(endToEnd());}System.out.println("JDK "+System.getProperty("java.version")+" | warm-up=3 | measured iterations="+n);report("Lexer + parser + semantic + codegen",p);report("Full pipeline + javac + execution",e);}
}
