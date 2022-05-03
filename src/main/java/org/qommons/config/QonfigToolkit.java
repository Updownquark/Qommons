package org.qommons.config;

import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.qommons.MultiInheritanceMap;
import org.qommons.MultiInheritanceSet;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterCollections;
import org.qommons.collect.BetterHashMultiMap;
import org.qommons.collect.BetterMultiMap;

/** A structure containing a set of types that can be used to parsed highly-structured and validated {@link QonfigDocument}s */
public class QonfigToolkit {
	public interface ToolkitBuilder {
		void parseTypes(QonfigParseSession session);

		void fillOutTypes(QonfigParseSession session);

		Set<QonfigElementDef> getDeclaredRoots(QonfigParseSession session);
	}

	private final URL theLocation;
	private final Map<String, QonfigToolkit> theDependencies;
	private final Map<String, QonfigValueType.Declared> theDeclaredAttributeTypes;
	private final Map<String, QonfigValueType.Declared> theCompiledAttributeTypes;
	private final Map<String, QonfigElementDef> theDeclaredElements;
	private final BetterMultiMap<String, QonfigElementDef> theCompiledElements;
	private final Map<String, QonfigAddOn> theDeclaredAddOns;
	private final BetterMultiMap<String, QonfigAddOn> theCompiledAddOns;
	private final List<QonfigAutoInheritance> theDeclaredAutoInheritance;
	private final MultiInheritanceMap<QonfigElementOrAddOn, MultiInheritanceSet<QonfigAddOn>> theTypeAutoInheritance;
	private final MultiInheritanceMap<QonfigChildDef, MultiInheritanceSet<QonfigAddOn>> theRoleAutoInheritance;
	private final MultiInheritanceMap<QonfigElementOrAddOn, MultiInheritanceMap<QonfigChildDef, MultiInheritanceSet<QonfigAddOn>>> theTypeAndRoleAutoInheritance;
	private final Set<QonfigElementDef> theDeclaredRoots;
	private final Set<QonfigElementDef> theRoots;

