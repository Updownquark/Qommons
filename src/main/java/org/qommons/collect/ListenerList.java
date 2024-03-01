package org.qommons.collect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * <p>
 * A very simple, fast, flexible, thread-safe list originally intended for listeners registered to an event source. It has special features
 * that lend itself to that use extremely well, but has many other uses as well.
 * </p>
 * <p>
 * This class is basically a linked queue, but operates differently than a java {@link Collection}:
 * <ul>
 * <li>When a value is {@link #add(Object, boolean) added} to a ListenerList, the return value is a reference to the element in the list
 * where the value was added, similar to a {@link BetterCollection}. This element can be used to remove the element from the collection or
 * replace it with a different value. This element is the only way of modifying or removing the element in the collection, other than
 * {@link #poll(long) polling} or {@link #clear() clearing}.</li>
 * 
 * <li>Instead of returning an {@link Iterator} that can be used outside this list's control, this class provides a
 * {@link #forEach(Consumer) forEach} method that performs an action on each value in the collection. The tradeoff for the loss of control
 * is a safety check against re-entrancy, as described below.</li>
 * <li>Values may be added or removed from this list at any time, in any state, including from within a call to {@link #forEach(Consumer)}
 * or during a forEach invocation on another thread. Actions on a value may remove the value or any other value, add other values to the
 * list at any position, or perform any other operation on the list. The {@link #add(Object, boolean)} method provides a parameter to
 * control whether the new value participates in the current iteration (on the current thread).</li>
 * </ul>
 * </p>
 * 
 * <p>
 * This class also provides the ability to perform an action when this class goes into or falls out of use, i.e., when a value is added to
 * an empty list, or the last value is removed from a list. See {@link Builder#withInUse(InUseListener)}.
 * </p>
 * 
 * <p>
 * This class obtains locks (if configured via {@link Builder#withSync(boolean)}) when values are added or removed, or when the list is
 * {@link #clear() cleared}. It does not obtain any locks for {@link #forEach(Consumer) iteration}, size checks, or non-clear {@link #dump()
 * dumping}.
 * </p>
 * 
 * <p>
 * This class is thread-safe (if so- {@link Builder#withSync(boolean) configured}), but if values are added or removed by other threads
 * during {@link #forEach(Consumer) iteration}, the added or removed values may or may not be acted upon by that iteration, regardless of
 * the <code>skipCurrent</code> argument to {@link #add(Object, boolean)}.
 * </p>
 * 
 * <p>
 * In general, this list does not permit re-entrancy, the invocation of {@link #forEach(Consumer)} by an action during an invocation of
 * {@link #forEach(Consumer)} on the same thread. Many errors in code using this list can arise from re-entrant invocation of this method.
 * Re-entrancy can be enabled with {@link Builder#allowReentrant()} if you are sure your code can handle it.
 * </p>
 * 
 * @param <E> The type of value that this list can store
 */
public class ListenerList<E> {
	private static boolean SWALLOW_EXCEPTIONS = true;

	/**
	 * @return Whether all instances of this class (and others that may use this flag) will swallow {@link RuntimeException} thrown by an
	 *         iteration action (printing their stack traces first) in order that further iteration remains unaffected
	 */
	public static boolean isSwallowingExceptions() {
		return SWALLOW_EXCEPTIONS;
	}

	/**
	 * @param swallowExceptions Whether all instances of this class (and others that use this flag) should swallow {@link RuntimeException}
	 *        thrown by iteration actions (printing their stack traces first) in order that further iteration remains unaffected
	 */
	public static void setSwallowExceptions(boolean swallowExceptions) {
		SWALLOW_EXCEPTIONS = swallowExceptions;
	}

	/**
	 * An element containing a value in a {@link ListenerList}
	 * 
	 * @param <E> The type of the value that this node holds
	 */
	public interface Element<E> extends Runnable {
		/** @return Whether this element is still present in the list */
		boolean isPresent();

		/**
		 * Removes this element from the list
		 * 
		 * @return True if the element was removed as a result of this call, false if it had been removed already
		 */
		boolean remove();

		/** @return The value that this element contains in the list */
		E get();

		/** @param newValue The new value for this element */
		void set(E newValue);

		/** Calls {@link #remove()} */
		@Override
		default void run() {
			remove();
		}
	}

	/** A listener to be invoked when a value is added to an empty list or a solitary value is removed from a {@link ListenerList} */
	public interface InUseListener {
		/** @param inUse Whether list just went in to (true) or out of (false) use */
		void inUseChanged(boolean inUse);
	}

	/** Builds a ListenerList with customizable options */
	public static class Builder {
		private String theReentrancyError;
		private boolean isForEachSafe;
		private InUseListener theInUseListener;
		private boolean fastSize;
		private boolean isSynchronized;

		Builder() {
			// Initialize with defaults, which mostly lean toward safety and functionality, away from performance
			theReentrancyError = "Reentrancy not allowed";
			isForEachSafe = true;
			fastSize = true;
			isSynchronized = true;
		}

		/**
		 * Configures the list to be built to allow {@link #forEachSafe(boolean) forEach} actions to invoke
		 * {@link ListenerList#forEach(Consumer)}, directly or indirectly. Using this will also improve the performance of
		 * {@link ListenerList#forEach(Consumer)} by avoiding the need to keep track of call status for each thread.
		 * 
		 * @return This builder
		 */
		public Builder allowReentrant() {
			return reentrancyError(null);
		}

		/**
		 * @param error The message in the exception that will be thrown if {@link ListenerList#forEach(Consumer)} is invoked by an action
		 *        in a forEach invocation, directly or indirectly. If null, this method is the same as {@link #allowReentrant()}.
		 * @return This builder
		 * @see #allowReentrant()
		 */
		public Builder reentrancyError(String error) {
			theReentrancyError = error;
			return this;
		}

		/**
		 * 
		 * @param safe Whether to ensure thread safety for the reentrancy-prevention and <code>skipCurrent</code> (for
		 *        {@link ListenerList#add(Object, boolean)}) features of {@link ListenerList#forEach(Consumer)}. If false, the list will be
		 *        lighter and a bit more performant, but the above-mentioned features will be error-prone if
		 *        {@link ListenerList#forEach(Consumer)} is ever called by multiple threads concurrently.
		 * @return This builder
		 */
		public Builder forEachSafe(boolean safe) {
			isForEachSafe = safe;
			return this;
		}

		/**
		 * @param listener The listener to be notified when a value is added to the empty list or a solitary value removed from it.
		 * @return This builder
		 */
		public Builder withInUse(InUseListener listener) {
			theInUseListener = listener;
			return this;
		}

		/**
		 * @param fast Whether this list should maintain an {@link AtomicInteger} with its current size, so that {@link ListenerList#size()}
		 *        is constant time. If false, this list is a bit lighter, but the {@link ListenerList#size()} operation is linear time. This
		 *        option is ignored (effectively true) if the {@link #withInUse(InUseListener) in-use listener} is set.
		 * @return This builder
		 */
		public Builder withFastSize(boolean fast) {
			fastSize = fast;
			return this;
		}

		/**
		 * @param sync Whether the list should be synchronized to prevent loss of data
		 * @return This builder
		 */
		public Builder withSync(boolean sync) {
			isSynchronized = sync;
			return this;
		}

		/**
		 * <p>
		 * Optimizes the list for performance, discarding all thread safety mechanisms.
		 * </p>
		 * <p>
		 * A list will function and throw no errors with this option, no matter how it is used, but if it is used by multiple threads use
		 * the list, errors may occur, such as:
		 * <ul>
		 * <li>True parameters passed to {@link ListenerList#add(Object, boolean)} may not always be respected</li>
		 * <li>{@link ListenerList#add(Object, boolean) Additions} and {@link Element#remove() removals} on the list may not be effective,
		 * or may cause other elements to be removed from the list (but {@link Element#isPresent()} may not report this).</li>
		 * </ul>
		 * <p>
		 * Other, unanticipated errors, may occur as well.
		 * </p>
		 * 
		 * @return This builder
		 * @see #withSync(boolean)
		 * @see #forEachSafe(boolean)
		 * @see #allowReentrant()
		 */
		public Builder unsafe() {
			return allowReentrant().forEachSafe(false).withFastSize(false).withSync(false);
		}

		/**
		 * @param <E> The type of the list to build
		 * @return The new list
		 */
		public <E> ListenerList<E> build() {
			return new ListenerList<>(theReentrancyError, isForEachSafe, theInUseListener, fastSize, isSynchronized);
		}
	}

	/**
	 * @return A builder to build a {@link ListenerList}. The builder is initialized with options that lean toward safety and functionality.
	 *         Use the builder's options if performance is more of a priority.
	 */
	public static Builder build() {
		return new Builder();
	}

	private static class Iterating {
		int iterId = -1;
	}

	private class Node implements Element<E> {
		E theValue;
		volatile Node next;
		volatile Node previous;

		Node(E value) {
			theValue = value;
		}

		boolean isInAddFiringRound(int firing) {
			return false;
		}

		@Override
		public boolean isPresent() {
			return previous.next == this;
		}

		@Override
		public E get() {
			return theValue;
		}

		@Override
		public void set(E newValue) {
			theValue = newValue;
		}

		@Override
		public void run() {
			remove();
		}

		@Override
		public boolean remove() {
			if (previous.next != this)
				return false;

			return removeListener(this);
		}
	}

	private class SkipOneNode extends Node {
		private int skipOne;

		public SkipOneNode(E value, int skipOne) {
			super(value);
			this.skipOne = skipOne;
		}

		@Override
		boolean isInAddFiringRound(int firing) {
			if (firing == skipOne) {
				skipOne = -1;
				return true;
			}
			return false;
		}
	}

	private class RunLastNode extends SkipOneNode {
		RunLastNode(E value, int skipOne) {
			super(value, skipOne);
		}
	}

	private final ThreadLocal<Iterating> isFiringSafe;
	private final Node theTerminal;
	private final String theReentrancyError;
	private final InUseListener theInUseListener;
	private final boolean isSynchronized;

	private final AtomicInteger theSize;
	private volatile int unsafeIterId = -1;
	private final AtomicInteger theIterIdGen;

	ListenerList(String reentrancyError, boolean safeForEach, InUseListener inUseListener, boolean fastSize,
		boolean sync) {
		// The code is much simpler and safer if all the real elements can know that there's a non-null node before and after them.
		// The first node's previous pointer and the last node's next pointer would always be null,
		// so there's no need to have different nodes for first and last.
		theTerminal = new Node(null);
		theTerminal.next = theTerminal.previous = theTerminal;

		isFiringSafe = safeForEach ? ThreadLocal.withInitial(Iterating::new) : null;
		theReentrancyError = reentrancyError;
		theInUseListener = inUseListener;
		if (inUseListener != null)
			fastSize = true;
		theSize = fastSize ? new AtomicInteger() : null;
		isSynchronized = sync;
		theIterIdGen = new AtomicInteger();
	}

	/**
	 * @param value The value to add
	 * @param skipCurrent Whether to skip actions on this value during the current {@link #forEach(Consumer) forEach} iteration if this
	 *        addition is a result of the action being invoked from a {@link #forEach(Consumer)} call
	 * @return The added element
	 */
	public Element<E> add(E value, boolean skipCurrent) {
		int firing;
		if (skipCurrent) {
			if (isFiringSafe != null)
				firing = isFiringSafe.get().iterId; // Thread safe because isFiring is a ThreadLocal
			else
				firing = unsafeIterId;
		} else
			firing = -1;
		Node newNode = firing == -1 ? new Node(value) : new SkipOneNode(value, firing);
		addNode(newNode, true);
		return newNode;
	}

	/**
	 * Adds a value to this list, to be acted upon after all other values (other than those added later with this method). This terminal
	 * preference will be respected by {@link #forEach(Consumer)}, {@link #poll(long)}, {@link #dump()}, {@link #dumpAndClear(Consumer)},
	 * and other methods that operate on values in the list
	 * 
	 * @param value The value to add
	 * @param skipCurrent Whether to skip actions on this value during the current {@link #forEach(Consumer) forEach} iteration if this
	 *        addition is a result of the action being invoked from a {@link #forEach(Consumer)} call
	 * @return The added element
	 */
	public Element<E> addLast(E value, boolean skipCurrent) {
		int firing;
		if (skipCurrent) {
			if (isFiringSafe != null)
				firing = isFiringSafe.get().iterId; // Thread safe because isFiring is a ThreadLocal
			else
				firing = unsafeIterId;
		} else
			firing = -1;
		RunLastNode node = new RunLastNode(value, firing);
		addNode(node, true);
		return node;
	}

	/**
	 * Adds a value to the beginning of this list
	 * 
	 * @param value The value to add
	 * @return The added element
	 */
	public Element<E> addFirst(E value) {
		Node newNode = new Node(value);
		addNode(newNode, false);
		return newNode;
	}

	/**
	 * @param value The value to add
	 * @param skipCurrent Whether to skip actions on this value during the current {@link #forEach(Consumer) forEach} iteration if this
	 *        addition is a result of the action being invoked from a {@link #forEach(Consumer)} call
	 * @return True if the list was empty prior to this add
	 */
	public boolean addWasFirst(E value, boolean skipCurrent) {
		Node newNode = new Node(value);
		return addNode(newNode, false);
	}

	private boolean addNode(Node newNode, boolean last) {
		if (last)
			newNode.next = theTerminal;// We know we'll be adding this node as the last node (excluding the terminal)
		else
			newNode.previous = theTerminal;
		// The next part affects the list's state, so only one at a time
		if (isSynchronized) {
			synchronized (theTerminal) {
				return _add(newNode, last);
			}
		} else
			return _add(newNode, last);
	}

	private boolean _add(Node newNode, boolean last) {
		boolean newInUse = theSize != null && theSize.getAndIncrement() == 0;
		if (last) {
			Node oldLast = theTerminal.previous;
			newNode.previous = oldLast;
			theTerminal.previous = newNode;
			oldLast.next = newNode;
		} else {
			Node oldFirst = theTerminal.next;
			newNode.next = oldFirst;
			theTerminal.next = newNode;
			oldFirst.previous = newNode;
		}
		if (newInUse && theInUseListener != null)
			theInUseListener.inUseChanged(true);
		return newInUse;
	}

	boolean removeListener(Node node) {
		if (isSynchronized) {
			synchronized (theTerminal) {
				return _removeListener(node);
			}
		} else
			return _removeListener(node);
	}

	private boolean _removeListener(Node node) {
		if (node.previous.next != node)
			return false;
		Node prev = node.previous;
		Node next = node.next;
		prev.next = next;
		next.previous = prev;
		boolean newNotInUse = theSize != null && theSize.decrementAndGet() == 0;
		if (newNotInUse && theInUseListener != null)
			theInUseListener.inUseChanged(false);
		return true;
	}

	/**
	 * Removes and returns the first element (the head) of this list, if the list is not empty
	 * 
	 * @param waitTime If &gt;0, how long to wait in this method until a value is available
	 * @return The removed node, or null if the list was empty all through the specified wait time
	 */
	public Element<E> poll(long waitTime) {
		return poll(true, waitTime);
	}

	/**
	 * Removes and returns the first or last element of this list, if the list is not empty
	 * 
	 * @param first Whether to get the first or last element in the list
	 * @param waitTime If &gt;0, how long to wait in this method until a value is available
	 * @return The removed node, or null if the list was empty all through the specified wait time
	 */
	public Element<E> poll(boolean first, long waitTime) {
		boolean wait = waitTime > 0;
		boolean timeChecking = wait && waitTime < Long.MAX_VALUE;
		long start = timeChecking ? System.currentTimeMillis() : 0;
		int waited = 0;
		RunLastNode runLast = null;
		Node remove = first ? theTerminal.next : theTerminal.previous;
		do {
			if (remove == theTerminal) {//
			} else if (first && remove instanceof ListenerList.RunLastNode) {
				if (runLast == null)
					runLast = (RunLastNode) remove;
				remove = remove.next;
				continue;
			} else if (!remove.remove()) {
				remove = first ? theTerminal.next : theTerminal.previous;
				continue; // Removed elsewhere--try again
			} else
				return remove;

			if (runLast != null) {
				if (runLast.remove())
					return runLast;
				runLast = null;
				continue;
			}
			if (wait) {
				try {
					Thread.sleep(0, 100_000);
				} catch (InterruptedException e) {
				}
				if (timeChecking && ++waited == 10) {
					waited = 0;
					if (System.currentTimeMillis() > start + waitTime)
						return null;
				}
				remove = first ? theTerminal.next : theTerminal.previous;
			}
		} while (wait);
		return null;
	}
	
	/**
	 * Removes and returns the first value (the head) in this list, if the list is not empty
	 * 
	 * @param waitTime If &gt;0, how long to wait in this method until a value is available
	 * @return The removed value, or null if the list was empty all through the specified wait time
	 */
	public E pollValue(long waitTime) {
		return pollValue(true, waitTime);
	}

	/**
	 * Removes and returns the first or last value in this list, if the list is not empty
	 * 
	 * @param first Whether to get the first or last value in the list
	 * @param waitTime If &gt;0, how long to wait in this method until a value is available
	 * @return The removed value, or null if the list was empty all through the specified wait time
	 */
	public E pollValue(boolean first, long waitTime) {
		Element<E> polled = poll(first, waitTime);
		return polled == null ? null : polled.get();
	}

	/**
	 * Returns the first element (the head) in this list (without removing it) if the list is not empty
	 * 
	 * @return The first element in this list, or null if the list is empty
	 */
	public Element<E> peekFirst(){
		RunLastNode runLast = null;
		Node node=theTerminal.next;
		while (node != theTerminal) {
			if (node instanceof ListenerList.RunLastNode) {
				if (runLast == null)
					runLast = (ListenerList<E>.RunLastNode) node;
				node = node.next;
			} else
				break;
		}
		if(node==theTerminal)
			return runLast;
		else
			return node;
	}

	/**
	 * Applies a specified action to each value in this list
	 * 
	 * @param action The action to perform on each value in this list
	 */
	public void forEach(Consumer<E> action) {
		Node node = theTerminal.next;
		int iterId = theIterIdGen.getAndUpdate(ListenerList::incIterId);
		int reentrant;
		if (isFiringSafe != null) {
			reentrant = isFiringSafe.get().iterId;
			if (reentrant != -1 && theReentrancyError != null)
				throw new ReentrantNotificationException(theReentrancyError);
			isFiringSafe.get().iterId = iterId;
		} else {
			reentrant = unsafeIterId;
			if (reentrant != -1 && theReentrancyError != null)
				throw new ReentrantNotificationException(theReentrancyError);
			unsafeIterId = iterId;
		}
		List<RunLastNode> runLast = null;
		try {
			while (node != theTerminal) {
				if (node.isInAddFiringRound(iterId)) { // Don't execute the same round it was added, if so specified
				} else if (node instanceof ListenerList.RunLastNode) {
					if (runLast == null)
						runLast = new ArrayList<>();
					runLast.add((RunLastNode) node);
				} else {
					try {
						action.accept(node.theValue);
					} catch (ReentrantNotificationException | AssertionError e) {
						throw e;
					} catch (RuntimeException e) {
						if (SWALLOW_EXCEPTIONS) {
							// If the action throws an exception, we can't have that gumming up the works
							// If they want better handling, they can try/catch their own code
							e.printStackTrace();
						} else
							throw e;
					}
				}

				/* Now we need to get the next value in the list.
				 * A problem may occur, however, if the list is modified as a result of an action.
				 * If, for example, an action removes the value it is operating on and and also the next value in the list,
				 * the current node will still be pointing to the next value, which has been removed.
				 * We have to find the most recent element that we already called for this action that is still in the list
				 * and use its next node.
				 */
				while (!node.isPresent() && node != theTerminal)
					node = node.previous;
				node = node.next;
			}
			if (runLast != null) {
				for (RunLastNode rln : runLast) {
					if (rln.isPresent()) {
						try {
							action.accept(rln.theValue);
						} catch (ReentrantNotificationException | AssertionError e) {
							throw e;
						} catch (RuntimeException e) {
							if (SWALLOW_EXCEPTIONS) {
								// If the action throws an exception, we can't have that gumming up the works
								// If they want better handling, they can try/catch their own code
								e.printStackTrace();
							} else
								throw e;
						}
					}
				}
			}
		} finally {
			if (isFiringSafe != null) {
				isFiringSafe.get().iterId = reentrant;
			} else {
				unsafeIterId = reentrant;
			}
		}
	}

	static int incIterId(int prevId) {
		int nextId = prevId + 1;
		if (nextId == -1)
			nextId++;
		return nextId;
	}

	/** Removes all values from this list */
	public void clear() {
		synchronized (theTerminal) {
			Node node = theTerminal;
			Node nextNode = node.next;
			boolean wasInUse = nextNode != theTerminal;
			while (nextNode != theTerminal) {
				node.previous = node;
				node.next = theTerminal;
				node = nextNode;
				nextNode = node.next;
			}
			if (theSize != null)
				theSize.set(0);
			if (wasInUse && theInUseListener != null)
				theInUseListener.inUseChanged(false);
		}
	}

	/** @return Whether this list has no values in it */
	public boolean isEmpty() {
		return theTerminal.next == theTerminal;
	}

	/** @return The number of values currently in this list */
	public int size() {
		if (theSize != null)
			return theSize.get();
		Node node = theTerminal.next;
		int sz = 0;
		while (node != theTerminal) {
			sz++;
			node = node.next;
		}
		return sz;
	}

	/**
	 * @return Whether {@link #forEach(Consumer)} is currently being called. If this list uses {@link Builder#forEachSafe(boolean) safe
	 *         iteration}, this will only return true in the case that iteration is happening on the current thread. Otherwise, it will
	 *         return true during iteration on any thread.
	 */
	public boolean isFiring() {
		int reentrant;
		if (isFiringSafe != null)
			reentrant = isFiringSafe.get().iterId;
		else
			reentrant = unsafeIterId;
		return reentrant != -1;
	}

	/**
	 * Adds all of this list's content into a new list (without removing it from this one)
	 * 
	 * @return The new list containing all of the values currently in this list
	 */
	public List<E> dump() {
		return dumpInto(new ArrayList<>(theSize == null ? 10 : theSize.get() + 3));
	}

	/**
	 * Adds all of this list's content into the given collection (without removing it from this one)
	 * 
	 * @param <C> The type of the collection
	 * @param collection The collection to put all of this list's values into
	 * @return The collection
	 */
	public <C extends Collection<? super E>> C dumpInto(C collection) {
		// Don't use forEach, since this might be useful for debugging during event firing and reentrancy may not be allowed
		Node node = theTerminal.next;
		List<E> lastNodes = null;
		while (node != theTerminal) {
			if (node instanceof ListenerList.RunLastNode) {
				if (lastNodes == null)
					lastNodes = new ArrayList<>();
				lastNodes.add(node.theValue);
			} else
				collection.add(node.theValue);
			node = node.next;
		}
		if (lastNodes != null)
			collection.addAll(lastNodes);
		return collection;
	}

	/**
	 * Clears this list, providing each value to a consumer before it is removed
	 * 
	 * @param consumer The consumer to accept each value in this list
	 * @return The number of items that were found in the list
	 */
	public int dumpAndClear(Consumer<E> consumer) {
		Node node = theTerminal.next;
		List<E> lastNodes = null;
		int count = 0;
		while (node != theTerminal) {
			count++;
			if (node instanceof ListenerList.RunLastNode) {
				if (lastNodes == null)
					lastNodes = new ArrayList<>();
				lastNodes.add(node.theValue);
			} else if (consumer != null)
				consumer.accept(node.theValue);
			node.remove();
			node = node.next;
		}
		if (lastNodes != null)
			lastNodes.forEach(consumer);
		return count;
	}

	/**
	 * Removes this list's content and returns it in a new list. If items are added to this list externally during this call, the list may
	 * contain the added items after this call returns.
	 * 
	 * @return The list of content that was previously in this list
	 */
	public List<E> dumpAndClear() {
		if (isEmpty())
			return Collections.emptyList();
		int size;
		List<E> list;
		if (theSize != null) {
			size = theSize.get();
			list = new ArrayList<>(size);
		} else {
			size = Integer.MAX_VALUE;
			list = new ArrayList<>();
		}
		int i = 0;
		Node node = theTerminal.next;
		List<E> lastNodes = null;
		while (i < size && node != theTerminal) {
			if (node instanceof ListenerList.RunLastNode) {
				if (lastNodes == null)
					lastNodes = new ArrayList<>();
				lastNodes.add(node.theValue);
			} else
				list.add(node.theValue);
			node.remove();
			node = node.next;
			i++;
		}
		if (lastNodes != null)
			list.addAll(lastNodes);
		if (node != theTerminal)
			theTerminal.next = node;
		return list;
	}

	@Override
	public String toString() {
		StringBuilder str = new StringBuilder();
		str.append('[');
		boolean first = true;
		StringBuilder lastStr = null;
		// Don't use forEach, since toString might be useful for debugging during event firing and reentrancy may not be allowed
		Node node = theTerminal.next;
		while (node != theTerminal) {
			StringBuilder s;
			if (node instanceof ListenerList.RunLastNode) {
				if (lastStr == null)
					lastStr = new StringBuilder();
				else
					lastStr.append(", ");
				s = lastStr;
			} else {
				if (first)
					first = false;
				else
					str.append(", ");
				s = str;
			}
			s.append(node.theValue);
			node = node.next;
		}
		if (lastStr != null) {
			if (!first)
				str.append(", ");
			str.append(lastStr);
		}
		str.append(']');
		return str.toString();
	}
}
