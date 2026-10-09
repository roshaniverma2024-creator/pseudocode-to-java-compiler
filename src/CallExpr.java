import java.util.List;

public class CallExpr extends ASTNode {
    public final String functionName;
    public final List<ASTNode> arguments;

    public CallExpr(String functionName, List<ASTNode> arguments) {
        this.functionName = functionName;
        this.arguments = arguments;
    }
}