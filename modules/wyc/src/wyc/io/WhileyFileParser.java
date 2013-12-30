// Copyright (c) 2011, David J. Pearce (djp@ecs.vuw.ac.nz)
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
//    * Redistributions of source code must retain the above copyright
//      notice, this list of conditions and the following disclaimer.
//    * Redistributions in binary form must reproduce the above copyright
//      notice, this list of conditions and the following disclaimer in the
//      documentation and/or other materials provided with the distribution.
//    * Neither the name of the <organization> nor the
//      names of its contributors may be used to endorse or promote products
//      derived from this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
// ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
// WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
// DISCLAIMED. IN NO EVENT SHALL DAVID J. PEARCE BE LIABLE FOR ANY
// DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
// (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
// LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
// ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
// (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
// SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

package wyc.io;

import java.io.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

import wybs.lang.Attribute;
import wybs.lang.Path;
import wybs.lang.SyntaxError;
import wybs.util.Pair;
import wybs.util.Trie;
import wyc.lang.*;
import wyil.lang.*;
import static wyc.io.WhileyFileLexer.*;

/**
 * Convert a list of tokens into an Abstract Syntax Tree (AST) representing the
 * original source file in question. No effort is made to check whether or not
 * the generated tree is syntactically correct. Subsequent stages of the
 * compiler are responsible for doing this.
 * 
 * @author David J. Pearce
 * 
 */
public final class WhileyFileParser {
	private String filename;
	private ArrayList<Token> tokens;	
	private int index;
	
	public WhileyFileParser(String filename, List<Token> tokens) {
		this.filename = filename;
		this.tokens = new ArrayList<Token>(tokens); 		
	}
	public WhileyFile read() {		
		Path.ID pkg = parsePackage();
		
		// Now, figure out module name from filename
		String name = filename.substring(filename.lastIndexOf(File.separatorChar) + 1,filename.length()-7);		
		WhileyFile wf = new WhileyFile(pkg.append(name),filename);
		
		while(index < tokens.size()) {			
			Token t = tokens.get(index);
			if (t instanceof NewLine || t instanceof LineComment|| t instanceof BlockComment) {
				matchEndLine();
			} else if(t instanceof Keyword) {
				Keyword k = (Keyword) t;
				if(k.text.equals("import")) {					
					parseImport(wf);
				} else {
					List<Modifier> modifiers = parseModifiers();
					
					t = tokens.get(index);
					
					if (t.text.equals("define")) {
						parseDefType(modifiers, wf);
					} else {
						parseFunctionOrMethod(modifiers, wf);
					} 
				}
			} else {
				parseFunctionOrMethod(new ArrayList<Modifier>(),wf);				
			}			
		}
		
		return wf;
	}
	
	private Trie parsePackage() {
		
		while (index < tokens.size()
				&& (tokens.get(index) instanceof LineComment || tokens.get(index) instanceof NewLine)) {			
			parseSkip();
		}
		
		Trie pkg = Trie.ROOT;
		
		if(index < tokens.size() && tokens.get(index).text.equals("package")) {			
			matchKeyword("package");
			
			pkg = pkg.append(matchIdentifier().text);
						
			while (index < tokens.size() && tokens.get(index) instanceof Dot) {
				match(Dot.class);
				pkg = pkg.append(matchIdentifier().text);
			}

			matchEndLine();
			return pkg;
		} else {
			return pkg; // no package
		}
	}
	
	private void parseImport(WhileyFile wf) {
		int start = index;
		matchKeyword("import");
		
		// first, check if from is used
		String name = null;
		if ((index + 1) < tokens.size()
				&& tokens.get(index + 1).text.equals("from")) {
			Token t = tokens.get(index);
			if (t.text.equals("*")) {
				match(Star.class);
				name = "*";
			} else {
				name = matchIdentifier().text;
			}
			matchIdentifier();
		}
				
		Trie filter = Trie.ROOT.append(matchIdentifier().text);
		
		while (index < tokens.size()) {
			Token lookahead = tokens.get(index);
			if(lookahead instanceof Dot) {
				match(Dot.class);							
			} else if(lookahead instanceof DotDot) {
				match(DotDot.class);
				filter = filter.append("**");
			} else {
				break;
			}
			
			if(index < tokens.size()) {
				Token t = tokens.get(index);
				if(t.text.equals("*")) {
					match(Star.class);
					filter = filter.append("*");	
				} else {
					filter = filter.append(matchIdentifier().text);
				}
			}
		}
							
		int end = index;
		matchEndLine();
		
		wf.add(new WhileyFile.Import(filter, name, sourceAttr(start,
				end - 1)));
	}
	
	private void parseFunctionOrMethod(List<Modifier> modifiers, WhileyFile wf) {			
		int start = index;		
		SyntacticType ret = parseType();				
		// FIXME: potential bug here at end of file		
		boolean method = false;
		
		List<WhileyFile.Parameter> paramTypes = new ArrayList();
		HashSet<String> paramNames = new HashSet<String>();

		if (tokens.get(index) instanceof ColonColon) {
			// headless method
			method = true;
			match(ColonColon.class);
		} else if (tokens.get(index + 1) instanceof ColonColon) {
			method = true;
			int pstart = index;
			SyntacticType t = parseType();
			match(ColonColon.class);
			paramTypes.add(wf.new Parameter(t, "this", sourceAttr(pstart,
					index - 1)));
		}
		
		Identifier name = matchIdentifier();						
		
		paramTypes.addAll(parseParameterSequence(wf,LeftBrace.class,RightBrace.class));
				
		SyntacticType throwType = parseThrowsClause();
		Pair<List<Expr>, List<Expr>> conditions = parseRequiresEnsures(wf);
		match(Colon.class);
		int end = index;
		matchEndLine();
		
		List<Stmt> stmts = parseBlock(wf,0);
		WhileyFile.Declaration declaration;
		if(method) {
			declaration = wf.new Method(modifiers, name.text, ret, paramTypes,
					conditions.first(), conditions.second(), throwType, stmts,
					sourceAttr(start, end - 1));
		} else {
			declaration = wf.new Function(modifiers, name.text, ret, paramTypes,
					conditions.first(), conditions.second(), throwType, stmts,
					sourceAttr(start, end - 1));
		}
		wf.add(declaration);
	}
	
	private void parseDefType(List<Modifier> modifiers, WhileyFile wf) {		
		int start = index; 
		matchKeyword("define");
		
		Identifier name = matchIdentifier();		
		
		matchKeyword("as");
		
		int mid = index;
		
		// At this point, there are two possibilities. Either we have a type
		// constructor, or we have an expression (which should correspond to a
		// constant).
		
		try {			
			SyntacticType t = parseType();	
			Expr constraint = null;
			if (index < tokens.size() && tokens.get(index).text.equals("where")) {
				// this is a constrained type
				matchKeyword("where");
				
				constraint = parseCondition(wf,false);
			}
			int end = index;			
			matchEndLine();			
			WhileyFile.Declaration declaration = wf.new TypeDef(modifiers, t, name.text, constraint, sourceAttr(start,end-1));
			wf.add(declaration);
			return;
		} catch(Exception e) {}
		
		// Ok, failed parsing type constructor. So, backtrack and try for
		// expression.
		index = mid;		
		Expr e = parseCondition(wf,false);
		int end = index;
		matchEndLine();		
		WhileyFile.Declaration declaration = wf.new Constant(modifiers, e, name.text, sourceAttr(start,end-1));
		wf.add(declaration);
	}
	
	private List<Modifier> parseModifiers() {
		ArrayList<Modifier> mods = new ArrayList<Modifier>();
		Token lookahead;
		while (index < tokens.size()
				&& isModifier((lookahead = tokens.get(index)))) {
			if(lookahead.text.equals("public")) {
				mods.add(Modifier.PUBLIC);
			} else if(lookahead.text.equals("protected")) {
				mods.add(Modifier.PROTECTED);
			} else if(lookahead.text.equals("private")) {
				mods.add(Modifier.PRIVATE);
			} else if(lookahead.text.equals("native")) {
				mods.add(Modifier.NATIVE);
			} else if(lookahead.text.equals("export")) {
				mods.add(Modifier.EXPORT);
			} 
			index = index + 1;
		}
		return mods;
	}
	
	private String[] modifiers = {
			"public",
			"export",
			"native"			
	};
	
