import javax.tools.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

// Compiles the generated Java in-process (javax.tools - no PATH needed) and runs it in a child JVM.
//  - interactive mode: the program talks to the real console (so INPUT works)
//  - capture mode:     stdin is supplied as text and stdout/stderr are captured (used by the test runner)
public class Executor {

    public static class Result {
        public boolean compiled;
        public String compilerLog = "";
        public int exitCode;
        public String stdout = "";
        public String stderr = "";
        public boolean timedOut;
    }

    // Writes <className>.java into 'workDir', compiles it there, and returns false + a log if javac rejects it.
    private static Result compile(String className, String javaCode, Path workDir) throws IOException {
        Result result = new Result();
        Path source = workDir.resolve(className + ".java");
        Files.writeString(source, javaCode, StandardCharsets.UTF_8);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("No Java compiler found - please run with a JDK (not just a JRE).");
        }
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fm = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            fm.setLocation(StandardLocation.CLASS_OUTPUT, List.of(workDir.toFile()));
            List<String> options = List.of("-encoding", "UTF-8");
            boolean ok = compiler.getTask(null, fm, diagnostics, options, null, fm.getJavaFileObjects(source.toFile())).call();
            StringBuilder log = new StringBuilder();
            for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
                log.append("line ").append(d.getLineNumber()).append(": ").append(d.getMessage(null)).append("\n");
            }
            result.compiled = ok;
            result.compilerLog = log.toString();
        }
        return result;
    }

    private static List<String> javaCommand(Path workDir, String className) {
        List<String> cmd = new ArrayList<>();
        cmd.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        cmd.add("-Dfile.encoding=UTF-8");
        cmd.add("-cp");
        cmd.add(workDir.toString());
        cmd.add(className);
        return cmd;
    }

    // Interactive: stdin/stdout go straight to the console. stderr is captured to show a short runtime error.
    public static Result runInteractive(String className, String javaCode, int timeoutSeconds) throws Exception {
        Path dir = Files.createTempDirectory("pseudo2java");
        try {
            Result result = compile(className, javaCode, dir);
            if (!result.compiled) return result;
            System.out.flush();
            ProcessBuilder pb = new ProcessBuilder(javaCommand(dir, className));
            pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
            pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            Process p = pb.start();
            StringBuilder err = new StringBuilder();
            Thread t = pump(p.getErrorStream(), err);
            if (!p.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                result.timedOut = true;
            }
            t.join(1000);
            result.exitCode = result.timedOut ? -1 : p.exitValue();
            result.stderr = err.toString();
            return result;
        } finally {
            deleteQuietly(dir);
        }
    }

    // Capture: feed 'stdin' to the program and return everything it printed.
    public static Result runCaptured(String className, String javaCode, String stdin, int timeoutSeconds) throws Exception {
        Path dir = Files.createTempDirectory("pseudo2java");
        try {
            Result result = compile(className, javaCode, dir);
            if (!result.compiled) return result;
            Process p = new ProcessBuilder(javaCommand(dir, className)).start();
            StringBuilder out = new StringBuilder();
            StringBuilder err = new StringBuilder();
            Thread t1 = pump(p.getInputStream(), out);
            Thread t2 = pump(p.getErrorStream(), err);
            try (OutputStream os = p.getOutputStream()) {
                if (stdin != null) os.write(stdin.getBytes(StandardCharsets.UTF_8));
            } catch (IOException ignored) {
                // the program may exit without reading all of its input
            }
            if (!p.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                result.timedOut = true;
            }
            t1.join(1000);
            t2.join(1000);
            result.exitCode = result.timedOut ? -1 : p.exitValue();
            result.stdout = out.toString();
            result.stderr = err.toString();
            return result;
        } finally {
            deleteQuietly(dir);
        }
    }

    private static Thread pump(InputStream in, StringBuilder sink) {
        Thread t = new Thread(() -> {
            try {
                sink.append(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException ignored) {
            }
        });
        t.setDaemon(true);
        t.start();
        return t;
    }

    private static void deleteQuietly(Path dir) {
        try (var files = Files.walk(dir)) {
            files.sorted((a, b) -> b.compareTo(a)).forEach(f -> f.toFile().delete());
        } catch (IOException ignored) {
        }
    }
}
