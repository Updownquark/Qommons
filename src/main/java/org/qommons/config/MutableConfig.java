package org.qommons.config;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Verifier;

/** A modifiable version of QommonsConfig */
public class MutableConfig extends QommonsConfig {
	/** Listens to changes in a configuration */
	public static interface ConfigListener {
		/** @param config The configuration that was added */
		void configAdded(MutableConfig config);

		/** @param config The configuration that was removed */
		void configRemoved(MutableConfig config);

		/**
		 * @param config The configuration whose value was modified
		 * @param previousValue The previous value of the configuration
		 */
		void configChanged(MutableConfig config, String previousValue);
	}

	private MutableConfig theParent;

	private String theName;

	private String theValue;

	private String theUntrimmedValue;

	private MutableConfig [] theSubConfigs;

	private ConfigListener [] theListeners;

	private ConfigListener theSubConfigListener;

	/**
	 * Creates a blank config with just a name
	 *
	 * @param name The name for the configuration
	 */
	public MutableConfig(String name) {
		theName = name;
		theSubConfigs = createConfigArray(0);
		theListeners = new ConfigListener[0];
		theSubConfigListener = new ConfigListener() {
			@Override
			public void configAdded(MutableConfig config) {
				MutableConfig.this.configAdded(config);
			}

			@Override
			public void configRemoved(MutableConfig config) {
				MutableConfig.this.configRemoved(config);
			}

			@Override
			public void configChanged(MutableConfig config, String previousValue) {
				MutableConfig.this.configChanged(config, previousValue);
			}
		};
	}

	/**
	 * Creates a blank config with just a name and a parent
	 *
	 * @param parent The parent for this config
	 * @param name The name for the configuration
	 */
	public MutableConfig(MutableConfig parent, String name) {
		this(name);
		theParent = parent;
	}

	/**
	 * Creates a modifiable version of an existing QommonsConfig
	 *
	 * @param parent The configuration that this config is a sub-config for. Null for a top-level configuration.
	 * @param config The configuration to duplicate as modifiable
	 */
	public MutableConfig(MutableConfig parent, QommonsConfig config) {
		theParent = parent;
		theName = config.getName();
		theValue = config.getValue();
		theUntrimmedValue = config.getValueUntrimmed();
		theListeners = new ConfigListener[0];
		QommonsConfig [] subs = config.subConfigs();
		theSubConfigs = createConfigArray(subs.length);
		for(int i = 0; i < subs.length; i++) {
			theSubConfigs[i] = copy(subs[i]);
			theSubConfigs[i].addListener(theSubConfigListener);
		}
	}

	/** @return The configuration that this config is a sub-config for. Null if this is a top-level configuration. */
	public MutableConfig getParent() {
		return theParent;
	}

	void setParent(MutableConfig config) {
		theParent = config;
	}

	ConfigListener getSubConfigListener() {
		return theSubConfigListener;
	}

	/** @return The path to this configuration from the top-level config, including the top-level config. '/'-separated. */
	public String getPath() {
		java.util.ArrayList<MutableConfig> configPath = new java.util.ArrayList<>();
		MutableConfig config = this;
		while(config != null) {
			configPath.add(config);
			config = config.getParent();
		}
		StringBuilder ret = new StringBuilder();
		for(int i = configPath.size() - 1; i >= 0; i--) {
			ret.append(configPath.get(i).getName());
			if(i > 0)
				ret.append('/');
		}
		return ret.toString();
	}

	@Override
	public String getName() {
		return theName;
	}

	/** @param name The name for this configuration */
	public void setName(String name) {
		theName = name;
	}

	@Override
	public String getValue() {
		return theValue;
	}

	@Override
	public String getValueUntrimmed() {
		return theUntrimmedValue;
	}

	/**
	 * @param key The name of the sub-configuration to store the value in
	 * @param value The value to store for the given sub-configuration
	 * @return This config, for chaining
	 */
	public MutableConfig set(String key, String value) {
		MutableConfig config = getOrCreate(key);
		if (value != null)
			config.setValue(value);
		return this;
	}

