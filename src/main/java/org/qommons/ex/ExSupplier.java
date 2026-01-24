package org.qommons.ex;

import java.util.function.Supplier;

import org.qommons.fn.FunctionUtils;

/**
 * A {@link Supplier} look-alike that can throw a checked exception
 * 
 * @param <R> The type to supply
 * @param <X> The exception type
 */
public interface ExSupplier<R, X extends Throwable> {
	/**
	 * @return The supplied value
	 * @throws X An exception
	 */
	R get() throws X;

	/** @return A {@link Supplier} that calls this supplier, wrapping any checked exceptions with {@link CheckedExceptionWrapper} */
	default Supplier<R> unsafe() {
		return new Unsafe<>(this);
	}

	/**
	 * @param <T> The type to supply
	 * @param <E> The exception type
	 * @param s The supplier to wrap
	 * @return An {@link ExSupplier} that calls the given supplier and never throws any checked exceptions
	 */
	static <T, E extends Throwable> ExSupplier<T, E> of(Supplier<T> s) {
		if (s == null)
			return null;
		return FunctionUtils.printableExSupplier(s::get, s::toString, s);
	}

	/**
	 * Implements {@link ExSupplier#unsafe()}
	 * 
	 * @param <R> The type to supply
	 * @param <X> The exception type
	 */
	class Unsafe<R, X extends Throwable> implements Supplier<R> {
		private ExSupplier<R, X> theSupplier;

		public Unsafe(ExSupplier<R, X> supplier) {
			theSupplier = supplier;
		}

		@Override
		public R get() {
			try {
				return theSupplier.get();
			} catch (RuntimeException | Error e) {
				throw e;
			} catch (Throwable e) {
				throw new CheckedExceptionWrapper(e);
			}
		}

		@Override
		public int hashCode() {
			return theSupplier.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else
				return obj instanceof Unsafe && theSupplier.equals(((Unsafe<?, ?>) obj).theSupplier);
		}

		@Override
		public String toString() {
			return theSupplier.toString();
		}
	}
}
