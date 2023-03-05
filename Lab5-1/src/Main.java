import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.llvm.LLVM.*;

import java.io.IOException;

import static org.bytedeco.llvm.global.LLVM.*;

public class Main
{
    public static final BytePointer error = new BytePointer();
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
        Visitor visitor = new Visitor();

        visitor.visit(tree);

        // 打印Module中的信息
        LLVMDumpModule(visitor.module);

        if (LLVMPrintModuleToFile(visitor.module, args[1], error) != 0) {    // moudle是你自定义的LLVMModuleRef对象
            LLVMDisposeMessage(error);
        }
    }
}