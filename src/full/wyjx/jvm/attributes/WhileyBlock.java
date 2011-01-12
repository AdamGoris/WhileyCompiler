// This file is part of the Whiley-to-Java Compiler (wyjc).
//
// The Whiley-to-Java Compiler is free software; you can redistribute 
// it and/or modify it under the terms of the GNU General Public 
// License as published by the Free Software Foundation; either 
// version 3 of the License, or (at your option) any later version.
//
// The Whiley-to-Java Compiler is distributed in the hope that it 
// will be useful, but WITHOUT ANY WARRANTY; without even the 
// implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR 
// PURPOSE.  See the GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public 
// License along with the Whiley-to-Java Compiler. If not, see 
// <http://www.gnu.org/licenses/>
//
// Copyright 2010, David James Pearce. 

package wyjx.jvm.attributes;

import java.io.*;
import java.math.BigInteger;
import java.util.*;

import wyil.jvm.attributes.*;
import wyil.jvm.rt.BigRational;
import wyil.lang.*;
import wyil.lang.Code.*;
import wyjvm.io.*;
import wyjvm.lang.*;

public class WhileyBlock implements BytecodeAttribute {
	private final Block block;
	private final String name;
	
	public WhileyBlock(String name, Block block) {
		this.block = block;
		this.name = name;
	}
	
	public String name() {
		return name;
	}
	
	public Block block() {
		return block;
	}
	
	public void print(PrintWriter output,
			Map<Constant.Info, Integer> constantPool, ClassLoader loader) {

	}
	
	public void addPoolItems(Set<Constant.Info> constantPool, ClassLoader loader) {
		constantPool.add(new Constant.Utf8(name));
		addPoolItems(block, constantPool);
	}
	
	public static void addPoolItems(Block block, Set<Constant.Info> constantPool) {
		for(Stmt s : block) {
			addPoolItems(s.code,constantPool);
		}
	}
	
	public static void addPoolItems(Code c, Set<Constant.Info> constantPool) {
		if(c instanceof Assign) {
			Assign a = (Assign) c;
			if(a.lhs != null) {
				addPoolItems(a.lhs,constantPool);
			} 								
			addPoolItems(a.rhs,constantPool);						
		} else if(c instanceof Code.Goto) {
			Code.Goto a = (Code.Goto) c;			
			constantPool.add(new Constant.Utf8(a.target));
		} else if(c instanceof Code.Fail) {
			Code.Fail a = (Code.Fail) c;			
			constantPool.add(new Constant.Utf8(a.msg));			
		} else if(c instanceof Code.Label) {
			Code.Label a = (Code.Label) c;
			constantPool.add(new Constant.Utf8(a.label));			
		} else if(c instanceof Code.End) {
			Code.End a = (Code.End) c;
			constantPool.add(new Constant.Utf8(a.target));			
		} else if(c instanceof IfGoto) {
			IfGoto a = (IfGoto) c;
			addPoolItems(a.lhs,constantPool);			
			addPoolItems(a.rhs,constantPool);			
			constantPool.add(new Constant.Utf8(a.target));
		} else if(c instanceof Forall) {
			Forall a = (Forall) c;	
			constantPool.add(new Constant.Utf8(a.label));
			addPoolItems(a.variable, constantPool);
			addPoolItems(a.source, constantPool);	
			for(CExpr.LVar v : a.modifies) {
				addPoolItems(v, constantPool);
			}
		} else if(c instanceof Induct) {
			Induct a = (Induct) c;	
			constantPool.add(new Constant.Utf8(a.label));
			addPoolItems(a.variable, constantPool);
			addPoolItems(a.source, constantPool);				
		} else if(c instanceof Recurse) {
			Recurse a = (Recurse) c;	
			addPoolItems(a.rhs, constantPool);						
		} 
	}
	
