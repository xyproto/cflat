// vim: sw=2 ts=2
// tango2

package no.uio.ifi.cflat.syntax;

/*
 * module Syntax
 */

import static no.uio.ifi.cflat.scanner.Token.*;
import no.uio.ifi.cflat.cflat.Cflat;
import no.uio.ifi.cflat.code.Code;
import no.uio.ifi.cflat.error.Error;
import no.uio.ifi.cflat.log.Log;
import no.uio.ifi.cflat.scanner.Scanner;
import no.uio.ifi.cflat.scanner.Token;
import no.uio.ifi.cflat.types.ArrayType;
import no.uio.ifi.cflat.types.Type;
import no.uio.ifi.cflat.types.Types;


/*
 * Creates a syntax tree by parsing; 
 * prints the parse tree (if requested);
 * checks it;
 * generates executable code. 
 */
public class Syntax {
    static DeclList library;
    static Program program;

    public static void init() {
	// part 1
	library = new GlobalDeclList();
	addFnToLib("getdouble", Types.doubleType, null, null);
	addFnToLib("getint", Types.intType, null, null);
	addFnToLib("putdouble", Types.doubleType, "x", Types.doubleType);
	addFnToLib("putint", Types.intType, "x", Types.intType);
	addFnToLib("exit", Types.intType, "status", Types.intType);
	addFnToLib("putchar", Types.intType, "c", Types.intType);
	addFnToLib("getchar", Types.intType, null, null);
	library.check(null);
   
    }

    public static void addFnToLib(String name, Type retrType, String paramName, Type paramType) {
	FuncDecl d = new FuncDecl(name);
	d.type = retrType;
	d.lineNum = -1;
	if (paramName != null) {
	    ParamDecl pd = new ParamDecl(paramName, 0);
	    pd.type = paramType;
	    d.functionParameters.nParams = 1;
	    d.functionParameters.addDecl(pd);
	}
	library.addDecl(d);
    }
	    

    public static void finish() {
	// part 1
    }

    public static void checkProgram() {
	program.check(library);
    }

    public static void genCode() {
	program.genCode(null);
    }

    public static void parseProgram() {
	program = new Program();
	program.parse();
    }

    public static void printProgram() {
	program.printTree();
    }

    static void error(SyntaxUnit use, String message) {
	Error.error(use.lineNum, message);
    }


}


/*
 * Master class for all syntactic units. (This class is not mentioned in the
 * syntax diagrams.)
 */
abstract class SyntaxUnit {
    int lineNum;

    SyntaxUnit() {
	lineNum = Scanner.curLine;
    }

    abstract void check(DeclList curDecls);

    abstract void genCode(FuncDecl curFunc);

    abstract void parse();

    abstract void printTree();
}


/*
** A <program>
 */
class Program extends SyntaxUnit {
    DeclList progDecls = new GlobalDeclList();

    @Override
    void check(DeclList curDecls) {
	progDecls.check(curDecls);

	if (!Cflat.noLink) {
	    // Check that 'main' has been declared properly:
	    // findDecl will throw a syntaxerror if main cannot be found
	    Declaration d = progDecls.findDecl("main", this);
	}
    }

    @Override
    void genCode(FuncDecl curFunc) {
	progDecls.genCode(null);
    }

    @Override
    void parse() {
	Log.enterParser("<program>");
	progDecls.parse();
	if (Scanner.curToken != eofToken)
	    Error.expected("A declaration");

	Log.leaveParser("</program>");
    }

    @Override
    void printTree() {
	progDecls.printTree();
    }
}


/*
 * A declarati

on list. (This class is not mentioned in the syntax diagrams.)
 */

abstract class DeclList extends SyntaxUnit {
    Declaration firstDecl;
    DeclList outerScope;

    DeclList() {
	firstDecl = null;
    }

    @Override
    void check(DeclList curDecls) {
	outerScope = curDecls;
	Declaration dx = firstDecl;
	while (dx != null) {
	    dx.check(this);
	    dx = dx.nextDecl;
	}
    }

    @Override
    void printTree() {
	Declaration dx = firstDecl;
	while (dx != null) {
	    dx.printTree();
	    dx = dx.nextDecl;
	    if (dx != null) Log.wTreeLn();
	}
    }

    void addDecl(Declaration d) {
	if (firstDecl == null) {
	    firstDecl = d;
	} else {
	    Declaration tmp = firstDecl;
	    while (tmp.nextDecl != null) {
		tmp = tmp.nextDecl;
	    }
	    tmp.nextDecl = d;
	}
    }

    int dataSize() {
	Declaration dx = firstDecl;
	int res = 0;

	while (dx != null) {
	    res += dx.declSize();
	    dx = dx.nextDecl;
	}
	return res;
    }

    Declaration findDecl(String name, SyntaxUnit usedIn) {
	for (Declaration dx = firstDecl; dx != null; dx = dx.nextDecl) {
	    if (name.equals(dx.name) && dx.visible == true) {
		Log.noteBinding(name, dx.lineNum, usedIn.lineNum);
		return dx;
	    }
	}
	if (outerScope != null) return outerScope.findDecl(name, usedIn);
	Syntax.error(usedIn, name + " is not declared.");
	return null;
    }
}


/*
 * A list of global declarations. (This class is not mentioned in the syntax
 * diagrams.)
 */
class GlobalDeclList extends DeclList {

    @Override
    void genCode(FuncDecl curFunc) {
	for (Declaration d = firstDecl; d != null; d = d.nextDecl) {
	    d.genCode(curFunc);
	}
    }

    @Override
    void parse() {
	while (Token.isTypeName(Scanner.curToken)) {
	    if (Scanner.nextToken == nameToken) {
		if (Scanner.nextNextToken == leftParToken) {
		    FuncDecl fd = new FuncDecl(Scanner.nextName);
		    addDecl(fd);
		    fd.parse();
		} else if (Scanner.nextNextToken == leftBracketToken) {
		    GlobalArrayDecl gad = new GlobalArrayDecl(Scanner.nextName);
		    addDecl(gad);
		    gad.parse();
		} else {
		    GlobalSimpleVarDecl gsv = new GlobalSimpleVarDecl(Scanner.nextName);
		    addDecl(gsv);
		    gsv.parse();
		}
	    } else {
		Error.expected("A declaration");
	    }
	}
    }
}