	public QonfigToolkit(URL location, Map<String, QonfigToolkit> dependencies, Map<String, QonfigValueType.Declared> declaredTypes,
		Map<String, QonfigAddOn> declaredAddOns, Map<String, QonfigElementDef> declaredElements,
		List<QonfigAutoInheritance> autoInheritance, ToolkitBuilder builder) throws QonfigParseException {
		theLocation = location;
		theDependencies = dependencies;
		theDeclaredAttributeTypes = declaredTypes;
		theDeclaredAddOns = declaredAddOns;
		theDeclaredElements = declaredElements;
		theDeclaredAutoInheritance = autoInheritance;

		if (builder != null) {
			QonfigParseSession session = QonfigParseSession.forRoot("qonfig-def", this);
			builder.parseTypes(session);

			Map<String, QonfigValueType.Declared> compiledTypes = new HashMap<>(theDeclaredAttributeTypes);
			Set<String> conflictedTypeNames = new HashSet<>();
			BetterMultiMap<String, QonfigAddOn> compiledAddOns = BetterHashMultiMap.<String, QonfigAddOn> build().withDistinctValues()
				.buildMultiMap();
			BetterMultiMap<String, QonfigElementDef> compiledElements = BetterHashMultiMap.<String, QonfigElementDef> build()
				.withDistinctValues().buildMultiMap();
			for (QonfigAddOn el : theDeclaredAddOns.values())
				compiledAddOns.add(el.getName(), el);
			for (QonfigElementDef el : theDeclaredElements.values())
				compiledElements.add(el.getName(), el);
			Set<QonfigElementDef> depRoots = new LinkedHashSet<>();
			for (QonfigToolkit dep : dependencies.values()) {
				depRoots.addAll(dep.getRoots());
				for (QonfigElementDef e : dep.theCompiledElements.values())
					compiledElements.add(e.getName(), e);
				for (QonfigAddOn e : dep.theCompiledAddOns.values())
					compiledAddOns.add(e.getName(), e);
				for (Map.Entry<String, QonfigValueType.Declared> type : dep.theCompiledAttributeTypes.entrySet()) {
					if (conflictedTypeNames.contains(type.getKey()))
						continue;
					QonfigValueType.Declared found = compiledTypes.putIfAbsent(type.getKey(), type.getValue());
					if (found != null) {
						if (found.getDeclarer() != this) {
							conflictedTypeNames.add(type.getKey());
							compiledTypes.remove(type.getKey());
						}
					}
				}
			}
			theCompiledElements = BetterCollections.unmodifiableMultiMap(compiledElements);
			theCompiledAddOns = BetterCollections.unmodifiableMultiMap(compiledAddOns);
			theCompiledAttributeTypes = Collections.unmodifiableMap(compiledTypes);

			builder.fillOutTypes(session);
			theDeclaredRoots = builder.getDeclaredRoots(session);
			Set<QonfigElementDef> allRoots = new LinkedHashSet<>();
			allRoots.addAll(theDeclaredRoots);
			allRoots.addAll(depRoots);
			theRoots = Collections.unmodifiableSet(allRoots);
			session.throwErrors(location == null ? "Document" : location.toString())//
				.printWarnings(System.err, location == null ? "Document" : location.toString());

			theTypeAutoInheritance = MultiInheritanceMap.create(QonfigElementOrAddOn::isAssignableFrom);
			theRoleAutoInheritance = MultiInheritanceMap.create(QonfigChildDef::isFulfilledBy);
			theTypeAndRoleAutoInheritance = MultiInheritanceMap.create(QonfigElementOrAddOn::isAssignableFrom);
			for (QonfigToolkit dep : theDependencies.values()) {
				for (Map.Entry<QonfigElementOrAddOn, MultiInheritanceSet<QonfigAddOn>> typeInh : dep.theTypeAutoInheritance.entrySet()) {
					theTypeAutoInheritance.compute(typeInh.getKey(), (t, old) -> {
						if (old == null)
							old = MultiInheritanceSet.create(QonfigAddOn::isAssignableFrom);
						old.addAll(typeInh.getValue().values());
						return old;
					});
				}
				for (Map.Entry<QonfigChildDef, MultiInheritanceSet<QonfigAddOn>> roleInh : dep.theRoleAutoInheritance.entrySet()) {
					theRoleAutoInheritance.compute(roleInh.getKey(), (r, old) -> {
						if (old == null)
							old = MultiInheritanceSet.create(QonfigAddOn::isAssignableFrom);
						old.addAll(roleInh.getValue().values());
						return old;
					});
				}
				for (Map.Entry<QonfigElementOrAddOn, MultiInheritanceMap<QonfigChildDef, MultiInheritanceSet<QonfigAddOn>>> typeRoleInh : dep.theTypeAndRoleAutoInheritance
					.entrySet()) {
					theTypeAndRoleAutoInheritance.compute(typeRoleInh.getKey(), (t, roleInh) -> {
						if (roleInh == null)
							roleInh = MultiInheritanceMap
								.<QonfigChildDef, MultiInheritanceSet<QonfigAddOn>> create(QonfigChildDef::isFulfilledBy);
						for (Map.Entry<QonfigChildDef, MultiInheritanceSet<QonfigAddOn>> roleInhEntry : typeRoleInh.getValue().entrySet()) {
							roleInh.compute(roleInhEntry.getKey(), (r, old) -> {
								if (old == null)
									old = MultiInheritanceSet.create(QonfigAddOn::isAssignableFrom);
								old.addAll(roleInhEntry.getValue().values());
								return old;
							});
						}
						return roleInh;
					});
				}
			}
			for (QonfigAutoInheritance autoInherit : theDeclaredAutoInheritance) {
				for (QonfigAutoInheritance.AutoInheritTarget target : autoInherit.getTargets()) {
					if (target.getTarget() != null) {
						if (target.getRole() != null) {
							theTypeAndRoleAutoInheritance.compute(target.getTarget(), (t, roleInh) -> {
								if (roleInh == null)
									roleInh = MultiInheritanceMap
										.<QonfigChildDef, MultiInheritanceSet<QonfigAddOn>> create(QonfigChildDef::isFulfilledBy);
								roleInh.compute(target.getRole(), (r, old) -> {
									if (old == null)
										old = MultiInheritanceSet.create(QonfigAddOn::isAssignableFrom);
									old.addAll(autoInherit.getInheritance().values());
									return old;
								});
								return roleInh;
							});
						} else {
							theTypeAutoInheritance.compute(target.getTarget(), (t, old) -> {
								if (old == null)
									old = MultiInheritanceSet.create(QonfigAddOn::isAssignableFrom);
								old.addAll(autoInherit.getInheritance().values());
								return old;
							});
						}
					} else {
						theRoleAutoInheritance.compute(target.getRole(), (r, old) -> {
							if (old == null)
								old = MultiInheritanceSet.create(QonfigAddOn::isAssignableFrom);
							old.addAll(autoInherit.getInheritance().values());
							return old;
						});
					}
				}
			}
		} else { // PLACEHOLDER
			theCompiledElements = null;
			theCompiledAddOns = null;
			theCompiledAttributeTypes = null;
			theDeclaredRoots = null;
			theRoots = null;
			theTypeAutoInheritance = null;
			theRoleAutoInheritance = null;
			theTypeAndRoleAutoInheritance = null;
		}
	}

