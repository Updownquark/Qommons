package org.qommons.collect;

import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicReference;

/**
 * <p>
 * An atomic stack implementation. This class is not atomic in that it uses any atomic structures internally, but rather that it may be used
 * with such structures safely.
 * </p>
 * <p>
 * Instances of this class are immutable. The "modification" methods, {@link #push(Object) push} and {@link #pop() pop}, do not change the
 * instance they are called on, but rather return a new stack instance with the intended sequence.
 * </p>
 * <p>
 * Since the calling instance is unchanged, this class may be used in, e.g. an {@link AtomicReference} where multiple threads may be
 * attempting to add or remove values on the stack at once. The atomicity of this structure guarantees that e.g.
 * <code>{@link AtomicReference#updateAndGet(java.util.function.UnaryOperator) reference.updateAndGet}(stack->stack.{@link #push(Object) push(value)})</code>
 * will succeed in adding the value to the stack exactly once.
 * </p>
 * <p>
 * This class implements java's {@link Collection} interface ({@link Queue} actually), but all of the collection modification methods, e.g.
 * {@link #add(Object)} or {@link #remove(Object)} throw {@link UnsupportedOperationException}s.
 * </p>
 * <p>
 * The empty instance of this queue (a static singleton) has special behavior:
 * <ul>
 * <li>The {@link #top()} and {@link #bottom()} methods return null instead of throwing an exception. The standard {@link Queue} methods
 * behave as expected.</li>
 * <li>The {@link #pop()} method returns the empty instance There is no such thing as an empty instance of this
 * 
 * @param <T> The type of values in the stack
 */
public class AtomicStack<T> extends AbstractCollection<T> implements Queue<T> {
	static final AtomicStack<?> EMPTY = new AtomicStack<>(null, null);
	static final String UNMODIFIABLE = AtomicStack.class.getSimpleName()
		+ " is an immutable structure.  Use push(T) and pop() to obtain new modified stacks";

	/**
	 * @param <T> The type of the queue
	 * @return An empty queue
	 */
	@SuppressWarnings("unchecked")
	public static <T> AtomicStack<T> empty() {
		return (AtomicStack<T>) EMPTY;
	}

	private final AtomicStack<T> bottom;
	private final AtomicStack<T> parent;
	private final T value;
	private final int size;

	AtomicStack(AtomicStack<T> parent, T value) {
		if (parent == null) {
			bottom = this;
			this.parent = this;
			size = 0;
		} else {
			this.bottom = parent == EMPTY ? this : parent.bottom;
			this.parent = parent;
			size = parent.size + 1;
		}
		this.value = value;
	}

	/** @return The value on the top of this stack, or null if the stack is empty */
	public T top() {
		return value;
	}

	/** @return The first value added to this stack, or null if the stack is empty */
	public T bottom() {
		return bottom.value;
	}

	@Override
	public int size() {
		return size;
	}

	/**
	 * @param newValue The value to add to the stack
	 * @return A new stack containing all the values in this stack plus the given one at the {@link #top() top}
	 */
	public AtomicStack<T> push(T newValue) {
		return new AtomicStack<>(this, newValue);
	}

	/**
	 * @return A new stack containing all the values in this stack minus the most recent value added to it. If this stack is empty, it will
	 *         return itself.
	 */
	public AtomicStack<T> pop() {
		return parent;
	}

	@Override
	public Iterator<T> iterator() {
		return new IteratorView<>(this);
	}

	@Override
	public boolean offer(T e) {
		return false;
	}

	@Override
	public T remove() {
		throw new UnsupportedOperationException(UNMODIFIABLE);
	}

	@Override
	public T poll() {
		throw new UnsupportedOperationException(UNMODIFIABLE);
	}

	@Override
	public T element() {
		if (this == EMPTY)
			throw new NoSuchElementException("Empty queue");
		return value;
	}

	@Override
	public T peek() {
		return value;
	}

	static class IteratorView<T> implements Iterator<T> {
		private AtomicStack<T> theStack;

		IteratorView(AtomicStack<T> stack) {
			theStack = stack;
		}

		@Override
		public boolean hasNext() {
			return theStack != EMPTY;
		}

		@Override
		public T next() {
			AtomicStack<T> myStack = theStack;
			if (myStack == EMPTY)
				throw new NoSuchElementException();
			theStack = myStack.pop();
			return myStack.top();
		}
	}
}
