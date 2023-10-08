package org.qommons.config;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.w3c.dom.Element;

public interface ExternalReferenceStitcher {
	QonfigElement stitchExternal(QonfigParseSession session, QonfigElement reference, Set<QonfigChildDef> roles,
		Set<QonfigAddOn> inheritance) throws QonfigParseException;

	public static class Default implements ExternalReferenceStitcher {
		private final Map<String, Element> theCache;

		public Default() {
			theCache = new HashMap<>();
		}

		@Override
		public QonfigElement stitchExternal(QonfigParseSession session, QonfigElement reference, Set<QonfigChildDef> roles,
			Set<QonfigAddOn> inheritance) throws QonfigParseException {

			// TODO Auto-generated method stub
			return null;
		}
	}

	public static final ExternalReferenceStitcher ERROR = new ExternalReferenceStitcher() {
		@Override
		public QonfigElement stitchExternal(QonfigParseSession session, QonfigElement reference, Set<QonfigChildDef> roles,
			Set<QonfigAddOn> inheritance) throws QonfigParseException {
			throw QonfigParseException.createSimple(reference.getPositionInFile(), "No such external reference recognized/allowed", null);
		}
	};
}