	public static void addPoolItems(CExpr rval, Set<Constant.Info> constantPool) {
		if(rval instanceof CExpr.Variable) {
			CExpr.Variable var = (CExpr.Variable) rval;
			constantPool.add(new Constant.Utf8(var.name));
			WhileyType.addPoolItems(var.type, constantPool);
		} else if(rval instanceof CExpr.Register) {
			CExpr.Register var = (CExpr.Register) rval;		
			WhileyType.addPoolItems(var.type, constantPool);
		} else if(rval instanceof CExpr.UnOp) {
			CExpr.UnOp c = (CExpr.UnOp)rval;
			addPoolItems(c.rhs,constantPool);			
		} else if(rval instanceof CExpr.BinOp) {
			CExpr.BinOp bop = (CExpr.BinOp)rval; 
			addPoolItems(bop.lhs,constantPool);
			addPoolItems(bop.rhs,constantPool);			
		} else if(rval instanceof CExpr.NaryOp) {
			CExpr.NaryOp nop = (CExpr.NaryOp)rval; 
			for(CExpr arg : nop.args) {
				addPoolItems(arg,constantPool);
			}
		} else if(rval instanceof CExpr.Invoke) {
			CExpr.Invoke ivk = (CExpr.Invoke)rval; 
			addPoolItems(ivk.name, constantPool);
			if(ivk.receiver != null) {
				addPoolItems(ivk.receiver, constantPool);
			}
			WhileyType.addPoolItems(ivk.type, constantPool);
			for(CExpr arg : ivk.args) {
				addPoolItems(arg,constantPool);
			}			
		} else if(rval instanceof CExpr.ListAccess) {
			CExpr.ListAccess c = (CExpr.ListAccess)rval;
			addPoolItems(c.src,constantPool);			
			addPoolItems(c.index,constantPool);
		} else if(rval instanceof CExpr.RecordAccess) {
			CExpr.RecordAccess c = (CExpr.RecordAccess)rval;
			addPoolItems(c.lhs,constantPool);			
			constantPool.add(new Constant.Utf8(c.field));
		} 	
	}
	
	public static void addPoolItems(NameID name, Set<Constant.Info> constantPool) {
		constantPool.add(new Constant.Utf8(name.name()));
		constantPool.add(new Constant.Utf8(name.module().module()));
		constantPool.add(new Constant.Utf8(name.module().pkg().toString()));
	}
	
	public void write(BinaryOutputStream writer,
			Map<Constant.Info, Integer> constantPool, ClassLoader loader)
			throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		BinaryOutputStream iw = new BinaryOutputStream(out);

		write(block, iw, constantPool);

