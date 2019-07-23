/* ArrayUtils.java Created Aug 29, 2005 by Andrew Butler, PSL */
package org.qommons;

import java.lang.reflect.Array;
import java.util.List;

/**
 * ArrayUtils provides some static methods for manipulating arrays easily when using a tool such as {@link java.util.ArrayList} is
 * inconvenient.
 */
public final class ArrayUtils {
	private ArrayUtils() {
	}

	/**
	 * Gets the first element in the given array for which the {@link EqualsChecker#equals(Object, Object)} returns true, or null if this
	 * never occurs. The equals method is called with the element as the first argument and the test parameter as the second.
	 *
	 * @param <T> The type of array to get an element of
	 * @param array The array to get the element of
	 * @param test The object to compare the others with. This may be null.
	 * @param checker The checker to determine which element to get
	 * @return The first element in the array for which the {@link EqualsChecker#equals(Object, Object)} returns true, or null if this never
	 *         occurs
	 */
	public static <T> T get(T [] array, Object test, EqualsChecker checker) {
		for(T el : array)
			if(checker.equals(el, test))
				return el;
		return null;
	}

	/**
	 * Inserts an element into the array--for primitive types
	 *
	 * @param anArray The array to insert into
	 * @param anElement The element to insert
	 * @param anIndex The index for the new element
	 * @return The new array with all elements of <code>anArray</code>, but with <code>anElement</code> inserted at index
	 *         <code>anIndex</code>
	 */
	public static Object addP(Object anArray, Object anElement, int anIndex) {
		Object ret;
		int length;
		if(anArray == null) {
			if(anIndex != 0)
				throw new ArrayIndexOutOfBoundsException("Cannot set " + anIndex + " element in a null array");
			ret = Array.newInstance(anElement.getClass(), 1);
			Array.set(ret, 0, anElement);
			return ret;
		} else {
			length = Array.getLength(anArray);
			ret = Array.newInstance(anArray.getClass().getComponentType(), length + 1);
		}
		System.arraycopy(anArray, 0, ret, 0, anIndex);
		put(ret, anElement, anIndex);
		System.arraycopy(anArray, anIndex, ret, anIndex + 1, length - anIndex);
		return ret;
	}

	/**
	 * Inserts an element into the array
	 *
	 * @param <T> The type of the object array
	 * @param anArray The array to insert into
	 * @param anElement The element to insert
	 * @param anIndex The index for the new element
	 * @return The new array with all elements of <code>anArray</code>, but with <code>anElement</code> inserted at index
	 *         <code>anIndex</code>
	 */
	public static <T> T [] add(T [] anArray, T anElement, int anIndex) {
		T [] ret;
		if(anArray == null) {
			if(anIndex != 0)
				throw new ArrayIndexOutOfBoundsException("Cannot set " + anIndex + " element in a null array");
			ret = (T []) Array.newInstance(anElement.getClass(), 1);
			ret[0] = anElement;
			return ret;
		}
		ret = (T []) Array.newInstance(anArray.getClass().getComponentType(), anArray.length + 1);
		System.arraycopy(anArray, 0, ret, 0, anIndex);
		put(ret, anElement, anIndex);
		System.arraycopy(anArray, anIndex, ret, anIndex + 1, anArray.length - anIndex);
		return ret;
	}

	/**
	 * Inserts new elements into an array
	 *
	 * @param <T> The type of the array
	 * @param anArray The array to insert into
	 * @param anIndex The start index for the new elements
	 * @param elements The elements to insert
	 * @return The new array with all elements of <code>anArray</code>, but with <code>elements</code> inserted at index
	 *         <code>anIndex</code>
	 */
	public static <T> T [] add(T [] anArray, int anIndex, T... elements) {
		T [] ret;
		if(anArray == null) {
			if(anIndex != 0)
				throw new ArrayIndexOutOfBoundsException("Cannot set " + anIndex + " element in a null array");
			return elements;
		}
		ret = (T []) Array.newInstance(anArray.getClass().getComponentType(), anArray.length + elements.length);
		System.arraycopy(anArray, 0, ret, 0, anIndex);
		System.arraycopy(elements, 0, ret, anIndex, elements.length);
		System.arraycopy(anArray, anIndex, ret, anIndex + elements.length, anArray.length - anIndex);
		return ret;
	}

	/**
	 * @param anArray The array to extend
	 * @param anElement The element to add into <code>anArray</code>
	 * @return A new array of length <code>anArray.length+1</code> whose contents are those of <code>anArray</code> followed by
	 *         <code>anElement</code>
	 */
	public static Object addP(Object anArray, Object anElement) {
		int len = anArray == null ? 0 : Array.getLength(anArray);
		return addP(anArray, anElement, len);
	}

	/**
	 * @param <T> The type of the array
	 * @param anArray The array to extend
	 * @param anElement The element to add into <code>anArray</code>
	 * @return A new array of length <code>anArray.length+1</code> whose contents are those of <code>anArray</code> followed by
	 *         <code>anElement</code>
	 */
	public static <T> T [] add(T [] anArray, T anElement) {
		int len = anArray == null ? 0 : Array.getLength(anArray);
		return add(anArray, anElement, len);
	}

	/**
	 * @param <T> The type of the array
	 * @param anArray The array to extend
	 * @param elements The elements to add into <code>anArray</code>
	 * @return A new array of length <code>anArray.length+elements.length</code> whose contents are those of <code>anArray</code> followed
	 *         by those of <code>elements</code>
	 */
	public static <T> T [] addAll(T [] anArray, T... elements) {
		int len = anArray == null ? 0 : Array.getLength(anArray);
		return add(anArray, len, elements);
	}

	/**
	 * Inserts all elements of {@code array1} and {@code array2} into a new array, maintaining sort order. Both arrays must be sorted prior
	 * to calling this method or the resulting array's ordering will not be correct.
	 *
	 * @param <T> The element type of the array
	 * @param array1 The first array of elements to add
	 * @param array2 The second array of elements to add
	 * @param compare The comparator to get the ordering from, or null if {@code T} extends {@link Comparable} and the natural ordering is
	 *            desired
	 * @return An array containing all elements of {@code array1} and {@code array2}, ordered by {@code compare}
	 */
	public static <T> T [] addAll(T [] array1, T [] array2, java.util.Comparator<? super T> compare) {
		if(array2.length == 0)
			return array1;
		if(array1.length == 0)
			return array2;
		T [] ret = (T []) Array.newInstance(array1.getClass().getComponentType(), array1.length + array2.length);
		int i1 = 0, i2 = 0;
		int iRet = 0;
		while(i1 < array1.length && i2 < array2.length) {
			int comp = compare != null ? compare.compare(array1[i1], array2[i2]) : ((Comparable<T>) array1[i1]).compareTo(array2[i2]);
			if(comp <= 0)
				ret[iRet++] = array1[i1++];
			else
				ret[iRet++] = array2[i2++];
		}
		while(i1 < array1.length)
			ret[iRet++] = array1[i1++];
		while(i2 < array2.length)
			ret[iRet++] = array2[i2++];
		return ret;
	}

	/**
	 * Moves an element in an array from one index to another
	 *
	 * @param <T> The type of the array
	 * @param anArray The array to move an element within
	 * @param from The index of the element to move
	 * @param to The index to move the element to
	 * @return The original array
	 */
	public static <T> T [] move(T [] anArray, int from, int to) {
		final T element = anArray[from];
		for(; from < to; from++)
			anArray[from] = anArray[from + 1];
		for(; from > to; from--)
			anArray[from] = anArray[from - 1];
		anArray[to] = element;
		return anArray;
	}

	/**
	 * Moves an element in a primitive array from one index to another
	 *
	 * @param anArray The array to move an element within
	 * @param from The index of the element to move
	 * @param to The index to move the element to
	 * @return The original array
	 */
	public static Object moveP(Object anArray, int from, int to) {
		if(anArray instanceof Object [])
			return move((Object []) anArray, from, to);
		final Object element = Array.get(anArray, from);
		for(; from < to; from++)
			Array.set(anArray, from, Array.get(anArray, from + 1));
		for(; from > to; from--)
			Array.set(anArray, from, Array.get(anArray, from - 1));
		Array.set(anArray, to, element);
		return anArray;
	}

	private static void put(Object array, Object element, int index) {
		try {
			if(array instanceof Object []) {
				try {
					((Object []) array)[index] = element;
				} catch(ArrayStoreException e) {
					throw new IllegalArgumentException(e.getMessage() + ": " + (element == null ? "null" : element.getClass().getName())
						+ " into " + array.getClass().getName());
				}
			} else {
				try {
					Array.set(array, index, element);
				} catch(IllegalArgumentException e) {
					throw new IllegalArgumentException(e.getMessage() + ": " + (element == null ? "null" : element.getClass().getName())
						+ " into " + array.getClass().getName(), e);
				}
			}
		} catch(ArrayIndexOutOfBoundsException e) {
			throw new ArrayIndexOutOfBoundsException(index + " into " + Array.getLength(array));
		}
	}

