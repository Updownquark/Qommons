package org.qommons.config;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.function.Consumer;

import org.qommons.QommonsUtils;
import org.qommons.io.ErrorReporting;
import org.qommons.io.LocatedFilePosition;
import org.qommons.io.MinML.XmlParseException;
import org.qommons.io.TextParseException;

/** Provides a utility method for interpreting an application from a setup XML file formatted as qonfig-app.qtd */
public class QonfigApp {
	private static QonfigToolkit QONFIG_APP_TOOLKIT;

	/** @return The Qonfig-App toolkit */
	public static synchronized QonfigToolkit getQonfigAppToolkit() {
		if (QONFIG_APP_TOOLKIT != null)
			return QONFIG_APP_TOOLKIT;
		URL qonfigAppTKUrl = QonfigApp.class.getResource("qonfig-app.qtd");
		if (qonfigAppTKUrl == null)
			throw new IllegalStateException("App toolkit 'qonfig-app.qtd' is missing");
		// Parse the app definition
		DefaultQonfigParser qonfigParser = new DefaultQonfigParser();
		InputStream aTKIn;
		try {
			aTKIn = qonfigAppTKUrl.openStream();
		} catch (NullPointerException e) {
			throw new IllegalStateException("Could not locate app toolkit definition '" + qonfigAppTKUrl.getPath() + "'");
		} catch (IOException e) {
			throw new IllegalStateException("Could not locate toolkit definition '" + qonfigAppTKUrl.getPath() + "'", e);
		}
		try {
			QONFIG_APP_TOOLKIT = qonfigParser.parseToolkit(qonfigAppTKUrl, aTKIn, null);
			aTKIn.close();
		} catch (IOException e) {
			throw new IllegalStateException("Could not read app toolkit definition '" + qonfigAppTKUrl.getPath() + "'", e);
		} catch (TextParseException e) {
			throw new IllegalArgumentException("Could not parse toolkit definition XML '" + qonfigAppTKUrl.getPath() + "'", e);
		} catch (QonfigParseException e) {
			throw new IllegalStateException("Could not parse app toolkit definition '" + qonfigAppTKUrl.getPath() + "'", e);
		} finally {
		}
		return QONFIG_APP_TOOLKIT;
	}

