package org.qommons.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jdom2.Attribute;
import org.jdom2.Element;
import org.jdom2.input.SAXBuilder;
import org.jdom2.input.sax.XMLReaderSAX2Factory;

/**
 * <p>
 * A QommonsConfig is a hierarchical configuration structure containing attributes that can be used to setup and determine the behavior of
 * applications.
 * </p>
 * <p>
 * For all of the get functions, the key may be used to navigate beyond the immediate children of the config using a notation similar to
 * XPath. '/' characters may be used to navigate beyond one level deep.
 * </p>
 */
public abstract class QommonsConfig implements Cloneable {
	/** Replaces spaces in XML names */
	protected static final String SPACE_REPLACEMENT = "._sp_.";
	/** Prefix for XML names in which illegal characters have been replaced */
	protected static final String PREFIX = "_.pfx._";
	/** Pattern for finding sequences to replace in {@link #PREFIX}-marked names */
	protected static final Pattern INVALID_REPLACEMENT_PATT = Pattern.compile("\\._ch(?<code>[0-9A-Fa-f]{4})_\\.");
	/** Pattern for replacing sequences with characters that are illegal for XML names */
	protected static final String INVALID_REPLACEMENT_TEXT = "._chXXXX_.";

	/** The default config implementation */
	public static class DefaultConfig extends QommonsConfig {
		private final String theName;

		private final String theValue;
		private final String theUntrimmedValue;

		private QommonsConfig [] theElements;

		/**
		 * Creates a default config
		 *
		 * @param name The name for the config
		 * @param value The value for the config
		 * @param els The child configs
		 */
		public DefaultConfig(String name, String value, QommonsConfig [] els) {
			this(name, value, value, els);
		}

		/**
		 * Creates a default config
		 *
		 * @param name The name for the config
		 * @param value The value for the config
		 * @param untrimmedValue The untrimmed value for the config
		 * @param els The child configs
		 */
		public DefaultConfig(String name, String value, String untrimmedValue, QommonsConfig[] els) {
			theName = name;
			theValue = value;
			theUntrimmedValue = untrimmedValue;
			if(els == null)
				els = new QommonsConfig[0];
			theElements = els;
		}

		@Override
		public String getName() {
			return theName;
		}

		@Override
		public String getValue() {
			return theValue;
		}

		@Override
		public String getValueUntrimmed() {
			return theUntrimmedValue;
		}

		@Override
		public QommonsConfig [] subConfigs() {
			return theElements.clone();
		}

		@Override
		public DefaultConfig clone() {
			DefaultConfig ret = (DefaultConfig) super.clone();
			ret.theElements = new QommonsConfig[theElements.length];
			for(int i = 0; i < theElements.length; i++)
				ret.theElements[i] = theElements[i].clone();
			return ret;
		}
	}

	/** Effectively merges one or more configs to inherit attributes in succession */
	public static class MergedConfig extends QommonsConfig {
		private QommonsConfig [] theMerged;

		/**
		 * Creates a merged config
		 *
		 * @param configs The configurations to merge
		 */
		public MergedConfig(QommonsConfig... configs) {
			if(configs.length == 0)
				throw new IllegalArgumentException("Merged configs must contain at least one config");
			theMerged = configs;
		}

		/** @return The configs that were merged to create this config */
		public QommonsConfig [] getMerged() {
			return theMerged.clone();
		}

		@Override
		public String getName() {
			return theMerged[0].getName();
		}

		@Override
		public String getValue() {
			return theMerged[0].getValue();
		}

		@Override
		public String getValueUntrimmed() {
			return theMerged[0].getValueUntrimmed();
		}

		@Override
		public QommonsConfig [] subConfigs() {
			int size = 0;
			QommonsConfig [] [] unmerged = new QommonsConfig[theMerged.length][];
			for(int m = 0; m < theMerged.length; m++) {
				unmerged[m] = theMerged[m].subConfigs();
				size += unmerged[m].length;
			}
			QommonsConfig [] ret = new QommonsConfig[size];
			size = 0;
			for(int m = 0; m < unmerged.length; m++) {
				System.arraycopy(unmerged[m], 0, ret, size, unmerged[m].length);
				size += unmerged[m].length;
			}
			return ret;
		}

