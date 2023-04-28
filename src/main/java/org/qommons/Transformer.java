package org.qommons;

import java.util.function.BiFunction;

import org.qommons.ex.ExBiFunction;

/**
 * A simple utility to transform java objects by type
 * 
 * @param <X> The type of exception that this transformer may throw
 */
public interface Transformer<X extends Throwable> {
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
		private BiFunction<Class<?>, Class<?>, X> theNoTransformerException;

		Builder() {
			this(new ClassMap<>(), null);
		}

		Builder(ClassMap<ClassMap<? extends ExBiFunction<?, Transformer<X>, ?, ? extends X>>> typeTransformers,
			BiFunction<Class<?>, Class<?>, X> noTransformerException) {
			theTypeTransformers = typeTransformers;
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
			return new CompositeTransformer<>(deepCopy(theTypeTransformers), theNoTransformerException);
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
		private BiFunction<Class<?>, Class<?>, X> theNoTransformerException;

		CompositeTransformer(ClassMap<ClassMap<? extends ExBiFunction<?, Transformer<X>, ?, ? extends X>>> typeTransformers,
			BiFunction<Class<?>, Class<?>, X> noTransformerException) {
			theTypeTransformers = typeTransformers;
			theNoTransformerException = noTransformerException;
		}

		@Override
		public <T> T transform(Object source, Class<T> as) throws X {
			if (source == null)
				return null;
			return _transform(source, as);
		}

		private <S, T> T _transform(S source, Class<T> targetType) throws X {
			ClassMap<ExBiFunction<?, Transformer<X>, ? extends T, ? extends X>> targetTransformers;
			targetTransformers = (ClassMap<ExBiFunction<?, Transformer<X>, ? extends T, ? extends X>>) theTypeTransformers.get(targetType,
				ClassMap.TypeMatch.SUB_TYPE);
			ExBiFunction<? super S, Transformer<X>, ? extends T, ? extends X> typeTransformer;
			Class<S> sourceType = (Class<S>) source.getClass();
			if (targetTransformers != null) {
				typeTransformer = (ExBiFunction<? super S, Transformer<X>, ? extends T, ? extends X>) targetTransformers.get(sourceType,
					ClassMap.TypeMatch.SUPER_TYPE);
			} else
				typeTransformer = null;
			if (typeTransformer == null) {
				if (theNoTransformerException != null)
					throw theNoTransformerException.apply(sourceType, targetType);
				else
					throw new IllegalArgumentException("No transformer for " + sourceType.getName() + "->" + targetType.getName());
			}
			return typeTransformer.apply(source, this);
		}

		/**
		 * @return A builder pre-populated with all of this transformer's capabilities, which can be used to create a new transformer with
		 *         additional capabilities
		 */
		public Builder<X> copy() {
			return new Builder<>(Builder.deepCopy(theTypeTransformers), theNoTransformerException);
		}
	}
}
