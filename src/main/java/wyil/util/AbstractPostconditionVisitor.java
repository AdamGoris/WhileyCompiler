package wyil.util;

import wyil.lang.WyilFile.Decl;

public class AbstractPostconditionVisitor
		extends AbstractFunction<AbstractPostconditionVisitor.Context, AbstractPostconditionVisitor.Context> {

	@Override
	public Context visitType(Decl.Type type, Context context) {

		return null;
	}

	public static class Context {

	}
}
