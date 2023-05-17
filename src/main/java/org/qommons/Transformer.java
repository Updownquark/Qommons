package org.qommons;

import java.util.function.BiFunction;

import org.qommons.ClassMap.TypeMatch;
import org.qommons.ex.ExBiFunction;

/**
 * A simple utility to transform java objects by type
 * 
 * @param <X> The type of exception that this transformer may throw
 */
public interface Transformer<X extends Throwable> {
	/**
	 * A function capable of modifying a transformed value
	 * 
	 * @param <S> The type of the source value that was transformed
	 * @param <T> The type of the transformed value
	 * @param <X> The type of the exception that this modifier or the transformer may throw
	 */
	public interface Modifier<S, T, X extends Throwable> {
		/**
		 * Modifies a transformed value
		 * 
		 * @param <T2> The type of the transformed value
		 * @param source The source value that was transformed
		 * @param value The transformed value
		 * @param tx The transformer
		 * @return The modified value
		 * @throws X If the modification fails
		 */
		<T2 extends T> T2 modify(S source, T2 value, Transformer<X> tx) throws X;

		/**
		 * A sequence of two or modifiers
		 * 
		 * @param <S> The source type of the first modifier, and this modifier
		 * @param <T> The target type of the first modifier, also the source type of the second
		 * @param <X> The target type of the second modifier, and this modifier
		 */
		public class Composite<S, T, X extends Throwable> implements Modifier<S, T, X> {
			private final Modifier<? super S, T, X> theFirst;
			private final Modifier<? super S, T, X> theSecond;

			/**
			 * @param first The first modifier to apply
			 * @param second The second modifier to apply
			 */
			public Composite(Modifier<? super S, T, X> first, Modifier<? super S, T, X> second) {
				theFirst = first;
				theSecond = second;
			}

			@Override
			public <T2 extends T> T2 modify(S source, T2 value, Transformer<X> tx) throws X {
				T2 interm = theFirst.modify(source, value, tx);
				return theSecond.modify(source, interm, tx);
			}
		}
	}
	/**
	 * @param <T> The type of the value to transform the source into
	 * @param source The source value to transform
	 * @param as The type of the value to transform the source into
	 * @return The transformed value
	 * @throws X If the transformation is not configured or if it fails
	 */
	<T> T transform(Object source, Class<T> as) throws X;

	/**
	 * @param <X> The type of exception that this transformer may throw
	 * @return A builder to configure a new transformer
	 */
	static <X extends Throwable> Builder<X> build() {
		return new Builder<>();
	}

	/**
	 * Configures a new {@link Transformer}
	 * 
	 * @param <X> The type of exception that this transformer may throw
	 */
	public class Builder<X extends Throwable> {
		private final ClassMap<ClassMap<? extends ExBiFunction<?, Transformer<X>, ?, ? extends X>>> theTypeTransformers;
		private final ClassMap<ClassMap<? extends Modifier<?, ?, X>>> theModifiers;
		private BiFunction<Class<?>, Class<?>, X> theNoTransformerException;

		Builder() {
			this(new ClassMap<>(), new ClassMap<>(), null);
		}

		Builder(ClassMap<ClassMap<? extends ExBiFunction<?, Transformer<X>, ?, ? extends X>>> typeTransformers,
			ClassMap<ClassMap<? extends Modifier<?, ?, X>>> modifiers,
			BiFunction<Class<?>, Class<?>, X> noTransformerException) {
			theTypeTransformers = typeTransformers;
			theModifiers = modifiers;
			theNoTransformerException = noTransformerException;
		}

		/**
		 * Populates the future transformer with a new transformation type capability
		 * 
		 * @param <S> The type to convert from
		 * @param <T> The type to convert to
		 * @param sourceType The type to convert from
		 * @param targetType The type to convert to
		 * @param transformer The function to do the transformation
		 * @return This builder
		 */
		public <S, T> Builder<X> with(Class<S> sourceType, Class<T> targetType,
			ExBiFunction<? super S, Transformer<X>, ? extends T, ? extends X> transformer) {
			ClassMap<ExBiFunction<?, Transformer<X>, ? extends T, ? extends X>> targetTransformers;
			targetTransformers = (ClassMap<ExBiFunction<?, Transformer<X>, ? extends T, ? extends X>>) theTypeTransformers
				.computeIfAbsent(targetType, () -> new ClassMap<>());
			targetTransformers.put(sourceType, transformer);
			return this;
		}

