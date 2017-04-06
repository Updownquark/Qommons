package org.qommons.collect;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

import org.qommons.Transaction;
import org.qommons.value.Settable;
import org.qommons.value.Value;

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;

/**
 * A {@link Map} backed by {@link Qollection}s
 * 
 * @param <K> The type of key for the map
 * @param <V> The type of value for the map
 */
public interface QMap<K, V> extends TransactableMap<K, V> {
	/**
	 * A {@link java.util.Map.Entry} with observable capabilities. The {@link #equals(Object) equals} and {@link #hashCode() hashCode}
	 * methods of this class must use only the entry's key.
	 *
	 * @param <K> The type of key this entry uses
	 * @param <V> The type of value this entry stores
	 */
	public interface QMapEntry<K, V> extends Entry<K, V>, Value<V> {
		@Override
		default V get() {
			return getValue();
		}

		/**
		 * @param <K> The type key for the entry
		 * @param <V> The type of value for the entry
		 * @param type The value type for the entry
		 * @param key The key for the entry
		 * @param value The value for the entry
		 * @return A constant-value observable entry
		 */
		public static <K, V> QMapEntry<K, V> constEntry(TypeToken<V> type, K key, V value) {
			class ObservableKeyEntry implements QMapEntry<K, V> {
				@Override
				public TypeToken<V> getType() {
					return type;
				}

				@Override
				public K getKey() {
					return key;
				}

				@Override
				public V getValue() {
					return value;
				}

				@Override
				public V setValue(V value2) {
					return value;
				}

				@Override
				public int hashCode() {
					return Objects.hashCode(key);
				}

				@Override
				public boolean equals(Object obj) {
					return obj instanceof Map.Entry && Objects.equals(((Map.Entry<?, ?>) obj).getKey(), key);
				}
			}
			return new ObservableKeyEntry();
		}

		/**
		 * @param <K> The type key for the entry
		 * @param <V> The type of value for the entry
		 * @param entry The entry to flatten
		 * @return An Observable entry whose value is the value of the observable value in the given entry
		 */
		static <K, V> QMapEntry<K, V> flatten(QMapEntry<K, ? extends Value<V>> entry) {
			class FlattenedQEntry extends Settable.SettableFlattenedValue<V> implements QMapEntry<K, V> {
				FlattenedQEntry(Value<? extends Value<? extends V>> value, Supplier<? extends V> defaultValue) {
					super(value, defaultValue);
				}

				@Override
				public K getKey() {
					return entry.getKey();
				}

				@Override
				public V getValue() {
					return get();
				}

				@Override
				public V setValue(V value) {
					return set(value, null);
				}

				@Override
				public int hashCode() {
					return Objects.hashCode(getKey());
				}

				@Override
				public boolean equals(Object obj) {
					return obj instanceof Map.Entry && Objects.equals(((Map.Entry<?, ?>) obj).getKey(), getKey());
				}
			}
			return new FlattenedQEntry(entry, () -> null);
		}
	}

	/** @return The type of keys this map uses */
	TypeToken<K> getKeyType();

	/** @return The type of values this map stores */
	TypeToken<V> getValueType();

	@Override
	QSet<K> keySet();

	/**
	 * <p>
	 * A default implementation of {@link #keySet()}.
	 * </p>
	 * <p>
	 * No {@link QMap} implementation may use the default implementations for its {@link #keySet()}, {@link #get(Object)}, and
	 * {@link #valueEntries()} methods. {@link #defaultValueEntries(QMap)} may not be used in the same implementation as
	 * {@link #defaultKeySet(QMap)} or {@link #defaultValueOf(QMap, Object)}. Either {@link #valueEntries()} or both {@link #keySet()} and
	 * {@link #get(Object)} must be custom. If an implementation supplies custom {@link #keySet()} and {@link #get(Object)} implementations,
	 * it may use {@link #defaultValueEntries(QMap)} for its {@link #valueEntries()} . If an implementation supplies a custom
	 * {@link #valueEntries()} implementation, it may use {@link #defaultKeySet(QMap)} and {@link #defaultValueOf(QMap, Object)} for its
	 * {@link #keySet()} and {@link #get(Object)} implementations, respectively. Using default implementations for both will result in
	 * infinite loops.
	 * </p>
	 *
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 * @param map The map to create a key set for
	 * @return A key set for the map
	 */
	public static <K, V> QSet<K> defaultKeySet(QMap<K, V> map) {
		return QSet.unique(map.valueEntries().map(Entry::getKey));
	}

	/**
	 * @param key The key to get the value for
	 * @return An observable value that changes whenever the value for the given key changes in this map
	 */
	Value<V> valueOf(Object key);

