package org.qommons.collect;

import java.util.function.Function;

import org.qommons.LambdaUtils;
import org.qommons.ThreadConstraint;
import org.qommons.Transactable;
import org.qommons.TransactableBuilder;

/**
 * A sub-type of {@link TransactableBuilder} that specifically builds collections (or other multi-element data structures)
 * 
 * @param <B> The sub-type of this builder
 */
public interface CollectionBuilder<B extends CollectionBuilder<? extends B>> extends TransactableBuilder<B> {
	@Override
	default B withLocking(Function<Object, Transactable> locker) {
		return withCollectionLocking(LambdaUtils.printableFn(obj -> new RRWLockingStrategy(locker.apply(obj)), locker::toString, locker));
	}

	/**
	 * @param locker The collection locker to use to thread-secure structures built with this builder
	 * @return This builder
	 */
	default B withLocking(CollectionLockingStrategy locker) {
		return withCollectionLocking(LambdaUtils.constantFn(locker, locker::toString, locker));
	}

	/**
	 * @param locker A function to produce collection lockers to use to thread-secure structures built with this builder
	 * @return This builder
	 */
	B withCollectionLocking(Function<Object, CollectionLockingStrategy> locker);

	/**
	 * An abstract default {@link CollectionBuilder} implementation
	 * 
	 * @param <B> The sub-type of this builder
	 */
	public abstract class Default<B extends Default<? extends B>> implements CollectionBuilder<B> {
		private static final Function<Object, CollectionLockingStrategy> DEFAULT_LOCKER = LambdaUtils
			.printableFn(__ -> new FastFailLockingStrategy(ThreadConstraint.ANY), "fast-fail", "fast-fail-collection-locker");

		private Function<Object, CollectionLockingStrategy> theLocker;
		private String theDescription;

		/** @param defaultDescrip The initial (default) description for objects built with this builder */
		public Default(String defaultDescrip) {
			theLocker = DEFAULT_LOCKER;
			theDescription = defaultDescrip;
		}

		@Override
		public B withCollectionLocking(Function<Object, CollectionLockingStrategy> locker) {
			if (locker == null)
				theLocker = DEFAULT_LOCKER;
			else
				theLocker = locker;
			return (B) this;
		}

		@Override
		public B withThreadConstraint(ThreadConstraint threadConstraint) {
			if (theLocker != DEFAULT_LOCKER)
				System.err.println("WARNING: Using withThreadConstraint() after modifying the locking--locking policy will be reset");
			theLocker = LambdaUtils.constantFn(new FastFailLockingStrategy(threadConstraint), "fast-fail on " + threadConstraint, null);
			return (B) this;
		}

		/** @return The function to produce lockers for objects built with this builder */
		protected Function<Object, CollectionLockingStrategy> getLocker() {
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
