package org.qommons;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Semantic version class */
public class Version implements Comparable<Version> {
	/** A pattern to parse semantic version strings */
	public static final Pattern VERSION_PATTERN = Pattern
		.compile("v?(?<major>\\d+)\\.(?<minor>\\d+)\\.(?<patch>\\d+)(?:(?<join>[\\.\\-_])?(?<qualifier>[a-zA-Z0-9_\\-\\.]+))?");

	/** The major revision */
	public final int major;
	/** The minor revision */
	public final int minor;
	/** The patch revision */
	public final int patch;
	/** The version qualifier */
	public final String qualifier;
	/** A joiner character for the qualifier */
	public final char qualifierJoin;

	/**
	 * Creates a (potentially) qualified version
	 * 
	 * @param major The major revision
	 * @param minor The minor revision
	 * @param patch The patch revision
	 * @param qualifierJoin A join character for the qualifier
	 * @param qualifier The version qualifier
	 */
	public Version(int major, int minor, int patch, char qualifierJoin, String qualifier) {
		this.major = major;
		this.minor = minor;
		this.patch = patch;
		this.qualifierJoin = qualifierJoin;
		if (qualifier == null || qualifier.trim().isEmpty())
			this.qualifier = null;
		else
			this.qualifier = qualifier;
	}

	/**
	 * Creates an unqualified version
	 * 
	 * @param major The major revision
	 * @param minor The minor revision
	 * @param patch The patch revision
	 */
	public Version(int major, int minor, int patch) {
		this(major, minor, patch, (char) 0, null);
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
				// A qualifier that is just more version numbers should be treated as just further version qualification
				// A qualifier that has text (e.g. rc, er, etc.) implies a pre-release
				if (o.qualifier != null)
					comp = qualifier.compareTo(o.qualifier);
				else if (isNumeric(qualifier))
					comp = 1;
				else
					comp = -1;
			} else if (o.qualifier != null) {
				if (isNumeric(o.qualifier))
					comp = -1;
				else
					comp = 1;
			}
		}
		return comp;
	}

	private static boolean isNumeric(String qualifier) {
		for (int c = 0; c < qualifier.length(); c++) {
			char ch = qualifier.charAt(c);
			if ((ch < '0' || ch > '9') && ch != '.' && ch != '-' && ch != '_')
				return false;
		}
		return true;
	}

	/** @return A {@link Version} that is the same as this, but with no {@link #qualifier} */
	public Version unqualified() {
		if (qualifier == null)
			return this;
		else
			return new Version(major, minor, patch);
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
		if (qualifier != null) {
			if (qualifierJoin != 0)
				str.append(qualifierJoin);
			str.append(qualifier);
		}
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
		Matcher versionMatch = VERSION_PATTERN.matcher(version);
		if (!versionMatch.matches())
			throw new IllegalArgumentException("Version sequence does not match \"XX.YY.ZZ(.QQQ)\": " + version);
		return parse(versionMatch);
	}

	/**
	 * Parses a version from a {@link #VERSION_PATTERN} match
	 * 
	 * @param versionMatch The {@link #VERSION_PATTERN} match for the text to parse
	 * @return The version corresponding to the text match, or null if the pattern does not match
	 */
	public static Version parse(Matcher versionMatch) {
		if (!versionMatch.matches())
			return null;
		String qualifier = versionMatch.group("qualifier");
		String join = versionMatch.group("join");
		return new Version(//
			Integer.parseInt(versionMatch.group("major")), //
			Integer.parseInt(versionMatch.group("minor")), //
			Integer.parseInt(versionMatch.group("patch")), //
			(join == null || join.isEmpty()) ? (char) 0 : join.charAt(0), //
			qualifier);
	}
}