		writer.write_u2(constantPool.get(new Constant.Utf8(name())));
		writer.write_u4(out.size());
		writer.write(out.toByteArray());
	}

	protected static void write(Block expr, BinaryOutputStream writer,
			Map<Constant.Info, Integer> constantPool) throws IOException {
		writer.write_u4(expr.size());
		for(Stmt s : expr) {
			write(s.code, writer, constantPool);
		}
	}
	
	protected static void write(Code c, BinaryOutputStream writer,
			Map<Constant.Info, Integer> constantPool) throws IOException {
		if(c instanceof Assign) {
			Assign a = (Assign) c;
			if(a.lhs != null) {
				writer.write_u1(ASSIGN);
				write(a.lhs,writer,constantPool);
				write(a.rhs,writer,constantPool);
			} else {
				writer.write_u1(CODEEXPR);				
				write(a.rhs,writer,constantPool);
			}			
		} else if(c instanceof Code.Goto) {
			Code.Goto a = (Code.Goto) c;			
			writer.write_u1(GOTO);
			writer.write_u2(constantPool.get(new Constant.Utf8(a.target)));
		} else if(c instanceof Code.Fail) {
			Code.Fail a = (Code.Fail) c;			
			writer.write_u1(FAIL);
			writer.write_u2(constantPool.get(new Constant.Utf8(a.msg)));
		} else if(c instanceof Code.Label) {
			Code.Label a = (Code.Label) c;			
			writer.write_u1(LABEL);
			writer.write_u2(constantPool.get(new Constant.Utf8(a.label)));
		} else if(c instanceof Code.ForallEnd) {
			Code.End a = (Code.End) c;			
			writer.write_u1(FORALLEND);
			writer.write_u2(constantPool.get(new Constant.Utf8(a.target)));
		} else if(c instanceof Code.InductEnd) {
			Code.End a = (Code.End) c;			
			writer.write_u1(INDUCTEND);
			writer.write_u2(constantPool.get(new Constant.Utf8(a.target)));
		} else if(c instanceof IfGoto) {
			IfGoto a = (IfGoto) c;			
			writer.write_u1(IFGOTO);			
			writer.write_u1(COP2INT(a.op));
			writer.write_u2(constantPool.get(new Constant.Utf8(a.target)));
			write(a.lhs,writer,constantPool);
			write(a.rhs,writer,constantPool);			
		} else if (c instanceof Skip || c instanceof Check
				|| c instanceof Code.CheckEnd) {
			// Do nothing for skip and also for check blocks. The reason we can
			// ignore check blocks is that a precondition will necessary become
			// a check block and nested check blocks add nothing useful.  
		} else if(c instanceof Forall) {
			Forall a = (Forall) c;	
			writer.write_u1(FORALL);
			writer.write_u2(constantPool.get(new Constant.Utf8(a.label)));
			write(a.variable,writer,constantPool);
			write(a.source,writer,constantPool);	
			writer.write_u2(a.modifies.size());
			for(CExpr.LVar v : a.modifies) {
				write(v,writer,constantPool);
			}
		} else if(c instanceof Induct) {
			Induct a = (Induct) c;	
			writer.write_u1(INDUCT);
			writer.write_u2(constantPool.get(new Constant.Utf8(a.label)));
			write(a.variable,writer,constantPool);
			write(a.source,writer,constantPool);				
		} else if(c instanceof Recurse) {
			Recurse a = (Recurse) c;	
			writer.write_u1(RECURSE);
			write(a.rhs,writer,constantPool);				
		} else {
			throw new IllegalArgumentException("Code not permitted in WhileyBlock: " + c);
		}
	}	
	
	protected static void write(CExpr rval, BinaryOutputStream writer,
			Map<Constant.Info, Integer> constantPool) throws IOException {
		if(rval instanceof Value) {
			write((Value)rval,writer,constantPool);
		} else if(rval instanceof CExpr.Variable) {
			write((CExpr.Variable)rval,writer,constantPool);
		} else if(rval instanceof CExpr.Register) {
			write((CExpr.Register)rval,writer,constantPool);
		} else if(rval instanceof CExpr.UnOp) {
			write((CExpr.UnOp)rval,writer,constantPool);
		} else if(rval instanceof CExpr.BinOp) {
			write((CExpr.BinOp)rval,writer,constantPool);
		} else if(rval instanceof CExpr.NaryOp) {
			write((CExpr.NaryOp)rval,writer,constantPool);
		} else if(rval instanceof CExpr.Invoke) {
			write((CExpr.Invoke)rval,writer,constantPool);
		} else if(rval instanceof CExpr.ListAccess) {
			write((CExpr.ListAccess)rval,writer,constantPool);
		} else if(rval instanceof CExpr.RecordAccess) {
			write((CExpr.RecordAccess)rval,writer,constantPool);
		} else {
			throw new IllegalArgumentException("Unknown expression encountered: " + rval);
		}
	}	
	
	public static void write(CExpr.Variable expr, BinaryOutputStream writer,
			Map<Constant.Info, Integer> constantPool) throws IOException {
		writer.write_u1(VARIABLE);				
		int idx = constantPool.get(new Constant.Utf8(expr.name));		
		writer.write_u2(idx);						
		WhileyType.write(expr.type,writer,constantPool);
	}
	
	public static void write(CExpr.Register expr, BinaryOutputStream writer,
			Map<Constant.Info, Integer> constantPool) throws IOException {
		writer.write_u1(REGISTER);												
		writer.write_u2(expr.index);					
		WhileyType.write(expr.type,writer,constantPool);
	}
	
	public static void write(CExpr.UnOp expr, BinaryOutputStream writer,
			Map<Constant.Info, Integer> constantPool) throws IOException {
		writer.write_u1(UOP2INT(expr.op));																			
		write(expr.rhs,writer,constantPool);
	}
		
	public static void write(CExpr.Invoke expr, BinaryOutputStream writer,
			Map<Constant.Info, Integer> constantPool) throws IOException {
		if(expr.receiver != null) {
			writer.write_u1(METHINVOKE);
		} else {
			writer.write_u1(FUNINVOKE);
		}
		writeNameId(expr.name,writer,constantPool);
		WhileyType.write(expr.type,writer,constantPool);
		if(expr.receiver != null) {
			write(expr.receiver,writer,constantPool);
		}
		writer.write_u1(expr.args.size());
		for(CExpr arg : expr.args) {
			write(arg,writer,constantPool);
		}
	}
	
	public static void writeNameId(NameID name, BinaryOutputStream writer,
			Map<Constant.Info, Integer> constantPool) throws IOException {
		writeModuleId(name.module(),writer,constantPool);
		int idx = constantPool.get(new Constant.Utf8(name.name()));							
		writer.write_u2(idx);		
	}
	
	public static void writeModuleId(ModuleID name, BinaryOutputStream writer,
			Map<Constant.Info, Integer> constantPool) throws IOException {
		writePkgId(name.pkg(),writer,constantPool);
		int idx = constantPool.get(new Constant.Utf8(name.module()));							
		writer.write_u2(idx);		
	}
	
	public static void writePkgId(PkgID pkg, BinaryOutputStream writer,
			Map<Constant.Info, Integer> constantPool) throws IOException {		
		int idx = constantPool.get(new Constant.Utf8(pkg.toString()));							
		writer.write_u2(idx);		
	}
	
	public static void write(CExpr.BinOp expr, BinaryOutputStream writer,
			Map<Constant.Info, Integer> constantPool) throws IOException {
		writer.write_u1(BOP2INT(expr.op));												
		write(expr.lhs,writer,constantPool);						
		write(expr.rhs,writer,constantPool);
	}
	
	public static void write(CExpr.NaryOp expr, BinaryOutputStream writer,
			Map<Constant.Info, Integer> constantPool) throws IOException {
		writer.write_u1(NOP2INT(expr.op));
		writer.write_u4(expr.args.size());
		for(CExpr arg : expr.args) {
			write(arg,writer,constantPool);
		}
	}
	
	public static void write(CExpr.ListAccess expr, BinaryOutputStream writer,
			Map<Constant.Info, Integer> constantPool) throws IOException {
		writer.write_u1(LISTACCESS);												
		write(expr.src,writer,constantPool);						
		write(expr.index,writer,constantPool);
	}
	
	public static void write(CExpr.RecordAccess expr, BinaryOutputStream writer,
			Map<Constant.Info, Integer> constantPool) throws IOException {
		writer.write_u1(RECORDACCESS);
		write(expr.lhs,writer,constantPool);
		int idx = constantPool.get(new Constant.Utf8(expr.field));							
		writer.write_u2(idx);										
	}
	
	public static class Reader implements BytecodeAttributeReader { 		
		private final String name;
		
		public Reader(String name) {
			this.name = name;
		}
		public String name() {			
			return name;
		}
		
		public WhileyBlock read(BinaryInputStream reader,
				Map<Integer, Constant.Info> constantPool) throws IOException {			
			int idx = reader.read_u2();
			int size = reader.read_u4(); // not needed
			Constant.Utf8 utf8 = (Constant.Utf8) constantPool.get(idx);
			
			Block block = readBlock(reader, constantPool);
			
			return new WhileyBlock(utf8.str, block);
		}
		
		protected static Block readBlock(BinaryInputStream reader,
				Map<Integer, Constant.Info> constantPool) throws IOException {							
			int ncodes = reader.read_u4();			
			Block block = new Block();
			for(int i=0;i!=ncodes;++i) {
				block.add(readCode(reader,constantPool));
			}
			return block;
		}

		protected static Code readCode(BinaryInputStream reader,
				Map<Integer, Constant.Info> constantPool) throws IOException {							
			int code = reader.read_u1();
			switch(code) {
			case ASSIGN:
			{				
				CExpr.LVal lhs = (CExpr.LVal) readCExpr(reader,constantPool);
				CExpr rhs = readCExpr(reader,constantPool);
				return new Code.Assign(lhs,rhs);
			}
			case CODEEXPR:
			{					
				CExpr rhs = readCExpr(reader,constantPool);
				return new Code.Assign(null,rhs);
			}
			case GOTO:
			{				
				int idx = reader.read_u2();
				Constant.Utf8 target = (Constant.Utf8) constantPool.get(idx);
				return new Code.Goto(target.str);
			}
			case LABEL:
			{				
				int idx = reader.read_u2();
				Constant.Utf8 target = (Constant.Utf8) constantPool.get(idx);
				return new Code.Label(target.str);
			}
			case FORALLEND:
			{				
				int idx = reader.read_u2();
				Constant.Utf8 target = (Constant.Utf8) constantPool.get(idx);
				return new Code.ForallEnd(target.str);
			}
			case INDUCTEND:
			{				
				int idx = reader.read_u2();
				Constant.Utf8 target = (Constant.Utf8) constantPool.get(idx);
				return new Code.InductEnd(target.str);
			}
			case FAIL:
			{				
				int idx = reader.read_u2();
				Constant.Utf8 target = (Constant.Utf8) constantPool.get(idx);
				return new Code.Fail(target.str);
			}
			case IFGOTO:
			{													
				int cop = reader.read_u1();
				int idx = reader.read_u2();
				Constant.Utf8 target = (Constant.Utf8) constantPool.get(idx);				
				CExpr lhs = readCExpr(reader,constantPool);
				CExpr rhs = readCExpr(reader,constantPool);
				return new Code.IfGoto(INT2COP(cop),lhs,rhs,target.str);
			}
			case FORALL:
			{
				int idx = reader.read_u2();
				Constant.Utf8 label = (Constant.Utf8) constantPool.get(idx);
				CExpr.Register var = (CExpr.Register) readCExpr(reader,constantPool);
				CExpr src = readCExpr(reader,constantPool);				
				ArrayList<CExpr.LVar> modifies = new ArrayList<CExpr.LVar>();
				int nmods = reader.read_u2();
				for(int i=0;i!=nmods;++i) {
					modifies.add((CExpr.LVar) readCExpr(reader,constantPool));
				}
				// FIXME: problem with modifies
				return new Code.Forall(label.str,null,var,src,modifies);
			}
			case INDUCT:
			{
				int idx = reader.read_u2();
				Constant.Utf8 label = (Constant.Utf8) constantPool.get(idx);
				CExpr.Register var = (CExpr.Register) readCExpr(reader,constantPool);
				CExpr src = readCExpr(reader,constantPool);				
				return new Code.Induct(label.str,var,src);
			}
			case RECURSE:
			{
				CExpr rhs = readCExpr(reader,constantPool);				
				return new Code.Recurse(rhs);
			}
			}
			throw new IllegalArgumentException("unknown code encountered: " + code);
		}

		protected static Value readValue(BinaryInputStream reader,
				Map<Integer, Constant.Info> constantPool) throws IOException {		
			return (Value) readCExpr(reader,constantPool);
		}
		
		protected static CExpr readCExpr(BinaryInputStream reader,
				Map<Integer, Constant.Info> constantPool) throws IOException {		
			int code = reader.read_u1();				
			switch (code) {
			case WhileyDefine.INTVAL:			
			case WhileyDefine.REALVAL:
			case WhileyDefine.LISTVAL:
			case WhileyDefine.SETVAL:
			case WhileyDefine.RECORDVAL:
				return WhileyDefine.Reader.readValue(reader,constantPool);			
			case VARIABLE:
			{					
				// The encoding of variables could be optimised to avoid using the
				// constant pool in most, if not all cases.
				int idx = reader.read_u2();			
				Constant.Utf8 utf8 = (Constant.Utf8) constantPool.get(idx);				
				Type type = WhileyType.Reader.readType(reader, constantPool);
				return CExpr.VAR(type,utf8.str);
			}
			case REGISTER:
			{
				// The encoding of variables could be optimised to avoid using the
				// constant pool in most, if not all cases.				
				int idx = reader.read_u2();							
				Type type = WhileyType.Reader.readType(reader, constantPool);
				return CExpr.REG(type,idx);
			}
			case NEG:
			case NOT:
			case LENGTHOF:
			case PROCESSSPAWN:
			case PROCESSACCESS:
			{
				CExpr lhs = readCExpr(reader,constantPool);
				return CExpr.UNOP(INT2UOP(code),lhs);
			}
			case ADD:				
			case SUB:				
			case MUL:				
			case DIV:				
			case UNION:				
			case INTERSECT:				
			case DIFFERENCE:				
			case APPEND:
			{
				CExpr lhs = readCExpr(reader,constantPool);
				CExpr rhs = readCExpr(reader,constantPool);
				return CExpr.BINOP(INT2BOP(code),lhs,rhs);
			}
			case FUNINVOKE:
			{
				
				NameID nid = readNameID(reader,constantPool);
				Type.Fun type = (Type.Fun) WhileyType.Reader.readType(reader, constantPool);
				// FIXME: need to split case num off of the name somehow?
				int casenum = 1;
				int nargs = reader.read_u1();
				ArrayList<CExpr> args = new ArrayList<CExpr>();
				for(int i=0;i!=nargs;++i) {
					args.add(readCExpr(reader,constantPool));
				}
				return CExpr.INVOKE(type, nid, casenum, null, args);
			}
			case METHINVOKE:
			{
				
				NameID nid = readNameID(reader,constantPool);
				Type.Fun type = (Type.Fun) WhileyType.Reader.readType(reader, constantPool);
				// FIXME: need to split case num off of the name somehow?
				int casenum = 1;
				int nargs = reader.read_u1();
				CExpr receiver = readCExpr(reader,constantPool);
				ArrayList<CExpr> args = new ArrayList<CExpr>();
				for(int i=0;i!=nargs;++i) {
					args.add(readCExpr(reader,constantPool));
				}
				return CExpr.INVOKE(type, nid, casenum, receiver, args);
			}
			case LISTACCESS:
			{
				CExpr lhs = readCExpr(reader,constantPool);
				CExpr rhs = readCExpr(reader,constantPool);
				return CExpr.LISTACCESS(lhs,rhs);
			}
			case RECORDACCESS:
			{
				CExpr lhs = readCExpr(reader,constantPool);
				int idx = reader.read_u2();
				Constant.Utf8 field = (Constant.Utf8) constantPool.get(idx);
				return CExpr.RECORDACCESS(lhs,field.str);			
			}			
			case SETGEN:				
			case LISTGEN:
			case SUBLIST:
			{
				int len = reader.read_u4();
				ArrayList<CExpr> args = new ArrayList<CExpr>();
				for(int i=0;i!=len;++i) {
					args.add(readCExpr(reader,constantPool));
				}
				return CExpr.NARYOP(INT2NOP(code), args);
			}
			}
			throw new RuntimeException("Unknown CExpr encountered in WhileyBlock: " + code);
		}
	
		public static PkgID readPkgID(BinaryInputStream reader,
				Map<Integer, Constant.Info> constantPool) throws IOException {
			int idx = reader.read_u2();
			Constant.Utf8 pkg = (Constant.Utf8) constantPool.get(idx);
			return PkgID.fromString(pkg.str);
		}
		
		public static ModuleID readModuleID(BinaryInputStream reader,
				Map<Integer, Constant.Info> constantPool) throws IOException {
			PkgID pkg = readPkgID(reader,constantPool);
			int idx = reader.read_u2();
			Constant.Utf8 module = (Constant.Utf8) constantPool.get(idx);
			return new ModuleID(pkg,module.str);
		}
		
		public static NameID readNameID(BinaryInputStream reader,
				Map<Integer, Constant.Info> constantPool) throws IOException {
			ModuleID mid = readModuleID(reader,constantPool);
			int idx = reader.read_u2();
			Constant.Utf8 name = (Constant.Utf8) constantPool.get(idx);			
			return new NameID(mid,name.str);			
		}
	}
	
	public static Code.COP INT2COP(int op) {
		switch (op) {
		case EQ:
			return Code.COP.EQ;
		case NEQ:
			return Code.COP.NEQ;
		case LT:
			return Code.COP.LT;
		case LTEQ:
			return Code.COP.LTEQ;
		case GT:
			return Code.COP.GT;
		case GTEQ:
			return Code.COP.GTEQ;
		case SUBSET:
			return Code.COP.SUBSET;
		case SUBSETEQ:
			return Code.COP.SUBSETEQ;
		case ELEMOF:
			return Code.COP.ELEMOF;
		case SUBTYPEEQ:
			return Code.COP.SUBTYPEEQ;
		case NSUBTYPEEQ:
			return Code.COP.NSUBTYPEEQ;
		
		}
		
		throw new IllegalArgumentException(
				"Invalid conditional operation encountered: " + op);
	}
	
	public static int COP2INT(Code.COP op) {
		switch (op) {
		case EQ:
			return EQ;
		case NEQ:
			return NEQ;
		case LT:
			return LT;
		case LTEQ:
			return LTEQ;
		case GT:
			return GT;
		case GTEQ:
			return GTEQ;
		case SUBSET:
			return SUBSET;
		case SUBSETEQ:
			return SUBSETEQ;
		case ELEMOF:
			return ELEMOF;
		case SUBTYPEEQ:
			return SUBTYPEEQ;
		case NSUBTYPEEQ:
			return NSUBTYPEEQ;
		}
		
		throw new IllegalArgumentException(
				"Invalid conditional operation encountered: " + op);
	}
	
	public static int BOP2INT(CExpr.BOP op) {
		switch (op) {
		case ADD:
			return ADD;
		case SUB:
			return SUB;
		case MUL:
			return MUL;
		case DIV:
			return DIV;
		case UNION:
			return UNION;
		case INTERSECT:
			return INTERSECT;
		case DIFFERENCE:
			return DIFFERENCE;
		case APPEND:
			return APPEND;
		}

		throw new IllegalArgumentException(
				"Invalid binary operation encountered: " + op);
	}
	
	public static CExpr.BOP INT2BOP(int op) {
		switch (op) {
		case ADD:
			return CExpr.BOP.ADD;
		case SUB:
			return CExpr.BOP.SUB;
		case MUL:
			return CExpr.BOP.MUL;
		case DIV:
			return CExpr.BOP.DIV;
		case UNION:
			return CExpr.BOP.UNION;
		case INTERSECT:
			return CExpr.BOP.INTERSECT;
		case DIFFERENCE:
			return CExpr.BOP.DIFFERENCE;
		case APPEND:
			return CExpr.BOP.APPEND;
		}

		throw new IllegalArgumentException(
				"Invalid binary operation encountered: " + op);
	}
	
	public static int UOP2INT(CExpr.UOP op) {
		switch (op) {
		case NEG:
			return NEG;
		case LENGTHOF:
			return LENGTHOF;
		case PROCESSACCESS:
			return PROCESSACCESS;
		case PROCESSSPAWN:
			return PROCESSSPAWN;
		}

		throw new IllegalArgumentException(
				"Invalid binary operation encountered: " + op);
	}
	
	public static int NOP2INT(CExpr.NOP op) {
		switch (op) {
		case SETGEN:
			return SETGEN;
		case LISTGEN:
			return LISTGEN;
		case SUBLIST:
			return SUBLIST;		
		}

		throw new IllegalArgumentException(
				"Invalid nary operation encountered: " + op);
	}
	
	public static CExpr.NOP INT2NOP(int op) {
		switch (op) {
		case SETGEN:
			return CExpr.NOP.SETGEN;
		case LISTGEN:
			return CExpr.NOP.LISTGEN;
		case SUBLIST:
			return CExpr.NOP.SUBLIST;		
		}

		throw new IllegalArgumentException(
				"Invalid nary operation encountered: " + op);
	}
	
	public static CExpr.UOP INT2UOP(int op) {
		switch (op) {
		case NEG:
			return CExpr.UOP.NEG;
		case LENGTHOF:
			return CExpr.UOP.LENGTHOF;
		case PROCESSACCESS:
			return CExpr.UOP.PROCESSACCESS;
		case PROCESSSPAWN:
			return CExpr.UOP.PROCESSSPAWN;
		}

		throw new IllegalArgumentException(
				"Invalid binary operation encountered: " + op);
	}
	// =========== CODES ===============
	
	private final static int ASSIGN = 0;
	private final static int CODEEXPR = 1;
	private final static int GOTO = 2;
	private final static int IFGOTO = 3;	
	private final static int LABEL = 4;
	private final static int FAIL = 5;
	private final static int FORALL = 6;
	private final static int FORALLEND = 7;
	private final static int INDUCT = 8;
	private final static int INDUCTEND = 9;
	private final static int RECURSE = 10;
	
	// =========== COP ===============
	
	private final static int EQ = 12;
	private final static int NEQ = 13;
	private final static int LT = 14;
	private final static int LTEQ = 15;
	private final static int GT = 16;
	private final static int GTEQ = 17;
	private final static int SUBSET = 18;
	private final static int SUBSETEQ = 19;
	private final static int ELEMOF = 20;
	private final static int SUBTYPEEQ = 21;
	private final static int NSUBTYPEEQ = 22;
	
	// =========== CEXPR ===============
	
	private final static int TYPEVAL = 8;
	private final static int VARIABLE = 10;
	private final static int REGISTER = 11;
	private final static int FUNINVOKE = 12;
	private final static int METHINVOKE = 13;		
	private final static int LISTACCESS = 14;
	private final static int RECORDACCESS = 15;
	
	// =========== UOP ===============
	
	private final static int NEG = 20;
	private final static int NOT = 21;
	private final static int LENGTHOF = 22;
	private final static int PROCESSACCESS = 23;
	private final static int PROCESSSPAWN = 24;
	
	// =========== BOP ===============
	
	private final static int ADD = 30;
	private final static int SUB = 31;
	private final static int MUL = 32;
	private final static int DIV = 33;
	private final static int UNION = 34;
	private final static int INTERSECT = 35;
	private final static int DIFFERENCE = 36;
	private final static int APPEND = 37;		
	
	// =========== NOP ===============
	
	private final static int SETGEN = 40;
	private final static int LISTGEN = 41;
	private final static int SUBLIST = 42;		
}
