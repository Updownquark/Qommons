package org.qommons;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.qommons.collect.BetterList;

public class CachedInheritance<T> implements Named {
	private static final Map<Class<?>, Class<?>> PRIMITIVE_WRAPPERS = QommonsUtils.<Class<?>, Class<?>> buildMap(null)//
		.with(void.class, Void.class)//
		.with(boolean.class, Boolean.class)//
		.with(char.class, Character.class)//
		.with(byte.class, Byte.class)//
		.with(short.class, Short.class)//
		.with(int.class, Integer.class)//
		.with(long.class, Long.class)//
		.with(float.class, Float.class)//
		.with(double.class, Double.class)//
		.getUnmodifiable();
	private static final Map<Class<?>, CachedInheritance<?>> INHERITANCE_CACHE = new ConcurrentHashMap<>();
	public static final CachedInheritance<Object> OBJECT = createInheritance(Object.class);

	public static <T> CachedInheritance<T> get(Class<T> type) {
		if (type == Object.class)
			return (CachedInheritance<T>) OBJECT;
		else if (type.isPrimitive())
			type = (Class<T>) PRIMITIVE_WRAPPERS.get(type);
		/* We use ConcurrentHashMap for its thread safety, but unfortunately we can't use its very nice ability
		 * to only lock parts of the tree for updates.
		 * This is because if an entry is missing in the map, it's not enough to just add that entry.
		 * We need to install the full hierarchy of the type, which will likely contain new types
		 * as well as modifications to existing types.
		 */
		CachedInheritance<T> inh = (CachedInheritance<T>) INHERITANCE_CACHE.get(type);
		if (inh == null) {
			synchronized (CachedInheritance.class) {
				return createInheritance(type);
			}
		}
		return inh;
	}

	private static <T> CachedInheritance<T> createInheritance(Class<T> type) {
		CachedInheritance<T> inh = (CachedInheritance<T>) INHERITANCE_CACHE.get(type);
		if (inh != null)
			return inh;
		Class<? super T> superT;
		Class<? super T>[] superIs;
		if (type == Object.class) {
			superT = null;
			superIs = new Class[0];
		} else {
			superT = type.getSuperclass();
			superIs = (Class<? super T>[]) type.getInterfaces();
			if (superIs.length > 0) {
				if (superT == Object.class)
					superT = null;
			} else if (superT == null)
				superT = Object.class;
		}
		CachedInheritance<? super T>[] parents = new CachedInheritance[(superT == null ? 0 : 1) + superIs.length];
		int p = 0;
		if (superT != null)
			parents[p++] = createInheritance(superT);
		for (Class<? super T> superI : superIs)
			parents[p++] = createInheritance(superI);
		inh = new CachedInheritance<>(type, parents);
		INHERITANCE_CACHE.put(type, inh);
		return inh;
	}

	public final Class<T> type;
	private final CachedInheritance<? super T>[] parents;
	private final int[] parentIndexes;
	private final CachedInheritance<? super T>[] primaryDescent;
	private volatile CachedInheritance<? extends T>[] extensions;
	private int extensionCount;

	CachedInheritance(Class<T> type, CachedInheritance<? super T>[] parents) {
		this.type = type;
		this.parents = parents;
		parentIndexes = new int[parents.length];
		if (parents.length == 0) {
			primaryDescent = new CachedInheritance[0];
		} else {
			primaryDescent = Arrays.copyOf(parents[0].primaryDescent, parents[0].primaryDescent.length + 1);
			primaryDescent[primaryDescent.length - 1] = parents[0];
			for (int p = 0; p < parents.length; p++)
				parentIndexes[p] = parents[p].addExtension(this);
		}
	}

	private int addExtension(CachedInheritance<? extends T> extension) {
		int index = extensionCount;
		extensionCount++;
		if (index == 0)
			extensions = new CachedInheritance[4];
		else {
			if (index == extensions.length) {
				extensions = Arrays.copyOf(extensions, extensions.length * 2);
			}
		}
		extensions[index] = extension;
		return index;
	}

	public Class<T> getType() {
		return type;
	}

	@Override
	public String getName() {
		return type.getName();
	}

	public int getPrimaryDepth() {
		return primaryDescent.length;
	}

	public CachedInheritance<? super T> getPrimaryDescent(int index) {
		if (index == primaryDescent.length)
			return this;
		return primaryDescent[index];
	}

	public BetterList<CachedInheritance<? super T>> getParents() {
		return BetterList.of(parents);
	}

	public int getParentCount() {
		return parents.length;
	}

	public CachedInheritance<? super T> getParent(int index) {
		return parents[index];
	}

	public int getParentIndex(int index) {
		return parentIndexes[index];
	}

	public boolean isParentOf(CachedInheritance<?> other) {
		for (int i = 0; i < extensionCount; i++) {
			if (extensions[i] == other)
				return true;
		}
		return false;
	}

	public BetterList<CachedInheritance<? extends T>> getExtensions() {
		return BetterList.of(extensions);
	}

	public int getExtensionCount() {
		return extensionCount;
	}

	public CachedInheritance<? extends T> getExtension(int index) {
		return extensions[index];
	}