		@Override
		public QommonsConfig subConfig(String type, String... props) {
			QommonsConfig ret = null;
			for(QommonsConfig m : theMerged) {
				ret = m.subConfig(type, props);
				if(ret != null)
					break;
			}
			return ret;
		}

		@Override
		public QommonsConfig [] subConfigs(String type, String... props) {
			int size = 0;
			QommonsConfig [] [] unmerged = new QommonsConfig[theMerged.length][];
			for(int m = 0; m < theMerged.length; m++) {
				unmerged[m] = theMerged[m].subConfigs(type, props);
				size += unmerged[m].length;
			}
			QommonsConfig [] ret = new QommonsConfig[size];
			size = 0;
			for(int m = 0; m < unmerged.length; m++) {
				System.arraycopy(unmerged[m], 0, ret, size, unmerged[m].length);
				size += unmerged[m].length;
			}
			return ret;
		}

		@Override
		public String get(String key) {
			String ret = null;
			for(QommonsConfig m : theMerged) {
				ret = m.get(key);
				if(ret != null)
					break;
			}
			return ret;
		}

		@Override
		public String [] getAll(String key) {
			int size = 0;
			String [] [] unmerged = new String[theMerged.length][];
			for(int m = 0; m < theMerged.length; m++) {
				unmerged[m] = theMerged[m].getAll(key);
				size += unmerged[m].length;
			}
			String [] ret = new String[size];
			size = 0;
			for(int m = 0; m < unmerged.length; m++) {
				System.arraycopy(unmerged[m], 0, ret, size, unmerged[m].length);
				size += unmerged[m].length;
			}
			return ret;
		}

		@Override
		public MergedConfig clone() {
			MergedConfig ret = (MergedConfig) super.clone();
			ret.theMerged = new QommonsConfig[theMerged.length];
			for(int i = 0; i < theMerged.length; i++)
				ret.theMerged[i] = theMerged[i].clone();
			return ret;
		}
	}

	/** @return The name of this configuration */
	public abstract String getName();

	/** @return The base value of this configuration */
	public abstract String getValue();

	/** @return The base value of this configuration, without any white space trimmed from the ends */
	public abstract String getValueUntrimmed();

	/** @return All of this configuration's sub-configurations */
	public abstract QommonsConfig [] subConfigs();

	/**
	 * @param type The type of the config to get
	 * @param props The properties that a config must match to be returned by this method
	 * @return The first configuration that would be returned from {@link #subConfigs(String, String...)}, or null if no such
	 *         sub-configuration exists
	 */
	public QommonsConfig subConfig(String type, String... props) {
		int index = type.indexOf('/');
		if(index >= 0) {
			QommonsConfig pathEl = subConfig(type.substring(0, index));
			type = type.substring(index + 1);
			if(pathEl == null)
				return null;
			return pathEl.subConfig(type);
		}

		for(QommonsConfig config : subConfigs())
			if(config.getName().equals(type)) {
				boolean propMatch = true;
				for(int i = 0; i + 2 <= props.length; i += 2) {
					String value = config.get(props[i]);
					if(value == null ? props[i + 1] != null : !value.equals(props[i + 1])) {
						propMatch = false;
						break;
					}
				}
				if(propMatch)
					return config;
			}
		return null;
	}

	/**
	 * Gets a sub-configurations of a given type and potentially selected by a set of attributes
	 *
	 * @param type The type of the configs to get
	 * @param props The properties (name, value, name, value...) that a config must match to be returned by this method
	 * @return All configuration in this config's children (or descendants) that match the given type and properties
	 */
	public QommonsConfig [] subConfigs(String type, String... props) {
		int index = type.indexOf('/');
		if(index >= 0) {
			QommonsConfig [] pathEls = subConfigs(type.substring(0, index));
			type = type.substring(index + 1);
			QommonsConfig [] [] ret = new QommonsConfig[pathEls.length][];
			for(int p = 0; p < pathEls.length; p++)
				ret[p] = pathEls[p].subConfigs(type, props);
			QommonsConfig [] ret2 = org.qommons.ArrayUtils.mergeInclusive(QommonsConfig.class, ret);
			if(getClass() != QommonsConfig.class) {
				QommonsConfig [] ret3 = createConfigArray(ret2.length);
				System.arraycopy(ret2, 0, ret3, 0, ret2.length);
				ret2 = ret3;
			}
			return ret2;
		}

		java.util.ArrayList<QommonsConfig> ret = new java.util.ArrayList<>();
		for(QommonsConfig config : subConfigs())
			if(config.getName().equals(type)) {
				boolean propMatch = true;
				for(int i = 0; i + 2 <= props.length; i += 2) {
					String value = config.get(props[i]);
					if(value == null ? props[i + 1] != null : !value.equals(props[i + 1])) {
						propMatch = false;
						break;
					}
				}
				if(propMatch)
					ret.add(config);
			}
		return ret.toArray(createConfigArray(ret.size()));
	}

