package org.qommons;

/** A hashable map key that is a composite of any number of other keys */
public class MultiKey
{
	private Object [] theKeys;

	/** @param keys The keys for this multi-key */
	public MultiKey(Object... keys)
	{
		theKeys = keys;
	}

	/**
	 * @param index The index of the key to get
	 * @return The key in this multi-key at the given index
	 */
	public Object getKey(int index)
	{
		return theKeys[index];
	}

	@Override
	public boolean equals(Object o)
	{
		return o instanceof MultiKey && ArrayUtils.equals(theKeys, ((MultiKey) o).theKeys);
	}

	@Override
	public int hashCode()
	{
		return ArrayUtils.hashCode(theKeys);
	}
}