	/**
	 * Merges all elements of a set of arrays into a single array with no duplicates. For primitive types.
	 *
	 * @param type The type of the result
	 * @param arrays The arrays to merge
	 * @return A new array containing all elements of <code>array1</code> and all elements of <code>array2</code> that are not present in
	 *         <code>array1</code>
	 * @throws NullPointerException If either array is null
	 * @throws ArrayStoreException If elements in the arrays are incompatible with <code>type</code>
	 */
	public static Object mergeInclusiveP(Class<?> type, Object... arrays) {
		java.util.LinkedHashSet<Object> set = new java.util.LinkedHashSet<>();
		int i, j;
		for(i = 0; i < arrays.length; i++) {
			int len = Array.getLength(arrays[i]);
			for(j = 0; j < len; j++)
				set.add(Array.get(arrays[i], j));
		}
		Object ret = Array.newInstance(type, set.size());
		i = 0;
		for(Object el : set) {
			put(ret, el, i);
			i++;
		}
		return ret;
	}

	/**
	 * Merges all elements of a set of arrays into a single array with no duplicates.
	 *
	 * @param <T1> The type of the result
	 * @param <T2> The type of the input arrays
	 * @param type The type of the result
	 * @param arrays The arrays to merge
	 * @return A new array containing all elements of <code>array1</code> and all elements of <code>array2</code> that are not present in
	 *         <code>array1</code>
	 * @throws NullPointerException If either array is null
	 */
	public static <T1, T2 extends T1> T1 [] mergeInclusive(Class<T1> type, T2 []... arrays) {
		java.util.LinkedHashSet<T1> set = new java.util.LinkedHashSet<>();
		int i, j;
		for(i = 0; i < arrays.length; i++) {
			for(j = 0; j < arrays[i].length; j++)
				set.add(arrays[i][j]);
		}
		return set.toArray((T1 []) Array.newInstance(type, set.size()));
	}

	/**
	 * Merges elements found in each of a set of arrays into a single array with no duplicates. For primitive types.
	 *
	 * @param type The type of the result
	 * @param arrays The arrays to merge
	 * @return A new array containing all common elements between <code>array1</code> and <code>array2</code>
	 * @throws NullPointerException If either array is null
	 * @throws ArrayStoreException If elements in the arrays are incompatible with <code>type</code>
	 */
	public static Object mergeExclusiveP(Class<?> type, Object... arrays) {
		if(arrays.length == 0)
			return Array.newInstance(type, 0);
		java.util.ArrayList<Object> retSet = new java.util.ArrayList<>();
		int i, j, k;
		int len = Array.getLength(arrays[0]);
		for(j = 0; j < len; j++)
			retSet.add(Array.get(arrays[0], j));
		for(i = 1; i < arrays.length; i++) {
			for(j = 0; j < retSet.size(); j++) {
				len = Array.getLength(arrays[i]);
				boolean hasEl = false;
				for(k = 0; k < len; k++)
					if(equalsUnordered(retSet.get(j), Array.get(arrays[i], k))) {
						hasEl = true;
						break;
					}
				if(!hasEl) {
					retSet.remove(j);
					j--;
				}
			}
		}
		Object ret = Array.newInstance(type, retSet.size());
		for(i = 0; i < retSet.size(); i++)
			Array.set(ret, i, retSet.get(i));
		return ret;
	}

	/**
	 * Merges elements found in both of two arrays into a single array with no duplicates.
	 *
	 * @param <T1> The type of the result
	 * @param <T2> The type of the input arrays
	 * @param type The type of the result
	 * @param arrays The arrays to merge
	 * @return A new array containing all common elements between <code>array1</code> and <code>array2</code>
	 * @throws NullPointerException If either array is null
	 */
	public static <T1, T2 extends T1> T1 [] mergeExclusive(Class<T1> type, T2 []... arrays) {
		if(arrays.length == 0)
			return (T1 []) Array.newInstance(type, 0);
		java.util.ArrayList<Object> retSet = new java.util.ArrayList<>();
		int i, j, k;
		for(j = 0; j < arrays[0].length; j++)
			retSet.add(arrays[0][j]);
		for(i = 1; i < arrays.length; i++) {
			for(j = 0; j < retSet.size(); j++) {
				boolean hasEl = false;
				for(k = 0; k < arrays[i].length; k++)
					if(equalsUnordered(retSet.get(j), arrays[i][k])) {
						hasEl = true;
						break;
					}
				if(!hasEl) {
					retSet.remove(j);
					j--;
				}
			}
		}
		return retSet.toArray((T1 []) Array.newInstance(type, retSet.size()));
	}

	/**
	 * Concatenates a series of arrays of any type
	 *
	 * @param type The type of the arrays
	 * @param arrays The arrays to concatenate
	 * @return An array containing all elements of the arrays
	 */
	public static Object concatP(Class<?> type, Object... arrays) {
		if(arrays.length == 0)
			return Array.newInstance(type, 0);
		if(arrays.length == 1)
			return arrays[0];
		int size = 0;
		int [] sizes = new int[arrays.length];
		for(int a = 0; a < arrays.length; a++) {
			sizes[a] = Array.getLength(arrays[a]);
			size += sizes[a];
		}
		Object ret = Array.newInstance(type, size);
		int aLen = 0;
		for(int a = 0; a < arrays.length; a++) {
			System.arraycopy(arrays[a], 0, ret, aLen, sizes[a]);
			aLen += sizes[a];
		}
		return ret;
	}

	/**
	 * Concatenates a series of arrays of a non-primitive type
	 *
	 * @param <T> The type of the arrays
	 * @param type The type of the arrays
	 * @param arrays The arrays to concatenate
	 * @return An array containing all elements of the arrays
	 */
	public static <T> T [] concat(Class<T> type, T []... arrays) {
		if(arrays.length == 0)
			return (T []) Array.newInstance(type, 0);
		if(arrays.length == 1)
			return arrays[0];
		int size = 0;
		for(int a = 0; a < arrays.length; a++) {
			if(arrays[a] != null)
				size += arrays[a].length;
		}
		T [] ret = (T []) Array.newInstance(type, size);
		int aLen = 0;
		for(int a = 0; a < arrays.length; a++) {
			if(arrays[a] == null)
				continue;
			System.arraycopy(arrays[a], 0, ret, aLen, arrays[a].length);
			aLen += arrays[a].length;
		}
		return ret;
	}

	/**
	 * @param <T> The type of the array to search
	 * @param anArray The array to search
	 * @param anElement The element to search for
	 * @return The first index <code>0&lt;=idx&lt;anArray.length</code> such that {@link #equals(Object, Object)} returns true for both
	 *         <code>anArray[idx]</code> and <code>anElement</code>, or -1 if no such index exists
	 */
	public static <T> int indexOf(T [] anArray, T anElement) {
		if(anArray == null)
			return -1;
		for(int i = 0; i < anArray.length; i++)
			if(equals(anArray[i], anElement))
				return i;
		return -1;
	}

	/**
	 * Searches an array using binary search.
	 * 
	 * This method has an advantage over {@link java.util.Arrays#binarySearch(Object[], Object, java.util.Comparator)} in that the
	 * comparable given to this method need not be an actual <code>T</code>-typed value, but may be any algorithm that can find a value by
	 * knowing whether the value it is looking for is greater or less than a given value.
	 *
	 * It should be noted that the compare method ({@link Comparable#compareTo(Object)} is used for identity comparison, not
	 * {@link Object#equals(Object)}; therefore the compare method may return 0 where equals returns false or vice versa, and this method
	 * may return a different result than {@link #indexOf(Object[], Object)} even for sorted arrays.
	 * 
	 * If the array is not sorted according to the search comparable, this method will give unpredictable results.
	 *
	 * @param <T> The type of array to search
	 * @param array The array to search
	 * @param search A comparable value to use to search the array
	 * @return If there exists in the array an <code>element</code> for which <code>search.compareTo(element)==0</search>, then the index
	 * of that element; otherwise <code>-(index+1)</code>, where <code>index</code> is the index where such an element would be inserted
	 *         into the sorted array
	 */
	public static <T> int binarySearch(T[] array, Comparable<? super T> search) {
		return binarySearch(java.util.Arrays.asList(array), search);
	}

	/**
	 * Searches a list using binary search.
	 * 
	 * This method has an advantage over {@link java.util.Collections#binarySearch(List, Object, java.util.Comparator)} in that the
	 * comparable given to this method need not be an actual <code>T</code>-typed value, but may be any algorithm that can find a value by
	 * knowing whether the value it is looking for is greater or less than a given value.
	 *
	 * It should be noted that the compare method ({@link Comparable#compareTo(Object)} is used for identity comparison, not
	 * {@link Object#equals(Object)}; therefore the compare method may return 0 where equals returns false or vice versa, and this method
	 * may return a different result than {@link List#indexOf(Object)} even for sorted lists.
	 * 
	 * If the list is not sorted according to the search comparable, this method will give unpredictable results.
	 *
	 * @param <T> The type of list to search
	 * @param list The list to search
	 * @param search A comparable value to use to search the list
	 * @return If there exists in the list an <code>element</code> for which <code>search.compareTo(element)==0</search>, then the index
	 * of that element; otherwise <code>-(index+1)</code>, where <code>index</code> is the index where such an element would be inserted
	 *         into the sorted list
	 */
	public static <T> int binarySearch(List<? extends T> list, Comparable<? super T> search) {
		return binarySearch(0, list.size() - 1, index -> search.compareTo(list.get(index)));
	}

