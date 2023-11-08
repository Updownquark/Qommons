package org.qommons.config;

/** A parsed Qonfig document */
public class QonfigDocument {
	private final String theLocation;
	private final QonfigToolkit theToolkit;
	private PartialQonfigElement theRoot;

	QonfigDocument(String location, QonfigToolkit toolkit) {
		theLocation = location.intern(); // This string is compared a lot in my code, so this speeds that up
		theToolkit = toolkit;
	}

	void setRoot(PartialQonfigElement root) {
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
		if (!(theRoot instanceof QonfigElement))
			throw new IllegalStateException("This document is partial");
		return (QonfigElement) theRoot;
	}

	public PartialQonfigElement getPartialRoot() {
		return theRoot;
	}

	@Override
	public String toString() {
		return theLocation;
	}
}
