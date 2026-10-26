import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;

/** Live Swing view of the compiler stages. Each edit refreshes tokens, AST, scope/type data and Java. */
public class CompilerVisualizer {
    private final JFrame frame = new JFrame("Pseudocode to Java Compiler - Phase 3");
    private final JTextArea source = area(18, 42);
    private final JTextArea tokens = area(18, 30);
    private final JTextArea ast = area(18, 34);
    private final JTextArea symbols = area(18, 32);
    private final JTextArea java = area(18, 38);
    private final JTextArea status = area(6, 40);
    private String lastValidJava;
    private Timer refreshTimer;

    private static JTextArea area(int rows, int cols) {
        JTextArea a = new JTextArea(rows, cols); a.setEditable(false); a.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        a.setLineWrap(false); return a;
    }
    private CompilerVisualizer(String initial) {
        source.setEditable(true); source.setText(initial);
        JPanel grid = new JPanel(new GridLayout(2, 2, 6, 6));
        grid.add(panel("Tokens", tokens)); grid.add(panel("AST", ast));
        grid.add(panel("Symbol table and scopes", symbols)); grid.add(panel("Generated Java", java));
        JButton open = new JButton("Open file"); JButton save = new JButton("Save source");
        JButton run = new JButton("Compile and run");
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT)); buttons.add(open); buttons.add(save); buttons.add(run);
        open.addActionListener(e -> openFile()); save.addActionListener(e -> saveFile()); run.addActionListener(e -> runProgram());
        JPanel left = new JPanel(new BorderLayout(4,4)); left.add(new JLabel("Pseudocode source (updates the compiler stages as you type)"), BorderLayout.NORTH); left.add(new JScrollPane(source), BorderLayout.CENTER);
        JSplitPane top = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, grid); top.setResizeWeight(0.3);
        JPanel bottom = new JPanel(new BorderLayout(4,4)); bottom.add(new JLabel("Diagnostics and execution"), BorderLayout.NORTH); bottom.add(new JScrollPane(status), BorderLayout.CENTER);
        frame.setLayout(new BorderLayout(6,6)); frame.add(buttons, BorderLayout.NORTH); frame.add(top, BorderLayout.CENTER); frame.add(bottom, BorderLayout.SOUTH);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE); frame.setSize(1440, 900); frame.setLocationByPlatform(true);
        refreshTimer = new Timer(350, e -> refresh()); refreshTimer.setRepeats(false);
        source.getDocument().addDocumentListener(new DocumentListener() { public void insertUpdate(DocumentEvent e){ schedule(); } public void removeUpdate(DocumentEvent e){ schedule(); } public void changedUpdate(DocumentEvent e){ schedule(); } });
        refresh(); frame.setVisible(true);
    }
    private static JPanel panel(String title, JTextArea text) { JPanel p=new JPanel(new BorderLayout()); p.add(new JLabel(title),BorderLayout.NORTH); p.add(new JScrollPane(text),BorderLayout.CENTER); return p; }
    private void schedule(){ refreshTimer.restart(); }
    private void refresh(){
        lastValidJava=null; String text=source.getText(); status.setText("Live analysis: parsing and checking current source...");
        try {
            List<Token> ts=new Lexer(text).tokenize(); StringBuilder tb=new StringBuilder(); for(Token t:ts) tb.append(String.format("%4d:%-3d %-20s %s%n",t.line,t.column,t.type,t.text.replace("\n","\\n"))); tokens.setText(tb.toString());
            Block program=new Parser(ts).parseProgram(); ByteArrayOutputStream bytes=new ByteArrayOutputStream(); AstPrinter.print(program,new PrintStream(bytes)); ast.setText(bytes.toString(StandardCharsets.UTF_8));
            SymbolTable table=new SymbolTable(); new SemanticAnalyzer(table).analyze(program); StringBuilder sb=new StringBuilder(); for(String e:table.trace()) sb.append(e).append('\n'); sb.append("\nVariables\n"); for(String[] r:table.history()) sb.append(String.join(" | ",r)).append('\n'); sb.append("\nFunctions\n"); for(SymbolTable.FunctionSignature f:table.functions()) sb.append(f.name).append(" : ").append(f.returnType).append(" ( ").append(String.join(", ",f.paramTypes)).append(" )\n"); symbols.setText(sb.toString());
            lastValidJava=new CodeGenerator().generateProgram(program,"GeneratedProgram",table); java.setText(lastValidJava); status.setText("All stages valid. Press Compile and run to execute the generated Java.\nTokens: "+ts.size()+"   Top-level statements: "+program.statements.size());
        } catch(CompileError ex) { StringBuilder message=new StringBuilder(); message.append(ex.stage.label).append(" error at ").append(ex.line).append(":").append(ex.column).append(" - ").append(ex.getMessage()); if(ex.stage==CompileError.Stage.SEMANTIC){ try { List<CompileError> all=DiagnosticsCli.analyze(text); if(all.size()>1){message.setLength(0);message.append(all.size()).append(" independent semantic errors:\n");for(CompileError d:all)message.append("  ").append(d.format()).append("\n");message.append("\nRecovery applies across top-level constructs.");} } catch(CompileError ignored){} } message.append("\n\nThe affected downstream panes show the last successful stage or are empty until the source is valid."); status.setText(message.toString()); }
        catch(Exception ex){ status.setText("Visualizer error: "+ex); }
    }
    private void runProgram(){ refresh(); if(lastValidJava==null){JOptionPane.showMessageDialog(frame,"Fix the compiler error before running.","Cannot run",JOptionPane.WARNING_MESSAGE);return;} status.setText("Compiling and running generated Java..."); String code=lastValidJava; new SwingWorker<Executor.Result,Void>(){protected Executor.Result doInBackground() throws Exception{return Executor.runCaptured("GeneratedProgram",code,"",20);} protected void done(){try{Executor.Result r=get();status.setText(r.compiled?(r.timedOut?"Runtime timed out after 20 seconds.\n":"Exit code: "+r.exitCode+"\n")+"Program output:\n"+r.stdout+(r.stderr.isBlank()?"":"\nErrors:\n"+r.stderr):"Generated Java did not compile:\n"+r.compilerLog);}catch(Exception e){status.setText("Execution failed: "+e);}}}.execute();}
    private void openFile(){JFileChooser c=new JFileChooser();if(c.showOpenDialog(frame)==JFileChooser.APPROVE_OPTION)try{source.setText(Files.readString(c.getSelectedFile().toPath(),StandardCharsets.UTF_8));}catch(IOException e){showError(e);}}
    private void saveFile(){JFileChooser c=new JFileChooser();if(c.showSaveDialog(frame)==JFileChooser.APPROVE_OPTION)try{Files.writeString(c.getSelectedFile().toPath(),source.getText(),StandardCharsets.UTF_8);}catch(IOException e){showError(e);}}
    private void showError(Exception e){JOptionPane.showMessageDialog(frame,e.getMessage(),"File error",JOptionPane.ERROR_MESSAGE);}
    public static void launch(){SwingUtilities.invokeLater(()->new CompilerVisualizer("LET n : int = 5;\nFUNCTION factorial(k : int) : int {\n  IF (k <= 1) { RETURN 1; } ELSE { RETURN k * factorial(k - 1); }\n}\nPRINT(factorial(n));\n"));}
    public static void main(String[] args){launch();}
}
