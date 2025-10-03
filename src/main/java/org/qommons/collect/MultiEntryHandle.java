package org.qommons.collect;

/**
 * Represents an entry in a {@link BetterMultiMap} containing a collection of values with a common key
 * 
 * @param <K> The type of the key
 * @param <V> The type of the value
 */
public interface MultiEntryHandle<K, V> extends MultiMap.MultiEntry<K, V>, CollectionElement<K> {
	@Override
	default K get() {
		return getKey();
	}

	@Override
	BetterCollection<V> getValues();

	@Override
	MultiEntryHandle<K, V> getAdjacent(boolean next);

	@Override
	default MultiEntryHandle<K, V> reverse() {
		return new ReversedMultiEntryHandle<>(this);
	}

	/**
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 * @param entry The entry to reverse
	 * @return The reversed entry (or null if entry was null)
	 */
	public static <K, V> MultiEntryHandle<K, V> reverse(MultiEntryHandle<K, V> entry) {
		return entry == null ? null : entry.reverse();
	}

	/**
	 * Implements {@link MultiEntryHandle#reverse()}
	 * 
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
	class ReversedMultiEntryHandle<K, V> implements MultiEntryHandle<K, V> {
		private final MultiEntryHandle<K, V> theWrapped;

		public ReversedMultiEntryHandle(MultiEntryHandle<K, V> source) {
			theWrapped = source;
		}

		protected MultiEntryHandle<K, V> getWrapped() {
			return theWrapped;
		}

		@Override
		public ElementId getElementId() {
			return theWrapped.getElementId().reverse();
		}

		@Override
		public K getKey() {
			return theWrapped.getKey();
		}

		@Override
		public BetterCollection<V> getValues() {
			return theWrapped.getValues().reverse();
		}

		@Override
		public MultiEntryHandle<K, V> getAdjacent(boolean next) {
			return MultiEntryHandle.reverse(getWrapped().getAdjacent(!next));
		}
	}
}
