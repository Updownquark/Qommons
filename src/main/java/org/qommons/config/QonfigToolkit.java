package org.qommons.config;

import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterCollections;
import org.qommons.collect.BetterHashMultiMap;
import org.qommons.collect.BetterMultiMap;

/** A structure containing a set of types that can be used to parsed highly-structured and validated {@link QonfigDocument}s */
public class QonfigToolkit {
	public interface ToolkitBuilder {
		void parseTypes(QonfigParseSession session);

		void fillOutTypes(QonfigParseSession session);

		QonfigElementDef getDeclaredRoot(QonfigParseSession session);
	}

	private final URL theLocation;
	private final Map<String, QonfigToolkit> theDependencies;
	private final Map<String, QonfigValueType.Declared> theDeclaredAttributeTypes;
	private final Map<String, QonfigValueType.Declared> theCompiledAttributeTypes;
	private final Map<String, QonfigElementDef> theDeclaredElements;
	private final BetterMultiMap<String, QonfigElementDef> theCompiledElements;
	private final Map<String, QonfigAddOn> theDeclaredAddOns;
	private final BetterMultiMap<String, QonfigAddOn> theCompiledAddOns;
	private final QonfigElementDef theDeclaredRoot;

	public QonfigToolkit(URL location, Map<String, QonfigToolkit> dependencies, Map<String, QonfigValueType.Declared> declaredTypes,
		Map<String, QonfigAddOn> declaredAddOns, Map<String, QonfigElementDef> declaredElements, ToolkitBuilder builder)
		throws QonfigParseException {
		theLocation = location;
		theDependencies = dependencies;
		theDeclaredAttributeTypes = declaredTypes;
		theDeclaredAddOns = declaredAddOns;
		theDeclaredElements = declaredElements;

		if (builder != null) {
			QonfigParseSession session = QonfigParseSession.forRoot("qonfig-def", this);
			builder.parseTypes(session);

			Map<String, QonfigValueType.Declared> compiledTypes = new HashMap<>(theDeclaredAttributeTypes);
			Set<String> conflictedTypeNames = new HashSet<>();
			BetterMultiMap<String, QonfigAddOn> compiledAddOns = BetterHashMultiMap.<String, QonfigAddOn> build().safe(false)
				.withDistinctValues().buildMultiMap();
			BetterMultiMap<String, QonfigElementDef> compiledElements = BetterHashMultiMap.<String, QonfigElementDef> build().safe(false)
				.withDistinctValues().buildMultiMap();
			for (QonfigAddOn el : theDeclaredAddOns.values())
				compiledAddOns.add(el.getName(), el);
			for (QonfigElementDef el : theDeclaredElements.values())
				compiledElements.add(el.getName(), el);
			QonfigElementDef root = null;
			boolean hasMultiRoot = false;
			for (QonfigToolkit dep : dependencies.values()) {
				if (dep.getRoot() != null) {
					if (root != null)
						hasMultiRoot |= !root.equals(dep.getRoot());
					root = dep.getRoot();
				}
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
			theDeclaredRoot = builder.getDeclaredRoot(session);
			if (theDeclaredRoot == null && hasMultiRoot)
				session.withWarning("Toolkit does not declare a root, but inherits multiple different roots--will not inherit any");
			session.throwErrors().printWarnings(System.err);
		} else { // PLACEHOLDER
			theCompiledElements = null;
			theCompiledAddOns = null;
			theCompiledAttributeTypes = null;
			theDeclaredRoot = null;
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

	/** @return The root element declared for documents of this toolkit */
	public QonfigElementDef getDeclaredRoot() {
		return theDeclaredRoot;
	}

	/** @return The root element to use for documents of this toolkit */
	public QonfigElementDef getRoot() {
		if (theDeclaredRoot != null)
			return theDeclaredRoot;
		else if (theDependencies.isEmpty())
			return null;
		QonfigElementDef root = null;
		for (QonfigToolkit dep : theDependencies.values()) {
			QonfigElementDef depRoot = dep.getRoot();
			if (depRoot != null) {
				if (root == null)
					root = depRoot;
				else if (!root.equals(depRoot))
					return null; // As warned in the constructor, we won't inherit a root if multiple distinct ones would be inherited
			}
		}
		return root;
	}

	@Override
	public String toString() {
		return theLocation.toString();
	}
}
