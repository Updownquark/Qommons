package org.qommons.config;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.qommons.config.QonfigInterpreterCore.Builder;

/** Provides a utility method for interpreting an application from a setup XML file formatted as qonfig-app.qtd */
public class QonfigApp {
	/**
	 * @param <T> The type of the application value
	 * @param appSetupFile The location of the setup file (interpreted as a class resource)
	 * @param type The type of the application value
	 * @return The application value
	 * @throws IllegalArgumentException If this method cannot locate, parse, or interpret the application setup file
	 */
	public static <T> T interpretApp(String appSetupFile, Class<T> type) throws IllegalArgumentException {
		if (!appSetupFile.startsWith("/")) // Force absolute
			appSetupFile = "/" + appSetupFile;
		URL appDefUrl = QonfigApp.class.getResource(appSetupFile);
		if (appDefUrl == null)
			throw new IllegalArgumentException("Could not locate Quick-App definition file: " + appSetupFile);
		return interpretApp(appDefUrl, type);
	}

	/**
	 * @param <T> The type of the application value
	 * @param appDefUrl The location of the setup file
	 * @param type The type of the application value
	 * @return The application value
	 * @throws IllegalArgumentException If this method cannot locate, parse, or interpret the application setup file
	 */
	public static <T> T interpretApp(URL appDefUrl, Class<T> type) {
		// Parse the app definition
		DefaultQonfigParser qonfigParser = new DefaultQonfigParser();
		URL appTKUrl = QonfigApp.class.getResource("qonfig-app.qtd");
		QonfigToolkit appTK;
		try (InputStream aTKIn = appTKUrl.openStream()) {
			appTK = qonfigParser.parseToolkit(appTKUrl, aTKIn);
		} catch (NullPointerException e) {
			throw new IllegalStateException("Could not locate app toolkit definition");
		} catch (IOException e) {
			throw new IllegalStateException("Could not read app toolkit definition", e);
		} catch (QonfigParseException e) {
			throw new IllegalStateException("Could not parse app toolkit definition", e);
		}
		qonfigParser.withToolkit(appTK);
		QonfigElement appDef;
		try (InputStream appDefIn = appDefUrl.openStream()) {
			appDef = qonfigParser.parseDocument(appDefUrl.toString(), appDefIn).getRoot();
		} catch (IOException e) {
			throw new IllegalArgumentException("Could not read Quick-App definition: " + appDefUrl, e);
		} catch (QonfigParseException e) {
			throw new IllegalArgumentException("Could not parse Quick-App definition: " + appDefUrl, e);
		}

		String appDefLoc = appDefUrl.toString();

		// Ensure the Quick file exists
		String appFile = appDef.getAttributeText(appTK.getAttribute("qonfig-app", "app-file"));
		URL appFileURL = QonfigApp.class.getResource(appFile);
		if (appFileURL == null) {
			try {
				String resolved = QommonsConfig.resolve(appFile, appDefLoc);
				if (resolved == null)
					throw new IllegalArgumentException("Could not find app file " + appFile);
				appFileURL = new URL(resolved);
			} catch (IOException e) {
				throw new IllegalArgumentException("Could not find app file " + appFile, e);
			}
		}

		// Install the dependency toolkits in the Qonfig parser
		qonfigParser = new DefaultQonfigParser(); // Reset the parser
		for (QonfigElement toolkitEl : appDef.getChildrenInRole(appTK, "qonfig-app", "toolkit")) {
			List<CustomValueType> valueTypes = create(toolkitEl.getChildrenInRole(appTK, "toolkit", "value-type"), CustomValueType.class);
			String toolkitDef = toolkitEl.getAttributeText(appTK.getAttribute("toolkit", "def"));
			URL toolkitURL = QonfigApp.class.getResource(toolkitDef);
			if (toolkitURL == null)
				throw new IllegalArgumentException("Could not find toolkit " + toolkitDef);
			try (InputStream tkIn = toolkitURL.openStream()) {
				qonfigParser.parseToolkit(toolkitURL, tkIn, //
					valueTypes.toArray(new CustomValueType[valueTypes.size()]));
			} catch (IOException e) {
				throw new IllegalStateException("Could not read toolkit " + toolkitDef, e);
			} catch (QonfigParseException e) {
				throw new IllegalStateException("Could not parse toolkit " + toolkitDef, e);
			} catch (RuntimeException e) {
				throw new IllegalStateException("Could not parse toolkit " + toolkitDef, e);
			}
		}

		// Parse the application file
		QonfigDocument qonfigDoc;
		try (InputStream appFileIn = appFileURL.openStream()) {
			qonfigDoc = qonfigParser.parseDocument(appFileURL.toString(), appFileIn);
		} catch (IOException e) {
			throw new IllegalArgumentException("Could not read application file " + appFile, e);
		} catch (QonfigParseException e) {
			throw new IllegalArgumentException("Could not parse application file " + appFile, e);
		}

		// Build the interpreter
		Set<QonfigToolkit> toolkits = new LinkedHashSet<>();
		addToolkits(qonfigDoc.getDocToolkit(), toolkits);
		QonfigInterpreterCore.Builder coreBuilder = QonfigInterpreterCore.build(QonfigApp.class,
			toolkits.toArray(new QonfigToolkit[toolkits.size()]));
		for (SpecialSessionImplementation<?> ssi : create(appDef.getChildrenInRole(appTK, "qonfig-app", "special-session"),
			SpecialSessionImplementation.class)) {
			addSpecial(ssi, coreBuilder);
		}

		for (QonfigInterpretation interp : create(appDef.getChildrenInRole(appTK, "qonfig-app", "interpretation"),
			QonfigInterpretation.class)) {
			coreBuilder.configure(interp);
		}
		QonfigInterpreterCore interpreter = coreBuilder.build();

		// Interpret the app
		T appValue;
		try {
			appValue = interpreter.interpret(qonfigDoc.getRoot())//
				.interpret(type);
		} catch (QonfigInterpretationException e) {
			throw new IllegalStateException("Could not interpret Quick file at " + appFile, e);
		}
		return appValue;
	}

	static <T> List<T> create(Collection<QonfigElement> elements, Class<T> type) {
		List<T> values = new ArrayList<>(elements.size());
		for (QonfigElement el : elements) {
			Class<?> elType;
			try {
				elType = Class.forName(el.getValueText());
			} catch (ClassNotFoundException e) {
				throw new IllegalArgumentException("No such " + type.getSimpleName() + " findable: " + el.getValueText());
			}
			if (!type.isAssignableFrom(elType))
				throw new IllegalArgumentException("Class " + elType.getName() + " is not a " + type.getName());
			T value;
			try {
				value = (T) elType.newInstance();
			} catch (IllegalAccessException e) {
				throw new IllegalArgumentException(
					"Could not access " + type.getSimpleName() + " " + elType.getName() + " for instantiation", e);
			} catch (InstantiationException e) {
				throw new IllegalArgumentException("Could not instantiate " + type.getSimpleName() + " " + elType.getName(), e);
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