	/** A binary index searcher for {@link ArrayUtils#binarySearch(int, int, IntComparable)} */
	@FunctionalInterface
	public interface IntComparable {
		/**
		 * @param index The index to compare
		 * @return
		 *         <ul>
		 *         <li>-1 if the search is searching for an element less than that at the given index</li>
		 *         <li>0 if the given index matches the search perfectly</li>
		 *         <li>1 if the search is searching for an element greater than that at the given index</li>
		 *         </ul>
		 */
		int compareTo(int index);
	}

	/**
	 * An abstract binary search method that can search through anything by index
	 * 
	 * @param min The lowest index in the search domain
	 * @param max The highest index in the search domain
	 * @param search The indexed search function
	 * @return The <code>index</code> in the search domain for which
	 *         <code>search.{@link IntComparable#compareTo(int) compareTo}(index)==0</code> if such an index exists; otherwise
	 *         <code>-(index+1)</code>, where <code>index</code> is the index where such an index would be inserted into the search domain
	 */
	public static int binarySearch(int min, int max, IntComparable search) {
		if (min > max)
			return -1;
		int mid = -1;
		int comp = 0;
		while (min < max) {
			mid = (min + max) / 2;
			comp = search.compareTo(mid);
			if (comp > 0)
				min = mid + 1;
			else if (comp < 0)
				max = mid - 1;
			else
				return mid;
		}
		if (mid != min)
			comp = search.compareTo(min);
		if (comp == 0)
			return min;
		else if (comp > 0)
			min++;
		if (min < 0) // We'll tolerate negative indexes, but the information that an exact match wasn't found can't be conveyed in this case
			return min;
		else
			return -(min + 1);
	}

	/**
	 * @param anArray The array to search
	 * @param anElement The element to search for
	 * @return The first index <code>0&lt;=idx&lt;anArray.length</code> such that {@link #equals(Object, Object)} returns true for both
	 *         <code>anArray[idx]</code> and <code>anElement</code>, or -1 if no such index exists
	 */
	public static int indexOfP(Object anArray, Object anElement) {
		if(anArray == null)
			return -1;
		if(anArray instanceof Object []) {
			Object [] array2 = (Object []) anArray;
			for(int i = 0; i < array2.length; i++) {
				if(equals(array2[i], anElement))
					return i;
			}
			return -1;
		} else {
			int i, len;
			len = Array.getLength(anArray);
			for(i = 0; i < len; i++) {
				if(equals(Array.get(anArray, i), anElement))
					return i;
			}
			return -1;
		}
	}

	/**
	 * Removes the specified object from the array. For primitive types.
	 *
	 * @param anArray The array to remove an element from
	 * @param anIndex The index of the element to remove
	 * @return A new array with all the elements of <code>anArray</code> except the element at <code>anIndex</code>
	 */
	public static Object removeP(Object anArray, int anIndex) {
		Object ret;
		int length;
		if(anArray == null)
			return null;
		else {
			length = Array.getLength(anArray);
			ret = Array.newInstance(anArray.getClass().getComponentType(), length - 1);
		}
		System.arraycopy(anArray, 0, ret, 0, anIndex);
		System.arraycopy(anArray, anIndex + 1, ret, anIndex, length - anIndex - 1);
		return ret;
	}

	/**
	 * Removes the specified object from the array
	 *
	 * @param <T> The type of the array
	 * @param anArray The array to remove an element from
	 * @param anIndex The index of the element to remove
	 * @return A new array with all the elements of <code>anArray</code> except the element at <code>anIndex</code>
	 */
	public static <T> T [] remove(T [] anArray, int anIndex) {
		T [] ret;
		if(anArray == null)
			return null;
		else {
			ret = (T []) Array.newInstance(anArray.getClass().getComponentType(), anArray.length - 1);
		}
		System.arraycopy(anArray, 0, ret, 0, anIndex);
		System.arraycopy(anArray, anIndex + 1, ret, anIndex, anArray.length - anIndex - 1);
		return ret;
	}

	/**
	 * Removes the specified object from the array
	 *
	 * @param anArray The array to remove an element from
	 * @param anElement The element to remove
	 * @return A new array with all the elements of <code>anArray</code> except <code>anElement</code>
	 */
	public static Object removeP(Object anArray, Object anElement) {
		int idx = indexOfP(anArray, anElement);
		if(idx >= 0)
			return removeP(anArray, idx);
		else
			return anArray;
	}

	/**
	 * Removes the specified object from the array
	 *
	 * @param <T> The type of the array
	 * @param anArray The array to remove an element from
	 * @param anElement The element to remove
	 * @return A new array with all the elements of <code>anArray</code> except <code>anElement</code>
	 */
	public static <T> T [] remove(T [] anArray, T anElement) {
		int idx = indexOf(anArray, anElement);
		if(idx >= 0)
			return remove(anArray, idx);
		else
			return anArray;
	}

	/**
	 * Removes all contents of <code>array2</code> from <code>array1</code>. All instances of <code>array2</code> will also be removed from
	 * <code>array1</code>. For primitive types.
	 *
	 * @param array1 The array to remove elements from
	 * @param array2 The array containing the elements to remove; or the element to remove itself
	 * @return <code>array1</code> missing all the contents of <code>array2</code>
	 */
	public static Object removeAllP(Object array1, Object array2) {
		if(array1 == null || array2 == null)
			return array1;
		if(!array1.getClass().isArray())
			return null;
		if(!array2.getClass().isArray())
			array2 = new Object[] {array2};
		else
			array2 = addP(array2, array2);
		java.util.BitSet remove = new java.util.BitSet();
		int len1 = Array.getLength(array1);
		int len2 = Array.getLength(array2);
		int i, j;
		for(i = 0; i < len1; i++) {
			for(j = 0; j < len2; j++) {
				if(equals(Array.get(array1, i), Array.get(array2, j))) {
					remove.set(i);
					break;
				}
			}
		}
		Object ret = Array.newInstance(array1.getClass().getComponentType(), len1 - remove.cardinality());
		// This copying section might be replaced by a more efficient version
		// using System.arraycopy()--this would be much faster than reflection,
		// especially for large arrays needing only a few elements removed
		for(i = 0, j = 0; i < len1; i++) {
			if(!remove.get(i)) {
				put(ret, Array.get(array1, i), j);
				j++;
			}
		}
		return ret;
	}

	/**
	 * Removes all contents of <code>array2</code> from <code>array1</code>. All instances of <code>array2</code> will also be removed from
	 * <code>array1</code>.
	 *
	 * @param <T> The type of the array
	 * @param array1 The array to remove elements from
	 * @param array2 The array containing the elements to remove; or the element to remove itself
	 * @return <code>array1</code> missing all the contents of <code>array2</code>
	 */
	public static <T> T [] removeAll(T [] array1, Object array2) {
		if(array1 == null || array2 == null)
			return array1;
		if(!array1.getClass().isArray())
			return null;
		if(!array2.getClass().isArray())
			array2 = new Object[] {array2};
		java.util.BitSet remove = new java.util.BitSet();
		int len1 = array1.length;
		int len2 = Array.getLength(array2);
		int i, j;
		for(i = 0; i < len1; i++) {
			for(j = 0; j < len2; j++) {
				if(equals(array1[i], Array.get(array2, j))) {
					remove.set(i);
					break;
				}
			}
		}
		T [] ret = (T []) Array.newInstance(array1.getClass().getComponentType(), len1 - remove.cardinality());
		// This copying section might be replaced by a more efficient version
		// using System.arraycopy()--this would be much faster than reflection,
		// especially for large arrays needing only a few elements removed
		for(i = 0, j = 0; i < len1; i++) {
			if(!remove.get(i)) {
				ret[j] = array1[i];
				j++;
			}
		}
		return ret;
	}

	/**
	 * Searches an array (possibly primitive) for an element
	 *
	 * @param anArray The array to search
	 * @param anElement The element to search for
	 * @return True if <code>anArray</code> contains <code>anElement</code>, false otherwise
	 */
	public static boolean containsP(Object anArray, Object anElement) {
		if(anArray == null)
			return false;
		if(anArray instanceof Object []) {
			Object [] oa = (Object []) anArray;
			for(int i = 0; i < oa.length; i++) {
				if(equals(oa[i], anElement))
					return true;
			}
		} else {
			int len = Array.getLength(anArray);
			for(int i = 0; i < len; i++) {
				if(equals(Array.get(anArray, i), anElement))
					return true;
			}
		}
		return false;
	}

