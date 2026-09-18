import com.sun.source.util.JavacTask;
import javax.tools.*;
import java.util.*;

/** Syntax only. Deliberately does not claim Android compilation or API compatibility. */
public final class JavaSyntaxCheck {
    public static void main(String[] args) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, null)) {
            JavacTask task = (JavacTask) compiler.getTask(null, files, diagnostics,
                    List.of("-proc:none", "--release", "17"), null, files.getJavaFileObjects(args));
            task.parse();
            boolean failed = false;
            for (Diagnostic<?> diagnostic : diagnostics.getDiagnostics()) {
                if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                    failed = true;
                    System.err.println(diagnostic);
                }
            }
            if (failed) System.exit(1);
        }
        System.out.println("PASS: Java syntax for " + args.length + " files (not Android compilation)");
    }
}
