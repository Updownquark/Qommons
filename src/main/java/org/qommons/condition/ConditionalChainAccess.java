package org.qommons.condition;

import java.util.function.Function;

import org.qommons.collect.BetterList;

/**
 * A compound {@link ConditionalValueAccess}
 *
 * @param <E> The entity type
 * @param <T> The information type
 */
public class ConditionalChainAccess<E, T> implements ConditionalValueAccess<E, T> {
	private final BetterList<ConditionalFieldAccess<?, ?>> theFieldSequence;

	<F> ConditionalChainAccess(ConditionalFieldAccess<E, F> firstField, ConditionalFieldAccess<? super F, T> secondField) {
		this(new ConditionalFieldAccess[] { firstField, secondField });
	}

	ConditionalChainAccess(ConditionalFieldAccess<?, ?>[] fields) {
		ConditionalEntity<?> entity = null;
		for (int f = 0; f < fields.length; f++) {
			if (f > 0) {
				if (entity.owns(fields[f])) {// Fine
				} else if (entity.isAssignableFrom(fields[f].getSourceEntity())) {
					fields[f] = entity.getOverriddenField(fields[f]);
				} else
					throw new IllegalArgumentException("Bad field sequence--" + fields[f] + " does not follow " + fields[f - 1]);
			}
			entity = fields[f].getTargetEntity();
			if (f < fields.length - 1 && entity == null)
				throw new IllegalArgumentException("Bad field sequence--intermediate field does not target an entity: " + fields[f]);
		}
		theFieldSequence = BetterList.of(fields);
	}

	public BetterList<? extends ConditionalFieldAccess<?, ?>> getFieldSequence() {
		return theFieldSequence;
	}

	@Override
	public ConditionalEntity<E> getSourceEntity() {
		return ((ConditionalValueAccess<E, ?>) theFieldSequence.getFirst()).getSourceEntity();
	}

	@Override
	public ConditionalEntity<T> getTargetEntity() {
		return (ConditionalEntity<T>) theFieldSequence.getLast().getTargetEntity();
	}

	@Override
	public boolean isOverride(ConditionalValueAccess<? extends E, ?> field) {
		if (!(field instanceof ConditionalChainAccess))
			return false;
		ConditionalChainAccess<?, ?> other = (ConditionalChainAccess<?, ?>) field;
		if (theFieldSequence.size() != other.theFieldSequence.size())
			return false;
		for (int f = 0; f < theFieldSequence.size(); f++)
			if (!((ConditionalFieldAccess<E, ?>) theFieldSequence.get(f)).isOverride(//
				(ConditionalFieldAccess<E, ?>) other.theFieldSequence.get(f)))
				return false;
		return true;
	}

	@Override
	public <T2> ConditionalChainAccess<E, T2> dot(Function<? super T, T2> attr) {
		ConditionalEntity<T> target = getTargetEntity();
		if (target == null)
			throw new UnsupportedOperationException("This method can only be used with entity-typed fields");
		ConditionalFieldAccess<T, T2> lastField = target.getField(attr);
		return dot(lastField);
	}

	@Override
	public <T2> ConditionalChainAccess<E, T2> dot(ConditionalFieldAccess<? super T, T2> field) {
		ConditionalEntity<T> target = getTargetEntity();
		if (target == null)
			throw new UnsupportedOperationException("This method can only be used with entity-typed fields");
		else if (!field.getSourceEntity().isAssignableFrom(target))
			throw new IllegalArgumentException(field + " cannot be applied to " + target);
		else if (!field.getSourceEntity().equals(target))
			field = (ConditionalFieldAccess<? super T, T2>) target.getOverriddenField(field);
		ConditionalFieldAccess<?, ?>[] fields = new ConditionalFieldAccess[theFieldSequence.size() + 1];
		theFieldSequence.toArray(fields);
		fields[fields.length - 1] = field;
		return new ConditionalChainAccess<>(fields);
	}

	@Override
	public T get(E entity) {
		Object value = entity;
		for (ConditionalFieldAccess<?, ?> field : theFieldSequence)
			value = ((ConditionalFieldAccess<Object, Object>) field).get(value);
		return (T) value;
	}

	@Override
	public int compare(T o1, T o2) {
		if (o1 == o2)
			return 0;
		Object v1 = o1;
		Object v2 = o2;
		for (ConditionalValueAccess<?, ?> field : theFieldSequence) {
			ConditionalValueAccess<Object, Object> f = (ConditionalValueAccess<Object, Object>) field;
			v1 = f.get(v1);
			v2 = f.get(v2);
			if (f.compare(v1, v2) == 0)
				return 0;
		}
		return 0;
	}

	@Override
	public int compareTo(ConditionalValueAccess<?, ?> o) {
		if (!(o instanceof ConditionalChainAccess))
			return 1;
		ConditionalChainAccess<E, ?> other = (ConditionalChainAccess<E, ?>) o;
		for (int f = 0; f < theFieldSequence.size() && f < other.theFieldSequence.size(); f++) {
			int comp = ((ConditionalValueAccess<E, ?>) theFieldSequence.get(f)).compareTo(//
				other.theFieldSequence.get(f));
			if (comp != 0)
				return comp;
		}
		return Integer.compare(theFieldSequence.size(), other.theFieldSequence.size());
	}

	@Override
	public int hashCode() {
		return theFieldSequence.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (o == this)
			return true;
		else if (!(o instanceof ConditionalChainAccess))
			return false;
		return theFieldSequence.equals(((ConditionalChainAccess<?, ?>) o).theFieldSequence);
	}

	@Override
	public String toString() {
		StringBuilder str = new StringBuilder().append(theFieldSequence.getFirst().getSourceEntity());
		for (ConditionalValueAccess<?, ?> field : theFieldSequence)
			str.append('.').append(field);
		return str.toString();
	}
}