	/**
	 * @param <T> The type of the array to search
	 * @param anArray The array to search
	 * @param anElement The element to search for
	 * @return True if <code>anArray</code> contains <code>anElement</code>, false otherwise
	 */
	public static <T> boolean contains(T [] anArray, T anElement) {
		if(anArray == null)
			return false;
		for(T val : anArray)
			if(equals(val, anElement))
				return true;
		return false;
	}

	/**
	 * @param <T> The type of the iterable to search
	 * @param aSet The iterable to search
	 * @param anElement The element to search for
	 * @return True if <code>aSet</code> contains <code>anElement</code>, false otherwise
	 */
	public static <T> boolean contains(Iterable<T> aSet, T anElement) {
		if(aSet == null)
			return false;
		for(T val : aSet)
			if(equals(val, anElement))
				return true;
		return false;
	}

	/**
	 * Like {@link #containsP(Object, Object)} but the equality test is by identity instead of the equals method
	 *
	 * @param anArray The array to search
	 * @param anElement The element to search for
	 * @return True if <code>anArray</code> contains <code>anElement</code> by identity, false otherwise
	 */
	public static boolean containspID(Object anArray, Object anElement) {
		if(anArray == null)
			return false;
		if(anArray instanceof Object []) {
			Object [] oa = (Object []) anArray;
			for(int i = 0; i < oa.length; i++) {
				if(oa[i] == anElement)
					return true;
			}
		} else {
			int len = Array.getLength(anArray);
			for(int i = 0; i < len; i++) {
				if(Array.get(anArray, i) == anElement)
					return true;
			}
		}
		return false;
	}

	/**
	 * Like {@link #contains(Object[], Object)} but the equality test is by identity instead of the equals method
	 *
	 * @param <T> The type of the array to search
	 * @param <E> The type of the element to search for
	 * @param anArray The array to search
	 * @param anElement The element to search for
	 * @return True if <code>anArray</code> contains <code>anElement</code> by identity, false otherwise
	 */
	public static <T, E extends T> boolean containsID(T [] anArray, E anElement) {
		if(anArray == null)
			return false;
		for(int i = 0; i < anArray.length; i++) {
			if(anArray[i] == anElement)
				return true;
		}
		return false;
	}

	/**
	 * A utility version of equals that allows comparison of null objects and arrays. WARNING: Does not account for an array that contains a
	 * reference to itself, directly or indirectly
	 *
	 * @param o1 The first object to compare
	 * @param o2 The second object to compare
	 * @return true if <code>o1</code> and <code>o2</code> are arrays and their elements are equivalent or if either
	 *         <code>o1==null && o2==null</code> or <code>o1.equals(o2)</code>, false otherwise
	 */
	public static boolean equals(Object o1, Object o2) {
		if(o1 == null)
			return o2 == null;
		if(o2 == null)
			return false;
		if(o1 instanceof Object [] && o2 instanceof Object [])
			return equals((Object []) o1, (Object []) o2);
		if(o1.getClass().isArray() && o2.getClass().isArray()) {
			if(!o1.getClass().equals(o2.getClass()))
				return false;
			int len = Array.getLength(o1);
			if(len != Array.getLength(o2))
				return false;
			for(int i = 0; i < len; i++) {
				if(!equals(Array.get(o1, i), Array.get(o2, i)))
					return false;
			}
			return true;
		}
		return o1.equals(o2);
	}

	private static boolean equals(Object [] o1, Object [] o2) {
		if(o1 == null)
			return o2 == null;
		if(o2 == null)
			return false;
		if(!o1.getClass().equals(o2.getClass()))
			return false;
		if(o1.length != o2.length)
			return false;
		for(int i = 0; i < o1.length; i++) {
			if(!equals(o1[i], o2[i]))
				return false;
		}
		return true;
	}

	/**
	 * Like {@link #equals(Object, Object)}, but compares arrays without regard to order
	 *
	 * @param o1 The first object to compare
	 * @param o2 The second object to compare
	 * @return true if <code>o1</code> and <code>o2</code> are arrays and their elements are equivalent or if either
	 *         <code>o1==null && o2==null</code> or <code>o1.equals(o2)</code>, false otherwise
	 */
	public static boolean equalsUnordered(Object o1, Object o2) {
		return equalsUnordered(o1, o2, new EqualsChecker() {
			@Override
			public boolean equals(Object o3, Object o4) {
				if(o3 == null)
					return o4 == null;
				if(o4 == null)
					return false;
				if(!o3.getClass().isArray()) {
					if(o4.getClass().isArray())
						return false;
					return o3.equals(o4);
				} else if(!o4.getClass().isArray())
					return false;
				return equalsUnordered(o3, o4);
			}
		});
	}

	/**
	 * Checks two values for equality
	 */
	public static interface EqualsChecker {
		/**
		 * Checks two values for equality
		 *
		 * @param o1 The first value to check
		 * @param o2 The second value to check
		 * @return Whether the two values are equal
		 */
		boolean equals(Object o1, Object o2);
	}

	/**
	 * Like {@link #equals(Object, Object)}, but compares arrays without regard to order and uses an independent checker to check for
	 * equality of elements
	 *
	 * @param o1 The first object to compare
	 * @param o2 The second object to compare
	 * @param checker The checker to check elements for equality
	 * @return true if <code>o1</code> and <code>o2</code> are arrays and their elements are equivalent or if either
	 *         <code>o1==null && o2==null</code> or <code>o1.equals(o2)</code>, false otherwise
	 */
	public static boolean equalsUnordered(Object o1, Object o2, EqualsChecker checker) {
		if(o1 == null)
			return o2 == null;
		if(o2 == null)
			return false;
		if(o1 instanceof Object [] && o2 instanceof Object [])
			return equalsUnordered((Object []) o1, (Object []) o2, checker);
		if(o1.getClass().isArray() && o2.getClass().isArray()) {
			if(!o1.getClass().equals(o2.getClass()))
				return false;
			int len = Array.getLength(o1);
			if(len != Array.getLength(o2))
				return false;
			if(len == 0)
				return true;
			long [] bs = new long[(len - 1) / 64 + 1];
			long mask;
			int i, j, bsIdx;
			o1Loop: for(i = 0; i < len; i++) {
				mask = 0;
				for(j = 0; j < len; j++) {
					bsIdx = j / 64;
					if(mask == 0)
						mask = 0x8000000000000000L;
					if((bs[bsIdx] & mask) == 0 && checker.equals(Array.get(o1, i), Array.get(o2, j))) {
						bs[bsIdx] |= mask;
						continue o1Loop;
					}
					mask >>>= 1;
				}
				return false;
			}
			return true;
		}
		return checker.equals(o1, o2);
	}

	private static boolean equalsUnordered(Object [] o1, Object [] o2, EqualsChecker checker) {
		if(o1 == null)
			return o2 == null;
		if(o2 == null)
			return false;
		if(o1.length != o2.length)
			return false;
		if(o1.length == 0)
			return true;
		long [] bs = new long[(o2.length - 1) / 64 + 1];
		long mask;
		int i, j, bsIdx;
		o1Loop: for(i = 0; i < o1.length; i++) {
			mask = 0;
			for(j = 0; j < o2.length; j++) {
				bsIdx = j / 64;
				if(mask == 0)
					mask = 0x8000000000000000L;
				if((bs[bsIdx] & mask) == 0 && checker.equals(o1[i], o2[j])) {
					bs[bsIdx] |= mask;
					continue o1Loop;
				}
				mask >>>= 1;
			}
			return false;
		}
		return true;
	}

	/**
	 * A compliment to the {@link #equals(Object, Object)} method, this method returns a hashcode for <code>array</code> such that it is
	 * consistent with the equals contract for the {@link Object#equals(Object)} method, represented by the {@link #equals(Object, Object)}
	 * method or the {@link #equalsUnordered(Object, Object)} methods.
	 *
	 * @param array The array to get a hashcode for
	 * @return A hashcode based on <code>array</code> and its contents, if any
	 */
	public static int hashCode(Object array) {
		if(array == null)
			return 0;
		if(!array.getClass().isArray())
			return array.hashCode();
		int len = Array.getLength(array);
		int ret = len * 17;
		for(int i = 0; i < len; i++)
			ret += hashCode(Array.get(array, i));
		return ret;
	}

	/**
	 * A utility method for printing out the contents of an array (not deeply)
	 *
	 * @param array The array to print
	 * @return A String containing the representation of the contents of <code>array</code>
	 */
	public static String toString(Object array) {
		if(array == null)
			return "" + null;
		else if(array instanceof Object []) {
			Object [] oa = (Object []) array;
			StringBuffer ret = new StringBuffer("[");
			for(int i = 0; i < oa.length; i++) {
				ret.append(toString(oa[i]));
				if(i < oa.length - 1)
					ret.append(", ");
			}
			ret.append("]");
			return ret.toString();
		} else if(array.getClass().isArray()) {
			StringBuffer ret = new StringBuffer("[");
			int len = Array.getLength(array);
			for(int i = 0; i < len; i++) {
				ret.append(Array.get(array, i));
				if(i < len - 1)
					ret.append(", ");
			}
			ret.append("]");
			return ret.toString();
		} else
			return array.toString();
	}

