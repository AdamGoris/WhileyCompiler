package wycs.transforms;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static wybs.lang.SyntaxError.*;
import wybs.lang.Attribute;
import wybs.lang.Builder;
import wybs.lang.NameID;
import wybs.lang.Transform;
import wybs.util.Pair;
import wybs.util.ResolveError;
import wycs.builders.Wyal2WycsBuilder;
import wycs.syntax.*;

public class ConstraintInline implements Transform<WyalFile> {
	
	/**
	 * Determines whether constraint inlining is enabled or not.
	 */
	private boolean enabled = getEnable();

	private final Wyal2WycsBuilder builder;
	
	private String filename;

	// ======================================================================
	// Constructor(s)
	// ======================================================================
	
	public ConstraintInline(Builder builder) {
		this.builder = (Wyal2WycsBuilder) builder;
	}
	
	// ======================================================================
	// Configuration Methods
	// ======================================================================

	public static String describeEnable() {
		return "Enable/disable constraint inlining";
	}

	public static boolean getEnable() {
		return true; // default value
	}

	public void setEnable(boolean flag) {
		this.enabled = flag;
	}

	// ======================================================================
	// Apply Method
	// ======================================================================

	public void apply(WyalFile wf) {
		if(enabled) {
			this.filename = wf.filename();
			for(WyalFile.Declaration s : wf.declarations()) {
				transform(s);
			}
		}
	}
	
	private void transform(WyalFile.Declaration s) {
		if(s instanceof WyalFile.Function) {
			WyalFile.Function sf = (WyalFile.Function) s;
			transform(sf);
		} else if(s instanceof WyalFile.Define) {
			WyalFile.Define sf = (WyalFile.Define) s;
			transform(sf);
		} else if(s instanceof WyalFile.Assert) {
			transform((WyalFile.Assert)s);
		} else if(s instanceof WyalFile.Import) {
			// can ignore for now
		} else {
			internalFailure("unknown declaration encountered (" + s + ")",
					filename, s);
		}
	}
	
	private void transform(WyalFile.Function s) {
		if(s.constraint != null) {
			s.constraint = transformCondition(s.constraint,s);
		}
	}
	
	private void transform(WyalFile.Define s) {
		s.condition = transformCondition(s.condition,s);
	}
	
	private void transform(WyalFile.Assert s) {
		s.expr = transformCondition(s.expr,s);
	}
	
	private Expr transformCondition(Expr e, WyalFile.Context context) {
		if (e instanceof Expr.Variable || e instanceof Expr.Constant) {
			// do nothing
			return e;
		} else if (e instanceof Expr.Unary) {
			return transformCondition((Expr.Unary)e, context);
		} else if (e instanceof Expr.Binary) {
			return transformCondition((Expr.Binary)e, context);
		} else if (e instanceof Expr.Nary) {
			return transformCondition((Expr.Nary)e, context);
		} else if (e instanceof Expr.Quantifier) {
			return transformCondition((Expr.Quantifier)e, context);
		} else if (e instanceof Expr.FunCall) {
			return transformCondition((Expr.FunCall)e, context);
		} else {
			internalFailure("invalid boolean expression encountered (" + e
					+ ")", filename, e);
			return null;
		}
	}
	
	private Expr transformCondition(Expr.Unary e, WyalFile.Context context) {
		switch(e.op) {
		case NOT:
			e.operand = transformCondition(e.operand, context);
			return e;
		default:
			internalFailure("invalid boolean expression encountered (" + e
					+ ")", filename, e);
			return null;
		}
	}
	
	private Expr transformCondition(Expr.Binary e, WyalFile.Context context) {
		switch (e.op) {
		case EQ:
		case NEQ:
		case LT:
		case LTEQ:
		case GT:
		case GTEQ:
		case IN:
		case SUBSET:
		case SUBSETEQ:
		case SUPSET:
		case SUPSETEQ: {
			ArrayList<Expr> assumptions = new ArrayList<Expr>();
			transformExpression(e, assumptions, context);
			if (assumptions.size() > 0) {
				Expr lhs = Expr.Nary(Expr.Nary.Op.AND, assumptions,
						e.attribute(Attribute.Source.class));				
				return Expr.Binary(Expr.Binary.Op.IMPLIES, lhs,e,
						e.attribute(Attribute.Source.class));
			} else {
				return e;
			}
		}
		case IMPLIES:
		case IFF: {
			e.leftOperand = transformCondition(e.leftOperand, context);
			e.rightOperand = transformCondition(e.rightOperand, context);
			return e;
		}
		default:
			internalFailure("invalid boolean expression encountered (" + e
					+ ")", filename, e);
			return null;
		}
	}
	
