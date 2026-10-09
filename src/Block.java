import java.util.List;

public class Block extends ASTNode {
    public final List<ASTNode> statements;

    public Block(List<ASTNode> statements) {
        this.statements = statements;
    }
}