	/**
	 * @param size The size of the array to create
	 * @return A config array whose type is that of this class
	 */
	@SuppressWarnings("static-method")
	protected QommonsConfig [] createConfigArray(int size) {
		return new QommonsConfig[size];
	}

	/**
	 * Gets all attribute values from this configuration (may have been represented by an actual XML attribute or an element in XML) that
	 * match a given name.
	 *
	 * @param key The name of the attribute to get the values of
	 * @return The values of the given attribute in this config
	 */
	public String [] getAll(String key) {
		QommonsConfig [] configs = subConfigs(key);
		String [] ret = new String[configs.length];
		for(int i = 0; i < configs.length; i++)
			ret[i] = configs[i].getValue();
		return ret;
	}

	/**
	 * Gets a single attribute value from this config
	 *
	 * @param key The name of the attribute to get the value of
	 * @return The first value that would be returned from {@link #getAll(String)}, or null if no values would be returned
	 */
	public String get(String key) {
		QommonsConfig config = subConfig(key);
		return config == null ? null : config.getValue();
	}

	/**
	 * Parses an int from an attribute of this config
	 *
	 * @param key The name of the attribute to get the value of
	 * @param def The value to return if the attribute is missing from the config
	 * @return The integer parsed from the given attribute of this config, or the given default value if the attribute is missing
	 */
	public int getInt(String key, int def) {
		String ret = get(key);
		if(ret == null)
			return def;
		try {
			return Integer.parseInt(ret);
		} catch(NumberFormatException e) {
			throw new IllegalArgumentException("Value of property " + key + " (" + ret + ") is not an integer", e);
		}
	}

	/**
	 * Parses a float from an attribute of this config
	 *
	 * @param key The name of the attribute to get the value of
	 * @param def The value to return if the attribute is missing from the config
	 * @return The float parsed from the given attribute of this config, or the given default value if the attribute is missing
	 */
	public float getFloat(String key, float def) {
		String ret = get(key);
		if(ret == null)
			return def;
		try {
			return Float.parseFloat(ret);
		} catch(NumberFormatException e) {
			throw new IllegalArgumentException("Value of property " + key + " (" + ret + ") is not a float", e);
		}
	}

	/**
	 * Parses a double from an attribute of this config
	 *
	 * @param key The name of the attribute to get the value of
	 * @param def The value to return if the attribute is missing from the config
	 * @return The float parsed from the given attribute of this config, or the given default value if the attribute is missing
	 */
	public double getDouble(String key, double def) {
		String ret = get(key);
		if (ret == null)
			return def;
		try {
			return Double.parseDouble(ret);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Value of property " + key + " (" + ret + ") is not a double", e);
		}
	}

	/**
	 * Parses a boolean from an attribute of this config
	 *
	 * @param key The name of the attribute to get the value of
	 * @param def The value to return if the given key is missing from this config
	 * @return The boolean parsed from the given attribute of this config, or <code>def</code> if the attribute is missing
	 */
	public boolean is(String key, boolean def) {
		String ret = get(key);
		if(ret == null)
			return def;
		if("true".equalsIgnoreCase(ret))
			return true;
		else if("false".equalsIgnoreCase(ret))
			return false;
		else
			throw new IllegalArgumentException("Value of property " + key + " (" + ret + ") is not a boolean");
	}

	/**
	 * Parses a time from an attribute of this config. This method uses {@link org.qommons.QommonsUtils#parseEnglishTime(String)} to parse
	 * the time.
	 *
	 * @param key The name of the attribute to get the value of
	 * @return The time parsed from the given attribute of this config, or -1 if the attribute is missing
	 */
	public long getTime(String key) {
		String ret = get(key);
		if(ret == null)
			return -1;
		return org.qommons.QommonsUtils.parseEnglishTime(ret);
	}