	/** @return The resource location defining the toolkit */
	public URL getLocation() {
		return theLocation;
	}

	/** @return All toolkits extended by this toolkit, by name */
	public Map<String, QonfigToolkit> getDependencies() {
		return theDependencies;
	}

	/**
	 * @param toolkit The toolkit to check
	 * @return Whether this toolkit depends on the given toolkit, either as one of its dependencies or any order deep of its dependencies'
	 *         dependencies
	 */
	public boolean dependsOn(QonfigToolkit toolkit) {
		if (toolkit == this)
			return true;
		for (QonfigToolkit dep : theDependencies.values()) {
			if (dep.dependsOn(toolkit))
				return true;
		}
		return false;
	}

	/** @return All element definitions declared by this toolkit */
	public Map<String, QonfigElementDef> getDeclaredElements() {
		return theDeclaredElements;
	}

	/** @return All element definitions declared or inherited by this toolkit */
	public BetterMultiMap<String, QonfigElementDef> getAllElements() {
		return theCompiledElements;
	}

	/** @return All add-on definitions declared by this toolkit */
	public Map<String, QonfigAddOn> getDeclaredAddOns() {
		return theDeclaredAddOns;
	}

	/** @return All add-on definitions declared or inherited by this toolkit */
	public BetterMultiMap<String, QonfigAddOn> getAllAddOns() {
		return theCompiledAddOns;
	}

	/** @return All attribute types declared by this toolkit */
	public Map<String, QonfigValueType.Declared> getDeclaredAttributeTypes() {
		return theDeclaredAttributeTypes;
	}

	/**
	 * @param name The (possibly qualified) name of the element to get
	 * @return The element with the given name, or null if no such element exists
	 * @throws IllegalArgumentException If this toolkit does not declare an element with the given name and multiple elements matching the
	 *         given name exist in this toolkit's dependencies
	 */
	public QonfigElementDef getElement(String name) throws IllegalArgumentException {
		int colon = name.indexOf(':');
		if (colon >= 0) {
			String ns = name.substring(0, colon);
			QonfigToolkit dep = theDependencies.get(ns);
			if (dep == null)
				throw new IllegalArgumentException("No such dependency: " + ns);
			return dep.getElement(name.substring(colon + 1));
		}
		QonfigElementDef found = theDeclaredElements.get(name);
		if (found != null)
			return found;
		if (theCompiledElements == null) {
			for (QonfigToolkit dep : theDependencies.values()) {
				found = dep.getElement(name);
				if (found != null)
					return found;
			}
			return null;
		}
		BetterCollection<QonfigElementDef> els = theCompiledElements.get(name);
		switch (els.size()) {
		case 0:
			return null;
		case 1:
			return els.getFirst();
		default:
			throw new IllegalArgumentException("Multiple elements matching '" + name + "'");
		}
	}