	/**
	 * Replaces <code>toReplace</code> with <code>replacement</code> in <code>array</code> one time at the most
	 *
	 * @param array The array to search and replace in
	 * @param toReplace The object to replace
	 * @param replacement The object to replace <code>toReplace</code> with the first time it is found
	 * @return The index where <code>toReplace</code> was found and replaced, or -1 if it was not found in <code>array</code>
	 */
	public static int replaceOnce(Object array, Object toReplace, Object replacement) {
		if(array == null)
			return -1;
		if(array instanceof Object []) {
			Object [] array2 = (Object []) array;
			for(int i = 0; i < array2.length; i++) {
				if(equals(array2[i], toReplace)) {
					array2[i] = replacement;
					return i;
				}
			}
			return -1;
		} else {
			int i, len;
			len = Array.getLength(array);
			for(i = 0; i < len; i++) {
				if(equals(Array.get(array, i), toReplace)) {
					put(array, replacement, i);
					return i;
				}
			}
			return -1;
		}
	}

	/**
	 * Replaces <code>toReplace</code> with <code>replacement</code> in <code>array</code> as many times as it occurs
	 *
	 * @param array The array to search and replace in
	 * @param toReplace The object to replace
	 * @param replacement The object to replace <code>toReplace</code> with each time it is found
	 * @return The number of times <code>toReplace</code> was found and replaced
	 */
	public static int replaceAll(Object array, Object toReplace, Object replacement) {
		int count = 0;
		if(array == null)
			return count;
		if(array instanceof Object []) {
			Object [] array2 = (Object []) array;
			for(int i = 0; i < array2.length; i++) {
				if(equals(array2[i], toReplace)) {
					array2[i] = replacement;
					count++;
				}
			}
		} else {
			int i, len;
			len = Array.getLength(array);
			for(i = 0; i < len; i++) {
				if(equals(Array.get(array, i), toReplace)) {
					put(array, replacement, i);
					count++;
				}
			}
		}
		return count;
	}

	/**
	 * Gets the elements that the two arrays have in common. Elements are retrieved from the first array.
	 *
	 * @param <T> The type of the first array and the return array
	 * @param <T2> The type of the second array
	 * @param array1 The first array
	 * @param array2 The second array
	 * @return The elements in <code>array1</code> that occur at least once in <code>array2</code>
	 */
	public static <T, T2 extends T> T [] commonElements(T [] array1, T2 [] array2) {
		int count = 0;
		if(array1 == null && array2 == null)
			return null;
		else if(array2 == null)
			return (T []) Array.newInstance(array1.getClass().getComponentType(), 0);
		else if(array1 == null)
			return (T []) Array.newInstance(array2.getClass().getComponentType(), 0);
		int i, j;
		for(i = 0; i < array1.length; i++)
			for(j = 0; j < array2.length; j++)
				if(equals(array1[i], array2[j])) {
					count++;
					break;
				}
		T [] ret = (T []) Array.newInstance(array1.getClass().getComponentType(), count);
		count = 0;
		for(i = 0; i < array1.length; i++)
			for(j = 0; j < array2.length; j++)
				if(equals(array1[i], array2[j])) {
					ret[count] = array1[i];
					count++;
					break;
				}
		return ret;
	}

	/**
	 * Gets the elements that the two arrays have in common. Elements are retrieved from the first array.
	 *
	 * @param array1 The first array
	 * @param array2 The second array
	 * @return The elements in <code>array1</code> that occur at least once in <code>array2</code>
	 */
	public static Object [] commonElementsP(Object array1, Object array2) {
		int count = 0;
		if(array1 == null || array2 == null)
			return new Object[0];
		if(!array1.getClass().isArray() || array2.getClass().isArray())
			return new Object[0];
		int len1 = Array.getLength(array1);
		int len2 = Array.getLength(array2);
		int i, j;
		for(i = 0; i < len1; i++)
			for(j = 0; j < len2; j++)
				if(equals(Array.get(array1, i), Array.get(array2, j))) {
					count++;
					break;
				}
		Object [] ret = new Object[count];
		count = 0;
		for(i = 0; i < len1; i++)
			for(j = 0; j < len2; j++)
				if(equals(Array.get(array1, i), Array.get(array2, j))) {
					ret[count] = Array.get(array1, i);
					count++;
					break;
				}
		return ret;
	}

	/**
	 * Gets the elements that are in array1, but not array 2.
	 *
	 * @param <T> The type of the arrays
	 * @param array1 The first array
	 * @param array2 The second array
	 * @return The elements in <code>array1</code> that do not occur in <code>array2</code>
	 */
	public static <T> T [] removedElements(T [] array1, T [] array2) {
		int count = 0;
		if(array1 == null || array2 == null)
			return array1;
		int len1 = array1.length;
		int len2 = array2.length;
		int i, j;
		for(i = 0; i < len1; i++) {
			count++;
			for(j = 0; j < len2; j++) {
				if(equals(array1[i], array2[j])) {
					count--;
					break;
				}
			}
		}
		T [] ret = (T []) Array.newInstance(array1.getClass().getComponentType(), count);
		count = 0;
		for(i = 0; i < len1; i++) {
			count++;
			for(j = 0; j < len2; j++) {
				if(equals(array1[i], array2[j])) {
					count--;
					break;
				}
				ret[count] = array1[i];
			}
		}
		return ret;
	}

	/**
	 * Gets the elements that are in array1, but not array 2.
	 *
	 * @param array1 The first array
	 * @param array2 The second array
	 * @return The elements in <code>array1</code> that do not occur in <code>array2</code>
	 */
	public static Object [] removedElementsP(Object array1, Object array2) {
		int count = 0;
		if(array1 == null || array2 == null)
			return new Object[0];
		if(!array1.getClass().isArray() || array2.getClass().isArray())
			return new Object[0];
		int len1 = Array.getLength(array1);
		int len2 = Array.getLength(array2);
		int i, j;
		for(i = 0; i < len1; i++) {
			count++;
			for(j = 0; j < len2; j++) {
				if(equals(Array.get(array1, i), Array.get(array2, j))) {
					count--;
					break;
				}
			}
		}
		Object [] ret = new Object[count];
		count = 0;
		for(i = 0; i < len1; i++) {
			count++;
			for(j = 0; j < len2; j++) {
				if(equals(Array.get(array1, i), Array.get(array2, j))) {
					count--;
					break;
				}
				ret[count] = Array.get(array1, i);
			}
		}
		return ret;
	}

	/**
	 * Gets the elements that are in array2, but not array 1.
	 *
	 * @param array1 The first array
	 * @param array2 The second array
	 * @return The elements in <code>array2</code> that do not occur in <code>array1</code>
	 */
	public static Object [] addedElements(Object array1, Object array2) {
		int count = 0;
		if(array1 == null || array2 == null)
			return new Object[0];
		if(!array1.getClass().isArray() || array2.getClass().isArray())
			return new Object[0];
		int len1 = Array.getLength(array1);
		int len2 = Array.getLength(array2);
		int i, j;
		for(i = 0; i < len1; i++) {
			count++;
			for(j = 0; j < len2; j++) {
				if(equals(Array.get(array2, i), Array.get(array1, j))) {
					count--;
					break;
				}
			}
		}
		Object [] ret = new Object[count];
		count = 0;
		for(i = 0; i < len1; i++) {
			count++;
			for(j = 0; j < len2; j++) {
				if(equals(Array.get(array2, i), Array.get(array1, j))) {
					count--;
					break;
				}
				ret[count] = Array.get(array2, i);
			}
		}
		return ret;
	}

	/**
	 * Reverses the order of an array
	 *
	 * @param <T> The type of the array to reverse
	 * @param array The array to reverse
	 * @return The reversed array, same as the original reference
	 */
	public static <T> T [] reverse(T [] array) {
		if(array == null)
			return array;
		T temp;
		final int aLen = array.length - 1;
		for(int i = 0; i < array.length / 2; i++) {
			temp = array[i];
			array[i] = array[aLen - i];
			array[aLen - i] = temp;
		}
		return array;
	}

	/**
	 * Reverses the order of an array. Similar to {@link #reverse(Object[])} but works for primitive arrays as well.
	 *
	 * @param array The array to reverse
	 * @return The reversed array, same as the original reference
	 */
	public static Object reverseP(Object array) {
		if(array == null)
			return array;
		if(array instanceof Object [])
			return reverse((Object []) array);
		Object temp;
		final int aLen = Array.getLength(array);
		for(int i = 0; i < aLen / 2; i++) {
			temp = Array.get(array, i);
			put(array, Array.get(array, aLen - i - 1), i);
			put(array, temp, aLen - i - 1);
		}
		return array;
	}

