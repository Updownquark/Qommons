/*
 * IntList.java Created Aug 310, 2010 by Andrew Butler, PSL
 */
package org.qommons;

/**
 * <p>
 * Acts like an {@link java.util.ArrayList} but for primitive boolean values.
 * </p>
 *
 * <p>
 * This class is NOT thread-safe. If an instance of this class is accessed by multiple threads and
 * may be modified by one or more of them, it MUST be synchronized externally.
 * </p>
 */
public class BooleanList implements Iterable<Boolean>, Sealable, Cloneable
{
	private boolean [] theValue;

	private int theSize;

	private boolean isSealed;

	/** Creates a list with a capacity of 5 */
	public BooleanList()
	{
		this(5);
	}

	/**
	 * Creates a list with a set capacity
	 *
	 * @param size The initial capacity of the list
	 */
	public BooleanList(int size)
	{
		theValue = new boolean [size];
	}

	/**
	 * Creates a list with a set of values
	 *
	 * @param values The values for the list
	 */
	public BooleanList(boolean [] values)
	{
		theValue = values;
		theSize = values.length;
	}

	/** @return The number of elements in the list */
	public int size()
	{
		return theSize;
	}

	/** @return Whether this list is empty of elements */
	public boolean isEmpty()
	{
		return theSize == 0;
	}

	/** Clears this list, setting its size to 0 */
	public void clear()
	{
		assertUnsealed();
		theSize = 0;
	}

	@Override
	public boolean isSealed()
	{
		return isSealed;
	}

	@Override
	public void seal()
	{
		trimToSize();
		isSealed = true;
	}

	void assertUnsealed()
	{
		if(isSealed)
			throw new Sealable.SealedException(this);
	}

	/**
	 * Gets the value in the list at the given index
	 *
	 * @param index The index of the value to get
	 * @return The value at the given index
	 */
	public boolean get(int index)
	{
		if(index < 0 || index >= theSize)
			throw new ArrayIndexOutOfBoundsException(index);
		return theValue[index];
	}

	/**
	 * Adds a value to this list.
	 *
	 * @param value The value to add to the list
	 */
	public void add(boolean value)
	{
		assertUnsealed();
		ensureCapacity(theSize + 1);
		theValue[theSize++] = value;
	}

	/**
	 * Adds a value to this list at the given index.
	 *
	 * @param index The index to add the value at
	 * @param value The value to add to the list
	 */
	public void add(int index, boolean value)
	{
		assertUnsealed();
		if(index < 0 || index > theSize)
			throw new ArrayIndexOutOfBoundsException(index);
		ensureCapacity(theSize + 1);
		for(int i = theSize; i > index; i--)
			theValue[i] = theValue[i - 1];
		theValue[index] = value;
		theSize++;
	}

	/**
	 * Adds an array of values to the end of this list.
	 *
	 * @param value The values to add
	 */
	public void addAll(boolean... value)
	{
		addAll(value, 0, value.length);
	}

	/**
	 * Adds all elements of the given array within the given range.
	 *
	 * @param value The array with the values to add
	 * @param start The starting index (inclusive) of the values in the array to add
	 * @param end The end index (exclusive) of the value in the array to add
	 */
	public void addAll(boolean [] value, int start, int end)
	{
		assertUnsealed();
		if(start >= value.length)
			return;
		if(end > value.length)
			end = value.length;

		ensureCapacity(theSize + end - start);
		for(int i = start; i < end; i++)
			theValue[theSize + i - start] = value[i];
		theSize += end - start;
	}

	/**
	 * Adds a list of values to the end of this list
	 *
	 * @param list The list of values to add
	 */
	public void addAll(BooleanList list)
	{
		addAll(list.theValue, 0, list.theSize);
	}

	/**
	 * <p>
	 * Replaces a value in this list with another value.
	 * </p>
	 *
	 * @param index The index of the value to replace
	 * @param value The value to replace the old value with
	 * @return The old value at the given index
	 */
	public boolean set(int index, boolean value)
	{
		assertUnsealed();
		if(index < 0 || index >= theSize)
			throw new ArrayIndexOutOfBoundsException(index);
		boolean ret = theValue[index];
		theValue[index] = value;
		return ret;
	}

