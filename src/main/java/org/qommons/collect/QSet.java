package org.qommons.collect;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.qommons.Equalizer.EqualizerNode;
import org.qommons.IterableUtils;
import org.qommons.Transaction;
import org.qommons.collect.QSet.UniqueElement;
import org.qommons.collect.Quiterator.CollectionElement;
import org.qommons.value.Value;

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;

public interface QSet<E> extends Qollection<E>, Set<E> {
	@Override
	default Iterator<E> iterator() {
		return Qollection.super.iterator();
	}

	@Override
	abstract Quiterator<E> spliterator();

	@Override
	default boolean isEmpty() {
		return Qollection.super.isEmpty();
	}

	@Override
	default boolean contains(Object o) {
		return Qollection.super.contains(o);
	}

	@Override
	default boolean containsAll(java.util.Collection<?> coll) {
		return Qollection.super.containsAll(coll);
	}

	/**
	 * @param o The object to get the equivalent of
	 * @return The object in this set whose value is equivalent to the given value
	 */
	default Value<E> equivalent(Object o) {
		return new QSetEquivalentFinder<>(this, o);
	}

	@Override
	default E[] toArray() {
		return Qollection.super.toArray();
	}

	@Override
	default <T> T[] toArray(T[] a) {
		return Qollection.super.toArray(a);
	}

	/**
	 * @param filter The filter function
	 * @return A collection containing all non-null elements passing the given test
	 */
	@Override
	default QSet<E> filter(Function<? super E, String> filter) {
		return new FilteredSet<>(this, getType(), filter);
	}

	/**
	 * @param <T> The type for the new collection
	 * @param type The type to filter this collection by
	 * @return A collection backed by this collection, consisting only of elements in this collection whose values are instances of the
	 *         given class
	 */
	@Override
	default <T> QSet<T> filter(Class<T> type) {
		return new FilteredSet<>(this, TypeToken.of(type), value -> {
			if (type.isInstance(value))
				return null;
			else
				return value.getClass().getName() + " is not an instance of " + type.getName();
		});
	}

	/**
	 * Creates a mapped set, relying on the caller to guarantee that values returned by the function are equivalent to the argument
	 *
	 * @param <T> The type of the mapped collection
	 * @param type The run-time type for the mapped collection (may be null)
	 * @param map The mapping function to map the elements of this collection
	 * @param reverse The reverse function if addition support is desired for the mapped collection
	 * @return The mapped collection
	 */
	default <T> QSet<T> mapEquivalent(Function<? super E, T> map, Function<? super T, E> reverse) {
		return mapEquivalent((TypeToken<T>) TypeToken.of(map.getClass()).resolveType(Function.class.getTypeParameters()[1]), map, reverse);
	}

	/**
	 * Creates a mapped set, relying on the caller to guarantee that values returned by the function are equivalent to the argument
	 *
	 * @param <T> The type of the mapped collection
	 * @param type The run-time type for the mapped collection (may be null)
	 * @param map The mapping function to map the elements of this collection
	 * @param reverse The reverse function if addition support is desired for the mapped collection
	 * @return The mapped collection
	 */
	default <T> QSet<T> mapEquivalent(TypeToken<T> type, Function<? super E, T> map, Function<? super T, E> reverse) {
		return new MappedQSet<>(this, type, map, reverse);
	}

	@Override
	default QSet<E> immutable() {
		return new ImmutableQSet<>(this);
	}

	@Override
	default QSet<E> filterRemove(Function<? super E, String> filter) {
		return (QSet<E>) Qollection.super.filterRemove(filter);
	}

	@Override
	default QSet<E> noRemove() {
		return (QSet<E>) Qollection.super.noRemove();
	}

	@Override
	default QSet<E> filterAdd(Function<? super E, String> filter) {
		return (QSet<E>) Qollection.super.filterAdd(filter);
	}

	@Override
	default QSet<E> noAdd() {
		return (QSet<E>) Qollection.super.noAdd();
	}

