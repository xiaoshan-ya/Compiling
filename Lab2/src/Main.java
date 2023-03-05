import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;
import java.io.IOException;
import java.util.List;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;

public class Main
{
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("input path is required");
        }
        String source = args[0];
        CharStream input = CharStreams.fromFileName(source);
        SysYLexer sysYLexer = new SysYLexer(input);

        CommonTokenStream tokens = new CommonTokenStream(sysYLexer);
        SysYParser sysYParser = new SysYParser(tokens);

        // 打印错误信息
        myErrorListener myErrorListener = new myErrorListener();
        sysYParser.removeErrorListeners();
        sysYParser.addErrorListener(myErrorListener);

        // 打印语法树，Antlr提供visitor方式遍历语法树
        // 默认使用深度优先遍历
        ParseTree tree = sysYParser.program();
        Visitor visitor = new Visitor();
        boolean flag = myErrorListener.getFlag();
        if (myErrorListener.getFlag()){
            visitor.visit(tree);
        }

    }
}