	/**
	 * <p>
	 * A default implementation of {@link #valueOf(Object)}.
	 * </p>
	 * <p>
	 * No {@link QMap} implementation may use the default implementations for its {@link #keySet()}, {@link #get(Object)}, and
	 * {@link #valueEntries()} methods. {@link #defaultValueEntries(QMap)} may not be used in the same implementation as
	 * {@link #defaultKeySet(QMap)} or {@link #defaultValueOf(QMap, Object)}. Either {@link #valueEntries()} or both {@link #keySet()} and
	 * {@link #get(Object)} must be custom. If an implementation supplies custom {@link #keySet()} and {@link #get(Object)} implementations,
	 * it may use {@link #defaultValueEntries(QMap)} for its {@link #valueEntries()} . If an implementation supplies a custom
	 * {@link #valueEntries()} implementation, it may use {@link #defaultKeySet(QMap)} and {@link #defaultValueOf(QMap, Object)} for its
	 * {@link #keySet()} and {@link #get(Object)} implementations, respectively. Using default implementations for both will result in
	 * infinite loops.
	 * </p>
	 *
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 * @param map The map to valueOf the value in
	 * @param key The key to valueOf the value of
	 * @return An observable value representing the value of the given key in this map
	 */
	public static <K, V> Value<V> defaultValueOf(QMap<K, V> map, Object key) {
		Map.Entry<Object, Object> keyEntry = new Map.Entry<Object, Object>() {
			@Override
			public Object getKey() {
				return key;
			}

			@Override
			public Object getValue() {
				return null;
			}

			@Override
			public Object setValue(Object value) {
				return null;
			}

			@Override
			public int hashCode() {
				return Objects.hashCode(key);
			}

			@Override
			public boolean equals(Object obj) {
				return obj instanceof Map.Entry && Objects.equals(((Map.Entry<?, ?>) obj).getKey(), key);
			}

			@Override
			public String toString() {
				return String.valueOf(key);
			}
		};
		return Value.flatten(map.valueEntries().equivalent(keyEntry));
	}

	/**
	 * @param key The key to get the entry for
	 * @return An {@link QMapEntry} that represents the given key's presence in this map
	 */
	default QMapEntry<K, V> entryFor(K key) {
		Value<V> value = valueOf(key);
		if (value instanceof Settable)
			return new SettableEntry<>(key, (Settable<V>) value);
		else
			return new ObsEntryImpl<>(key, value);
	}

	/** @return An observable collection of {@link QMapEntry observable entries} of all the key-value pairs stored in this map */
	QSet<? extends QMapEntry<K, V>> valueEntries();

	/**
	 * <p>
	 * A default implementation of {@link #valueEntries()}.
	 * </p>
	 * <p>
	 * No {@link QMap} implementation may use the default implementations for its {@link #keySet()}, {@link #get(Object)}, and
	 * {@link #valueEntries()} methods. {@link #defaultValueEntries(QMap)} may not be used in the same implementation as
	 * {@link #defaultKeySet(QMap)} or {@link #defaultValueOf(QMap, Object)}. Either {@link #valueEntries()} or both {@link #keySet()} and
	 * {@link #get(Object)} must be custom. If an implementation supplies custom {@link #keySet()} and {@link #get(Object)} implementations,
	 * it may use {@link #defaultValueEntries(QMap)} for its {@link #valueEntries()} . If an implementation supplies a custom
	 * {@link #valueEntries()} implementation, it may use {@link #defaultKeySet(QMap)} and {@link #defaultValueOf(QMap, Object)} for its
	 * {@link #keySet()} and {@link #get(Object)} implementations, respectively. Using default implementations for both will result in
	 * infinite loops.
	 * </p>
	 *
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 * @param map The map to create an entry set for
	 * @return An entry set for the map
	 */
	public static <K, V> QSet<? extends QMapEntry<K, V>> defaultValueEntries(QMap<K, V> map) {
		return QSet.unique(map.keySet().map(map::entryFor));
	}

	/** @return An observable value reflecting the number of key-value pairs stored in this map */
	default Value<Integer> sizeValue() {
		return keySet().sizeValue();
	}

	/** @return An observable collection of all the values stored in this map */
	@Override
	default Qollection<V> values() {
		TypeToken<Value<V>> obValType = new TypeToken<Value<V>>() {}.where(new TypeParameter<V>() {}, getValueType());
		return Qollection.flattenValues(keySet().buildMap(obValType).map(this::valueOf, true).build());
	}

	@Override
	default int size() {
		return valueEntries().size();
	}

