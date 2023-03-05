lexer grammar SysYLexer;

@header{
    package SysYLexer;
}

CONST : 'const';

INT : 'int';

VOID : 'void';

IF : 'if';

ELSE : 'else';

WHILE : 'while';

BREAK : 'break';

CONTINUE : 'continue';

RETURN : 'return';

PLUS : '+';

MINUS : '-';

MUL : '*';

DIV : '/';

MOD : '%';

ASSIGN : '=';

EQ : '==';

NEQ : '!=';

LT : '<';

GT : '>';

LE : '<=';

GE : '>=';

NOT : '!';

AND : '&&';

OR : '||';

L_PAREN : '(';

R_PAREN : ')';

L_BRACE : '{';

R_BRACE : '}';

L_BRACKT : '[';

R_BRACKT : ']';

COMMA : ',';

SEMICOLON : ';';
//TODO 以下划线或字母开头，仅包含下划线、英文字母大小写、阿拉伯数字
IDENT : (LETTER |'_') (LETTER | DIGIT |'_')*;

//TODO 数字常量，包含十进制数，0开头的八进制数，0x或0X开头的十六进制数
INTEGR_CONST : (DIGIT) ((('x'|'X') ((DIGIT|[a-fA-F])+)) | (DIGIT*));

WS : [ \r\n\t]+ -> skip;

LINE_COMMENT : '//' .*? '\n' -> skip;

MULTILINE_COMMENT : '/*' .*? '*/' ->skip;

fragment LETTER : [a-zA-Z];

fragment DIGIT : [0-9];