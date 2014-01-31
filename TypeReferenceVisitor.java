package astparser.visitor;

import java.util.*;
import org.eclipse.jdt.core.dom.*;

/**
 * NameReferenceVisitor visits ASTNode, find all SimpleName nodes that match
 * the interest keys. That is, the visitor finds all nodes that are references
 * of the input keys. The input keys can be type keys, variable keys or 
 * any possible SimpleName keys.
 * The visitor will return parent nodes of the SimpleNode that it found. 
 * The result is returned as a hashmap where the map keys are the input keys.
 */

public class TypeReferenceVisitor extends ASTVisitor {
	private HashMap<String, ReferenceInfo> references;
	
	/**
	 * Constructor that set the input keys generated by Eclipse JDT
	 * An example of type keys: the type "android.view.MenuItem" has key
	 * "Landroid/view/MenuItem;"
	 * An example of local variable keys. An android.view.View field name
	 * aButton, declared in type astparser.tests.TypeReference has key
	 * "Lastparser/tests/TypeReference;.aButton)Landroid/view/View;"
	 */
	public TypeReferenceVisitor(String[] typeKeys) {			

		references = new HashMap<String, ReferenceInfo>(typeKeys.length);

		for (String key : typeKeys) {
			references.put(key, new ReferenceInfo(key));
		}
	}

	@Override 
	public boolean visit(Assignment node) {
		Expression lhs = node.getLeftHandSide();
		Expression rhs = node.getRightHandSide();

		//main interest: lhs, rhs
		String foundKey = null;
		foundKey = matchTypeKey(lhs);
		if (foundKey == null)
			foundKey = matchTypeKey(rhs);
		if (foundKey != null) {			
			// if found, then we keep this node
			// and don't need to explore it further
			references.get(foundKey).add(node);
			return false;
		}
		// if not found, then can be explore further by other visits
		// eg. MethodInvocation
		return true;
	}

	/**
	 * Check if the input expression has an interesting type
	 *
	 * @param ex  the expression to be check
	 * @return    type key of the expression if it has an interesting type,
	 * 				otherwise, null.
	 */
	private String matchTypeKey(Expression exp) {
		ITypeBinding typeBind = exp.resolveTypeBinding();
		
		if (typeBind != null) {
			String typeKey = typeBind.getKey();
			if (references.containsKey(typeKey))
				return typeKey;
		}
		
		return null;
	}

	@Override
	public boolean visit(MethodInvocation node) {
		// main interest: calling object, or arguments
		
		// if the method is invoked from an object, get that
		// object by getExpression()
		// null if no object, then we can move on
		// and check where the method is declared
		Expression callExp = node.getExpression();
		String foundKey = null;
		if (callExp != null) {
			foundKey = matchTypeKey(callExp);
			if (foundKey != null) {			
				// if found, then we keep this node
				// and don't need to explore it further
				references.get(foundKey).add(node);
				// here we found the type as the calling object
				return false;
			}
		}

		IMethodBinding binding = node.resolveMethodBinding();
		if (binding != null) {
			ITypeBinding type = binding.getDeclaringClass();
			if (type != null) {
				String typeKey =  type.getKey();
				if (references.containsKey(typeKey)) {
					references.get(typeKey).add(node);
					// here we found the type as the declaring class
					// of the method
					return false;
				}
			}
		}

		List<Expression> args = node.arguments();
		foundKey = null;
		for (Expression exp : args) {
			foundKey = matchTypeKey(exp);
			if (foundKey != null) {
				references.get(foundKey).add(node);
				// here we find an argument which has an interesting type
				return false;
			}
		}
		// found nothing, may go lower
		return true;
	}

	public List<ReferenceInfo> getNonEmpty () {
		Set<String> keys = references.keySet();
		List<ReferenceInfo> refs = new ArrayList<ReferenceInfo>();
		for (String key : keys) {
			ReferenceInfo ref = references.get(key);
			if (!ref.isEmpty())
				refs.add(ref);
		}
		return refs;
	}
}