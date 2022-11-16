package org.qommons.config;

import java.util.Objects;

import org.qommons.StringUtils;

/** An abstract class for something that is directly owned by a toolkit */
public abstract class AbstractQonfigType implements QonfigType {
	private final QonfigToolkit theDeclarer;
	private final String theName;
	private final int theLineNumber;

	/**
	 * @param declarer The toolkit that declared this type
	 * @param name The name of this type
	 * @param lineNumber The line number in the file where this type was defined
	 */
	public AbstractQonfigType(QonfigToolkit declarer, String name, int lineNumber) {
		theDeclarer = declarer;
		theName = name;
		theLineNumber = lineNumber;
	}

	@Override
	public QonfigToolkit getDeclarer() {
		return theDeclarer;
	}

	@Override
	public String getName() {
		return theName;
	}

	@Override
	public int getLineNumber() {
		return theLineNumber;
	}

	/**
	 * Implementation for {@link Comparable#compareTo(Object)} if the subclass chooses to use it
	 * 
	 * @param o The type to compare to
	 * @return The comparison with the given type
	 */
	protected int compareTo(AbstractQonfigType o) {
		int comp = StringUtils.compareNumberTolerant(theName, o.theName, true, true);
		if (comp == 0 && !theDeclarer.equals(o.theDeclarer))
			comp = theDeclarer.getLocation().toString().compareTo(o.theDeclarer.getLocation().toString());
		return comp;
	}

	@Override
	public int hashCode() {
		return Objects.hash(theDeclarer, theName);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		else if (!(obj instanceof AbstractQonfigType))
			return false;
		return theDeclarer.equals(((AbstractQonfigType) obj).theDeclarer) && theName.equals(((AbstractQonfigType) obj).theName);
	}

	@Override
	public String toString() {
		return theName;
	}
}