	@Override
	default boolean isEmpty() {
		return valueEntries().isEmpty();
	}

	@Override
	default boolean containsKey(Object key) {
		return valueEntries().find(entry -> java.util.Objects.equals(entry.getKey(), key)).get() != null;
	}

	@Override
	default boolean containsValue(Object value) {
		return valueEntries().find(entry -> java.util.Objects.equals(entry.getValue(), value)).get() != null;
	}

	@Override
	default V get(Object key) {
		return valueOf(key).get();
	}

	@Override
	default QSet<Entry<K, V>> entrySet() {
		return (QSet<Entry<K, V>>) (QSet<?>) valueEntries();
	}

	/**
	 * @param <T> The type of values to map to
	 * @param map The function to map values
	 * @return A map with the same key set, but with its values mapped according to the given mapping function
	 */
	default <T> QMap<K, T> map(Function<? super V, T> map) {
		QMap<K, V> outer = this;
		return new QMap<K, T>() {
			private TypeToken<T> theValueType = (TypeToken<T>) TypeToken.of(map.getClass())
				.resolveType(Function.class.getTypeParameters()[1]);

			@Override
			public Transaction lock(boolean write, Object cause) {
				return outer.lock(write, cause);
			}

			@Override
			public TypeToken<K> getKeyType() {
				return outer.getKeyType();
			}

			@Override
			public TypeToken<T> getValueType() {
				return theValueType;
			}

			@Override
			public QSet<K> keySet() {
				return outer.keySet();
			}

			@Override
			public Value<T> valueOf(Object key) {
				return outer.valueOf(key).mapV(map);
			}

			@Override
			public QSet<? extends QMapEntry<K, T>> valueEntries() {
				return QMap.defaultValueEntries(this);
			}

			@Override
			public String toString() {
				return entrySet().toString();
			}
		};
	}

	/**
	 * @param keyFilter The filter to pare down this map's keys
	 * @return A map that has the same content as this map, except for the keys filtered out by the key filter
	 */
	default QMap<K, V> filterKeys(Function<? super K, String> keyFilter) {
		QMap<K, V> outer = this;
		return new QMap<K, V>() {
			@Override
			public Transaction lock(boolean write, Object cause) {
				return outer.lock(write, cause);
			}

			@Override
			public TypeToken<K> getKeyType() {
				return outer.getKeyType();
			}

			@Override
			public TypeToken<V> getValueType() {
				return outer.getValueType();
			}

			@Override
			public QSet<K> keySet() {
				return outer.keySet().filter(keyFilter);
			}

			@Override
			public Value<V> valueOf(Object key) {
				if (getKeyType().getRawType().isInstance(key) && keyFilter.apply((K) key) == null)
					return outer.valueOf(key);
				else
					return Value.constant(getValueType(), null);
			}

			@Override
			public QSet<? extends QMapEntry<K, V>> valueEntries() {
				return outer.valueEntries().filter(entry -> keyFilter.apply(entry.getKey()));
			}
		};
	}

	// TODO Modification control

	/**
	 * @param modMsg The message to return when attempting modification
	 * @return An immutable copy of this map
	 */
	default QMap<K, V> immutable(String modMsg) {
		QMap<K, V> outer = this;
		class Immutable implements QMap<K, V> {
			@Override
			public TypeToken<K> getKeyType() {
				return outer.getKeyType();
			}

			@Override
			public TypeToken<V> getValueType() {
				return outer.getValueType();
			}

			@Override
			public QSet<K> keySet() {
				return outer.keySet().immutable(modMsg);
			}

			@Override
			public Qollection<V> values() {
				return outer.values().immutable(modMsg);
			}

			@Override
			public QSet<? extends QMapEntry<K, V>> valueEntries() {
				return outer.valueEntries().immutable(modMsg);
			}

			@Override
			public Value<V> valueOf(Object key) {
				Value<V> val = outer.valueOf(key);
				if (val instanceof Settable)
					return ((Settable<V>) val).unsettable();
				else
					return val;
			}

			@Override
			public Transaction lock(boolean write, Object cause) {
				return outer.lock(write, cause);
			}

			@Override
			public String toString() {
				return entrySet().toString();
			}
		}
		return new Immutable();
	}