	private boolean isModifier(Token tok) {
		for(String m : modifiers) {
			if(tok.text.equals(m)) {
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Parse a block nested with a given parent indent. Thus, the indentation of
	 * this block must be greater than that of its parent.
	 * 
	 * @param parentIndent
	 * @return
	 */
	private List<Stmt> parseBlock(WhileyFile wf, int parentIndent) {
		// first, determine the new indent level for this block.
		int indent = getIndent();
		
		if(indent <= parentIndent) {
			return Collections.EMPTY_LIST; // empty block
		} else {
			parentIndent = indent;
			
			// second, parse all statements until the indent level changes.
			ArrayList<Stmt> stmts = new ArrayList<Stmt>();			
			while (indent == parentIndent && index < tokens.size()) {
				parseIndent(parentIndent);
				if(index < tokens.size()) {
					stmts.add(parseStatement(wf,parentIndent));
					indent = getIndent();
				}
			}
			
			return stmts;
		}
	}
	
	private void parseIndent(int indent) {
		if (index < tokens.size()) {
			Token t = tokens.get(index);
			if (t instanceof Indent && ((Indent) t).indent == indent) {
				index = index + 1;
			} else {
				syntaxError("unexpected end-of-block", t);
			}
		} else {
			throw new SyntaxError("unexpected end-of-file", filename, index,
					index);
		}
	}
	
	private int getIndent() {
		if (index < tokens.size() && tokens.get(index) instanceof Indent) {
			return ((Indent) tokens.get(index)).indent;
		} else if (index < tokens.size()
				&& tokens.get(index) instanceof LineComment) {
			// This indicates a completely empty line. In which case, we just
			// ignore it.
			matchEndLine();
			return getIndent();
		} else {
			return 0;
		}
	}
	
	private SyntacticType parseThrowsClause() {
		checkNotEof();
		if (index < tokens.size() && tokens.get(index).text.equals("throws")) {
			matchKeyword("throws");
			return parseType();
		}
		return new SyntacticType.Void();
	}
	
	private Pair<List<Expr>, List<Expr>> parseRequiresEnsures(WhileyFile wf) {
		skipWhiteSpace();
		checkNotEof();
		
		ArrayList<Expr> requires = new ArrayList<Expr>();
		ArrayList<Expr> ensures = new ArrayList<Expr>();
		
		boolean firstTime = true;	
		while (index < tokens.size() && !(tokens.get(index) instanceof Colon)) {
			if (!firstTime) {
				match(Comma.class);
			}
			firstTime = false;
			Token lookahead = tokens.get(index);
			if (lookahead.text.equals("requires")) {
				matchKeyword("requires");
				requires.add(parseCondition(wf, false));
			} else if (lookahead.text.equals("ensures")) {
				matchKeyword("ensures");
				ensures.add(parseCondition(wf, false));
			} else {
				syntaxError("expected requires or ensures.", lookahead);
			}
		}

		return new Pair<List<Expr>, List<Expr>>(requires, ensures);
	}
	
	private Stmt parseStatement(WhileyFile wf, int indent) {		
		checkNotEof();
		Token token = tokens.get(index);
		
		if(token.text.equals("skip")) {
			return parseSkip();
		} else if(token.text.equals("return")) {
			return parseReturn(wf);
		} else if(token.text.equals("assert")) {
			return parseAssertOrAssume(wf,false);
		} else if(token.text.equals("assume")) {
			return parseAssertOrAssume(wf,true);
		} else if(token.text.equals("debug")) {
			return parseDebug(wf);
		} else if(token.text.equals("if")) {			
			return parseIf(wf,indent);
		} else if(token.text.equals("switch")) {			
			return parseSwitch(wf,indent);
		} else if(token.text.equals("try")) {			
			return parseTryCatch(wf,indent);
		} else if(token.text.equals("break")) {			
			return parseBreak(indent);
		} else if(token.text.equals("continue")) {			
			return parseContinue(indent);
		} else if(token.text.equals("throw")) {			
			return parseThrow(wf,indent);
		} else if(token.text.equals("do")) {			
			return parseDoWhile(wf,indent);
		} else if(token.text.equals("while")) {			
			return parseWhile(wf,indent);
		} else if(token.text.equals("for")) {			
			return parseFor(wf,indent);
		} else if(token.text.equals("new")) {			
			return parseNew(wf);
		} else if ((index + 1) < tokens.size()
				&& tokens.get(index + 1) instanceof LeftBrace) {
			// must be a method invocation
			return parseInvokeStmt(wf);			
		} else {
			int start = index;
			Expr t = parseTupleExpression(wf);
			if(t instanceof Expr.AbstractInvoke) {
				matchEndLine();
				return (Expr.AbstractInvoke) t;
			} else {
				index = start;
				return parseAssign(wf);
			}
		}
	}		
	
	private Expr.AbstractInvoke parseInvokeStmt(WhileyFile wf) {				
		int start = index;
		Identifier name = matchIdentifier();		
		match(LeftBrace.class);
		boolean firstTime=true;
		ArrayList<Expr> args = new ArrayList<Expr>();
		while (index < tokens.size()
				&& !(tokens.get(index) instanceof RightBrace)) {
			if(!firstTime) {
				match(Comma.class);
			} else {
				firstTime=false;
			}			
			Expr e = parseBitwiseExpression(wf,false);
			args.add(e);
			
		}
		match(RightBrace.class);
		int end = index;
		matchEndLine();				
		
		// no receiver is possible in this case.
		return new Expr.AbstractInvoke(name.text, null, args, sourceAttr(start,
				end - 1));
	}
	
	private Stmt parseReturn(WhileyFile wf) {
		int start = index;
		matchKeyword("return");
		Expr e = null;
		if (index < tokens.size()
				&& !(tokens.get(index) instanceof NewLine || tokens.get(index) instanceof LineComment)) {
			e = parseTupleExpression(wf);
		}
		int end = index;
		matchEndLine();
		return new Stmt.Return(e, sourceAttr(start, end - 1));
	}
	
	private Stmt parseAssertOrAssume(WhileyFile wf, boolean isAssume) {
		int start = index;
		if(isAssume) {
			matchKeyword("assume");
		} else {
			matchKeyword("assert");
		}
		checkNotEof();
		Expr e = parseCondition(wf,false);
		int end = index;
		matchEndLine();
		if(isAssume) {
			return new Stmt.Assume(e, sourceAttr(start,end-1));
		} else {
			return new Stmt.Assert(e, sourceAttr(start,end-1));
		}
	}
	
	private Stmt parseSkip() {
		int start = index;
		matchKeyword("skip");
		matchEndLine();		
		return new Stmt.Skip(sourceAttr(start,index-1));
	}
	
	private Stmt parseDebug(WhileyFile wf) {		
		int start = index;
		matchKeyword("debug");		
		checkNotEof();
		Expr e = parseBitwiseExpression(wf,false);
		int end = index;
		matchEndLine();		
		return new Stmt.Debug(e, sourceAttr(start,end-1));
	}
	
	private Stmt parseIf(WhileyFile wf, int indent) {
		int start = index;
		matchKeyword("if");						
		Expr c = parseCondition(wf, false);								
		match(Colon.class);
		int end = index;
		matchEndLine();
		List<Stmt> tblk = parseBlock(wf,indent);				
		List<Stmt> fblk = Collections.EMPTY_LIST;
		
		if ((index+1) < tokens.size() && tokens.get(index) instanceof Indent) {
			Indent ts = (Indent) tokens.get(index);			
			if(ts.indent == indent && tokens.get(index+1).text.equals("else")) {
				match(Indent.class);
				matchKeyword("else");
				
				if(index < tokens.size() && tokens.get(index).text.equals("if")) {
					Stmt if2 = parseIf(wf,indent);
					fblk = new ArrayList<Stmt>();
					fblk.add(if2);
				} else {
					match(Colon.class);
					matchEndLine();
					fblk = parseBlock(wf,indent);
				}
			}
		}		
		
		return new Stmt.IfElse(c,tblk,fblk, sourceAttr(start,end-1));
	}
	
	private Stmt.Case parseCase(WhileyFile wf, int indent) {
		checkNotEof();
		int start = index;
		List<Expr> values;
		if(index < tokens.size() && tokens.get(index).text.equals("default")) {				
			matchKeyword("default");
			values = Collections.EMPTY_LIST;			
		} else {
			matchKeyword("case");
			values = new ArrayList<Expr>();
			values.add(parseCondition(wf, false));
			while(index < tokens.size() && tokens.get(index) instanceof Comma) {				
				match(Comma.class);
				values.add(parseCondition(wf, false));
			}
		}		
		match(Colon.class);
		int end = index;
		matchEndLine();		
		List<Stmt> stmts = parseBlock(wf,indent);
		return new Stmt.Case(values,stmts,sourceAttr(start,end-1));
	}
	
	private ArrayList<Stmt.Case> parseCaseBlock(WhileyFile wf, int parentIndent) {
		int indent = getIndent();
		if (indent <= parentIndent) {
			return new ArrayList(); // / empty case block
		} else {
			parentIndent = indent;

			// second, parse all statements until the indent level changes.
			ArrayList<Stmt.Case> cases = new ArrayList<Stmt.Case>();
			while (indent == parentIndent && index < tokens.size()) {
				parseIndent(parentIndent);
				if(index < tokens.size()) {
					cases.add(parseCase(wf,parentIndent));
					indent = getIndent();
				}
			}

			return cases;
		}
	}
	
	private Stmt parseSwitch(WhileyFile wf, int indent) {
		int start = index;
		matchKeyword("switch");
		Expr c = parseBitwiseExpression(wf, false);								
		match(Colon.class);
		int end = index;
		matchEndLine();
		ArrayList<Stmt.Case> cases = parseCaseBlock(wf,indent);		
		return new Stmt.Switch(c, cases, sourceAttr(start,end-1));
	}
	
	private Stmt.Catch parseCatch(WhileyFile wf, int indent) {
		checkNotEof();
		int start = index;		
		matchKeyword("catch");
		match(LeftBrace.class);
		SyntacticType type = parseType();
		String variable = matchIdentifier().text;
		match(RightBrace.class);
		match(Colon.class);
		int end = index;
		matchEndLine();		
		List<Stmt> stmts = parseBlock(wf,indent);
		return new Stmt.Catch(type,variable,stmts,sourceAttr(start,end-1));
	}
	
	private ArrayList<Stmt.Catch> parseCatchBlock(WhileyFile wf, int parentIndent) {
		int indent = getIndent();
		ArrayList<Stmt.Catch> catches = new ArrayList<Stmt.Catch>();
		while (indent == parentIndent && (index+1) < tokens.size()
				&& tokens.get(index+1).text.equals("catch")) {
			parseIndent(parentIndent);
			if(index < tokens.size()) {
				catches.add(parseCatch(wf, parentIndent));
				indent = getIndent();
			}
		}

		return catches;
	}
	
	private Stmt parseTryCatch(WhileyFile wf, int indent) {
		int start = index;
		matchKeyword("try");									
		match(Colon.class);
		int end = index;
		matchEndLine();
		List<Stmt> blk = parseBlock(wf,indent);
		List<Stmt.Catch> catches = parseCatchBlock(wf,indent);		
		return new Stmt.TryCatch(blk, catches, sourceAttr(start,end-1));
	}
	
	private Stmt parseThrow(WhileyFile wf, int indent) {
		int start = index;
		matchKeyword("throw");
		Expr c = parseBitwiseExpression(wf, false);
		int end = index;
		matchEndLine();		
		return new Stmt.Throw(c,sourceAttr(start,end-1));
	}
	
	private Stmt parseBreak(int indent) {
		int start = index;
		matchKeyword("break");
		int end = index;
		matchEndLine();		
		return new Stmt.Break(sourceAttr(start,end-1));
	}
	
	private Stmt parseContinue(int indent) {
		int start = index;
		matchKeyword("continue");
		int end = index;
		matchEndLine();		
		return new Stmt.Continue(sourceAttr(start,end-1));
	}
	
	private Stmt parseWhile(WhileyFile wf, int indent) {
		int start = index;
		matchKeyword("while");						
		Expr condition = parseCondition(wf, false);
		List<Expr> invariants = new ArrayList<Expr>();
		boolean firstTime = true;
		while(index < tokens.size() && !(tokens.get(index) instanceof Colon)) {
			if(!firstTime) {
				match(Comma.class);
			}
			firstTime=false;
			matchKeyword("where");
			invariants.add(parseCondition(wf, false));
		}		
		match(Colon.class);
		int end = index;
		matchEndLine();
		List<Stmt> blk = parseBlock(wf, indent);								
		
		return new Stmt.While(condition,invariants,blk, sourceAttr(start,end-1));
	}
	
	private Stmt parseDoWhile(WhileyFile wf, int indent) {
		int start = index;
		matchKeyword("do");						
		Expr invariant = null;
		if (tokens.get(index).text.equals("where")) {
			matchKeyword("where");
			invariant = parseCondition(wf, false);
		}
		match(Colon.class);
		int end = index;
		matchEndLine();
		List<Stmt> blk = parseBlock(wf, indent);								
		parseIndent(indent);
		matchKeyword("while");
		Expr condition = parseCondition(wf, false);
		matchEndLine();
		
		return new Stmt.DoWhile(condition,invariant,blk, sourceAttr(start,end-1));
	}
	
	private Stmt parseFor(WhileyFile wf, int indent) {
		int start = index;
		matchKeyword("for");				
		ArrayList<String> variables = new ArrayList<String>();
		variables.add(matchIdentifier().text);				
		if(index < tokens.size() && tokens.get(index) instanceof Comma) {
			match(Comma.class);
			variables.add(matchIdentifier().text);
		}
		match(ElemOf.class);
		Expr source = parseCondition(wf, false);		
		Expr invariant = null;
		if(tokens.get(index).text.equals("where")) {
			matchKeyword("where");
			invariant = parseCondition(wf, false);
		}
		match(Colon.class);
		int end = index;
		matchEndLine();
		List<Stmt> blk = parseBlock(wf, indent);								

		return new Stmt.ForAll(variables,source,invariant,blk, sourceAttr(start,end-1));
	}		
	
	private Stmt parseAssign(WhileyFile wf) {		
		// standard assignment
		int start = index;
		Expr.LVal lhs = parseTupleLVal(wf);							
		match(Equals.class);		
		Expr rhs = parseCondition(wf,false);
		int end = index;
		matchEndLine();
		return new Stmt.Assign(lhs, rhs, sourceAttr(start,
				end - 1));		
	}	
	
	private Expr.LVal parseTupleLVal(WhileyFile wf) {
		int start = index;
		Expr.LVal e = parseRationalLVal(wf);		
		if (index < tokens.size() && tokens.get(index) instanceof Comma) {
			// this is a rational destructuring
			ArrayList<Expr> exprs = new ArrayList<Expr>();
			exprs.add(e);
			while (index < tokens.size() && tokens.get(index) instanceof Comma) {
				match(Comma.class);
				exprs.add(parseRationalLVal(wf));
				checkNotEof();
			}
			return new Expr.Tuple(exprs,sourceAttr(start,index-1));
		} else {
			return e;
		}
	}

	private Expr.LVal parseRationalLVal(WhileyFile wf) {
		int start = index;
		Expr.LVal lhs = parseIndexLVal(wf);		
		if (index < tokens.size() && tokens.get(index) instanceof RightSlash) {
			// this is a rational destructuring
			match(RightSlash.class);
			checkNotEof();
			Expr.LVal rhs = parseIndexLVal(wf);			
			return new Expr.RationalLVal(lhs,rhs,sourceAttr(start,index-1));
		} else {
			return lhs;
		}
	}
	
	private Expr.LVal parseIndexLVal(WhileyFile wf) {
		checkNotEof();
		int start = index;
		Expr.LVal lhs = parseLVal(wf);
		
		if(index < tokens.size()) {
			Token lookahead = tokens.get(index);

			while (lookahead instanceof LeftSquare 
					|| lookahead instanceof Dot
					|| lookahead instanceof Question		
					|| lookahead instanceof Shreak
					|| lookahead instanceof RightArrow
					|| lookahead instanceof LeftBrace) {				
				if(lookahead instanceof LeftSquare) {
					match(LeftSquare.class);				

					lookahead = tokens.get(index);

					Expr rhs = parseAddSubExpression(wf);

					match(RightSquare.class);
					lhs = new Expr.IndexOf(lhs, rhs, sourceAttr(start,
							index - 1));					
				} else if(lookahead instanceof Dot || lookahead instanceof RightArrow) {				
					if(lookahead instanceof Dot) {
						match(Dot.class);
					} else {
						match(RightArrow.class);
						lhs = new Expr.Dereference(lhs,sourceAttr(start,index - 1));	
					}
					int tmp = index;
					String name = matchIdentifier().text; 						
					lhs =  new Expr.AbstractDotAccess(lhs, name, sourceAttr(start,index - 1));					
				} 
				if(index < tokens.size()) {
					lookahead = tokens.get(index);	
				} else {
					lookahead = null;
				}
			}
		}
		
		return lhs;		
	}
	
	private Expr.LVal parseLVal(WhileyFile wf) {
		checkNotEof();

		int start = index;
		Token token = tokens.get(index);

		if(token instanceof LeftBrace) {
			match(LeftBrace.class);
			
			checkNotEof();			
			Expr.LVal v = parseTupleLVal(wf);			
			
			checkNotEof();
			token = tokens.get(index);			
			match(RightBrace.class);
			return v;			 		
		} else if (token instanceof Identifier) {
			return new Expr.AssignedVariable(matchIdentifier().text,
					sourceAttr(start, index - 1));
		}

		syntaxError("unrecognised lval", token);
		return null;
	}
	
	private Expr parseTupleExpression(WhileyFile wf) {
		int start = index;
		Expr e = parseCondition(wf, false);		
		if (index < tokens.size() && tokens.get(index) instanceof Comma) {
			// this is a tuple constructor
			ArrayList<Expr> exprs = new ArrayList<Expr>();
			exprs.add(e);
			while (index < tokens.size() && tokens.get(index) instanceof Comma) {
				match(Comma.class);
				exprs.add(parseCondition(wf, false));
				checkNotEof();
			}
			return new Expr.Tuple(exprs,sourceAttr(start,index-1));
		} else {
			return e;
		}
	}
	
	/**
	 * The startSetComp flag is used to indicate whether this expression is the
	 * first first value of a set expression. This is necessary in order to
	 * disambiguate bitwise or from set comprehension. For example, consider
	 * this expression:
	 * 
	 * <pre>
	 * { x | x in xs, y == x }
	 * </pre>
	 * 
	 * To realise that this expression is a set comprehension, we must know that
	 * <code>x in xs</code> results a boolean type and, hence, cannot be
	 * converted into a <code>byte</code>. This kind of knowledge is beyond what
	 * the parser knows at this stage. Therefore, the first item of any set
	 * expression will not greedily consume the <code>|</code>. This means that,
	 * if we want a boolean set which uses bitwise or, then we have to
	 * disambiguate with braces like so:
	 * <pre>
	 * { (x|y), z } // valid set expression, not set comprehension
	 * </pre> 
	 * @param startSetComp
	 * @return
	 */
	private Expr parseCondition(WhileyFile wf, boolean startSet) {
		checkNotEof();
		int start = index;		
		Expr c1 = parseConditionExpression(wf, startSet);		
		
		if(index < tokens.size() && tokens.get(index) instanceof LogicalAnd) {			
			match(LogicalAnd.class);
			
			
			Expr c2 = parseCondition(wf, startSet);			
			return new Expr.BinOp(Expr.BOp.AND, c1, c2, sourceAttr(start,
					index - 1));
		} else if(index < tokens.size() && tokens.get(index) instanceof LogicalOr) {
			match(LogicalOr.class);
			
			
			Expr c2 = parseCondition(wf, startSet);
			return new Expr.BinOp(Expr.BOp.OR, c1, c2, sourceAttr(start,
					index - 1));			
		} 
		return c1;		
	}
		
	private Expr parseConditionExpression(WhileyFile wf, boolean startSet) {		
		int start = index;
		
		if (index < tokens.size()
				&& tokens.get(index) instanceof WhileyFileLexer.None) {
			match(WhileyFileLexer.None.class);
			
			
			Expr.Comprehension sc = parseQuantifierSet(wf);
			return new Expr.Comprehension(Expr.COp.NONE, null, sc.sources,
					sc.condition, sourceAttr(start, index - 1));
		} else if (index < tokens.size()
				&& tokens.get(index) instanceof WhileyFileLexer.Some) {
			match(WhileyFileLexer.Some.class);
			
			
			Expr.Comprehension sc = parseQuantifierSet(wf);			
			return new Expr.Comprehension(Expr.COp.SOME, null, sc.sources,
					sc.condition, sourceAttr(start, index - 1));			
		} else if (index < tokens.size()
				&& tokens.get(index) instanceof WhileyFileLexer.All) {
			match(WhileyFileLexer.All.class);
			
			
			Expr.Comprehension sc = parseQuantifierSet(wf);			
			return new Expr.Comprehension(Expr.COp.ALL, null, sc.sources,
					sc.condition, sourceAttr(start, index - 1));			
		} // could also do one and lone
		
		Expr lhs = parseBitwiseExpression(wf, startSet);
		
		if (index < tokens.size() && tokens.get(index) instanceof LessEquals) {
			match(LessEquals.class);				
			
			
			Expr rhs = parseBitwiseExpression(wf, startSet);
			return new Expr.BinOp(Expr.BOp.LTEQ, lhs,  rhs, sourceAttr(start,index-1));
		} else if (index < tokens.size() && tokens.get(index) instanceof LeftAngle) {
 			match(LeftAngle.class);				
 			
 			
 			Expr rhs = parseBitwiseExpression(wf, startSet);
			return new Expr.BinOp(Expr.BOp.LT, lhs,  rhs, sourceAttr(start,index-1));
		} else if (index < tokens.size() && tokens.get(index) instanceof GreaterEquals) {
			match(GreaterEquals.class);	
			
			
			Expr rhs = parseBitwiseExpression(wf, startSet);
			return new Expr.BinOp(Expr.BOp.GTEQ,  lhs,  rhs, sourceAttr(start,index-1));
		} else if (index < tokens.size() && tokens.get(index) instanceof RightAngle) {
			match(RightAngle.class);			
			
			
			Expr rhs = parseBitwiseExpression(wf, startSet);
			return new Expr.BinOp(Expr.BOp.GT, lhs,  rhs, sourceAttr(start,index-1));
		} else if (index < tokens.size() && tokens.get(index) instanceof EqualsEquals) {
			match(EqualsEquals.class);			
			
			
			Expr rhs = parseBitwiseExpression(wf, startSet);
			return new Expr.BinOp(Expr.BOp.EQ, lhs,  rhs, sourceAttr(start,index-1));
		} else if (index < tokens.size() && tokens.get(index) instanceof NotEquals) {
			match(NotEquals.class);									
			Expr rhs = parseBitwiseExpression(wf, startSet);			
			return new Expr.BinOp(Expr.BOp.NEQ, lhs,  rhs, sourceAttr(start,index-1));
		} else if (index < tokens.size() && tokens.get(index) instanceof WhileyFileLexer.InstanceOf) {
			return parseTypeEquals(lhs,start);			
		} else if (index < tokens.size() && tokens.get(index) instanceof WhileyFileLexer.ElemOf) {
			match(WhileyFileLexer.ElemOf.class);									
			Expr rhs = parseBitwiseExpression(wf, startSet);
			return new Expr.BinOp(Expr.BOp.ELEMENTOF,lhs,  rhs, sourceAttr(start,index-1));
		} else if (index < tokens.size() && tokens.get(index) instanceof WhileyFileLexer.SubsetEquals) {
			match(WhileyFileLexer.SubsetEquals.class);									
			Expr rhs = parseBitwiseExpression(wf, startSet);
			return new Expr.BinOp(Expr.BOp.SUBSETEQ, lhs, rhs, sourceAttr(start,index-1));
		} else if (index < tokens.size() && tokens.get(index) instanceof WhileyFileLexer.Subset) {
			match(WhileyFileLexer.Subset.class);									
			Expr rhs = parseBitwiseExpression(wf, startSet);
			return new Expr.BinOp(Expr.BOp.SUBSET, lhs,  rhs, sourceAttr(start,index-1));
		} else {
			return lhs;
		}	
	}
	
	private Expr parseTypeEquals(Expr lhs, int start) {
		match(WhileyFileLexer.InstanceOf.class);			
				
		SyntacticType type = parseType();
		Expr.TypeVal tc = new Expr.TypeVal(type, sourceAttr(start, index - 1));				
		
		return new Expr.BinOp(Expr.BOp.IS, lhs, tc, sourceAttr(start,
				index - 1));
	}
	
	private static boolean isBitwiseTok(Token tok, boolean startSet) {
		return tok instanceof Ampersand || tok instanceof Caret || (!startSet && tok instanceof Bar);
	}
	
	private static Expr.BOp bitwiseOp(Token tok) {
		if(tok instanceof Ampersand) {
			return Expr.BOp.BITWISEAND;
		} else if(tok instanceof Bar) {
			return Expr.BOp.BITWISEOR;
		} else {
			return Expr.BOp.BITWISEXOR;
		} 		
	}
	
	private Expr parseBitwiseExpression(WhileyFile wf, boolean startSet) {
		int start = index;
		ArrayList<Expr> exprs = new ArrayList<Expr>();
		ArrayList<Expr.BOp> ops = new ArrayList<Expr.BOp>();
		ArrayList<Integer> ends = new ArrayList<Integer>();
		exprs.add(parseShiftExpression(wf));
		
		while(index < tokens.size() && isBitwiseTok(tokens.get(index),startSet)) {
			Token token = tokens.get(index);
			match(token.getClass());
			ops.add(bitwiseOp(token));
			exprs.add(parseShiftExpression(wf));	
			ends.add(index-1);
		}
		
		Expr result = exprs.get(0);
		
		for(int i=1;i<exprs.size();++i) {
			Expr rhs = exprs.get(i);
			Expr.BOp bop = ops.get(i-1);			
			result = new Expr.BinOp(bop, result, rhs,  sourceAttr(start,
					ends.get(i-1)));
		}
		
		return result;
	}
	
	private static boolean isShiftTok(Token tok) {
		return tok instanceof LeftLeftAngle || tok instanceof RightRightAngle;
	}
	
	private static Expr.BOp shiftOp(Token tok) {
		if(tok instanceof LeftLeftAngle) {
			return Expr.BOp.LEFTSHIFT;
		} else {
			return Expr.BOp.RIGHTSHIFT;
		} 
	}
	
	private Expr parseShiftExpression(WhileyFile wf) {
		int start = index;
		ArrayList<Expr> exprs = new ArrayList<Expr>();
		ArrayList<Expr.BOp> ops = new ArrayList<Expr.BOp>();
		ArrayList<Integer> ends = new ArrayList<Integer>();
		exprs.add(parseRangeExpression(wf));
		
		while(index < tokens.size() && isShiftTok(tokens.get(index))) {
			Token token = tokens.get(index);
			match(token.getClass());
			ops.add(shiftOp(token));
			exprs.add(parseRangeExpression(wf));	
			ends.add(index);
		}
		
		Expr result = exprs.get(0);
		
		for(int i=1;i<exprs.size();++i) {
			Expr rhs = exprs.get(i);
			Expr.BOp bop = ops.get(i-1);			
			result = new Expr.BinOp(bop, result, rhs,  sourceAttr(start,
					ends.get(i-1)));
		}
		
		return result;
	}
	
	private Expr parseRangeExpression(WhileyFile wf) {
		int start = index;			
		Expr lhs = parseAddSubExpression(wf);		
		
		if(index < tokens.size() && tokens.get(index) instanceof DotDot) {			
			match(DotDot.class);
			Expr rhs = parseAddSubExpression(wf);
			return new Expr.BinOp(Expr.BOp.RANGE, lhs, rhs, sourceAttr(start,
					index - 1));
		} else {		
			return lhs;
		}
	}
	
	private static boolean isAddSubTok(Token tok) {
		return tok instanceof Plus || tok instanceof Minus
				|| tok instanceof Union || tok instanceof Intersection;
	}
	
	private static Expr.BOp addSubOp(Token tok) {
		if(tok instanceof Plus) {
			return Expr.BOp.ADD;
		} else if(tok instanceof Minus) {
			return Expr.BOp.SUB;
		} else if(tok instanceof Union) {
			return Expr.BOp.UNION;
		} else {
			return Expr.BOp.INTERSECTION;
		} 
	}
	
	private Expr parseAddSubExpression(WhileyFile wf) {
		int start = index;
		ArrayList<Expr> exprs = new ArrayList<Expr>();
		ArrayList<Expr.BOp> ops = new ArrayList<Expr.BOp>();
		ArrayList<Integer> ends = new ArrayList<Integer>();
		exprs.add(parseMulDivExpression(wf));
		
		while(index < tokens.size() && isAddSubTok(tokens.get(index))) {
			Token token = tokens.get(index);
			match(token.getClass());
			ops.add(addSubOp(token));
			exprs.add(parseMulDivExpression(wf));	
			ends.add(index-1);
		}
		
		Expr result = exprs.get(0);
		
		for(int i=1;i<exprs.size();++i) {
			Expr rhs = exprs.get(i);
			Expr.BOp bop = ops.get(i-1);			
			result = new Expr.BinOp(bop, result, rhs,  sourceAttr(start,
					ends.get(i-1)));
		}
		
		return result;
	}		
		
	private static boolean isMulDivTok(Token t) {
		return t instanceof Star || t instanceof RightSlash || t instanceof Percent;
	}
	
	private Expr.BOp mulDivOp(Token t) {
		if(t instanceof Star) {
			return Expr.BOp.MUL;
		} else if(t instanceof RightSlash) {
			return Expr.BOp.DIV;
		} else {
			return Expr.BOp.REM;
		}
	}
	
	private Expr parseMulDivExpression(WhileyFile wf) {
		int start = index;
		ArrayList<Expr> exprs = new ArrayList<Expr>();
		ArrayList<Expr.BOp> ops = new ArrayList<Expr.BOp>();
		ArrayList<Integer> ends = new ArrayList<Integer>();
		exprs.add(parseCastExpression(wf));
		
		while(index < tokens.size() && isMulDivTok(tokens.get(index))) {
			Token token = tokens.get(index);
			match(token.getClass());
			ops.add(mulDivOp(token));
			exprs.add(parseCastExpression(wf));	
			ends.add(index-1);
		}
		
		Expr result = exprs.get(0);
		
		for(int i=1;i<exprs.size();++i) {
			Expr rhs = exprs.get(i);
			Expr.BOp bop = ops.get(i-1);			
			result = new Expr.BinOp(bop, result, rhs,  sourceAttr(start,
					ends.get(i-1)));
		}
		
		return result;		
	}	
	
	private Expr parseCastExpression(WhileyFile wf) {
		Token lookahead = tokens.get(index);
		if(lookahead instanceof LeftBrace) {
			int start = index;
			try {
				match(LeftBrace.class);
				SyntacticType type = parseType();
				match(RightBrace.class);
				Expr expr = parseIndexTerm(wf);
				return new Expr.Cast(type, expr, sourceAttr(start,
						index - 1));
			} catch(SyntaxError e) {
				// ok, failed parsing the cast expression ... cannot be a cast
				// then!  restart assuming just an index term...
				index = start;
			}
		} 
		return parseIndexTerm(wf);		
	}
	
	private Expr parseIndexTerm(WhileyFile wf) {
		checkNotEof();
		int start = index;
		Expr lhs = parseTerm(wf);
		
		if(index < tokens.size()) {
			Token lookahead = tokens.get(index);

			while (lookahead instanceof LeftSquare 
					|| lookahead instanceof Dot
					|| lookahead instanceof Question		
					|| lookahead instanceof Shreak
					|| lookahead instanceof RightArrow
					|| lookahead instanceof LeftBrace) {				
				if(lookahead instanceof LeftSquare) {
					match(LeftSquare.class);				

					lookahead = tokens.get(index);

					if (lookahead instanceof DotDot) {
						// this indicates a sublist without a starting expression;
						// hence, start point defaults to zero
						match(DotDot.class);

						lookahead = tokens.get(index);
						Expr end = parseAddSubExpression(wf);
						match(RightSquare.class);
						return new Expr.SubList(lhs, new Expr.Constant(
								Constant.V_INTEGER(BigInteger.ZERO), sourceAttr(
										start, index - 1)), end, sourceAttr(
								start, index - 1));
					}

					Expr rhs = parseAddSubExpression(wf);

					lookahead = tokens.get(index);
					if(lookahead instanceof DotDot) {					
						match(DotDot.class);

						lookahead = tokens.get(index);
						Expr end;
						if(lookahead instanceof RightSquare) {
							// In this case, no end of the slice has been provided.
							// Therefore, it is taken to be the length of the source
							// expression.						
							end = new Expr.LengthOf(lhs, lhs
									.attribute(Attribute.Source.class));
						} else {
							end = parseBitwiseExpression(wf,false);						
						}
						match(RightSquare.class);
						lhs = new Expr.SubList(lhs, rhs, end, sourceAttr(start,
								index - 1));
					} else {
						match(RightSquare.class);							
						lhs = new Expr.IndexOf(lhs, rhs, sourceAttr(start,
								index - 1));
					}
				} else if(lookahead instanceof Dot || lookahead instanceof RightArrow) {				
					if(lookahead instanceof Dot) {
						match(Dot.class);
					} else {
						match(RightArrow.class);
						lhs = new Expr.Dereference(lhs,sourceAttr(start,index - 1));	
					}
					int tmp = index;
					String name = matchIdentifier().text; 	
					if(index < tokens.size() && tokens.get(index) instanceof LeftBrace) {
						// this indicates a method invocation.
						index = tmp; // slight backtrack
						Expr.AbstractInvoke<?> ivk = parseInvokeExpr(wf);							
						lhs = new Expr.AbstractInvoke(ivk.name, lhs, ivk.arguments,
								sourceAttr(
										start, index - 1));				
					} else {
						lhs =  new Expr.AbstractDotAccess(lhs, name, sourceAttr(start,index - 1));
					}
				} else if(lookahead instanceof Question) {
					match(Question.class);								 						
					Expr.AbstractInvoke<?> ivk = parseInvokeExpr(wf);							
					lhs = new Expr.AbstractInvoke(ivk.name, lhs, ivk.arguments,
							sourceAttr(
									start, index - 1));								
				} else {
					match(Shreak.class);								 						
					Expr.AbstractInvoke<?> ivk = parseInvokeExpr(wf);							
					lhs = new Expr.AbstractInvoke(ivk.name, lhs, ivk.arguments,
							sourceAttr(
									start, index - 1));								
				}
				if(index < tokens.size()) {
					lookahead = tokens.get(index);	
				} else {
					lookahead = null;
				}
			}
		}
		
		return lhs;		
	}
		
	private Expr parseTerm(WhileyFile wf) {		
		checkNotEof();		
		
		int start = index;
		Token token = tokens.get(index);		
		
		if(token instanceof LeftBrace) {
			match(LeftBrace.class);
			
			checkNotEof();			
			Expr v = parseTupleExpression(wf);			
			
			checkNotEof();
			token = tokens.get(index);			
			match(RightBrace.class);
			return v;			 		
		} else if(token instanceof Star) {
			// this indicates a process dereference
			match(Star.class);
			
			Expr e = parseTerm(wf);
			return new Expr.Dereference(e, sourceAttr(start,
					index - 1));
		} else if ((index + 1) < tokens.size()
				&& token instanceof Identifier
				&& tokens.get(index + 1) instanceof LeftBrace) {				
			// must be a method invocation			
			return parseInvokeExpr(wf);
		} else if (token.text.equals("null")) {
			matchKeyword("null");			
			return new Expr.Constant(Constant.V_NULL,
					sourceAttr(start, index - 1));
		} else if (token.text.equals("true")) {
			matchKeyword("true");			
			return new Expr.Constant(Constant.V_BOOL(true),
					sourceAttr(start, index - 1));
		} else if (token.text.equals("false")) {	
			matchKeyword("false");
			return new Expr.Constant(Constant.V_BOOL(false),
					sourceAttr(start, index - 1));			
		} else if(token.text.equals("new")) {
			return parseNew(wf);			
		} else if (token instanceof Identifier) {
			return new Expr.AbstractVariable(matchIdentifier().text, sourceAttr(start,
					index - 1));			
		} else if (token instanceof WhileyFileLexer.Byte) {			
			byte val = match(WhileyFileLexer.Byte.class).value;
			return new Expr.Constant(Constant.V_BYTE(val), sourceAttr(start, index - 1));
		} else if (token instanceof Char) {			
			char val = match(Char.class).value;
			return new Expr.Constant(Constant.V_CHAR(val), sourceAttr(start, index - 1));
		} else if (token instanceof Int) {			
			BigInteger val = match(Int.class).value;
			return new Expr.Constant(Constant.V_INTEGER(val), sourceAttr(start, index - 1));
		} else if (token instanceof Real) {
			BigDecimal val = match(Real.class).value;
			return new Expr.Constant(Constant.V_DECIMAL(val), sourceAttr(start,
					index - 1));			
		} else if (token instanceof Strung) {
			return parseString();
		} else if (token instanceof Minus) {
			return parseNegation(wf);
		} else if (token instanceof Bar) {
			return parseLengthOf(wf);
		} else if (token instanceof LeftSquare) {
			return parseListVal(wf);
		} else if (token instanceof LeftCurly) {
			return parseSetVal(wf);
		} else if (token instanceof EmptySet) {
			match(EmptySet.class);
			return new Expr.Constant(Constant.V_SET(new ArrayList<Constant>()),
					sourceAttr(start, index - 1));
		} else if (token instanceof Shreak) {
			match(Shreak.class);
			return new Expr.UnOp(Expr.UOp.NOT, parseIndexTerm(wf),
					sourceAttr(start, index - 1));
		} else if (token instanceof Tilde) {
			match(Tilde.class);
			return new Expr.UnOp(Expr.UOp.INVERT, parseIndexTerm(wf),
					sourceAttr(start, index - 1));
		} else if (token instanceof Ampersand) {
		      return parseLambda(wf);
	    }
		syntaxError("unrecognised term (" + token.text + ")",token);
		return null;		
	}
	
	private Expr parseLambda(WhileyFile wf) {
		int start = index;
		match(Ampersand.class);
		
		if (index < tokens.size() && tokens.get(index) instanceof LeftBrace) {
			// Indicates a lambda expression is given.
			List<WhileyFile.Parameter> parameters = parseParameterSequence(wf,
					LeftBrace.class, RightArrow.class);
			
			Expr body = parseCondition(wf,false);			
			match(RightBrace.class);
			
			return new Expr.Lambda(parameters,body,sourceAttr(start, index - 1));
		} else {
			// Indicates the address of an existing function is being taken.
			String funName = matchIdentifier().text;
			ArrayList<SyntacticType> paramTypes = null;

			if (index < tokens.size() && tokens.get(index) instanceof LeftBrace) {
				match(LeftBrace.class);
				ArrayList<WhileyFile.Parameter> parameters = new ArrayList<WhileyFile.Parameter>();
				ArrayList<Expr> arguments = new ArrayList<Expr>();

				boolean firstTime = true;
				int paramIndex = 0;
				while (index < tokens.size()
						&& !(tokens.get(index) instanceof RightBrace)) {
					if (!firstTime) {
						match(Comma.class);
					}
					firstTime = false;

					Expr argument = parseCondition(wf, false);

					if (argument instanceof Expr.AbstractVariable
							&& ((Expr.AbstractVariable) argument).var
									.equals("_")) {
						String name = "$_" + paramIndex;
						arguments.add(new Expr.LocalVariable(name, argument
								.attributes()));
						parameters.add(wf.new Parameter(null, name, argument
								.attributes()));
					} else {
						arguments.add(argument);
					}
					paramIndex++;
				}
				Expr.AbstractInvoke body = new Expr.AbstractInvoke(funName,
						null, arguments, sourceAttr(start, index - 1));
				match(RightBrace.class);
				return new Expr.Lambda(Collections.EMPTY_LIST, body,
						sourceAttr(start, index - 1));
			} else {
				return new Expr.AbstractFunctionOrMethod(funName, paramTypes,
						sourceAttr(start, index - 1));
			}
		}
	}

	private Expr.New parseNew(WhileyFile wf) {
		int start = index;
		matchKeyword("new");
		
		Expr state = parseBitwiseExpression(wf,false);
		return new Expr.New(state, sourceAttr(start,index - 1));
	}
	
	private Expr parseListVal(WhileyFile wf) {
		int start = index;
		ArrayList<Expr> exprs = new ArrayList<Expr>();
		match(LeftSquare.class);
		
		boolean firstTime = true;
		checkNotEof();
		Token token = tokens.get(index);
		while(!(token instanceof RightSquare)) {
			if(!firstTime) {
				match(Comma.class);
				
			}
			firstTime=false;
			exprs.add(parseCondition(wf,false));
			
			checkNotEof();
			token = tokens.get(index);
		}
		match(RightSquare.class);
		return new Expr.List(exprs, sourceAttr(start, index - 1));
	}
	
	private Expr.Comprehension parseQuantifierSet(WhileyFile wf) {
		int start = index;		
		match(LeftCurly.class);
		
		Token token = tokens.get(index);			
		boolean firstTime = true;						
		List<Pair<String,Expr>> srcs = new ArrayList<Pair<String,Expr>>();
		HashSet<String> vars = new HashSet<String>();
		while(!(token instanceof Bar)) {			
			if(!firstTime) {
				match(Comma.class);			
			}
			firstTime=false;
			Identifier id = matchIdentifier();
			
			String var = id.text;
			if(vars.contains(var)) {
				syntaxError(
						"variable "
								+ var
								+ " cannot have multiple source collections",
						id);
			} else {
				vars.add(var);
			}
			match(WhileyFileLexer.ElemOf.class);
			
			Expr src = parseConditionExpression(wf,true);			
			srcs.add(new Pair(var,src));
			
			checkNotEof();
			token = tokens.get(index);
		}
		match(Bar.class);		
		Expr condition = parseCondition(wf,false);
		
		match(RightCurly.class);
		return new Expr.Comprehension(Expr.COp.SETCOMP, null, srcs, condition,
				sourceAttr(start, index - 1));
	}
	
	private Expr parseSetVal(WhileyFile wf) {
		int start = index;		
		match(LeftCurly.class);
		
		ArrayList<Expr> exprs = new ArrayList<Expr>();	
		Token token = tokens.get(index);
		
		if(token instanceof RightCurly) {
			match(RightCurly.class);			
			// empty set definition
			Constant v = Constant.V_SET(Collections.EMPTY_LIST); 
			return new Expr.Constant(v, sourceAttr(start, index - 1));
		} else if(token instanceof StrongRightArrow) {
			match(StrongRightArrow.class);		
			match(RightCurly.class);			
			// empty dictionary definition
			Constant v = Constant.V_MAP(Collections.EMPTY_SET); 
			return new Expr.Constant(v, sourceAttr(start, index - 1));
		}
		
		// NOTE. need to indicate this is the start of a set expression. This is
		// necessary to ensure that the <code|</code> operator is not consumed
		// as a bitwise or.
		exprs.add(parseCondition(wf,true)); 
		
		
		boolean setComp = false;
		boolean firstTime = false;
		if (index < tokens.size() && tokens.get(index) instanceof Bar) { 
			// this is a set comprehension
			setComp=true;
			match(Bar.class);
			firstTime=true;
		} else if(index < tokens.size() && tokens.get(index) instanceof StrongRightArrow) {
			// this is a dictionary constructor					
			return parseDictionaryVal(wf,start,exprs.get(0));
		} else if (index < tokens.size() && tokens.get(index) instanceof Colon
				&& exprs.get(0) instanceof Expr.AbstractVariable) {
			// this is a record constructor
			Expr.AbstractVariable v = (Expr.AbstractVariable)exprs.get(0); 
			return parseRecordVal(wf,start,v.var);
		}
		
		checkNotEof();
		token = tokens.get(index);
		while(!(token instanceof RightCurly)) {						
			if(!firstTime) {
				match(Comma.class);				
			}
			firstTime=false;
			exprs.add(parseCondition(wf,false));
			
			checkNotEof();
			token = tokens.get(index);
		}
		match(RightCurly.class);
		
		if(setComp) {
			Expr value = exprs.get(0);
			List<Pair<String,Expr>> srcs = new ArrayList<Pair<String,Expr>>();
			HashSet<String> vars = new HashSet<String>();
			Expr condition = null;			
			
			for(int i=1;i!=exprs.size();++i) {
				Expr v = exprs.get(i);				
				if(v instanceof Expr.BinOp) {
					Expr.BinOp eof = (Expr.BinOp) v;					
					if (eof.op == Expr.BOp.ELEMENTOF
							&& eof.lhs instanceof Expr.AbstractVariable) {
						String var = ((Expr.AbstractVariable) eof.lhs).var;
						if (vars.contains(var)) {
							syntaxError(
									"variable "
											+ var
											+ " cannot have multiple source collections",
									v);
						}
						vars.add(var);
						srcs.add(new Pair<String,Expr>(var,  eof.rhs));
						continue;
					} 					
				} 
				
				if((i+1) == exprs.size()) {
					condition = v;					
				} else {
					syntaxError("condition expected",v);
				}
			}			
			return new Expr.Comprehension(Expr.COp.SETCOMP, value, srcs,
					condition, sourceAttr(start, index - 1));
		} else {	
			return new Expr.Set(exprs, sourceAttr(start, index - 1));
		}
	}
	
	private Expr parseDictionaryVal(WhileyFile wf, int start, Expr key) {
		ArrayList<Pair<Expr,Expr>> pairs = new ArrayList<Pair<Expr,Expr>>();		
		match(StrongRightArrow.class);
		Expr value = parseCondition(wf, false);	
		pairs.add(new Pair<Expr,Expr>(key,value));
		
		Token token = tokens.get(index);		
		while(!(token instanceof RightCurly)) {									
			match(Comma.class);
			
			key = parseCondition(wf, false);
			match(StrongRightArrow.class);
			value = parseCondition(wf, false);
			pairs.add(new Pair<Expr,Expr>(key,value));
			
			checkNotEof();
			token = tokens.get(index);
		}
		match(RightCurly.class);
		return new Expr.Map(pairs,sourceAttr(start, index - 1));
	}
	
	private Expr parseRecordVal(WhileyFile wf, int start, String ident) {
		
		// this indicates a record value.				
		match(Colon.class);
		
		Expr e = parseBitwiseExpression(wf, false);
		
		
		HashMap<String,Expr> exprs = new HashMap<String,Expr>();
		exprs.put(ident, e);
		checkNotEof();
		Token token = tokens.get(index);
		while(!(token instanceof RightCurly)) {			
			match(Comma.class);
			
			checkNotEof();
			token = tokens.get(index);			
			Identifier n = matchIdentifier();

			if(exprs.containsKey(n.text)) {
				syntaxError("duplicate tuple key",n);
			}

			match(Colon.class);
			
			e = parseBitwiseExpression(wf, false);				
			exprs.put(n.text,e);
			checkNotEof();
			token = tokens.get(index);					
		} 
		match(RightCurly.class);

		return new Expr.Record(exprs,sourceAttr(start, index - 1));
	} 
	
	private Expr parseLengthOf(WhileyFile wf) {
		int start = index;
		match(Bar.class);
		
		Expr e = parseRangeExpression(wf);
		
		match(Bar.class);
		return new Expr.LengthOf(e, sourceAttr(start, index - 1));
	}

	private Expr parseNegation(WhileyFile wf) {
		int start = index;
		match(Minus.class);
		
		Expr e = parseIndexTerm(wf);
		
		if(e instanceof Expr.Constant) {
			Expr.Constant c = (Expr.Constant) e;
			if (c.value instanceof Constant.Decimal) {
				BigDecimal br = ((Constant.Decimal) c.value).value;
				return new Expr.Constant(Constant.V_DECIMAL(br.negate()),
						sourceAttr(start, index));
			}
		} 
		
		return new Expr.UnOp(Expr.UOp.NEG, e, sourceAttr(start, index));		
	}

	private Expr.AbstractInvoke parseInvokeExpr(WhileyFile wf) {		
		int start = index;
		Identifier name = matchIdentifier();		
		match(LeftBrace.class);
		
		boolean firstTime=true;
		ArrayList<Expr> args = new ArrayList<Expr>();
		while (index < tokens.size()
				&& !(tokens.get(index) instanceof RightBrace)) {
			if(!firstTime) {
				match(Comma.class);
				
			} else {
				firstTime=false;
			}			
			Expr e = parseBitwiseExpression(wf,false);
			
			args.add(e);		
		}
		match(RightBrace.class);		
		return new Expr.AbstractInvoke(name.text, null, args, sourceAttr(start,index-1));
	}
	
	private Expr parseString() {
		int start = index;
		String s = match(Strung.class).string;
		Constant.Strung str = Constant.V_STRING(s);
		return new Expr.Constant(str, sourceAttr(start, index - 1));
	}
	
	private SyntacticType parseType() {
		int start = index;
				
		SyntacticType t = parseUnionIntersectionType();
		
		if ((index + 1) < tokens.size()
				&& tokens.get(index) instanceof ColonColon
				&& tokens.get(index + 1) instanceof LeftBrace) {
			return parseMethodType(t,start);			
		} else if (index < tokens.size() && tokens.get(index) instanceof LeftBrace) {
			return parseFunctionType(t,start);
		} else {
			return t;
		}
	}
	
	private SyntacticType parseMethodType(SyntacticType ret, int start) {
		match(ColonColon.class);
		match(LeftBrace.class);
		ArrayList<SyntacticType> types = parseTypeSequence();
		match(RightBrace.class);
		return new SyntacticType.Method(ret, null, types, sourceAttr(start,
				index - 1));
	}
	
	private SyntacticType parseFunctionType(SyntacticType ret,
			int start) {
		// this is a function or method type
		match(LeftBrace.class);
		ArrayList<SyntacticType> types = parseTypeSequence();		
		match(RightBrace.class);
		if (index < tokens.size() && (tokens.get(index) instanceof ColonColon)) {
			// this indicates a method type
			if (types.size() != 1) {
				syntaxError("receiver type required for method type",
						tokens.get(index));
			}
			match(ColonColon.class);
			match(LeftBrace.class);			
			boolean firstTime = true;
			while (index < tokens.size()
					&& !(tokens.get(index) instanceof RightBrace)) {
				if (!firstTime) {
					match(Comma.class);
				}
				firstTime = false;
				types.add(parseType());
			}
			match(RightBrace.class);
			return new SyntacticType.Method(ret, null, types, sourceAttr(
					start, index - 1));
		} else {
			return new SyntacticType.Function(ret, null, types, sourceAttr(
					start, index - 1));
		}
	}
	
	private SyntacticType parseUnionIntersectionType() {
		int start = index;
		SyntacticType t = parseNegationType();
		// Now, attempt to look for negation, union or intersection types.
		if (index < tokens.size() && tokens.get(index) instanceof Bar) {
			// this is a union type
			ArrayList<SyntacticType.NonUnion> types = new ArrayList<SyntacticType.NonUnion>();
			types.add((SyntacticType.NonUnion) t);
			while (index < tokens.size() && tokens.get(index) instanceof Bar) {
				match(Bar.class);
				// the following is needed because the lexer filter cannot
				// distinguish between a lengthof operator, and union type.
				skipWhiteSpace();
				t = parseNegationType();
				types.add((SyntacticType.NonUnion) t);
			}
			return new SyntacticType.Union(types, sourceAttr(start, index - 1));
		} else if (index < tokens.size()
				&& tokens.get(index) instanceof Ampersand) {
			// this is an intersection type
			ArrayList<SyntacticType> types = new ArrayList<SyntacticType>();
			types.add(t);
			while (index < tokens.size()
					&& tokens.get(index) instanceof Ampersand) {
				match(Ampersand.class);
				// the following is needed because the lexer filter cannot
				// distinguish between a lengthof operator, and union type.
				skipWhiteSpace();
				t = parseNegationType();
				types.add(t);
			}
			return new SyntacticType.Intersection(types, sourceAttr(start,
					index - 1));
		} else {
			return t;
		}
	}
	
	private SyntacticType parseNegationType() {
		int start = index;				
		if (index < tokens.size() && tokens.get(index) instanceof Shreak) {			
			// this is a negation type
			match(Shreak.class);
			return new SyntacticType.Not(parseNegationType(),sourceAttr(start, index - 1));
		} else {
			return parseBraceType();
		}
	}
	
	private SyntacticType parseBraceType() {			
		if (index < tokens.size() && tokens.get(index) instanceof LeftBrace) {
			// tuple type or bracketed type
			int start = index;
			match(LeftBrace.class);
			SyntacticType t = parseType();
			skipWhiteSpace();
			if (index < tokens.size() && tokens.get(index) instanceof Comma) {
				// tuple type
				ArrayList<SyntacticType> types = new ArrayList<SyntacticType>();
				types.add(t);				
				while (index < tokens.size()
						&& tokens.get(index) instanceof Comma) {					
					match(Comma.class);
					types.add(parseType());
					skipWhiteSpace();
				}
				match(RightBrace.class);
				return new SyntacticType.Tuple(types, sourceAttr(start, index - 1));
			} else {
				// bracketed type
				match(RightBrace.class);
				return t;
			}			
		} else {
			return parseBaseType();
		}
	}
	
	private SyntacticType parseBaseType() {				
		checkNotEof();
		int start = index;
		Token token = tokens.get(index);
		SyntacticType t;
		
		if(token.text.equals("any")) {
			matchKeyword("any");
			t = new SyntacticType.Any(sourceAttr(start,index-1));
		} else if(token.text.equals("null")) {
			matchKeyword("null");
			t = new SyntacticType.Null(sourceAttr(start,index-1));
		} else if(token.text.equals("byte")) {
			matchKeyword("byte");			
			t = new SyntacticType.Byte(sourceAttr(start,index-1));
		} else if(token.text.equals("char")) {
			matchKeyword("char");			
			t = new SyntacticType.Char(sourceAttr(start,index-1));
		} else if(token.text.equals("int")) {
			matchKeyword("int");			
			t = new SyntacticType.Int(sourceAttr(start,index-1));
		} else if(token.text.equals("real")) {
			matchKeyword("real");
			t = new SyntacticType.Real(sourceAttr(start,index-1));
		} else if(token.text.equals("string")) {
			matchKeyword("string");
			t = new SyntacticType.Strung(sourceAttr(start,index-1));
		} else if(token.text.equals("void")) {
			matchKeyword("void");
			t = new SyntacticType.Void(sourceAttr(start,index-1));
		} else if(token.text.equals("bool")) {
			matchKeyword("bool");
			t = new SyntacticType.Bool(sourceAttr(start,index-1));
		} else if(token.text.equals("ref")) {
			matchKeyword("ref");
			t = new SyntacticType.Reference(parseType(),sourceAttr(start,index-1));			
		} else if(token instanceof LeftBrace) {
			match(LeftBrace.class);
			
			ArrayList<SyntacticType> types = new ArrayList<SyntacticType>();
			types.add(parseType());
			match(Comma.class);
			
			types.add(parseType());
			checkNotEof();
			token = tokens.get(index);
			while(!(token instanceof RightBrace)) {
				match(Comma.class);
				
				types.add(parseType());
				checkNotEof();
				token = tokens.get(index);
			}
			match(RightBrace.class);
			return new SyntacticType.Tuple(types);
		} else if(token instanceof LeftCurly) {		
			t = parseRecordOrSetOrMapType();
		} else if(token instanceof LeftSquare) {
			match(LeftSquare.class);			
			t = parseType();			
			match(RightSquare.class);
			t = new SyntacticType.List(t,sourceAttr(start,index-1));
		} else {		
			ArrayList<String> names = new ArrayList<String>();
			names.add(matchIdentifier().text);			
			while(index < tokens.size() && tokens.get(index) instanceof Dot) {
				match(Dot.class);
				names.add(matchIdentifier().text);
			}
			t = new SyntacticType.Nominal(names,sourceAttr(start,index-1));			
		}		
		
		return t;
	}		
	
	private SyntacticType parseRecordOrSetOrMapType() {
		int start = index;
		match(LeftCurly.class);
		
		SyntacticType t = parseType();			
		
		checkNotEof();
		if(tokens.get(index) instanceof RightCurly) {
			// set type
			match(RightCurly.class);
			return new SyntacticType.Set(t,sourceAttr(start,index-1));
		} else if(tokens.get(index) instanceof StrongRightArrow) {
			// map type
			match(StrongRightArrow.class);
			SyntacticType v = parseType();			
			match(RightCurly.class);
			return new SyntacticType.Map(t,v,sourceAttr(start,index-1));				
		} else {
			index = start; // reset lookahead
			return parseRecordType();
		}		
	}
	
	private SyntacticType parseRecordType() {
		int start = index;
		match(LeftCurly.class);
		
		Pair<SyntacticType,Token> typeField = parseMixedNameType(); 			
		HashMap<String,SyntacticType> types = new HashMap<String,SyntacticType>();			
		types.put(typeField.second().text, typeField.first());

		checkNotEof();
		Token token = tokens.get(index);
		boolean isOpen = false;

		while(!(token instanceof RightCurly)) {
			match(Comma.class);

			checkNotEof();
			token = tokens.get(index);

			if(token instanceof DotDotDot) {
				// special case indicates an open record
				match(DotDotDot.class);
				isOpen = true;
				break;
			}

			typeField = parseMixedNameType();
			String field = typeField.second().text;
			
			if(types.containsKey(field)) {
				syntaxError("duplicate record key",typeField.second());
			}					
			types.put(field, typeField.first());
			checkNotEof();
			token = tokens.get(index);								
		}				

		match(RightCurly.class);
		return new SyntacticType.Record(isOpen, types, sourceAttr(start,index-1));				
	} 
	
	private Pair<SyntacticType, Token> parseMixedNameType() {
		int start = index;
		SyntacticType type = parseType();
		Token identifier;
		
		if (index < tokens.size() && tokens.get(index) instanceof ColonColon) {
			match(ColonColon.class);
			identifier = matchIdentifier();
			match(LeftBrace.class);
			ArrayList<SyntacticType> params = parseTypeSequence();
			match(RightBrace.class);
			type = new SyntacticType.Method(type, null, params, sourceAttr(
					start, index - 1));
		} else {
			identifier = matchIdentifier();
			if (index < tokens.size() && tokens.get(index) instanceof LeftBrace) {
				type = parseFunctionType(type, start);
			}
		}
		 
		return new Pair<SyntacticType, Token>(type, identifier);
	}
	
	private ArrayList<SyntacticType> parseTypeSequence() {
		ArrayList<SyntacticType> types = new ArrayList<SyntacticType>();
		boolean firstTime = true;
		while (index < tokens.size()
				&& !(tokens.get(index) instanceof RightBrace)) {
			if (!firstTime) {
				match(Comma.class);
			}
			firstTime = false;
			types.add(parseType());
		}
		return types;
	}
	
	private ArrayList<WhileyFile.Parameter> parseParameterSequence(
			WhileyFile wf, Class<? extends Token> start,
			Class<? extends Token> end) {

		match(start);

		boolean firstTime = true;
		HashSet<String> parameterNames = new HashSet<String>();
		ArrayList<WhileyFile.Parameter> parameters = new ArrayList<WhileyFile.Parameter>();
		
		while (index < tokens.size()
				&& !end.isInstance(tokens.get(index))) {
			if (!firstTime) {
				match(Comma.class);
			}
			firstTime = false;
			int pstart = index;
			SyntacticType t = parseType();
			Identifier n = matchIdentifier();
			if (parameterNames.contains(n.text)) {
				syntaxError("duplicate parameter name", n);
			} else if (!n.text.equals("$")) {
				parameterNames.add(n.text);
			} else {
				syntaxError("parameter name not permitted", n);
			}
			parameters.add(wf.new Parameter(t, n.text, sourceAttr(pstart,
					index - 1)));
		}

		match(end);
		return parameters;
	}
	
	private void skipWhiteSpace() {
		while (index < tokens.size() && isWhiteSpace(tokens.get(index))) {
			index++;
		}
	}

	private boolean isWhiteSpace(Token t) {
		return t instanceof WhileyFileLexer.NewLine
				|| t instanceof WhileyFileLexer.LineComment
				|| t instanceof WhileyFileLexer.BlockComment
				|| t instanceof WhileyFileLexer.Indent;
	}
	
	private void checkNotEof() {		
		if (index >= tokens.size()) {
			throw new SyntaxError("unexpected end-of-file", filename,
					index - 1, index - 1);
		}
		return;
	}
	
	private <T extends Token> T match(Class<T> c) {
		checkNotEof();
		Token t = tokens.get(index);
		if (!c.isInstance(t)) {			
			syntaxError("syntax error" , t);
		}
		index = index + 1;
		return (T) t;
	}
	
	private Identifier matchIdentifier() {
		checkNotEof();
		Token t = tokens.get(index);
		if (t instanceof Identifier) {
			Identifier i = (Identifier) t;
			index = index + 1;
			return i;
		}
		syntaxError("identifier expected", t);
		return null; // unreachable.
	}
	
	private Keyword matchKeyword(String keyword) {
		checkNotEof();
		Token t = tokens.get(index);
		if (t instanceof Keyword) {
			if (t.text.equals(keyword)) {
				index = index + 1;
				return (Keyword) t;
			}
		}
		syntaxError("keyword " + keyword + " expected.", t);
		return null;
	}
	
	private void matchEndLine() {
		while (index < tokens.size()) {
			Token t = tokens.get(index++);
			if (t instanceof NewLine) {
				break;
			} else if (!(t instanceof LineComment)
					&& !(t instanceof BlockComment) && !(t instanceof Indent)) {
				syntaxError("unexpected token encountered (" + t.text + ")", t);
			}
		}
	}
	
	private Attribute.Source sourceAttr(int start, int end) {		
		Token t1 = tokens.get(start);				
		Token t2 = tokens.get(end);
		return new Attribute.Source(t1.start,t2.end(),t1.line);
	}
	
	private void syntaxError(String msg, Expr e) {
		Attribute.Source loc = e.attribute(Attribute.Source.class);
		throw new SyntaxError(msg, filename, loc.start, loc.end);
	}
	
	private void syntaxError(String msg, Token t) {
		throw new SyntaxError(msg, filename, t.start, t.start + t.text.length() - 1);
	}		
}
