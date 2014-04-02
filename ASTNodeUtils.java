package astparser;

import org.eclipse.jdt.core.dom.*;
import java.util.*;
import astparser.UIModel.*;

public class ASTNodeUtils {
	public static MethodDeclaration getContainingMethod(ASTNode node) {
		while (node != null 
				&& !(node instanceof MethodDeclaration)){
			node = node.getParent();
		}

		return (MethodDeclaration)node;
	}


	public static ASTNode getContainingType(ASTNode node) {
		while (node != null 
				&& !(node instanceof AbstractTypeDeclaration)
				&& !(node instanceof AnonymousClassDeclaration)){
			node = node.getParent();
		}

		return node;
	}

	public static String getMethodKey(MethodDeclaration method) {
		IMethodBinding methodBinding = null;
		if (method != null && (methodBinding = method.resolveBinding()) != null) {
			return methodBinding.getKey();
		}
		return null;
	}

	public static String getTypeKey(AbstractTypeDeclaration type) {
		ITypeBinding typeBinding = null;
		if (type != null && (typeBinding = type.resolveBinding()) != null) {
			return typeBinding.getKey();
		}
		return null;
	}

	public static String getTypeKey(AnonymousClassDeclaration type) {
		ITypeBinding typeBinding = null;
		if (type != null && (typeBinding = type.resolveBinding()) != null) {
			return typeBinding.getKey();
		}
		return null;	
	}


	public static Set<String> getSuperTypeQualifiedNames(ITypeBinding startClass) {
		Set<String> qualNames = null;
		if (startClass != null) {
			qualNames = new HashSet<String>();
			
			Queue<ITypeBinding> tbQueue = new LinkedList<ITypeBinding>();

			tbQueue.offer(startClass);

			ITypeBinding current;

			// a BFS to visit all super types
			// while the queue is not empty
			while ((current = tbQueue.poll()) != null) {
				String currentTypeName = current.getQualifiedName();
				if (!qualNames.contains(currentTypeName)) {
					// if we haven't seen this class yet

					qualNames.add(currentTypeName);

					// we find its direct super types
					tbQueue.addAll(Arrays.asList(current.getInterfaces()));

					ITypeBinding superClass = current.getSuperclass();
					if (superClass != null)
						tbQueue.offer(superClass);
				}
				// or else we have seen this type before,
				// so its super types have been listed,
				// we don't need to do anything
			}
		}

		return qualNames;
	}

	public static List<String> getSuperClassQualifiedNames(ITypeBinding
																 startClass) {
		List<String> qualNames = null;
		if (startClass != null) {
			qualNames = new ArrayList<String>();

			ITypeBinding current = startClass;

			// a BFS to visit all super types
			// while the queue is not empty
			while (current != null) {
				qualNames.add(current.getQualifiedName());
					
				current = current.getSuperclass();
			}
		}

		return qualNames;
	}


	public static List<ASTNode> getAllTypeNodes(ASTNode[] units) {
		final List<ASTNode> nodes = new ArrayList<ASTNode>();

		ASTVisitor visitor = new ASTVisitor() {
			@Override
			public boolean visit(AnonymousClassDeclaration node) {
				nodes.add(node);
				return true;
			}

			@Override 
			public boolean visit(TypeDeclaration node) {
				nodes.add(node);
				return true;
			}
		};

		for (ASTNode u : units) {
			u.accept(visitor);
		}

		return nodes;
	}

	public static String getMethodName(String classMethodName) {
		return classMethodName.substring(classMethodName.indexOf('#') + 1);
	}

	public static String getClassName(String classMethodName) {
		return classMethodName.substring(0, classMethodName.indexOf('#'));
	}

	public static boolean matchMethodByName(IMethodBinding tBinding,
													 String classMethodName) {
		String mName = ASTNodeUtils.getMethodName(classMethodName);
		String cName = ASTNodeUtils.getClassName(classMethodName);

		// match name first
		if (!mName.equals(tBinding.getName()))
			return false;
		// then match super types;
		ITypeBinding declaringClass = tBinding.getDeclaringClass();

		Set<String> superTypeNames
			= ASTNodeUtils.getSuperTypeQualifiedNames(declaringClass);

		if (superTypeNames == null)
			return false;

		
		return superTypeNames.contains(cName);
	}