	/**
	 * @param name The (possibly qualified) name of the add-on to get
	 * @return The add-on with the given name, or null if no such add-on exists
	 * @throws IllegalArgumentException If this toolkit does not declare an add-on with the given name and multiple add-ons matching the
	 *         given name exist in this toolkit's dependencies
	 */
	public QonfigAddOn getAddOn(String name) {
		int colon = name.indexOf(':');
		if (colon >= 0) {
			String ns = name.substring(0, colon);
			QonfigToolkit dep = theDependencies.get(ns);
			if (dep == null)
				throw new IllegalArgumentException("No such dependency: " + ns);
			return dep.getAddOn(name.substring(colon + 1));
		}
		QonfigAddOn found = theDeclaredAddOns.get(name);
		if (found != null)
			return found;
		if (theCompiledAddOns == null) {
			for (QonfigToolkit dep : theDependencies.values()) {
				found = dep.getAddOn(name);
				if (found != null)
					return found;
			}
			return null;
		}
		BetterCollection<QonfigAddOn> els = theCompiledAddOns.get(name);
		switch (els.size()) {
		case 0:
			return null;
		case 1:
			return els.getFirst();
		default:
			throw new IllegalArgumentException("Multiple add-ons matching '" + name + "'");
		}
	}

	/**
	 * @param name The (possibly qualified) name of the element or add-on to get
	 * @return The element or add-on with the given name, or null if none such exists
	 * @throws IllegalArgumentException If this toolkit does not declare an element or add-on with the given name and multiple
	 *         elements/add-ons matching the given name exist in this toolkit's dependencies
	 */
	public QonfigElementOrAddOn getElementOrAddOn(String name) {
		int colon = name.indexOf(':');
		if (colon >= 0) {
			String ns = name.substring(0, colon);
			QonfigToolkit dep = theDependencies.get(ns);
			if (dep == null)
				throw new IllegalArgumentException("No such dependency: " + ns);
			return dep.getElement(name.substring(colon + 1));
		}
		QonfigElementOrAddOn found = theDeclaredElements.get(name);
		if (found != null)
			return found;
		found = theDeclaredAddOns.get(name);
		if (found != null)
			return found;
		if (theCompiledElements == null) {
			for (QonfigToolkit dep : theDependencies.values()) {
				found = dep.getElementOrAddOn(name);
				if (found != null)
					return found;
			}
			return null;
		}
		BetterCollection<? extends QonfigElementOrAddOn> els = theCompiledElements.get(name);
		switch (els.size()) {
		case 0:
			break;
		case 1:
			if (!theCompiledAddOns.get(name).isEmpty())
				throw new IllegalArgumentException("Multiple elements/add-ons matching '" + name + "'");
			return els.getFirst();
		default:
			throw new IllegalArgumentException("Multiple elements matching '" + name + "'");
		}
		els = theCompiledAddOns.get(name);
		switch (els.size()) {
		case 0:
			return null;
		case 1:
			return els.getFirst();
		default:
			throw new IllegalArgumentException("Multiple add-ons matching '" + name + "'");
		}
	}

	/**
	 * @param name The (possibly qualified) name of the attribute type to get
	 * @return The attribute type with the given name, or null if no such type exists
	 */
	public QonfigValueType.Declared getAttributeType(String name) {
		int colon = name.indexOf(':');
		if (colon >= 0) {
			String ns = name.substring(0, colon);
			QonfigToolkit dep = theDependencies.get(ns);
			if (dep == null)
				throw new IllegalArgumentException("No such dependency: " + ns);
			return dep.getAttributeType(name.substring(colon + 1));
		}
		if (theCompiledAttributeTypes != null)
			return theCompiledAttributeTypes.get(name);
		QonfigValueType.Declared found = theDeclaredAttributeTypes.get(name);
		if (found != null)
			return found;
		for (QonfigToolkit dep : theDependencies.values()) {
			found = dep.getAttributeType(name);
			if (found != null)
				return found;
		}
		return null;
	}

	/** @return All auto-inheritance conditions declared by this toolkit */
	public List<QonfigAutoInheritance> getDeclaredAutoInheritance() {
		return theDeclaredAutoInheritance;
	}

