package org.qommons.config;

import java.util.Collections;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.qommons.Transaction;
import org.qommons.collect.BetterHashMap;
import org.qommons.collect.BetterMap;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.MapEntryHandle;

/** Values for a hierarchical session */
public interface SessionValues {
	/**
	 * An immutable values instance that contains no properties and accepts none (throwing a {@link UnsupportedOperationException} when the
	 * attempt is made)
	 */
	public static final SessionValues EMPTY = new SessionValues() {
		@Override
		public Object get(String sessionKey, boolean localOnly) {
			return null;
		}

		@Override
		public <T> T get(String sessionKey, Class<? super T> type) {
			return null;
		}

		@Override
		public SessionValues put(String sessionKey, Object value) {
			throw new UnsupportedOperationException("This session values cannot be added to");
		}

		@Override
		public SessionValues putLocal(String sessionKey, Object value) {
			throw new UnsupportedOperationException("This session values cannot be added to");
		}

		@Override
		public SessionValues putGlobal(String sessionKey, Object value) {
			throw new UnsupportedOperationException("This session values cannot be added to");
		}

		@Override
		public <T> T computeIfAbsent(String sessionKey, Supplier<T> creator) {
			throw new UnsupportedOperationException("This session values cannot be added to");
		}

		@Override
		public Set<String> keySet() {
			return Collections.emptySet();
		}

		@Override
		public ValueSource getSource(String sessionKey) {
			return null;
		}

		@Override
		public String toString() {
			return "*empty*";
		}
	};

	/**
	 * @param sessionKey The key to get data for
	 * @return Data stored for the given key in this session
	 */
	default Object get(String sessionKey) {
		return get(sessionKey, false);
	}

	/**
	 * @param sessionKey The key to get data for
	 * @param localOnly Whether, if no data is present for the given key, to search for inherited values in its parent sessions
	 * @return Data stored for the given key in this session
	 */
	Object get(String sessionKey, boolean localOnly);

	/**
	 * @param <T> The expected type of the value
	 * @param sessionKey The key to get data for
	 * @param type The expected type of the value. The super wildcard is to accommodate generics.
	 * @return Data stored for the given key in this session
	 */
	<T> T get(String sessionKey, Class<? super T> type);

	/**
	 * Puts a value into this session that will be visible to all descendants of this session (created after this call)
	 * 
	 * @param sessionKey The key to store data for
	 * @param value The data to store for the given key in this session
	 * @return This session
	 */
	SessionValues put(String sessionKey, Object value);

	/**
	 * Puts a value into this session that will be visible only to sessions "parallel" to this session--sessions for the same element
	 * 
	 * @param sessionKey The key to store data for
	 * @param value The data to store for the given key in this session
	 * @return This session
	 */
	SessionValues putLocal(String sessionKey, Object value);

	/**
	 * Puts a value into this session that will be visible to all sessions in the hierarchy except where it is overridden
	 * 
	 * @param sessionKey The key to store data for
	 * @param value The data to store for the given key in this session
	 * @return This session
	 */
	SessionValues putGlobal(String sessionKey, Object value);

	/**
	 * @param <T> The type of the data
	 * @param sessionKey The key to store data for
	 * @param creator Creates data to store for the given key in this session (if absent)
	 * @return The previous or new value
	 */
	<T> T computeIfAbsent(String sessionKey, Supplier<T> creator);

	/** @return All keys that this {@link SessionValues} has a value for */
	Set<String> keySet();

	/**
	 * @param sessionKey The key to find the source of
	 * @return The source of the given value in this session, or null if this session does not contain a value for the key
	 */
	ValueSource getSource(String sessionKey);

	/** @return Whether this session values has any properties defined on it (as opposed to only inheriting them) */
	default boolean hasOwnValues() {
		for (String key : keySet()) {
			ValueSource source = getSource(key);
			if (source != ValueSource.Inherited)
				return true;
		}
		return false;
	}

	/**
	 * @param sessionKey The key to store data for
	 * @param value The data to store for the given key in this session
	 * @return A transaction that, when closed, will restore the value for the session key to its value before this call
	 */
	default Transaction putTemp(String sessionKey, Object value) {
		Object preValue = get(sessionKey);
		put(sessionKey, value);
		return () -> put(sessionKey, preValue);
	}

	/**
	 * @param <T> The type of the value to modify
	 * @param sessionKey The key of the value to modify
	 * @param defaultInitValue The value to modify if none is currently present for the key
	 * @param modify The action to modify the value
	 * @param revert The action to revert the value modification on the value
	 * @return A transaction which, when {@link Transaction#close() closed}, will revert the modification
	 */
	default <T> Transaction modifyTemp(String sessionKey, Supplier<? extends T> defaultInitValue, Consumer<? super T> modify,
		Consumer<? super T> revert) {
		T preValue = (T) get(sessionKey);
		T newValue;
		if (preValue != null) {
			newValue = preValue;
			modify.accept(newValue);
		} else {
			newValue = defaultInitValue.get();
			modify.accept(newValue);
			put(sessionKey, newValue);
		}
		return () -> {
			revert.accept(newValue);
			if (preValue == null)
				put(sessionKey, preValue);
		};
	}