	/**
	 * @see DifferenceListenerE
	 *
	 * @param <T1> The type of the original array
	 * @param <T2> The type of the modifying array
	 */
	public static interface DifferenceListener<T1, T2> extends DifferenceListenerE<T1, T2, RuntimeException> {
		/**
		 * @see org.qommons.ArrayUtils.DifferenceListenerE#identity(java.lang.Object, java.lang.Object)
		 */
		@Override
		boolean identity(T1 o1, T2 o2);

		/**
		 * @see org.qommons.ArrayUtils.DifferenceListenerE#added(java.lang.Object, int, int)
		 */
		@Override
		T1 added(T2 o, int mIdx, int retIdx);

		/**
		 * @see org.qommons.ArrayUtils.DifferenceListenerE#removed(java.lang.Object, int, int, int)
		 */
		@Override
		T1 removed(T1 o, int oIdx, int incMod, int retIdx);

		/**
		 * @see org.qommons.ArrayUtils.DifferenceListenerE#set(java.lang.Object, int, int, java.lang.Object, int, int)
		 */
		@Override
		T1 set(T1 o1, int idx1, int incMod, T2 o2, int idx2, int retIdx);
	}

	/**
	 * <p>
	 * This listener contains all information needed to reconcile two arrays using the
	 * {@link ArrayUtils#adjust(Object[], Object[], org.qommons.ArrayUtils.DifferenceListenerE)} method.
	 * </p>
	 *
	 * <p>
	 * There are many different indices that may or may not be relevant to a particular listener:
	 * <ul>
	 * <li><b>oIdx:</b> The index of the element in the original array</li>
	 * <li><b>incMod:</b> The index of the element in a copy of the original array that has been modified incrementally for each add,
	 * remove, and reorder operation</li>
	 * <li><b>mIdx:</b> The index of the element in the modifier array</li>
	 * <li><b>retIdx:</b> The index that the returned element (if not null) will be at in the result array</li>
	 * </ul>
	 * oIdx and mIdx are self-explanatory. incMod and retIdx are useful for listeners that modify an external ordered set incrementally with
	 * each listener method invocation.
	 * </p>
	 *
	 * <p>
	 * <b>An Example</b><br />
	 * Suppose there is a list, <b>L</b>, whose data can only be modified incrementally by the methods add(value, index), remove(index), and
	 * move(fromIndex, toIndex), but can be accessed in batch by the getData method, which returns an array of all data in the list. Suppose
	 * the list needs to be modified to represent a new arbitrary set of data, <b>d</b>. <b>d</b> may or may not contain elements
	 * represented in <b>L</b>. These elements, if present, may be in different order and new items not present in <b>L</b> may be present
	 * in <b>d</b>. To modify <b>L</b> to represent the data in <b>d</b> in the correct order, the
	 * {@link ArrayUtils#adjust(Object[], Object[], DifferenceListenerE)} method should be called with <b>L</b>.getData(), <b>d</b>, and a
	 * listener whose methods perform the following operations:
	 * <ul>
	 * <li>{@link #identity(Object, Object)}: Tests the two elements to see if the item from <b>L</b> represents the item in <b>d</b>,
	 * possibly by simply calling {@link Object#equals(Object)}</li>
	 * <li>{@link #added(Object, int, int)}: Invokes <b>L</b>.add(o2, retIdx).</li>
	 * <li>{@link #removed(Object, int, int, int)}: Invokes <b>L</b>.remove(incMod). Using incMod accounts for any previous modifications
	 * that may have been performed on the list from the listener.</li>
	 * <li>{@link #set(Object, int, int, Object, int, int)}: Invokes <b>L</b>.move(incMod, retIdx). This again accounts for all previous
	 * changes to the list and moves the element to the index intended by the adjust method.</li>
	 * </ul>
	 * The end result of this invocation will be that <b>L</b> contains all the data in <b>d</b>, only the data in <b>d</b>, and is ordered
	 * identically to <b>d</b>. The returned array may be ignored.
	 * </p>
	 *
	 * <p>
	 * <b>Alternate Operation:</b><br />
	 * <ul>
	 * <li>If it is intended that the modification operation should only add data to <b>L</b>, the remove method may do nothing and return
	 * o. The return value will cause the adjust method to update future indexes, assuming that the value is kept and not removed.</li>
	 * <li>If order is not important or no move(fromIndex, toIndex) method exists, the set method may do nothing and return o1. This will
	 * have no effect on the result of the operation other than leaving the items originally in <b>L</b> (those that were also present in
	 * <b>d</b>) in their original order.</li>
	 * <li>Other permutations may exist depending on the business logic used by the listener.</li>
	 * </ul>
	 * </p>
	 *
	 * <p>
	 * The listener is entirely free to choose whether items are added or removed from the original array and its representations by
	 * modifying whether the return value from those methods is null. The adjust method does not care what is returned from the
	 * add/remove/set methods except whether the value is null. The listener is free to perform index moving operations or not. This method
	 * will never cause an index out of bounds error assuming the related data sets are not modified by anything other than the listener and
	 * the data in the arrays passed into the adjust method do not change.
	 * </p>
	 *
	 * @param <T1> The type of the original array
	 * @param <T2> The type of the modifying array
	 * @param <E> The type of exception that may be thrown
	 */
	public static interface DifferenceListenerE<T1, T2, E extends Throwable> {
		/**
		 * Tests the identity of an item from each of the two sets. This method should return true if one item is a representation of the
		 * other or both are a representation of the same piece of data.
		 *
		 * @param o1 The object from the first set
		 * @param o2 The object from the second set
		 * @return Whether the two objects are fundamentally equivalent
		 * @throws E If an error occurs in this method
		 */
		boolean identity(T1 o1, T2 o2) throws E;

		/**
		 * Called when a value is found in the second set that is not present in the first set
		 *
		 * @param o The unmatched object
		 * @param mIdx The index in the second array where the unmatched object was found
		 * @param retIdx The index that the new element will be inserted into the final array
		 * @return The new T1-type element to insert into the return array, or null if the new element is not to be inserted
		 * @throws E If an error occurs in this method
		 */
		T1 added(T2 o, int mIdx, int retIdx) throws E;

		/**
		 * Called when a value is found in the first set that is not present in the second set
		 *
		 * @param o The unmatched object
		 * @param oIdx The index in the first array where the unmatched object was found
		 * @param incMod The index where the unmatched element would occur the incrementally modified original array
		 * @param retIdx The index in the final array where the replacement will be located if a non-null value is returned
		 * @return null if the original object is to be removed, otherwise its replacement
		 * @throws E If an error occurs in this method
		 */
		T1 removed(T1 o, int oIdx, int incMod, int retIdx) throws E;

		/**
		 * Called when elements in the two arrays match
		 *
		 * @param o1 The element in the first array
		 * @param idx1 The index of the element in the first array
		 * @param incMod The index where the element in the original array would occur the incrementally modified original array
		 * @param o2 The element in the second array
		 * @param idx2 The index of the element in the second array
		 * @param retIdx The index of the returned element in the final array
		 * @return The element to be inserted in the final array, or null if the element is to be removed
		 * @throws E If an error occurs in this method
		 */
		T1 set(T1 o1, int idx1, int incMod, T2 o2, int idx2, int retIdx) throws E;
	}

	/**
	 * <p>
	 * Reconciles differences between two ordered sets of objects. Allows a programmer highly customized control between two different
	 * representations of a data set. The arrays are compared and the listener is notified when any differences are encountered, allowing it
	 * to determine the composition of the returned array and/or perform well-defined operations represented by the differences or
	 * similarities.
	 * </p>
	 * <p>
	 * For more information, see
	 * <a href="https://github.com/Updownquark/Qommons/wiki/ArrayUtils">https://github.com/Updownquark/Qommons/wiki/ArrayUtils</a>
	 * </p>
	 *
	 * @param <T1> The type of the original array
	 * @param <T2> The type of the modifying array
	 * @param <E> The type of exception that may be thrown
	 * @param original The original array
	 * @param modifier The modifying array
	 * @param dl The listener to determine how to deal with differences between the two arrays
	 * @return A final array that is the result of applying select changes between the original and the modifying arrays
	 * @throws E If the {@link DifferenceListenerE} throws an exception
	 */
	public static <T1, T2, E extends Throwable> T1 [] adjust(T1 [] original, T2 [] modifier, DifferenceListenerE<T1, T2, E> dl) throws E {
		ArrayAdjuster<T1, T2, E> adjuster = new ArrayAdjuster<>(original, modifier, dl);
		return adjuster.adjust();
	}

	/**
	 * @param <T1> The type of the original array
	 * @param <T2> The type of the modifying array
	 * @param <E> The type of exception that may be thrown
	 * @param original The original array
	 * @param modifier The modifying array
	 * @param dl The listener to determine how to deal with differences between the two arrays
	 * @throws E If the {@link DifferenceListenerE} throws an exception
	 * @see #adjust(Object[], Object[], DifferenceListenerE)
	 */
	public static <T1, T2, E extends Throwable> void adjustNoCreate(T1[] original, T2[] modifier, DifferenceListenerE<T1, T2, E> dl)
		throws E {
		ArrayAdjuster<T1, T2, E> adjuster = new ArrayAdjuster<>(original, modifier, dl);
		adjuster.noCreate().adjust();
	}

