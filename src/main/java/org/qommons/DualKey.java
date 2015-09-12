/**
 * DualKey.java Created Sep 25, 2007 by Andrew Butler, PSL
 */
package org.qommons;

/**
 * Implements a map key with two hashable components. Hashing is done using the {@link ArrayUtils} class, so arrays of hashable objects may
 * be used for the component keys.
 * 
 * @param <K1> The type of the first key
 * @param <K2> The type of the second key
 */
public class DualKey<K1, K2>
{
	private final K1 theKey1;

	private final K2 theKey2;

	/**
	 * Creates a DualKey
	 * 
	 * @param key1 The first key component
	 * @param key2 The second key component
	 */
	public DualKey(K1 key1, K2 key2)
	{
		theKey1 = key1;
		theKey2 = key2;
	}

	/** @return This key's first component */
	public K1 getKey1()
	{
		return theKey1;
	}

	/** @return This key's second component */
	public K2 getKey2()
	{
		return theKey2;
	}

	@Override
	public boolean equals(Object o)
	{
		if(!(o instanceof DualKey<?, ?>))
			return false;
		return ArrayUtils.equals(((DualKey<?, ?>) o).theKey1, theKey1) && ArrayUtils.equals(((DualKey<?, ?>) o).theKey2, theKey2);
	}

	@Override
	public int hashCode()
	{
		return ArrayUtils.hashCode(theKey1) * 31 + ArrayUtils.hashCode(theKey2);
	}

	@Override
	public String toString()
	{
		return "{" + theKey1 + " & " + theKey2 + "}";
	}
}