	@Override
	default QSet<E> filterModification(Function<? super E, String> removeFilter, Function<? super E, String> addFilter) {
		return new ModFilteredSet<>(this, removeFilter, addFilter);
	}

	/**
	 * Turns an observable value containing an observable collection into the contents of the value
	 * 
	 * @param <E> The type of elements in the set
	 * @param collectionObservable The observable value
	 * @return A collection representing the contents of the value, or a zero-length collection when null
	 */
	public static <E> QSet<E> flattenValue(Value<? extends QSet<E>> collectionObservable) {
		return new FlattenedValueSet<>(collectionObservable);
	}

	/**
	 * A default toString() method for set implementations to use
	 *
	 * @param set The set to print
	 * @return The string representation of the set
	 */
	public static String toString(QSet<?> set) {
		StringBuilder ret = new StringBuilder("{");
		boolean first = true;
		try (Transaction t = set.lock(false, null)) {
			for (Object value : set) {
				if (!first) {
					ret.append(", ");
				} else
					first = false;
				ret.append(value);
			}
		}
		ret.append('}');
		return ret.toString();
	}

	/**
	 * @param <T> The type of the collection
	 * @param type The run-time type of the collection
	 * @param coll The collection with elements to wrap
	 * @return A collection containing the given elements that cannot be changed
	 */
	public static <T> QSet<T> constant(TypeToken<T> type, java.util.Collection<T> coll) {
		LinkedHashMap<T, CollectionElement<T>> modSet = new LinkedHashMap<>(coll.size());
		for (T value : coll) {
			if (!modSet.containsKey(value)) {
				modSet.put(value, new CollectionElement<T>() {
					@Override
					public TypeToken<T> getType() {
						return type;
					}

					@Override
					public T get() {
						return value;
					}

					@Override
					public <V extends T> String isAcceptable(V value2) {
						return "This collection is immutable";
					}

					@Override
					public <V extends T> T set(V value2, Object cause) throws IllegalArgumentException {
						throw new IllegalArgumentException("This collection is immutable");
					}

					@Override
					public Value<String> isEnabled() {
						return Value.constant("This collection is immutable");
					}

					@Override
					public String canRemove() {
						return "This collection is immutable";
					}

					@Override
					public void remove() throws IllegalArgumentException {
						throw new IllegalArgumentException("This collection is immutable");
					}

					@Override
					public String canAdd(T toAdd) {
						return "This collection is immutable";
					}

					@Override
					public void add(T toAdd) throws IllegalArgumentException {
						throw new IllegalArgumentException("This collection is immutable");
					}
				});
			}
		}
		class ConstantQSet implements PartialSetImpl<T> {
			@Override
			public Transaction lock(boolean write, Object cause) {
				return () -> {
				};
			}

			@Override
			public TypeToken<T> getType() {
				return type;
			}

			@Override
			public int size() {
				return modSet.size();
			}

			@Override
			public Iterator<T> iterator() {
				return IterableUtils.immutableIterator(modSet.keySet().iterator());
			}

			@Override
			public String canRemove(Object value) {
				return "This collection is immutable";
			}

			@Override
			public String canAdd(T value) {
				return "This collection is immutable";
			}

			@Override
			public Quiterator<T> spliterator() {
				Supplier<Function<T, CollectionElement<T>>> elementSupplier = () -> {
					return value -> modSet.get(value);
				};
				return new Quiterator.SimpleQuiterator<T, T>(modSet.keySet().spliterator(), elementSupplier) {
					@Override
					public int characteristics() {
						return super.characteristics() | Spliterator.IMMUTABLE;
					}
				};
			}
		}
		return new ConstantQSet();
	}

	/**
	 * @param <T> The type of the collection
	 * @param type The run-time type of the collection
	 * @param values The array with elements to wrap
	 * @return A collection containing the given elements that cannot be changed
	 */
	public static <T> QSet<T> constant(TypeToken<T> type, T... values) {
		return constant(type, java.util.Arrays.asList(values));
	}

	/**
	 * @param <T> The type of the collection
	 * @param coll The collection to turn into a set
	 * @return A set containing all unique elements of the given collection
	 */
	public static <T> QSet<T> unique(Qollection<T> coll) {
		return new CollectionWrappingSet<>(coll);
	}

