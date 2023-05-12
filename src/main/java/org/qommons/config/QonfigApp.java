package org.qommons.config;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.qommons.config.QonfigInterpreterCore.Builder;
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
	 */
	public static QonfigDocument parseApp(URL appDefUrl, URL... appToolkits) throws IOException, TextParseException, QonfigParseException {
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

		try (InputStream appDefIn = appDefUrl.openStream()) {
			return qonfigParser.parseDocument(appDefUrl.toString(), appDefIn);
		} catch (IOException e) {
			throw new IOException("Could not read Qonfig-App definition: " + appDefUrl, e);
		} catch (XmlParseException e) {
			throw new TextParseException("Could not parse Qonfig-App definition XML: " + appDefUrl, e.getPosition(), e);
		}
	}

	/**
	 * @param <T> The type of the application value
	 * @param appDef The {@link #getQonfigAppToolkit() Qonfig-App}-formatted application to interpret
	 * @param type The type of the application value
	 * @return The application value
	 * @throws IOException If the application's app-file reference could not be found or read
	 * @throws TextParseException If the app-file could not be parsed as XML
	 * @throws QonfigParseException If the app-file could not be parsed as Qonfig
	 * @throws QonfigInterpretationException If the application could not be interpreted
	 * @throws IllegalArgumentException If this method cannot locate, parse, or interpret the application setup file
	 */
	public static <T> T interpretApp(QonfigDocument appDef, Class<T> type)
		throws IOException, TextParseException, QonfigParseException, QonfigInterpretationException {
		return interpretApp(appDef, type, null);
	}

	/**
	 * @param <T> The type of the application value
	 * @param appDef The {@link #getQonfigAppToolkit() Qonfig-App}-formatted application to interpret
	 * @param type The type of the application value
	 * @param session Accepts the interpretation session for the app root (may be null)
	 * @return The application value
	 * @throws IOException If the application's app-file reference could not be found or read
	 * @throws TextParseException If the app-file could not be parsed as XML
	 * @throws QonfigParseException If the app-file could not be parsed as Qonfig
	 * @throws QonfigInterpretationException If the application could not be interpreted
	 * @throws IllegalArgumentException If this method cannot locate, parse, or interpret the application setup file
	 */
	public static <T> T interpretApp(QonfigDocument appDef, Class<T> type, Consumer<AbstractQIS<?>> session)
		throws IOException, TextParseException, QonfigParseException, QonfigInterpretationException {
		QonfigToolkit qonfigAppTK = getQonfigAppToolkit();

		// Ensure the Qonfig file exists
		String appFile = appDef.getRoot().getAttributeText(qonfigAppTK.getAttribute("qonfig-app", "app-file"));
		URL appFileURL = QonfigApp.class.getResource(appFile);
		if (appFileURL == null) {
			try {
				String resolved = QommonsConfig.resolve(appFile, appDef.getLocation());
				if (resolved == null)
					throw new IllegalArgumentException("Could not find app file " + appFile);
				appFileURL = new URL(resolved);
			} catch (IOException e) {
				throw new IllegalArgumentException("Could not find app file " + appFile, e);
			}
		}

		// Install the dependency toolkits in the Qonfig parser
		DefaultQonfigParser qonfigParser = new DefaultQonfigParser();
		for (QonfigElement toolkitEl : appDef.getRoot().getChildrenInRole(qonfigAppTK, "qonfig-app", "toolkit")) {
			List<CustomValueType> valueTypes = create(toolkitEl.getChildrenInRole(qonfigAppTK, "toolkit", "value-type"),
				CustomValueType.class);
			String toolkitDef = toolkitEl.getAttributeText(qonfigAppTK.getAttribute("toolkit", "def"));
			URL toolkitURL = QonfigApp.class.getResource(toolkitDef);
			if (toolkitURL == null)
				throw new IllegalArgumentException("Could not find toolkit " + toolkitDef);
			try (InputStream tkIn = toolkitURL.openStream()) {
				qonfigParser.parseToolkit(toolkitURL, tkIn, //
					valueTypes.toArray(new CustomValueType[valueTypes.size()]));
			} catch (IOException e) {
				throw new IllegalStateException("Could not read toolkit " + toolkitDef, e);
			} catch (XmlParseException e) {
				throw new IllegalArgumentException("Could not parse toolkit XML: " + appDef.getLocation(), e);
			} catch (QonfigParseException e) {
				e.printStackTrace();
				throw new IllegalStateException("Could not parse toolkit " + toolkitDef);
			} catch (RuntimeException e) {
				throw new IllegalStateException("Could not parse toolkit " + toolkitDef, e);
			}
		}

		// Parse the application file
		QonfigDocument qonfigDoc;
		try (InputStream appFileIn = appFileURL.openStream()) {
			qonfigDoc = qonfigParser.parseDocument(appFileURL.toString(), appFileIn);
		} catch (IOException e) {
			throw new IOException("Could not read application file " + appFile, e);
		} catch (XmlParseException e) {
			throw new TextParseException("Could not parse application file XML: " + appDef.getLocation(), e.getPosition(), e);
		}

		// Build the interpreter
		Set<QonfigToolkit> toolkits = new LinkedHashSet<>();
		addToolkits(qonfigDoc.getDocToolkit(), toolkits);
		QonfigInterpreterCore.Builder coreBuilder = QonfigInterpreterCore.build(QonfigApp.class,
			toolkits.toArray(new QonfigToolkit[toolkits.size()]));
		for (SpecialSessionImplementation<?> ssi : create(appDef.getRoot().getChildrenInRole(qonfigAppTK, "qonfig-app", "special-session"),
			SpecialSessionImplementation.class)) {
			addSpecial(ssi, coreBuilder);
		}

		for (QonfigInterpretation interp : create(appDef.getRoot().getChildrenInRole(qonfigAppTK, "qonfig-app", "interpretation"),
			QonfigInterpretation.class)) {
			coreBuilder.configure(interp);
		}
		QonfigInterpreterCore interpreter = coreBuilder.build();
		// Interpret the app
		QonfigInterpreterCore.CoreSession coreSession = interpreter.interpret(qonfigDoc.getRoot());
		if (session != null)
			session.accept(coreSession);
		return coreSession.interpret(type);
	}

	/**
	 * @param <T> The type of values to create
	 * @param elements The elements whose values contain the names of classes implementing the given type
	 * @param type The type of values to create
	 * @return A list of the instantiated values
	 * @throws QonfigParseException If one of the values could not be instantiated
	 */
	public static <T> List<T> create(Collection<QonfigElement> elements, Class<T> type) throws QonfigParseException {
		List<T> values = new ArrayList<>(elements.size());
		for (QonfigElement el : elements) {
			Class<?> elType;
			try {
				elType = Class.forName(el.getValueText());
			} catch (ClassNotFoundException e) {
				throw QonfigParseException.createSimple(el.getDocument().getLocation(), el.getType().getName(),
					el.getValue().position, "No such " + type.getSimpleName() + " findable: " + el.getValueText(), e);
			}
			if (!type.isAssignableFrom(elType))
				throw new IllegalArgumentException("Class " + elType.getName() + " is not a " + type.getName());
			T value;
			try {
				value = (T) elType.newInstance();
			} catch (IllegalAccessException e) {
				throw QonfigParseException.createSimple(el.getDocument().getLocation(), el.getType().getName(),
					el.getValue().position,
					"Could not access " + type.getSimpleName() + " " + elType.getName() + " for instantiation", e);
			} catch (InstantiationException e) {
				throw QonfigParseException.createSimple(el.getDocument().getLocation(), el.getType().getName(),
					el.getValue().position, "Could not instantiate " + type.getSimpleName() + " " + elType.getName(), e);
			}
			values.add(value);
		}
		return values;
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
}