	private Expr transformCondition(Expr.Nary e, WyalFile.Context context) {
		switch(e.op) {
		case AND:
		case OR: {
			Expr[] e_operands = e.operands;
			for(int i=0;i!=e_operands.length;++i) {
				e_operands[i] = transformCondition(e_operands[i], context);
			}
			return e;
		}		
		default:
			internalFailure("invalid boolean expression encountered (" + e
					+ ")", filename, e);
			return null;
		}
	}
	
	private Expr transformCondition(Expr.Quantifier e, WyalFile.Context context) {
		ArrayList<Expr> assumptions = new ArrayList<Expr>();

		e.operand = transformCondition(e.operand, context);
		if (assumptions.size() > 0) {
			Expr lhs = Expr.Nary(Expr.Nary.Op.AND, assumptions,
					e.attribute(Attribute.Source.class));
			return Expr.Binary(Expr.Binary.Op.IMPLIES, lhs, e,
					e.attribute(Attribute.Source.class));
		} else {
			return e;
		}
	}
	
	private Expr transformCondition(Expr.FunCall e, WyalFile.Context context) {
		ArrayList<Expr> assumptions = new ArrayList<Expr>();
		Expr r = e;
		
		try {
			Pair<NameID, WyalFile.Function> p = builder.resolveAs(e.name,
					WyalFile.Function.class, context);
			WyalFile.Function fn = p.second();
			if(fn.constraint != null) {		
				// TODO: refactor this with the identical version later on
				HashMap<String,SyntacticType> typing = new HashMap<String,SyntacticType>();
				for(int i=0;i!=fn.generics.size();++i) {
					String name = fn.generics.get(i);
					typing.put(name, e.generics[i]);
				}
				HashMap<String,Expr> binding = new HashMap<String,Expr>();
				bind(e.operand,fn.from,binding);			
				bind(e,fn.to,binding);
				assumptions.add(fn.constraint.substitute(binding).instantiate(typing));		
			}
		} catch(ResolveError re) {
			// This indicates we couldn't find a function with the corresponding
			// name. But, we don't want to give up just yet. It could be a macro
			// definition!
			try {
				Pair<NameID, WyalFile.Define> p = builder.resolveAs(e.name,
						WyalFile.Define.class, context);
				WyalFile.Define dn = p.second();
				// TODO: refactor this with the identical version previously
				HashMap<String,SyntacticType> typing = new HashMap<String,SyntacticType>();
				for(int i=0;i!=dn.generics.size();++i) {
					String name = dn.generics.get(i);
					typing.put(name, e.generics[i]);
				}				
				HashMap<String,Expr> binding = new HashMap<String,Expr>();
				bind(e.operand,dn.from,binding);							
				r = dn.condition.substitute(binding).instantiate(typing);
			} catch (ResolveError err2) {
				internalFailure("cannot resolve as function or definition", context
						.file().filename(), e);
				return null;
			}
		}
		
		transformExpression(e.operand, assumptions, context);
		if (assumptions.size() > 0) {
			Expr lhs = Expr.Nary(Expr.Nary.Op.AND, assumptions,
					e.attribute(Attribute.Source.class));				
			return Expr.Binary(Expr.Binary.Op.IMPLIES, lhs,r,
					e.attribute(Attribute.Source.class));
		} else {
			return r;
		} 
	}
	
	private void transformExpression(Expr e, ArrayList<Expr> constraints, WyalFile.Context context) {
		if (e instanceof Expr.Variable || e instanceof Expr.Constant) {
			// do nothing
		} else if (e instanceof Expr.Unary) {
			transformExpression((Expr.Unary)e,constraints,context);
		} else if (e instanceof Expr.Binary) {
			transformExpression((Expr.Binary)e,constraints,context);
		} else if (e instanceof Expr.Nary) {
			transformExpression((Expr.Nary)e,constraints,context);
		} else if (e instanceof Expr.Load) {
			transformExpression((Expr.Load)e,constraints,context);
		} else if (e instanceof Expr.FunCall) {
			transformExpression((Expr.FunCall)e,constraints,context);
		} else {
			internalFailure("invalid expression encountered (" + e
					+ ", " + e.getClass().getName() + ")", filename, e);
		}
	}
	