	/**
	 * An extension of QSet that implements some of the redundant methods and throws UnsupportedOperationExceptions for modifications.
	 *
	 * @param <E> The type of element in the set
	 */
	interface PartialSetImpl<E> extends PartialQollectionImpl<E>, QSet<E> {
		@Override
		default boolean remove(Object o) {
			return PartialQollectionImpl.super.remove(o);
		}

		@Override
		default boolean removeAll(Collection<?> c) {
			return PartialQollectionImpl.super.removeAll(c);
		}

		@Override
		default boolean retainAll(Collection<?> c) {
			return PartialQollectionImpl.super.retainAll(c);
		}

		@Override
		default void clear() {
			PartialQollectionImpl.super.clear();
		}

		@Override
		default boolean add(E value) {
			return PartialQollectionImpl.super.add(value);
		}

		@Override
		default boolean addAll(Collection<? extends E> c) {
			return PartialQollectionImpl.super.addAll(c);
		}

		@Override
		abstract Quiterator<E> spliterator();
	}

	/**
	 * Implements {@link QSet#equivalent(Object)}
	 *
	 * @param <E> The type of the set to find the value in
	 */
	class QSetEquivalentFinder<E> implements Value<E> {
		private final QSet<E> theSet;

		private final Object theKey;

		protected QSetEquivalentFinder(QSet<E> set, Object key) {
			theSet = set;
			theKey = key;
		}

		@Override
		public TypeToken<E> getType() {
			return theSet.getType();
		}

		@Override
		public E get() {
			for (E value : theSet) {
				if (Objects.equals(value, theKey))
					return value;
			}
			return null;
		}
	}

	/**
	 * Implements {@link QSet#filter(Function)} and {@link QSet#filter(Class)}
	 *
	 * @param <E> The type of the set to filter
	 * @param <T> the type of the mapped set
	 */
	class FilteredSet<E, T> extends FilterMappedQollection<E, T> implements PartialSetImpl<T> {
		protected FilteredSet(QSet<E> wrap, TypeToken<T> type, Function<? super E, String> filter) {
			super(wrap, type, value -> {
				String pass = filter.apply(value);
				return new FilterMapResult<>(pass == null ? (T) value : null, pass);
			}, value -> (E) value);
		}

		@Override
		protected QSet<E> getWrapped() {
			return (QSet<E>) super.getWrapped();
		}
	}

	/**
	 * Implements {@link QSet#mapEquivalent(TypeToken, Function, Function)}
	 *
	 * @param <E> The type of the set to map
	 * @param <T> The type of the mapped set
	 */
	class MappedQSet<E, T> extends FilterMappedQollection<E, T> implements QSet<T> {
		protected MappedQSet(QSet<E> wrap, TypeToken<T> type, Function<? super E, T> map, Function<? super T, E> reverse) {
			super(wrap, type, value -> new FilterMapResult<>(map.apply(value), null), reverse);
		}

		@Override
		protected QSet<E> getWrapped() {
			return (QSet<E>) super.getWrapped();
		}
	}

	/**
	 * An observable set that cannot be modified directly, but reflects the value of a wrapped set as it changes
	 *
	 * @param <E> The type of elements in the set
	 */
	class ImmutableQSet<E> extends ImmutableQollection<E> implements PartialSetImpl<E> {
		protected ImmutableQSet(QSet<E> wrap) {
			super(wrap);
		}

		@Override
		protected QSet<E> getWrapped() {
			return (QSet<E>) super.getWrapped();
		}

		@Override
		public ImmutableQSet<E> immutable() {
			return this;
		}
	}

	/**
	 * Implements {@link QSet#filterModification(Function, Function)}
	 *
	 * @param <E> The type of elements in the set
	 */
	class ModFilteredSet<E> extends ModFilteredQollection<E> implements PartialSetImpl<E> {
		public ModFilteredSet(QSet<E> wrapped, Function<? super E, String> removeFilter, Function<? super E, String> addFilter) {
			super(wrapped, removeFilter, addFilter);
		}