	public static HashMap<String,List<MethodDeclaration>>
		matchAndroidUIMethodImpls(
			List<String> classMethodName,
			ASTNode[] units) {

		final HashMap<String, List<String>> 
			mapMethodName = new HashMap<String, List<String>>();

		for (String cMName : classMethodName) {
			String mName = ASTNodeUtils.getMethodName(cMName);
			String cName = ASTNodeUtils.getClassName(cMName);

			if (!mapMethodName.containsKey(mName)) {
				mapMethodName.put(mName, 
							new ArrayList<String>());
			}

			mapMethodName.get(mName).add(cName);
		}

		final HashMap<String, List<MethodDeclaration>>
			methodNameBindings = new HashMap<String, List<MethodDeclaration>>();

		ASTVisitor visitor = new ASTVisitor() {

			@Override
			public boolean visit(MethodDeclaration node) {
				String methodName = node.getName().toString();
				IMethodBinding methodBinding = node.resolveBinding();				

				if (methodBinding != null 
					&& mapMethodName.containsKey(methodName)) {

					ITypeBinding declaringClass = methodBinding.getDeclaringClass();

					Set<String> superTypeNames
						= ASTNodeUtils.getSuperTypeQualifiedNames(declaringClass);

					if (superTypeNames == null)
						return true;

					for (String cName : mapMethodName.get(methodName)) {
						if (superTypeNames.contains(cName)) {
							
							if (!methodNameBindings.containsKey(methodName)) {
								methodNameBindings.put(methodName, 
											new ArrayList<MethodDeclaration>());
							}

							methodNameBindings.get(methodName).add(node);
							break;
						}
					}
				}

				return true;
			}	
		};


		for (ASTNode u : units) {
			u.accept(visitor);
		}

		return methodNameBindings;

	}

	/**
	 *	Find the super class of the input subclass that match the qualified name
	 *	@return the ITypeBinding of the super class with the qualified name, or
	 *				null if not found
	 */
	public static ITypeBinding subClassOf(ITypeBinding subClass, 
													String superClassQName) {

		while (subClass != null &&
			!subClass.getQualifiedName().equals(superClassQName))
			subClass = subClass.getSuperclass();

		return subClass;
	}
	/**
	 *	find and bind events with the actual statements that set up the events
	 *	
	 *	@param allActions	contains all actions including the events and setters
	 */
	public static void bindEventSetters(HashMap<IMethodBinding, UIAction> 
																allActions) {

		for (UIAction action : allActions.values()) {
			// find action that binds events
			// redundant check
			if (action.type == UIAction.ActionType.EXTERNAL_UI
				&& action.metaClassInfo != null
				&& action.metaClassInfo.type == 
										UIActionClass.UIActionType.BIND_EVENT) {

				for (UIActionInvocation act : action.invokedList) {
					List<Expression> args = act.astSourceNode.arguments();
					ITypeBinding argTypeBinding;
					
					for (Expression exp : args) {
						if ((argTypeBinding = exp.resolveTypeBinding()) != null) {
							for (IMethodBinding method : 
										argTypeBinding.getDeclaredMethods()) {

								if (allActions.containsKey(method)) {
									UIAction action2 = allActions.get(method);
									UIActionInvocationBindEvent bindEAct 
											= (UIActionInvocationBindEvent)act;

									if (action2 instanceof UIActionLinkedEvent) {
										UIActionLinkedEvent eventAction
											= ((UIActionLinkedEvent)action2);

										if (eventAction.setters == null)
											eventAction.setters =
											  new HashSet<UIActionInvocation>();
										
										eventAction.setters.add(bindEAct);
									}

									if (bindEAct.bindedEvents == null)
										bindEAct.bindedEvents = new 
												ArrayList<UIAction>();
									bindEAct.bindedEvents.add(action2);
								}  // else {
								// 	System.out.println("null key args " + method); 
								// 	System.out.println("in " + exp);
								// }
							}
						}
					}
				}
			}
		}
	}