/*
 * A list of local declarations. (This class is not mentioned in the syntax
 * diagrams.)
 */
class LocalDeclList extends DeclList {
    @Override
    void genCode(FuncDecl curFunc) {
	for (Declaration d = firstDecl; d != null; d = d.nextDecl) {
	    d.genCode(curFunc);
	}
    }

    @Override
    void parse() {
	VarDecl d = null;
	while (Token.isTypeName(Scanner.curToken) && Scanner.nextToken == nameToken) {
	    if (Scanner.nextNextToken == leftBracketToken) {
		d = new LocalArrayDecl(Scanner.nextName);
	    } else {
		d = new LocalSimpleVarDecl(Scanner.nextName);
	    }
	    addDecl(d);
	    d.parse();
	}
    }
    /*
     * This overrides printtree cause the printout of local decllist differs from globaldecllist
     */
    @Override
    void printTree() {
	Declaration dx = firstDecl;
	while (dx != null) {
	    dx.printTree();
	    dx = dx.nextDecl;
	    if (dx == null) Log.wTreeLn();
	}
    }
}


/*
 * A list of parameter declarations. (This class is not mentioned in the syntax
 * diagrams.)
 */
class ParamDeclList extends DeclList {
    int nParams = 0;
    @Override
    void genCode(FuncDecl curFunc) {
	for (Declaration d = firstDecl; d != null; d = d.nextDecl) {
	    d.genCode(curFunc);
	}
    }

    @Override
    void parse() {
	int curAssStep = 8;
	while (Token.isTypeName(Scanner.curToken) && Scanner.nextToken == nameToken) {
	    ParamDecl pd = new ParamDecl(Scanner.nextName, curAssStep);
	    addDecl(pd);
	    nParams++;
	    curAssStep += 4;
	    pd.parse(); 
	    if (Scanner.curToken == commaToken) {
		Scanner.skip(commaToken);
	    }
	}
    }

    @Override
    void printTree() {
	Declaration tmp = firstDecl;
	while (tmp != null) {
	    Log.wTree(tmp.type.typeName() + " " + tmp.name);
	    tmp = tmp.nextDecl;
	    if (tmp != null) Log.wTree(",");
	}
    }
}


/*
 * Any kind of declaration. (This class is not mentioned in the syntax
 * diagrams.)
 */
abstract class Declaration extends SyntaxUnit {
    String name, assemblerName;
    Type type;
    boolean visible = false;
    Declaration nextDecl = null;

    Declaration(String n) {
	name = n;
    }

    abstract int declSize();

    /**
     * checkWhetherArray: Utility method to check whether this Declaration is
     * really an array. The compiler must check that a name is used properly; for
     * instance, using an array name a in "a()" or in "x=a;" is illegal. This is
     * handled in the following way:
     * <ul>
     * <li>When a name a is found in a setting which implies that should be an
     * array (i.e., in a construct like "a["), the parser will first search for
     * a's declaration d.
     * <li>The parser will call d.checkWhetherArray(this).
     * <li>Every sub-class of Declaration will implement a checkWhetherArray. If
     * the declaration is indeed an array, checkWhetherArray will do nothing, but
     * if it is not, the method will give an error message.
     * </ul>
     * Examples
     * <dl>
     * <dt>GlobalArrayDecl.checkWhetherArray(...)</dt>
     * <dd>will do nothing, as everything is all right.</dd>
     * <dt>FuncDecl.checkWhetherArray(...)</dt>
     * <dd>will give an error message.</dd>
     * </dl>
     */
    abstract void checkWhetherArray(SyntaxUnit use);

    /**
     * checkWhetherFunction: Utility method to check whether this Declaration is
     * really a function.
     * 
     * @param nParamsUsed
     *          Number of parameters used in the actual call. (The method will
     *          give an error message if the function was used with too many or
     *          too few parameters.)
     * @param use
     *          From where is the check performed?
     * @see checkWhetherArray
     */
    abstract void checkWhetherFunction(int nParamsUsed, SyntaxUnit use);

    /**
     * checkWhetherSimpleVar: Utility method to check whether this Declaration is
     * really a simple variable.
     * 
     * @see checkWhetherArray
     */
    abstract void checkWhetherSimpleVar(SyntaxUnit use);

}


/*
 * A <var decl>
 */
abstract class VarDecl extends Declaration {
    VarDecl(String n) {
	super(n);
    }

    @Override
    int declSize() {
	return type.size();
    }

    @Override
    void checkWhetherFunction(int nParamsUsed, SyntaxUnit use) {
	Syntax.error(use, name + " is a variable and no function!");
    }

    @Override
    void printTree() {
	Log.wTree(type.typeName() + " " + name);
	Log.wTreeLn(";");
    }

    // -- Must be changed in part 1+2:
}


/*
 * A global array declaration
 */
class GlobalArrayDecl extends VarDecl {
    GlobalArrayDecl(String n) {
	super(n);
	assemblerName = (Cflat.underscoredGlobals() ? "_" : "") + n;
    }

    @Override
    void check(DeclList curDecls) {
	visible = true;
	if (((ArrayType)type).nElems < 0)
	    Syntax.error(this, "Arrays cannot have negative size!");
    }

    @Override
    void checkWhetherArray(SyntaxUnit use) {
	/* OK */
    }

    @Override
    void checkWhetherSimpleVar(SyntaxUnit use) {
	Syntax.error(use, name + " is an array and no simple variable!");
    }

    @Override
    void genCode(FuncDecl curFunc) {
	ArrayType arrT = (ArrayType)type;
	Code.genVar(assemblerName, true, declSize(), arrT.elemType.typeName() + " " + name + "[" + arrT.nElems + "];");
    }

