package org.qommons.config;

import java.util.Objects;

import org.qommons.StringUtils;
import org.qommons.io.PositionedContent;

/** An abstract class for something that is directly owned by a toolkit */
public abstract class AbstractQonfigType implements QonfigType {
	private final QonfigToolkit theDeclarer;
	private final String theName;
	private final PositionedContent thePosition;
	private final String theDescription;

	/**
	 * @param declarer The toolkit that declared this type
	 * @param name The name of this type
	 * @param position The position in the file where this type was defined
	 * @param description The description of this declared type
	 */
	protected AbstractQonfigType(QonfigToolkit declarer, String name, PositionedContent position, String description) {
		theDeclarer = declarer;
		theName = name;
		thePosition = position;
		theDescription = description;
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
	public PositionedContent getFilePosition() {
		return thePosition;
	}

	@Override
	public String getDescription() {
		return theDescription;
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
