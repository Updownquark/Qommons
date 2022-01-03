package org.qommons.config;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

	private static final QonfigToolkit PLACEHOLDER;
	static {
		try {
			PLACEHOLDER = new QonfigToolkit(null, null, null, null, null, null);
		} catch (QonfigParseException e) {
			throw new IllegalStateException("This should not happen", e);
		}
	}

	private final Map<URL, QonfigToolkit> theToolkits;

	/** Creates the parser */
	public DefaultQonfigParser() {
		theToolkits = new HashMap<>();
	}

	@Override
	public DefaultQonfigParser withToolkit(QonfigToolkit... toolkits) {
		for (QonfigToolkit toolkit : toolkits) {
			if (toolkit != null)
				theToolkits.put(toolkit.getLocation(), toolkit);
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
		StrictXmlReader rootReader = new StrictXmlReader(root);
		Map<URL, QonfigToolkit> parsed = new LinkedHashMap<>();
		LinkedList<String> path = new LinkedList<>();
		Map<String, QonfigToolkit> uses = new LinkedHashMap<>();
		for (Map.Entry<String, String> use : rootReader.findAttributes(USES, 0, -1).entrySet()) {
			String name = use.getKey().substring(5);
			if (name.isEmpty())
				throw new IllegalArgumentException("Empty toolkit name");
			checkName(name);
			path.add(name);
			URL ref;
			try {
				ref = new URL(QommonsConfig.resolve(use.getValue(), location.toString()));
			} catch (IOException e) {
				throw new IllegalArgumentException("Bad location for toolkit dependency " + path + "/" + name + ": " + use.getValue(), e);
			}

			QonfigToolkit oldDep = parsed.putIfAbsent(ref, PLACEHOLDER);
			if (oldDep == null) {
				QonfigToolkit dep = _parseToolkitXml(ref, ref.openStream(), path);
				parsed.put(ref, dep);
				uses.put(name, dep);
			} else
				uses.put(name, oldDep);
		}
		if (uses.isEmpty())
			throw new IllegalArgumentException("No toolkit uses declared");
		QonfigElementDef rootDef;
		if (rootReader.getPrefix() != null) {
			QonfigToolkit primary = uses.get(rootReader.getPrefix());
			if (primary == null)
				throw new IllegalArgumentException("Unrecognized namespace for root: " + rootReader.getPrefix());
			else if (primary.getRoot() == null)
				throw new IllegalArgumentException("Toolkit " + rootReader.getPrefix() + "=" + primary.getLocation()
					+ " declares no root--cannot be uses as the primary for a document");
			rootDef = primary.getElement(rootReader.getTagName());
			if (rootDef == null)
				throw new IllegalArgumentException(
					"No such element '" + rootReader.getTagName() + "' found in toolkit " + rootReader.getPrefix() + " (" + primary + ")");
			else if (rootDef != primary.getRoot())
				throw new IllegalArgumentException(
					rootReader.getName() + " is not the root of toolkit " + rootReader.getPrefix() + " (" + primary + ")");
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
				isRoot = tk.getRoot() == rootDef2;
				if (isRoot)
					break;
			}
			if (!isRoot)
				throw new IllegalArgumentException("Element '" + rootReader.getName() + "' is not declared as the root of any toolkit");
			rootDef = rootDef2;
		}
		QonfigToolkit docToolkit = new QonfigToolkit(null, Collections.unmodifiableMap(uses), Collections.emptyMap(),
			Collections.emptyMap(), Collections.emptyMap(), new QonfigToolkit.ToolkitBuilder() {
				@Override
				public void parseTypes(QonfigParseSession s) {
				}

				@Override
				public void fillOutTypes(QonfigParseSession s) {
				}

				@Override
				public QonfigElementDef getDeclaredRoot(QonfigParseSession s) {
					return rootDef;
				}
			});
		session = QonfigParseSession.forRoot(rootReader.getName(), docToolkit);
		doc = new QonfigDocument(location, docToolkit);
		parseDocElement(session, QonfigElement.build(session, doc, null, rootDef), rootReader);
		rootReader.check();
		session.printWarnings(System.err, location);
		session.throwErrors(location);
		return doc;
	}

	private static final Pattern USES = Pattern.compile("uses\\:.*");

	QonfigElement parseDocElement(QonfigParseSession session, QonfigElement.Builder builder, StrictXmlReader el) {
		if (el.getParent() != null) {
			for (Map.Entry<String, String> uses : el.findAttributes(USES, 0, -1).entrySet())
				session.withError("uses attributes may only be used at the root: '" + uses.getKey() + "'", null);
		}
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
		builder.doneWithAttributes();

		for (StrictXmlReader child : el.getElements(0, -1)) {
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
				parseDocElement(childSession, cb, child);
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
		if (theToolkits.containsKey(location) && theToolkits.get(location) != PLACEHOLDER)
			return theToolkits.get(location);
		Element root;
		try {
			root = QommonsConfig.getRootElement(xml);
		} catch (IOException e) {
			throw new IOException("For toolkit " + location, e);
		}
		xml.close();
		StrictXmlReader rootReader = new StrictXmlReader(root);
		if (!rootReader.getTagName().equals("qonfig-def"))
			throw new IllegalArgumentException("Expected 'qonfig-def' for root element, not " + root.getNodeName());
		Map<String, QonfigToolkit> dependencies = new LinkedHashMap<>();
		for (Map.Entry<String, String> ext : rootReader.findAttributes(EXTENDS, 0, -1).entrySet()) {
			String name = ext.getKey().substring("extends:".length());
			if (name.isEmpty())
				throw new IllegalArgumentException(path + ": Empty dependency name");
			checkName(name);
			path.add(name);
			URL ref;
			try {
				ref = new URL(QommonsConfig.resolve(ext.getValue(), location.toString()));
			} catch (IOException e) {
				throw new IllegalArgumentException("Bad location for toolkit dependency " + path + "/" + name, e);
			}
			QonfigToolkit oldDep = theToolkits.putIfAbsent(ref, PLACEHOLDER);
			if (oldDep == PLACEHOLDER)
				throw new IllegalArgumentException("Circular or unresolvable toolkit dependency: " + path);
			else if (oldDep == null) {
				QonfigToolkit dep = _parseToolkitXml(ref, ref.openStream(), path);
				dependencies.put(name, dep);
			} else
				dependencies.put(name, oldDep);
		}
		ToolkitParser parser = new ToolkitParser(rootReader, customValueTypes);
		QonfigToolkit toolkit = new QonfigToolkit(location, Collections.unmodifiableMap(dependencies),
			Collections.unmodifiableMap(parser.getDeclaredTypes()), Collections.unmodifiableMap(parser.getDeclaredAddOns()),
			Collections.unmodifiableMap(parser.getDeclaredElements()), parser);
		rootReader.check();
		// TODO Verify
		theToolkits.put(location, toolkit);
		return toolkit;
	}

	private static final Pattern EXTENDS = Pattern.compile("extends\\:.*");

	private class ToolkitParser implements QonfigToolkit.ToolkitBuilder {
		private final StrictXmlReader root;
		private final Map<String, QonfigValueType.Declared> declaredTypes;
		private final Map<String, QonfigElementOrAddOn.Builder> theBuilders;
		private QonfigElementDef declaredRoot;

		private final Map<String, QonfigAddOn> declaredAddOns;
		private final Map<String, QonfigElementDef> declaredElements;

		private String rootName;
		private final CustomValueType[] theCustomValueTypes;
		private final Map<String, StrictXmlReader> theNodes;

		ToolkitParser(StrictXmlReader root, CustomValueType[] customValueTypes) {
			this.root = root;
			declaredTypes = new LinkedHashMap<>();
			theBuilders = new LinkedHashMap<>();
			declaredAddOns = new LinkedHashMap<>();
			declaredElements = new LinkedHashMap<>();
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

		@Override
		public void parseTypes(QonfigParseSession session) {
			for (CustomValueType cvt : theCustomValueTypes) {
				declaredTypes.put(cvt.getName(), new QonfigValueType.Custom(session.getToolkit(), cvt));
			}
			// First pass to parse basic definitions
			rootName = root.getAttribute("root", false);
			StrictXmlReader patterns = root.getElement("patterns", false);
			if (patterns != null) {
				QonfigParseSession patternsSession = session.forChild(patterns.getName(), 0);
				for (StrictXmlReader pattern : patterns.getElements(0, -1)) {
					String name = pattern.getAttribute("name", false);
					if (name == null) {
						patternsSession.withError("Pattern declared with no name");
						continue;
					}
					QonfigParseSession patternSession = patternsSession.forChild(name, 0);
					if (declaredTypes.containsKey(name)) {
						patternSession.withError("A pattern named " + name + " has already been declared");
						continue;
					}
					if (!checkName(name)) {
						patternSession.withError("Illegal pattern name: " + name);
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
			// Element/add-on parsing is in several stages:
			// Stage 1: Create a builder for each listed element with simple properties and text, and remove unparseable elements
			// Stage 2: Hook up extensions, checking for recursion
			// Stage 3: Create new attributes and text
			// Stage 4: Override attributes
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
					elSession.withError("An " + (old instanceof QonfigAddOn.Builder ? "add-on" : "element-def") + " named " + name
						+ " has already been declared");
					return;
				}
				boolean addOn = element.getName().equals("add-on");
				QonfigElementOrAddOn.Builder builder = addOn ? QonfigAddOn.build(name, elSession) : QonfigElementDef.build(name, elSession);
				theBuilders.put(name, builder);
				theNodes.put(name, element);
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
				parseText(element, builder, elSession);
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
						return null;
					}
				}
				Pattern p;
				try {
					p = Pattern.compile(text);
				} catch (PatternSyntaxException e) {
					session.withError("Bad pattern : " + text);
					return null;
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
					return null;
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
				return new QonfigValueType.Explicit(session.getToolkit(), name, wrapped, prefix, suffix);
			default:
				session.withError("Unrecognized pattern specification: " + pattern.getName());
				return null;
			}
		}

		private void parseText(StrictXmlReader element, QonfigElementOrAddOn.Builder builder, QonfigParseSession elSession) {
			StrictXmlReader text = element.getElement("value", false);
			if (text != null) {
				QonfigParseSession textSession = elSession.forChild(text.getName(), 0);
				String typeName = text.getAttribute("type", false);
				QonfigValueType type;
				if (typeName == null) {
					type = null;
				} else
					type = parseAttributeType(elSession, typeName, false);
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
					if (parseType == null && builder.getSuperElement().getValue() != null)
						parseType = builder.getSuperElement().getValue().getType();
					defaultV = parseType.parse(defaultS, elSession.getToolkit(), textSession);
				}
				builder.withValue(type, spec, defaultV);
				try {
					text.check();
				} catch (IllegalArgumentException e) {
					textSession.withWarning(e.getMessage());
				}
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
				parseChildren(element, session, completed);

			// Stage 6: Override children
			completed.clear();
			for (StrictXmlReader element : theNodes.values())
				parseChildOverrides(element, session, completed);

			// Validate and complete structure compilation
			completed.clear();
			for (StrictXmlReader element : theNodes.values())
				validate(element, completed);

			for (StrictXmlReader element : theNodes.values()) {
				try {
					element.check();
				} catch (IllegalArgumentException e) {
					theBuilders.get(element.getAttribute("name", true)).getSession().withError(e.getMessage());
				}
			}
			if (rootName != null) {
				declaredRoot = session.getToolkit().getElement(rootName);
				if (declaredRoot == null) {
					if (session.getToolkit().getAddOn(rootName) == null)
						session.withError("Unrecognized root element: " + rootName);
					else
						session.withError("Root must be an element-def, not " + rootName);
				}
			}
		}

		@Override
		public QonfigElementDef getDeclaredRoot(QonfigParseSession session) {
			return declaredRoot;
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
							path.removeLast();
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
								path.removeLast();
								return null;
							}
						}
					}
					if (el != null)
						builder.inherits(el);
				}
			}
			path.removeLast();
			completed.add(name);
			return builder.get();
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
			for (StrictXmlReader attr : element.getElements("attribute", 0, -1)) {
				String attrName = attr.getAttribute("name", false);
				if (attrName == null) {
					builder.getSession().withError("No name attribute for attribute definition");
					continue;
				}
				if (attrName.lastIndexOf('.') < 0)
					continue; // Simple attribute, already parsed
				QonfigParseSession attrSession = builder.getSession().forChild("attribute", attrName);
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
		}

		private void parseChildren(StrictXmlReader element, QonfigParseSession session, Set<String> completed) {
			QonfigElementOrAddOn.Builder builder = theBuilders.get(element.getAttribute("name", false));
			if (completed.contains(builder.getName()))
				return;
			completed.add(builder.getName());
			if (builder.getSuperElement() != null && builder.getSuperElement().getDeclarer() == session.getToolkit())
				parseChildren(theNodes.get(builder.getSuperElement().getName()), session, completed);
			for (QonfigAddOn inh : builder.getInheritance()) {
				if (inh.getDeclarer() == session.getToolkit())
					parseChildren(theNodes.get(inh.getName()), session, completed);
			}
			builder.setStage(QonfigElementOrAddOn.Builder.Stage.NewChildren);
			for (StrictXmlReader child : element.getElements("child-def", 0, -1)) {
				String name = child.getAttribute("name", false);
				if (name == null) {
					builder.getSession().withError("Unnamed child-def");
					break;
				}
				QonfigParseSession childSession = builder.getSession().forChild("child-def", name);
				if (!checkName(name))
					childSession.withError("Bad child-def name");
				String rolesS = child.getAttribute("role", false);
				Set<QonfigChildDef.Declared> roles;
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
						inherits.add(el);
					}
				}
				if (elType != null)
					builder.withChild(name, elType, roles, inherits, min, max);
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
						inherits.add(el);
					}
				}
				if (overridden != null)
					builder.modifyChild(overridden, elType, inherits, min, max);
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
			BetterHashSet<QonfigChildDef.Declared> found = BetterHashSet.build().unsafe().buildSet();
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
			builder.setStage(QonfigElementOrAddOn.Builder.Stage.Built);
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