	/**
	 * @param appDefUrl The location of the {@link #getQonfigAppToolkit() Qonfig-App}-formatted application to parse
	 * @param appToolkits The locations of other toolkit definitions that may be needed to parse the application
	 * @return The parsed application
	 * @throws IOException If the application could not be read
	 * @throws TextParseException If the application could not be parsed as XML
	 * @throws QonfigParseException If the application could not be parsed as Qonfig
	 * @throws IllegalStateException If a referenced resource, like a toolkit, cannot be resolved
	 */
	public static QonfigApp parseApp(URL appDefUrl, URL... appToolkits)
		throws IOException, TextParseException, QonfigParseException, IllegalStateException {
		Builder builder = build();
		QonfigToolkit qonfigAppTK = getQonfigAppToolkit();
		builder.withToolkit(qonfigAppTK);

		for (URL appToolkit : appToolkits) {
			try {
				builder.withToolkit(appToolkit);
			} catch (IOException e) {
				throw new IOException("Could not read app toolkit definition '" + appToolkit.getPath() + "'", e);
			} catch (XmlParseException e) {
				throw new TextParseException("Could not parse toolkit definition XML '" + appToolkit.getPath() + "'", e.getPosition(), e);
			}
		}

		QonfigDocument appDef;
		try (InputStream appDefIn = appDefUrl.openStream()) {
			appDef = builder.getParser().parseDocument(false, appDefUrl.toString(), appDefIn);
		} catch (IOException e) {
			throw new IOException("Could not read Qonfig-App definition: " + appDefUrl, e);
		} catch (XmlParseException e) {
			throw new TextParseException("Could not parse Qonfig-App definition XML: " + appDefUrl, e.getPosition(), e);
		}

		String appFile = appDef.getRoot().getAttributeText(qonfigAppTK.getAttribute("qonfig-app", "app-file"));
		builder.clear();

		// Resolve the dependency toolkits
		QonfigAttributeDef.Declared promiseNameAttr = qonfigAppTK.getAttribute("promise-fulfillment", "fulfills");
		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		for (QonfigElement toolkitEl : appDef.getRoot().getChildrenInRole(qonfigAppTK, "qonfig-app", "toolkit")) {
			ToolkitConfig tkCfg = builder.buildToolkit();
			for (CustomValueType valueType : create(toolkitEl.getChildrenInRole(qonfigAppTK, "toolkit", "value-type"),
				CustomValueType.class))
				tkCfg.withValueType(valueType);
			for (QonfigElement pfEl : toolkitEl.getChildrenInRole(qonfigAppTK, "toolkit", "promise-fulfillment")) {
				String promiseName = pfEl.getAttributeText(promiseNameAttr);
				tkCfg.withPromise(promiseName, create(Collections.singleton(pfEl), QonfigPromiseFulfillment.class).get(0));
			}
			String toolkitDef = toolkitEl.getAttributeText(qonfigAppTK.getAttribute("toolkit", "def"));
			URL toolkitURL = loader == null ? null : loader.getResource(toolkitDef);
			if (toolkitURL == null)
				toolkitURL = QonfigApp.class.getResource(toolkitDef);
			if (toolkitURL == null) {
				String appLoc = appDefUrl.toString();
				int lastSlash = appLoc.lastIndexOf('/');
				if (lastSlash > 0) {
					try {
						toolkitURL = new URL(appLoc.substring(0, lastSlash) + "/" + toolkitDef);
					} catch (MalformedURLException e) {
					}
				}
			}
			if (toolkitURL == null)
				throw new IllegalArgumentException("Could not find toolkit " + toolkitDef);

			try {
				tkCfg.buildToolkit(toolkitURL);
			} catch (IOException e) {
				throw new IllegalStateException("Could not read toolkit " + toolkitDef + ": " + e.getMessage(), e);
			} catch (XmlParseException e) {
				throw new TextParseException("Could not parse toolkit XML: " + e.getMessage(), e.getPosition(), e);
			} catch (QonfigParseException e) {
				throw new TextParseException("Could not parse toolkit: " + e.getMessage(), e.getIssues().get(0).fileLocation, e);
			} catch (RuntimeException e) {
				throw new IllegalStateException("Could not parse toolkit: " + e.getMessage() + toolkitDef, e);
			}
		}

		for (SpecialSessionImplementation<?> sessionType : create(
			appDef.getRoot().getChildrenInRole(qonfigAppTK, "qonfig-app", "special-session"),
			(Class<SpecialSessionImplementation<?>>) (Class<?>) SpecialSessionImplementation.class))
			builder.withSessionType(sessionType);

		for (QonfigInterpretation interpretation : create(appDef.getRoot().getChildrenInRole(qonfigAppTK, "qonfig-app", "interpretation"),
			QonfigInterpretation.class))
			builder.withInterpretation(interpretation);

		return builder.build(appDef, appFile);
	}

	/**
	 * @param <T> The type of values to create
	 * @param elements The elements whose values contain the names of classes implementing the given type
	 * @param type The type of values to create
	 * @return A list of the instantiated values
	 * @throws QonfigParseException If one of the values could not be instantiated
	 */
	public static <T> List<T> create(Collection<QonfigElement> elements, Class<T> type) throws QonfigParseException {
		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		ArrayList<T> values = new ArrayList<>(elements.size());
		for (QonfigElement el : elements) {
			Class<?> elType;
			if (loader != null) {
				try {
					elType = loader.loadClass(el.getValueText());
				} catch (ClassNotFoundException e) {
					try {
						elType = QonfigApp.class.getClassLoader().loadClass(el.getValueText());
					} catch (ClassNotFoundException e2) {
						throw QonfigParseException.createSimple(
							new LocatedFilePosition(el.getDocument().getLocation(), el.getValue().position.getPosition(0)),
							"No such " + type.getSimpleName() + " findable: " + el.getValueText(), e);
					}
				}
			} else {
				try {
					elType = QonfigApp.class.getClassLoader().loadClass(el.getValueText());
				} catch (ClassNotFoundException e) {
					throw QonfigParseException.createSimple(
						new LocatedFilePosition(el.getDocument().getLocation(), el.getValue().position.getPosition(0)),
						"No such " + type.getSimpleName() + " findable: " + el.getValueText(), e);
				}
			}
			if (!type.isAssignableFrom(elType))
				throw new IllegalArgumentException("Class " + elType.getName() + " is not a " + type.getName());
			T value;
			try {
				value = (T) elType.newInstance();
			} catch (IllegalAccessException e) {
				throw QonfigParseException.createSimple(
					new LocatedFilePosition(el.getDocument().getLocation(), el.getValue().position.getPosition(0)),
					"Could not access " + type.getSimpleName() + " " + elType.getName() + " for instantiation", e);
			} catch (InstantiationException e) {
				throw QonfigParseException.createSimple(
					new LocatedFilePosition(el.getDocument().getLocation(), el.getValue().position.getPosition(0)),
					"Could not instantiate " + type.getSimpleName() + " " + elType.getName(), e);
			}
			values.add(value);
		}
		values.trimToSize();
		return Collections.unmodifiableList(values);
	}

