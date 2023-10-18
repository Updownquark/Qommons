package org.qommons.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;

import org.qommons.QommonsUtils;

/** A value parsed by a {@link QonfigPattern} */
public class PatternMatch {
	private final String theWholeText;
	private final List<String> theGroups;
	private final Map<String, QommonsUtils.NamedGroupCapture> theNamedGroups;

	/** @param m The regex pattern match */
	public PatternMatch(Matcher m) {
		theWholeText = m.group();
		if (m.groupCount() == 0) {
			theGroups = Collections.emptyList();
			theNamedGroups = Collections.emptyMap();
		} else {
			theNamedGroups = Collections.unmodifiableMap(QommonsUtils.getCaptureGroups(m));
			List<String> groups = new ArrayList<>(m.groupCount());
			for (int i = 1; i <= m.groupCount(); i++) {
				groups.add(m.group(i));
			}
			theGroups = Collections.unmodifiableList(groups);
		}
	}

	/** @return The full text of the match */
	public String getWholeText() {
		return theWholeText;
	}

	/** @return The names of all named capturing groups defined by the pattern */
	public List<String> getGroups() {
		return theGroups;
	}

	/** @return The values of all named capturing groups specified in the match */
	public Map<String, QommonsUtils.NamedGroupCapture> getNamedGroups() {
		return theNamedGroups;
	}

	public String getGroup(String name) {
		QommonsUtils.NamedGroupCapture capture = theNamedGroups.get(name);
		return capture == null ? null : capture.value;
	}

	@Override
	public int hashCode() {
		return Objects.hash(theWholeText, theGroups, theNamedGroups);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		else if (!(obj instanceof PatternMatch))
			return false;
		PatternMatch other = (PatternMatch) obj;
		return theWholeText.equals(other.theWholeText)//
			&& theGroups.equals(other.theGroups)//
			&& theNamedGroups.equals(other.theNamedGroups);
	}

	@Override
	public String toString() {
		return theWholeText;
	}
}
