import org.antlr.v4.runtime.RuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.antlr.v4.runtime.tree.Tree;

import java.util.Objects;

public class Visitor extends SysYParserBaseVisitor<Void> {
	@Override
	public Void visitChildren(RuleNode node) {
		RuleContext ruleContext = node.getRuleContext();

		// 获得规则索引
		int ruleIndex = ruleContext.getRuleIndex();

		// 获得索引对应的语法规则的名称，语法规则是出现在.g4文件中的变量名
		String name = SysYParser.ruleNames[ruleIndex];
		char up = Character.toUpperCase(name.charAt(0));
		String newName = "";
		newName = newName + up;
		for (int i = 1; i < name.length(); i++) {
			newName = newName + name.charAt(i);
		}

		// 得到缩进层数
		int depth = ruleContext.depth() - 1;

		for (int i = 0; i < depth; i++) {
			System.err.print("  ");
		}

		// 不是叶节点
		System.err.println(newName);

		return super.visitChildren(node);
	}

	@Override
	public Void visitTerminal(TerminalNode node) {
		// 获取终结符内容
		String string = node.getText();

		if (Objects.equals(string, "(") || Objects.equals(string, ")")
				|| Objects.equals(string, "{") || Objects.equals(string, "}")
				|| Objects.equals(string, "[") || Objects.equals(string, "]")
				|| Objects.equals(string, ";") || Objects.equals(string, ",")
				|| Objects.equals(string, "<EOF>"))
		{
			return null;
		}

		// 得到在SysYLexer中的token
		Token token = node.getSymbol();

		// 得到索引对应的词法规则名称
		int lexerIndex = token.getType();
		String name = SysYLexer.ruleNames[lexerIndex - 1];

		if (Objects.equals(name, "WS")
			|| Objects.equals(name, "LINE_COMMENT")
			|| Objects.equals(name, "MULTILINE_COMMENT"))
		{
			return null;
		}
		// 进制转换
		if (Objects.equals(name, "INTEGR_CONST")) {
			int n = 0;
			boolean judge = false;
			if (string.length() != 1 && string.charAt(0) == '0' && string.charAt(1) != 'x' && string.charAt(1) != 'X') { // 八进制转十进制
				string = string.substring(1);
				n = Integer.parseInt(string, 8);
				judge = true;
			}
			else if (string.length() != 1 && (string.charAt(1) == 'x' || string.charAt(1) == 'X')) {// 十六进制转十进制
				string = string.substring(2);
				n = Integer.parseInt(string, 16);
				judge = true;
			}

			if (judge) {
				string = n + "";
			}

		}

		// 得到缩进长度
		int depth = 0;
		Tree tmp = node;
		while(tmp.getParent()!=null){
			depth++;
			tmp = tmp.getParent();
		}
		for (int i = 0; i < depth; i++) {
			System.err.print("  ");
		}

		// 得到颜色
		String color = "";
		if (lexerIndex <= 9) {
			color = "orange";
		}
		else if (lexerIndex >= 10 && lexerIndex <= 24) {
			color = "blue";
		}
		else if (lexerIndex == 33) {
			color = "red";
		}
		else if (lexerIndex == 34) {
			color = "green";
		}

		// 打印叶节点
		System.err.println(string + " " + name + "[" + color + "]");


		return null;
	}
}
