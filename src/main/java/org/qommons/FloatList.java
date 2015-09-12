/*
 * FloatList.java Created Aug 10, 2010 by Andrew Butler, PSL
 */
package org.qommons;

/**
 * <p>
 * Acts like an {@link java.util.ArrayList} but for primitive float values.
 * </p>
 *
 * <p>
 * This class also has a sorted option that will cause the list to sort itself and maintain the
 * sorted order (ascending). If this option is set, calls to {@link #add(int, float)} will disregard
 * the index parameter to maintain correct order. Calls to {@link #set(int, float)} will behave the
 * same as successive calls to {@link #remove(int)} and {@link #add(float)}.
 * </p>
 *
 * <p>
 * This class also has a unique option that will cause the list to contain at most one instance of
 * any value. If this option is set, calls to {@link #add(float)}, {@link #add(int, float)},
 * {@link #addAll(float...)}, etc. will not always add the values given if the value(s) already
 * exist. The unique feature has better performance if used with the sorted feature, but both
 * features may be used independently.
 * </p>
 *
 * <p>
 * This class is NOT thread-safe. If an instance of this class is accessed by multiple threads, it
 * MUST be synchronized externally.
 * </p>
 */
public class FloatList implements Iterable<Float>, Sealable, Cloneable
{
	private float [] theValue;

	private int theSize;

	private boolean isSorted;

	private boolean isUnique;

	private boolean isSealed;

	/** Creates a list with a capacity of 5 */
	public FloatList()
	{
		this(5);
	}

	/**
	 * Creates a float list with the option of having the list sorted and/or unique-constrained
	 * initially
	 *
	 * @param sorted Whether the list should be sorted
	 * @param unique Whether the list should eliminate duplicate values
	 */
	public FloatList(boolean sorted, boolean unique)
	{
		this(5);
		isSorted = sorted;
		isUnique = unique;
	}

	/**
	 * Creates a list with a set capacity
	 *
	 * @param size The initial capacity of the list
	 */
	public FloatList(int size)
	{
		theValue = new float [size];
	}

	/**
	 * Creates a list with a set of values
	 *
	 * @param values The values for the list
	 */
	public FloatList(float [] values)
	{
		theValue = values;
		theSize = values.length;
	}

	/** @return Whether this list sorts its elements */
	public boolean isSorted()
	{
		return isSorted;
	}

	/**
	 * Sets whether this list should keep itself sorted or not. If set to true, this method will
	 * sort the current value set.
	 *
	 * @param sorted Whether the elements in this list should be sorted or not
	 */
	public void setSorted(boolean sorted)
	{
		if(isSealed)
			throw new IllegalStateException("This list has been sealed and cannot be modified");
		if(sorted && !isSorted)
			java.util.Arrays.sort(theValue, 0, theSize);
		isSorted = sorted;
	}

	/**
	 * @return Whether the elements in this list are in ascending order, regardless of the value of
	 *         the sorted property of this list.
	 */
	public boolean checkSorted()
	{
		float preV = Float.MIN_VALUE;
		for(int i = 0; i < theSize; i++)
		{
			if(compare(preV, theValue[i]) > 0)
				return false;
			preV = theValue[i];
		}
		return true;
	}

	/** @return Whether this list eliminates duplicate values */
	public boolean isUnique()
	{
		return isUnique;
	}

	/**
	 * Sets whether this list should accept duplicate values. If set to true, this method will
	 * eliminate duplicate values that may exist in the current set.
	 *
	 * @param unique Whether this list should eliminate duplicate values
	 */
	public void setUnique(boolean unique)
	{
		if(isSealed)
			throw new IllegalStateException("This list has been sealed and cannot be modified");
		if(unique && !isUnique)
		{
			if(isSorted)
				for(int i = 0; i < theSize - 1; i++)
					while(i < theSize - 1 && theValue[i + 1] == theValue[i])
						remove(i + 1);
			else
				for(int i = 0; i < theSize - 1; i++)
				{
					int idx = lastIndexOf(theValue[i]);
					while(idx != i)
					{
						remove(idx);
						idx = lastIndexOf(theValue[i]);
					}
				}
		}
		isUnique = unique;
	}