	/**
	 * Removes a value from this list
	 *
	 * @param index The index of the value to remove
	 * @return The value that was removed
	 */
	public boolean remove(int index)
	{
		assertUnsealed();
		if(index < 0 || index >= theSize)
			throw new ArrayIndexOutOfBoundsException(index);
		boolean ret = theValue[index];
		for(int i = index; i < theSize - 1; i++)
			theValue[i] = theValue[i + 1];
		theSize--;
		return ret;
	}

	/**
	 * Removes all instances of the given value from this list
	 *
	 * @param value The value to remove
	 * @return The number of times the value was removed
	 */
	public int removeAll(boolean value)
	{
		assertUnsealed();
		int ret = 0;
		for(int i = 0; i < theSize; i++)
			if(theValue[i] == value)
			{
				remove(i);
				i--;
				ret++;
			}
		return ret;
	}

	/**
	 * Switches the positions of two values
	 *
	 * @param idx1 The index of the first value to switch
	 * @param idx2 The index of the second value to switch
	 */
	public void swap(int idx1, int idx2)
	{
		assertUnsealed();
		if(idx1 < 0 || idx1 >= theSize)
			throw new ArrayIndexOutOfBoundsException(idx1);
		if(idx2 < 0 || idx2 >= theSize)
			throw new ArrayIndexOutOfBoundsException(idx2);
		boolean temp = theValue[idx1];
		theValue[idx1] = theValue[idx2];
		theValue[idx2] = temp;
	}

	@Override
	public java.util.ListIterator<Boolean> iterator()
	{
		return new BooleanListIterator(toArray());
	}

	/**
	 * Determines if this list contains a given value
	 *
	 * @param value The value to find
	 * @return Whether this list contains the given value
	 */
	public boolean contains(boolean value)
	{
		return indexOf(value) >= 0;
	}

	/**
	 * Counts the number of times a value is represented in this list
	 *
	 * @param value The value to count
	 * @return The number of times the value appears in this list
	 */
	public int instanceCount(boolean value)
	{
		int ret = 0;
		for(int i = 0; i < theSize; i++)
			if(theValue[i] == value)
				ret++;
		return ret;
	}

	/**
	 * Finds a value in this list
	 *
	 * @param value The value to find
	 * @return The first index whose value is the given value
	 */
	public int indexOf(boolean value)
	{
		for(int i = 0; i < theSize; i++)
			if(theValue[i] == value)
				return i;
		return -1;
	}

	/**
	 * Finds a value in this list
	 *
	 * @param value The value to find
	 * @return The last index whose value is the given value
	 */
	public int lastIndexOf(boolean value)
	{
		for(int i = theSize - 1; i >= 0; i--)
			if(theValue[i] == value)
				return i;
		return -1;
	}

	/** @return The list of values currently in this list */
	public boolean [] toArray()
	{
		boolean [] ret = new boolean [theSize];
		System.arraycopy(theValue, 0, ret, 0, theSize);
		return ret;
	}

	/**
	 * Similary to {@link #toArray()} but creates an array of {@link Boolean} wrappers
	 *
	 * @return The list of values currently in this list
	 */
	public Boolean [] toObjectArray()
	{
		Boolean [] ret = new Boolean [theSize];
		for(int i = 0; i < ret.length; i++)
			ret[i] = Boolean.valueOf(theValue[i]);
		return ret;
	}

	/**
	 * Copies a subset of this list's data into an array
	 *
	 * @param srcPos The index in this list to start copying from
	 * @param dest The array to copy the data into
	 * @param destPos The index in the destination array to start copying to
	 * @param length The number of items to copy
	 */
	public void arrayCopy(int srcPos, boolean [] dest, int destPos, int length)
	{
		int i = srcPos;
		int j = destPos;
		for(int k = 0; k < length; i++, j++, k++)
			dest[j] = theValue[i];
	}

	@Override
	public String toString()
	{
		StringBuilder ret = new StringBuilder();
		ret.append('[');
		for(int i = 0; i < theSize; i++)
		{
			if(i > 0)
				ret.append(", ");
			ret.append(theValue[i]);
		}
		ret.append(']');
		return ret.toString();
	}