		@Override
		protected QSet<E> getWrapped() {
			return (QSet<E>) super.getWrapped();
		}
	}

	/**
	 * Implements {@link QSet#flattenValue(Value)}
	 *
	 * @param <E> The type of elements in the set
	 */
	class FlattenedValueSet<E> extends FlattenedValueQollection<E> implements PartialSetImpl<E> {
		public FlattenedValueSet(Value<? extends QSet<E>> collectionObservable) {
			super(collectionObservable);
		}

		@Override
		protected Value<? extends QSet<E>> getWrapped() {
			return (Value<? extends QSet<E>>) super.getWrapped();
		}
	}

	/**
	 * Implements {@link QSet#unique(Qollection)}
	 *
	 * @param <E> The type of elements in the set
	 */
	class CollectionWrappingSet<E> implements PartialSetImpl<E> {
		private final Qollection<E> theCollection;

		public CollectionWrappingSet(Qollection<E> collection) {
			theCollection = collection;
		}

		protected Qollection<E> getWrapped() {
			return theCollection;
		}

		@Override
		public TypeToken<E> getType() {
			return theCollection.getType();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theCollection.lock(write, cause);
		}

		@Override
		public int size() {
			HashSet<E> set = new HashSet<>();
			for (E value : theCollection)
				set.add(value);
			return set.size();
		}

		@Override
		public Iterator<E> iterator() {
			return unique(theCollection.iterator());
		}

		@Override
		public String canRemove(Object value) {
			return theCollection.canRemove(value);
		}

		@Override
		public String canAdd(E value) {
			String canAdd = theCollection.canAdd(value);
			if (canAdd != null)
				return canAdd;
			HashSet<E> set = new HashSet<>();
			for (E v : theCollection)
				set.add(v);
			return set.contains(value) ? "This value is already present in the set" : null;
		}

		protected Iterator<E> unique(Iterator<E> backing) {
			return new Iterator<E>() {
				private final HashSet<E> set = new HashSet<>();

				private E nextVal;

				@Override
				public boolean hasNext() {
					while (nextVal == null && backing.hasNext()) {
						nextVal = backing.next();
						if (!set.add(nextVal))
							nextVal = null;
					}
					return nextVal != null;
				}

				@Override
				public E next() {
					if (nextVal == null && !hasNext())
						throw new java.util.NoSuchElementException();
					E ret = nextVal;
					nextVal = null;
					return ret;
				}

				@Override
				public void remove() {
					backing.remove();
				}
			};
		}

		protected class UniqueElementTracking {
			protected Map<EqualizerNode<E>, UniqueElement<E>> elements = new LinkedHashMap<>();
		}

		@Override
		public Subscription onElement(Consumer<? super ObservableElement<E>> onElement) {
			return onElement(onElement, Qollection::onElement);
		}

		protected Subscription onElement(Consumer<? super ObservableElement<E>> onElement,
			BiFunction<Qollection<E>, Consumer<? super ObservableElement<E>>, Subscription> subscriber) {
			final UniqueElementTracking tracking = createElementTracking();
			return subscriber.apply(theCollection, element -> {
				element.subscribe(new Observer<ValueEvent<E>>() {
					@Override
					public <EV extends ValueEvent<E>> void onNext(EV event) {
						EqualizerNode<E> newNode = new EqualizerNode<>(theEqualizer, event.getValue());
						UniqueElement<E> newUnique = tracking.elements.get(newNode);
						if (newUnique == null)
							newUnique = addUniqueElement(tracking, newNode);
						boolean addElement = newUnique.isEmpty();
						boolean reAdd;
						if (event.isInitial()) {
							reAdd = newUnique.addElement(element, event);
						} else {
							EqualizerNode<E> oldNode = new EqualizerNode<>(theEqualizer, event.getOldValue());
							UniqueElement<E> oldUnique = tracking.elements.get(oldNode);
							if (oldUnique == newUnique) {
								reAdd = newUnique.changed(element);
							} else {
								if (oldUnique != null)
									removeFromOld(oldUnique, oldNode, event);
								reAdd = newUnique.addElement(element, event);
							}
						}
						if (addElement)
							onElement.accept(newUnique);
						else if (reAdd) {
							newUnique.reset(event);
							onElement.accept(newUnique);
						}
					}

					@Override
					public <EV extends ValueEvent<E>> void onCompleted(EV event) {
						EqualizerNode<E> node = new EqualizerNode<>(theEqualizer, event.getValue());
						UniqueElement<E> unique = tracking.elements.get(node);
						if (unique != null)
							removeFromOld(unique, node, event);
					}

					void removeFromOld(UniqueElement<E> unique, EqualizerNode<E> node, Object cause) {
						boolean reAdd = unique.removeElement(element, cause);
						if (unique.isEmpty())
							tracking.elements.remove(node);
						else if (reAdd) {
							unique.reset(cause);
							onElement.accept(unique);
						}
					}
				});
			});
		}

