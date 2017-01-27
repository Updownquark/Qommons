package org.qommons.collect;

import java.lang.reflect.Array;

public interface Action<T> {
	/**
	 * @param cause An object that may have caused the action (e.g. a user event)
	 * @return The result of the action
	 * @throws IllegalStateException If this action is not enabled
	 */
	T act(Object cause) throws IllegalStateException;

	/** @return An observable whose value reports null if this value can be set directly, or a string describing why it cannot */
	Value<String> isEnabled();

	/**
	 * @param <T> The type of the action
	 * @param wrapper An observable value that supplies actions
	 * @return An action based on the content of the wrapper
	 */
	static <T> Action<T> flatten(Value<? extends Action<? extends T>> wrapper) {
		return new FlattenedAction<>(wrapper);
	}

	/**
	 * @param <T> The type of the acton's value
	 * @param type The run-time type of the action's value
	 * @param value The value to be returned each time by the action
	 * @return An action that does nothing but return the given value
	 */
	static <T> Action<T> nullAction(T value) {
		return new Action<T>() {
			@Override
			public T act(Object cause) throws IllegalStateException {
				return value;
			}

			@Override
			public Value<String> isEnabled() {
				return Value.constant(null);
			}
		};
	}

	/**
	 * @param <T> The type of the action
	 * @param message The disabled message for the action
	 * @return An action that is always disabled with the given message
	 */
	static <T> Action<T> disabled(String message) {
		return new Action<T>() {
			@Override
			public T act(Object cause) throws IllegalStateException {
				throw new IllegalStateException(message);
			}

			@Override
			public Value<String> isEnabled() {
				return Value.constant(message);
			}
		};
	}

	/**
	 * Combines several actions into one
	 *
	 * @param <T> The type of the actions
	 * @param actions The actions to combine
	 * @return A single action that invokes the given actions and returns their values as an array
	 */
	static <T> Action<T[]> and(Action<? extends T>... actions) {
		return and(QommonsList.constant(java.util.Arrays.asList(actions)));
	}

	/**
	 * Combines several actions into one
	 *
	 * @param <T> The type of the actions
	 * @param actions The actions to combine
	 * @return A single action that invokes the given actions and returns their values as an array
	 */
	static <T> Action<T[]> and(QommonsList<? extends Action<? extends T>> actions) {
		return new AndAction<>(actions);
	}

	/**
	 * An observable action whose methods reflect those of the content of an observable value, or a disabled action when the content is null
	 *
	 * @param <T> The type of value the action produces
	 */
	class FlattenedAction<T> implements Action<T> {
		private final Value<? extends Action<? extends T>> theWrapper;

		protected FlattenedAction(Value<? extends Action<? extends T>> wrapper) {
			theWrapper = wrapper;
		}

		@Override
		public T act(Object cause) throws IllegalStateException {
			Action<? extends T> wrapped = theWrapper.get();
			if (wrapped != null)
				return wrapped.act(cause);
			else
				throw new IllegalStateException("This wrapper (" + theWrapper + ") is empty");
		}

		@Override
		public Value<String> isEnabled() {
			return Value.flatten(theWrapper.mapV(action -> action.isEnabled()), () -> "This wrapper (" + theWrapper + ") is empty");
		}
	}

	/**
	 * Implements {@link Action#and(QommonsList)}
	 *
	 * @param <T> The type of the actions
	 */
	class AndAction<T> implements Action<T[]> {
		private final QommonsList<? extends Action<? extends T>> theActions;

		protected AndAction(QommonsList<? extends Action<? extends T>> actions) {
			theActions = actions;
		}

		@Override
		public T[] act(Object cause) throws IllegalStateException {
			Action<? extends T>[] actions = theActions.toArray();
			for (Action<? extends T> action : actions) {
				String msg = action.isEnabled().get();
				if (msg != null)
					throw new IllegalStateException(msg);
			}
			T[] values = (T[]) Array.newInstance(theArrayType.getComponentType().getRawType(), actions.length);
			for (int i = 0; i < values.length; i++)
				values[i] = actions[i].act(cause);
			return values;
		}

		@Override
		public Value<String> isEnabled() {
			return QommonsList.flattenValues(theActions.map(action -> action.isEnabled())).findFirst(e -> e != null);
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder();
			boolean first = true;
			for (Action<?> action : theActions) {
				if (!first)
					str.append(';');
				first = false;
				str.append(action);
			}
			return str.toString();
		}
	}
}