	/**
	 * @return False if there are any duplicates in this list, true otherwise; regardless of the
	 *         value of the unique property of this list.
	 */
	public boolean checkUnique()
	{
		for(int i = 0; i < theSize - 1; i++)
			if(lastIndexOf(theValue[i]) != i)
				return false;
		return true;
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
	public float get(int index)
	{
		if(index < 0 || index >= theSize)
			throw new ArrayIndexOutOfBoundsException(index);
		return theValue[index];
	}

	/**
	 * Adds a value to this list.
	 *
	 * <p>
	 * If this list is sorted, the value will be inserted at the index where it belongs; otherwise
	 * the value will be added to the end of the list.
	 * </p>
	 *
	 * <p>
	 * If this list is unique, the value will not be added if it already exists in the list
	 * </p>
	 *
	 * @param value The value to add to the list
	 * @return Whether the value was added. This will only be false if this list is unique and the
	 *         value already exists.
	 */
	public boolean add(float value)
	{
		assertUnsealed();
		ensureCapacity(theSize + 1);
		if(isSorted)
		{
			int index = indexFor(value);
			if(isUnique && index < theSize && theValue[index] == value)
				return false;
			for(int i = theSize; i > index; i--)
				theValue[i] = theValue[i - 1];
			theValue[index] = value;
			theSize++;
		}
		else if(!isUnique || indexOf(value) < 0)
			theValue[theSize++] = value;
		return true;
	}

	/**
	 * Performs a binary search to find the location where the given value would belong in this
	 * list. If there already exist more than one instance of the given value, the result will be
	 * the index of one of these, but the exact index of the result is undetermined if more than one
	 * instance exists.
	 *
	 * @param value The value to find the index for
	 * @return The index at which the given value would be added into this array from an
	 *         {@link #add(float) add} operation.
	 * @throws IllegalStateException If this list is not sorted
	 */
	public int indexFor(float value)
	{
		if(!isSorted)
			throw new IllegalStateException("The indexFor method is only meaningful for a"
				+ " sorted list");
		if(theSize == 0)
			return 0;
		int min = 0, max = theSize - 1;
		while(min < max)
		{
			int mid = (min + max) >>> 1;
		int diff = compare(value, theValue[mid]);
		if(diff > 0)
			min = mid + 1;
		else if(diff < 0)
			max = mid;
		else
			return mid;
		}
		if(compare(theValue[max], value) < 0)
			max++;
		return max;
	}

	/**
	 * Compares two float values
	 *
	 * @param v1 The first value to compare
	 * @param v2 The second value to compare
	 * @return -1 if v1<v2, 1 if v1>v2, or 0 if the two are equivalent
	 * @see Float#compare(float, float)
	 */
	public static int compare(float v1, float v2)
	{
		return Float.compare(v1, v2);
	}

	/**
	 * Adds a value to this list at the given index.
	 *
	 * <p>
	 * If this list is sorted, the index parameter will be ignored and the value will be inserted at
	 * the index where it belongs.
	 * </p>
	 *
	 * <p>
	 * If this list is unique, the value will not be added if it already exists in the list.
	 * <p>
	 *
	 * @param index The index to add the value at
	 * @param value The value to add to the list
	 * @return Whether the value was added. This will only be false if this list is unique and the
	 *         value already exists.
	 */
	public boolean add(int index, float value)
	{
		assertUnsealed();
		if(isSorted)
			return add(value);
		else if(!isUnique || indexOf(value) < 0)
		{
			if(index < 0 || index > theSize)
				throw new ArrayIndexOutOfBoundsException(index);
			ensureCapacity(theSize + 1);
			for(int i = theSize; i > index; i--)
				theValue[i] = theValue[i - 1];
			theValue[index] = value;
			theSize++;
			return true;
		}
		else
			return false;
	}

	/**
	 * Adds an array of values to the end of this list.
	 *
	 * <p>
	 * If this list is sorted, all values will be inserted into the indexes where they belong;
	 * otherwise the values will be added to the end of this list.
	 * </p>
	 *
	 * <p>
	 * If this list is unique, each value will only be added if it does not already exist in the
	 * list, and values that appear multiple times in the given set will be added once.
	 * </p>
	 *
	 * @param value The values to add
	 * @return The number of values added to this list
	 */
	public int addAll(float... value)
	{
		return addAll(value, 0, value.length, -1);
	}

	/**
	 * Adds all elements of the given array within the given range.
	 *
	 * <p>
	 * If this list is sorted, all values will be inserted into the indexes where they belong;
	 * otherwise the values will be added to the end of this list.
	 * </p>
	 *
	 * <p>
	 * If this list is unique, each value will only be added if it does not already exist in the
	 * list, and values that appear multiple times in the given set will be added once.
	 * </p>
	 *
	 * @param value The array with the values to add
	 * @param start The starting index (inclusive) of the values in the array to add
	 * @param end The end index (exclusive) of the value in the array to add
	 * @param insert The index to insert the values into. -1 inserts the values after the end. This
	 *        parameter is ignored for sorted lists
	 * @return The number of values added to this list
	 */
	public int addAll(float [] value, int start, int end, int insert)
	{
		assertUnsealed();
		if(start >= value.length)
			return 0;
		if(end > value.length)
			end = value.length;

		if(isSorted)
		{
			java.util.Arrays.sort(value, start, end);
			int i1 = 0, i2 = start;
			int count;
			if(isUnique)
			{
				// Remove duplicates in the additions
				float [] dup = new float[value.length];
				int dupIdx = 0;
				for(int i = 0; i < value.length; i++) {
					if(i > 0 && value[i] == value[i - 1])
						continue;
					if(contains(value[i]))
						continue;
					dup[dupIdx++] = value[i];
				}
				count = dupIdx;
				value = dup;
			}
			else
				count = end - start;
			if(count == 0)
				return 0;
			ensureCapacity(theSize + count);

			i1 = theSize - 1;
			i2 = end - 1;
			int i = theSize + count - 1;
			theSize += count;
			int ret2 = count;
			while(i >= 0 && ret2 > 0 && (i1 >= 0 || i2 >= start))
			{
				if(i1 < 0)
				{
					if(!isUnique || i == theSize - 1 || theValue[i + 1] != value[i2])
					{
						theValue[i--] = value[i2];
						ret2--;
					}
					i2--;
				}
				else if(i2 < start || theValue[i1] >= value[i2])
					theValue[i--] = theValue[i1--];
				else
				{
					if(!isUnique || i == theSize - 1 || theValue[i + 1] != value[i2])
					{
						theValue[i--] = value[i2];
						ret2--;
					}
					i2--;
				}
			}
			return count;
		}
		else
		{
			if(insert < 0)
				insert = theSize;
			java.util.BitSet unq = null;
			int count;
			if(isUnique)
			{
				unq = new java.util.BitSet();
				for(int i = start; i < end; i++)
					if(!contains(value[i]))
						unq.set(i);
				count = unq.cardinality();
			}
			else
				count = end - start;
			ensureCapacity(theSize + count);
			// Move to make room
			for(int i = theSize - 1; i >= insert; i--)
				theValue[i + count] = theValue[i];

			int j = insert;
			for(int i = start; i < end; i++)
				if(unq == null || unq.get(i))
					theValue[j++] = value[start + i];
			theSize += count;
			return count;
		}
	}

	/**
	 * Adds a list of values to the end of this list
	 *
	 * <p>
	 * If this list is sorted, all values will be inserted into the indexes where they belong;
	 * otherwise the values will be added to the end of this list
	 * </p>
	 *
	 * <p>
	 * If this list is unique, each value will only be added if it does not already exist in the
	 * list, and values that appear multiple times in the given set will be added once.
	 * </p>
	 *
	 * @param list The list of values to add
	 * @param insert The index to insert the values into. -1 inserts the values after the end. This
	 *        parameter is ignored for sorted lists
	 * @return The number of values added to this list
	 */
	public int addAll(FloatList list, int insert)
	{
		return addAll(list.theValue, 0, list.theSize, insert);
	}

	/**
	 * <p>
	 * Replaces a value in this list with another value.
	 * </p>
	 *
	 * <p>
	 * If this list is sorted, the value at the given index will be removed and the new value will
	 * be inserted into the index where it belongs.
	 * </p>
	 *
	 * <p>
	 * If this list is unique, the value at the given index will be removed and the new value will
	 * replace it ONLY if the value does not exist elsewhere in the list.
	 * </p>
	 *
	 * @param index The index of the value to replace
	 * @param value The value to replace the old value with
	 * @return The old value at the given index
	 */
	public float set(int index, float value)
	{
		assertUnsealed();
		if(index < 0 || index >= theSize)
			throw new ArrayIndexOutOfBoundsException(index);
		float ret = theValue[index];
		if(isUnique)
		{
			theValue[index] = value + 1;
			if(indexOf(value) >= 0)
			{
				remove(index);
				return ret;
			}
		}
		if(isSorted)
		{
			int newIndex = indexFor(value);
			for(int i = index; i < newIndex; i++)
				theValue[i] = theValue[i + 1]; // If newIndex>index
			for(int i = index; i > newIndex; i--)
				theValue[i] = theValue[i - 1]; // If newIndex<index
			theValue[newIndex] = value;
		}
		else
			theValue[index] = value;
		return ret;
	}

	/**
	 * Removes a value from this list
	 *
	 * @param index The index of the value to remove
	 * @return The value that was removed
	 */
	public float remove(int index)
	{
		assertUnsealed();
		if(index < 0 || index >= theSize)
			throw new ArrayIndexOutOfBoundsException(index);
		float ret = theValue[index];
		for(int i = index; i < theSize - 1; i++)
			theValue[i] = theValue[i + 1];
		theSize--;
		return ret;
	}

	/**
	 * Removes a range of indices
	 *
	 * @param start The starting index of the range to remove (inclusive)
	 * @param end The ending index of the range to remove (exclusive)
	 */
	public void remove(int start, int end)
	{
		assertUnsealed();
		if(start < 0)
			throw new ArrayIndexOutOfBoundsException(start);
		if(end > theSize)
			throw new ArrayIndexOutOfBoundsException(end);
		int length = end - start;
		for(int i = start; i < end && i + length < theSize; i++)
			theValue[i] = theValue[i + length];
		theSize -= length;
	}

	/**
	 * Removes a set of indices
	 *
	 * @param indices The indices to remove
	 * @return The number of elements removed
	 */
	public int remove(java.util.BitSet indices)
	{
		assertUnsealed();
		int length = 0;
		int i = indices.nextSetBit(0);
		int next;
		while(i >= 0 && i < theSize)
		{
			length++;
			next = indices.nextSetBit(i + 1);
			if(next < 0)
				next = theSize;
			for(int j = i; j < next && j + length < theSize; j++)
				theValue[j] = theValue[j + length];
			i = next;
		}
		theSize -= length;
		return length;
	}

	/**
	 * Removes a value from this list
	 *
	 * @param value The value to remove
	 * @return Whether the value was found and removed
	 */
	public boolean removeValue(float value)
	{
		assertUnsealed();
		if(isSorted)
		{
			int idx = indexFor(value);
			if(idx < 0 || idx >= theSize || !equal(theValue[idx], value))
				return false;
			remove(idx);
			return true;
		}
		else
		{
			for(int i = 0; i < theSize; i++)
				if(equal(theValue[i], value))
				{
					remove(i);
					return true;
				}
		}
		return false;
	}

	/**
	 * Compares two floats for equality. Interprets all Float.NaN values as equal and interprets 0.0
	 * and -0.0 as different.
	 *
	 * @param f1 The first float to compare
	 * @param f2 The second float to compare
	 * @return Whether the two float values are equivalent
	 */
	public static boolean equal(float f1, float f2)
	{
		if(Float.isNaN(f1))
			return Float.isNaN(f2);
		if(Float.isNaN(f2))
			return false;
		if(f1 == 0 && f2 == 0) // 0.0 and -0.0 are different
			return Float.compare(f1, f2) == 0;
		return f1 == f2;
	}

	/**
	 * Removes all instances of the given value from this list
	 *
	 * @param value The value to remove
	 * @return The number of times the value was removed
	 */
	public int removeAll(float value)
	{
		if(isUnique)
			return removeValue(value) ? 1 : 0;
		assertUnsealed();
		if(isSorted)
		{
			int idx = indexFor(value);
			if(idx < 0 || idx >= theSize || !equal(theValue[idx], value))
				return 0;
			int begin, end;
			for(begin = idx; begin > 0 && equal(theValue[begin - 1], value); begin--) {
			}
			for(end = idx + 1; end < theSize && equal(theValue[end], value); end++) {
			}
			remove(begin, end);
			return end - begin;
		}
		else
		{
			int ret = 0;
			for(int i = 0; i < theSize; i++)
				if(equal(theValue[i], value))
				{
					remove(i);
					i--;
					ret++;
				}
			return ret;
		}
	}

	/**
	 * Removes all values in this list that are present in the given list
	 *
	 * @param list The list whose values to remove from this list
	 * @return The number of elements removed from this list as a result of this call
	 */
	public int removeAll(FloatList list)
	{
		assertUnsealed();
		java.util.BitSet indices = new java.util.BitSet();
		if(isSorted && list.isSorted)
		{
			int i = 0, j = 0;
			while(i < theSize && j < list.theSize)
			{
				int comp = compare(theValue[i], list.theValue[j]);
				if(comp == 0)
				{
					indices.set(i);
					i++;
				}
				else if(comp < 0)
					i++;
				else
					j++;
			}
		}
		else
		{
			for(int i = 0; i < theSize; i++)
				if(list.contains(theValue[i]))
					indices.set(i);
		}
		remove(indices);
		return indices.cardinality();
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
		if(isSorted)
			throw new IllegalStateException("Cannot perform a move operation on a sorted list");
		if(idx1 < 0 || idx1 >= theSize)
			throw new ArrayIndexOutOfBoundsException(idx1);
		if(idx2 < 0 || idx2 >= theSize)
			throw new ArrayIndexOutOfBoundsException(idx2);
		float temp = theValue[idx1];
		theValue[idx1] = theValue[idx2];
		theValue[idx2] = temp;
	}

	@Override
	public java.util.ListIterator<Float> iterator()
	{
		return new FloatListIterator(toArray());
	}

	/**
	 * Adds all elements of an array that are not present in this list.
	 *
	 * <p>
	 * If this list is sorted, the given list will be sorted and each value will be inserted at the
	 * index where it belongs (assuming it is not already present in the list)
	 * </p>
	 *
	 * @param list The list to add new values from
	 * @return The number of values added to this list
	 */
	public int or(float... list)
	{
		assertUnsealed();
		if(isUnique)
			return addAll(list, 0, list.length, -1);
		else
		{
			isUnique = true;
			try
			{
				return addAll(list, 0, list.length, -1);
			} finally
			{
				isUnique = false;
			}
		}
	}

	/**
	 * Adds all elements of a new list that are not present in this list.
	 *
	 * <p>
	 * If this list is sorted, the given list will be sorted and each value will be inserted at the
	 * index where it belongs (assuming it is not already present in the list)
	 * </p>
	 *
	 * @param list The list to add new values from
	 * @return The number of values added to this list
	 */
	public int or(FloatList list)
	{
		assertUnsealed();
		if(isUnique)
			return addAll(list.theValue, 0, list.theSize, -1);
		else
		{
			isUnique = true;
			try
			{
				return addAll(list.theValue, 0, list.theSize, -1);
			} finally
			{
				isUnique = false;
			}
		}
	}

	/**
	 * Removes all elements of this list that are not present in the given list
	 *
	 * @param list The list to keep elements from
	 * @return The number of elements removed from this lists
	 */
	public int and(FloatList list)
	{
		assertUnsealed();
		int ret = 0;
		for(int i = theSize - 1; i >= 0; i--)
		{
			int j;
			for(j = list.theSize - 1; j >= 0; j--)
				if(equal(theValue[i], list.theValue[j]))
					break;
			if(j < 0)
			{
				remove(i);
				ret++;
			}
		}
		return ret;
	}

	/**
	 * Determines if this list contains a given value
	 *
	 * @param value The value to find
	 * @return Whether this list contains the given value
	 */
	public boolean contains(float value)
	{
		if(isSorted)
		{
			int idx = indexFor(value);
			return idx >= 0 && idx < theSize && equal(theValue[idx], value);
		}
		return indexOf(value) >= 0;
	}

	/**
	 * Counts the number of times a value is represented in this list
	 *
	 * @param value The value to count
	 * @return The number of times the value appears in this list
	 */
	public int instanceCount(float value)
	{
		if(isSorted)
		{
			final int idx = indexFor(value);
			if(idx < 0 || idx >= theSize || equal(theValue[idx], value))
				return 0;
			if(isUnique)
				return 1;
			int ret = 1;
			for(int idx2 = ret - 1; idx2 >= 0 && equal(theValue[idx2], value); idx2--)
				ret++;
			for(int idx2 = ret + 1; idx2 < theSize && equal(theValue[idx2], value); idx2++)
				ret++;
			return ret;
		}
		int ret = 0;
		for(int i = 0; i < theSize; i++)
			if(equal(theValue[i], value))
				ret++;
		return ret;
	}

	/**
	 * Finds a value in this list
	 *
	 * @param value The value to find
	 * @return The first index whose value is the given value
	 */
	public int indexOf(float value)
	{
		if(isSorted)
		{
			int ret = indexFor(value);
			if(ret < 0 || ret >= theSize || equal(theValue[ret], value))
				return -1;
			if(!isUnique)
				while(ret > 0 && equal(theValue[ret - 1], value))
					ret--;
			return ret;
		}
		for(int i = 0; i < theSize; i++)
			if(equal(theValue[i], value))
				return i;
		return -1;
	}

	/**
	 * Finds a value in this list
	 *
	 * @param value The value to find
	 * @return The last index whose value is the given value
	 */
	public int lastIndexOf(float value)
	{
		if(isSorted)
		{
			int ret = indexFor(value);
			if(ret < 0 || ret >= theSize || equal(theValue[ret], value))
				return -1;
			if(!isUnique)
				while(ret < theSize - 1 && equal(theValue[ret + 1], value))
					ret++;
			return ret;
		}
		for(int i = theSize - 1; i >= 0; i--)
			if(equal(theValue[i], value))
				return i;
		return -1;
	}

	/** @return The list of values currently in this list */
	public float [] toArray()
	{
		float [] ret = new float [theSize];
		System.arraycopy(theValue, 0, ret, 0, theSize);
		return ret;
	}

	/** @return The list of values currently in this list, cast to longs */
	public double [] toDoubleArray()
	{
		double [] ret = new double [theSize];
		for(int i = 0; i < ret.length; i++)
			ret[i] = theValue[i];
		return ret;
	}

	/**
	 * Similar to {@link #toArray()} but creates an array of {@link Float} wrappers
	 *
	 * @return The list of values currently in this list
	 */
	public Float [] toObjectArray()
	{
		Float [] ret = new Float [theSize];
		for(int i = 0; i < ret.length; i++)
			ret[i] = Float.valueOf(theValue[i]);
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
	public void arrayCopy(int srcPos, float [] dest, int destPos, int length)
	{
		System.arraycopy(theValue, srcPos, dest, destPos, length);
	}

	/**
	 * Copies a subset of this list's data into an array
	 *
	 * @param srcPos The index in this list to start copying from
	 * @param dest The array to copy the data into
	 * @param destPos The index in the destination array to start copying to
	 * @param length The number of items to copy
	 */
	public void arrayCopy(int srcPos, double [] dest, int destPos, int length)
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
	public FloatList clone()
	{
		FloatList ret;
		try
		{
			ret = (FloatList) super.clone();
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
		float [] oldData = theValue;
		theValue = new float [theSize];
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
			float oldData[] = theValue;
			int newCapacity = (oldCapacity * 3) / 2 + 1;
			if(newCapacity < minCapacity)
				newCapacity = minCapacity;
			theValue = new float [newCapacity];
			System.arraycopy(oldData, 0, theValue, 0, theSize);
		}
	}

	private class FloatListIterator implements java.util.ListIterator<Float>
	{
		private float [] theContent;

		private int theIndex;

		private boolean lastRemoved;

		FloatListIterator(float [] content)
		{
			theContent = content;
		}

		@Override
		public boolean hasNext()
		{
			return theIndex < theContent.length;
		}

		@Override
		public Float next()
		{
			Float ret = Float.valueOf(theContent[theIndex]);
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
		public Float previous()
		{
			Float ret = Float.valueOf(theContent[theIndex - 1]);
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
			FloatList.this.remove(theIndex);
			float [] newContent = new float [theContent.length - 1];
			System.arraycopy(theContent, 0, newContent, 0, theIndex);
			System.arraycopy(theContent, theIndex + 1, newContent, theIndex, newContent.length
				- theIndex);
			theContent = newContent;
			lastRemoved = true;
		}

		@Override
		public void set(Float e)
		{
			if(lastRemoved)
				throw new IllegalStateException("set() cannot be called after remove()");
			if(get(theIndex) != theContent[theIndex])
				throw new java.util.ConcurrentModificationException(
					"List has been modified apart from this iterator");
			theContent[theIndex] = e.floatValue();
			FloatList.this.set(theIndex, e.floatValue());
		}

		@Override
		public void add(Float e)
		{
			if(get(theIndex) != theContent[theIndex])
				throw new java.util.ConcurrentModificationException(
					"List has been modified apart from this iterator");
			FloatList.this.add(theIndex, e.intValue());
			float [] newContent = new float [theContent.length + 1];
			System.arraycopy(theContent, 0, newContent, 0, theIndex);
			System.arraycopy(theContent, theIndex, newContent, theIndex + 1, theContent.length
				- theIndex);
			newContent[theIndex] = e.floatValue();
			theIndex++;
			theContent = newContent;
			lastRemoved = false;
		}
	}
}
