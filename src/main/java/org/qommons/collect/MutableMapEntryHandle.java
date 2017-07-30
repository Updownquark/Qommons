package org.qommons.collect;

import java.util.Map;

public interface MutableMapEntryHandle<K, V> extends MapEntryHandle<K, V>, MutableCollectionElement<V>, Map.Entry<K, V> {
	@Override
	default V getValue() {
		return get();
	}

	@Override
	default V setValue(V value) {
		V old = get();
		set(value);
		return old;
	}

	@Override
	default MutableMapEntryHandle<K, V> reverse() {
		return new ReversedMutableMapEntryHandle<>(this);
	}

	@Override
	default MapEntryHandle<K, V> immutable() {
		return new ImmutableMapEntryHandle<>(this);
	}

	class ReversedMutableMapEntryHandle<K, V> extends ReversedMutableElement<V> implements MutableMapEntryHandle<K, V> {
		public ReversedMutableMapEntryHandle(MutableMapEntryHandle<K, V> wrapped) {
			super(wrapped);
		}

		@Override
		protected MutableMapEntryHandle<K, V> getWrapped() {
			return (MutableMapEntryHandle<K, V>) super.getWrapped();
		}

		@Override
		public MutableMapEntryHandle<K, V> reverse() {
			return getWrapped();
		}

		@Override
		public K getKey() {
			return getWrapped().getKey();
		}

		@Override
		public String toString() {
			return getKey() + "=" + get();
		}
	}

	class ImmutableMapEntryHandle<K, V> implements MapEntryHandle<K, V> {
		private final MutableMapEntryHandle<K, V> theWrapped;

		public ImmutableMapEntryHandle(MutableMapEntryHandle<K, V> wrapped) {
			theWrapped = wrapped;
		}

		@Override
		public ElementId getElementId() {
			return theWrapped.getElementId();
		}

		@Override
		public boolean isPresent() {
			return theWrapped.isPresent();
		}

		@Override
		public K getKey() {
			return theWrapped.getKey();
		}

		@Override
		public V get() {
			return theWrapped.get();
		}

		@Override
		public int hashCode() {
			return theWrapped.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof ImmutableMapEntryHandle)
				obj = ((ImmutableMapEntryHandle<?, ?>) obj).theWrapped;
			return theWrapped.equals(obj);
		}

		@Override
		public String toString() {
			return getKey() + "=" + get();
		}
	}
}