	/**
	 * @param value The value for this configuration
	 * @return This config, for chaining
	 */
	public MutableConfig setValue(String value) {
		String preValue = theValue;
		theValue = value == null ? null : value.trim();
		theUntrimmedValue = value;
		configChanged(this, preValue);
		return this;
	}

	@Override
	public MutableConfig [] subConfigs() {
		return theSubConfigs.clone();
	}

	@Override
	public MutableConfig subConfig(String type, String... props) {
		return (MutableConfig) super.subConfig(type, props);
	}

	@Override
	public MutableConfig [] subConfigs(String type, String... props) {
		return (MutableConfig []) super.subConfigs(type, props);
	}

	/**
	 * Retrieves the first sub configuration with the given name, or creates a new sub configuration with the given name if none exists
	 * already
	 *
	 * @param type The name of the configuration to get or create
	 * @param props The properties to match
	 * @return The retrieved or created configuration
	 */
	public MutableConfig getOrCreate(String type, String... props) {
		MutableConfig ret = subConfig(type, props);
		if(ret == null) {
			ret = addChild(type);
			for (int i = 0; i < props.length; i += 2)
				ret.set(props[i], props[i + 1]);
		}
		return ret;
	}

	/**
	 * @param name The name for the new child
	 * @return The new child, already added to this config
	 */
	public MutableConfig addChild(String name) {
		int slashIdx = name.indexOf('/');
		if (slashIdx >= 0)
			return getOrCreate(name.substring(0, slashIdx)).addChild(name.substring(slashIdx + 1));
		MutableConfig ret = createChild(name);
		addSubConfig(ret);
		return ret;
	}

	/**
	 * @param name The name for the new child
	 * @return A new config element to be added as a child to this config
	 */
	@SuppressWarnings("static-method")
	protected MutableConfig createChild(String name) {
		return new MutableConfig(name);
	}

	/**
	 * @param config The config to copy
	 * @return An instance of this class with the same data as <code>config</code>
	 */
	protected MutableConfig copy(QommonsConfig config) {
		return new MutableConfig(this, config);
	}

	@Override
	protected MutableConfig [] createConfigArray(int size) {
		return new MutableConfig[size];
	}

	/** @param subs The sub configurations for this configuration */
	public void setSubConfigs(MutableConfig [] subs) {
		org.qommons.ArrayUtils.adjust(theSubConfigs, subs, new org.qommons.ArrayUtils.DifferenceListener<MutableConfig, MutableConfig>() {
			@Override
			public boolean identity(MutableConfig o1, MutableConfig o2) {
				return o1 == o2;
			}

			@Override
			public MutableConfig added(MutableConfig o, int mIdx, int retIdx) {
				o.setParent(MutableConfig.this);
				o.addListener(getSubConfigListener());
				return null;
			}

			@Override
			public MutableConfig removed(MutableConfig o, int oIdx, int incMod, int retIdx) {
				if(o.getParent() == MutableConfig.this)
					o.setParent(null);
				o.removeListener(getSubConfigListener());
				return null;
			}

			@Override
			public MutableConfig set(MutableConfig o1, int idx1, int incMod, MutableConfig o2, int idx2, int retIdx) {
				return null;
			}
		});
		theSubConfigs = subs;
	}

	/**
	 * @param sub The sub configuration to add to this configuration
	 * @return The added config, for chaining
	 */
	public MutableConfig addSubConfig(MutableConfig sub) {
		theSubConfigs = org.qommons.ArrayUtils.add(theSubConfigs, sub);
		sub.theParent = this;
		sub.addListener(theSubConfigListener);
		return sub;
	}

	/** @param sub The sub configuration to remove from this configuration */
	public void removeSubConfig(MutableConfig sub) {
		theSubConfigs = org.qommons.ArrayUtils.remove(theSubConfigs, sub);
		if(sub.theParent == this)
			sub.theParent = null;
		sub.removeListener(theSubConfigListener);
	}