	/**
	 *	find all actions that start and end modal windows, bind those actions
	 *	with the corresponding windows (UIActionInvocation -> UIObject)
	 *
	 *	the implementation is Android-dependent
	 */
	public static void bindStartModals(HashMap<IMethodBinding, UIAction> 
													allActions, 
										HashMap<ITypeBinding, UIObject> 
													allUIObjects) {
		for (UIAction action : allActions.values()) {
			// find action that binds events
			// redundant check
			if (action.type == UIAction.ActionType.EXTERNAL_UI
				&& action.metaClassInfo != null
				&& (action.metaClassInfo.type 
									== UIActionClass.UIActionType.START_MODAL
					|| action.metaClassInfo.type 
									== UIActionClass.UIActionType.END_MODAL)) {

				for (UIActionInvocation actInvoke : action.invokedList) {
					UIActionInvocationStartModal act 
							= (UIActionInvocationStartModal)actInvoke;

					if (action.metaClassInfo.type == 
							UIActionClass.UIActionType.START_MODAL) {

						List<Expression> args = act.astSourceNode.arguments();
						ITypeBinding argTypeBinding;
						
						
						for (Expression exp : args) {
							if ((argTypeBinding = exp.resolveTypeBinding()) != null
								&& argTypeBinding.getQualifiedName().
										equals("android.content.Intent")) {
								
								// exp is an intent 
								// it can be either a variable, declare somewhere
								// or can be a ClassInstanceCreation

								// the creation of the intent is based on
								// the target Activity class
								// or the registered id in the AndroidManifest.xml

								// in any way, it may be too hard to identify it
								// statically
							}
						}

					} else if (action.metaClassInfo.type ==
							UIActionClass.UIActionType.END_MODAL) {

						// "finish" will end the calling activiy
						if (act.invokedMethod.getName().equals("finish")) {
							ITypeBinding callingClass =
								act.invokingMethod.getDeclaringClass();

							while (callingClass != null &&
									ASTNodeUtils.subClassOf(callingClass, 
										"android.app.Activity") == null) {
								callingClass = callingClass.getDeclaringClass();
							}

							if (callingClass != null)
								act.targetObject
									= allUIObjects.get(callingClass);
						}

						// finishActivity(int requestCode)
						// -> finish the activity that was called by
						// 		startActivityForResult(Intent intent,
						//								int requestCode)
						// they can be match by looking at intent and requestCode
					}
				}
			}
		}
	}

