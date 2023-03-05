import org.antlr.v4.runtime.RuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.antlr.v4.runtime.tree.Tree;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Visitor extends SysYParserBaseVisitor<Void> {
	public GlobalScope globalScope; // 指向根节点的全局作用域
	public List<BaseScope> localScopeList = new ArrayList<>(); // 指向当前遍历到的作用域
	public List<FunctionSymbol> funcScopeList = new ArrayList<>();
	public int localScopePoint = 0; // 局部作用域指针
	public int funcScopePoint = 0; // 函数作用域指针
	public BaseScope currentScope = globalScope; // 指向当前遍历到的作用域
	String rename;
	String originalName;
	public String renameScopeName;
	public int localScopeCounter = 0;


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
		//TODO 修改部分
		if (currentScope.name.equals(renameScopeName)) {
			if (currentScope.name.equals("function")) { // 函数作用域时
				for (FunctionSymbol fun: funcScopeList) {
					if (fun.name.equals(currentScope.name) && string.equals(originalName)) {
						string = fun.rename;
					}
				}
			}
			if (currentScope.name.equals("GlobalScope") && globalScope.symbols.get(string) != null) {
				string = globalScope.rename;
			}
			else { // 局部作用域时
				for (BaseScope local: localScopeList) {
					if (local.name.equals(currentScope.name) && string.equals(originalName)) {
						string = local.rename;
					}
				}
			}

		}

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

	@Override
	// 在进入Prog节点时开启一个全局作用域
	public Void visitProgram(SysYParser.ProgramContext ctx) {
		currentScope = globalScope;
		return super.visitProgram(ctx);
	}

	@Override
	// 进入函数作用域
	public Void visitFuncDef(SysYParser.FuncDefContext ctx) {
		currentScope = globalScope;
		String funcName = ctx.IDENT().getText(); // 得到函数名

		// 建立函数作用域
		FunctionSymbol funcScope = new FunctionSymbol(funcName, currentScope);
		Function type = new Function();
		funcScope.setType(type);
		// 函数同时也是一个符号，需要放入符号表中
		globalScope.define(funcScope);
		currentScope = funcScope;

		super.visitFuncDef(ctx);
		currentScope = funcScope.enclosingScope;
		return null;
	}

	@Override
	// 进入局部作用域
	public Void visitBlock(SysYParser.BlockContext ctx) {
		LocalScope localScope = new LocalScope(currentScope);
		String localScopeName = localScope.getName() + localScopeCounter;
		localScope.setName(localScopeName);
		localScopeCounter++;

		currentScope = localScope;

		super.visitBlock(ctx);
		currentScope = localScope.enclosingScope;
		return null;
	}
}