	/** An enum describing where a value session is from */
	enum ValueSource {
		/**
		 * The value was set on the session itself (or another session for the same element) and is also available to child sessions via
		 * inheritance
		 */
		Own,
		/** The value is inherited from a parent session */
		Inherited,
		/**
		 * The value was set on the session itself (or another session for the same element) and is NOT available to child sessions via
		 * inheritance
		 */
		Local
	}

	/** @return A new root {@link SessionValues} instance */
	public static Default newRoot() {
		return new Default();
	}

	/** Default {@link SessionValues} implementation with the {@link #createChild()} method to support hierarchy */
	public class Default implements SessionValues {
		interface ValueContainer {
			Object getValue();
	
			ValueSource getSource();
		}
	
		static class Root implements ValueContainer {
			Object value;
			final boolean local;
	
			Root(Object value, boolean local) {
				this.value = value;
				this.local = local;
			}
	
			Root set(Object v) {
				value = v;
				return this;
			}
	
			@Override
			public Object getValue() {
				return value;
			}
	
			@Override
			public ValueSource getSource() {
				return local ? ValueSource.Local : ValueSource.Own;
			}
	
			@Override
			public String toString() {
				return getSource() + ": " + getValue();
			}
		}
	
		static class Inherited implements ValueContainer {
			private final CollectionElement<ValueContainer> container;
	
			Inherited(CollectionElement<ValueContainer> container) {
				this.container = container;
			}
	
			@Override
			public Object getValue() {
				return container.get().getValue();
			}
	
			@Override
			public ValueSource getSource() {
				return ValueSource.Inherited;
			}
	
			@Override
			public String toString() {
				return getSource() + ": " + getValue();
			}
		}
	
		static Object open(ValueContainer container) {
			return container == null ? null : container.getValue();
		}
	
		private final Default theParent;
		private final BetterMap<String, ValueContainer> theValues;
	
		Default() { // For root
			theParent = null;
			theValues = BetterHashMap.build().build();
		}
	
		private Default(Default parent) {
			theParent = parent;
			theValues = BetterHashMap.build().build();
			for (MapEntryHandle<String, ValueContainer> entry = theParent.theValues.getTerminalEntry(true); //
				entry != null; //
				entry = theParent.theValues.getAdjacentEntry(entry.getElementId(), true)) {
				if (entry.getValue().getSource() != ValueSource.Local)
					theValues.put(entry.getKey(), new Inherited(entry));
			}
		}
	
		/** @return A new {@link SessionValues} instance that inherits all of this instance's non-local properties */
		public Default createChild() {
			return new Default(this);
		}
	
		@Override
		public Object get(String sessionKey, boolean localOnly) {
			return open(localOnly ? theValues.get(sessionKey) : find(sessionKey, true));
		}
	
		@Override
		public <T> T get(String sessionKey, Class<? super T> type) {
			Object value = get(sessionKey);
			return (T) type.cast(value);
		}
	
		private ValueContainer find(String sessionKey, boolean local) {
			return theValues.compute(sessionKey, (k, old) -> {
				if (old == null)
					return theParent == null ? null : theParent.find(k, false);
				else if (local || !(old instanceof Root) || !((Root) old).local)
					return old;
				else
					return null;
			});
		}
	
		@Override
		public Default put(String sessionKey, Object value) {
			theValues.compute(sessionKey, (k, old) -> {
				if (!(old instanceof Root) || ((Root) old).local)
					return new Root(value, false);
				else
					return ((Root) old).set(value);
			});
			return this;
		}
	
		@Override
		public Default putLocal(String sessionKey, Object value) {
			theValues.compute(sessionKey, (k, old) -> {
				if (old == null)
					return new Root(value, true);
				else if (old instanceof Root && ((Root) old).local)
					return ((Root) old).set(value);
				else
					throw new IllegalStateException("Cannot convert an inheritable value into a local one: " + sessionKey);
			});
			return this;
		}
	
		@Override
		public SessionValues putGlobal(String sessionKey, Object value) {
			_putGlobal(sessionKey, value);
			return this;
		}
	
		private MapEntryHandle<String, ValueContainer> _putGlobal(String sessionKey, Object value) {
			if (theParent == null)
				return theValues.putEntry(sessionKey, new Root(value, false), false);
			MapEntryHandle<String, ValueContainer> entry = theParent._putGlobal(sessionKey, value);
			return theValues.putEntry(sessionKey, new Inherited(entry), false);
		}
	
		@Override
		public <T> T computeIfAbsent(String sessionKey, Supplier<T> creator) {
			return (T) open(theValues.compute(sessionKey, (k, old) -> old == null ? new Root(creator.get(), false) : old));
		}

		@Override
		public Set<String> keySet() {
			return Collections.unmodifiableSet(theValues.keySet());
		}

		@Override
		public ValueSource getSource(String sessionKey) {
			ValueContainer container = theValues.get(sessionKey);
			return container == null ? null : container.getSource();
		}
	}
}