	private static <QIS extends SpecialSession<QIS>> void addSpecial(SpecialSessionImplementation<QIS> ssi,
		QonfigInterpreterCore.Builder coreBuilder) {
		coreBuilder.withSpecial(ssi.getProvidedAPI(), ssi);
	}

	private final QonfigDocument theDocument;
	private final String theLoadingLocation;
	private final String theAppFile;
	private final Set<QonfigToolkit> theToolkits;
	private final List<SpecialSessionImplementation<?>> theSessionTypes;
	private final List<QonfigInterpretation> theInterpretations;

	/**
	 * @param document The document defining the app
	 * @param loadingLocation The location relative to which to load the application file
	 * @param appFile The location of the file containing the user interface definition of the application
	 * @param toolkits All toolkits configured to support the application
	 * @param sessionTypes All Qonfig session types configured to support the application
	 * @param interpretations All Qonfig interpretations configured to support the application
	 */
	protected QonfigApp(QonfigDocument document, String loadingLocation, String appFile, Set<QonfigToolkit> toolkits,
		List<SpecialSessionImplementation<?>> sessionTypes, List<QonfigInterpretation> interpretations) {
		theDocument = document;
		theLoadingLocation = loadingLocation;
		theAppFile = appFile;
		theToolkits = toolkits;
		theSessionTypes = sessionTypes;
		theInterpretations = interpretations;
	}

	/** @return The document that defined this application */
	public QonfigDocument getDocument() {
		return theDocument;
	}

	/** @return The location of the document that defined this application */
	public String getLocation() {
		return theLoadingLocation;
	}

	/** @return The location of the file defining the user interface of the application */
	public String getAppFile() {
		return theAppFile;
	}

	/** @return All toolkits configured to support the application */
	public Set<QonfigToolkit> getToolkits() {
		return theToolkits;
	}

	/** @return All Qonfig session types configured to support the application */
	public List<SpecialSessionImplementation<?>> getSessionTypes() {
		return theSessionTypes;
	}

	/** @return All Qonfig interpretations configured to support the application */
	public List<QonfigInterpretation> getInterpretations() {
		return theInterpretations;
	}

	/**
	 * @return The application file resolved to a URL
	 * @throws IllegalArgumentException If the application file could not be resolved
	 */
	public URL resolveAppFile() throws IllegalArgumentException {
		URL appFileURL = QonfigApp.class.getResource(getAppFile());
		if (appFileURL == null) {
			try {
				String resolved = QommonsConfig.resolve(getAppFile(), getLocation());
				if (resolved == null)
					throw new IllegalArgumentException("Could not find app file " + getAppFile());
				appFileURL = new URL(resolved);
			} catch (IOException e) {
				throw new IllegalArgumentException("Could not find app file " + getAppFile(), e);
			}
		}
		return appFileURL;
	}

	/**
	 * @param <T> The type of the application value
	 * @param type The type of the application value
	 * @return The application value
	 * @throws IOException If the application's app-file reference could not be found or read
	 * @throws TextParseException If the app-file could not be parsed as XML
	 * @throws QonfigParseException If the app-file could not be parsed as Qonfig
	 * @throws QonfigInterpretationException If the application could not be interpreted
	 * @throws IllegalArgumentException If this method cannot locate, parse, or interpret the application setup file
	 */
	public <T> T interpretApp(Class<T> type) throws IOException, TextParseException, QonfigParseException, QonfigInterpretationException {
		return interpretApp(type, null);
	}