    @Override
    void parse() {
	Log.enterParser("<var decl>");
	Type arrType = Types.getType(Scanner.curToken);
	Scanner.skip(Scanner.curToken);
	Scanner.skip(nameToken);
	Scanner.skip(leftBracketToken);
	type = new ArrayType(Scanner.curNum, arrType);
	Scanner.skip(Scanner.curToken);
	Scanner.skip(rightBracketToken);
	Scanner.skip(semicolonToken);
	Log.leaveParser("</var decl>");
    }

    @Override
    void printTree() {
	ArrayType arrType = (ArrayType)type;
	Log.wTreeLn(arrType.elemType.typeName() + " " + name + "[" + arrType.nElems +  "];");
    }
}


/*
 * A global simple variable declaration
 */
class GlobalSimpleVarDecl extends VarDecl {
    GlobalSimpleVarDecl(String n) {
	super(n);
	assemblerName = (Cflat.underscoredGlobals() ? "_" : "") + n;
    }

    @Override
    void check(DeclList curDecls) {
	visible = true;
    }

    @Override
    void checkWhetherArray(SyntaxUnit use) {
	Syntax.error(this, name + " is a simple variable and no array.");
    }

    @Override
    void checkWhetherSimpleVar(SyntaxUnit use) {
	/* OK */
    }

    @Override
    void genCode(FuncDecl curFunc) {
	Code.genVar(name, true, type.size(), type.typeName() + " " + name + ";");
    }

    @Override
    void parse() {
	Log.enterParser("<var decl>");
	type = Types.getType(Scanner.curToken);
	Scanner.skip(Scanner.curToken);
	Scanner.skip(nameToken);
	Scanner.skip(semicolonToken);
	Log.leaveParser("</var decl>");
    }
}


/*
 * A local array declaration
 */
class LocalArrayDecl extends VarDecl {
    LocalArrayDecl(String n) {
	super(n);
    }

    @Override
    void check(DeclList curDecls) {
	visible = true;
	if (((ArrayType)type).nElems < 0)
	    Syntax.error(this, "Arrays cannot have negative size!");
    }

    @Override
    void checkWhetherArray(SyntaxUnit use) {
	// *OK*
    }

    @Override
    void checkWhetherSimpleVar(SyntaxUnit use) {
	Syntax.error(this, name + " is a array and no simple variable.");
    }

    @Override
    void genCode(FuncDecl curFunc) {
	// -- Must be changed in part 2:
	Log.w("LocalArrayDecl.genCode");
    }

    @Override
    void parse() {
	Log.enterParser("<var decl>");
	Type arrType = Types.getType(Scanner.curToken);
	Scanner.skip(Scanner.curToken);
	Scanner.skip(nameToken);
	Scanner.skip(leftBracketToken);
	type = new ArrayType(Scanner.curNum, arrType);
	Scanner.skip(Scanner.curToken);
	Scanner.skip(rightBracketToken);
	Scanner.skip(semicolonToken);
	Log.leaveParser("</var decl>");
    }

    @Override
    void printTree() {
	ArrayType arrType = (ArrayType)type;
	Log.wTreeLn(arrType.elemType.typeName() + " " + name + "[" + arrType.nElems +  "];");
    }

}


/*
 * A local simple variable declaration
 */
class LocalSimpleVarDecl extends VarDecl {

    LocalSimpleVarDecl(String n) {
	super(n);
    }

    @Override
    void check(DeclList curDecls) {
	visible = true;
    }

    @Override
    void checkWhetherArray(SyntaxUnit use) {
	Syntax.error(this, name + " is a simple variable and no array.");
    }

    @Override
    void checkWhetherSimpleVar(SyntaxUnit use) {
	// * OK *
    }

    @Override
    void genCode(FuncDecl curFunc) {
	Code.genInstr("", "subl", "$" + type.size() + ",%esp", "Get " + type.size() + " bytes local data space");
    }

    @Override
    void parse() {
	Log.enterParser("<var decl>");
	type = Types.getType(Scanner.curToken);
	Scanner.skip(Scanner.curToken);
	name = Scanner.curName;
	Scanner.skip(nameToken);
	Scanner.skip(semicolonToken);
	Log.leaveParser("</var decl>");
    }
}


/*
 * A <param decl>
 */
class ParamDecl extends VarDecl {
    int paramNum = 0;

    ParamDecl(String n, int paramNum) {
	super(n);
	this.paramNum = paramNum;
    }

    @Override
    void check(DeclList curDecls) {
	visible = true;
    }

    @Override
    void checkWhetherArray(SyntaxUnit use) {
	Syntax.error(this, name + " arrays can not be used as parameter.");
    }

    @Override
    void checkWhetherSimpleVar(SyntaxUnit use) {
	// * OK * 
    }

    @Override
    void genCode(FuncDecl curFunc) {
	assemblerName = paramNum + "(%ebp)";
    }

    @Override
    void parse() {
	Log.enterParser("<param decl>");
	type = Types.getType(Scanner.curToken);
	Scanner.skip(Scanner.curToken);
	name = Scanner.curName;
	Scanner.skip(nameToken);
	Log.leaveParser("</param decl>");
    }
}


/*


 * A <func decl>
 */
class FuncDecl extends Declaration {
    ParamDeclList functionParameters;
    LocalDeclList functionBodyDecls;
    StatmList functionBodyStatms;

    FuncDecl(String n) {
	// Used for user functions:
	super(n);
	assemblerName = (Cflat.underscoredGlobals() ? "_" : "") + n;
	functionParameters = new ParamDeclList();
	functionBodyDecls = new LocalDeclList();
	functionBodyStatms = new StatmList();

    }

    @Override
    int declSize() {
	return 0;
    }

    @Override
    void check(DeclList curDecls) {
	visible = true;
	functionParameters.check(curDecls);
	functionBodyDecls.check(functionParameters);
	functionBodyStatms.check(functionBodyDecls);
	// Check that all return statements in this function is of same type as function
	for (Statement s = functionBodyStatms.firstStatement; s != null; s = s.nextStatm) {
	    if (s instanceof ReturnStatm && type != ((ReturnStatm)s).returnExpression.valType) {
		Syntax.error(s, "return statement must have same type as function declaration");
	    }
	}
    }

