package org.qommons.config;

import java.net.URL;
import java.text.ParseException;
import java.util.*;

import org.qommons.MultiInheritanceMap;
import org.qommons.MultiInheritanceSet;
import org.qommons.Named;
import org.qommons.QommonsUtils;
import org.qommons.SelfDescribed;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterCollections;
import org.qommons.collect.BetterHashMultiMap;
import org.qommons.collect.BetterMultiMap;
import org.qommons.config.QonfigAutoInheritance.AutoInheritTarget;
import org.qommons.io.PositionedContent;

/** A structure containing a set of types that can be used to parsed highly-structured and validated {@link QonfigDocument}s */
public class QonfigToolkit implements Named, SelfDescribed {
	/**
	 * A Qonfig toolkit is defined by its name and Major/Minor version. The Patch version of Semantic Versioning is reserved for the
	 * implementation/interpretation. This class represents the version piece of the toolkit definition
	 */
	public static class ToolkitDefVersion implements Comparable<ToolkitDefVersion> {
		/** The major version of this toolkit. Toolkits with the same major version should be backward-compatible. */
		public final int majorVersion;
		/**
		 * The minor version of this toolkit. Any change to a toolkit that is backward-compatible should cause an increase in the minor
		 * version
		 */
		public final int minorVersion;

		/**
		 * @param majorVersion The major version of this toolkit
		 * @param minorVersion THe minor version of this toolkit
		 */
		public ToolkitDefVersion(int majorVersion, int minorVersion) {
			this.majorVersion = majorVersion;
			this.minorVersion = minorVersion;
		}

		@Override
		public int compareTo(ToolkitDefVersion o) {
			int comp = Integer.compare(majorVersion, o.majorVersion);
			if (comp == 0)
				comp = Integer.compare(minorVersion, o.minorVersion);
			return comp;
		}

		@Override
		public int hashCode() {
			return Integer.reverse(majorVersion) ^ minorVersion;
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof ToolkitDefVersion && majorVersion == ((ToolkitDefVersion) obj).majorVersion
				&& minorVersion == ((ToolkitDefVersion) obj).minorVersion;
		}

		@Override
		public String toString() {
			return "v" + majorVersion + "." + minorVersion;
		}
	}

	/** Fills out a QonfigToolkit. This interface allows {@link QonfigToolkit}s to be immutable after they are built */
	public interface ToolkitBuilder {
		/**
		 * Parses all the types for the toolkit
		 * 
		 * @param session The parsing session for error reporting, etc.
		 * @throws QonfigParseException If an unrecoverable error occurs parsing the types
		 */
		void parseTypes(QonfigParseSession session) throws QonfigParseException;

		/**
		 * Links and fills out all the types for the toolkit
		 * 
		 * @param session The parsing session for error reporting, etc.
		 * @throws QonfigParseException If an unrecoverable error occurs fleshing outthe types
		 */
		void fillOutTypes(QonfigParseSession session) throws QonfigParseException;
	}

	/** A structure that contains the definition of a toolkit */
	public static class ToolkitDef extends ToolkitDefVersion {
		/** The name of the toolkit */
		public final String name;
	
		/**
		 * @param name The name of the toolkit
		 * @param majorVersion The major version of the toolkit
		 * @param minorVersion The minor version of the toolkit
		 */
		public ToolkitDef(String name, int majorVersion, int minorVersion) {
			super(majorVersion, minorVersion);
			this.name = name;
		}
	
		/** @param toolkit The toolkit to create a definition of */
		public ToolkitDef(QonfigToolkit toolkit) {
			this(toolkit.getName(), toolkit.getMajorVersion(), toolkit.getMinorVersion());
		}
	
		@Override
		public int hashCode() {
			return Objects.hash(name, majorVersion);
		}
	
		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else if (!(obj instanceof ToolkitDef))
				return false;
			ToolkitDef other = (ToolkitDef) obj;
			return name.equals(other.name) && majorVersion == other.majorVersion;
		}
	
		@Override
		public String toString() {
			return name + " " + majorVersion + "." + minorVersion;
		}