	/**
	 * Parses a time interval from an attribute of this config. This method uses {@link org.qommons.QommonsUtils#parseEnglishTime(String)}
	 * to parse the time.
	 *
	 * @param key The name of the attribute to get the value of
	 * @param def The default value to return if the given key does not exist in this config
	 * @return The time interval parsed from the given attribute of this config, or <code>def</code> if the attribute is missing
	 */
	public long getTime(String key, long def) {
		String ret = get(key);
		if(ret == null)
			return def;
		return org.qommons.QommonsUtils.parseEnglishTime(ret);
	}

	/**
	 * Parses a class from an attribute of this config.
	 *
	 * @param <T> The type of the class to return
	 * @param key The name of the attribute to get the value of
	 * @param superClass The super-class to cast the value to
	 * @return The class parsed from the fully-qualified name in the value of the given attribute, or null if the attribute is missing
	 * @throws ClassNotFoundException If the class named in the attribute cannot be found in the classpath
	 * @throws ClassCastException If the class named in the attribute is not a subclass of the given <code>superClass</code>
	 */
	public <T> Class<? extends T> getClass(String key, Class<T> superClass) throws ClassNotFoundException, ClassCastException {
		String className = get(key);
		if(className == null)
			return null;
		Class<?> ret = Class.forName(className);
		if(superClass == null)
			return (Class<? extends T>) ret;
		else
			return ret.asSubclass(superClass);
	}

	@Override
	public int hashCode() {
		String value = getValue();
		if (value == null)
			value = getValueUntrimmed();
		return Objects.hash(getName(), value, subConfigs());
	}

	@Override
	public boolean equals(Object o) {
		if(!(o instanceof QommonsConfig))
			return false;
		QommonsConfig config = (QommonsConfig) o;
		if(!getName().equals(config.getName()))
			return false;
		if(getValue() == null ? config.getValue() != null : !getValue().equals(config.getValue()))
			return false;
		QommonsConfig [] children = subConfigs();
		QommonsConfig [] children2 = config.subConfigs();
		if(children.length != children2.length)
			return false;
		for(int i = 0; i < children.length; i++)
			if(!children[i].equals(children2[i]))
				return false;
		return true;
	}

	@Override
	public QommonsConfig clone() {
		try {
			return (QommonsConfig) super.clone();
		} catch(CloneNotSupportedException e) {
			throw new IllegalStateException(e.toString());
		}
	}

	@Override
	public String toString() {
		StringBuilder ret = new StringBuilder();
		toString(ret, 0);
		return ret.toString();
	}

	/**
	 * Prints this config to a string builder
	 *
	 * @param ret The string builder to write this config to
	 * @param indent The amount to indent the text
	 */
	public void toString(StringBuilder ret, int indent) {
		for(int i = 0; i < indent; i++)
			ret.append('\t');
		ret.append('<').append(getName());
		QommonsConfig [] subConfigs = subConfigs();
		String [] values = new String[subConfigs.length];
		int withChildren = 0;
		boolean hasHadElement = false;
		for(int c = 0; c < subConfigs.length; c++) {
			values[c] = subConfigs[c].getValue();
			if(!hasHadElement && subConfigs[c].subConfigs().length == 0 && (values[c] != null && values[c].length() > 0)) {
				ret.append(' ').append(subConfigs[c].getName()).append('=').append('"');
				ret.append(values[c]).append('"');
				subConfigs[c] = null;
			} else {
				withChildren++;
				hasHadElement = true;
			}
		}
		if(withChildren == 0 && getValue() == null)
			ret.append(' ').append('/').append('>');
		else {
			ret.append('>');
			if(getValue() != null)
				ret.append(getValue());
			if(withChildren > 0)
				ret.append('\n');
			for(int c = 0; c < subConfigs.length; c++) {
				if(subConfigs[c] == null)
					continue;
				subConfigs[c].toString(ret, indent + 1);
				ret.append('\n');
			}
			if(withChildren > 0)
				for(int i = 0; i < indent; i++)
					ret.append('\t');
			ret.append('<').append('/').append(getName()).append('>');
		}
	}

