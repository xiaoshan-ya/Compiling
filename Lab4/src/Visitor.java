import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.llvm.LLVM.LLVMValueRef;
import org.bytedeco.llvm.LLVM.*;

import java.util.ArrayList;
import java.util.List;

import static org.bytedeco.llvm.global.LLVM.*;

public class Visitor extends SysYParserBaseVisitor<LLVMValueRef>{
	public Visitor() { // 构造函数
		//初始化LLVM
		LLVMInitializeCore(LLVMGetGlobalPassRegistry());
		LLVMLinkInMCJIT();
		LLVMInitializeNativeAsmPrinter();
		LLVMInitializeNativeAsmParser();
		LLVMInitializeNativeTarget();
	}
	//创建module
	LLVMModuleRef module = LLVMModuleCreateWithName("moudle");

	//初始化IRBuilder，后续将使用这个builder去生成LLVM IR
	LLVMBuilderRef builder = LLVMCreateBuilder();

	//考虑到我们的语言中仅存在int一个基本类型，可以通过下面的语句为LLVM的int型重命名方便以后使用
	LLVMTypeRef i32Type = LLVMInt32Type();

	@Override
	// 函数定义，函数无参数，且仅包含return语句
	public LLVMValueRef visitFuncDef(SysYParser.FuncDefContext ctx) {
		//生成返回值类型
		LLVMTypeRef returnType = i32Type;

		//生成函数参数类型
		PointerPointer<Pointer> argumentTypes = new PointerPointer<>(0);

		//生成函数类型,多个参数
		LLVMTypeRef ft = LLVMFunctionType(returnType, argumentTypes, /* argumentCount */ 0, /* isVariadic */ 0);

		//生成函数，即向之前创建的module中添加函数
		LLVMValueRef function = LLVMAddFunction(module, /*functionName:String*/ctx.IDENT().getText(), ft);

		//通过如下语句在函数中加入基本块，一个函数可以加入多个基本块
		LLVMBasicBlockRef block1 = LLVMAppendBasicBlock(function, /*blockName:String*/"mainEntry");

		//选择要在哪个基本块后追加指令
		LLVMPositionBuilderAtEnd(builder, block1);//后续生成的指令将追加在block1的后面

		return super.visitFuncDef(ctx);
	}

	@Override
	// 返回语句
	public LLVMValueRef visitReturnStmt(SysYParser.ReturnStmtContext ctx) {
		//函数返回指令
		//创建一个常量,这里是常数0
		int number = 0;
		//int a = Integer.parseInt(ctx.exp().getText());

		if (ctx.exp() instanceof SysYParser.PlusExpContext) {
			number += LLVMConstIntGetSExtValue(visitPlusExp((SysYParser.PlusExpContext) ctx.exp()));
		}
		else if (ctx.exp() instanceof SysYParser.MulExpContext) {
			number += LLVMConstIntGetSExtValue(visitMulExp((SysYParser.MulExpContext) ctx.exp()));
		}
		else if (ctx.exp() instanceof SysYParser.UnaryOpExpContext) {
			number += LLVMConstIntGetSExtValue(visitUnaryOpExp((SysYParser.UnaryOpExpContext) ctx.exp()));
			int x=1;
		}
		else if (ctx.exp() instanceof SysYParser.ExpParenthesisContext) {
			number += LLVMConstIntGetSExtValue(visitExpParenthesis((SysYParser.ExpParenthesisContext) ctx.exp()));
		}
		else if (ctx.exp() instanceof SysYParser.NumberExpContext) {
			String numStr = ctx.exp().getText();
			numStr = getTenNumber(numStr);
			number += Integer.parseInt(numStr);
		}

		LLVMValueRef result = LLVMConstInt(i32Type, number, /* signExtend */ 0);
		LLVMBuildRet(builder, /*result:LLVMValueRef*/result);
//		return super.visitReturnStmt(ctx);
		return null;
	}

	@Override
	public LLVMValueRef visitPlusExp(SysYParser.PlusExpContext ctx) {
		int number = 0;

		// 左半部分
		number += getNumber(ctx.exp(0));

		// 右半部分
		if (ctx.PLUS() != null) { // 加法
			number += getNumber(ctx.exp(1));
		}
		else if (ctx.MINUS() != null) { // 减法
			number -= getNumber(ctx.exp(1));
		}

		LLVMValueRef result = LLVMConstInt(i32Type, number, /* signExtend */ 0);
//		super.visitPlusExp(ctx);
		return result;
	}

