package org.qommons.config;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.function.Supplier;

import org.qommons.QommonsUtils;

/** A convenient structure for defining and providing toolkit instances */
public class QonfigToolkitAccess implements Supplier<QonfigToolkit> {
	private final String theLocationString;
	private final Class<?> theResourceClass;
	private URL theLocation;

	private final List<QonfigToolkitAccess> theDependencies;

	private QonfigToolkit theToolkit;
	private RuntimeException theError;

	/**
	 * Specifies a toolkit accessor for a class resource
	 * 
	 * @param resourceClass The class that the resource is relative to
	 * @param location The relative location of the toolkit XML
	 * @param dependencies Accessors for toolkits that the given toolkit may depend on
	 */
	public QonfigToolkitAccess(Class<?> resourceClass, String location, QonfigToolkitAccess... dependencies) {
		theResourceClass = resourceClass;
		theLocationString = location;
		theDependencies = QommonsUtils.unmodifiableCopy(dependencies);
	}

	/**
	 * Specifies a toolkit accessor for an absolute URL location
	 * 
	 * @param location The URL location string of the toolkit XML
	 * @param dependencies Accessors for toolkits that the given toolkit may depend on
	 */
	public QonfigToolkitAccess(String location, QonfigToolkitAccess... dependencies) {
		theResourceClass = null;
		theLocationString = location;
		theDependencies = QommonsUtils.unmodifiableCopy(dependencies);
	}

	/**
	 * Specifies a toolkit accessor for an absolute URL location
	 * 
	 * @param location The URL location of the toolkit XML
	 * @param dependencies Accessors for toolkits that the given toolkit may depend on
	 */
	public QonfigToolkitAccess(URL location, QonfigToolkitAccess... dependencies) {
		theResourceClass = null;
		theLocation = location;
		theLocationString = location.toString();
		theDependencies = QommonsUtils.unmodifiableCopy(dependencies);
	}

	/** @return Whether this accessor has at least attempted to build the toolkit */
	public boolean isBuilt() {
		return theToolkit != null || theError != null;
	}

	/** @return Whether the toolkit was parsed successfully (will be attempted if no attempt has yet been made) */
	public boolean isValid() {
		if (theToolkit != null)
			return true;
		else if (theError != null)
			return false;
		try {
			get();
			return true;
		} catch (RuntimeException e) {
			return false;
		}
	}

	/**
	 * Supplies the toolkit, parsing it first if needed
	 * 
	 * @return The toolkit
	 * @throws IllegalArgumentException If an error occurs parsing it
	 */
	@Override
	public QonfigToolkit get() throws IllegalArgumentException {
		if (theToolkit != null)
			return theToolkit;
		else if (theError != null)
			throw theError;
		synchronized (this) {
			if (theToolkit != null)
				return theToolkit;
			else if (theError != null)
				throw theError;

			if (theLocation == null) {
				if (theResourceClass != null) {
					theLocation = theResourceClass.getResource(theLocationString);
					if (theLocation == null) {
						theError = new IllegalArgumentException(
							"No toolkit found at " + theResourceClass.getName() + ":" + theLocationString);
						throw theError;
					}
				} else {
					try {
						theLocation = new URL(theLocationString);
					} catch (MalformedURLException e) {
						theError = new IllegalArgumentException("Bad toolkit URL: " + theLocationString, e);
						throw theError;
					}
				}
			}

			DefaultQonfigParser parser = new DefaultQonfigParser();
			for (QonfigToolkitAccess dep : theDependencies)
				parser.withToolkit(dep.get());
			QonfigToolkit tk;
			try (InputStream in = theLocation.openStream()) {
				tk = parser.parseToolkit(theLocation, in);
			} catch (IOException | QonfigParseException e) {
				tk = null;
				theError = new IllegalStateException("Unable to parse toolkit " + theLocationString, e);
				throw theError;
			}
			theToolkit = tk;
			return tk;
		}
	}

	@Override
	public String toString() {
		if (theResourceClass != null)
			return theResourceClass.getName() + ":" + theLocationString;
		else
			return theLocationString;
	}
}
