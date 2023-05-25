package org.qommons.config;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import org.qommons.QommonsUtils;
import org.qommons.io.SimpleXMLParser.XmlParseException;

/** A convenient structure for defining and providing toolkit instances */
public class QonfigToolkitAccess implements Supplier<QonfigToolkit> {
	private final String theLocationString;
	private final Class<?> theResourceClass;
	private URL theLocation;

	private final List<QonfigToolkitAccess> theDependencies;
	private final List<CustomValueType> theCustomValueTypes;

	private volatile QonfigToolkit theToolkit;
	private volatile RuntimeException theError;

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
		theCustomValueTypes = new ArrayList<>(3);
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
		theCustomValueTypes = new ArrayList<>(3);
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
		theCustomValueTypes = new ArrayList<>(3);
	}

	/**
	 * @param customValueTypes Custom value types for the toolkit to use
	 * @return This accessor
	 */
	public QonfigToolkitAccess withCustomValueType(CustomValueType... customValueTypes) {
		if (isBuilt())
			throw new IllegalStateException("The toolkit has already been built");
		theCustomValueTypes.addAll(Arrays.asList(customValueTypes));
		return this;
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
		QonfigToolkit toolkit = theToolkit;
		RuntimeException error = theError;
		if (toolkit != null)
			return toolkit;
		else if (error != null)
			throw error;
		synchronized (this) {
			toolkit = theToolkit;
			error = theError;
			if (toolkit != null)
				return toolkit;
			else if (error != null)
				throw error;

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
				addDependency(parser, dep.get());
			try (InputStream in = theLocation.openStream()) {
				toolkit = parser.parseToolkit(theLocation, in, //
					theCustomValueTypes.toArray(new CustomValueType[theCustomValueTypes.size()]));
			} catch (IOException | XmlParseException | QonfigParseException e) {
				theError = new IllegalStateException("Unable to parse toolkit " + theLocationString, e);
				throw theError;
			}
			theToolkit = toolkit;
			return toolkit;
		}
	}

	private void addDependency(DefaultQonfigParser parser, QonfigToolkit toolkit) {
		if (parser.usesToolkit(toolkit.getName(), toolkit.getMajorVersion()))
			return;
		parser.withToolkit(toolkit);
		for (QonfigToolkit dep : toolkit.getDependencies().values())
			addDependency(parser, dep);
	}

	@Override
	public String toString() {
		if (theResourceClass != null)
			return theResourceClass.getName() + ":" + theLocationString;
		else
			return theLocationString;
	}
}
