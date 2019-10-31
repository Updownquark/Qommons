package org.qommons.collect;

public interface MutableMultiMapHandle<K, V> extends MultiEntryValueHandle<K, V>, MutableMapEntryHandle<K, V> {
	@Override
	default MutableMultiMapHandle<K, V> reverse() {
		return new ReversedMutableMultiMapHandle<>(this);
	}

	class ReversedMutableMultiMapHandle<K, V> extends ReversedMutableMapEntryHandle<K, V> implements MutableMultiMapHandle<K, V> {
		public ReversedMutableMultiMapHandle(MutableMultiMapHandle<K, V> wrapped) {
			super(wrapped);
		}

		@Override
		protected MutableMultiMapHandle<K, V> getWrapped() {
			return (MutableMultiMapHandle<K, V>) super.getWrapped();
		}

		@Override
		public ElementId getKeyId() {
			return getWrapped().getKeyId().reverse();
		}

		@Override
		public MutableMultiMapHandle<K, V> reverse() {
			return getWrapped();
		}
	}
}