	/**
	 * @param <T> The type of the application value
	 * @param type The type of the application value
	 * @param session Accepts the interpretation session for the app root (may be null)
	 * @return The application value
	 * @throws IOException If the application's app-file reference could not be found or read
	 * @throws TextParseException If the app-file could not be parsed as XML
	 * @throws QonfigParseException If the app-file could not be parsed as Qonfig
	 * @throws QonfigInterpretationException If the application could not be interpreted
	 * @throws IllegalArgumentException If this method cannot locate, parse, or interpret the application setup file
	 */
	public <T> T interpretApp(Class<T> type, Consumer<AbstractQIS<?>> session)
		throws IOException, TextParseException, QonfigParseException, QonfigInterpretationException {
		// Ensure the Qonfig file exists
		URL appFileURL = resolveAppFile();

		DefaultQonfigParser qonfigParser = new DefaultQonfigParser();
		for (QonfigToolkit dep : getToolkits())
			qonfigParser.withToolkit(dep);

		// Parse the application file
		QonfigDocument qonfigDoc;
		try (InputStream appFileIn = appFileURL.openStream()) {
			qonfigDoc = qonfigParser.parseDocument(false, appFileURL.toString(), appFileIn);
		} catch (IOException e) {
			throw new IOException("Could not read application file " + getAppFile() + "\n" + e.getMessage(), e);
		} catch (XmlParseException e) {
			throw new TextParseException("Could not parse application file XML: " + appFileURL + "\n" + e.getMessage(), e.getPosition(), e);
		}

		appFileParsed(qonfigDoc);

		// Build the interpreter
		QonfigInterpreterCore.Builder coreBuilder = QonfigInterpreterCore.build(
			new ErrorReporting.Default(qonfigDoc.getRoot().getFilePosition()),
			getToolkits().toArray(new QonfigToolkit[getToolkits().size()]));

		for (SpecialSessionImplementation<?> ssi : getSessionTypes())
			addSpecial(ssi, coreBuilder);

		for (QonfigInterpretation interp : getInterpretations())
			coreBuilder.configure(interp);

		QonfigInterpreterCore interpreter = coreBuilder.build();
		// Interpret the app
		QonfigInterpreterCore.CoreSession coreSession = interpreter.interpret(qonfigDoc.getRoot());
		if (session != null)
			session.accept(coreSession);
		return coreSession.interpret(type);
	}

	/**
	 * Called after the app file has been parsed
	 * 
	 * @param doc The parsed application document
	 */
	protected void appFileParsed(QonfigDocument doc) {
	}

	@Override
	public String toString() {
		return theAppFile;
	}

	/** @return A builder for a QonfigApp */
	public static Builder build() {
		return new Builder();
	}

	/** Builds a QonfigElement */
	public static class Builder {
		private DefaultQonfigParser theParser;
		private final Set<QonfigToolkit> theToolkits;
		private final List<SpecialSessionImplementation<?>> theSessionTypes;
		private final List<QonfigInterpretation> theInterpretations;

		Builder() {
			theParser = new DefaultQonfigParser();
			theToolkits = new HashSet<>();
			theSessionTypes = new ArrayList<>();
			theInterpretations = new ArrayList<>();
		}

		/** @return The parser used by this builder to parse toolkits */
		public DefaultQonfigParser getParser() {
			return theParser;
		}

		/**
		 * @param toolkit The toolkit to include
		 * @return This builder
		 */
		public Builder withToolkit(QonfigToolkit toolkit) {
			theToolkits.add(toolkit);
			theParser.withToolkit(toolkit);
			return this;
		}

		/**
		 * Includes a toolkit
		 * 
		 * @param toolkitLocation The toolkit definition file location
		 * @return This builder
		 * @throws IOException If the toolkit file could not be read
		 * @throws TextParseException If the toolkit file could not be parsed as XML
		 * @throws QonfigParseException If the toolkit file could not be parsed as a Qonfig toolkit
		 */
		public Builder withToolkit(URL toolkitLocation) throws IOException, TextParseException, QonfigParseException {
			QonfigToolkit appTK;
			try (InputStream aTKIn = toolkitLocation.openStream()) {
				appTK = theParser.parseToolkit(toolkitLocation, aTKIn, null);
			}
			withToolkit(appTK);
			return this;
		}

		/** @return Configuration for a toolkit to include */
		public ToolkitConfig buildToolkit() {
			return new ToolkitConfig(this);
		}