	@Override
	public BooleanList clone()
	{
		BooleanList ret;
		try
		{
			ret = (BooleanList) super.clone();
		} catch(CloneNotSupportedException e)
		{
			throw new IllegalStateException("Clone not supported", e);
		}
		ret.theValue = theValue.clone();
		ret.isSealed = false;
		return ret;
	}

	/** Trims this list so that it wastes no space and its capacity is equal to its size */
	public void trimToSize()
	{
		if(theValue.length == theSize)
			return;
		boolean [] oldData = theValue;
		theValue = new boolean [theSize];
		System.arraycopy(oldData, 0, theValue, 0, theSize);
	}

	/**
	 * Ensures that this list's capacity is at list the given value
	 *
	 * @param minCapacity The minimum capacity for the list
	 */
	public void ensureCapacity(int minCapacity)
	{
		if(minCapacity <= theSize)
			return;
		assertUnsealed();
		int oldCapacity = theValue.length;
		if(minCapacity > oldCapacity)
		{
			boolean oldData[] = theValue;
			int newCapacity = (oldCapacity * 3) / 2 + 1;
			if(newCapacity < minCapacity)
				newCapacity = minCapacity;
			theValue = new boolean [newCapacity];
			System.arraycopy(oldData, 0, theValue, 0, theSize);
		}
	}

	private class BooleanListIterator implements java.util.ListIterator<Boolean>
	{
		private boolean [] theContent;

		private int theIndex;

		private boolean lastRemoved;

		BooleanListIterator(boolean [] content)
		{
			theContent = content;
		}

		@Override
		public boolean hasNext()
		{
			return theIndex < theContent.length;
		}

		@Override
		public Boolean next()
		{
			Boolean ret = Boolean.valueOf(theContent[theIndex]);
			theIndex++;
			lastRemoved = false;
			return ret;
		}

		@Override
		public boolean hasPrevious()
		{
			return theIndex > 0;
		}

		@Override
		public Boolean previous()
		{
			Boolean ret = Boolean.valueOf(theContent[theIndex - 1]);
			theIndex--;
			lastRemoved = false;
			return ret;
		}

		@Override
		public int nextIndex()
		{
			return theIndex;
		}

		@Override
		public int previousIndex()
		{
			return theIndex - 1;
		}

		@Override
		public void remove()
		{
			if(lastRemoved)
				throw new IllegalStateException(
					"remove() can only be called once with each call to" + " next() or previous()");
			if(get(theIndex) != theContent[theIndex])
				throw new java.util.ConcurrentModificationException(
					"list has been modified apart from this iterator");
			BooleanList.this.remove(theIndex);
			boolean [] newContent = new boolean [theContent.length - 1];
			System.arraycopy(theContent, 0, newContent, 0, theIndex);
			System.arraycopy(theContent, theIndex + 1, newContent, theIndex, newContent.length
				- theIndex);
			theContent = newContent;
			lastRemoved = true;
		}

		@Override
		public void set(Boolean e)
		{
			if(lastRemoved)
				throw new IllegalStateException("set() cannot be called after remove()");
			if(get(theIndex) != theContent[theIndex])
				throw new java.util.ConcurrentModificationException(
					"List has been modified apart from this iterator");
			theContent[theIndex] = e.booleanValue();
			BooleanList.this.set(theIndex, e.booleanValue());
		}

		@Override
		public void add(Boolean e)
		{
			if(get(theIndex) != theContent[theIndex])
				throw new java.util.ConcurrentModificationException(
					"List has been modified apart from this iterator");
			BooleanList.this.add(theIndex, e.booleanValue());
			boolean [] newContent = new boolean [theContent.length + 1];
			System.arraycopy(theContent, 0, newContent, 0, theIndex);
			System.arraycopy(theContent, theIndex, newContent, theIndex + 1, theContent.length
				- theIndex);
			newContent[theIndex] = e.booleanValue();
			theIndex++;
			theContent = newContent;
			lastRemoved = false;
		}
	}
}