    @Override
    void checkWhetherArray(SyntaxUnit use) {
	Syntax.error(this, name + " is a function and not a array.");
    }

    @Override
    void checkWhetherFunction(int nParamsUsed, SyntaxUnit use) {
	if (nParamsUsed != functionParameters.nParams) Syntax.error(use, name + " takes " +  functionParameters.nParams + " paramters, not " + nParamsUsed);
    }

    @Override
    void checkWhetherSimpleVar(SyntaxUnit use) {
	Syntax.error(this, name + " is a function and not a SimpleVar.");
    }

    @Override
    void genCode(FuncDecl curFunc) {
	Code.genInstr("", ".globl", assemblerName, "");
	Code.genInstr(assemblerName, "pushl", "%ebp", "Start function " + name);
	Code.genInstr("", "movl", "%esp,%ebp", "");
	// -- Must be changed in part 2:
	functionParameters.genCode(this);
	functionBodyDecls.genCode(this);
	functionBodyStatms.genCode(this);
	Code.genInstr(".exit$" + name, "","","");
	Code.genInstr("", "movl", "%ebp,%esp", "");
	Code.genInstr("", "popl", "%ebp", "");
	Code.genInstr("", "ret", "", "End function " + name);
	
    }

    @Override
    void parse() {
	Log.enterParser("<func decl>");
	type = Types.getType(Scanner.curToken);
	Scanner.skip(Scanner.curToken);
	name = Scanner.curName;
	Scanner.skip(nameToken);
	Scanner.skip(leftParToken);
	functionParameters.parse();
	Scanner.skip(rightParToken);
	Scanner.skip(leftCurlToken);
	Log.enterParser("<func body>");
	functionBodyDecls.parse();
	functionBodyStatms.parse();
	Log.leaveParser("</func body>");
	Scanner.skip(rightCurlToken);
	Log.leaveParser("</func decl>");
    }

    @Override
    void printTree() {
	Log.wTree(type.typeName() + " " + name + " (");
	functionParameters.printTree();
	Log.wTreeLn(")");
	Log.wTreeLn("{");
	Log.indentTree();
	functionBodyDecls.printTree();
	functionBodyStatms.printTree();
	Log.outdentTree();
	Log.wTreeLn("}");
    }
}

/*
 * A <statm list>.
 */
class StatmList extends SyntaxUnit {
    Statement firstStatement;

    StatmList() {
	firstStatement = null;
    }
    @Override
    void check(DeclList curDecls) {
	Statement st = firstStatement;
	while (st != null) {
	    st.check(curDecls);
	    st = st.nextStatm;
	}
      
    }

    @Override
    void genCode(FuncDecl curFunc) {
	for (Statement st = firstStatement; st != null; st = st.nextStatm) {
	    st.genCode(curFunc);
	}

    }

    @Override
    void parse() {
	Log.enterParser("<statm list>");
	while (Scanner.curToken != rightCurlToken) {
	    Log.enterParser("<statement>");
	    Statement st = Statement.makeNewStatement();
	    st.parse();
	    add(st);
	    Log.leaveParser("</statement>");
	}
	Log.leaveParser("</statm list>");
    }

    /*
     * Helper method to remove ugly list logic from parse
     */
    void add(Statement s) {
	if (firstStatement == null) {
	    firstStatement = s;
	} else {
	    Statement tmp = firstStatement;
	    while (tmp.nextStatm != null) {
		tmp = tmp.nextStatm;
	    }
	    tmp.nextStatm = s;
	}
    }

    @Override
    void printTree() {
	Statement st = firstStatement;
	while (st != null) {
	    st.printTree();
	    st = st.nextStatm;
	}
    }
}


/*
 * A <statement>.
 */
abstract class Statement extends SyntaxUnit {
    Statement nextStatm = null;

    static Statement makeNewStatement() {
	if (Scanner.curToken == nameToken && Scanner.nextToken == leftParToken) {
	    return new CallStatm();
	} else if (Scanner.curToken == nameToken) {
	    return new AssignStatm();
	} else if (Scanner.curToken == forToken) {
	    return new ForStatm();
	} else if (Scanner.curToken == ifToken) {
	    return new IfStatm();
	} else if (Scanner.curToken == returnToken) {
	    return new ReturnStatm();
	} else if (Scanner.curToken == whileToken) {
	    return new WhileStatm();
	} else if (Scanner.curToken == semicolonToken) {
	    return new EmptyStatm();
	} else {
	    Error.expected("A statement");
	}
	return null; // Just to keep the Java compiler happy. :-)
    }
}


/*
 * An <empty statm>.
 */
class EmptyStatm extends Statement {
    // -- Must be changed in part 1+2:

    @Override
    void check(DeclList curDecls) {
	// Its empty, just like the EmptyStatm
    }

    @Override
    void genCode(FuncDecl curFunc) {
	// -- Must be changed in part 2:
	Log.w("EmptyStatm.genCode");
    }

    @Override
    void parse() {
	Log.enterParser("<empty statm>");
	Scanner.skip(semicolonToken);
	Log.leaveParser("</empty statm>");
    }

    @Override
    void printTree() {
	Log.wTreeLn(";");
    }
}


/*
 * A <for-statm>.
 */
class ForStatm extends Statement {
    Assignment forCounter;
    Expression forTest;
    Assignment forIncrement;
    StatmList forBody;
    ForStatm() {
	forCounter = new Assignment();
	forTest = new Expression();
	forIncrement = new Assignment();
	forBody = new StatmList();
    }

    @Override
    void check(DeclList curDecls) {
	forCounter.check(curDecls);
	forTest.check(curDecls);
	forIncrement.check(curDecls);
	forBody.check(curDecls);
    }

    @Override
    void genCode(FuncDecl curFunc) {
    }