		/**
		 * Includes a toolkit
		 * 
		 * @param toolkitLocation The toolkit definition file location
		 * @param config Configuration for the toolkit definition
		 * @return This builder
		 * @throws IOException If the toolkit file could not be read
		 * @throws TextParseException If the toolkit file could not be parsed as XML
		 * @throws QonfigParseException If the toolkit file could not be parsed as a Qonfig toolkit
		 */
		public Builder withToolkit(URL toolkitLocation, Consumer<ToolkitConfig> config)
			throws IOException, TextParseException, QonfigParseException {
			ToolkitConfig tkCfg = buildToolkit();
			config.accept(tkCfg);
			tkCfg.buildToolkit(toolkitLocation);
			return this;
		}

		/**
		 * @param sessionType The session sub-type to support
		 * @return This builder
		 */
		public Builder withSessionType(SpecialSessionImplementation<?> sessionType) {
			theSessionTypes.add(sessionType);
			return this;
		}

		/**
		 * @param interpretation The Qonfig interpretation to support
		 * @return This builder
		 */
		public Builder withInterpretation(QonfigInterpretation interpretation) {
			theInterpretations.add(interpretation);
			return this;
		}

		/**
		 * Resets this builder
		 * 
		 * @return This builder
		 */
		public Builder clear() {
			theToolkits.clear();
			theSessionTypes.clear();
			theInterpretations.clear();
			theParser = new DefaultQonfigParser();
			return this;
		}

		/**
		 * @param appDocument The Qonfig document of the app loading file defining all the toolkits, session types, and interpretations that
		 *        may be needed by the Qonfig application
		 * @param appFile The Qonfig application file containing the actual content of the application
		 * @return The QonfigApp to load the application
		 */
		public QonfigApp build(QonfigDocument appDocument, String appFile) {
			return new QonfigApp(appDocument, appDocument.getLocation(), appFile, QommonsUtils.unmodifiableDistinctCopy(theToolkits),
				QommonsUtils.unmodifiableCopy(theSessionTypes), QommonsUtils.unmodifiableCopy(theInterpretations));
		}

		/**
		 * @param loadingLocation The location relative to which to load the application file
		 * @param appFile The Qonfig application file containing the actual content of the application
		 * @return The QonfigApp to load the application
		 */
		public QonfigApp build(String loadingLocation, String appFile) {
			return new QonfigApp(null, loadingLocation, appFile, QommonsUtils.unmodifiableDistinctCopy(theToolkits),
				QommonsUtils.unmodifiableCopy(theSessionTypes), QommonsUtils.unmodifiableCopy(theInterpretations));
		}
	}

	/** Configuration for a toolkit to add to a QonfigApp via {@link Builder#buildToolkit()} */
	public static class ToolkitConfig {
		private final Builder theBuilder;
		private final List<CustomValueType> theValueTypes;
		private final Map<String, QonfigPromiseFulfillment> thePromiseFulfillment;

		ToolkitConfig(Builder builder) {
			theBuilder = builder;
			theValueTypes = new ArrayList<>();
			thePromiseFulfillment = new LinkedHashMap<>();
		}

		/**
		 * @param valueType The custom value type required by the toolkit
		 * @return This toolkit configuration
		 */
		public ToolkitConfig withValueType(CustomValueType valueType) {
			theValueTypes.add(valueType);
			return this;
		}

		/**
		 * @param promiseName The name of the promise to fulfill
		 * @param fulfillment The promise fulfillment implementation for the promise
		 * @return This toolkit configuration
		 */
		public ToolkitConfig withPromise(String promiseName, QonfigPromiseFulfillment fulfillment) {
			thePromiseFulfillment.put(promiseName, fulfillment);
			return this;
		}

		/**
		 * @param toolkitLocation The toolkit definition file location
		 * @return The {@link Builder} that the toolkit was added to
		 * @throws IOException If the toolkit file could not be read
		 * @throws TextParseException If the toolkit file could not be parsed as XML
		 * @throws QonfigParseException If the toolkit file could not be parsed as a Qonfig toolkit
		 */
		public Builder buildToolkit(URL toolkitLocation) throws IOException, TextParseException, QonfigParseException {
			try (InputStream tkIn = toolkitLocation.openStream()) {
				theBuilder.withToolkit(theBuilder.getParser().parseToolkit(toolkitLocation, tkIn, thePromiseFulfillment, //
					theValueTypes.toArray(new CustomValueType[theValueTypes.size()])));
			}
			return theBuilder;
		}
	}
}
