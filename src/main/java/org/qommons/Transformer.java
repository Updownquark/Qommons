package org.qommons;

import java.util.function.BiFunction;

import org.qommons.ex.ExBiFunction;

public interface Transformer<X extends Throwable> {
	<T> T transform(Object source, Class<T> as) throws X;

	static <X extends Throwable> Builder<X> build() {
		return new Builder<>();
	}

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

		public <S, T> Builder<X> with(Class<S> sourceType, Class<T> targetType,
			ExBiFunction<? super S, Transformer<X>, ? extends T, ? extends X> transformer) {
			ClassMap<ExBiFunction<?, Transformer<X>, ? extends T, ? extends X>> targetTransformers;
			targetTransformers = (ClassMap<ExBiFunction<?, Transformer<X>, ? extends T, ? extends X>>) theTypeTransformers
				.computeIfAbsent(targetType, () -> new ClassMap<>());
			targetTransformers.put(sourceType, transformer);
			return this;
		}

		public Builder<X> withNoTransformerException(BiFunction<Class<?>, Class<?>, X> noTransformerException) {
			theNoTransformerException = noTransformerException;
			return this;
		}

		public Transformer<X> build() {
			return new CompositeTransformer<>(deepCopy(theTypeTransformers), theNoTransformerException);
		}

		static <T> ClassMap<ClassMap<? extends T>> deepCopy(ClassMap<ClassMap<? extends T>> classMap) {
			ClassMap<ClassMap<? extends T>> copy = new ClassMap<>();
			for (BiTuple<Class<?>, ClassMap<? extends T>> entry : classMap.getAllEntries())
				copy.put(entry.getValue1(), entry.getValue2().copy());
			return copy;
		}
	}

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

		public Builder<X> copy() {
			return new Builder<>(Builder.deepCopy(theTypeTransformers), theNoTransformerException);
		}
	}
}
