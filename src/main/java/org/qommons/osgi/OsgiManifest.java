package org.qommons.osgi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.qommons.QommonsUtils;
import org.qommons.Range;
import org.qommons.Version;

/** A parser and API for using OSGi-style manifest files */
public class OsgiManifest {
	/**
	 * Represents an entry in the manifest. An entry represents a single value in a manifest property. Each property may have any number of
	 * entries.
	 */
	public static class ManifestEntry {
		private final String theValue;
		private final Map<String, String> theAttributes;

		/**
		 * @param value The value of the entry
		 * @param attrs The attributes for the entry
		 */
		public ManifestEntry(String value, Map<String, String> attrs) {
			theValue = value;
			theAttributes = QommonsUtils.unmodifiableCopy(attrs);
		}

		/** @return The entry's value */
		public String getValue() {
			return theValue;
		}

		/** @return The attributes of the entry */
		public Map<String, String> getAttributes() {
			return theAttributes;
		}

		/**
		 * Parses the "version" or "bundle-version" attribute
		 *
		 * @return The version range for this entry
		 */
		public Range<Version> getVersion() {
			String versionStr = theAttributes.get("version");
			if (versionStr == null) {
				versionStr = theAttributes.get("bundle-version");
			}
			if (versionStr == null) {
				return Range.all();
			}
			if (versionStr.charAt(0) == '(' || versionStr.charAt(0) == '[') {
				boolean lowClosed = versionStr.charAt(0) == '[';
				boolean highClosed;
				switch (versionStr.charAt(versionStr.length() - 1)) {
				case ']':
					highClosed = true;
					break;
				case ')':
					highClosed = false;
					break;
				default:
					throw new IllegalArgumentException("Bad range--must end with ']' or ')'");
				}
				int comma = versionStr.indexOf(',');
				if (comma < 0) {
					throw new IllegalArgumentException("Bad range--',' expected");
				}
				return Range.between(Version.parse(versionStr.substring(1, comma).trim()), lowClosed, //
					Version.parse(versionStr.substring(comma + 1, versionStr.length() - 1).trim()), highClosed);
			} else {
				return Range.exactly(Version.parse(versionStr));
			}
		}

		/**
		 * @param attr The name of the attribute
		 * @param defValue The default value to return if the attribute is not set or is not recognized as a boolean value
		 * @return Whether the given attribute is set to true
		 */
		public boolean is(String attr, boolean defValue) {
			String value = theAttributes.get(attr);
			if (value == null) {
				return defValue;
			}
			switch (value.toLowerCase()) {
			case "true":
			case "t":
			case "yes":
			case "y":
				return true;
			case "false":
			case "f":
			case "no":
			case "n":
				return false;
			default:
				return defValue;
			}
		}

		@Override
		public int hashCode() {
			return Objects.hash(theValue, theAttributes);
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			} else if (!(o instanceof ManifestEntry)) {
				return false;
			}
			return theValue.equals(((ManifestEntry) o).theValue) && theAttributes.equals(((ManifestEntry) o).theAttributes);
		}