	/**
	 * @param <K> The key type for the map
	 * @param <V> The value type for the map
	 * @param keyType The key type for the map
	 * @param valueType The value type for the map
	 * @return An immutable, empty map with the given types
	 */
	static <K, V> QMap<K, V> empty(TypeToken<K> keyType, TypeToken<V> valueType) {
		return new QMap<K, V>() {
			@Override
			public TypeToken<K> getKeyType() {
				return keyType;
			}

			@Override
			public TypeToken<V> getValueType() {
				return valueType;
			}

			@Override
			public Transaction lock(boolean write, Object cause) {
				return () -> {
				};
			}

			@Override
			public QSet<K> keySet() {
				return QSet.constant(keyType);
			}

			@Override
			public Value<V> valueOf(Object key) {
				return Value.constant(valueType, null);
			}

			@Override
			public QSet<? extends QMapEntry<K, V>> valueEntries() {
				return QMap.defaultValueEntries(this);
			}

			@Override
			public String toString() {
				return entrySet().toString();
			}
		};
	}

	@Override
	default V put(K key, V value) {
		QMapEntry<K, V> entry = entryFor(key);
		if (entry instanceof Settable)
			return ((Settable<V>) entry).set(value, null);
		else
			throw new UnsupportedOperationException();
	}

	@Override
	default V remove(Object key) {
		V ret = get(key);
		keySet().remove(key);
		return ret;
	}

	@Override
	default void putAll(Map<? extends K, ? extends V> m) {
		for (Entry<? extends K, ? extends V> entry : m.entrySet())
			put(entry.getKey(), entry.getValue());
	}

	@Override
	default void clear() {
		keySet().clear();
	}

	/**
	 * @param <K> The type of key in the map
	 * @param <V> The type of value in the map
	 * @param map The map to flatten
	 * @return A map whose values are the values of this map's observable values
	 */
	public static <K, V> QMap<K, V> flatten(QMap<K, ? extends Value<? extends V>> map) {
		return new QMap<K, V>() {
			@Override
			public Transaction lock(boolean write, Object cause) {
				return map.lock(write, cause);
			}

			@Override
			public TypeToken<K> getKeyType() {
				return map.getKeyType();
			}

			@Override
			public TypeToken<V> getValueType() {
				return (TypeToken<V>) map.getValueType().resolveType(Value.class.getTypeParameters()[0]);
			}

			@Override
			public QSet<K> keySet() {
				return map.keySet();
			}

			@Override
			public Value<V> valueOf(Object key) {
				return Value.flatten(map.valueOf(key));
			}

			@Override
			public QSet<? extends QMapEntry<K, V>> valueEntries() {
				TypeToken<QMapEntry<K, V>> type = new TypeToken<QMapEntry<K, V>>() {}.where(new TypeParameter<K>() {}, map.getKeyType())
					.where(new TypeParameter<V>() {}, getValueType());
				return map.valueEntries().buildMap(type).mapEquiv(entry -> (QMapEntry<K, V>) QMapEntry.flatten(entry), false).build();
			}
		};
	}

	/**
	 * A simple entry implementation
	 *
	 * @param <K> The key type for this entry
	 * @param <V> The value type for this entry
	 */
	class ObsEntryImpl<K, V> implements QMapEntry<K, V> {
		private final K theKey;

		private final Value<V> theValue;

		ObsEntryImpl(K key, Value<V> value) {
			theKey = key;
			theValue = value;
		}

		protected Value<V> getWrapped() {
			return theValue;
		}

		@Override
		public K getKey() {
			return theKey;
		}

		@Override
		public V getValue() {
			return theValue.get();
		}

		@Override
		public V setValue(V value) {
			throw new UnsupportedOperationException();
		}

		@Override
		public TypeToken<V> getType() {
			return theValue.getType();
		}

		@Override
		public int hashCode() {
			return Objects.hashCode(theKey);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			return obj instanceof ObsEntryImpl && Objects.equals(theKey, ((ObsEntryImpl<?, ?>) obj).theKey);
		}

		@Override
		public String toString() {
			return theKey + "=" + theValue.get();
		}
	}

	/**
	 * A simple settable entry implementation
	 *
	 * @param <K> The key type for this entry
	 * @param <V> The value type for this entry
	 */
	class SettableEntry<K, V> extends ObsEntryImpl<K, V> implements Settable<V> {
		public SettableEntry(K key, Settable<V> value) {
			super(key, value);
		}

		@Override
		protected Settable<V> getWrapped() {
			return (Settable<V>) super.getWrapped();
		}

		@Override
		public <V2 extends V> V set(V2 value, Object cause) throws IllegalArgumentException {
			return getWrapped().set(value, cause);
		}

		@Override
		public <V2 extends V> String isAcceptable(V2 value) {
			return getWrapped().isAcceptable(value);
		}

		@Override
		public Value<String> isEnabled() {
			return getWrapped().isEnabled();
		}
	}
}