		protected UniqueElementTracking createElementTracking() {
			return new UniqueElementTracking();
		}

		protected UniqueElement<E> addUniqueElement(UniqueElementTracking tracking, EqualizerNode<E> node) {
			UniqueElement<E> unique = new UniqueElement<>(this, false);
			tracking.elements.put(node, unique);
			return unique;
		}

		@Override
		public String toString() {
			return QSet.toString(this);
		}
	}

	/**
	 * Implements elements for {@link QSet#unique(Qollection)}
	 *
	 * @param <E> The type of value in the element
	 */
	class UniqueElement<E> implements ObservableElement<E> {
		private final CollectionWrappingSet<E> theSet;
		private final boolean isAlwaysUsingFirst;
		private final Collection<ObservableElement<E>> theElements;
		private final SimpleSettableValue<ObservableElement<E>> theCurrentElement;

		UniqueElement(CollectionWrappingSet<E> set, boolean alwaysUseFirst) {
			theSet = set;
			isAlwaysUsingFirst = alwaysUseFirst;
			theElements = createElements();
			theCurrentElement = new SimpleSettableValue<>(
				new TypeToken<ObservableElement<E>>() {}.where(new TypeParameter<E>() {}, theSet.getType()), true);
		}

		protected Collection<ObservableElement<E>> createElements() {
			return new ArrayDeque<>();
		}

		@Override
		public TypeToken<E> getType() {
			return theSet.getType();
		}

		@Override
		public E get() {
			return theElements.isEmpty() ? null : theElements.iterator().next().get();
		}

		@Override
		public Subscription subscribe(Observer<? super ValueEvent<E>> observer) {
			return ObservableElement.flatten(theCurrentElement).subscribe(observer);
		}

		@Override
		public boolean isSafe() {
			return theSet.isSafe();
		}

		@Override
		public Value<E> persistent() {
			return theElements.isEmpty() ? Value.constant(theSet.getType(), null) : theElements.iterator().next().persistent();
		}

		protected ObservableElement<E> getCurrentElement() {
			return theCurrentElement.get();
		}

		protected boolean addElement(ObservableElement<E> element, Object cause) {
			theElements.add(element);
			boolean newBest;
			if (isAlwaysUsingFirst)
				newBest = theElements.iterator().next() == element;
			else
				newBest = theCurrentElement.get() == null;
			if (newBest)
				return setCurrentElement(element, cause);
			else
				return false;
		}

		protected boolean removeElement(ObservableElement<E> element, Object cause) {
			theElements.remove(element);
			if (theCurrentElement.get() == element)
				return setCurrentElement(theElements.isEmpty() ? null : theElements.iterator().next(), cause);
			else
				return false;
		}

		protected boolean changed(ObservableElement<E> element) {
			return false;
		}

		protected void reset(Object cause) {
			theCurrentElement.set(theElements.iterator().next(), cause);
		}

		protected boolean setCurrentElement(ObservableElement<E> element, Object cause) {
			theCurrentElement.set(element, cause);
			return false;
		}

		protected boolean isEmpty() {
			return theElements.isEmpty();
		}
	}
}