		@Override
		public String toString() {
			if (theAttributes.isEmpty()) {
				return theValue;
			}
			StringBuilder str = new StringBuilder(theValue);
			for (Map.Entry<String, String> attr : theAttributes.entrySet()) {
				str.append(';').append(attr.getKey()).append('=').append('"').append(attr.getValue()).append('"');
			}
			return str.toString();
		}
	}

	/**
	 * @param entries The entry list to print
	 * @param str The string builder to print the entries to (may be null)
	 * @return The string builder given (or a new one if null was given) with the entries printed to it
	 */
	public static StringBuilder printManifestSet(List<ManifestEntry> entries, StringBuilder str) {
		if (str == null) {
			str = new StringBuilder();
		}
		for (ManifestEntry entry : entries) {
			if (str.length() > 0) {
				str.append(",\n ");
			}
			str.append(entry);
		}
		return str;
	}

	private final Map<String, List<ManifestEntry>> theEntries;

	OsgiManifest(Map<String, List<ManifestEntry>> entries) {
		theEntries = entries;
	}

	/**
	 * @param entryName The name of the entry
	 * @param requireOne Whether to throw an {@link IllegalArgumentException} if the entry does not exist or if there are multiple such
	 *        entries
	 * @return The first entry with the given name, or null if none exists
	 */
	public ManifestEntry get(String entryName, boolean requireOne) {
		List<ManifestEntry> entries = theEntries.get(entryName);
		if (entries == null) {
			if (requireOne) {
				throw new IllegalArgumentException("No entry named " + entryName);
			} else {
				return null;
			}
		} else if (entries.size() > 1) {
			throw new IllegalArgumentException("Multiple (" + entries.size() + ") entries named " + entryName);
		}
		return entries.get(0);
	}

	/**
	 * @param entryName The name of the entry
	 * @return All entries in the manifest with the given name
	 */
	public List<ManifestEntry> getAll(String entryName) {
		return theEntries.getOrDefault(entryName, Collections.emptyList());
	}

	@Override
	public int hashCode() {
		return theEntries.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof OsgiManifest && theEntries.equals(((OsgiManifest) o).theEntries);
	}

	@Override
	public String toString() {
		StringBuilder str = new StringBuilder();
		for (Map.Entry<String, List<ManifestEntry>> entry : theEntries.entrySet()) {
			if (str.length() > 0) {
				str.append('\n');
			}
			str.append(entry.getKey()).append(": ");
			printManifestSet(entry.getValue(), str);
		}
		return str.toString();
	}

	/** @return A builder to create an {@link OsgiManifest} */
	public static Builder build() {
		return new Builder();
	}

	/** The Pattern to parse a single attribute name/value from an entry */
	public static final Pattern MANIFEST_ATTRIBUTE_PATTERN = Pattern.compile("([^;:=]+)\\s*:?=\\s*([^;=]*)\\s*");
	/** The Pattern to parse a single manifest entry value */
	public static final Pattern MANIFEST_ENTRY_PATTERN = Pattern
		.compile("(?<value>[^:=]+)\\s*(?<attrs>(;" + MANIFEST_ATTRIBUTE_PATTERN.pattern() + ")*)");

	/** Builds an {@link OsgiManifest} */
	public static class Builder {
		private final Map<String, List<ManifestEntry>> theEntries;

		Builder() {
			theEntries = new LinkedHashMap<>();
		}

		/**
		 * Builds the manifest from an OSGi manifest file
		 * 
		 * @param manifest The reader of the manifest file
		 * @return This builder
		 * @throws IOException If the manifest cannot be read
		 * @throws IllegalArgumentException If the manifest cannot be parsed
		 */
		public Builder read(Reader manifest) throws IOException, IllegalArgumentException {
			List<ManifestEntry> currentEntry = null;
			Map<String, String> attributes = new LinkedHashMap<>();
			BufferedReader br = manifest instanceof BufferedReader ? (BufferedReader) manifest : new BufferedReader(manifest);
			String lastEntryName = null;
			int lineNumber = 1, lastNonEmptyLine = 0;
			for (String line = br.readLine(); line != null; line = br.readLine(), lineNumber++) {
				int lastCh = line.length();
				while (lastCh > 0 && Character.isWhitespace(line.charAt(lastCh - 1))) {
					lastCh--;
				}
				if (lastCh == 0) {
					continue;
				} else if (lastCh < line.length()) {
					line = line.substring(0, lastCh);
				}
				while (line.charAt(line.length() - 1) == ';') { // More attributes on following lines
					String nextLine = br.readLine();
					lineNumber++;
					if (!Character.isWhitespace(nextLine.charAt(0))) {
						throw new IllegalArgumentException("Space expected on continued entry at line " + lineNumber);
					}
					lastCh = nextLine.length();
					while (lastCh > 0 && Character.isWhitespace(nextLine.charAt(lastCh - 1))) {
						lastCh--;
					}
					if (lastCh == 0) {
						continue;
					}
					line += nextLine.substring(1, lastCh);
				}
				boolean terminal = line.charAt(line.length() - 1) != ',';
				String entryValue;
				if (currentEntry == null) { // New entry
					if (Character.isWhitespace(line.charAt(0))) {
						throw new IllegalArgumentException(
							"Missing ',' on non-terminal entry '" + lastEntryName + "' at line " + lastNonEmptyLine);
					}
					int colon = line.indexOf(':');
					if (colon < 0) {
						throw new IllegalArgumentException("No ':' in entry on line " + lineNumber);
					}
					String entryName = line.substring(0, colon).trim();
					int size = terminal ? 1 : 5;
					lastEntryName = entryName;
					currentEntry = theEntries.computeIfAbsent(entryName, __ -> new ArrayList<>(size));
					entryValue = parseEntry(//
						line.substring(colon + 1, line.length() - (terminal ? 0 : 1)).trim(), attributes, lineNumber);
				} else {
					if (!Character.isWhitespace(line.charAt(0))) {
						throw new IllegalArgumentException("Space expected on continued entry at line " + lineNumber);
					}
					entryValue = parseEntry(//
						line.substring(1, line.length() - (terminal ? 0 : 1)).trim(), attributes, lineNumber);
				}
				if (entryValue != null) {
					ManifestEntry newEntry = new ManifestEntry(entryValue, QommonsUtils.unmodifiableCopy(attributes));
					currentEntry.add(newEntry);
				} else {
					terminal = false;
				}
				if (terminal) {
					currentEntry = null;
				}

				lastNonEmptyLine = lineNumber;
				attributes.clear();
			}
			return this;
		}

		private static String parseEntry(String entryString, Map<String, String> attributes, int lineNumber) {
			if (entryString.isEmpty()) {
				return null;
			}
			Matcher match = MANIFEST_ENTRY_PATTERN.matcher(entryString);
			if (!match.matches()) {
				throw new IllegalArgumentException("Bad manifest entry at line " + lineNumber + ": " + entryString);
			}
			String value = match.group("value");
			String attrStr = match.group("attrs");
			if (attrStr == null || attrStr.isEmpty()) {
				return value;
			}
			for (String attr : attrStr.split(";")) {
				if (attr.isEmpty()) {
					continue;
				}
				Matcher attrMatch = MANIFEST_ATTRIBUTE_PATTERN.matcher(attr);
				if (!attrMatch.matches()) {
					throw new IllegalStateException("Bad manifest entry at line " + lineNumber + ": " + entryString);
				}
				String attrValue = attrMatch.group(2).trim();
				if (attrValue.length() > 0 && attrValue.charAt(0) == '"') {
					if (attrValue.charAt(attrValue.length() - 1) != '"') {
						throw new IllegalArgumentException("Manifest attributes beginning with '\"' must also end with '\"'");
					}
					attrValue = attrValue.substring(1, attrValue.length() - 1);
				}
				attributes.put(attrMatch.group(1).trim(), attrValue);
			}
			return value;
		}

		/**
		 * @param entryName The name of the entry/entries to add
		 * @param builder Builds out the values for the entry
		 * @return This builder
		 */
		public Builder addEntry(String entryName, Consumer<ManifestEntrySetBuilder> builder) {
			ManifestEntrySetBuilder entrySetBuilder = new ManifestEntrySetBuilder();
			builder.accept(entrySetBuilder);
			if (entrySetBuilder.theEntries.isEmpty()) {
				throw new IllegalStateException("Manifest entries must have at least one value: " + entryName);
			}
			theEntries.compute(entryName, (__, old) -> {
				if (old == null) {
					old = entrySetBuilder.theEntries;
				} else {
					old.addAll(entrySetBuilder.theEntries);
				}
				return old;
			});
			return this;
		}

		/** @return The built manifest */
		public OsgiManifest build() {
			Map<String, List<ManifestEntry>> entries = new HashMap<>((theEntries.size() + 3) * 3 / 2);
			for (Map.Entry<String, List<ManifestEntry>> entry : theEntries.entrySet()) {
				entries.put(entry.getKey(), QommonsUtils.unmodifiableCopy(entry.getValue()));
			}
			return new OsgiManifest(Collections.unmodifiableMap(entries));
		}
	}

	/** Adds entries with a particular name to an {@link OsgiManifest} {@link Builder} */
	public static class ManifestEntrySetBuilder {
		final List<ManifestEntry> theEntries;

		ManifestEntrySetBuilder() {
			theEntries = new ArrayList<>(3);
		}

		/**
		 * Adds a value to the manifest
		 *
		 * @param value The value for the manifest entry
		 * @param attributes The attributes for the entry
		 * @return This builder
		 */
		public ManifestEntrySetBuilder add(String value, Consumer<Map<String, String>> attributes) {
			Map<String, String> attrs;
			if (attributes == null) {
				attrs = Collections.emptyMap();
			} else {
				attrs = new LinkedHashMap<>();
				attributes.accept(attrs);
				attrs = QommonsUtils.unmodifiableCopy(attrs);
			}
			theEntries.add(new ManifestEntry(value, attrs));
			return this;
		}
	}
}
