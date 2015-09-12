/*
 * Sealable.java Created Jul 8, 2011 by Andrew Butler, PSL
 */
package org.qommons;

/**
 * <p>
 * A sealable item is one that can be modified until the {@link #seal()} method is called. After
 * this method is called, the item becomes immutable and any attempt to modify it will result in a
 * {@link SealedException} being thrown. This interface may also be used to seal only subsets of a
 * type's changeable data.
 * </p>
 * <p>
 * Sealable types typically implement {@link Cloneable}, and a cloned object returned from
 * {@link Object#clone()} will typically be unsealed.
 * </p>
 */
public interface Sealable
{
	/** The type of exception that is thrown when an attempt is made to modify a sealed object */
	public static class SealedException extends RuntimeException
	{
		/**
		 * A shortcut constructor that generates a simple message describing the error
		 * 
		 * @param item The item that has been sealed
		 */
		public SealedException(Sealable item)
		{
			super("Sealable item " + item + " (type " + item.getClass().getName()
				+ ") has been sealed and cannot be modified");
		}

		/** @param message The message for the exception */
		public SealedException(String message)
		{
			super(message);
		}
	}

	/** @return Whether this item has been sealed */
	boolean isSealed();

	/** Seals this item, causing it to become immutable. Normally this cannot be undone. */
	void seal();
}