	static final Object NULL = new Object();

	/**
	 * Adjusts arrays. This is the more complicated and capable structure used by
	 * {@link ArrayUtils#adjust(Object[], Object[], DifferenceListenerE)}. In addition to all the functionality available to
	 * {@link ArrayUtils#adjust(Object[], Object[], DifferenceListenerE) adjust}, use of this class directly allows null elements to be
	 * returned in the result array. See {@link #nullElement()} for more information.
	 *
	 * @param <T1> The type of the original array
	 * @param <T2> The type of the modifying array
	 * @param <E> The type of exception that may be thrown
	 */
	public static class ArrayAdjuster<T1, T2, E extends Throwable> {
		private final T1 [] original;

		private final int [] oIdxAdj;

		private final int [] oMappings;

		private final T2 [] modifier;

		private final int [] mMappings;

		private final boolean [] entriesSet;

		private final DifferenceListenerE<T1, T2, E> dl;

		private int maxLength;

		private boolean isNullElement;
		private boolean createArray;

		/**
		 * Creates an adjuster that can adjust one array by another
		 *
		 * @param o The original array
		 * @param m The modified array
		 * @param listener The listener to determine how to deal with differences between the two arrays
		 * @see ArrayUtils#adjust(Object[], Object[], DifferenceListenerE)
		 */
		public ArrayAdjuster(T1 [] o, T2 [] m, DifferenceListenerE<T1, T2, E> listener) {
			original = o;
			oIdxAdj = new int[o.length];
			oMappings = new int[o.length];
			modifier = m;
			mMappings = new int[m.length];
			entriesSet = new boolean[m.length];
			dl = listener;
			createArray = true;
		}

		public ArrayAdjuster<T1, T2, E> noCreate() {
			createArray = false;
			return this;
		}

		private void init() throws E {
			int o, m, r = original.length + modifier.length;
			for(m = 0; m < modifier.length; m++)
				mMappings[m] = -1;
			int minM = 0;
			for(o = 0; o < original.length; o++) {
				oIdxAdj[o] = o;
				oMappings[o] = -1;
				for (m = minM; m < modifier.length; m++) {
					if(mMappings[m] >= 0)
						continue;
					if(dl.identity(original[o], modifier[m])) {
						oMappings[o] = m;
						mMappings[m] = o;
						r--;
						if (m == minM)
							minM++;
						break;
					}
				}
			}
			maxLength = r;
		}

		/**
		 * Adjusts the arrays set from the constructor
		 *
		 * @return The adjusted array
		 * @throws E If an error occurs in one of the listener's methods
		 * @see ArrayUtils#adjust(Object[], Object[], DifferenceListenerE)
		 */
		public T1 [] adjust() throws E {
			init();
			int m, o, r = 0;
			Object [] ret = new Object[maxLength];
			for(o = 0; o < original.length && oMappings[o] < 0; o++)
				if(remove(o, -1, ret, r))
					r++;
			for(m = 0; m < modifier.length; m++) {
				// Add or set each modifier
				o = mMappings[m];
				if(o >= 0) {
					if(set(o, m, ret, r))
						r++;
					/* Remove the originals that occur before the next match */
					for(o++; o < original.length && oMappings[o] < 0; o++) {
						if(ret[r] != null) {
							Object temp = ret[r];
							for(int r2 = r; r < ret.length - 1 && temp != null; r2++) {
								Object temp2 = ret[r2 + 1];
								ret[r2 + 1] = temp;
								temp = temp2;
							}
						}
						if(remove(o, m + 1, ret, r))
							r++;
					}
				} else {
					if(ret[r] != null) {
						Object temp = ret[r];
						for(int r2 = r; r < ret.length - 1 && temp != null; r2++) {
							Object temp2 = ret[r2 + 1];
							ret[r2 + 1] = temp;
							temp = temp2;
						}
					}
					if(add(m, ret, r))
						r++;
				}
			}

			for(int i = 0; i < r; i++)
				if(ret[i] == NULL)
					ret[i] = null;
			if (createArray) {
				T1[] actualRet = (T1[]) Array.newInstance(original.getClass().getComponentType(), r);
				System.arraycopy(ret, 0, actualRet, 0, r);
				return actualRet;
			} else
				return null;
		}

		/**
		 * Marks the current value as a null value. If an actual null were returned, adjust would interpret this as meaning the element
		 * should be removed from the array, with subsequent indices being affected. If this method is called from
		 * {@link DifferenceListenerE}.add, remove, or set, the element's place will be saved and that element in the returned array will be
		 * null.
		 */
		public void nullElement() {
			isNullElement = true;
		}

		static void mergeSort(int [] order, int [] distances, int start, int end) {
			if(end - start <= 1)
				return;
			int mid = (start + end + 1) / 2;
			mergeSort(order, distances, start, mid);
			mergeSort(order, distances, mid, end);
			while(start < mid && mid < end) {
				if(distances[start] < distances[mid]) // Reverse order
				{
					int temp = distances[mid];
					int temp2 = order[mid];
					for(int i = mid; i > start; i--) {
						distances[i] = distances[i - 1];
						order[i] = order[i - 1];
					}
					distances[start] = temp;
					order[start] = temp2;
					mid++;
				}
				start++;
			}
		}

		private boolean add(int m, Object [] ret, int r) throws E {
			entriesSet[m] = true;
			T1 item = dl.added(modifier[m], m, r);
			if(isNullElement) {
				item = (T1) NULL;
				isNullElement = false;
			}
			// Adjust the incremental modification indexes
			if(item != null) {
				ret[r] = item;
				for(int i = 0; i < oIdxAdj.length; i++)
					if(oIdxAdj[i] >= r)
						oIdxAdj[i]++;
			} else {
				for(; r < ret.length - 1; r++)
					ret[r] = ret[r + 1];
			}
			return item != null;
		}

		private boolean remove(int o, int m, Object [] ret, int r) throws E {
			T1 item = dl.removed(original[o], o, oIdxAdj[o], r);
			if(isNullElement) {
				item = (T1) NULL;
				isNullElement = false;
			}
			// Adjust the incremental modification indexes
			if(item == null) {
				oIdxAdj[o] = -1;
				for(; o < oIdxAdj.length; o++)
					oIdxAdj[o]--;
			} else
				ret[r] = item;
			return item != null;
		}

		private boolean set(int o, int m, Object [] ret, int r) throws E {
			if(entriesSet[m])
				return ret[r] != null;
			if(oIdxAdj[o] > r && oIdxAdj[o] - r <= 10) {
				/* If a few elements have been moved forward several indices, we want to fire a
				 * few move events on those elements rather than many move events for a few
				 * index differences. */
				int o2;
				for(o2 = o - 1; o2 >= 0; o2--)
					if(oMappings[o2] > m)
						set(o2, oMappings[o2], ret, r + oMappings[o2] - m);
			}
			entriesSet[m] = true;
			T1 item = dl.set(original[o], o, oIdxAdj[o], modifier[m], m, r);
			if(isNullElement) {
				item = (T1) NULL;
				isNullElement = false;
			}
			// Adjust the incremental modification indexes
			if(item != null) {
				ret[r] = item;
				int oAdj = oIdxAdj[o];
				if(r > oAdj) { // Element moved forward--decrement incMods up to the new index
					for(int i = 0; i < oIdxAdj.length; i++)
						if(oIdxAdj[i] >= oAdj && oIdxAdj[i] <= r)
							oIdxAdj[i]--;
				} else if(r < oAdj) { // Element moved backward--increment incMods up to the original index
					for(int i = 0; i < oIdxAdj.length; i++)
						if(oIdxAdj[i] >= r && oIdxAdj[i] < oAdj)
							oIdxAdj[i]++;
				}
			} else {
				oIdxAdj[o] = -1;
				for(int i = 0; i < oIdxAdj.length; i++)
					if(oIdxAdj[i] > r)
						oIdxAdj[i]--;
				for(; r < ret.length - 1; r++)
					ret[r] = ret[r + 1];
			}
			return item != null;
		}
	}