    @Override
    void parse() {
	Log.enterParser("<for-statm>");
	Scanner.skip(forToken);
	Scanner.skip(leftParToken);
	forCounter.parse();
	Scanner.skip(semicolonToken);
	forTest.parse();
	Scanner.skip(semicolonToken);
	forIncrement.parse();
	Scanner.skip(rightParToken);
	Scanner.skip(leftCurlToken);
	forBody.parse();
	Scanner.skip(rightCurlToken);
	Log.leaveParser("</for-statm>");
    }

    @Override
    void printTree() {
	Log.wTree("for (");
	forCounter.printTree();
	Log.wTree(";  ");
	forTest.printTree();
	Log.wTree(";  ");
	forIncrement.printTree();
	Log.wTreeLn(") {");
	Log.indentTree();
	forBody.printTree();
	Log.outdentTree();
	Log.wTreeLn("}");
    }
}

class CallStatm extends Statement {
    FunctionCall functionCall;

    CallStatm() {
	functionCall = new FunctionCall();
    }
    @Override
    void check(DeclList curDecls) {
	functionCall.check(curDecls);
    }

    @Override
    void genCode(FuncDecl curFunc) {
	// Call the function
	functionCall.genCode(curFunc);
    }

    @Override
    void parse() {
	Log.enterParser("<call-statm>");
	functionCall.parse();
	Scanner.skip(semicolonToken);
	Log.leaveParser("</call-statm>");
    }

    @Override
    void printTree() {
	functionCall.printTree();
	Log.wTreeLn(";");
    }
}

class AssignStatm extends Statement {
    Assignment assignment;

    AssignStatm() {
	assignment = new Assignment();
    }
    @Override
    void check(DeclList curDecls) {
	assignment.check(curDecls);
    }

    @Override
    void genCode(FuncDecl curFunc) {
	assignment.genCode(curFunc);
    }

    @Override
    void parse() {
	Log.enterParser("<assign-statm>");
	assignment.parse();
	Scanner.skip(semicolonToken);
	Log.leaveParser("</assign statm>"); 
    }

    @Override
    void printTree() {
	assignment.printTree();
	Log.wTreeLn(";");
    }
}

class Assignment extends Statement {
    Variable variable;
    Expression expression;

    Assignment() {
	variable = new Variable();
	expression = new Expression();
    }
    void check(DeclList curDecls) {
	variable.check(curDecls);
	expression.check(curDecls);
    }

    void genCode(FuncDecl curFunc){
	expression.genCode(curFunc);
	if (variable.declRef.type == Types.doubleType && variable.declRef instanceof GlobalSimpleVarDecl || variable.declRef instanceof GlobalArrayDecl) {
	    Code.genInstr("", "movl", "%eax,.tmp", "");
	    Code.genInstr("", "fildl", ".tmp", "  (" + variable.declRef.type.typeName() + ")");
	    Code.genInstr("", "fstpl", variable.varName, variable.varName + " =");
	} else if (variable.declRef instanceof GlobalSimpleVarDecl || variable.declRef instanceof GlobalArrayDecl) {
	    Code.genInstr("", "movl", "%eax," + variable.varName, variable.varName + " =");
	} else if (variable.declRef.type == Types.doubleType) {
	    Code.genInstr("", "movl", "%eax,.tmp", "");
	    Code.genInstr("", "fildl", ".tmp", "  (" + variable.declRef.type.typeName() + ")");
	    Code.genInstr("", "fstpl", "-" + variable.declRef.type.size() + "(%ebp)", variable.varName + " =");
	} else {
	    Code.genInstr("", "movl", "%eax," + "-" + expression.valType.size() + "(%ebp)", variable.varName + " =");
	}
    }
    void parse() {
	Log.enterParser("<assignment>");
	variable.parse();
	Scanner.skip(assignToken);
	expression.parse();
	Log.leaveParser("</assignment>");
    }
    void printTree() {
	variable.printTree();
	Log.wTree(" = ");
	expression.printTree();
    }
}

/*
 * An <if-statm>.
 */
class IfStatm extends Statement {
    Expression ifTest;
    StatmList ifPart;
    StatmList elsePart;

    IfStatm() {
	ifTest = new Expression();
	ifPart = new StatmList();
	elsePart = null;
    }
    @Override
    void check(DeclList curDecls) {
	ifTest.check(curDecls);
	ifPart.check(curDecls);
	if (elsePart != null) elsePart.check(curDecls);
    }

    @Override
    void genCode(FuncDecl curFunc) {
	// -- Must be changed in part 2:
	Log.w("IfStatm.genCode");
    }

    @Override
    void parse() {
	Log.enterParser("<if-statm>");
	Scanner.skip(ifToken);
	Scanner.skip(leftParToken);
	ifTest.parse();
	Scanner.skip(rightParToken);
	Scanner.skip(leftCurlToken);
	ifPart.parse();
	Scanner.skip(rightCurlToken);
	if (Scanner.curToken == elseToken) {
	    Log.enterParser("<else-part>");
	    Scanner.readNext();
	    Scanner.skip(leftCurlToken);
	    elsePart = new StatmList();
	    elsePart.parse();
	    Scanner.skip(rightCurlToken);
	    Log.leaveParser("</else-part>");
	}
	Log.leaveParser("</if-statm>");
    }

    @Override
    void printTree() {
	Log.wTree("if (");
	ifTest.printTree();
	Log.wTreeLn(") {");
	Log.indentTree();
	ifPart.printTree();
	if (elsePart != null) { // else part is optional
	    Log.outdentTree();
	    Log.wTreeLn("} else {");
	    Log.indentTree();
	    elsePart.printTree();
	}
	Log.outdentTree();
	Log.wTreeLn("}");

    }
}


/*
 * A <return-statm>.
 */
class ReturnStatm extends Statement {
    Expression returnExpression;
    ReturnStatm() {
	returnExpression = new Expression();
    }
    @Override
    void check(DeclList curDecls) {
	returnExpression.check(curDecls);
    }

    @Override
    void genCode(FuncDecl curFunc) {
	returnExpression.genCode(curFunc);
	Code.genInstr("", "jmp", ".exit$" + curFunc.name, "Return-statement");
    }