		/**
		 * Populates the future transformer with a new modification type capability, which relies on a transformer but modifies the
		 * transformed value by type
		 * 
		 * @param <S> The type to convert from
		 * @param <T> The type to convert to
		 * @param sourceType The type to convert from
		 * @param targetType The type to convert to
		 * @param modifier The modifier for transformed values
		 * @return This builder
		 */
		public <S, T> Builder<X> modifyWith(Class<S> sourceType, Class<T> targetType, Modifier<? super S, T, X> modifier) {
			ClassMap<Modifier<?, T, X>> targetModifiers;
			targetModifiers = (ClassMap<Modifier<?, T, X>>) theModifiers.computeIfAbsent(targetType, () -> new ClassMap<>());
			targetModifiers.compute(sourceType, pre -> {
				if (pre == null)
					return modifier;
				else
					return new Modifier.Composite<>((Modifier<S, T, X>) pre, modifier);
			});
			return this;
		}

		/**
		 * @param noTransformerException The function to create the exception to throw in the case of a requested transformation which has
		 *        not been configured
		 * @return This builder
		 */
		public Builder<X> withNoTransformerException(BiFunction<Class<?>, Class<?>, X> noTransformerException) {
			theNoTransformerException = noTransformerException;
			return this;
		}

		/** @return A transformer configured with the transformation capabilities configured in this builder */
		public CompositeTransformer<X> build() {
			return new CompositeTransformer<>(deepCopy(theTypeTransformers), deepCopy(theModifiers), theNoTransformerException);
		}

		static <T> ClassMap<ClassMap<? extends T>> deepCopy(ClassMap<ClassMap<? extends T>> classMap) {
			ClassMap<ClassMap<? extends T>> copy = new ClassMap<>();
			for (BiTuple<Class<?>, ClassMap<? extends T>> entry : classMap.getAllEntries())
				copy.put(entry.getValue1(), entry.getValue2().copy());
			return copy;
		}
	}

	/**
	 * Default {@link Transformer} implementation returned by {@link Builder#build()}
	 * 
	 * @param <X> The type of exception that this transformer may throw
	 */
	public class CompositeTransformer<X extends Throwable> implements Transformer<X> {
		private final ClassMap<ClassMap<? extends ExBiFunction<?, Transformer<X>, ?, ? extends X>>> theTypeTransformers;
		private final ClassMap<ClassMap<? extends Modifier<?, ?, X>>> theModifiers;
		private BiFunction<Class<?>, Class<?>, X> theNoTransformerException;

		CompositeTransformer(ClassMap<ClassMap<? extends ExBiFunction<?, Transformer<X>, ?, ? extends X>>> typeTransformers,
			ClassMap<ClassMap<? extends Modifier<?, ?, X>>> modifiers,
			BiFunction<Class<?>, Class<?>, X> noTransformerException) {
			theTypeTransformers = typeTransformers;
			theModifiers = modifiers;
			theNoTransformerException = noTransformerException;
		}

		@Override
		public <T> T transform(Object source, Class<T> as) throws X {
			if (source == null)
				return null;
			return _transform(source, as);
		}

		private <S, T> T _transform(S source, Class<T> targetType) throws X {
			Class<S> sourceType = (Class<S>) source.getClass();
			ExBiFunction<? super S, Transformer<X>, ? extends T, ? extends X> typeTransformer = null;
			for (ClassMap<? extends ExBiFunction<?, Transformer<X>, ?, ? extends X>> targetTransformers : theTypeTransformers
				.getAll(targetType, ClassMap.TypeMatch.SUB_TYPE)) {
				typeTransformer = (ExBiFunction<? super S, Transformer<X>, ? extends T, ? extends X>) targetTransformers.get(sourceType,
					ClassMap.TypeMatch.SUPER_TYPE);
				if (typeTransformer != null)
					break;
			}
			if (typeTransformer == null) {
				if (theNoTransformerException != null)
					throw theNoTransformerException.apply(sourceType, targetType);
				else
					throw new IllegalArgumentException("No transformer for " + sourceType.getName() + "->" + targetType.getName());
			}
			T transformed = typeTransformer.apply(source, this);
			Class<T> targetType2 = transformed == null ? targetType : (Class<T>) transformed.getClass();

			for (ClassMap<? extends Modifier<?, ?, ? extends X>> targetModifiers : theModifiers.getAll(targetType2, TypeMatch.SUPER_TYPE)) {
				for (Modifier<?, ?, ? extends X> modifier : targetModifiers.getAll(sourceType, TypeMatch.SUPER_TYPE)) {
					Modifier<? super S, ? super T, X> modifier2 = (Modifier<? super S, ? super T, X>) modifier;
					transformed = modifier2.<T> modify(source, transformed, this);
				}
			}
			return transformed;
		}

		/**
		 * @return A builder pre-populated with all of this transformer's capabilities, which can be used to create a new transformer with
		 *         additional capabilities
		 */
		public Builder<X> copy() {
			return new Builder<>(Builder.deepCopy(theTypeTransformers), Builder.deepCopy(theModifiers), theNoTransformerException);
		}
	}
}