	/**
	 * @param target The element or add-on type to check for auto-inheritance
	 * @param roles The child roles that the element fulfills
	 * @return The additional add-ons that the element should inherit from automatic inheritance
	 */
	public MultiInheritanceSet<QonfigAddOn> getAutoInheritance(QonfigElementOrAddOn target, Set<QonfigChildDef> roles) {
		MultiInheritanceSet<QonfigAddOn> inheritance = null;
		for (MultiInheritanceSet<QonfigAddOn> autoInherit : theTypeAutoInheritance.getAll(target)) {
			if (inheritance == null)
				inheritance = MultiInheritanceSet.create(QonfigAddOn::isAssignableFrom);
			inheritance.addAll(autoInherit.values());
		}
		for (QonfigChildDef role : roles) {
			for (MultiInheritanceSet<QonfigAddOn> autoInherit : theRoleAutoInheritance.getAll(role)) {
				if (inheritance == null)
					inheritance = MultiInheritanceSet.create(QonfigAddOn::isAssignableFrom);
				inheritance.addAll(autoInherit.values());
			}
			for (MultiInheritanceMap<QonfigChildDef, MultiInheritanceSet<QonfigAddOn>> typeRoleInh : theTypeAndRoleAutoInheritance
				.getAll(target)) {
				for (MultiInheritanceSet<QonfigAddOn> autoInherit : typeRoleInh.getAll(role)) {
					if (inheritance == null)
						inheritance = MultiInheritanceSet.create(QonfigAddOn::isAssignableFrom);
					inheritance.addAll(autoInherit.values());
				}
			}
		}
		return inheritance == null ? MultiInheritanceSet.empty() : inheritance;
	}

	/** @return The available root elements declared for documents of this toolkit */
	public Set<QonfigElementDef> getDeclaredRoots() {
		return theDeclaredRoots;
	}

	/** @return The root elements usable for documents of this toolkit */
	public Set<QonfigElementDef> getRoots() {
		return theRoots;
	}

	private volatile QonfigElementOrAddOn theCachedElement;

	/**
	 * @param elementOrAddOnName The name of the element or add-on that defined the attribute, or an extension of it
	 * @param attributeName The name of the attribute to get
	 * @return The attribute definition
	 * @throws IllegalArgumentException If no such element/add-on is defined under this toolkit, no such attribute is defined on it, or
	 *         multiple matching attributes are defined on it
	 */
	public QonfigAttributeDef.Declared getAttribute(String elementOrAddOnName, String attributeName) throws IllegalArgumentException {
		QonfigElementOrAddOn el = null;
		boolean cache = elementOrAddOnName.indexOf(':') < 0;
		if (cache) {
			el = theCachedElement;
			if (el != null && !el.getName().equals(elementOrAddOnName))
				el = null;
		}
		if (el == null) {
			el = getElementOrAddOn(elementOrAddOnName);
			if (cache && el != null)
				theCachedElement = el;
		}
		if (el == null)
			throw new IllegalArgumentException("No such element or add-on '" + elementOrAddOnName + "'");
		QonfigAttributeDef attr = el.getAttribute(attributeName);
		if (attr == null)
			throw new IllegalArgumentException("No such attribute " + elementOrAddOnName + "." + attributeName);
		return attr.getDeclared();
	}

	/**
	 * @param elementOrAddOnName The name of the element or add-on that defined the attribute, or an extension of it
	 * @param roleName The role name of the child to get
	 * @return The child definition
	 * @throws IllegalArgumentException If no such element/add-on is defined under this toolkit, no such role is defined on it, or multiple
	 *         matching roles are defined on it
	 */
	public QonfigChildDef getChild(String elementOrAddOnName, String roleName) throws IllegalArgumentException {
		QonfigElementOrAddOn el = null;
		boolean cache = elementOrAddOnName.indexOf(':') < 0;
		if (cache) {
			el = theCachedElement;
			if (el != null && !el.getName().equals(elementOrAddOnName))
				el = null;
		}
		if (el == null) {
			el = getElementOrAddOn(elementOrAddOnName);
			if (cache && el != null)
				theCachedElement = el;
		}
		if (el == null)
			throw new IllegalArgumentException("No such element or add-on '" + elementOrAddOnName + "'");
		QonfigChildDef child = el.getChild(roleName);
		if (child == null)
			throw new IllegalArgumentException("No such child " + elementOrAddOnName + "." + roleName);
		return child;
	}

	@Override
	public String toString() {
		if (theLocation == null)
			return "PLACEHOLDER";
		return theLocation.toString();
	}
}