	public int getNumber (SysYParser.ExpContext exp) {
		int number = 0;
		if (exp instanceof SysYParser.NumberExpContext) { // 数字表达式
			String numStr = exp.getText();
			numStr = getTenNumber(numStr);
			number += Integer.parseInt(numStr);
		}
		else if (exp instanceof SysYParser.UnaryOpExpContext) { // 单目运算
			number += LLVMConstIntGetSExtValue(visitUnaryOpExp((SysYParser.UnaryOpExpContext) exp));
		}
		else if (exp instanceof SysYParser.ExpParenthesisContext) { // 括号运算
			number += LLVMConstIntGetSExtValue(visitExpParenthesis((SysYParser.ExpParenthesisContext) exp));
		}
		else if (exp instanceof SysYParser.MulExpContext) { // 乘法运算
			number += LLVMConstIntGetSExtValue(visitMulExp((SysYParser.MulExpContext) exp));
		}
		else if (exp instanceof SysYParser.PlusExpContext) { // 加法运算
			number += LLVMConstIntGetSExtValue(visitPlusExp((SysYParser.PlusExpContext) exp));
		}
		return number;
	}

	public String getTenNumber(String str) {
		int n = 0;
		boolean judge = false;
		if (str.length() != 1 && str.charAt(0) == '0' && str.charAt(1) != 'x' && str.charAt(1) != 'X') { // 八进制转十进制
			str = str.substring(1);
			n = Integer.parseInt(str, 8);
			judge = true;
		}
		else if (str.length() != 1 && (str.charAt(1) == 'x' || str.charAt(1) == 'X')) {// 十六进制转十进制
			str = str.substring(2);
			n = Integer.parseInt(str, 16);
			judge = true;
		}

		if (judge) {
			str = n + "";
		}
		return str;
	}

	@Override
	public LLVMValueRef visitUnaryOpExp(SysYParser.UnaryOpExpContext ctx) {
		int number = 0;
		String x = ctx.getText();

		if (ctx.exp() instanceof SysYParser.NumberExpContext) {
			String numStr = ctx.exp().getText();
			numStr = getTenNumber(numStr);

			if (ctx.unaryOp().getText().equals("-")) {
				number = -Integer.parseInt(numStr);
			}
			else if (ctx.unaryOp().getText().equals("+")) {
				number = Integer.parseInt(numStr);
			}
			else if (ctx.unaryOp().getText().equals("!")) {
				if (Integer.parseInt(numStr) == 0) {
					number = 1;
				}
			}
		}
		else if (ctx.exp() instanceof SysYParser.ExpParenthesisContext) {
			if (ctx.unaryOp().getText().equals("-")) {
				number = (int) -LLVMConstIntGetSExtValue(visitExpParenthesis((SysYParser.ExpParenthesisContext) ctx.exp()));
			}
			else if (ctx.unaryOp().getText().equals("+")) {
				number = (int) LLVMConstIntGetSExtValue(visitExpParenthesis((SysYParser.ExpParenthesisContext) ctx.exp()));
			}
			else if (ctx.unaryOp().getText().equals("!")) {
				if (LLVMConstIntGetSExtValue(visitExpParenthesis((SysYParser.ExpParenthesisContext) ctx.exp())) == 0) {
					number = 1;
				}
			}
		}
		else if (ctx.exp() instanceof SysYParser.UnaryOpExpContext) {
			if (ctx.unaryOp().getText().equals("-")) {
				number = (int) -LLVMConstIntGetSExtValue(visitUnaryOpExp((SysYParser.UnaryOpExpContext) ctx.exp()));
			}
			else if (ctx.unaryOp().getText().equals("+")) {
				number = (int) LLVMConstIntGetSExtValue(visitUnaryOpExp((SysYParser.UnaryOpExpContext) ctx.exp()));
			}
			else if (ctx.unaryOp().getText().equals("!")) {
				if ((int)LLVMConstIntGetSExtValue(visitUnaryOpExp((SysYParser.UnaryOpExpContext) ctx.exp())) == 0) {
					number = 1;
				}
			}
		}

		LLVMValueRef result = LLVMConstInt(i32Type, number, /* signExtend */ 0);
//		super.visitUnaryOpExp(ctx);
		return result;
	}

	@Override
	public LLVMValueRef visitMulExp(SysYParser.MulExpContext ctx) {
		int number = 0;
		int number2;

		// 左半部分
		number += getNumber(ctx.exp(0));

		// 右半部分
		if (ctx.MUL() != null) {
			number2 =getNumber(ctx.exp(1));
			number *= number2;
		}
		else if (ctx.DIV() != null) {
			number2 = getNumber(ctx.exp(1));
			number = number / number2;
		}
		else if (ctx.MOD() != null) {
			number2 =getNumber(ctx.exp(1));
			number %= getNumber(ctx.exp(1));
		}

		LLVMValueRef result = LLVMConstInt(i32Type, number, /* signExtend */ 0);
//		super.visitMulExp(ctx);
		return result;
	}

	@Override
	public LLVMValueRef visitExpParenthesis(SysYParser.ExpParenthesisContext ctx) {
		int number = getNumber(ctx.exp());
		LLVMValueRef result = LLVMConstInt(i32Type, number, /* signExtend */ 0);
//		super.visitExpParenthesis(ctx);
		return result;
	}
}
