package org.qommons.fn;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Extension of {@link Supplier} that provides a map function
 * 
 * @param <T> The return type of the supplier
 */
public interface BetterSupplier<T> extends Supplier<T> {
	/**
	 * @param <X> The return type of the mapping function
	 * @param map The mapping function
	 * @return A supplier that returns the result of the given mapping function on this supplier's value
	 */
	default <X> BetterSupplier<X> map(Function<? super T, ? extends X> map) {
		return new MappedSupplier<>(this, map);
	}

	/**
	 * @param <T> The type of the source supplier
	 * @param <X> The return type of the mapping function
	 */
	static class MappedSupplier<T, X> implements BetterSupplier<X> {
		private final Supplier<T> theSource;
		private final Function<? super T, ? extends X> theMap;

		public MappedSupplier(Supplier<T> source, Function<? super T, ? extends X> map) {
			theSource = source;
			theMap = map;
		}

		@Override
		public X get() {
			T sourceV = theSource.get();
			return theMap.apply(sourceV);
		}

		@Override
		public int hashCode() {
			return Objects.hash(theSource, theMap);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else if (!(obj instanceof MappedSupplier))
				return false;
			MappedSupplier<?, ?> other = (MappedSupplier<?, ?>) obj;
			return theSource.equals(other.theSource) && theMap.equals(other.theMap);
		}

		@Override
		public String toString() {
			return theSource + "." + theMap;
		}
	}
}