	/**
	 *	Check if a widget is enabled/disabled by some action invocation,
	 *	then check if that widget is binded with some events
	 *
	 *	This is Android-dependent
	 */
	public static void bindEnableWidgetWithEvents(
				HashMap<IMethodBinding, UIAction> allActions, 
				HashMap<ITypeBinding, UIObject> allUIObjects) {

		for (UIAction action : allActions.values()) {
			if (action.type == UIAction.ActionType.EXTERNAL_UI &&
				action.metaClassInfo != null &&
				action.metaClassInfo.type == UIActionClass.UIActionType.ENABLE_WIDGET) {

				// available supported Android Action:
				// <action method="setContentView" class="android.app.Activity" 
				// 	category="OUTSOURCE"
				// 	type="enable"/>
				// 	-> this requires finding the content view, parse the layout xml file
				//	and certainly requires identifying objects and their identity, which
				//	we are trying to advoid. Certainly this is not a concern if the events
				// 	are set-up by commands, rather then supplying event handlers through
				//	android:onClick properties
				// 	For now, this is ignored

				// <action method="setVisible" class="android.app.Activity" 
				// 	category="OUTSOURCE"
				// 	type="enable"/>
				//	-> this completely hides or shows an Activity, for now, let us ignore

				for (UIActionInvocation actInvoke : action.invokedList) {
					if (actInvoke instanceof UIActionInvocationEnableWidget) {

						UIActionInvocationEnableWidget act 
							= (UIActionInvocationEnableWidget)actInvoke;

						/* we have to identify the object that this action
								affects. Since we try to avoid this object
								identification, this is a stupid, ugly, hard-coded,
								restricted solution:

								we will only identify object with a name, i.e. a
								variable. then we search for its reference, hopefully 
								to find a set-event action, where we can learn 
								what events are binded to this object

							*/
						Expression callOnObject 
								= act.astSourceNode.getExpression();
						IBinding objBinding;

						Set<UIAction> affectedEvents = new HashSet<UIAction>();

						if (callOnObject != null && 
							callOnObject instanceof Name &&
							(objBinding = 
								((Name)callOnObject).resolveBinding())
								instanceof IVariableBinding) {
						
							// look for all actions that bind event upon
							// the same IVariableBinding
							for (UIAction act2 : allActions.values()) {
								if (act2.type == UIAction.ActionType.EXTERNAL_UI &&
									act2.metaClassInfo != null &&
									act2.metaClassInfo.type 
									== UIActionClass.UIActionType.BIND_EVENT) {

									for (UIActionInvocation actInv2 
											: act2.invokedList) {

										Expression coObj2 
										= actInv2.astSourceNode.getExpression();
										if (coObj2 != null
											&& coObj2 instanceof Name &&
											((Name)coObj2).resolveBinding()
												== objBinding) {
										// actInv2 is an UIActionInvocationBindEvent
											if (((UIActionInvocationBindEvent)actInv2).bindedEvents == null){
												// System.out.println("null binder " + 
												// 	actInv2.astSourceNode);
												// System.out.println("enable/disable in "
												// + act.astSourceNode);
											}
											else
											affectedEvents.addAll(
												((UIActionInvocationBindEvent)actInv2)
													.bindedEvents);
										}
									}
								}
							}
						}

						// <action method="setVisibility" class="android.view.View"
						// 	category="OUTSOURCE"
						// 	type="enable"/>
						//	-> setVisibility of a View, through View.VISIBLE, View.INVISIBLE,
						//		and View.GONE
						if (action.metaClassInfo.methodName.equals("setVisibility")
							&& action.metaClassInfo.classKey.equals("android.view.View")) {
							List<Expression> exps = act.astSourceNode.arguments();
							for (Expression exp : exps) {
								if (exp.toString().equals("View.VISIBLE")) {
									act.enabledEvents = affectedEvents;
									break;

								} else if (exp.toString().equals("View.INVISIBLE") ||
									exp.toString().equals("View.GONE")) {
									act.disabledEvents = affectedEvents;
									break;
								}
							}
						}
						// <action method="setEnabled" class="android.view.View"
						// 	category="OUTSOURCE"
						// 	type="enable"/>
						else if (action.metaClassInfo.methodName.equals("setEnabled")
							&& action.metaClassInfo.classKey.equals("android.view.View")) {
							List<Expression> exps = act.astSourceNode.arguments();
							for (Expression exp : exps) {
								if (exp.toString().equals("true")) {
									act.enabledEvents = affectedEvents;
									break;

								} else if (exp.toString().equals("false")) {
									act.disabledEvents = affectedEvents;
									break;
								}
							}
						}
					}
				}
			}
		}
	}
	/**
	 *	This method will, for each external execution, trace its execution path
	 *	up to the INTERAL_UI methods (INITS, TOP_EVENT, lINKED_EVENTS)
	 *	
	 *	Each path starts with an EXTERNAL_UI execution (UIActionInvocation)
	 *	and ends with a final UIActionInvocation, which is contained in an
	 *	INTERNAL_UI action (UIAction). The path is saved in a LinkedHashSet
	 *	to maintain the execution order, and to know which node in the AST has
	 *	been visited, in order to avoid cycles in path.
	 *	
	 *	Multiple UIActionInvocation paths that lead to the same INTERNAL_UI
	 *	action are grouped and saved in the UIActionInternal#executingPaths
	 *	properties.
	 *
	 *	@param allActions	contains all actions including the events and setters
	 */
	public static void traceExternalUIPaths(HashMap<IMethodBinding, UIAction>
															allActions) {

		for (UIAction action : allActions.values()) {

			if (action.type == UIAction.ActionType.EXTERNAL_UI) {

				Deque<UIActionInvocation> stack 
										= new ArrayDeque<UIActionInvocation>();
				Deque<LinkedHashSet<UIActionInvocation>> pathStack 
						= new ArrayDeque<LinkedHashSet<UIActionInvocation>>();

				LinkedHashSet<UIActionInvocation> currentPath;

				for (UIActionInvocation act : action.invokedList) {
					currentPath = new LinkedHashSet<UIActionInvocation>();

					currentPath.add(act);

					stack.addFirst(act);
					pathStack.addFirst(currentPath);	
				}
				

				UIActionInvocation currentAct;
				// DFS
				while ((currentAct = stack.peekFirst()) != null) {
					stack.removeFirst();
					currentPath = pathStack.removeFirst();
				
					UIAction invoker = allActions.get(currentAct.invokingMethod);

					if (invoker == null) 
						System.out.println("null invoker" + currentAct.invokingMethod);
					else 
					if (invoker.invokedList == null 
							|| invoker.invokedList.size() == 0) {

						
						// if the invoker of the invoker is not available: 
						// we have reach the sink node
						ITypeBinding invokerDeclaringClass 
							= invoker.containingType;

						// if the sink node is of type INTERNAL_UI, check it
						// else if the node is of type INTERNAL_APP_DEFINED,
						// the it probably is a redundant method

						// redundant check
						if (invoker.type == UIAction.ActionType.INTERNAL_UI
							&& invoker instanceof UIActionInternal) {

							UIActionInternal internalAct 
										= (UIActionInternal)invoker;

							if (internalAct.executingPaths == null)
								internalAct.executingPaths = new 
									ArrayList<LinkedHashSet<UIActionInvocation>>();
							
							internalAct.executingPaths.add(currentPath);

						}

					} else {
						// this method is still called by others, keep going down
						for (UIActionInvocation invokingAct 
														: invoker.invokedList) {
							// avoid cycles
							if (!currentPath.contains(invokingAct)) {
								LinkedHashSet<UIActionInvocation> nextPath = new 
									LinkedHashSet<UIActionInvocation>(currentPath);

								nextPath.add(invokingAct);

								stack.addFirst(invokingAct);
								pathStack.addFirst(nextPath);
							}
						}
					}
				}
			}
		}
	}

