package org.qommons.config;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A value type for pattern-matched strings */
public class QonfigPattern extends AbstractQonfigType implements QonfigValueType.Declared {
	private final Pattern thePattern;

	/**
	 * @param declarer The toolkit declaring this pattern
	 * @param name The name of the pattern
	 * @param pattern The regex pattern to apply
	 * @param lineNumber The line number where this pattern was defined
	 */
	public QonfigPattern(QonfigToolkit declarer, String name, Pattern pattern, int lineNumber) {
		super(declarer, name, lineNumber);
		thePattern = pattern;
	}

	/** @return The regex pattern that this pattern checks values against */
	public Pattern getPattern() {
		return thePattern;
	}

	@Override
	public Object parse(String value, QonfigToolkit tk, QonfigParseSession session) {
		Matcher m = thePattern.matcher(value);
		if (!m.matches()) {
			session.withError("Expected string matching '" + thePattern.pattern() + "', not '" + value + "'");
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
