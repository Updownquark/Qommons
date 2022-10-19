package org.qommons.config;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterHashSet;
import org.qommons.collect.BetterSet;
import org.w3c.dom.Element;

/** Default {@link QonfigParser} implementation */
public class DefaultQonfigParser implements QonfigParser {
	/** The name of the attribute to use todirectly specify inheritance in Qonfig documents */
	public static final String DOC_ELEMENT_INHERITANCE_ATTR = "with-extension";
	/** Names that cannot be used for Qonfig attributes */
	public static final Set<String> RESERVED_ATTR_NAMES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(//
		DOC_ELEMENT_INHERITANCE_ATTR, "role"//
	)));

	static class ToolkitDef {
		public final String name;
		public final int majorVersion;
		public final int minorVersion;

		ToolkitDef(String name, int majorVersion, int minorVersion) {
			this.name = name;
			this.majorVersion = majorVersion;
			this.minorVersion = minorVersion;
		}

		ToolkitDef(QonfigToolkit toolkit) {
			name = toolkit.getName();
			majorVersion = toolkit.getMajorVersion();
			minorVersion = toolkit.getMinorVersion();
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
	}

	private final Map<ToolkitDef, QonfigToolkit> theToolkits;

	/** Creates the parser */
	public DefaultQonfigParser() {
		theToolkits = new HashMap<>();
	}

	/**
	 * @param toolkitName The toolkit name to check
	 * @param majorVersion The major version of the toolkit
	 * @return Whether this parser has been configured {@link #withToolkit(QonfigToolkit...) with} the given toolkit
	 */
	public boolean usesToolkit(String toolkitName, int majorVersion) {
		return theToolkits.containsKey(new ToolkitDef(toolkitName, majorVersion, 0));
	}

	@Override
	public DefaultQonfigParser withToolkit(QonfigToolkit... toolkits) {
		for (QonfigToolkit toolkit : toolkits) {
			if (toolkit == null)
				continue;
			ToolkitDef def = new ToolkitDef(toolkit);
			QonfigToolkit found = theToolkits.putIfAbsent(def, toolkit);
			if (found != null) {
				int vComp = Integer.compare(def.minorVersion, found.getMinorVersion());
				if (vComp <= 0)
					continue;
				theToolkits.put(def, toolkit);
			}
			for (QonfigToolkit dep : toolkit.getDependencies().values())
				withToolkit(dep);
		}
		return this;
	}

	@Override
	public QonfigToolkit parseToolkit(URL location, InputStream content, CustomValueType... customValueTypes)
		throws IOException, QonfigParseException {
		return _parseToolkitXml(location, content, new LinkedList<>(), customValueTypes);
	}

	@Override
	public QonfigDocument parseDocument(String location, InputStream content) throws IOException, QonfigParseException {
		Element root = QommonsConfig.getRootElement(content);
		content.close();
		QonfigParseSession session;
		QonfigDocument doc;
		try (StrictXmlReader rootReader = new StrictXmlReader(root)) {
			LinkedList<String> path = new LinkedList<>();
			Map<String, QonfigToolkit> uses = new LinkedHashMap<>();
			for (Map.Entry<String, String> use : rootReader.findAttributes(USES, 0, -1).entrySet()) {
				String refName = use.getKey().substring(5);
				if (refName.isEmpty())
					throw new IllegalArgumentException("Empty toolkit name");
				checkName(refName);
				path.add(refName);
				Matcher match = TOOLKIT_REF.matcher(use.getValue());
				if (!match.matches())
					throw new IllegalArgumentException(
						location + ": Bad toolkit reference.  Expected 'name vM.m' but found " + use.getValue());
				ToolkitDef def = new ToolkitDef(match.group("name"), Integer.parseInt(match.group("major")),
					Integer.parseInt(match.group("minor")));

				QonfigToolkit dep = theToolkits.get(def);
				if (dep == null)
					throw new IllegalArgumentException("No such dependency " + def + " registered");
				uses.put(refName, dep);
			}
			if (uses.isEmpty())
				throw new IllegalArgumentException("No toolkit uses declared");
			QonfigElementDef rootDef;
			if (rootReader.getPrefix() != null) {
				QonfigToolkit primary = uses.get(rootReader.getPrefix());
				if (primary == null)
					throw new IllegalArgumentException("Unrecognized namespace for root: " + rootReader.getPrefix());
				rootDef = primary.getElement(rootReader.getTagName());
				if (rootDef == null)
					throw new IllegalArgumentException("No such element '" + rootReader.getTagName() + "' found in toolkit "
						+ rootReader.getPrefix() + " (" + primary + ")");
				else if (!primary.getRoots().contains(rootDef))
					throw new IllegalArgumentException(
						rootReader.getName() + " is not a root of toolkit " + rootReader.getPrefix() + " (" + primary + ")");
			} else {
				QonfigElementDef rootDef2 = null;
				for (QonfigToolkit tk : uses.values()) {
					QonfigElementDef found = tk.getElement(rootReader.getName());
					if (found != null) {
						if (rootDef2 == null)
							rootDef2 = found;
						else if (found != rootDef2)
							throw new IllegalArgumentException("Multiple element-defs found matching " + rootReader.getName());
					}
				}
				if (rootDef2 == null)
					throw new IllegalArgumentException("No such element-def: '" + rootReader.getName() + "'");
				boolean isRoot = false;
				for (QonfigToolkit tk : uses.values()) {
					isRoot = tk.getRoots().contains(rootDef2);
					if (isRoot)
						break;
				}
				if (!isRoot)
					throw new IllegalArgumentException("Element '" + rootReader.getName() + "' is not declared as the root of any toolkit");
				rootDef = rootDef2;
			}
			QonfigToolkit docToolkit = new QonfigToolkit(location, 1, 0, null, Collections.unmodifiableMap(uses), Collections.emptyMap(),
				Collections.emptyMap(), Collections.emptyMap(), Collections.emptyList(), new QonfigToolkit.ToolkitBuilder() {
					@Override
					public void parseTypes(QonfigParseSession s) {
					}

					@Override
					public void fillOutTypes(QonfigParseSession s) {
					}

					@Override
					public Set<QonfigElementDef> getDeclaredRoots(QonfigParseSession s) {
						return Collections.singleton(rootDef);
					}
				});
			session = QonfigParseSession.forRoot(rootReader.getName(), docToolkit);
			doc = new QonfigDocument(location, docToolkit);
			parseDocElement(session, QonfigElement.build(session, doc, null, rootDef), rootReader, true, el -> true);
		}
		session.printWarnings(System.err, location);
		session.throwErrors(location);
		return doc;
	}

	private static final Pattern USES = Pattern.compile("uses\\:.*");
	private static final Pattern TOOLKIT_REF = Pattern.compile("(?<name>[^\\s]+)\\s*v?(?<major>\\d+)\\.(?<minor>\\d+)");

	QonfigElement parseDocElement(QonfigParseSession session, QonfigElement.Builder builder, StrictXmlReader el, boolean withAttrs,
		Predicate<StrictXmlReader> childApplies) {
		if (el.getParent() != null) {
			for (Map.Entry<String, String> uses : el.findAttributes(USES, 0, -1).entrySet())
				session.withError("uses attributes may only be used at the root: '" + uses.getKey() + "'", null);
		}
		if (withAttrs) {
			for (Map.Entry<String, String> attr : el.getAllAttributes().entrySet()) {
				if (USES.matcher(attr.getKey()).matches()) {//
				} else if (DOC_ELEMENT_INHERITANCE_ATTR.equals(attr.getKey())) {
					for (String inhName : attr.getValue().split(",")) {
						inhName = inhName.trim();
						QonfigAddOn addOn;
						try {
							addOn = session.getToolkit().getAddOn(inhName);
							if (addOn == null) {
								session.forChild("attribute", DOC_ELEMENT_INHERITANCE_ATTR).withError("No such add-on " + inhName);
								continue;
							}
						} catch (IllegalArgumentException e) {
							session.forChild("attribute", DOC_ELEMENT_INHERITANCE_ATTR).withError(e.getMessage());
							continue;
						}
						builder.inherits(addOn);
					}
				} else if ("role".equals(attr.getKey())) {//
				} else {
					ElementQualifiedParseItem qAttr = ElementQualifiedParseItem.parse(attr.getKey(), session);
					builder.withAttribute(qAttr, new AttParser(attr.getValue()));
				}
			}

			String text = el.getTextTrim(false);
			if (text != null) {
				if (builder.getType().getValue() != null) {
					try {
						Object value = builder.getType().getValue().getType().parse(text, session.getToolkit(), session);
						builder.withValue(value);
					} catch (RuntimeException e) {
						session.forChild("value", null).withError("Could not parse element text: " + text, e);
					}
				} else if (!text.isEmpty())
					session.forChild("value", null).withError("No value expected or accepted: " + text);
			}
		}
		builder.doneWithAttributes();

		for (StrictXmlReader child : el.getElements(0, -1)) {
			if (!childApplies.test(child))
				continue;
			String roleAttr = child.getAttribute("role", false);
			QonfigParseSession childSession = session.forChild(child.getName(), roleAttr);
			QonfigElementDef childType;
			try {
				childType = session.getToolkit().getElement(child.getName());
			} catch (IllegalArgumentException e) {
				childSession.withError(e.getMessage());
				continue;
			}
			if (childType == null) {
				childSession.withError("No such element-def found");
				continue;
			}
			List<ElementQualifiedParseItem> roles;
			if (roleAttr == null)
				roles = Collections.emptyList();
			else {
				String[] split = roleAttr.split(",");
				roles = new ArrayList<>(split.length);
				for (String s : split) {
					s = s.trim();
					try {
						roles.add(ElementQualifiedParseItem.parse(s, session));
					} catch (IllegalArgumentException e) {
						session.forChild(child.getName(), roleAttr).withError(e.getMessage());
					}
				}
			}
			builder.withChild(roles, childType, cb -> {
				parseDocElement(childSession, cb, child, true, __ -> true);
			});
		}
		return builder.build();
	}

	private static class AttParser implements QonfigElement.AttributeValue {
		private final String theValue;

		AttParser(String value) {
			theValue = value;
		}

		@Override
		public Object parseAttributeValue(QonfigParseSession session, QonfigAttributeDef attribute) {
			return attribute.getType().parse(theValue, session.getToolkit(), session);
		}

		@Override
		public String toString() {
			return theValue;
		}
	}

	private QonfigToolkit _parseToolkitXml(URL location, InputStream xml, LinkedList<String> path, CustomValueType... customValueTypes)
		throws IOException, QonfigParseException {
		Element root;
		try {
			root = QommonsConfig.getRootElement(xml);
		} catch (IOException e) {
			throw new IOException("For toolkit " + location, e);
		}
		xml.close();
		ToolkitDef def;
		QonfigToolkit toolkit;
		try (StrictXmlReader rootReader = new StrictXmlReader(root)) {
			String name = rootReader.getAttribute("name", true);
			if (!TOOLKIT_NAME.matcher(name).matches())
				throw new IllegalArgumentException("Invalid toolkit name: " + name);
			Matcher version = TOOLKIT_VERSION.matcher(rootReader.getAttribute("version", true));
			if (!version.matches())
				throw new IllegalArgumentException("Illegal toolkit version.  Expected 'M.m' but found " + version.group());
			int major = Integer.parseInt(version.group("major"));
			int minor = Integer.parseInt(version.group("minor"));
			def = new ToolkitDef(name, major, minor);
			if (theToolkits.containsKey(def))
				throw new IllegalArgumentException("A toolkit named " + name + " is already registered");
			if (!rootReader.getTagName().equals("qonfig-def"))
				throw new IllegalArgumentException("Expected 'qonfig-def' for root element, not " + root.getNodeName());
			Map<String, QonfigToolkit> dependencies = new LinkedHashMap<>();
			for (Map.Entry<String, String> ext : rootReader.findAttributes(EXTENDS, 0, -1).entrySet()) {
				String refName = ext.getKey().substring("extends:".length());
				if (refName.isEmpty())
					throw new IllegalArgumentException(path + ": Empty dependency name");
				checkName(refName);
				path.add(refName);
				Matcher match = TOOLKIT_REF.matcher(ext.getValue());
				if (!match.matches())
					throw new IllegalArgumentException(def + ": Bad toolkit reference.  Expected 'name vM.m' but found " + ext.getValue());
				ToolkitDef depDef = new ToolkitDef(match.group("name"), Integer.parseInt(match.group("major")),
					Integer.parseInt(match.group("minor")));

				QonfigToolkit dep = theToolkits.get(depDef);
				if (dep == null)
					throw new IllegalArgumentException("No such dependency named " + depDef + " registered");
				dependencies.put(refName, dep);
			}
			ToolkitParser parser = new ToolkitParser(rootReader, customValueTypes);
			toolkit = new QonfigToolkit(name, major, minor, location, Collections.unmodifiableMap(dependencies),
				Collections.unmodifiableMap(parser.getDeclaredTypes()), Collections.unmodifiableMap(parser.getDeclaredAddOns()),
				Collections.unmodifiableMap(parser.getDeclaredElements()),
				Collections.unmodifiableList(parser.getDeclaredAutoInheritance()), parser);
		}
		// TODO Verify
		theToolkits.put(def, toolkit);
		return toolkit;
	}

	private static final Pattern EXTENDS = Pattern.compile("extends\\:.*");
	private static final Pattern TOOLKIT_NAME = Pattern.compile("[^\\s]+");
	private static final Pattern TOOLKIT_VERSION = Pattern.compile("(?<major>\\d+)\\.(?<minor>\\d+)");

	static final Set<String> TOOLKIT_EL_NAMES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(//
		"attribute", "child-def", "attr-mod", "child-mod", "value", "value-mod", "element-meta")));

	private class ToolkitParser implements QonfigToolkit.ToolkitBuilder {
		private final StrictXmlReader root;
		private final Map<String, QonfigValueType.Declared> declaredTypes;
		private final Map<String, QonfigElementOrAddOn.Builder> theBuilders;
		private final Set<QonfigElementDef> declaredRoots;

		private final Map<String, QonfigAddOn> declaredAddOns;
		private final Map<String, QonfigElementDef> declaredElements;
		private final List<QonfigAutoInheritance> declaredAutoInheritance;

		private String[] rootNames;
		private final CustomValueType[] theCustomValueTypes;
		private final Map<String, StrictXmlReader> theNodes;

		ToolkitParser(StrictXmlReader root, CustomValueType[] customValueTypes) {
			this.root = root;
			declaredTypes = new LinkedHashMap<>();
			theBuilders = new LinkedHashMap<>();
			declaredAddOns = new LinkedHashMap<>();
			declaredElements = new LinkedHashMap<>();
			declaredAutoInheritance = new ArrayList<>();
			declaredRoots = new LinkedHashSet<>();
			theNodes = new HashMap<>();

			theCustomValueTypes = customValueTypes;
		}

		Map<String, QonfigValueType.Declared> getDeclaredTypes() {
			return declaredTypes;
		}

		public Map<String, QonfigAddOn> getDeclaredAddOns() {
			return declaredAddOns;
		}

		Map<String, QonfigElementDef> getDeclaredElements() {
			return declaredElements;
		}

		List<QonfigAutoInheritance> getDeclaredAutoInheritance() {
			return declaredAutoInheritance;
		}

		@Override
		public void parseTypes(QonfigParseSession session) {
			// First pass to parse basic definitions
			String rootNamesStr = root.getAttribute("root", false);
			rootNames = rootNamesStr == null ? new String[0] : rootNamesStr.split(",");
			StrictXmlReader patterns = root.getElement("value-types", false);
			if (patterns != null) {
				QonfigParseSession patternsSession = session.forChild(patterns.getName(), 0);
				for (StrictXmlReader pattern : patterns.getElements(0, -1)) {
					String name = pattern.getAttribute("name", false);
					if (name == null) {
						patternsSession.withError("Value type declared with no name");
						continue;
					}
					QonfigParseSession patternSession = patternsSession.forChild(name, 0);
					if (declaredTypes.containsKey(name)) {
						patternSession.withError("A value type named " + name + " has already been declared");
						continue;
					}
					if (!checkName(name)) {
						patternSession.withError("Illegal value type name: " + name);
						continue;
					}
					QonfigValueType type = parsePattern(pattern, patternSession, true);
					if (type instanceof QonfigValueType.Declared)
						declaredTypes.put(name, (QonfigValueType.Declared) type);
					else
						patternSession.withError("Illegal value type");
				}
				try {
					patterns.check();
				} catch (IllegalArgumentException e) {
					patternsSession.withWarning(e.getMessage());
				}
			}
			for (CustomValueType cvt : theCustomValueTypes) {
				if (!declaredTypes.containsKey(cvt.getName()))
					session.withWarning("Custom value type '" + cvt.getName() + "' provided, but not expected by toolkit");
			}
			// Element/add-on parsing is in several stages:
			// Stage 1: Create a builder for each listed element with simple properties, and remove unparseable elements
			// Stage 2: Hook up extensions, checking for recursion
			// Stage 3: Create new attributes and text
			// Stage 4: Override attributes and text
			// Stage 5: Build children
			// Stages 1 and 2 are done here. Stages 3-6 will be done in the fillOutTypes() method
			QonfigParseSession addOnsSession = session.forChild("add-ons", 1);
			QonfigParseSession elsSession = session.forChild("elements", 1);
			// Stage 1: Create a builder for each listed add-on and element with simple properties and text,
			// and remove unparseable elements
			StrictXmlReader addOnsEl = root.getElement("add-ons", false);
			if (addOnsEl != null) {
				createElementsOrAddOns(addOnsEl.getElements("add-on", 0, -1), addOnsSession);
				try {
					addOnsEl.check();
				} catch (IllegalArgumentException e) {
					session.withWarning(e.getMessage());
				}
			}
			StrictXmlReader elementsEl = root.getElement("elements", false);
			if (elementsEl != null) {
				createElementsOrAddOns(elementsEl.getElements("element-def", 0, -1), elsSession);
				try {
					elementsEl.check();
				} catch (IllegalArgumentException e) {
					session.withWarning(e.getMessage());
				}
			}
			// Stage 2: Hook up extensions, checking for recursion
			LinkedList<String> path = new LinkedList<>();
			Set<String> completed = new HashSet<>();
			for (StrictXmlReader element : theNodes.values()) {
				path.clear();
				parseExtensions(element, path, elsSession, addOnsSession, completed);
			}
			for (QonfigElementOrAddOn.Builder el : theBuilders.values()) {
				QonfigElementOrAddOn built = el.get();
				if (built instanceof QonfigAddOn)
					declaredAddOns.put(built.getName(), (QonfigAddOn) built);
				else
					declaredElements.put(built.getName(), (QonfigElementDef) built);
			}
			root.getElement("auto-inheritance", false);
			try {
				root.check();
			} catch (IllegalArgumentException e) {
				session.withWarning(e.getMessage());
			}
		}

		private void createElementsOrAddOns(List<StrictXmlReader> elements, QonfigParseSession session) {
			Iterator<StrictXmlReader> elIter = elements.iterator();
			while (elIter.hasNext()) {
				StrictXmlReader element = elIter.next();
				String name = element.getAttribute("name", false);
				QonfigParseSession elSession = session.forChild(element.getName(), name);
				if (name == null) {
					elSession.withError("No name attribute for " + element.getName());
					elIter.remove();
					continue;
				}
				if (!checkName(name))
					elSession.withError("Illegal " + element.getName() + " name: " + name);
				QonfigElementOrAddOn.Builder old = theBuilders.get(name);
				if (old != null) {
					elSession.withError("Multiple element-defs/add-ons named " + name + " declared");
					return;
				}
				boolean addOn = element.getName().equals("add-on");
				QonfigElementOrAddOn.Builder builder = addOn ? QonfigAddOn.build(name, elSession) : QonfigElementDef.build(name, elSession);
				theBuilders.put(name, builder);
				theNodes.put(name, element);
				parseText(element, builder, false);
				String abstS = element.getAttribute("abstract", false);
				if (abstS != null) {
					switch (abstS) {
					case "true":
						builder.setAbstract(true);
						break;
					case "false":
						builder.setAbstract(false);
						break;
					default:
						elSession.withError("abstract attribute must be \"true\" or \"false\", not \"" + abstS + "\"");
						break;
					}
				}
			}
		}

		QonfigValueType parsePattern(StrictXmlReader pattern, QonfigParseSession session, boolean topLevel) {
			String name = pattern.getAttribute("name", topLevel);
			switch (pattern.getName()) {
			case "string":
				try {
					pattern.check();
				} catch (IllegalArgumentException e) {
					session.withWarning(e.getMessage());
				}
				return QonfigValueType.STRING;
			case "boolean":
				try {
					pattern.check();
				} catch (IllegalArgumentException e) {
					session.withWarning(e.getMessage());
				}
				return QonfigValueType.BOOLEAN;
			case "pattern":
				String text = pattern.getTextTrim(false);
				if (text == null) {
					if (name != null) {
						if (declaredTypes.containsKey(name))
							return declaredTypes.get(name);
						QonfigValueType type = session.getToolkit().getAttributeType(name);
						if (type == null)
							session.withError("Pattern '" + name + "' not found");
						return type;
					} else {
						session.withError("Pattern has no value");
						return new ErrorValueType(session.getToolkit(), name);
					}
				}
				Pattern p;
				try {
					p = Pattern.compile(text);
				} catch (PatternSyntaxException e) {
					session.withError("Bad pattern : " + text);
					return new ErrorValueType(session.getToolkit(), name);
				}
				try {
					pattern.check();
				} catch (IllegalArgumentException e) {
					session.withWarning(e.getMessage());
				}
				return new QonfigPattern(session.getToolkit(), name, p);
			case "literal":
				text = pattern.getTextTrim(false);
				if (text == null) {
					session.withError("Literal has no value");
					return new ErrorValueType(session.getToolkit(), name);
				}
				try {
					pattern.check();
				} catch (IllegalArgumentException e) {
					session.withWarning(e.getMessage());
				}
				return new QonfigValueType.Literal(session.getToolkit(), text);
			case "one-of":
				ArrayList<QonfigValueType> components = new ArrayList<>();
				try {
					int childIdx = 0;
					for (StrictXmlReader compEl : pattern.getElements(1, -1)) {
						QonfigValueType comp = parsePattern(compEl, session.forChild(compEl.getName(), childIdx), false);
						if (comp != null)
							components.add(comp);
						childIdx++;
						compEl.check();
					}
					pattern.check();
				} catch (IllegalArgumentException e) {
					session.withWarning(e.getMessage());
				}
				components.trimToSize();
				return new QonfigValueType.OneOf(session.getToolkit(), name, Collections.unmodifiableList(components));
			case "explicit":
				String prefix = pattern.getAttribute("prefix", false);
				String suffix = pattern.getAttribute("suffix", false);
				QonfigValueType wrapped = parsePattern(pattern.getElements(0, 1).get(0), session, false);
				try {
					pattern.check();
				} catch (IllegalArgumentException e) {
					session.withWarning(e.getMessage());
				}
				return new QonfigValueType.Explicit(session.getToolkit(), name, wrapped, //
					prefix == null ? "" : prefix, suffix == null ? "" : suffix);
			case "external":
				for (CustomValueType vt : theCustomValueTypes) {
					if (vt.getName().equals(name))
						return new QonfigValueType.Custom(session.getToolkit(), vt);
				}
				session.withError("Expected external value type '" + name + "', but none supplied");
				return new ErrorValueType(session.getToolkit(), name);
			default:
				session.withError("Unrecognized pattern specification: " + pattern.getName());
				return null;
			}
		}

		@Override
		public void fillOutTypes(QonfigParseSession session) {
			// Second pass to fill out elements and add-ons

			Set<String> completed = new HashSet<>();
			// Stage 3: Create new attributes and text
			for (StrictXmlReader element : theNodes.values())
				parseSimpleAttributes(element, session, completed);

			// Stage 4: Override attributes
			completed.clear();
			for (StrictXmlReader element : theNodes.values())
				overrideAttributes(element, session, completed);

			// Stage 5: Build content
			completed.clear();
			for (StrictXmlReader element : theNodes.values())
				parseChildren(element, session, completed, false);

			// Stage 6: Override children
			completed.clear();
			for (StrictXmlReader element : theNodes.values())
				parseChildOverrides(element, session, completed);

			// Stage 7: Parse metadata specs
			completed.clear();
			for (StrictXmlReader element : theNodes.values())
				parseChildren(element, session, completed, true);

			// Validate and complete structure compilation
			completed.clear();
			for (StrictXmlReader element : theNodes.values())
				validate(element, completed);

			// Stage 8: Parse auto-inheritance
			StrictXmlReader autoInheritEl = root.getElement("auto-inheritance", false);
			if (autoInheritEl != null) {
				QonfigParseSession autoInheritSession = session.forChild("auto-inheritance", 1);
				int i = 0;
				for (StrictXmlReader ai : autoInheritEl.getElements("auto-inherit", 0, -1))
					parseAutoInheritance(ai, autoInheritSession.forChild("auto-inherit", i++));
				try {
					autoInheritEl.check();
				} catch (IllegalArgumentException e) {
					session.withWarning(e.getMessage());
				}
			}

			// Stage 9: Parse metadata
			for (StrictXmlReader element : theNodes.values())
				parseMetadata(element, session);

			for (StrictXmlReader element : theNodes.values()) {
				try {
					element.check();
				} catch (IllegalArgumentException e) {
					theBuilders.get(element.getAttribute("name", true)).getSession().withError(e.getMessage());
				}
			}
			for (String rootName : rootNames) {
				QonfigElementDef declaredRoot = session.getToolkit().getElement(rootName);
				if (declaredRoot == null) {
					if (session.getToolkit().getAddOn(rootName) == null)
						session.withError("Unrecognized root element: " + rootName);
					else
						session.withError("Root must be an element-def, not " + rootName);
				}
				declaredRoots.add(declaredRoot);
			}

			// Finish build
			for (QonfigElementOrAddOn.Builder builder : theBuilders.values())
				builder.build();
		}

		@Override
		public Set<QonfigElementDef> getDeclaredRoots(QonfigParseSession session) {
			return Collections.unmodifiableSet(declaredRoots);
		}

		private QonfigElementOrAddOn parseExtensions(StrictXmlReader element, LinkedList<String> path, QonfigParseSession elsSession,
			QonfigParseSession addOnsSession, Set<String> completed) {
			String name = element.getAttribute("name", false);
			QonfigElementOrAddOn.Builder builder = theBuilders.get(name);
			boolean addOn = builder instanceof QonfigAddOn.Builder;
			String extendsS = element.getAttribute(addOn ? "requires" : "extends", false);
			String inheritsS = element.getAttribute("inherits", false);
			if (completed.contains(name))
				return builder.get(); // Already filled out
			if (extendsS == null && (inheritsS == null || inheritsS.isEmpty())) {
				completed.add(name);// No extensions
				return builder.get();
			}
			QonfigParseSession session = (addOn ? addOnsSession : elsSession).forChild(element.getName(), builder.getName());
			if (path.contains(builder.getName())) {
				session.withError("Circular element inheritance detected: " + path);
				return null;
			}
			path.add(builder.getName());
			try {
				if (extendsS != null) {
					int colon = extendsS.indexOf(':');
					QonfigElementDef el = null;
					if (colon >= 0) {
						try {
							el = session.getToolkit().getElement(extendsS);
							if (el == null)
								session.withError("No such element found: " + extendsS);
						} catch (IllegalArgumentException e) {
							session.withError("For extension " + extendsS + ": " + e.getMessage());
						}
					} else {
						QonfigElementOrAddOn.Builder extBuilder = theBuilders.get(extendsS);
						if (extBuilder == null) {
							el = session.getToolkit().getElement(extendsS);
							if (el == null)
								session.withError("No such element-def found: " + extendsS);
						} else if (!(extBuilder instanceof QonfigElementDef.Builder)) {
							session.withError(extendsS + " must refer to an element-def");
						} else {
							el = (QonfigElementDef) parseExtensions(theNodes.get(extendsS), path, elsSession, addOnsSession, completed);
							if (el == null) {
								session.withError("Circular element inheritance detected: " + path);
								return null;
							}
						}
					}
					if (el != null)
						builder.setSuperElement(el);
				}
				if (inheritsS != null && !inheritsS.isEmpty()) {
					for (String inh : inheritsS.split(",")) {
						inh = inh.trim();
						int colon = inh.indexOf(':');
						QonfigAddOn el = null;
						if (colon >= 0) {
							try {
								el = session.getToolkit().getAddOn(inh);
								if (el == null)
									session.withError("No such add-on found: " + inh);
							} catch (IllegalArgumentException e) {
								session.withError("For extension " + inh + ": " + e.getMessage());
							}
						} else {
							QonfigElementOrAddOn.Builder extBuilder = theBuilders.get(inh);
							if (extBuilder == null) {
								el = session.getToolkit().getAddOn(inh);
								if (el == null)
									session.withError("No such add-on found: " + inh);
							} else if (!(extBuilder instanceof QonfigAddOn.Builder)) {
								session.withError(inheritsS + " must refer to add-ons");
							} else {
								// TODO This throws an error in the case of an element-def specifying as inheritance
								// an add-on that requires it
								el = (QonfigAddOn) parseExtensions(theNodes.get(inh), path, elsSession, addOnsSession, completed);
								if (el == null) {
									session.withError("Circular inheritance detected: " + path);
									return null;
								}
							}
						}
						if (el != null)
							builder.inherits(el);
					}
				}
				return builder.get();
			} finally {
				path.removeLast();
				completed.add(name); // Mark complete even if there are errors, so the user can see all the errors at the end
			}
		}

		private void parseSimpleAttributes(StrictXmlReader element, QonfigParseSession session, Set<String> completed) {
			QonfigElementOrAddOn.Builder builder = theBuilders.get(element.getAttribute("name", false));
			if (completed.contains(builder.getName()))
				return;
			completed.add(builder.getName());
			if (builder.getSuperElement() != null && builder.getSuperElement().getDeclarer() == session.getToolkit())
				parseSimpleAttributes(theNodes.get(builder.getSuperElement().getName()), session, completed);
			for (QonfigAddOn inh : builder.getInheritance()) {
				if (inh.getDeclarer() == session.getToolkit())
					parseSimpleAttributes(theNodes.get(inh.getName()), session, completed);
			}
			builder.setStage(QonfigElementOrAddOn.Builder.Stage.NewAttributes);
			for (StrictXmlReader attr : element.getElements("attribute", 0, -1)) {
				String attrName = attr.getAttribute("name", false);
				if (attrName == null) {
					builder.getSession().withError("No name attribute for attribute definition");
					continue;
				}
				if (attrName.lastIndexOf('.') >= 0)
					continue; // Attribute override, not at this stage
				if (attrName.indexOf(':') >= 0) {
					builder.getSession().withError("No such attribute '" + attrName + "'--use '.' instead of ':'");
					continue;
				} else if (RESERVED_ATTR_NAMES.contains(attrName)) {
					builder.getSession().withError("Attribute name '" + attrName + "' is reserved");
					continue;
				}
				QonfigParseSession attrSession = builder.getSession().forChild("attribute", attrName);
				if (!checkName(attrName))
					attrSession.withError("Illegal attribute name");
				String typeName = attr.getAttribute("type", false);
				QonfigValueType type;
				if (typeName == null) {
					attrSession.withError("No type specified");
					type = null;
				} else
					type = parseAttributeType(attrSession, typeName, true);
				String specifyS = attr.getAttribute("specify", false);
				String defaultS = attr.getAttribute("default", false);
				SpecificationType spec;
				if (specifyS != null)
					spec = SpecificationType.fromAttributeValue(specifyS, attrSession);
				else if (defaultS != null)
					spec = SpecificationType.Optional;
				else
					spec = SpecificationType.Required;
				Object defaultV = null;
				if (defaultS != null && type != null)
					defaultV = type.parse(defaultS, attrSession.getToolkit(), attrSession);
				if (type != null)
					builder.withAttribute(attrName, type, spec, defaultV);
				try {
					attr.check();
				} catch (IllegalArgumentException e) {
					attrSession.withWarning(e.getMessage());
				}
			}
		}

		private void parseText(StrictXmlReader element, QonfigElementOrAddOn.Builder builder, boolean modify) {
			StrictXmlReader text = element.getElement(modify ? "value-mod" : "value", false);
			if (text == null)
				return;
			QonfigParseSession textSession = builder.getSession().forChild(text.getName(), 0);
			String typeName = text.getAttribute("type", false);
			QonfigValueType type;
			if (typeName == null) {
				type = null;
			} else
				type = parseAttributeType(textSession, typeName, false);
			String specifyS = text.getAttribute("specify", false);
			String defaultS = text.getAttribute("default", false);
			SpecificationType spec;
			if (specifyS != null)
				spec = SpecificationType.fromAttributeValue(specifyS, textSession);
			else if (defaultS != null)
				spec = SpecificationType.Optional;
			else
				spec = SpecificationType.Required;
			Object defaultV = null;
			if (defaultS != null) {
				QonfigValueType parseType = type;
				if (parseType == null && builder.getSuperElement() != null && builder.getSuperElement().getValue() != null)
					parseType = builder.getSuperElement().getValue().getType();
				if (parseType == null) {
					textSession.withError("No type specified");
					parseType = QonfigValueType.STRING;
				}
				defaultV = parseType.parse(defaultS, builder.getSession().getToolkit(), textSession);
			}
			if (modify)
				builder.modifyValue(type, spec, defaultV);
			else
				builder.withValue(type, spec, defaultV);
			try {
				text.check();
			} catch (IllegalArgumentException e) {
				textSession.withWarning(e.getMessage());
			}
		}

		private void overrideAttributes(StrictXmlReader element, QonfigParseSession session, Set<String> completed) {
			QonfigElementOrAddOn.Builder builder = theBuilders.get(element.getAttribute("name", false));
			if (completed.contains(builder.getName()))
				return;
			completed.add(builder.getName());
			if (builder.getSuperElement() != null && builder.getSuperElement().getDeclarer() == session.getToolkit())
				overrideAttributes(theNodes.get(builder.getSuperElement().getName()), session, completed);
			for (QonfigAddOn inh : builder.getInheritance()) {
				if (inh.getDeclarer() == session.getToolkit())
					overrideAttributes(theNodes.get(inh.getName()), session, completed);
			}
			builder.setStage(QonfigElementOrAddOn.Builder.Stage.ModifyAttributes);
			for (StrictXmlReader attr : element.getElements("attr-mod", 0, -1)) {
				String attrName = attr.getAttribute("name", false);
				if (attrName == null) {
					builder.getSession().withError("No name attribute for attribute definition");
					continue;
				}
				if (attrName.lastIndexOf('.') < 0)
					continue; // Simple attribute, already parsed
				QonfigParseSession attrSession = builder.getSession().forChild("attr-mod", attrName);
				ElementQualifiedParseItem qAttr;
				qAttr = ElementQualifiedParseItem.parse(attrName, attrSession);
				if (qAttr == null)
					continue;
				QonfigElementOrAddOn qualifier = qAttr.declaredElement != null ? qAttr.declaredElement : builder.get();
				QonfigAttributeDef overridden = qualifier.getDeclaredAttributes().get(qAttr.itemName);
				if (overridden == null) {
					switch (qualifier.getAttributesByName().get(qAttr.itemName).size()) {
					case 0:
						attrSession.withError("No such attribute found");
						return;
					case 1:
						overridden = qualifier.getAttributesByName().get(qAttr.itemName).getFirst();
						break;
					default:
						attrSession.withError("Multiple matching attributes found");
						return;
					}
				}
				String typeName = attr.getAttribute("type", false);
				QonfigValueType type;
				if (typeName == null) {
					type = null;
				} else
					type = parseAttributeType(attrSession, typeName, true);
				String specifyS = attr.getAttribute("specify", false);
				String defaultS = attr.getAttribute("default", false);
				SpecificationType spec;
				if (specifyS != null)
					spec = SpecificationType.fromAttributeValue(specifyS, attrSession);
				else if (defaultS != null)
					spec = SpecificationType.Optional;
				else
					spec = overridden.getSpecification();
				Object defaultV = null;
				if (defaultS != null) {
					QonfigValueType type2 = type;
					if (type2 == null)
						type2 = overridden.getType();
					defaultV = type2.parse(defaultS, attrSession.getToolkit(), attrSession);
					if (defaultV == null)
						builder.getSession().withError(
							"Default value '" + defaultS + "' parsed to null by attribute type " + type2 + "--this is not allowed");
				}
				if (overridden != null)
					builder.modifyAttribute(overridden, type, spec, defaultV);
				try {
					attr.check();
				} catch (IllegalArgumentException e) {
					attrSession.withWarning(e.getMessage());
				}
			}
			parseText(element, builder, true);
		}

		private void parseChildren(StrictXmlReader element, QonfigParseSession session, Set<String> completed, boolean metadata) {
			QonfigElementOrAddOn.Builder builder = theBuilders.get(element.getAttribute("name", false));
			if (completed.contains(builder.getName()))
				return;
			completed.add(builder.getName());
			if (builder.getSuperElement() != null && builder.getSuperElement().getDeclarer() == session.getToolkit())
				parseChildren(theNodes.get(builder.getSuperElement().getName()), session, completed, metadata);
			for (QonfigAddOn inh : builder.getInheritance()) {
				if (inh.getDeclarer() == session.getToolkit())
					parseChildren(theNodes.get(inh.getName()), session, completed, metadata);
			}
			if (!metadata)
				builder.setStage(QonfigElementOrAddOn.Builder.Stage.NewChildren);
			String elementName = metadata ? "element-meta" : "child-def";
			for (StrictXmlReader child : element.getElements(elementName, 0, -1)) {
				String name = child.getAttribute("name", false);
				if (name == null) {
					builder.getSession().withError("Unnamed " + elementName);
					break;
				}
				QonfigParseSession childSession = builder.getSession().forChild(elementName, name);
				if (!checkName(name))
					childSession.withError("Bad " + elementName + " name");
				Set<QonfigChildDef.Declared> roles;
				String rolesS = child.getAttribute("role", false);
				if (rolesS != null && metadata) {
					childSession.withError("role cannot be specified for " + elementName);
					rolesS = null;
				}
				if (rolesS != null) {
					roles = new LinkedHashSet<>(7);
					for (String roleName : rolesS.split(",")) {
						roleName = roleName.trim();
						ElementQualifiedParseItem parsedRole = ElementQualifiedParseItem.parse(roleName, builder.getSession());
						if (parsedRole == null)
							continue;
						QonfigElementOrAddOn qualifier;
						if (parsedRole.declaredElement != null)
							qualifier = parsedRole.declaredElement;
						else
							qualifier = builder.getSuperElement();
						QonfigChildDef.Declared role = qualifier.getDeclaredChildren().get(parsedRole.itemName);
						if (role == null) {
							BetterCollection<? extends QonfigChildDef> children = findChildren(qualifier, parsedRole.itemName,
								session.getToolkit());
							switch (children.size()) {
							case 0:
								childSession.withError("No such role '" + parsedRole.itemName + "' on element " + qualifier);
								break;
							case 1:
								role = children.getFirst().getDeclared();
								break;
							default:
								childSession.withError("Multiple roles named '" + parsedRole.itemName + "' on element " + qualifier);
								break;
							}
						}
						if (role != null)
							roles.add(role);
					}
				} else
					roles = Collections.emptySet();
				String typeName = child.getAttribute("type", false);
				QonfigElementDef elType;
				if (typeName == null) {
					childSession.withError("No type specified");
					elType = null;
				} else {
					try {
						elType = builder.getSession().getToolkit().getElement(typeName);
						if (elType == null)
							childSession.withError("Unrecognized element-def: '" + typeName + "'");
					} catch (IllegalArgumentException e) {
						childSession.withError(e.getMessage());
						elType = null;
					}
				}
				String minS = child.getAttribute("min", false);
				String maxS = child.getAttribute("max", false);
				int min, max;
				try {
					min = minS == null ? 1 : Integer.parseInt(minS);
				} catch (NumberFormatException e) {
					childSession.withError("Bad \"min\" value \"" + minS + "\"", e);
					min = 1;
				}
				try {
					if (maxS == null) {
						if (min < 1)
							max = 1;
						else
							max = min;
					} else if ("inf".equals(maxS))
						max = Integer.MAX_VALUE;
					else
						max = Integer.parseInt(maxS);
				} catch (NumberFormatException e) {
					childSession.withError("Bad \"max\" value \"" + maxS + "\"", e);
					max = Integer.MAX_VALUE;
				}
				String inheritsS = child.getAttribute("inherits", false);
				Set<QonfigAddOn> inherits = new LinkedHashSet<>();
				if (inheritsS != null) {
					for (String inherit : inheritsS.split(",")) {
						inherit = inherit.trim();
						if (inherit.isEmpty())
							continue;
						QonfigAddOn el;
						try {
							el = builder.getSession().getToolkit().getAddOn(inherit);
						} catch (IllegalArgumentException e) {
							childSession.withError(e.getMessage());
							continue;
						}
						if (el == null)
							childSession.withError("Unrecognized add-on \"" + inherit + "\" as inheritance");
						else
							inherits.add(el);
					}
				}
				Set<QonfigAddOn> requires = new LinkedHashSet<>();
				String requiresS = child.getAttribute("requires", false);
				if (requiresS != null) {
					for (String require : requiresS.split(",")) {
						require = require.trim();
						if (require.isEmpty())
							continue;
						QonfigAddOn el;
						try {
							el = builder.getSession().getToolkit().getAddOn(require);
						} catch (IllegalArgumentException e) {
							childSession.withError(e.getMessage());
							continue;
						}
						if (el == null)
							childSession.withError("Unrecognized add-on \"" + require + "\" as requirement");
						else
							requires.add(el);
					}
				}
				if (elType != null) {
					if (metadata)
						builder.withMetaSpec(name, elType, inherits, requires, min, max);
					else
						builder.withChild(name, elType, roles, inherits, requires, min, max);
				}
				try {
					child.check();
				} catch (IllegalArgumentException e) {
					childSession.withError(e.getMessage());
				}
			}
		}

		private void parseChildOverrides(StrictXmlReader element, QonfigParseSession session, Set<String> completed) {
			QonfigElementOrAddOn.Builder builder = theBuilders.get(element.getAttribute("name", false));
			if (completed.contains(builder.getName()))
				return;
			completed.add(builder.getName());
			if (builder.getSuperElement() != null && builder.getSuperElement().getDeclarer() == session.getToolkit())
				parseChildOverrides(theNodes.get(builder.getSuperElement().getName()), session, completed);
			for (QonfigAddOn inh : builder.getInheritance()) {
				if (inh.getDeclarer() == session.getToolkit())
					parseChildOverrides(theNodes.get(inh.getName()), session, completed);
			}
			builder.setStage(QonfigElementOrAddOn.Builder.Stage.ModifyChildren);
			childLoop: //
			for (StrictXmlReader child : element.getElements("child-mod", 0, -1)) {
				String name = child.getAttribute("child", false);
				if (name == null) {
					if (child.getAttribute("name", false) != null)
						builder.getSession().forChild("child-mod", null).withError("Use 'child=' instead of 'name='");
					else
						builder.getSession().forChild("child-mod", null).withError("No child attribute");
					break;
				}
				QonfigParseSession childSession = builder.getSession().forChild(child.getName(), name);
				QonfigChildDef.Declared overridden;
				{
					ElementQualifiedParseItem parsedRole = ElementQualifiedParseItem.parse(name, builder.getSession());
					if (parsedRole == null)
						continue;
					QonfigElementOrAddOn qualifier;
					if (parsedRole.declaredElement != null)
						qualifier = parsedRole.declaredElement;
					else {
						qualifier = builder.getSuperElement();
						if (qualifier == null) {
							childSession.withError("No requires, so no children");
							continue childLoop;
						}
					}
					overridden = qualifier.getDeclaredChildren().get(parsedRole.itemName);
					if (overridden == null) {
						// Can't use getChildrenByName() here because it hasn't been populated at this build stage
						BetterCollection<? extends QonfigChildDef> allOverridden = findChildren(qualifier, parsedRole.itemName,
							session.getToolkit());
						switch (allOverridden.size()) {
						case 0:
							childSession.withError("No such child '" + parsedRole.itemName + "' on element " + qualifier);
							break;
						case 1:
							overridden = allOverridden.getFirst().getDeclared();
							break;
						default:
							childSession.withError("Multiple children named '" + parsedRole.itemName + "' on element " + qualifier);
							break;
						}
					}
				}
				String typeName = child.getAttribute("type", false);
				QonfigElementDef elType;
				if (typeName == null) {
					elType = null;
				} else {
					try {
						elType = builder.getSession().getToolkit().getElement(typeName);
						if (elType == null)
							childSession.withError("Unrecognized element-def: '" + typeName + "'");
					} catch (IllegalArgumentException e) {
						childSession.withError(e.getMessage());
						elType = null;
					}
				}
				String minS = child.getAttribute("min", false);
				String maxS = child.getAttribute("max", false);
				Integer min, max;
				try {
					min = minS == null ? null : Integer.parseInt(minS);
				} catch (NumberFormatException e) {
					childSession.withError("Bad \"min\" value \"" + minS + "\"", e);
					min = null;
				}
				try {
					max = maxS == null ? null : Integer.parseInt(maxS);
				} catch (NumberFormatException e) {
					childSession.withError("Bad \"max\" value \"" + maxS + "\"", e);
					max = null;
				}
				String inheritsS = child.getAttribute("inherits", false);
				Set<QonfigAddOn> inherits = new LinkedHashSet<>();
				if (inheritsS != null) {
					for (String inherit : inheritsS.split(",")) {
						inherit = inherit.trim();
						if (inherit.isEmpty())
							continue;
						QonfigAddOn el;
						try {
							el = builder.getSession().getToolkit().getAddOn(inherit);
						} catch (IllegalArgumentException e) {
							childSession.withError(e.getMessage());
							continue;
						}
						if (el == null)
							childSession.withError("Unrecognized add-on \"" + inherit + "\" as inheritance");
						else
							inherits.add(el);
					}
				}
				String requiresS = child.getAttribute("requires", false);
				Set<QonfigAddOn> requires = new LinkedHashSet<>();
				if (requiresS != null) {
					for (String require : requiresS.split(",")) {
						require = require.trim();
						if (require.isEmpty())
							continue;
						QonfigAddOn el;
						try {
							el = builder.getSession().getToolkit().getAddOn(require);
						} catch (IllegalArgumentException e) {
							childSession.withError(e.getMessage());
							continue;
						}
						if (el == null)
							childSession.withError("Unrecognized add-on \"" + require + "\" as requirement");
						requires.add(el);
					}
				}
				if (overridden != null)
					builder.modifyChild(overridden, elType, inherits, requires, min, max);
				try {
					child.check();
				} catch (IllegalArgumentException e) {
					childSession.withError(e.getMessage());
				}
			}
		}

		private BetterCollection<? extends QonfigChildDef> findChildren(QonfigElementOrAddOn qualifier, String itemName,
			QonfigToolkit building) {
			if (qualifier.getDeclarer() != building)
				return qualifier.getChildrenByName().get(itemName);
			BetterHashSet<QonfigChildDef.Declared> found = BetterHashSet.build().build();
			findChildren(qualifier, itemName, building, found);
			return found;
		}

		private void findChildren(QonfigElementOrAddOn qualifier, String itemName, QonfigToolkit building,
			BetterSet<QonfigChildDef.Declared> found) {
			if (qualifier.getDeclarer() != building) {
				for (QonfigChildDef child : qualifier.getChildrenByName().get(itemName))
					found.add(child.getDeclared());
				return;
			}
			QonfigChildDef.Declared child = qualifier.getDeclaredChildren().get(itemName);
			if (child != null)
				found.add(child);
			if (qualifier.getSuperElement() != null)
				findChildren(qualifier.getSuperElement(), itemName, building, found);
			for (QonfigAddOn inh : qualifier.getFullInheritance().getExpanded(QonfigAddOn::getInheritance)) {
				child = inh.getDeclaredChildren().get(itemName);
				if (child != null)
					found.add(child);
			}
		}

		private void parseAutoInheritance(StrictXmlReader element, QonfigParseSession session) {
			QonfigAutoInheritance.Builder builder = QonfigAutoInheritance.build(session);
			String inhStr = element.getAttribute("inherits", false);
			if (inhStr == null || inhStr.isEmpty()) {
				session.withError("No inheritance");
				return;
			}
			String[] inheritance = inhStr.split(",");
			for (String inh : inheritance) {
				QonfigAddOn addOn = session.getToolkit().getAddOn(inh);
				if (addOn == null)
					session.withError("No such add-on found: " + inh);
				else
					builder.inherits(addOn);
			}
			List<StrictXmlReader> targets = element.getElements(0, -1);
			if (targets.isEmpty()) {
				session.withError("No targets");
				return;
			}
			int i = 0;
			for (StrictXmlReader target : targets) {
				QonfigParseSession targetSession = session.forChild("target", i++);
				String typeStr = target.getAttribute("element", false);
				QonfigElementOrAddOn type;
				if (typeStr == null)
					type = null;
				else {
					type = targetSession.getToolkit().getElementOrAddOn(typeStr);
					if (type == null) {
						targetSession.withError("No such element or add-on found: " + typeStr);
						continue;
					}
				}
				String roleStr = target.getAttribute("role", false);
				QonfigChildDef child;
				if (roleStr == null)
					child = null;
				else {
					int dot = roleStr.indexOf('.');
					if (dot < 0) {
						targetSession.withError("role name must be qualified with the element or add-on type that declares it");
						continue;
					}
					QonfigElementOrAddOn owner = session.getToolkit().getElementOrAddOn(roleStr.substring(0, dot));
					if (owner == null) {
						targetSession.withError("No such element or add-on found: " + roleStr.substring(0, dot));
						continue;
					}
					child = owner.getChild(roleStr.substring(dot + 1));
					if (child == null)
						child = owner.getMetaSpec().getChild(roleStr.substring(dot + 1));
					if (child == null) {
						targetSession.withError("No such child '" + roleStr.substring(dot + 1) + "' of " + owner);
						continue;
					}
				}
				builder.withTarget(type, child);
				target.check();
			}
			declaredAutoInheritance.add(builder.build());
			element.check();
		}

		private void parseMetadata(StrictXmlReader element, QonfigParseSession session) {
			QonfigElementOrAddOn.Builder builder = theBuilders.get(element.getAttribute("name", true));
			builder.withMetaData(md -> {
				parseDocElement(session, md, element, false, child -> !TOOLKIT_EL_NAMES.contains(child.getName()));
			});
		}

		private void validate(StrictXmlReader element, Set<String> completed) {
			QonfigElementOrAddOn.Builder builder = theBuilders.get(element.getAttribute("name", true));
			if (completed.contains(builder.getName()))
				return;
			completed.add(builder.getName());
			if (builder.getSuperElement() != null && builder.getSuperElement().getDeclarer() == builder.get().getDeclarer())
				validate(theNodes.get(builder.getSuperElement().getName()), completed);
			for (QonfigAddOn inh : builder.getInheritance()) {
				if (inh.getDeclarer() == builder.get().getDeclarer())
					validate(theNodes.get(inh.getName()), completed);
			}
			builder.setStage(QonfigElementOrAddOn.Builder.Stage.MetaData);
		}
	}

	/** A placeholder for when a value type cannot be parsed */
	static class ErrorValueType implements QonfigValueType.Declared {
		private final QonfigToolkit theDeclarer;
		private final String theName;

		/**
		 * @param declarer The toolkit declaring the value type
		 * @param name The name for the value type
		 */
		public ErrorValueType(QonfigToolkit declarer, String name) {
			theDeclarer = declarer;
			theName = name;
		}

		@Override
		public QonfigToolkit getDeclarer() {
			return theDeclarer;
		}

		@Override
		public String getName() {
			return theName;
		}

		@Override
		public Object parse(String value, QonfigToolkit tk, QonfigParseSession session) {
			return value;
		}

		@Override
		public boolean isInstance(Object value) {
			return false;
		}
	}

	static QonfigValueType parseAttributeType(QonfigParseSession session, String typeName, boolean withElement) {
		switch (typeName) {
		case "string":
			return QonfigValueType.STRING;
		case "boolean":
			return QonfigValueType.BOOLEAN;
		}
		QonfigValueType.Declared type = session.getToolkit().getAttributeType(typeName);
		if (type != null)
			return type;
		QonfigAddOn addOn = session.getToolkit().getAddOn(typeName);
		if (addOn != null) {
			if (withElement)
				return addOn;
			else {
				session.withError("Add-on type " + typeName + " cannot be used here");
				return QonfigValueType.STRING;
			}
		}
		session.withError("Unrecognized value type '" + typeName + "'", null);
		return QonfigValueType.STRING;
	}

	private static boolean checkName(String name) {
		if (DOC_ELEMENT_INHERITANCE_ATTR.equals(name))
			return false;
		return Verifier.checkAttributeName(name) == null;
	}
}