	/**
	 *	This method takes an ITypeBinding, and a map of UIObjectClass as input,
	 *	then find if the ITypeBinding is a sub-class of any class defined in the
	 *	map.
	 *
	 *	@return the matched UIObjectClass that are super-class of the binding,
	 *				null if not found.
	 */
	public static UIObjectClass findUIObjectClass
					(ITypeBinding type, HashMap<String, UIObjectClass> map) {

		ITypeBinding parent = type;

		while (parent != null) {
			String name = parent.getQualifiedName();
			if (map.containsKey(name)) {
				return map.get(name);
			}

			parent = parent.getSuperclass();
		}

		return null;
	}

	/**
	 *	This method find all possibly interesting UIObject's (windows/widgets/etc)
	 *	It sets up the object with the meta class info (UIObjectClass), the
	 *	initializing methods, and the top-event methods.
	 *
	 *	@return a map from the type key to the UIObject
	 */
	public static HashMap<ITypeBinding, UIObject>
	findAllUIObjects(HashMap<String, UIObjectClass> objectClassMeta,
					 HashMap<IMethodBinding, UIAction> allActions) {

		HashMap<ITypeBinding, UIObject> uiObjects =
							new HashMap<ITypeBinding, UIObject>();

		for (UIAction action : allActions.values()) {
			if (action.type == UIAction.ActionType.INTERNAL_UI
				&& action.metaClassInfo != null && 
				(action.metaClassInfo.type == UIActionClass.UIActionType.INIT || 
				action.metaClassInfo.type == UIActionClass.UIActionType.TOP_EVENT)) {
				// if the action is an INIT or TOP_EVENT
				UIObject uiO;

				if (!uiObjects.containsKey(action.containingType)) {
					// if we have not seen this UIObject before, create one
					uiO = new UIObject();
					uiO.typeBinding = action.containingType;
					uiO.metaClassInfo = ASTNodeUtils.findUIObjectClass(
											uiO.typeBinding, objectClassMeta);
					// add to list
					uiObjects.put(action.containingType, uiO);
				} else {
					uiO = uiObjects.get(action.containingType);
				}

				switch (action.metaClassInfo.type) {
					case INIT:
						if (uiO.initActions == null)
							uiO.initActions = new 
										HashMap<IMethodBinding, UIAction>();
						uiO.initActions.put(action.methodBinding, action);
						break;
					case TOP_EVENT:
						if (uiO.topEventActions == null)
							uiO.topEventActions = new 
										HashMap<IMethodBinding, UIAction>();
						uiO.topEventActions.put(action.methodBinding, action);
						break;
				}
			}
		}

		return uiObjects;
	}


	public static String matchSuperClass(ITypeBinding tBind, 
										Set<String> classes) {
		String name = null;
		while (tBind != null) {
			name = tBind.getQualifiedName();
			if (classes.contains(name)) {
				return name;
			}
			tBind = tBind.getSuperclass();
		}
		return null;
	}
}