	/**
	 * Parses a config from a pre-parsed or dynamically-generated XML element
	 *
	 * @param xml The XML element to parse the config from
	 * @return The parsed config
	 */
	public static QommonsConfig fromXml(Element xml) {
		QommonsConfig [] fx = fromXml(xml, null);
		if(fx.length == 0)
			return null;
		else if(fx.length == 1)
			return fx[0];
		else
			return create("none", null, fx);
	}

	/**
	 * Parses a config from an XML resource
	 *
	 * @param url The XML resource to parse the config from
	 * @return The parsed config
	 * @throws java.io.IOException If the resource cannot be read or parsed
	 */
	public static QommonsConfig fromXml(java.net.URL url) throws java.io.IOException {
		return fromXml(getRootElement(url));
	}

	/**
	 * Parses a config from XML found at a location
	 *
	 * @param location The location of the XML file
	 * @param relative The locations that the given location might be relative to (see {@link #getRootElement(String, String...)}
	 * @return The config parsed from the XML at the given location
	 * @throws java.io.IOException If the XML cannot be found or parsed into a configuration
	 */
	public static QommonsConfig fromXml(String location, String... relative) throws java.io.IOException {
		Element root = getRootElement(location, relative);
		if(root == null) {
			StringBuilder msg = new StringBuilder();
			msg.append("Could not resolve location: ");
			msg.append(location);
			for(String rel : relative) {
				msg.append('/');
				msg.append(rel);
			}
			throw new java.io.IOException(msg.toString());
		}
		QommonsConfig [] fx = fromXml(root, location, relative);
		if(fx.length == 0)
			return null;
		else if(fx.length == 1)
			return fx[0];
		else
			return create("none", null, fx);
	}

	/**
	 * @param s The string to parse as a URL
	 * @return The URL represented by the string
	 * @throws java.io.IOException If the string does not represent a valid URL
	 */
	public static java.net.URL toUrl(String s) throws java.io.IOException {
		java.net.URL configURL;
		if(s.startsWith("classpath:/")) { // Classpath resource
			configURL = QommonsConfig.class.getResource(s.substring("classpath:/".length()));
			if(configURL == null) {
				throw new java.io.FileNotFoundException("Classpath configuration URL " + s + " refers to a non-existent resource");
			}
			return configURL;
		} else if (s.contains("://"))
			return new java.net.URL(s); // Absolute resource
		else {
			// See if it's a file path
			File file = new File(s);
			if (file.exists())
				return file.toURI().toURL();
			throw new java.io.IOException("Location " + s + " is invalid or unreachable (" + file.getCanonicalPath() + ")");
		}
	}

	/**
	 * Parses the root element from an XML file
	 *
	 * @param url The URL of the XML resource to parse
	 * @return The root element of the XML
	 * @throws java.io.IOException If the XML could not be read or parsed
	 */
	public static Element getRootElement(java.net.URL url) throws java.io.IOException {
		Element configEl;
		try {
			configEl = new org.jdom2.input.SAXBuilder().build(url).getRootElement();
		} catch(org.jdom2.JDOMException e) {
			throw new java.io.IOException("Could not read XML file " + url, e);
		}
		return configEl;
	}

	private static final ThreadLocal<SAXBuilder> SAX_BUILDERS = ThreadLocal
		.withInitial(() -> new SAXBuilder(new XMLReaderSAX2Factory(false)));

	/**
	 * Parses the root element from an XML file
	 *
	 * @param stream The input stream of the XML resource to parse
	 * @return The root element of the XML
	 * @throws java.io.IOException If the XML could not be read or parsed
	 */
	public static Element getRootElement(InputStream stream) throws IOException {
		Element configEl;
		try {
			configEl = SAX_BUILDERS.get().build(stream).getRootElement();
		} catch (org.jdom2.JDOMException e) {
			throw new java.io.IOException("Could not read XML file " + stream, e);
		}
		return configEl;
	}

	/**
	 * Retrieves and parses XML for a given location
	 *
	 * @param location The location of the XML file to parse
	 * @param relative The locations to which the location may be relative
	 * @return The root element of the given XMl file
	 * @throws java.io.IOException If an error occurs finding, reading, or parsing the file
	 */
	public static Element getRootElement(String location, String... relative) throws java.io.IOException {
		String newLocation = resolve(location, relative);
		if(newLocation == null)
			return null;
		return getRootElement(toUrl(newLocation));
	}

