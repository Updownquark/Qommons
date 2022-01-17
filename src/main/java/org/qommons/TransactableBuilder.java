package org.qommons;

import java.util.function.Function;

/**
 * Builds some kind of object with identity and locking
 * 
 * @param <B> The sub-type of the builder
 */
public interface TransactableBuilder<B extends TransactableBuilder<? extends B>> {
	/**
	 * @param locker The transactable to use to thread-secure objects built with this builder
	 * @return This builder
	 */
	default B withLocking(Transactable locker) {
		return withLocking(LambdaUtils.constantFn(locker, locker::toString, locker));
	}

	/**
	 * @param locker A function to produce transactables to use to thread-secure objects built with this builder
	 * @return This builder
	 */
	B withLocking(Function<Object, Transactable> locker);

	/**
	 * @param descrip The description for the object(s) built with this builder
	 * @return This builder
	 */
	B withDescription(String descrip);

	/** @return The description for objects built with this builder */
	String getDescription();

	/**
	 * An abstract default {@link TransactableBuilder} implementation
	 * 
	 * @param <B> The sub-type of this builder
	 */
	public abstract class Default<B extends Default<? extends B>> implements TransactableBuilder<B> {
		private static final Function<Object, Transactable> DEFAULT_LOCKER = LambdaUtils.constantFn(Transactable.NONE, "UNSAFE",
			Transactable.NONE);

		private Function<Object, Transactable> theLocker;
		private String theDescription;

		/** @param defaultDescrip The initial (default) description for objects built with this builder */
		public Default(String defaultDescrip) {
			theLocker = DEFAULT_LOCKER;
			theDescription = defaultDescrip;
		}

		@Override
		public B withLocking(Function<Object, Transactable> locker) {
			if (locker == null)
				theLocker = DEFAULT_LOCKER;
			else
				theLocker = locker;
			return (B) this;
		}

		/** @return The function to produce lockers for objects built with this builder */
		protected Function<Object, Transactable> getLocker() {
			return theLocker;
		}

		@Override
		public B withDescription(String descrip) {
			if (descrip == null)
				throw new NullPointerException("null description not allowed");
			theDescription = descrip;
			return (B) this;
		}

		@Override
		public String getDescription() {
			return theDescription;
		}
	}
}
