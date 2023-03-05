import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;
import java.io.IOException;

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

        // Lexer
        SysYLexer sysYLexer = new SysYLexer(input);

        // Parser
        CommonTokenStream tokens = new CommonTokenStream(sysYLexer);
        SysYParser sysYParser = new SysYParser(tokens);

        ParseTree tree = sysYParser.program();

        // 重命名
//        int lineNo = Integer.parseInt(args[1]);
//        int column = Integer.parseInt(args[2]);
//        String name = args[3];
        int lineNo = 8;
        int column = 4;
        String name = "d";

        CheckVisitor checkVisitor = new CheckVisitor();

        // 重命名
        checkVisitor.findVal(lineNo, column, name);

        // 错误类型检查
        checkVisitor.visit(tree);

        // 打印语法树
        if (checkVisitor.judgeError) {
            checkVisitor.visitor(tree);
        }


    }
}
