package org.qommons.config;

/** Metadata content in a {@link QonfigElementOrAddOn} */
public class QonfigMetadata extends QonfigDocument {
	private final QonfigElementOrAddOn theElement;

	/** @param element The element or add-on that this metadata is for */
	public QonfigMetadata(QonfigElementOrAddOn element) {
		super(element.getDeclarer().getLocation().toString(), element.getDeclarer());
		theElement = element;
	}

	/** @return The element or add-on that this metadata is for */
	public QonfigElementOrAddOn getElement() {
		return theElement;
	}
}
