package org.qommons.collect;

public interface MutableOrderedMapEntry<K, V> extends OrderedMapEntry<K, V>, MutableMapEntryHandle<K, V>, MutableListElement<V> {
	@Override
	MutableOrderedMapEntry<K, V> getAdjacent(boolean next);

	@Override
	default MutableOrderedMapEntry<K, V> reverse() {
		return new ReversedMutableOrderedEntry<>(this);
	}

	static <K, V> MutableOrderedMapEntry<K, V> reverse(MutableOrderedMapEntry<K, V> element) {
		return element == null ? null : element.reverse();
	}

	static class ReversedMutableOrderedEntry<K, V> extends ReversedOrderedMapEntry<K, V> implements MutableOrderedMapEntry<K, V> {
		public ReversedMutableOrderedEntry(MutableOrderedMapEntry<K, V> wrapped) {
			super(wrapped);
		}

		@Override
		protected MutableOrderedMapEntry<K, V> getWrapped() {
			return (MutableOrderedMapEntry<K, V>) super.getWrapped();
		}

		@Override
		public MutableOrderedMapEntry<K, V> getAdjacent(boolean next) {
			return MutableOrderedMapEntry.reverse(getWrapped().getAdjacent(!next));
		}

		@Override
		public int getElementsBefore() {
			return getWrapped().getElementsAfter();
		}

		@Override
		public int getElementsAfter() {
			return getWrapped().getElementsBefore();
		}

		@Override
		public String isEnabled() {
			return getWrapped().isEnabled();
		}

		@Override
		public String isAcceptable(V value) {
			return getWrapped().isAcceptable(value);
		}

		@Override
		public void set(V value) throws UnsupportedOperationException, IllegalArgumentException {
			getWrapped().set(value);
		}

		@Override
		public String canRemove() {
			return getWrapped().canRemove();
		}

		@Override
		public void remove() throws UnsupportedOperationException {
			getWrapped().remove();
		}

		@Override
		public MutableOrderedMapEntry<K, V> reverse() {
			return getWrapped();
		}
	}
}
