// Copyright 2011 The Whiley Project Developers
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package wyil.transform;

import wyil.lang.WyilFile.Decl;
import wyil.util.AbstractFunction;

/**
 * Responsible for versioning variables in a manner suitable for verification
 * condition generation. The transformation is similar, in some ways, with
 * static single assignment form.
 *
 * @author David J. Pearce
 *
 */
public class VariableVersioning
		extends AbstractFunction<VariableVersioning.Environment, VariableVersioning.Environment> {

	@Override
	public Environment visitType(Decl.Type type, Environment environment) {
		// NOTE: there is nothing to do for type declarations
		return environment;
	}

	@Override
	public Environment visitStaticVariable(Decl.StaticVariable type, Environment environment) {
		// NOTE: there is nothing to do for static variable declarations
		return environment;
	}

	@Override
	public Environment visitFunctionOrMethod(Decl.FunctionOrMethod fm, Environment environment) {

		return environment;
	}

	/**
	 * The empty environment is provided as a constant for simplicity.
	 */
	public static final Environment EMPTY_ENVIRONMENT = new Environment();

	/**
	 * The environment maps local variables to their versioned
	 *
	 * @author David J. Pearce
	 *
	 */
	public static class Environment {

	}
}
