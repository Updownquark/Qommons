package org.qommons.config;

import java.util.Map;

import org.qommons.QommonsUtils;

/** A parsed Qonfig document */
public class QonfigDocument {
	private final String theLocation;
	private final Map<String, QonfigToolkit> theToolkits;
	private QonfigElement theRoot;

	QonfigDocument(String location, Map<String, QonfigToolkit> toolkits) {
		theLocation = location;
		theToolkits = QommonsUtils.unmodifiableCopy(toolkits);
	}

	void setRoot(QonfigElement root) {
		theRoot = root;
	}

	/** @return The location of the document */
	public String getLocation() {
		return theLocation;
	}

	/** @return The toolkits used by the document */
	public Map<String, QonfigToolkit> getToolkits() {
		return theToolkits;
	}

	/** @return The content of the document */
	public QonfigElement getRoot() {
		return theRoot;
	}
}
