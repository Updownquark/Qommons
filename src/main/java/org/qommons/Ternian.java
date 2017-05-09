package org.qommons;

/** A ternian is a boolean that can have an additional NONE state, indicating that it is neither true nor false */
public enum Ternian {
	/** Logically equivalent to {@link Boolean#FALSE} */
	FALSE(Boolean.FALSE),
	/** Neither true nor false */
	NONE(null),
	/** Logically equivalent to {@link Boolean#TRUE} */
	TRUE(Boolean.TRUE);

	/** <code>null</code> for {@link #NONE}, otherwise the corresponding <code>true</code> or <code>false</code> boolean */
	public final Boolean value;

	private Ternian(Boolean value) {
		this.value = value;
	}

	/**
	 * @param def The default to use if this value is {@link #NONE}
	 * @return This value as a boolean, or <code>def</code> if this is {@link #NONE}
	 */
	public boolean withDefault(boolean def) {
		if (value != null)
			return value;
		else
			return def;
	}

	/**
	 * @param b The nullable boolean value to represent with a Ternian
	 * @return {@link #NONE} if the boolean is null, otherwise the corresponding {@link #TRUE} or {@link #FALSE} value
	 */
	public static Ternian of(Boolean b) {
		if (b == null)
			return NONE;
		else if (b)
			return TRUE;
		else
			return FALSE;
	}
}
