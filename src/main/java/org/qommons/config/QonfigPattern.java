package org.qommons.config;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.qommons.io.ContentPosition;
import org.qommons.io.ErrorReporting;

/** A value type for pattern-matched strings */
public class QonfigPattern extends AbstractQonfigType implements QonfigValueType.Declared {
	private final Pattern thePattern;

	/**
	 * @param declarer The toolkit declaring this pattern
	 * @param name The name of the pattern
	 * @param pattern The regex pattern to apply
	 * @param position The position in the file where this value was defined
	 */
	public QonfigPattern(QonfigToolkit declarer, String name, Pattern pattern, ContentPosition position) {
		super(declarer, name, position);
		thePattern = pattern;
	}

	/** @return The regex pattern that this pattern checks values against */
	public Pattern getPattern() {
		return thePattern;
	}

	@Override
	public Object parse(String value, QonfigToolkit tk, ErrorReporting session) {
		Matcher m = thePattern.matcher(value);
		if (!m.matches()) {
			session.error("Expected string matching '" + thePattern.pattern() + "', not '" + value + "'");
			return null;
		}
		return new PatternMatch(m);
	}

	@Override
	public boolean isInstance(Object value) {
		return value instanceof PatternMatch;
	}

	@Override
	public String toString() {
		return super.toString() + "=" + thePattern.pattern();
	}
}