	private void transformExpression(Expr.Unary e, ArrayList<Expr> constraints, WyalFile.Context context) {
		switch (e.op) {
		case NOT:
		case NEG:
		case LENGTHOF:
			transformExpression(e.operand,constraints,context);
			break;					
		default:
			internalFailure("invalid unary expression encountered (" + e
					+ ")", filename, e);			
		}
	}
	
	private void transformExpression(Expr.Binary e, ArrayList<Expr> constraints, WyalFile.Context context) {
		switch (e.op) {
		case ADD:
		case SUB:
		case MUL:
		case DIV:
		case REM:
		case EQ:
		case NEQ:
		case IMPLIES:
		case IFF:
		case LT:
		case LTEQ:
		case GT:
		case GTEQ:
		case IN:
		case SUBSET:
		case SUBSETEQ:
		case SUPSET:
		case SUPSETEQ:
			transformExpression(e.leftOperand,constraints,context);
			transformExpression(e.rightOperand,constraints,context);
			break;
		default:
			internalFailure("invalid binary expression encountered (" + e
					+ ")", filename, e);			
		}
	}
	
	private void transformExpression(Expr.Nary e, ArrayList<Expr> constraints, WyalFile.Context context) {
		switch(e.op) {
		case AND:
		case OR:
		case SET:
		case TUPLE: {
			Expr[] e_operands = e.operands;
			for(int i=0;i!=e_operands.length;++i) {
				transformExpression(e_operands[i],constraints,context);
			}
			break;
		}				
		default:
			internalFailure("invalid nary expression encountered (" + e
					+ ")", filename, e);
		}
	}
	
	private void transformExpression(Expr.Load e, ArrayList<Expr> constraints, WyalFile.Context context) {
		transformExpression(e.operand,constraints,context);
	}
	
	private void transformExpression(Expr.FunCall e,
			ArrayList<Expr> constraints, WyalFile.Context context) {
		transformExpression(e.operand,constraints,context);		
		try {			
			Pair<NameID,WyalFile.Function> p = builder.resolveAs(e.name,WyalFile.Function.class,context);
			WyalFile.Function fn = p.second();
			if(fn.constraint != null) {
				HashMap<String,Expr> binding = new HashMap<String,Expr>();
				bind(e.operand,fn.from,binding);			
				bind(e,fn.to,binding);	
				// TODO: refactor this with the identical version later on
				HashMap<String,SyntacticType> typing = new HashMap<String,SyntacticType>();
				for(int i=0;i!=fn.generics.size();++i) {
					String name = fn.generics.get(i);
					typing.put(name, e.generics[i]);
				}
				constraints.add(fn.constraint.substitute(binding).instantiate(typing));						
			}
		} catch(ResolveError re) {
			// TODO: we should throw an internal failure here:
			//  internalFailure(re.getMessage(),filename,context,re);
			// but, for now, I won't until I figure out how to deal with
			// external function calls at the Whiley source level. 
		}
	}
	
	private void bind(Expr operand, TypePattern pattern,
			HashMap<String, Expr> binding) {
		if (pattern.var != null) {
			binding.put(pattern.var, operand);
		}
		if (pattern instanceof TypePattern.Tuple && operand instanceof Expr.Nary) {
			TypePattern.Tuple tt = (TypePattern.Tuple) pattern;
			Expr.Nary tc = (Expr.Nary) operand;
			if (tt.patterns.length != tc.operands.length
					|| tc.op != Expr.Nary.Op.TUPLE) {
				internalFailure("cannot bind function call to declaration",
						filename, operand);
			}
			TypePattern[] patterns = tt.patterns;
			Expr[] arguments = tc.operands;
			for (int i = 0; i != arguments.length; ++i) {
				bind(arguments[i], patterns[i], binding);
			}
		}
	}	
}