	/**
	 * Gets the location of a class to use in resolving relative paths with {@link #getRootElement(String, String...)}
	 *
	 * @param clazz The class to get the location of
	 * @return The location of the class file
	 */
	public static String getLocation(Class<?> clazz) {
		return "classpath://" + clazz.getName().replaceAll("\\.", "/") + ".class";
	}

	/**
	 * @param location The path of the resource to find
	 * @param relative The list of paths that the location is relative to
	 * @return The path that the given resource should be located ad
	 * @throws java.io.IOException If any of the given paths cannot be interpreted
	 */
	public static String resolve(String location, String... relative) throws java.io.IOException {
		if(location.contains(":/"))
			return location;
		else if(relative.length > 0) {
			String resolvedRel = resolve(relative[0], org.qommons.ArrayUtils.remove(relative, 0));
			int protocolIdx = resolvedRel.indexOf(":/");
			if(protocolIdx >= 0) {
				if(location.startsWith("/"))
					return resolvedRel.substring(0, protocolIdx) + ":/" + location;
				String newLocation = location;
				do {
					int lastSlash = resolvedRel.lastIndexOf("/");
					resolvedRel = resolvedRel.substring(0, lastSlash);
					if(newLocation.startsWith("../"))
						newLocation = newLocation.substring(3);
				} while(newLocation.startsWith("../"));
				if(!resolvedRel.contains(":/")) {
					throw new java.io.IOException(
						"Location " + location + " relative to " + org.qommons.ArrayUtils.toString(relative) + " is invalid");
				}
				return resolvedRel + "/" + newLocation;
			} else
				return null;
		} else {
			throw new java.io.IOException("Location " + location + " is invalid");
		}
	}

	private static QommonsConfig [] fromXml(Element xml, String location, String... relative) {
		String value = xml.getText();
		String trimmedValue = value == null ? value : value.trim();
		if (value.length() == 0) {
			value = null;
			trimmedValue = null;
		}
		java.util.List<Element> children = xml.getChildren();
		java.util.List<Attribute> atts = xml.getAttributes();
		int attSize = atts.size();
		if(children.size() == 0 && attSize == 0)
			return new QommonsConfig[] { new DefaultConfig(deXmlIfy(xml.getName()), trimmedValue, value, null) };
		java.util.ArrayList<QommonsConfig> childConfigs = new java.util.ArrayList<>();
		if(attSize > 0)
			for(Attribute att : atts)
				if(!att.getName().equals("if"))
					childConfigs.add(new DefaultConfig(deXmlIfy(att.getName()), att.getValue(), null));
		for(Element child : children)
			for(QommonsConfig toAdd : fromXml(child, location, relative))
				childConfigs.add(toAdd);
		return new QommonsConfig[] {
			create(deXmlIfy(xml.getName()), trimmedValue, value, childConfigs.toArray(new QommonsConfig[childConfigs.size()])) };
	}

	private static String deXmlIfy(String name) {
		if (name.startsWith(PREFIX))
			name = name.substring(PREFIX.length());
		String oldName;
		do {
			oldName = name;
			name = name.replace(SPACE_REPLACEMENT, " ");
		} while (oldName != name);

		Matcher matcher = INVALID_REPLACEMENT_PATT.matcher(name);
		StringBuilder str = null;
		int lastMatchEnd = 0;
		while (matcher.find()) {
			if (str == null)
				str = new StringBuilder();
			str.append(name.substring(lastMatchEnd, matcher.start()));
			int code = Integer.parseInt(matcher.group("code"));
			str.append((char) code);
			lastMatchEnd = matcher.end();
		}
		if (str == null)
			return name;

		str.append(name.substring(lastMatchEnd));
		return str.toString();
	}

	/**
	 * Creates a config
	 *
	 * @param name The name for the config
	 * @param value The base value for the config
	 * @param children The subconfigs for the new config
	 * @return The new config
	 */
	public static QommonsConfig create(String name, String value, QommonsConfig... children) {
		return create(name, value, value, children);
	}

	/**
	 * Creates a config
	 *
	 * @param name The name for the config
	 * @param value The base value for the config
	 * @param untrimmedValue The untrimmed value for the config
	 * @param children The subconfigs for the new config
	 * @return The new config
	 */
	public static QommonsConfig create(String name, String value, String untrimmedValue, QommonsConfig... children) {
		return new DefaultConfig(name, value, untrimmedValue, children);
	}
}