	// public boolean isAssignableFrom(CachedInheritance<?> other) {
	// if (other == this)
	// return true;
	// else if (parents.length == 0)
	// return true;
	// else if (primaryDescent.length >= other.primaryDescent.length)
	// return false;
	// else if (parents[0] != other.primaryDescent[primaryDescent.length - 1])
	// return false;
	// CachedInheritance<?> directInh;
	// if (other.primaryDescent.length == primaryDescent.length + 1)
	// directInh = other;
	// else
	// directInh = other.primaryDescent[primaryDescent.length + 1];
	// CachedInheritance<? extends T>[] exts = extensions;
	// if (exts == null)
	// return false;
	// else if (exts.length == 1)
	// return exts[0] == directInh;
	// // Parents aren't sorted, so we have to search linearly,
	// // but the comparison may be much faster
	// if (directInh.parents.length <= log2(exts.length) * 2)
	// return ArrayUtils.contains((CachedInheritance<?>[]) directInh.parents, this);
	// else {
	// int index = Arrays.binarySearch(exts, directInh, Named.DISTINCT_NUMBER_TOLERANT);
	// /* It's possible that there may be classes from multiple classloaders stored in a single map.
	// * IClasses from different class loaders with are not related to each other
	// */
	// if (index < 0)
	// return false;
	// for (int i = index; i >= 0 && directInh.getName().equals(exts[i].getName()); i--) {
	// if (exts[i] == directInh)
	// return true;
	// }
	// for (int i = index + 1; i < exts.length && directInh.getName().equals(exts[i].getName()); i++) {
	// if (exts[i] == directInh)
	// return true;
	// }
	// return false;
	// }
	// }
	//
	//
	// public static class IntPathList {
	// private int[][] theValues = new int[1][1];
	// private int theSize;
	//
	// public int size() {
	// return theSize;
	// }
	//
	// public int get(int index) {
	// int sizeMinusOne = theSize - 1;
	// return theValues[sizeMinusOne][sizeMinusOne - index];
	// }
	//
	// public int getLast() {
	// int sizeMinusOne = theSize - 1;
	// return theValues[sizeMinusOne][sizeMinusOne];
	// }
	//
	// private void pop() {
	// theSize--;
	// }
	//
	// private void add(int value) {
	// if (theSize == theValues.length)
	// theValues = ArrayUtils.add(theValues, Arrays.copyOf(theValues[theSize - 1], theSize + 1));
	// theValues[theSize][theSize] = value;
	// theSize++;
	// }
	//
	// private void incrementLast() {
	// int sizeMinusOne = theSize - 1;
	// theValues[sizeMinusOne][sizeMinusOne]++;
	// }
	// }
	//
	// Iterable<IntPathList> getAllDescentPaths(CachedInheritance<?> to) {
	// if (to == this)
	// return Collections.emptySet();
	// else if (primaryDescent.length > to.primaryDescent.length || to.primaryDescent[primaryDescent.length] != this)
	// return null;
	// return () -> new Iterator<IntPathList>() {
	// private final IntPathList path = new IntPathList();
	// private AtomicStack<CachedInheritance<?>> parentPath = AtomicStack.empty();
	// private boolean isOnNext;
	//
	// {
	// for (CachedInheritance<?> p = to; p != CachedInheritance.this; p = p.parents[0]) {
	// parentPath = parentPath.push(p);
	// path.add(p.parentIndexes[0]);
	// }
	// isOnNext = true;
	// }
	//
	// @Override
	// public boolean hasNext() {
	// if (isOnNext)
	// return !parentPath.isEmpty();
	// while (!parentPath.isEmpty()) {
	// boolean foundExtension = false;
	// for (path.incrementLast(); !foundExtension && path.getLast() < parentPath.top().parents.length; path.incrementLast()) {
	// CachedInheritance<?> top = parentPath.top();
	// if (CachedInheritance.this.isAssignableFrom(top.parents[path.getLast()])) {
	// foundExtension = true;
	// while (parentPath.top().parents[0] != CachedInheritance.this) {
	// path.add(parentPath.top().parentIndexes[0]);
	// parentPath = parentPath.push(parentPath.top().parents[0]);
	// }
	// }
	// }
	// if (foundExtension)
	// break;
	// else {
	// path.pop();
	// parentPath = parentPath.pop();
	// }
	// }
	// isOnNext = true;
	// return !parentPath.isEmpty();
	// }
	//
	// @Override
	// public IntPathList next() {
	// if (!hasNext())
	// throw new NoSuchElementException();
	// return path;
	// }
	// };
	// }

	@Override
	public String toString() {
		return type.getName();
	}

	static class ExtensionList<T> extends AbstractList<CachedInheritance<? extends T>> {
		private final CachedInheritance<T> theType;
		private final int theSize;

		ExtensionList(CachedInheritance<T> type) {
			theType = type;
			theSize = theType.extensionCount;
		}

		@Override
		public CachedInheritance<? extends T> get(int index) {
			if (index < 0 || index >= theSize)
				throw new IndexOutOfBoundsException(index + " of " + theSize);
			return theType.extensions[index];
		}

		@Override
		public int size() {
			return theSize;
		}
	}
}