    @Override
    void parse() {
	Log.enterParser("<return-statm>");
	Scanner.skip(returnToken);
	returnExpression.parse();
	Scanner.skip(semicolonToken);
	Log.leaveParser("</return-statm>");
    }

    @Override
    void printTree() {
	Log.wTree("return ");
	returnExpression.printTree();
	Log.wTreeLn(";");
    }
}

/*
 * A <while-statm>.
 */
class WhileStatm extends Statement {
    Expression test;
    StatmList body;

    WhileStatm() {
	test = new Expression();
	body = new StatmList();
    }

    @Override
    void check(DeclList curDecls) {
	test.check(curDecls);
	body.check(curDecls);
    }

    @Override
    void genCode(FuncDecl curFunc) {
	String testLabel = Code.getLocalLabel(), endLabel = Code.getLocalLabel();

	Code.genInstr(testLabel, "", "", "Start while-statement");
	test.genCode(curFunc);
	test.valType.genJumpIfZero(endLabel);
	body.genCode(curFunc);
	Code.genInstr("", "jmp", testLabel, "");
	Code.genInstr(endLabel, "", "", "End while-statement");
    }

    @Override
    void parse() {
	Log.enterParser("<while-statm>");
	Scanner.readNext();
	Scanner.skip(leftParToken);
	test.parse();
	Scanner.skip(rightParToken);
	Scanner.skip(leftCurlToken);
	body.parse();
	Scanner.skip(rightCurlToken);

	Log.leaveParser("</while-statm>");
    }

    @Override
    void printTree() {
	Log.wTree("while (");
	test.printTree();
	Log.wTreeLn(") {");
	Log.indentTree();
	body.printTree();
	Log.outdentTree();
	Log.wTreeLn("}");
    }
}


/*
 * An <expression list>.
 */

class ExprList extends SyntaxUnit {
    Expression firstExpr;
    int nExprs = 0;

    ExprList() {
	firstExpr = null;
    }

    @Override
    void check(DeclList curDecls) {
	for (Expression e = firstExpr; e != null; e = e.nextExpr) {
	    e.check(curDecls);
	}
    }

    @Override
    void genCode(FuncDecl curFunc) {
	Expression[] exprs = new Expression[nExprs];
	int counter = 0;
	for (Expression e = firstExpr; e != null; e = e.nextExpr) {
	    exprs[counter++] = e;
	}
	int paramN = 1;
	for (int i = exprs.length - 1; i >= 0; i--) {
	    exprs[i].genCode(curFunc);
	    Code.genInstr("", "pushl", "%eax", "Push parameter #" + paramN);
	    paramN++;
	}
    }

    @Override
    void parse() {
	Log.enterParser("<expr list>");
	while (Scanner.curToken != rightParToken) {
	    Expression e = new Expression();
	    add(e);
	    nExprs++;
	    e.parse();
	    if (Scanner.curToken == commaToken) Scanner.skip(commaToken);
	}
	Log.leaveParser("</expr list>");
    }

    /*
     * Helper method to remove ugly list logic from parse
     */
    void add(Expression e) {
	if (firstExpr == null) {
	    firstExpr = e;
	} else {
	    Expression tmp = firstExpr;
	    while (tmp.nextExpr != null) {
		tmp = tmp.nextExpr;
	    }
	    tmp.nextExpr = e;
	}
    }
    @Override
    void printTree() {
	Expression current = firstExpr;
	while (current != null) {
	    current.printTree();
	    current = current.nextExpr;
	    if (current != null) Log.wTree(",");
	}
    }
}


/*
 * An <expression>
 */
class Expression extends Operand {
    Expression nextExpr;
    Term firstTerm, secondTerm;
    Operator relOp;
    boolean innerExpr;

    Expression() {
	nextExpr = null;
	firstTerm = new Term();
	secondTerm = null;
	relOp = null;
	innerExpr = false;
    }
    @Override
    void check(DeclList curDecls) {
	if (relOp != null) {
	    firstTerm.check(curDecls);
	    relOp.check(curDecls);
	    secondTerm.check(curDecls);
	    valType = relOp.opType;
	} else {
	    firstTerm.check(curDecls);
	    valType = firstTerm.valType;
	}
	    
    }

    @Override
    void genCode(FuncDecl curFunc) {
	// -- Must be changed in part 2:
	firstTerm.genCode(curFunc);
	if (relOp != null) relOp.genCode(curFunc);
	if (secondTerm != null) secondTerm.genCode(curFunc);
    }

    @Override
    void parse() {
	Log.enterParser("<expression>");
	firstTerm.parse();
	if (Token.isRelOperator(Scanner.curToken)) {
	    relOp = new RelOperator();
	    relOp.parse();
	    secondTerm = new Term();
	    secondTerm.parse();
	}
	Log.leaveParser("</expression>");
    }

    @Override
    void printTree() {
	if (innerExpr) Log.wTree("(");
	firstTerm.printTree();
	if (secondTerm != null) {
	    relOp.printTree();
	    secondTerm.printTree();
	}
	if (innerExpr) Log.wTree(")"); // Inner expr depends upon wheter or not the expression is used as a operand, this is set in factor
    }
}


/*
 * A <term>
 */
class Term extends Operand {
    Factor firstFactor;
    TermOperator firstTop;

    Term() {
	firstFactor = new Factor();
	firstTop = null;
    }

    @Override
    void check(DeclList curDecls) {
	for (Factor f = firstFactor; f != null; f = f.nextFactor) {
	    f.check(curDecls);
	    if (valType == null) {
		valType = f.valType;
	    } else {
		valType.checkSameType(lineNum, f.valType, "Term");
	    }
	}
	for (Operator o = firstTop; o != null; o = o.nextOp) {
	    o.check(curDecls);
	    o.opType = valType;
	}
    }

    @Override
    void genCode(FuncDecl curFunc) {
	// -- Must be changed in part 2:
	for (Factor f = firstFactor; f != null; f = f.nextFactor) {
	    f.genCode(curFunc);
	}
	
    }

