package org.qommons.config;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.qommons.config.QonfigInterpreterCore.Builder;
import org.qommons.io.ErrorReporting;
import org.qommons.io.LocatedFilePosition;
import org.qommons.io.SimpleXMLParser.XmlParseException;
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
		try (InputStream aTKIn = qonfigAppTKUrl.openStream()) {
			QONFIG_APP_TOOLKIT = qonfigParser.parseToolkit(qonfigAppTKUrl, aTKIn);
		} catch (NullPointerException e) {
			throw new IllegalStateException("Could not locate app toolkit definition '" + qonfigAppTKUrl.getPath() + "'");
		} catch (IOException e) {
			throw new IllegalStateException("Could not read app toolkit definition '" + qonfigAppTKUrl.getPath() + "'", e);
		} catch (XmlParseException e) {
			throw new IllegalArgumentException("Could not parse toolkit definition XML '" + qonfigAppTKUrl.getPath() + "'", e);
		} catch (QonfigParseException e) {
			throw new IllegalStateException("Could not parse app toolkit definition '" + qonfigAppTKUrl.getPath() + "'", e);
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
		QonfigToolkit qonfigAppTK = getQonfigAppToolkit();
		DefaultQonfigParser qonfigParser = new DefaultQonfigParser();
		qonfigParser.withToolkit(qonfigAppTK);

		for (URL appToolkit : appToolkits) {
			QonfigToolkit appTK;
			try (InputStream aTKIn = appToolkit.openStream()) {
				appTK = qonfigParser.parseToolkit(appToolkit, aTKIn);
			} catch (IOException e) {
				throw new IOException("Could not read app toolkit definition '" + appToolkit.getPath() + "'", e);
			} catch (XmlParseException e) {
				throw new TextParseException("Could not parse toolkit definition XML '" + appToolkit.getPath() + "'", e.getPosition(), e);
			}
			qonfigParser.withToolkit(appTK);
		}

		QonfigDocument appDef;
		try (InputStream appDefIn = appDefUrl.openStream()) {
			appDef = qonfigParser.parseDocument(appDefUrl.toString(), appDefIn);
		} catch (IOException e) {
			throw new IOException("Could not read Qonfig-App definition: " + appDefUrl, e);
		} catch (XmlParseException e) {
			throw new TextParseException("Could not parse Qonfig-App definition XML: " + appDefUrl, e.getPosition(), e);
		}

		String appFile = appDef.getRoot().getAttributeText(qonfigAppTK.getAttribute("qonfig-app", "app-file"));

		Set<QonfigToolkit> toolkits = new LinkedHashSet<>();
		// Resolve the dependency toolkits
		for (QonfigElement toolkitEl : appDef.getRoot().getChildrenInRole(qonfigAppTK, "qonfig-app", "toolkit")) {
			List<CustomValueType> valueTypes = create(toolkitEl.getChildrenInRole(qonfigAppTK, "toolkit", "value-type"),
				CustomValueType.class);
			String toolkitDef = toolkitEl.getAttributeText(qonfigAppTK.getAttribute("toolkit", "def"));
			URL toolkitURL = QonfigApp.class.getResource(toolkitDef);
			if (toolkitURL == null)
				throw new IllegalArgumentException("Could not find toolkit " + toolkitDef);
			try (InputStream tkIn = toolkitURL.openStream()) {
				toolkits.add(qonfigParser.parseToolkit(toolkitURL, tkIn, //
					valueTypes.toArray(new CustomValueType[valueTypes.size()])));
			} catch (IOException e) {
				throw new IllegalStateException("Could not read toolkit " + toolkitDef, e);
			} catch (XmlParseException e) {
				throw new IllegalArgumentException("Could not parse toolkit XML: " + appDef.getLocation(), e);
			} catch (QonfigParseException e) {
				throw new IllegalStateException("Could not parse toolkit " + toolkitDef, e);
			} catch (RuntimeException e) {
				throw new IllegalStateException("Could not parse toolkit " + toolkitDef, e);
			}
		}

		List<SpecialSessionImplementation<?>> sessionTypes = create(
			appDef.getRoot().getChildrenInRole(qonfigAppTK, "qonfig-app", "special-session"),
			(Class<SpecialSessionImplementation<?>>) (Class<?>) SpecialSessionImplementation.class);

		List<QonfigInterpretation> interpretations = create(appDef.getRoot().getChildrenInRole(qonfigAppTK, "qonfig-app", "interpretation"),
			QonfigInterpretation.class);

		return new QonfigApp(appDef, appFile, Collections.unmodifiableSet(toolkits), sessionTypes, interpretations);
	}

	/**
	 * @param <T> The type of values to create
	 * @param elements The elements whose values contain the names of classes implementing the given type
	 * @param type The type of values to create
	 * @return A list of the instantiated values
	 * @throws QonfigParseException If one of the values could not be instantiated
	 */
	public static <T> List<T> create(Collection<QonfigElement> elements, Class<T> type) throws QonfigParseException {
		ArrayList<T> values = new ArrayList<>(elements.size());
		for (QonfigElement el : elements) {
			Class<?> elType;
			try {
				elType = Class.forName(el.getValueText());
			} catch (ClassNotFoundException e) {
				throw QonfigParseException.createSimple(
					new LocatedFilePosition(el.getDocument().getLocation(), el.getValue().position.getPosition(0)),
					"No such " + type.getSimpleName() + " findable: " + el.getValueText(), e);
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

	private static void addToolkits(QonfigToolkit toolkit, Set<QonfigToolkit> toolkits) {
		for (QonfigToolkit dep : toolkit.getDependencies().values()) {
			if (toolkits.add(dep))
				addToolkits(dep, toolkits);
		}
	}

	private static <QIS extends SpecialSession<QIS>> void addSpecial(SpecialSessionImplementation<QIS> ssi, Builder coreBuilder) {
		coreBuilder.withSpecial(ssi.getProvidedAPI(), ssi);
	}

	private final QonfigDocument theDocument;
	private final String theAppFile;
	private final Set<QonfigToolkit> theToolkits;
	private final List<SpecialSessionImplementation<?>> theSessionTypes;
	private final List<QonfigInterpretation> theInterpretations;

	/**
	 * @param document The document defining the app
	 * @param appFile The location of the file containing the user interface definition of the application
	 * @param toolkits All toolkits configured to support the application
	 * @param sessionTypes All Qonfig session types configured to support the application
	 * @param interpretations All Qonfig interpretations configured to support the application
	 */
	protected QonfigApp(QonfigDocument document, String appFile, Set<QonfigToolkit> toolkits,
		List<SpecialSessionImplementation<?>> sessionTypes,
		List<QonfigInterpretation> interpretations) {
		theDocument = document;
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
		return theDocument.getLocation();
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
			qonfigDoc = qonfigParser.parseDocument(appFileURL.toString(), appFileIn);
		} catch (IOException e) {
			throw new IOException("Could not read application file " + getAppFile(), e);
		} catch (XmlParseException e) {
			throw new TextParseException("Could not parse application file XML: " + appFileURL, e.getPosition(), e);
		}

		// Build the interpreter
		Set<QonfigToolkit> toolkits = new LinkedHashSet<>();
		addToolkits(qonfigDoc.getDocToolkit(), toolkits);
		QonfigInterpreterCore.Builder coreBuilder = QonfigInterpreterCore.build(QonfigApp.class,
			new ErrorReporting.Default(qonfigDoc.getRoot().getFilePosition()), toolkits.toArray(new QonfigToolkit[toolkits.size()]));

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

	@Override
	public String toString() {
		return theAppFile;
	}
}
