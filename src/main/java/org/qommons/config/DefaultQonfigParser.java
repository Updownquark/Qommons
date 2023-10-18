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
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.qommons.MultiInheritanceMap;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterHashSet;
import org.qommons.collect.BetterSet;
import org.qommons.io.ErrorReporting;
import org.qommons.io.FilePosition;
import org.qommons.io.LocatedFilePosition;
import org.qommons.io.LocatedPositionedContent;
import org.qommons.io.PositionedContent;
import org.qommons.io.SimpleXMLParser;
import org.qommons.io.SimpleXMLParser.XmlParseException;
import org.qommons.io.TextParseException;
import org.w3c.dom.Comment;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.ProcessingInstruction;
import org.w3c.dom.Text;

/** Default {@link QonfigParser} implementation */
public class DefaultQonfigParser implements QonfigParser {
	/** The name of the attribute to use to directly specify inheritance in Qonfig documents */
	public static final String DOC_ELEMENT_INHERITANCE_ATTR = "with-extension";
	/** Names that cannot be used for Qonfig attributes */
	public static final Set<String> RESERVED_ATTR_NAMES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(//
		DOC_ELEMENT_INHERITANCE_ATTR, "role"//
	)));

	private final Map<QonfigToolkit.ToolkitDef, QonfigToolkit> theToolkits;
	private final MultiInheritanceMap<QonfigElementOrAddOn, QonfigPromiseFulfillment> theStitchers;

	/** Creates the parser */
	public DefaultQonfigParser() {
		theToolkits = new HashMap<>();
		theStitchers = MultiInheritanceMap.create(QonfigElementOrAddOn::isAssignableFrom);
	}

	/**
	 * @param toolkitName The toolkit name to check
	 * @param majorVersion The major version of the toolkit
	 * @return Whether this parser has been configured {@link #withToolkit(QonfigToolkit...) with} the given toolkit
	 */
	public boolean usesToolkit(String toolkitName, int majorVersion) {
		return theToolkits.containsKey(new QonfigToolkit.ToolkitDef(toolkitName, majorVersion, 0));
	}

	@Override
	public DefaultQonfigParser withToolkit(QonfigToolkit... toolkits) {
		for (QonfigToolkit toolkit : toolkits) {
			if (toolkit == null)
				continue;
			QonfigToolkit.ToolkitDef def = new QonfigToolkit.ToolkitDef(toolkit);
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
	public DefaultQonfigParser withPromiseFulfillment(QonfigPromiseFulfillment stitcher) {
		QonfigToolkit.ToolkitDef targetTK = stitcher.getToolkit();
		QonfigToolkit toolkit = theToolkits.get(targetTK);
		if (toolkit == null)
			throw new IllegalArgumentException("No such toolkit installed: " + targetTK);
		QonfigElementOrAddOn qonfigType = toolkit.getElementOrAddOn(stitcher.getQonfigType());
		if (qonfigType == null)
			throw new IllegalArgumentException("No such qonfig type found: " + targetTK + "." + stitcher.getQonfigType());
		stitcher.setQonfigType(qonfigType);
		theStitchers.put(qonfigType, stitcher);
		return this;
	}

	@Override
	public QonfigToolkit parseToolkit(URL location, InputStream content, CustomValueType... customValueTypes)
		throws IOException, XmlParseException, QonfigParseException {
		return _parseToolkitXml(location, content, new LinkedList<>(), customValueTypes);
	}

	@Override
	public QonfigDocument parseDocument(boolean partial, String location, InputStream content)
		throws IOException, XmlParseException, QonfigParseException {
		Element root = new SimpleXMLParser().parseDocument(content).getDocumentElement();
		content.close();
		QonfigParseSession session;
		QonfigDocument doc;
		try (StrictXmlReader rootReader = new StrictXmlReader(root)) {
			LinkedList<String> path = new LinkedList<>();
			Map<String, QonfigToolkit> uses = new LinkedHashMap<>();
			for (Map.Entry<String, String> use : rootReader.findAttributes(USES).entrySet()) {
				String refName = use.getKey().substring(USES_PREFIX.length());
				if (refName.isEmpty())
					throw new IllegalArgumentException("Empty toolkit name");
				checkName(refName);
				path.add(refName);
				Matcher match = TOOLKIT_REF.matcher(use.getValue());
				if (!match.matches())
					throw new IllegalArgumentException(
						location + ": Bad toolkit reference.  Expected 'name vM.m' but found " + use.getValue());
				QonfigToolkit.ToolkitDef def = new QonfigToolkit.ToolkitDef(match.group("name"), Integer.parseInt(match.group("major")),
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
				rootDef = rootDef2;
			}
			PositionedContent position = new PositionedContent.Simple(SimpleXMLParser.getNamePosition(root), root.getNodeName());
			QonfigToolkit docToolkit = new QonfigToolkit(location, 1, 0, null, position, getDocumentation(rootReader),
				Collections.unmodifiableMap(uses), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(),
				Collections.emptyList(), new QonfigToolkit.ToolkitBuilder() {
					@Override
					public void parseTypes(QonfigParseSession s) {}

					@Override
					public void fillOutTypes(QonfigParseSession s) {}
				});
			session = QonfigParseSession.forRoot(partial, docToolkit, position);

			doc = new QonfigDocument(location, docToolkit);
			parseDocElement(partial, session, QonfigElement.buildRoot(partial, session, doc, rootDef, getDocumentation(rootReader)),
				rootReader, true, true, el -> true);
		}
		session.throwErrors(location);
		return doc;
	}

	private static final String USES_PREFIX = "xmlns:";
	private static final Pattern USES = Pattern.compile(USES_PREFIX.replace(":", "\\:") + ".*");
	private static final Pattern TOOLKIT_REF = Pattern.compile("(?<name>[^\\s]+)\\s*v?(?<major>\\d+)\\.(?<minor>\\d+)");

	PartialQonfigElement parseDocElement(boolean partial, QonfigParseSession session, QonfigElement.Builder builder, StrictXmlReader el,
		boolean withAttrs, boolean withInheritance, Predicate<StrictXmlReader> childApplies) {
		if (el.getParent() != null) {
			for (Map.Entry<String, String> uses : el.findAttributes(USES).entrySet())
				session.error("xmlns attributes may only be used at the root: '" + uses.getKey() + "'", null);
		}
		if (withAttrs) {
			for (Map.Entry<String, String> attr : el.getAllAttributes().entrySet()) {
				if (USES.matcher(attr.getKey()).matches()) {//
				} else if (DOC_ELEMENT_INHERITANCE_ATTR.equals(attr.getKey())) {
					if (withInheritance)
						parseInheritance(attr.getValue(), el.getAttributeValuePosition(attr.getKey()), session, builder::inherits);
				} else if ("role".equals(attr.getKey())) {//
				} else {
					ElementQualifiedParseItem qAttr = ElementQualifiedParseItem.parse(attr.getKey(), session,
						asContent(el.getAttributeNamePosition(attr.getKey()), attr.getKey()));
					builder.withAttribute(qAttr, new AttParser(attr.getValue(), el.getAttributeValuePosition(attr.getKey())));
				}
			}

			String text;
			try {
				text = el.getTextTrimIfExists();
			} catch (TextParseException e) {
				session.at(el.getTextTrimPosition()).error("Could not parse element text", e);
				text = null;
			}
			if (text != null) {
				if (builder.getType().getValue() != null) {
					try {
						Object value = builder.getType().getValue().getType().parse(text, session.getToolkit(), session);
						builder.withValue(text, value, el.getTextTrimPosition());
					} catch (RuntimeException e) {
						session.at(el.getTextTrimPosition()).error("Could not parse element text: " + text, e);
					}
				} else if (!text.isEmpty())
					session.at(el.getTextTrimPosition()).error("No value expected or accepted: " + text);
			}
		}
		builder.doneWithAttributes();

		for (StrictXmlReader child : el.getElements()) {
			if (!childApplies.test(child))
				continue;
			String roleAttr = child.getAttributeIfExists("role");
			FilePosition namePosition = SimpleXMLParser.getNamePosition(child.getElement());
			QonfigParseSession childSession = session.at(asContent(namePosition, child.getName()));
			QonfigElementDef childType;
			try {
				childType = session.getToolkit().getElement(child.getName());
			} catch (IllegalArgumentException e) {
				childSession.error(e.getMessage(), e);
				continue;
			}
			if (childType == null) {
				childSession.error("No such element-def found: " + child.getName());
				continue;
			}
			List<ElementQualifiedParseItem> roles;
			if (roleAttr == null)
				roles = Collections.emptyList();
			else
				roles = parseRoles(roleAttr, child.getAttributeValuePosition("role"), childSession);

			String descrip=getDocumentation(child);
			if(childType.isPromise()) {
				QonfigPromiseFulfillment stitcher = theStitchers.getAny(childType);
				if (stitcher == null) {
					childSession.error("No promise fulfillment provided for type '" + childType + "'");
					continue;
				}
				PartialQonfigElement promise = parseDocElement(partial, childSession,
					QonfigElement.buildRoot(partial, childSession, builder.getDocument(), childType, descrip), child, true, false,
					__ -> true);
				String inhStr = child.getAttributeIfExists(DOC_ELEMENT_INHERITANCE_ATTR);
				Set<QonfigAddOn> inh;
				if (inhStr == null)
					inh = Collections.emptySet();
				else {
					inh = new LinkedHashSet<>();
					parseInheritance(inhStr, child.getAttributeValuePosition(DOC_ELEMENT_INHERITANCE_ATTR), childSession, inh::add);
				}

				try {
					stitcher.fulfillPromise(promise, builder, roles, inh, this, childSession);
				} catch (IOException | QonfigParseException e) {
					childSession.error("Unable to fulfill promise", e);
				}
			} else {
				builder.withChild(roles, childType, cb -> {
					parseDocElement(partial, childSession, cb, child, true, true, __ -> true);
				}, asContent(namePosition, child.getName()), descrip);
			}
		}
		return builder.build();
	}

	private static void parseInheritance(String inh, PositionedContent content, QonfigParseSession session, Consumer<QonfigAddOn> addOn) {
		splitAttribute(inh, content, session, (inhName, inhPos) -> {
			QonfigAddOn ao;
			try {
				ao = session.getToolkit().getAddOn(inhName);
				if (ao == null) {
					session.at(inhPos).error("No such add-on " + inhName);
					return;
				}
			} catch (IllegalArgumentException e) {
				session.at(inhPos).error(e.getMessage());
				return;
			}
			addOn.accept(ao);
		});
	}

	private static void splitAttribute(String attributeValue, PositionedContent position, QonfigParseSession session,
		BiConsumer<String, PositionedContent> onItem) {
		int start = 0;
		for (int c = 0; c < attributeValue.length(); c++) {
			char ch = attributeValue.charAt(c);
			if (ch == ',' || Character.isWhitespace(ch)) {
				if (c > start || ch == ',') {
					String itemName = attributeValue.substring(start, c);
					PositionedContent itemPos = position.subSequence(start, position.length());
					try {
						onItem.accept(itemName, itemPos);
					} catch (RuntimeException e) {
						session.at(itemPos).error(e.getMessage());
					}
				}
				start = c + 1;
			}
		}
		if (start < attributeValue.length()) {
			String itemName = attributeValue.substring(start);
			PositionedContent itemPos = position.subSequence(start, position.length());
			try {
				onItem.accept(itemName, itemPos);
			} catch (RuntimeException e) {
				session.at(itemPos).error(e.getMessage());
			}
		}
	}

	private static List<ElementQualifiedParseItem> parseRoles(String roleAttr, PositionedContent position, QonfigParseSession session) {
		List<ElementQualifiedParseItem>[] roles = new List[1];
		splitAttribute(roleAttr, position, session, (role, rolePos) -> {
			if (roles[0] == null)
				roles[0] = new ArrayList<>();
			roles[0].add(ElementQualifiedParseItem.parse(role, session.at(rolePos), rolePos));
		});
		if (roles[0] == null)
			return Collections.emptyList();
		((ArrayList<?>) roles[0]).trimToSize();
		return roles[0];
	}

	/**
	 * Finds and parses documentation specified on the given element. Documentation (in this implementation) takes the form of an XML
	 * processing instruction with target "DOC" occurring immediately before the target element (with only comments and whitespace
	 * permissible in between)
	 * 
	 * @param xml The XML element to get documentation for
	 * @return The documentation for the element, if specified
	 */
	protected String getDocumentation(StrictXmlReader xml) {
		Node parentEl = xml.getElement().getParentNode();
		ProcessingInstruction prevPI = null;
		for (int i = 0; i < parentEl.getChildNodes().getLength(); i++) {
			Node child = parentEl.getChildNodes().item(i);
			if (child == xml.getElement())
				break;
			else if (child instanceof ProcessingInstruction)
				prevPI = (ProcessingInstruction) child;
			else if (prevPI != null) {
				if (child instanceof Comment) { //
				} else if (child instanceof Text) {
					if (!isAllWhiteSpace(child.getNodeValue()))
						prevPI = null;
				} else
					prevPI = null;
			}
		}
		if (prevPI == null || !prevPI.getTarget().equals("DOC") || prevPI.getData() == null || prevPI.getData().isEmpty())
			return null;
		String doc = prevPI.getData().trim();
		// Process the documentation
		StringBuilder str = null;
		int lastNonWS = -1;
		for (int i = 0; i < doc.length(); i++) {
			char ch = doc.charAt(i);
			if (Character.isWhitespace(ch)) {} else {
				if (i - lastNonWS > 1) {
					if (str == null)
						str = new StringBuilder().append(doc, 0, lastNonWS + 1);
					str.append(' ');
				}
				lastNonWS = i;
				if (str != null)
					str.append(ch);
			}
		}
		if (str == null)
			return doc;
		return str.toString();
	}

	private static boolean isAllWhiteSpace(String nodeValue) {
		if (nodeValue == null)
			return true;
		for (int c = 0; c < nodeValue.length(); c++) {
			if (!Character.isWhitespace(nodeValue.charAt(c)))
				return false;
		}
		return true;
	}

	private static class AttParser implements QonfigElement.AttributeValue {
		private final String theValue;
		private final PositionedContent thePosition;

		AttParser(String value, PositionedContent position) {
			theValue = value;
			thePosition = position;
		}

		@Override
		public String getText() {
			return theValue;
		}

		@Override
		public PositionedContent getPosition() {
			return thePosition;
		}

		@Override
		public Object parseAttributeValue(QonfigToolkit toolkit, QonfigAttributeDef attribute, ErrorReporting errors) {
			return attribute.getType().parse(theValue, toolkit, errors);
		}

		@Override
		public String toString() {
			return theValue;
		}
	}

	private QonfigToolkit _parseToolkitXml(URL location, InputStream xml, LinkedList<String> path, CustomValueType... customValueTypes)
		throws IOException, XmlParseException, QonfigParseException {
		Element root = new SimpleXMLParser().parseDocument(xml).getDocumentElement();
		xml.close();
		QonfigToolkit.ToolkitDef def;
		QonfigToolkit toolkit;
		try (StrictXmlReader rootReader = new StrictXmlReader(root)) {
			PositionedContent rootNameContent = asContent(rootReader.getNamePosition(), rootReader.getName());
			String name;
			try {
				name = rootReader.getAttribute("name");
			} catch (TextParseException e) {
				throw QonfigParseException.createSimple(new LocatedFilePosition(location.toString(), rootNameContent.getPosition(0)),
					"No 'name' given for toolkit", e);
			}
			if (!TOOLKIT_NAME.matcher(name).matches())
				throw new IllegalArgumentException("Invalid toolkit name: " + name);
			Matcher version;
			try {
				version = TOOLKIT_VERSION.matcher(rootReader.getAttribute("version"));
			} catch (TextParseException e) {
				throw QonfigParseException.createSimple(new LocatedFilePosition(location.toString(), rootNameContent.getPosition(0)),
					"No 'version' given for toolkit", e);
			}
			if (!version.matches())
				throw new IllegalArgumentException("Illegal toolkit version.  Expected 'M.m' but found " + version.group());
			int major = Integer.parseInt(version.group("major"));
			int minor = Integer.parseInt(version.group("minor"));
			def = new QonfigToolkit.ToolkitDef(name, major, minor);
			if (theToolkits.containsKey(def))
				throw new IllegalArgumentException("A toolkit named " + name + " is already registered");
			if (!rootReader.getTagName().equals("qonfig-def"))
				throw new IllegalArgumentException("Expected 'qonfig-def' for root element, not " + root.getNodeName());
			Map<String, QonfigToolkit> dependencies = new LinkedHashMap<>();
			for (Map.Entry<String, String> ext : rootReader.findAttributes(EXTENDS).entrySet()) {
				String refName = ext.getKey().substring(USES_PREFIX.length());
				if (refName.isEmpty())
					throw new IllegalArgumentException(path + ": Empty dependency name");
				checkName(refName);
				path.add(refName);
				Matcher match = TOOLKIT_REF.matcher(ext.getValue());
				if (!match.matches())
					throw new IllegalArgumentException(def + ": Bad toolkit reference.  Expected 'name vM.m' but found " + ext.getValue());
				QonfigToolkit.ToolkitDef depDef = new QonfigToolkit.ToolkitDef(match.group("name"), Integer.parseInt(match.group("major")),
					Integer.parseInt(match.group("minor")));

				QonfigToolkit dep = theToolkits.get(depDef);
				if (dep == null)
					throw new IllegalArgumentException("No such dependency named " + depDef + " registered");
				dependencies.put(refName, dep);
			}
			ToolkitParser parser = new ToolkitParser(location.toString(), rootReader, customValueTypes);
			toolkit = new QonfigToolkit(name, major, minor, location, rootNameContent, getDocumentation(rootReader),
				Collections.unmodifiableMap(dependencies), Collections.unmodifiableMap(parser.getDeclaredTypes()),
				Collections.unmodifiableMap(parser.getDeclaredAddOns()), Collections.unmodifiableMap(parser.getDeclaredElements()),
				Collections.unmodifiableList(parser.getDeclaredAutoInheritance()), parser);
		}
		// TODO Verify
		theToolkits.put(def, toolkit);
		return toolkit;
	}

	private static final Pattern EXTENDS = USES;
	private static final Pattern TOOLKIT_NAME = Pattern.compile("[^\\s]+");
	private static final Pattern TOOLKIT_VERSION = Pattern.compile("(?<major>\\d+)\\.(?<minor>\\d+)");

	static final Set<String> TOOLKIT_EL_NAMES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(//
		"attribute", "child-def", "attr-mod", "child-mod", "value", "value-mod", "element-meta", "element-meta-mod")));

	private class ToolkitParser implements QonfigToolkit.ToolkitBuilder {
		private final String fileLocation;
		private final StrictXmlReader root;
		private final Map<String, QonfigValueType.Declared> declaredTypes;
		private final Map<String, QonfigElementOrAddOn.Builder> theBuilders;

		private final Map<String, QonfigAddOn> declaredAddOns;
		private final Map<String, QonfigElementDef> declaredElements;
		private final List<QonfigAutoInheritance> declaredAutoInheritance;

		private final CustomValueType[] theCustomValueTypes;
		private final Map<String, StrictXmlReader> theNodes;

		ToolkitParser(String fileLocation, StrictXmlReader root, CustomValueType[] customValueTypes) {
			this.fileLocation = fileLocation;
			this.root = root;
			declaredTypes = new LinkedHashMap<>();
			theBuilders = new LinkedHashMap<>();
			declaredAddOns = new LinkedHashMap<>();
			declaredElements = new LinkedHashMap<>();
			declaredAutoInheritance = new ArrayList<>();
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
		public void parseTypes(QonfigParseSession session) throws QonfigParseException {
			// First pass to parse basic definitions
			PositionedContent rootNameContent = asContent(root.getNamePosition(), root.getName());
			StrictXmlReader patterns;
			try {
				patterns = root.getElementIfExists("value-types");
			} catch (TextParseException e) {
				throw QonfigParseException.createSimple(new LocatedFilePosition(fileLocation, rootNameContent.getPosition(0)),
					e.getMessage(), e);
			}
			if (patterns != null) {
				QonfigParseSession patternsSession = session.at(asContent(patterns.getNamePosition(), patterns.getName()));
				for (StrictXmlReader pattern : patterns.getElements()) {
					String name = pattern.getAttributeIfExists("name");
					if (name == null) {
						patternsSession.error("Value type declared with no name");
						continue;
					}
					QonfigParseSession patternSession = patternsSession.at(asContent(pattern.getNamePosition(), pattern.getName()));
					if (declaredTypes.containsKey(name)) {
						patternSession.error("A value type named " + name + " has already been declared");
						continue;
					}
					if (!checkName(name)) {
						patternSession.error("Illegal value type name: " + name);
						continue;
					}
					QonfigValueType type = parsePattern(pattern, patternSession, true);
					if (type instanceof QonfigValueType.Declared)
						declaredTypes.put(name, (QonfigValueType.Declared) type);
					else
						patternSession.error("Illegal value type");
				}
				try {
					patterns.check();
				} catch (TextParseException e) {
					patternsSession.warn(e.getMessage());
				}
			}
			for (CustomValueType cvt : theCustomValueTypes) {
				if (!declaredTypes.containsKey(cvt.getName()))
					session.warn("Custom value type '" + cvt.getName() + "' provided, but not expected by toolkit");
			}
			// Element/add-on parsing is in several stages:
			// Stage 1: Create a builder for each listed element with simple properties, and remove unparseable elements
			// Stage 2: Hook up extensions, checking for recursion
			// Stage 3: Create new attributes and text
			// Stage 4: Override attributes and text
			// Stage 5: Build children
			// Stages 1 and 2 are done here. Stages 3-6 will be done in the fillOutTypes() method
			QonfigParseSession addOnsSession;
			QonfigParseSession elsSession;
			// Stage 1: Create a builder for each listed add-on and element with simple properties and text,
			// and remove unparseable elements
			StrictXmlReader addOnsEl;
			try {
				addOnsEl = root.getElementIfExists("add-ons");
			} catch (TextParseException e) {
				throw QonfigParseException.createSimple(new LocatedFilePosition(fileLocation, rootNameContent.getPosition(0)),
					e.getMessage(), e);
			}
			if (addOnsEl != null) {
				addOnsSession = session.at(asContent(addOnsEl.getNamePosition(), addOnsEl.getName()));
				createElementsOrAddOns(addOnsEl.getElements("add-on"), addOnsSession);
				try {
					addOnsEl.check();
				} catch (TextParseException e) {
					session.at(new PositionedContent.Fixed(e.getPosition())).warn(e.getMessage());
				}
			} else
				addOnsSession = null;
			StrictXmlReader elementsEl;
			try {
				elementsEl = root.getElementIfExists("elements");
			} catch (TextParseException e) {
				throw QonfigParseException.createSimple(new LocatedFilePosition(fileLocation, rootNameContent.getPosition(0)),
					e.getMessage(), e);
			}
			if (elementsEl != null) {
				elsSession = session.at(asContent(elementsEl.getNamePosition(), elementsEl.getName()));
				createElementsOrAddOns(elementsEl.getElements("element-def"), elsSession);
				try {
					elementsEl.check();
				} catch (TextParseException e) {
					session.at(new PositionedContent.Fixed(e.getPosition())).warn(e.getMessage());
				}
			} else
				elsSession = null;
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
				root.getElementIfExists("auto-inheritance");
			} catch (TextParseException e) {
				throw QonfigParseException.createSimple(new LocatedFilePosition(fileLocation, rootNameContent.getPosition(0)),
					e.getMessage(), e);
			}
			try {
				root.check();
			} catch (TextParseException e) {
				session.at(new PositionedContent.Fixed(e.getPosition())).warn(e.getMessage());
			}
		}

		private void createElementsOrAddOns(List<StrictXmlReader> elements, QonfigParseSession session) throws QonfigParseException {
			Iterator<StrictXmlReader> elIter = elements.iterator();
			while (elIter.hasNext()) {
				StrictXmlReader element = elIter.next();
				String name = element.getAttributeIfExists("name");
				QonfigParseSession elSession = session.at(asContent(element.getNamePosition(), element.getName()));
				if (name == null) {
					elSession.error("No name attribute for " + element.getName());
					elIter.remove();
					continue;
				}
				if (!checkName(name))
					elSession.error("Illegal " + element.getName() + " name: " + name);
				QonfigElementOrAddOn.Builder old = theBuilders.get(name);
				if (old != null) {
					elSession.error("Multiple element-defs/add-ons named " + name + " declared");
					return;
				}
				boolean addOn = element.getName().equals("add-on");
				QonfigElementOrAddOn.Builder builder = addOn ? QonfigAddOn.build(name, elSession, getDocumentation(element))
					: QonfigElementDef.build(name, elSession, getDocumentation(element));
				theBuilders.put(name, builder);
				theNodes.put(name, element);
				parseText(element, builder, false);
				String abstS = element.getAttributeIfExists("abstract");
				if (abstS != null) {
					switch (abstS) {
					case "true":
						builder.setAbstract(true);
						break;
					case "false":
						builder.setAbstract(false);
						break;
					default:
						elSession.error("abstract attribute must be \"true\" or \"false\", not \"" + abstS + "\"");
						break;
					}
				}
			}
		}

		QonfigValueType parsePattern(StrictXmlReader pattern, QonfigParseSession session, boolean topLevel) throws QonfigParseException {
			PositionedContent patternContent = asContent(pattern.getNamePosition(), pattern.getName());
			String name;
			try {
				name = topLevel ? pattern.getAttribute("name") : pattern.getAttributeIfExists("name");
			} catch (TextParseException e) {
				throw QonfigParseException.createSimple(new LocatedFilePosition(fileLocation, patternContent.getPosition(0)),
					e.getMessage(), e);
			}
			switch (pattern.getName()) {
			case "string":
				try {
					pattern.check();
				} catch (TextParseException e) {
					session.warn(e.getMessage());
				}
				return QonfigValueType.STRING;
			case "boolean":
				try {
					pattern.check();
				} catch (TextParseException e) {
					session.warn(e.getMessage());
				}
				return QonfigValueType.BOOLEAN;
			case "int":
				try {
					pattern.check();
				} catch (TextParseException e) {
					session.warn(e.getMessage());
				}
				return QonfigValueType.INT;
			case "pattern":
				String text;
				try {
					text = pattern.getTextTrimIfExists();
				} catch (TextParseException e) {
					throw QonfigParseException.createSimple(new LocatedFilePosition(fileLocation, patternContent.getPosition(0)),
						e.getMessage(), e);
				}
				if (text == null) {
					if (name != null) {
						if (declaredTypes.containsKey(name))
							return declaredTypes.get(name);
						QonfigValueType type = session.getToolkit().getAttributeType(name);
						if (type == null)
							session.error("Pattern '" + name + "' not found");
						return type;
					} else {
						session.error("Pattern has no value");
						return new ErrorValueType(session.getToolkit(), name);
					}
				}
				Pattern p;
				try {
					p = Pattern.compile(text);
				} catch (PatternSyntaxException e) {
					session.error("Bad pattern : " + text);
					return new ErrorValueType(session.getToolkit(), name);
				}
				try {
					pattern.check();
				} catch (TextParseException e) {
					session.at(new PositionedContent.Fixed(e.getPosition())).warn(e.getMessage());
				}
				return new QonfigPattern(session.getToolkit(), name, p, patternContent, getDocumentation(pattern));
			case "literal":
				try {
					text = pattern.getTextTrimIfExists();
				} catch (TextParseException e) {
					throw QonfigParseException.createSimple(new LocatedFilePosition(fileLocation, patternContent.getPosition(0)),
						e.getMessage(), e);
				}
				if (text == null) {
					session.error("Literal has no value");
					return new ErrorValueType(session.getToolkit(), name);
				}
				try {
					pattern.check();
				} catch (TextParseException e) {
					session.at(new PositionedContent.Fixed(e.getPosition())).warn(e.getMessage());
				}
				return new QonfigValueType.Literal(session.getToolkit(), text, patternContent, getDocumentation(pattern));
			case "one-of":
				ArrayList<QonfigValueType> components = new ArrayList<>();
				try {
					for (StrictXmlReader compEl : pattern.getElements(1, -1)) {
						QonfigValueType comp = parsePattern(compEl, session.at(asContent(compEl.getNamePosition(), compEl.getName())),
							false);
						if (comp != null)
							components.add(comp);
						compEl.check();
					}
					pattern.check();
				} catch (TextParseException e) {
					session.at(new PositionedContent.Fixed(e.getPosition())).warn(e.getMessage());
				}
				components.trimToSize();
				return new QonfigValueType.OneOf(session.getToolkit(), name, Collections.unmodifiableList(components), patternContent,
					getDocumentation(pattern));
			case "explicit":
				String prefix = pattern.getAttributeIfExists("prefix");
				String suffix = pattern.getAttributeIfExists("suffix");
				QonfigValueType wrapped;
				try {
					wrapped = parsePattern(pattern.getElements(0, 1).get(0), session, false);
				} catch (TextParseException e) {
					throw QonfigParseException.createSimple(new LocatedFilePosition(fileLocation, patternContent.getPosition(0)),
						e.getMessage(), e);
				}
				try {
					pattern.check();
				} catch (TextParseException e) {
					session.warn(e.getMessage());
				}
				return new QonfigValueType.Explicit(session.getToolkit(), name, wrapped, //
					prefix == null ? "" : prefix, suffix == null ? "" : suffix, patternContent, getDocumentation(pattern));
			case "external":
				for (CustomValueType vt : theCustomValueTypes) {
					if (vt.getName().equals(name))
						return new QonfigValueType.Custom(session.getToolkit(), vt, patternContent, getDocumentation(pattern));
				}
				session.error("Expected external value type '" + name + "', but none supplied");
				return new ErrorValueType(session.getToolkit(), name);
			default:
				session.error("Unrecognized pattern specification: " + pattern.getName());
				return null;
			}
		}

		@Override
		public void fillOutTypes(QonfigParseSession session) throws QonfigParseException {
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
				parseChildOverrides(element, session, completed, false);

			// Stage 7: Parse metadata specs
			completed.clear();
			for (StrictXmlReader element : theNodes.values())
				parseChildren(element, session, completed, true);

			// Stage 8: Override metadata
			completed.clear();
			for (StrictXmlReader element : theNodes.values())
				parseChildOverrides(element, session, completed, true);

			// Validate and complete structure compilation
			completed.clear();
			for (StrictXmlReader element : theNodes.values())
				validate(element, completed);

			// Stage 8: Parse auto-inheritance
			StrictXmlReader autoInheritEl;
			try {
				autoInheritEl = root.getElementIfExists("auto-inheritance");
			} catch (TextParseException e) {
				throw QonfigParseException.createSimple(new LocatedFilePosition(fileLocation, root.getNamePosition()), e.getMessage(), e);
			}
			if (autoInheritEl != null) {
				for (StrictXmlReader ai : autoInheritEl.getElements("auto-inherit"))
					parseAutoInheritance(ai, session.at(asContent(ai.getNamePosition(), ai.getName())));
				try {
					autoInheritEl.check();
				} catch (TextParseException e) {
					session.at(new PositionedContent.Fixed(e.getPosition())).warn(e.getMessage());
				}
			}

			// Stage 9: Parse metadata
			for (StrictXmlReader element : theNodes.values())
				parseMetadata(element, session);

			for (StrictXmlReader element : theNodes.values()) {
				try {
					element.check();
				} catch (TextParseException e) {
					theBuilders.get(element.getAttributeIfExists("name")).getSession().at(new PositionedContent.Fixed(e.getPosition()))
						.error(e.getMessage());
				}
			}

			// Finish build
			for (QonfigElementOrAddOn.Builder builder : theBuilders.values())
				builder.build();
		}

		private QonfigElementOrAddOn parseExtensions(StrictXmlReader element, LinkedList<String> path, QonfigParseSession elsSession,
			QonfigParseSession addOnsSession, Set<String> completed) {
			String name = element.getAttributeIfExists("name");
			QonfigElementOrAddOn.Builder builder = theBuilders.get(name);
			boolean addOn = builder instanceof QonfigAddOn.Builder;
			String extendsS = element.getAttributeIfExists(addOn ? "requires" : "extends");
			String inheritsS = element.getAttributeIfExists("inherits");
			if (completed.contains(name))
				return builder.get(); // Already filled out

			if (!addOn) {
				String promiseS = element.getAttributeIfExists("promise");
				if (promiseS == null) {//
				} else if (promiseS.equals("false")) { // OK, but why even bother
				} else if (!promiseS.equals("true"))
					elsSession.at(element.getAttributeValuePosition("promise"))
						.error("Unrecognized 'promise': expected 'true' or 'false', not '" + promiseS + "'");
				else
					((QonfigElementDef.Builder) builder).promise(true);
			}

			if (extendsS == null && (inheritsS == null || inheritsS.isEmpty())) {
				completed.add(name);// No extensions
				return builder.get();
			}
			QonfigParseSession session = (addOn ? addOnsSession : elsSession).at(asContent(element.getNamePosition(), element.getName()));
			if (path.contains(builder.getName())) {
				session.error("Circular element inheritance detected: " + path);
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
								session.error("No such element found: " + extendsS);
						} catch (IllegalArgumentException e) {
							session.error("For extension " + extendsS + ": " + e.getMessage());
						}
					} else {
						QonfigElementOrAddOn.Builder extBuilder = theBuilders.get(extendsS);
						if (extBuilder == null) {
							el = session.getToolkit().getElement(extendsS);
							if (el == null)
								session.error("No such element-def found: " + extendsS);
						} else if (!(extBuilder instanceof QonfigElementDef.Builder)) {
							session.error(extendsS + " must refer to an element-def");
						} else {
							el = (QonfigElementDef) parseExtensions(theNodes.get(extendsS), path, elsSession, addOnsSession, completed);
							if (el == null) {
								session.error("Circular element inheritance detected: " + path);
								return null;
							}
						}
					}
					if (el != null)
						builder.setSuperElement(el);
				}
				if (inheritsS != null && !inheritsS.isEmpty()) {
					boolean[] circularInheritance = new boolean[1];
					splitAttribute(inheritsS, element.getAttributeValuePosition("inherits"), session, (inh, inhPos) -> {
						int colon = inh.indexOf(':');
						QonfigAddOn el = null;
						if (colon >= 0) {
							try {
								el = session.getToolkit().getAddOn(inh);
								if (el == null)
									session.at(inhPos).error("No such add-on found: " + inh);
							} catch (IllegalArgumentException e) {
								session.at(inhPos).error("For extension " + inh + ": " + e.getMessage());
							}
						} else {
							QonfigElementOrAddOn.Builder extBuilder = theBuilders.get(inh);
							if (extBuilder == null) {
								el = session.getToolkit().getAddOn(inh);
								if (el == null)
									session.at(inhPos).error("No such add-on found: " + inh);
							} else if (!(extBuilder instanceof QonfigAddOn.Builder)) {
								session.at(inhPos).error(inheritsS + " must refer to add-ons");
							} else {
								// TODO This throws an error in the case of an element-def specifying as inheritance
								// an add-on that requires it
								el = (QonfigAddOn) parseExtensions(theNodes.get(inh), path, elsSession, addOnsSession, completed);
								if (el == null) {
									session.at(inhPos).error("Circular inheritance detected: " + path);
									circularInheritance[0] = true;
									return;
								}
							}
						}
						if (el != null)
							builder.inherits(el);
					});
					if (circularInheritance[0])
						return null;
				}
				return builder.get();
			} finally {
				path.removeLast();
				completed.add(name); // Mark complete even if there are errors, so the user can see all the errors at the end
			}
		}

		private void parseSimpleAttributes(StrictXmlReader element, QonfigParseSession session, Set<String> completed) {
			QonfigElementOrAddOn.Builder builder = theBuilders.get(element.getAttributeIfExists("name"));
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
			for (StrictXmlReader attr : element.getElements("attribute")) {
				String attrName = attr.getAttributeIfExists("name");
				if (attrName == null) {
					builder.getSession().error("No name attribute for attribute definition");
					continue;
				}
				if (attrName.lastIndexOf('.') >= 0)
					continue; // Attribute override, not at this stage
				if (attrName.indexOf(':') >= 0) {
					builder.getSession().error("No such attribute '" + attrName + "'--use '.' instead of ':'");
					continue;
				} else if (RESERVED_ATTR_NAMES.contains(attrName)) {
					builder.getSession().error("Attribute name '" + attrName + "' is reserved");
					continue;
				}
				QonfigParseSession attrSession = builder.getSession().at(asContent(attr.getNamePosition(), attr.getName()));
				if (!checkName(attrName))
					attrSession.error("Illegal attribute name");
				String typeName = attr.getAttributeIfExists("type");
				QonfigValueType type;
				if (typeName == null) {
					attrSession.error("No type specified");
					type = null;
				} else
					type = parseAttributeType(attrSession.at(attr.getAttributeValuePosition("type")), typeName, true);
				String specifyS = attr.getAttributeIfExists("specify");
				PositionedContent defaultS = attr.getAttributeValuePosition("default");
				SpecificationType spec;
				if (specifyS != null)
					spec = SpecificationType.fromAttributeValue(specifyS, attrSession);
				else if (defaultS != null)
					spec = SpecificationType.Optional;
				else
					spec = SpecificationType.Required;
				Object defaultV = null;
				if (defaultS != null && type != null)
					defaultV = type.parse(defaultS.toString(), attrSession.getToolkit(), attrSession);
				if (type != null)
					builder.withAttribute(attrName, type, spec, defaultV, LocatedPositionedContent.of(fileLocation, defaultS),
						asContent(attr.getNamePosition(), attr.getName()), getDocumentation(attr));
				try {
					attr.check();
				} catch (TextParseException e) {
					attrSession.warn(e.getMessage());
				}
			}
		}

		private void parseText(StrictXmlReader element, QonfigElementOrAddOn.Builder builder, boolean modify) throws QonfigParseException {
			StrictXmlReader text;
			try {
				text = element.getElementIfExists(modify ? "value-mod" : "value");
			} catch (TextParseException e) {
				throw QonfigParseException.createSimple(new LocatedFilePosition(fileLocation, root.getNamePosition()), e.getMessage(), e);
			}
			if (text == null)
				return;
			QonfigParseSession textSession = builder.getSession().at(asContent(element.getNamePosition(), element.getName()));
			String typeName = text.getAttributeIfExists("type");
			QonfigValueType type;
			if (typeName == null) {
				type = null;
			} else
				type = parseAttributeType(textSession, typeName, false);
			String specifyS = text.getAttributeIfExists("specify");
			PositionedContent defaultS = text.getAttributeValuePosition("default");
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
					textSession.error("No type specified");
					parseType = QonfigValueType.STRING;
				}
				defaultV = parseType.parse(defaultS.toString(), builder.getSession().getToolkit(), textSession);
			}
			if (modify)
				builder.modifyValue(type, spec, defaultV, LocatedPositionedContent.of(fileLocation, defaultS),
					asContent(text.getNamePosition(), text.getName()), getDocumentation(text));
			else
				builder.withValue(type, spec, defaultV, LocatedPositionedContent.of(fileLocation, defaultS),
					asContent(text.getNamePosition(), text.getName()), getDocumentation(text));
			try {
				text.check();
			} catch (TextParseException e) {
				textSession.warn(e.getMessage());
			}
		}

		private void overrideAttributes(StrictXmlReader element, QonfigParseSession session, Set<String> completed)
			throws QonfigParseException {
			QonfigElementOrAddOn.Builder builder = theBuilders.get(element.getAttributeIfExists("name"));
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
			for (StrictXmlReader attr : element.getElements("attr-mod")) {
				String attrName = attr.getAttributeIfExists("name");
				if (attrName == null) {
					builder.getSession().error("No name attribute for attribute definition");
					continue;
				}
				QonfigParseSession attrSession = builder.getSession().at(asContent(attr.getNamePosition(), attr.getName()));
				if (attrName.lastIndexOf('.') < 0) {
					attrSession.at(attr.getAttributeValuePosition("name")).error("Attribute modification must specify 'element.name'");
					continue;
				}
				ElementQualifiedParseItem qAttr;
				qAttr = ElementQualifiedParseItem.parse(attrName, attrSession, attr.getAttributeValuePosition("name"));
				if (qAttr == null)
					continue;
				QonfigElementOrAddOn qualifier = qAttr.declaredElement != null ? qAttr.declaredElement : builder.get();
				QonfigAttributeDef overridden = qualifier.getDeclaredAttributes().get(qAttr.itemName);
				if (overridden == null) {
					switch (qualifier.getAttributesByName().get(qAttr.itemName).size()) {
					case 0:
						attrSession.error("No such attribute found");
						return;
					case 1:
						overridden = qualifier.getAttributesByName().get(qAttr.itemName).getFirst();
						break;
					default:
						attrSession.error("Multiple matching attributes found");
						return;
					}
				}
				String typeName = attr.getAttributeIfExists("type");
				QonfigValueType type;
				if (typeName == null) {
					type = null;
				} else
					type = parseAttributeType(attrSession, typeName, true);
				String specifyS = attr.getAttributeIfExists("specify");
				PositionedContent defaultS = attr.getAttributeValuePosition("default");
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
					defaultV = type2.parse(defaultS.toString(), attrSession.getToolkit(), attrSession);
					if (defaultV == null)
						builder.getSession()
							.error("Default value '" + defaultS + "' parsed to null by attribute type " + type2 + "--this is not allowed");
				}
				if (overridden != null)
					builder.modifyAttribute(overridden, type, spec, defaultV, LocatedPositionedContent.of(fileLocation, defaultS),
						asContent(attr.getNamePosition(), attr.getName()), getDocumentation(attr));
				try {
					attr.check();
				} catch (TextParseException e) {
					attrSession.warn(e.getMessage(), e);
				}
			}
			parseText(element, builder, true);
		}

		private void parseChildren(StrictXmlReader element, QonfigParseSession session, Set<String> completed, boolean metadata) {
			QonfigElementOrAddOn.Builder builder = theBuilders.get(element.getAttributeIfExists("name"));
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
			for (StrictXmlReader child : element.getElements(elementName)) {
				String name = child.getAttributeIfExists("name");
				if (name == null) {
					builder.getSession().error("Unnamed " + elementName);
					break;
				}
				QonfigParseSession childSession = builder.getSession().at(asContent(child.getNamePosition(), child.getName()));
				if (!checkName(name))
					childSession.at(child.getAttributeValuePosition("name")).error("Bad " + elementName + " name");
				Set<QonfigChildDef.Declared> roles;
				String rolesS = child.getAttributeIfExists("role");
				if (rolesS != null && metadata) {
					childSession.at(asContent(child.getAttributeNamePosition("role"), "role"))
						.error("role cannot be specified for " + elementName);
					rolesS = null;
				}
				if (rolesS != null) {
					List<ElementQualifiedParseItem> parsedRoles = parseRoles(rolesS, child.getAttributeValuePosition("role"), childSession);
					roles = new LinkedHashSet<>(parsedRoles.size() * 3 / 2);
					for (ElementQualifiedParseItem parsedRole : parsedRoles) {
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
								childSession.at(parsedRole.position)
									.error("No such role '" + parsedRole.itemName + "' on element " + qualifier);
								break;
							case 1:
								role = children.getFirst().getDeclared();
								break;
							default:
								childSession.at(parsedRole.position)
									.error("Multiple roles named '" + parsedRole.itemName + "' on element " + qualifier);
								break;
							}
						}
						if (role != null)
							roles.add(role);
					}
				} else
					roles = Collections.emptySet();
				String typeName = child.getAttributeIfExists("type");
				QonfigElementOrAddOn elType;
				if (typeName == null) {
					// Allow a child with no type specified
					// childSession.error("No type specified");
					elType = null;
				} else {
					try {
						elType = builder.getSession().getToolkit().getElementOrAddOn(typeName);
						if (elType == null)
							childSession.at(child.getAttributeValuePosition("type")).error("Unrecognized element-def: '" + typeName + "'");
					} catch (IllegalArgumentException e) {
						childSession.error(e.getMessage());
						elType = null;
					}
				}
				String minS = child.getAttributeIfExists("min");
				String maxS = child.getAttributeIfExists("max");
				int min, max;
				try {
					min = minS == null ? 1 : Integer.parseInt(minS);
				} catch (NumberFormatException e) {
					childSession.at(child.getAttributeValuePosition("min")).error("Bad \"min\" value \"" + minS + "\"", e);
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
					childSession.at(child.getAttributeValuePosition("max")).error("Bad \"max\" value \"" + maxS + "\"", e);
					max = Integer.MAX_VALUE;
				}
				String inheritsS = child.getAttributeIfExists("inherits");
				Set<QonfigAddOn> inherits = new LinkedHashSet<>();
				if (inheritsS != null) {
					splitAttribute(inheritsS, child.getAttributeValuePosition("inherits"), session, (inherit, pos) -> {
						QonfigAddOn el;
						try {
							el = builder.getSession().getToolkit().getAddOn(inherit);
						} catch (IllegalArgumentException e) {
							childSession.at(pos).error(e.getMessage());
							return;
						}
						if (el == null)
							childSession.at(pos).error("Unrecognized add-on \"" + inherit + "\" as inheritance");
						else
							inherits.add(el);
					});
				}
				Set<QonfigAddOn> requires = new LinkedHashSet<>();
				String requiresS = child.getAttributeIfExists("requires");
				if (requiresS != null) {
					splitAttribute(requiresS, child.getAttributeValuePosition("requires"), session, (require, pos) -> {
						if (require.isEmpty())
							return;
						QonfigAddOn el;
						try {
							el = builder.getSession().getToolkit().getAddOn(require);
						} catch (IllegalArgumentException e) {
							childSession.at(pos).error(e.getMessage());
							return;
						}
						if (el == null)
							childSession.at(pos).error("Unrecognized add-on \"" + require + "\" as requirement");
						else
							requires.add(el);
					});
				}
				// if (elType != null) { Allow child with no type specified
				if (metadata)
					builder.withMetaSpec(name, elType, inherits, requires, min, max, asContent(child.getNamePosition(), child.getName()),
						getDocumentation(child));
				else
					builder.withChild(name, elType, roles, inherits, requires, min, max,
						asContent(child.getNamePosition(), child.getName()), getDocumentation(child));
				// }
				try {
					child.check();
				} catch (TextParseException e) {
					childSession.at(new PositionedContent.Fixed(e.getPosition())).error(e.getMessage());
				}
			}
		}

		private void parseChildOverrides(StrictXmlReader element, QonfigParseSession session, Set<String> completed, boolean metadata) {
			QonfigElementOrAddOn.Builder builder = theBuilders.get(element.getAttributeIfExists("name"));
			if (completed.contains(builder.getName()))
				return;
			completed.add(builder.getName());
			if (builder.getSuperElement() != null && builder.getSuperElement().getDeclarer() == session.getToolkit())
				parseChildOverrides(theNodes.get(builder.getSuperElement().getName()), session, completed, metadata);
			for (QonfigAddOn inh : builder.getInheritance()) {
				if (inh.getDeclarer() == session.getToolkit())
					parseChildOverrides(theNodes.get(inh.getName()), session, completed, metadata);
			}
			builder
				.setStage(metadata ? QonfigElementOrAddOn.Builder.Stage.ModifyMetaSpec : QonfigElementOrAddOn.Builder.Stage.ModifyChildren);
			String elementName = metadata ? "element-meta-mod" : "child-mod";
			String childAttrName = metadata ? "meta" : "child";
			childLoop: //
			for (StrictXmlReader child : element.getElements(elementName)) {
				String name = child.getAttributeIfExists(childAttrName);
				QonfigParseSession childSession = builder.getSession().at(asContent(child.getNamePosition(), child.getName()));
				if (name == null) {
					if (child.getAttributeIfExists("name") != null)
						childSession.error("Use '" + childAttrName + "=' instead of 'name='");
					else
						childSession.error("No " + childAttrName + " attribute");
					break;
				}
				QonfigChildDef.Declared overridden;
				{
					ElementQualifiedParseItem parsedRole = ElementQualifiedParseItem.parse(name, builder.getSession(),
						child.getAttributeValuePosition(childAttrName));
					if (parsedRole == null)
						continue;
					QonfigElementOrAddOn qualifier;
					if (parsedRole.declaredElement != null)
						qualifier = parsedRole.declaredElement;
					else {
						qualifier = builder.getSuperElement();
						if (qualifier == null) {
							childSession.error("No " + (builder instanceof QonfigElementDef.Builder ? "extends" : "requires")
								+ ", so no children to modify");
							continue childLoop;
						}
					}
					if (metadata)
						overridden = qualifier.getMetaSpec().getDeclaredChildren().get(parsedRole.itemName);
					else
						overridden = qualifier.getDeclaredChildren().get(parsedRole.itemName);
					if (overridden == null) {
						// Can't use getChildrenByName() here because it hasn't been populated at this build stage
						BetterCollection<? extends QonfigChildDef> allOverridden = findChildren(
							metadata ? qualifier.getMetaSpec() : qualifier, parsedRole.itemName, session.getToolkit());
						switch (allOverridden.size()) {
						case 0:
							childSession.error("No such " + childAttrName + " '" + parsedRole.itemName + "' on element " + qualifier);
							break;
						case 1:
							overridden = allOverridden.getFirst().getDeclared();
							break;
						default:
							childSession.error("Multiple " + (metadata ? "metas" : "children") + " named '" + parsedRole.itemName
								+ "' on element " + qualifier);
							break;
						}
					}
				}
				String typeName = child.getAttributeIfExists("type");
				QonfigElementDef elType;
				if (typeName == null) {
					elType = null;
				} else {
					try {
						elType = builder.getSession().getToolkit().getElement(typeName);
						if (elType == null)
							childSession.error("Unrecognized element-def: '" + typeName + "'");
					} catch (IllegalArgumentException e) {
						childSession.error(e.getMessage());
						elType = null;
					}
				}
				String minS = child.getAttributeIfExists("min");
				String maxS = child.getAttributeIfExists("max");
				Integer min, max;
				try {
					min = minS == null ? null : Integer.parseInt(minS);
				} catch (NumberFormatException e) {
					childSession.error("Bad \"min\" value \"" + minS + "\"", e);
					min = null;
				}
				if (maxS == null)
					max = null;
				else if (maxS.equals("inf"))
					max = Integer.MAX_VALUE;
				else {
					try {
						max = Integer.parseInt(maxS);
					} catch (NumberFormatException e) {
						childSession.error("Bad \"max\" value \"" + maxS + "\"", e);
						max = null;
					}
				}
				String inheritsS = child.getAttributeIfExists("inherits");
				Set<QonfigAddOn> inherits = new LinkedHashSet<>();
				if (inheritsS != null) {
					splitAttribute(inheritsS, child.getAttributeValuePosition("inherits"), session, (inherit, inhPos) -> {
						if (inherit.isEmpty())
							return;
						QonfigAddOn el;
						try {
							el = builder.getSession().getToolkit().getAddOn(inherit);
						} catch (IllegalArgumentException e) {
							childSession.at(inhPos).error(e.getMessage());
							return;
						}
						if (el == null)
							childSession.at(inhPos).error("Unrecognized add-on \"" + inherit + "\" as inheritance");
						else
							inherits.add(el);
					});
				}
				String requiresS = child.getAttributeIfExists("requires");
				Set<QonfigAddOn> requires = new LinkedHashSet<>();
				if (requiresS != null) {
					splitAttribute(requiresS, child.getAttributeValuePosition("requires"), session, (require, reqPos) -> {
						if (require.isEmpty())
							return;
						QonfigAddOn el;
						try {
							el = builder.getSession().getToolkit().getAddOn(require);
						} catch (IllegalArgumentException e) {
							childSession.at(reqPos).error(e.getMessage());
							return;
						}
						if (el == null)
							childSession.at(reqPos).error("Unrecognized add-on \"" + require + "\" as requirement");
						else
							requires.add(el);
					});
				}
				if (overridden != null) {
					if (metadata)
						builder.getMetaSpec().modifyChild(overridden, elType, inherits, requires, min, max,
							asContent(child.getNamePosition(), child.getName()), getDocumentation(child));
					else
						builder.modifyChild(overridden, elType, inherits, requires, min, max,
							asContent(child.getNamePosition(), child.getName()), getDocumentation(child));
				}
				try {
					child.check();
				} catch (TextParseException e) {
					childSession.error(e.getMessage());
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
			String inhStr = element.getAttributeIfExists("inherits");
			if (inhStr == null || inhStr.isEmpty()) {
				session.error("No inheritance");
				return;
			}
			splitAttribute(inhStr, element.getAttributeValuePosition("inherits"), session, (inh, inhPos) -> {
				QonfigAddOn addOn = session.getToolkit().getAddOn(inh);
				if (addOn == null)
					session.at(inhPos).error("No such add-on found: " + inh);
				else
					builder.inherits(addOn);
			});
			List<StrictXmlReader> targets = element.getElements();
			if (targets.isEmpty()) {
				session.error("No targets");
				return;
			}
			for (StrictXmlReader target : targets) {
				QonfigParseSession targetSession = session.at(asContent(target.getNamePosition(), target.getName()));
				String typeStr = target.getAttributeIfExists("element");
				QonfigElementOrAddOn type;
				if (typeStr == null)
					type = null;
				else {
					type = targetSession.getToolkit().getElementOrAddOn(typeStr);
					if (type == null) {
						targetSession.error("No such element or add-on found: " + typeStr);
						continue;
					}
				}
				String roleStr = target.getAttributeIfExists("role");
				QonfigChildDef child;
				if (roleStr == null)
					child = null;
				else {
					int dot = roleStr.indexOf('.');
					if (dot < 0) {
						targetSession.error("role name must be qualified with the element or add-on type that declares it");
						continue;
					}
					QonfigElementOrAddOn owner = session.getToolkit().getElementOrAddOn(roleStr.substring(0, dot));
					if (owner == null) {
						targetSession.error("No such element or add-on found: " + roleStr.substring(0, dot));
						continue;
					}
					child = owner.getChild(roleStr.substring(dot + 1));
					if (child == null)
						child = owner.getMetaSpec().getChild(roleStr.substring(dot + 1));
					if (child == null) {
						targetSession.error("No such child '" + roleStr.substring(dot + 1) + "' of " + owner);
						continue;
					}
				}
				builder.withTarget(type, child, asContent(target.getNamePosition(), target.getName()));
				try {
					target.check();
				} catch (TextParseException e) {
					targetSession.error(e.getMessage(), e);
				}
			}
			declaredAutoInheritance.add(builder.build());
			try {
				element.check();
			} catch (TextParseException e) {
				session.error(e.getMessage(), e);
			}
		}

		private void parseMetadata(StrictXmlReader element, QonfigParseSession session) throws QonfigParseException {
			QonfigElementOrAddOn.Builder builder;
			try {
				builder = theBuilders.get(element.getAttribute("name"));
			} catch (TextParseException e) {
				throw QonfigParseException.createSimple(new LocatedFilePosition(fileLocation, root.getNamePosition()), e.getMessage(), e);
			}
			builder.withMetaData(md -> {
				parseDocElement(false, session, md, element, false, true, child -> !TOOLKIT_EL_NAMES.contains(child.getName()));
			});
		}

		private void validate(StrictXmlReader element, Set<String> completed) throws QonfigParseException {
			QonfigElementOrAddOn.Builder builder;
			try {
				builder = theBuilders.get(element.getAttribute("name"));
			} catch (TextParseException e) {
				throw QonfigParseException.createSimple(new LocatedFilePosition(fileLocation, root.getNamePosition()), e.getMessage(), e);
			}
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
		public Object parse(String value, QonfigToolkit tk, ErrorReporting session) {
			return value;
		}

		@Override
		public boolean isInstance(Object value) {
			return false;
		}

		@Override
		public PositionedContent getFilePosition() {
			return null;
		}

		@Override
		public String getDescription() {
			return "This type is only present when an error occurs parsing the actual type";
		}
	}

	static QonfigValueType parseAttributeType(QonfigParseSession session, String typeName, boolean withElement) {
		switch (typeName) {
		case "string":
			return QonfigValueType.STRING;
		case "boolean":
			return QonfigValueType.BOOLEAN;
		case "int":
			return QonfigValueType.INT;
		}
		QonfigValueType.Declared type = session.getToolkit().getAttributeType(typeName);
		if (type != null)
			return type;
		QonfigAddOn addOn = session.getToolkit().getAddOn(typeName);
		if (addOn != null) {
			if (withElement)
				return addOn;
			else {
				session.error("Add-on type " + typeName + " cannot be used here");
				return QonfigValueType.STRING;
			}
		}
		session.error("Unrecognized value type '" + typeName + "'", null);
		return QonfigValueType.STRING;
	}

	static PositionedContent asContent(FilePosition start, String content) {
		return new PositionedContent.Simple(start, content);
	}

	private static boolean checkName(String name) {
		if (DOC_ELEMENT_INHERITANCE_ATTR.equals(name))
			return false;
		return Verifier.checkAttributeName(name) == null;
	}
}