    /*
     * Helpermethod that removes ugly list operations from the parse method
     */
    void addTop(TermOperator top) {
	if (firstTop == null) {
	    firstTop = top;
	} else {
	    Operator tmp = firstTop;
	    while (tmp.nextOp != null) {
		tmp = tmp.nextOp;
	    }
	    tmp.nextOp = top;
	}
    }

    /*
     * Helpermethod that removes ugly list operations from the parse method
     */
    void addFactor(Factor factor) {
	if (firstFactor == null) {
	    firstFactor = factor;
	} else {
	    Factor tmp = firstFactor;
	    while (tmp.nextFactor != null) {
		tmp = tmp.nextFactor;
	    }
	    tmp.nextFactor = factor;
	}
    }

    @Override
    void parse() {
	Log.enterParser("<term>");
	firstFactor.parse();
	while (Token.isTermOperator(Scanner.curToken)) {
	    TermOperator tmpTop = new TermOperator();
	    addTop(tmpTop);
	    tmpTop.parse();
	    Factor tmpFactor = new Factor();
	    addFactor(tmpFactor);
	    tmpFactor.parse();
	}
	Log.leaveParser("</term>");
    }

    @Override
    void printTree() {
	Factor f = firstFactor;
	Operator top = firstTop;
	f.printTree();
	f = f.nextFactor;
	while (f != null) {
	    top.printTree();
	    f.printTree();
	    top = top.nextOp;
	    f = f.nextFactor;
	}
    }
}

class Factor extends Operand {
    Factor nextFactor; // Used in term, not referenced in this class itself
    Operator firstFo;
    Operand firstOperand;

    @Override
    void check(DeclList curDecls) {
	for (Operand o = firstOperand; o != null; o = o.nextOperand) {
	    o.check(curDecls);
	    if (valType == null) {
		valType = o.valType;
	    } else {
		valType.checkSameType(lineNum, o.valType, "Factor");
	    }
	}
	for (Operator o = firstFo; o != null; o = o.nextOp) {
	    o.check(curDecls);
	    o.opType = valType;
	}
    }

    @Override
    void genCode(FuncDecl curFunc) {
	Log.w("Factor.genCode");
	firstOperand.genCode(curFunc);
    }

    /*
     * Helpermethod that removes ugly list operations from the parse method
     */
    void addOperand(Operand op) {
	if (firstOperand == null) {
	    firstOperand = op;
	} else {
	    Operand tmp = firstOperand;
	    while (tmp.nextOperand != null) {
		tmp = tmp.nextOperand;
	    }
	    tmp.nextOperand = op;
	}
    }

    /*
     * Helpermethod that removes ugly list operations from the parse method
     */
    void addOperator(Operator op) {
	if (firstFo == null) {
	    firstFo= op;
	} else {
	    Operator tmp = firstFo;
	    while (tmp.nextOp != null) {
		tmp = tmp.nextOp;
	    }
	    tmp.nextOp = op;
	}
    }

    /*
     * A method that decides what kind of operand this factor consists of, 
     * it will make the operand and add it to the list via the addOperand method
     * it also parses the operand in question, so a call to this method assumes the scanner is 
     * at a position where it can legally read a operand, otherwise it will report a error.
     */
    void makeOperand() {
	if (Scanner.curToken == numberToken) {
	    Number op = new Number();
	    addOperand(op);
	    op.parse();
	} else if (Scanner.curToken == nameToken && Scanner.nextToken == leftParToken) {
	    FunctionCall op = new FunctionCall();
	    addOperand(op);
	    op.parse();
	} else if (Scanner.curToken == nameToken) {
	    Variable op = new Variable();
	    addOperand(op);
	    op.parse();
	} else if (Scanner.curToken == leftParToken) {
	    Scanner.skip(leftParToken);
	    Expression op = new Expression();
	    op.innerExpr = true;
	    addOperand(op);
	    op.parse();
	    Scanner.skip(rightParToken);
	} else {
	    Error.expected("An operand");
	}
    }

    @Override
    void parse() {
	Log.enterParser("<factor>");
	while (true) {
	    Log.enterParser("<operand>");
	    makeOperand();
	    Log.leaveParser("</operand>");
	    if (Token.isFactorOperator(Scanner.curToken)) {
		Operator tmp = new FactOperator();
		addOperator(tmp);
		tmp.parse();
	    } else {
		break;
	    }
	}
	Log.leaveParser("</factor>");
    }

    @Override
    void printTree() {
	Operand operand = firstOperand;
	Operator op = firstFo;
	operand.printTree();
	while (op != null) {
	    operand = operand.nextOperand;
	    op.printTree();
	    operand.printTree();
	    op = op.nextOp;
	}
    }
}


/*
 * An <operator>
 */
abstract class Operator extends SyntaxUnit {
    Operator nextOp = null;
    Type opType;
    Token opToken;

    @Override
    void check(DeclList curDecls) {
	
    }
}


class FactOperator extends Operator {
    @Override
    void genCode(FuncDecl curFunc) {
	Log.w("FactOperator.genCode");
    }

    @Override
    void parse() {
	Log.enterParser("<factor operator>");
	if (Token.isFactorOperator(Scanner.curToken)) {
	    opToken = Scanner.curToken;
	    Scanner.skip(Scanner.curToken);
	} else {
	    Error.expected("An factor operator");
	}
	Log.leaveParser("</factor operator>");
    }

    @Override
    void printTree() {
	if (opToken == multiplyToken) {
	    Log.wTree(" * ");
	} else {
	    Log.wTree(" / ");
	}
    }
}

class TermOperator extends Operator {
    @Override
    void genCode(FuncDecl curFunc) {
	Log.w("TermOperator.genCode");
    }

    @Override
    void parse() {
	Log.enterParser("<term operator>");
	opToken = Scanner.curToken;
	Scanner.skip(Scanner.curToken);
	Log.leaveParser("</term operator>");
    }

    @Override
    void printTree() {
	if (opToken == addToken) {
	    Log.wTree(" + ");
	} else {
	    Log.wTree(" - ");
	}
    }
}

