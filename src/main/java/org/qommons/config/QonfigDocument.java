package org.qommons.config;

/** A parsed Qonfig document */
public class QonfigDocument {
	private final String theLocation;
	private final QonfigToolkit theToolkit;
	private QonfigElement theRoot;

	QonfigDocument(String location, QonfigToolkit toolkit) {
		theLocation = location;
		theToolkit = toolkit;
	}

	void setRoot(QonfigElement root) {
		theRoot = root;
	}

	/** @return The location of the document */
	public String getLocation() {
		return theLocation;
	}

	/** @return The toolkit representing the types available to this document */
	public QonfigToolkit getDocToolkit() {
		return theToolkit;
	}

	/** @return The content of the document */
	public QonfigElement getRoot() {
		return theRoot;
	}
}