	/** @param listener The listener to be notified when this configuration or any of its children change */
	public void addListener(ConfigListener listener) {
		if(listener != null)
			theListeners = org.qommons.ArrayUtils.add(theListeners, listener);
	}

	/** @param listener The listener to stop notification for */
	public void removeListener(ConfigListener listener) {
		theListeners = org.qommons.ArrayUtils.remove(theListeners, listener);
	}

	void configAdded(MutableConfig config) {
		for(ConfigListener listener : theListeners)
			listener.configAdded(config);
	}

	void configRemoved(MutableConfig config) {
		for(ConfigListener listener : theListeners)
			listener.configRemoved(config);
	}

	void configChanged(MutableConfig config, String previousValue) {
		for(ConfigListener listener : theListeners)
			listener.configChanged(config, previousValue);
	}

	@Override
	public MutableConfig clone() {
		final MutableConfig ret = (MutableConfig) super.clone();
		ret.theSubConfigs = ret.createConfigArray(theSubConfigs.length);
		ret.theListeners = new ConfigListener[0];
		ret.theSubConfigListener = new ConfigListener() {
			@Override
			public void configAdded(MutableConfig config) {
				ret.configAdded(config);
			}

			@Override
			public void configRemoved(MutableConfig config) {
				ret.configRemoved(config);
			}

			@Override
			public void configChanged(MutableConfig config, String previousValue) {
				ret.configChanged(config, previousValue);
			}
		};
		for(int i = 0; i < theSubConfigs.length; i++) {
			ret.theSubConfigs[i] = theSubConfigs[i].clone();
			ret.theSubConfigs[i].addListener(ret.theSubConfigListener);
		}
		return ret;
	}

	/**
	 * Writes this configuration to an XML element
	 *
	 * @return The XML element representing this configuration
	 */
	public Element toXML() {
		Element ret = new Element(xmlIfy(theName));
		if(theValue != null)
			ret.setText(theValue);
		java.util.HashMap<String, int []> attrs = new java.util.HashMap<>();
		for(MutableConfig sub : theSubConfigs) {
			int [] count = attrs.get(sub.theName);
			if(count == null) {
				count = new int[1];
				attrs.put(sub.theName, count);
			}
			count[0]++;
		}
		for(MutableConfig sub : theSubConfigs) {
			if(attrs.get(sub.theName)[0] == 1 && sub.theSubConfigs.length == 0 && sub.theValue != null && sub.theValue.indexOf('\n') < 0)
				ret.setAttribute(xmlIfy(sub.theName), sub.theValue);
			else
				ret.addContent(sub.toXML());
		}
		return ret;
	}

	private static String xmlIfy(String name) {
		if (Verifier.checkElementName(name) == null)
			return name;
		StringBuilder str = new StringBuilder();
		if (!Character.isLetter(name.charAt(0)) && name.charAt(0) != '_')
			str.append(PREFIX);
		for (int i = 0; i < name.length(); i++) {
			char c = name.charAt(i);
			if (c == ' ')
				str.append(SPACE_REPLACEMENT);
			else if (Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == '.')
				str.append(c);
			else
				str.append(INVALID_REPLACEMENT_TEXT.replace("XXXX", Integer.toHexString(c)));
		}
		return str.toString();
	}

	/**
	 * @param config The configuration to write as XML
	 * @param out The stream to write the configuration to
	 * @throws java.io.IOException If an error occurs writing the XML document
	 */
	public static void writeAsXml(MutableConfig config, java.io.OutputStream out) throws java.io.IOException {
		Element root = config.toXML();
		Document doc = new Document(root);
		org.jdom2.output.Format format = org.jdom2.output.Format.getPrettyFormat();
		format.setIndent("\t");
		new org.jdom2.output.XMLOutputter(format).output(doc, out);
	}
}