/*
 * A relational operator (==, !=, <, <=, > or >=).
 */

class RelOperator extends Operator {
    @Override
    void genCode(FuncDecl curFunc) {
	if (opType == Types.doubleType) {
	    Code.genInstr("", "fldl", "(%esp)", "");
	    Code.genInstr("", "addl", "$8,%esp", "");
	    Code.genInstr("", "fsubp", "", "");
	    Code.genInstr("", "fstps", Code.tmpLabel, "");
	    Code.genInstr("", "cmpl", "$0," + Code.tmpLabel, "");
	} else {
	    Code.genInstr("", "popl", "%ecx", "");
	    Code.genInstr("", "cmpl", "%eax,%ecx", "");
	}
	Code.genInstr("", "movl", "$0,%eax", "");
	switch (opToken) {
	case equalToken:
	    Code.genInstr("", "sete", "%al", "Test ==");
	    break;
	case notEqualToken:
	    Code.genInstr("", "setne", "%al", "Test !=");
	    break;
	case lessToken:
	    Code.genInstr("", "setl", "%al", "Test <");
	    break;
	case lessEqualToken:
	    Code.genInstr("", "setle", "%al", "Test <=");
	    break;
	case greaterToken:
	    Code.genInstr("", "setg", "%al", "Test >");
	    break;
	case greaterEqualToken:
	    Code.genInstr("", "setge", "%al", "Test >=");
	    break;
	}
    }

    @Override
    void parse() {
	Log.enterParser("<rel operator>");
	opToken = Scanner.curToken;
	opType = Types.intType;
	Scanner.readNext();
	Log.leaveParser("</rel operator>");
    }

    @Override
    void printTree() {
	String op = "?";
	switch (opToken) {
	case equalToken:
	    op = "==";
	    break;
	case notEqualToken:
	    op = "!=";
	    break;
	case lessToken:
	    op = "<";
	    break;
	case lessEqualToken:
	    op = "<=";
	    break;
	case greaterToken:
	    op = ">";
	    break;
	case greaterEqualToken:
	    op = ">=";
	    break;
	}
	Log.wTree(" " + op + " ");
    }
}


/*
 * An <operand>
 */
abstract class Operand extends SyntaxUnit {
    Operand nextOperand = null;
    Type valType;
}

/*
 * A <function call>.
 */
class FunctionCall extends Operand {
    String functionName;
    ExprList arguments;
    FuncDecl declRef;
    FunctionCall() {
	functionName = null;
	arguments = new ExprList();
    }

    @Override
    void check(DeclList curDecls) {
	Declaration d = curDecls.findDecl(functionName, this);
	if (d == null) Syntax.error(this, functionName + " is not defined.");
	if (d != null) {
	    declRef = (FuncDecl)d;
	    arguments.check(curDecls);
	    valType = declRef.type;
	}
	declRef.checkWhetherFunction(arguments.nExprs, this);
	Expression argument = arguments.firstExpr;
	for (Declaration param = declRef.functionParameters.firstDecl; param != null; param = param.nextDecl) {
	    if (param.type != argument.valType) Syntax.error(this, param.name + " is " + param.type.typeName() + " not " + argument.valType.typeName());
	    argument = argument.nextExpr;
	}
    }

    @Override
    void genCode(FuncDecl curFunc) {
	// -- Must be changed in part 2:
	arguments.genCode(curFunc);
	Code.genInstr("", "call", declRef.assemblerName, "Call " + functionName);
	int size = 0;
	for (Expression e = arguments.firstExpr; e != null; e = e.nextExpr) {
	    size += e.valType.size();
	}
	Code.genInstr("", "addl", "$" + size + ",%esp", "Remove parameters");
	     
    }

    @Override
    void parse() {
	Log.enterParser("<function call>");
	functionName = Scanner.curName;
	Scanner.skip(nameToken);
	Scanner.skip(leftParToken);
	arguments.parse();
	Scanner.skip(rightParToken);
	Log.leaveParser("</function call>");
    }

    @Override
    void printTree() {
	Log.wTree(functionName + "(");
	arguments.printTree();
	Log.wTree(")");
    }
}


/*
 * A <number>.
 */
class Number extends Operand {
    int numVal;

    @Override
    void check(DeclList curDecls) {
    }

    @Override
    void genCode(FuncDecl curFunc) {
	Code.genInstr("", "movl", "$" + numVal + ",%eax", "" + numVal);
    }

    @Override
    void parse() {
	Log.enterParser("<number>");
	numVal = Scanner.curNum;
	valType = Types.intType;
	Scanner.skip(numberToken);
	Log.leaveParser("</number>");
    }

    @Override
    void printTree() {
	Log.wTree("" + numVal);
    }
}


/*
 * A <variable>.
 */

class Variable extends Operand {
    String varName;
    VarDecl declRef = null;
    Expression index = null;

    @Override
    void check(DeclList curDecls) {
	Declaration d = curDecls.findDecl(varName, this);
	if (index == null) {
	    d.checkWhetherSimpleVar(this);
	    valType = d.type;
	} else {
	    d.checkWhetherArray(this);
	    index.check(curDecls);
	    index.valType.checkType(lineNum, Types.intType, "Array index");
	    valType = ((ArrayType)d.type).elemType;
	}
	declRef = (VarDecl)d;
    }

    @Override
    void genCode(FuncDecl curFunc) {
	Code.genInstr("", "movl", varName + ",%eax", varName);
    }

    @Override
    void parse() {
	Log.enterParser("<variable>");
	varName = Scanner.curName;
	Scanner.skip(nameToken);
	if (Scanner.curToken == leftBracketToken) { // Bracket is optional
	    Scanner.skip(leftBracketToken);
	    index = new Expression();
	    index.parse();
	    Scanner.skip(rightBracketToken);
	}
	Log.leaveParser("</variable>");
    }

    @Override
    void printTree() {
	Log.wTree(varName);
	if (index != null) {
	    Log.wTree("[");
	    index.printTree();
	    Log.wTree("]");
	}
    }
}
