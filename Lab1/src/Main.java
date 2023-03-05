import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.Token;

import java.io.IOException;
import java.util.List;

public class Main
{
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("input path is required");
        }
        String source = args[0];
        CharStream input = CharStreams.fromFileName(source);
        SysYLexer.SysYLexer sysYLexer = new SysYLexer.SysYLexer(input);

        myErrorListener myErrorListener = new myErrorListener();
        sysYLexer.removeErrorListeners();
        sysYLexer.addErrorListener(myErrorListener);

        List<? extends Token> list=sysYLexer.getAllTokens();
        if (!myErrorListener.getFlag()){
            for (int i=0;i<list.size();i++){
                System.err.println(list.get(i).getType()+" "+list.get(i).getText()+" at line "+list.get(i).getLine()+"." );
            }
        }
    }

}