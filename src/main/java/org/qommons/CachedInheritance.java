package org.qommons;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.qommons.collect.BetterList;

/**
 * A class that allows fast access to Java class inheritance structure
 * 
 * @param <T> The Java type that this inheritance is for
 */
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
	/** Inheritance for {@link Object} */
	public static final CachedInheritance<Object> OBJECT = createInheritance(Object.class);

	/**
	 * @param <T> The type to get inheritance information for
	 * @param type The java class to get inheritance information for
	 * @return Inheritance information for the given type
	 */
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

	/** The Java type that this inheritance is for */
	public final Class<T> type;
	@SuppressWarnings("unused")
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

	/** @return The Java type that this inheritance is for */
	public Class<T> getType() {
		return type;
	}

	@Override
	public String getName() {
		return type.getName();
	}

	/*
	public int getPrimaryDepth() {
		return primaryDescent.length;
	}
	
	public CachedInheritance<? super T> getPrimaryDescent(int index) {
		if (index == primaryDescent.length)
			return this;
		return primaryDescent[index];
	}*/
	
	/** @return All parent types of this type */
	public BetterList<CachedInheritance<? super T>> getParents() {
		return BetterList.of(parents);
	}
	
	/*public int getParentCount() {
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
	*/

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