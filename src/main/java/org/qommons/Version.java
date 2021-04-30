package org.qommons;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Semantic version class */
public class Version implements Comparable<Version> {
	/** A pattern to parse semantic version strings */
	public static final Pattern VERSION_PATTERN = Pattern
		.compile("(?<major>\\d+)\\.(?<minor>\\d+)\\.(?<patch>\\d+)(?:\\.(?<qualifier>[a-zA-Z0-9_\\-]+))");

	/** The major revision */
	public final int major;
	/** The minor revision */
	public final int minor;
	/** The patch revision */
	public final int patch;
	/** The version qualifier */
	public final String qualifier;

	/**
	 * @param major The major revision
	 * @param minor The minor revision
	 * @param patch The patch revision
	 * @param qualifier The version qualifier
	 */
	public Version(int major, int minor, int patch, String qualifier) {
		this.major = major;
		this.minor = minor;
		this.patch = patch;
		this.qualifier = qualifier;
	}

	@Override
	public int compareTo(Version o) {
		int comp = Integer.compare(major, o.major);
		if (comp == 0)
			comp = Integer.compare(minor, o.minor);
		if (comp == 0)
			comp = Integer.compare(patch, o.patch);
		if (comp == 0) {//
			if (qualifier != null) {
				if (o.qualifier != null)
					comp = qualifier.compareTo(o.qualifier);
				else
					comp = -1;
			} else if (o.qualifier != null) {
				comp = 1;
			}
		}
		return comp;
	}

	@Override
	public int hashCode() {
		return (major << 24) ^ (minor << 16) ^ (patch << 8) ^ (qualifier == null ? 0 : qualifier.hashCode());
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		else if (!(obj instanceof Version))
			return false;
		Version other = (Version) obj;
		return major == other.major//
			&& minor == other.minor//
			&& patch == other.patch//
			&& Objects.equals(qualifier, other.qualifier);
	}

	@Override
	public String toString() {
		StringBuilder str = new StringBuilder();
		str.append(major).append('.').append(minor).append('.').append(patch);
		if (qualifier != null)
			str.append('.').append(qualifier);
		return str.toString();
	}

	/**
	 * Parses a semantic version from text
	 * 
	 * @param version The text to parse
	 * @return The parsed version
	 * @throws IllegalArgumentException If the text does not match the form "XX.YY.ZZ(.QQQ)"
	 */
	public static Version parse(CharSequence version) throws IllegalArgumentException {
		Matcher match = VERSION_PATTERN.matcher(version);
		if (!match.matches())
			throw new IllegalArgumentException("Version sequence does not match \"XX.YY.ZZ(.QQQ)\": " + version);
		return new Version(//
			Integer.parseInt(match.group("major")), //
			Integer.parseInt(match.group("minor")), //
			Integer.parseInt(match.group("patch")), //
			match.group("qualifier"));
	}
}