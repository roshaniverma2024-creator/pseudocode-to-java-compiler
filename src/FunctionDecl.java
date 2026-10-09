import java.util.List;

public class FunctionDecl extends ASTNode {
    public final String name;
    public final List<Param> parameters;
    public final String returnType;
    public final ASTNode body;

    public static class Param {
        public final String name;
        public final String type;

        public Param(String name, String type) {
            this.name = name;
            this.type = type;
        }
    }

    public FunctionDecl(String name, List<Param> parameters, String returnType, ASTNode body) {
        this.name = name;
        this.parameters = parameters;
        this.returnType = returnType;
        this.body = body;
    }
}