		/**
		 * Parses a toolkit definition from text
		 * 
		 * @param text The text to parse
		 * @return The parsed toolkit definition
		 * @throws ParseException If a definition could not be parsed from the text
		 */
		public static ToolkitDef parse(String text) throws ParseException {
			int space = text.indexOf(' ');
			if (space < 0)
				throw new ParseException("Expected a space between toolkit name and version", text.length());
			if (space == text.length() - 1)
				throw new ParseException("Expected toolkit version after space", text.length());

			int dot = text.lastIndexOf('.');
			if (dot < 0 || dot < space)
				throw new ParseException("Expected a dot between major and minor version", text.length());
			int majorVersion;
			if (text.charAt(space + 1) == 'v' || text.charAt(space + 1) == 'V') {
				if (space + 2 == text.length())
					throw new ParseException("Expected toolkit version after '" + text.charAt(space + 1) + "'", space + 2);
				try {
					majorVersion = Integer.parseInt(text.substring(space + 2, dot));
				} catch (NumberFormatException e) {
					throw new ParseException("Could not parse major version", space + 2);
				}
			} else {
				try {
					majorVersion = Integer.parseInt(text.substring(space + 1, dot));
				} catch (NumberFormatException e) {
					throw new ParseException("Could not parse major version", space + 1);
				}
			}

			if (dot + 1 == text.length())
				throw new ParseException("Expected minor version after '.'", dot + 1);
			int minorVersion;
			try {
				minorVersion = Integer.parseInt(text.substring(dot + 1));
			} catch (NumberFormatException e) {
				throw new ParseException("Could not parse minor version", dot + 1);
			}
			return new ToolkitDef(text.substring(0, space), majorVersion, minorVersion);
		}
	}

	private final String theName;
	private final int theMajorVersion;
	private final int theMinorVersion;
	private final URL theLocation;
	private final String theLocationString;
	private final String theDescription;
	private final Map<String, QonfigToolkit> theDependencies;
	private final Map<String, NavigableMap<ToolkitDefVersion, QonfigToolkit>> theDependenciesByDefinition;
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

	/**
	 * @param name The name of the toolkit
	 * @param majorVersion The major version of the toolkit
	 * @param minorVersion The minor version of the toolkit
	 * @param location The location of the file where the toolkit was declared
	 * @param position The file position of the root element of the toolkit
	 * @param description A description of the toolkit's purpose and potential use
	 * @param dependencies The named dependencies of this toolkit
	 * @param declaredTypes The set of value types declared by the toolkit--typically empty, to be populated by the <code>builder</code>
	 * @param declaredAddOns The set of add-ons declared by the toolkit--typically empty, to be populated by the <code>builder</code>
	 * @param declaredElements The set of elements declared by the toolkit--typically empty, to be populated by the <code>builder</code>
	 * @param autoInheritance The automatic inheritance rules of the toolkit--typically empty, to be populated by the <code>builder</code>
	 * @param builder The toolkit builder to populate all the type information for the toolkit
	 * @throws QonfigParseException If an error occurs populating the toolkit
	 */
	public QonfigToolkit(String name, int majorVersion, int minorVersion, URL location, PositionedContent position, String description,
		Map<String, QonfigToolkit> dependencies, Map<String, QonfigValueType.Declared> declaredTypes,
		Map<String, QonfigAddOn> declaredAddOns, Map<String, QonfigElementDef> declaredElements,
		List<QonfigAutoInheritance> autoInheritance, ToolkitBuilder builder)
		throws QonfigParseException {
		theName = name;
		theMajorVersion = majorVersion;
		theMinorVersion = minorVersion;
		theLocation = location;
		theDescription = description;
		theDependencies = dependencies;
		theDeclaredAttributeTypes = declaredTypes;
		theDeclaredAddOns = declaredAddOns;
		theDeclaredElements = declaredElements;
		theDeclaredAutoInheritance = autoInheritance;
		theLocationString = theLocation != null ? theLocation.toString() : name;

		if (builder != null) {
			QonfigParseSession session = QonfigParseSession.forRoot(false, this, position);
			builder.parseTypes(session);

			Map<String, QonfigValueType.Declared> compiledTypes = new HashMap<>(theDeclaredAttributeTypes);
			Set<String> conflictedTypeNames = new HashSet<>();
			BetterMultiMap<String, QonfigAddOn> compiledAddOns = BetterHashMultiMap.<String, QonfigAddOn> build().withDistinctValues()
				.buildMultiMap();
			BetterMultiMap<String, QonfigElementDef> compiledElements = BetterHashMultiMap.<String, QonfigElementDef> build()
				.withDistinctValues().buildMultiMap();
			Map<String, NavigableMap<ToolkitDefVersion, QonfigToolkit>> dependenciesByDef = new LinkedHashMap<>();
			for (QonfigAddOn el : theDeclaredAddOns.values())
				compiledAddOns.add(el.getName(), el);
			for (QonfigElementDef el : theDeclaredElements.values())
				compiledElements.add(el.getName(), el);
			for (QonfigToolkit dep : dependencies.values()) {
				dependenciesByDef.computeIfAbsent(dep.getName(), __ -> new TreeMap<>())
					.putIfAbsent(new ToolkitDefVersion(dep.getMajorVersion(), dep.getMinorVersion()), dep);
				for (Map.Entry<String, NavigableMap<ToolkitDefVersion, QonfigToolkit>> dd : dep.theDependenciesByDefinition.entrySet()) {
					NavigableMap<ToolkitDefVersion, QonfigToolkit> dbdv = dependenciesByDef.computeIfAbsent(dd.getKey(),
						__ -> new TreeMap<>());
					for (Map.Entry<ToolkitDefVersion, QonfigToolkit> v : dd.getValue().entrySet())
						dbdv.putIfAbsent(v.getKey(), v.getValue());
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
						if (found != type.getValue()) {
							conflictedTypeNames.add(type.getKey());
							compiledTypes.remove(type.getKey());
						}
					}
				}
			}
			for (Map.Entry<String, NavigableMap<ToolkitDefVersion, QonfigToolkit>> dbd : dependenciesByDef.entrySet())
				dbd.setValue(Collections.unmodifiableNavigableMap(dbd.getValue()));
			theDependenciesByDefinition = Collections.unmodifiableMap(dependenciesByDef);
			theCompiledElements = BetterCollections.unmodifiableMultiMap(compiledElements);
			theCompiledAddOns = BetterCollections.unmodifiableMultiMap(compiledAddOns);
			theCompiledAttributeTypes = Collections.unmodifiableMap(compiledTypes);

			builder.fillOutTypes(session);

			session.throwErrors(location == null ? "Document" : location.toString());

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
			theDependenciesByDefinition = null;
			theCompiledElements = null;
			theCompiledAddOns = null;
			theCompiledAttributeTypes = null;
			theTypeAutoInheritance = null;
			theRoleAutoInheritance = null;
			theTypeAndRoleAutoInheritance = null;
		}
	}

	@Override
	public String getName() {
		return theName;
	}

	/** @return The major version of this toolkit */
	public int getMajorVersion() {
		return theMajorVersion;
	}

	/** @return The minor version of this toolkit */
	public int getMinorVersion() {
		return theMinorVersion;
	}

	/** @return The resource location defining the toolkit */
	public URL getLocation() {
		return theLocation;
	}

	/** @return A string representation of this toolkit's location */
	public String getLocationString() {
		return theLocationString;
	}

	@Override
	public String getDescription() {
		return theDescription;
	}

	/** @return All toolkits directly extended by this toolkit, by the name assigned to the dependency in this toolkit definition */
	public Map<String, QonfigToolkit> getDependencies() {
		return theDependencies;
	}

	/** @return All toolkits directly or indirectly extended by this toolkit, by each toolkit's own name and version */
	public Map<String, NavigableMap<ToolkitDefVersion, QonfigToolkit>> getDependenciesByDefinition() {
		return theDependenciesByDefinition;
	}

	/**
	 * @param nsOrDef A reference to the toolkit, either a namespace or a name/version identifier
	 * @return The toolkit dependency in this toolkit matching the reference
	 * @throws ParseException If no such toolkit is a dependency of this toolkit
	 */
	public QonfigToolkit getDependency(String nsOrDef) throws ParseException {
		if (nsOrDef.indexOf(' ') < 0)
			return theDependencies.get(nsOrDef);
		ToolkitDef def = ToolkitDef.parse(nsOrDef);
		if (def.name.equals(theName) && def.majorVersion == theMajorVersion && def.minorVersion == theMinorVersion)
			return this;
		return theDependenciesByDefinition.getOrDefault(def.name, Collections.emptyNavigableMap())//
			.get(def);
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
			QonfigToolkit dep;
			try {
				dep = getDependency(ns);
			} catch (ParseException e) {
				throw new IllegalArgumentException(e.getMessage(), e);
			}
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
			throw new IllegalArgumentException("Multiple elements matching '" + name + "' from toolkits "//
				+ QommonsUtils.map(els, el -> el.getDeclarer(), false));
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
			QonfigToolkit dep;
			try {
				dep = getDependency(ns);
			} catch (ParseException e) {
				throw new IllegalArgumentException(e.getMessage(), e);
			}
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
			QonfigToolkit dep;
			try {
				dep = getDependency(ns);
			} catch (ParseException e) {
				throw new IllegalArgumentException(e.getMessage(), e);
			}
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
			QonfigToolkit dep;
			try {
				dep = getDependency(ns);
			} catch (ParseException e) {
				throw new IllegalArgumentException(e.getMessage(), e);
			}
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
	public MultiInheritanceSet<QonfigAddOn> getAutoInheritance(QonfigElementOrAddOn target, MultiInheritanceSet<QonfigChildDef> roles) {
		MultiInheritanceSet<QonfigAddOn> inheritance = null;
		if (theTypeAutoInheritance == null) {
			// Normal, happens during building. Means we have to do this the slow way.
			inheritance = MultiInheritanceSet.create(QonfigAddOn::isAssignableFrom);
			for (QonfigAutoInheritance inh : theDeclaredAutoInheritance) {
				for (AutoInheritTarget aiTarget : inh.getTargets()) {
					if (aiTarget.applies(target, roles)) {
						inheritance.addAll(inh.getInheritance().values());
						break;
					}
				}
			}
			for (QonfigToolkit dep : getDependencies().values()) {
				inheritance.addAll(dep.getAutoInheritance(target, roles).values());
			}
			return MultiInheritanceSet.unmodifiable(inheritance);
		} else {
			for (MultiInheritanceSet<QonfigAddOn> autoInherit : theTypeAutoInheritance.getAll(target)) {
				if (inheritance == null)
					inheritance = MultiInheritanceSet.create(QonfigAddOn::isAssignableFrom);
				inheritance.addAll(autoInherit.values());
			}
			for (QonfigChildDef role : roles.values()) {
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
		}
		return inheritance == null ? MultiInheritanceSet.empty() : inheritance;
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
	 * @param elementOrAddOnName The name of the element or add-on that defined the role, or an extension of it
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

	/**
	 * @param elementOrAddOnName The name of the element or add-on that defined the metadata role, or an extension of it
	 * @param roleName The role name of the metadata child to get
	 * @return The metadata child definition
	 * @throws IllegalArgumentException If no such element/add-on is defined under this toolkit, no such metadata role is defined on it, or
	 *         multiple matching roles are defined on it
	 */
	public QonfigChildDef getMetaChild(String elementOrAddOnName, String roleName) throws IllegalArgumentException {
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
		QonfigChildDef child = el.getMetaSpec().getChild(roleName);
		if (child == null)
			throw new IllegalArgumentException("No such meta child " + elementOrAddOnName + "." + roleName);
		return child;
	}

	@Override
	public int hashCode() {
		return Objects.hash(theName, theMajorVersion, theMinorVersion);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		else if (!(obj instanceof QonfigToolkit))
			return false;
		QonfigToolkit other = (QonfigToolkit) obj;
		return Objects.equals(theName, other.theName) && theMajorVersion == other.theMajorVersion
			&& theMinorVersion == other.theMinorVersion;
	}

	@Override
	public String toString() {
		return theName + " " + theMajorVersion + "." + theMinorVersion;
	}
}
