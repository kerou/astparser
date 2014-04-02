package astparser.UIModel;

import org.eclipse.jdt.core.dom.*;
import java.util.*;

public class UIActionInternal extends UIAction {
	
	/**
	 *	The AST node that defines the method. It is actually the source code
	 *	of the method. EXTERNAL methods don't have this property.
	 *
	 *	This property is Eclipse JDT dependent.
	 */
	public MethodDeclaration declaration;

	/**
	 *	Paths of calling
	 *	Each path is a series of calls created by the method, starting from bottom
	 *	with the last external method to be called to the method.
	 * 	For example, if the method calls a method A, A then calls B, B then calls
	 *	external method setOnClickListener, then the path will be
	 *	setOnClickListener -> B -> A
	 *	The path is explored using DFS, starting from the external method, visiting
	 *	through the calling method (using IMethodBinding fields declared in 
	 *	UIActionInvocation) until it reaches an implicitly called method, which
	 *	is of type INTERNAL_UI
	 *
	 *	We can extend the paths to include branching among the invocations: add
	 *	the branching blocks as nodes between the invokee and the invoker. Then
	 *	the complete path will include all the needed information about the effects
	 *	of an INTERNAL_UI. By merging the paths (grouping the actions as AND), 
	 *	we can conclude all the possible effects (the ungroupable paths are OR
	 *	effects).
	 *
	 * 	About merging of paths, we have some initial ideas (unproved):
	 *		- merge all paths that only differ by the external invocation
	 *		- path without branching nodes should be merged with those that have
	 *			branching in order to complete the whole effects
	 */
	public List<LinkedHashSet<UIActionInvocation>> executingPaths;

	private	HashMap<LinkedHashSet<UIActionInvocation>, Set<UIActionInvocation>> 
		possibleEffectsMap; 
	/**
	 *	Calculate the effect of an INTERNAL_UI action
	 *	Version 0.1: no branching information
	 *	TODO: add branching and adjust the merge operation
	 *
	 *	@return OR-set of possible effects. Each possible effect is an AND-set of
	 *			actions
	 */
	public Collection<Set<UIActionInvocation>> getPossibleEffects() {

		if (executingPaths == null)
			return null;

		if (possibleEffectsMap == null) {

			// we remove the external actions, and then check which sets are equal
			
			possibleEffectsMap 
			= new HashMap<LinkedHashSet<UIActionInvocation>, Set<UIActionInvocation>>();
			
			for (LinkedHashSet<UIActionInvocation> path : executingPaths) {
				LinkedHashSet<UIActionInvocation> newPath 
					= new LinkedHashSet<UIActionInvocation>(path);

				UIActionInvocation externalInvoke = newPath.iterator().next();
				newPath.remove(externalInvoke);
				
				if (!possibleEffectsMap.containsKey(newPath)) {
					possibleEffectsMap.put(newPath, new HashSet<UIActionInvocation>());
				}

				possibleEffectsMap.get(newPath).add(externalInvoke);
			}
		}
			
		return possibleEffectsMap.values();
	}

	private	Set<UIActionInvocation> allPossibleEffects; 

	public Set<UIActionInvocation> getAllPossibleEffects() {
		if (executingPaths == null)
			return null;

		if (allPossibleEffects == null) {
			allPossibleEffects = new HashSet<UIActionInvocation>();

			for (LinkedHashSet<UIActionInvocation> path : executingPaths) {
				allPossibleEffects.add(path.iterator().next());
			}	
		}
		

		return allPossibleEffects;
	}

	// BIND_EVENT & ENABLE_WIDGET
	public Set<UIAction> getEnabledEvents() {
		Set<UIActionInvocation> effects = getAllPossibleEffects();

		Set<UIAction> enabledEvents = new HashSet<UIAction>();
		
		if (effects != null) {
			

			for (UIActionInvocation effect : effects) {
				
				if (effect instanceof UIActionInvocationBindEvent) {
				// either by binding an event
					UIActionInvocationBindEvent ebActInv = 
						(UIActionInvocationBindEvent)effect;

					if (ebActInv.bindedEvents != null) {
						enabledEvents.addAll(ebActInv.bindedEvents);
					}
				} else if (effect instanceof UIActionInvocationEnableWidget) {
				// or by enabling a widget with some already binded events
					UIActionInvocationEnableWidget ewActInv = 
						(UIActionInvocationEnableWidget)effect;

					if (ewActInv.enabledEvents != null)
						enabledEvents.addAll(ewActInv.enabledEvents);
				}
			}
		}
		return enabledEvents;
	}

	// ENABLE_WIDGET
	public Set<UIAction> getDisabledEvents() {

		Set<UIActionInvocation> effects = getAllPossibleEffects();

		Set<UIAction> disabledEvents = new HashSet<UIAction>();
		
		if (effects != null) {
			for (UIActionInvocation effect : effects) {
				if (effect instanceof UIActionInvocationEnableWidget) {
					UIActionInvocationEnableWidget ewActInv = 
						(UIActionInvocationEnableWidget)effect;

					if (ewActInv.disabledEvents != null)
						disabledEvents.addAll(ewActInv.disabledEvents);
				}
			}
		}
		return disabledEvents;
	}

	public Set<UIActionInvocation> getStartEndModals() {
		Set<UIActionInvocation> effects = getAllPossibleEffects();

		Set<UIActionInvocation> startModals = new HashSet<UIActionInvocation>();
		
		if (effects != null) {
			for (UIActionInvocation effect : effects) {
				if (effect instanceof UIActionInvocationStartModal) {
					UIActionInvocationStartModal smActInv = 
						(UIActionInvocationStartModal)effect;

					startModals.add(smActInv);
				}
			}
		}
		return startModals;

	}
}