	/**
	 * The {@link List} version of {@link #adjust(Object[], Object[], DifferenceListenerE)}. In this version, the <code>original</code> list
	 * passed to the method may be modified.
	 *
	 * @param <T1> The type of the original list
	 * @param <T2> The type of the modifying list
	 * @param <E> The type of exception that may be thrown
	 * @param original The original list
	 * @param modifier The modifying list
	 * @param dl The listener to determine how to deal with differences between the two lists
	 * @throws E If the {@link DifferenceListenerE} throws an exception
	 */
	public static <T1, T2, E extends Throwable> void adjust(List<T1> original, List<T2> modifier, DifferenceListenerE<T1, T2, E> dl)
		throws E {
		ArrayAdjuster<T1, T2, E> adjuster = new ArrayAdjuster<>((T1 []) original.toArray(), (T2 []) modifier.toArray(),
			new DifferenceListenerE<T1, T2, E>() {
				@Override
				public boolean identity(T1 o1, T2 o2) throws E {
					return dl.identity(o1, o2);
				}

				@Override
				public T1 added(T2 o, int mIdx, int retIdx) throws E {
					T1 newValue = dl.added(o, mIdx, retIdx);
					if(newValue != null)
						original.add(retIdx, newValue);
					return newValue;
				}

				@Override
				public T1 removed(T1 o, int oIdx, int incMod, int retIdx) throws E {
					T1 keepValue = dl.removed(o, oIdx, incMod, retIdx);
					if(keepValue == null)
						original.remove(incMod);
					else
						original.set(incMod, keepValue);
					return keepValue;
				}

				@Override
				public T1 set(T1 o1, int idx1, int incMod, T2 o2, int idx2, int retIdx) throws E {
					T1 replaceValue = dl.set(o1, idx1, incMod, o2, idx2, retIdx);
					if(replaceValue == null)
						original.remove(incMod);
					else if (incMod == retIdx)
						original.set(incMod, replaceValue);
					else {
						original.remove(incMod);
						original.add(retIdx, replaceValue);
					}
					return replaceValue;
				}
			});
		adjuster.noCreate().adjust();
	}

	/**
	 * A listener used for {@link #sort(Object [], SortListener)}
	 *
	 * @param <T> The type of object to sort
	 */
	public static interface SortListener<T> extends java.util.Comparator<T> {
		/**
		 * Called just <u>after</u> two elements are swapped.
		 *
		 * @param o1 The first element being moved
		 * @param idx1 The index (in the pre-swap array) of the first element
		 * @param o2 The second element being moved
		 * @param idx2 The index (in the pre-swap array) of the second element
		 */
		void swapped(T o1, int idx1, T o2, int idx2);
	}

	/**
	 * Sorts an array, allowing for complex operations during the sort, such as sorting related arrays simultaneously.
	 *
	 * This is an implementation of selection sort. Selection sort is used because although it may perform many more comparison operations
	 * than other algorithms, there is no other sorting algorithm that performs fewer swaps. It is anticipated that this method will be used
	 * most often in the case that multiple operations must take place during a swap, while comparison should be relatively quick.
	 *
	 * @param <T> The type of the array to sort
	 * @param array The array to sort
	 * @param listener The listener to use for comparisons and to notify of swapping
	 */
	public static <T> void sort(T [] array, SortListener<T> listener) {
		for(int i = 0; i < array.length - 1; i++) {
			int min = findMin(array, i, listener);
			if(min != i) {
				T ta = array[i];
				T tb = array[min];
				array[i] = tb;
				array[min] = ta;
				listener.swapped(ta, i, tb, min);
			}
		}
	}

	private static <T> int findMin(T [] array, int start, SortListener<T> listener) {
		int minIdx = start;
		for(int i = start + 1; i < array.length; i++)
			if(listener.compare(array[i], array[minIdx]) < 0)
				minIdx = i;
		return minIdx;
	}

	/** Represents some statistical information calculated on a set of float data */
	public static class ArrayStatistics {
		/**
		 * The smallest non-NaN, non-infinite datum encountered by this statistics set
		 */
		public float min;

		/**
		 * The largest non-NaN, non-infinite datum encountered by this statistics set
		 */
		public float max;

		/**
		 * The number of NaN data encountered by this statistics set
		 */
		public int naNCount;

		/**
		 * The number of +Inf data encountered by this statistics set
		 */
		public int posInfCount;

		/**
		 * The number of -Inf data encountered by this statistics set
		 */
		public int negInfCount;

		/**
		 * The number of non-NaN, non-infinite data encountered by this statistics set
		 */
		public int validCount;

		/**
		 * The mean value of all non-NaN, non-infinite data encountered by this statistics set
		 */
		public float mean;

		/**
		 * A running data measure that allows the standard deviation of all non-NaN, non-infinite data encountered by this statistics set
		 */
		private float q;

		/**
		 * Creates an ArrayStatistics set
		 */
		public ArrayStatistics() {
			min = Float.MAX_VALUE;
			max = Float.MIN_VALUE;
			mean = 0;
			q = Float.NaN;
			validCount = 0;
			naNCount = 0;
			posInfCount = 0;
			negInfCount = 0;
		}

		/**
		 * Adds a value to this statistics set
		 *
		 * @param value The value to analyze
		 */
		public void inputValue(float value) {
			if(Float.isNaN(value))
				naNCount++;
			else if(Float.isInfinite(value)) {
				if(value > 0)
					posInfCount++;
				else
					negInfCount++;
			} else if(validCount == 0) {
				mean = value;
				q = 0;
				validCount++;
			} else {
				if(value < min)
					min = value;
				if(value > max)
					max = value;
				q = q + (validCount * (mean - value) * (mean - value)) / (validCount + 1);
				validCount++;
				mean = mean + (value - mean) / validCount;
			}
		}

		/**
		 * @return The standard deviation of all non-NaN, non-infinite data encountered by this statistics set
		 */
		public float getSigma() {
			return (float) Math.sqrt(q / (validCount - 1));
		}

		/**
		 * @return The total number of data encountered by this statistics set
		 */
		public int getTotalCount() {
			return validCount + naNCount + posInfCount + negInfCount;
		}

		@Override
		public String toString() {
			StringBuilder ret = new StringBuilder();
			ret.append(getTotalCount());
			ret.append(" values ranged ");
			ret.append(min);
			ret.append(" to ");
			ret.append(max);
			ret.append("; mean=");
			ret.append(mean);
			ret.append(", st.dev.=");
			ret.append(getSigma());
			ret.append("; ");
			ret.append(naNCount);
			ret.append("NaN, ");
			ret.append(posInfCount);
			ret.append("+Inf, ");
			ret.append(negInfCount);
			ret.append("-Inf");
			return ret.toString();
		}
	}

	/**
	 * Performs a simple statistical analysis of an array of floats
	 *
	 * @param array An n-dimensional array of floats
	 * @param stats The ArrayStatistics object to populate, or null to create a new one
	 * @return The statistics object
	 */
	public static ArrayStatistics getStatistics(Object array, ArrayStatistics stats) {
		if(array == null)
			return null;
		if(stats == null)
			stats = new ArrayStatistics();
		if(array instanceof Object []) {
			for(Object subArray : (Object []) array)
				getStatistics(subArray, stats);
			return stats;
		}
		if(!(array instanceof float []))
			throw new IllegalArgumentException("Cannot statistically analyze a " + array.getClass().getName() + " array");
		for(float f : (float []) array)
			stats.inputValue(f);
		return stats;
	}

	/**
	 * A testing method
	 *
	 * @param args The command-line arguments. The first argument is used to determine what test to run.
	 */
	public static void main(String [] args) {
		String test;
		if(args.length == 0)
			test = "intSort";
		else
			test = args[0];
		if(test.equals("intSort")) {
			int [] random = new int[10000];
			int [] random2 = new int[random.length];
			for(int i = 0; i < random.length; i++)
				random[i] = QommonsUtils.getRandomInt();
			ArrayAdjuster.mergeSort(random2, random, 0, random.length);
			boolean sorted = true;
			for(int i = 0; i < random.length - 1; i++)
				if(random[i] < random[i + 1]) {
					sorted = false;
					break;
				}
			System.out.println("Sort " + (sorted ? "succeeded" : "failed"));
		} else if(test.equals("adjust")) {
			final Integer [] start = new Integer[25];
			for(int i = 0; i < start.length; i++)
				start[i] = Integer.valueOf(i);
			Integer [] modifier = new Integer[start.length];
			for(int i = 0; i < modifier.length; i++) {
				if(i % 5 != 0)
					modifier[i] = Integer.valueOf(i);
				else
					modifier[i] = Integer.valueOf(i / 5 * start.length);
			}
			Integer [] result = adjust(start, modifier, new DifferenceListener<Integer, Integer>() {
				@Override
				public boolean identity(Integer o1, Integer o2) {
					return o1.equals(o2);
				}

				@Override
				public Integer added(Integer o, int mIdx, int retIdx) {
					return o;
				}

				@Override
				public Integer removed(Integer o, int oIdx, int oIdxAdj, int retIdx) {
					return o;
				}

				@Override
				public Integer set(Integer o1, int idx1, int oIdxAdj, Integer o2, int idx2, int retIdx) {
					if(o1.intValue() % 5 == 2)
						return null;
					return o1;
				}
			});
			System.out.println("Original array=" + toString(start));
			System.out.println("Modifier array=" + toString(modifier));
			System.out.println("Adjusted array=" + toString(result));
		} else
			throw new IllegalArgumentException("Unrecognized test: " + test);
	}
}
