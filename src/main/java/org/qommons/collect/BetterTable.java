package org.qommons.collect;

import java.util.Collection;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.qommons.CausalLock;
import org.qommons.Identifiable;
import org.qommons.Lockable.CoreId;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;

/**
 * <p>
 * A BetterTable is a data structure like a map, where each value in the map is addressed by 2 keys: a row and a column. A table has a value
 * (which may be null) for each row/column tuple in the map, so there are R x C values in the map, where R is the number of rows and C is
 * the number of columns. In other words, a table is not sparsely-populated--every row has a value for every column.
 * </p>
 * <p>
 * A BetterTable doesn't have any methods to access or manipulate its values directly, but rather exposes views, which are {@link BetterSet}
 * or {@link BetterMap} extensions, that present the data for access or manipulation.
 * </p>
 * <p>
 * The {@link #rows()} method presents the table's data from the perspective of the set of rows. {@link #rows()} returns a {@link TableView
 * TableView&lt;R>}, which extends {@link BetterSet BetterSet&lt;R>}. The value of each element in the set is the row, but each
 * {@link CollectionElement element} of the set is a {@link TableEntry TableEntry&lt;R, C, V>}, which extends {@link BetterMap
 * BetterMap&lt;C, V>} and contains all the values for the row, keyed by the column.
 * </p>
 * <p>
 * <p>
 * The {@link #columns()} method does the inverse, presenting the table's data from the perspective of the columns. Each element of the set
 * contains all the values for the column, keyed by the row.
 * </p>
 * <p>
 * When the rows or columns are added, due to modification calls on the {@link #rows() rows} or {@link #columns() columns} views, the table
 * generates values to fill itself out such that there is a value for each row/column pair. When rows or columns are removed, values in the
 * table are removed so that it is again rectangular.
 * </p>
 * 
 * @param <R> The type of rows in the table
 * @param <C> The type of columns in the table
 * @param <V> The type of values in the table
 */
public interface BetterTable<R, C, V> extends Identifiable, CausalLock {
	/**
	 * An entry in a {@link BetterTable}. An entry represents a single column or row in the table. Its {@link #get()} method returns the row
	 * or column value. The {@link #keySet()} is the full set of columns or rows in the table (columns if this is a row entry, rows for a
	 * column entry), and each value is the value in the table for this entry's row/column for the column/row map key.
	 * 
	 * @param <R> The type of rows in the table (if this is an entry in a {@link BetterTable#rows() row} view) or columns (for a
	 *        {@link BetterTable#columns() column} view).
	 * @param <C> The type of columns in the table (if this is an entry in a {@link BetterTable#rows() row} view) or rows (for a
	 *        {@link BetterTable#columns() column} view).
	 * @param <V> The type of values in the table
	 */
	public interface TableEntry<R, C, V> extends BetterMap<C, V>, CollectionElement<R> {
		@Override
		TableView<C, R, V> keySet();

		@Override
		TableValueEntry<R, C, V> putEntry(C key, V value, boolean first);

		@Override
		TableValueEntry<R, C, V> putEntry(C key, V value, ElementId after, ElementId before, boolean first);

		@Override
		TableValueEntry<R, C, V> getEntry(C key);

		@Override
		TableValueEntry<R, C, V> getOrPutEntry(C key, Function<? super C, ? extends V> value, ElementId after, ElementId before,
			boolean first, Runnable preAdd, Runnable postAdd);

		@Override
		TableValueEntry<R, C, V> getEntryById(ElementId entryId);

		@Override
		TableValueEntry<R, C, V> getTerminalEntry(boolean first);

		@Override
		TableValueEntry<R, C, V> getAdjacentEntry(ElementId entryId, boolean next);

		@Override
		MutableTableValueEntry<R, C, V> mutableEntry(ElementId entryId);

		@Override
		TableValueEntry<R, C, V> computeEntryIfAbsent(C key, Function<? super C, ? extends V> value, boolean first);

		@Override
		default TableEntry<R, C, V> reverse() {
			return new ReversedTableEntry<>(this);
		}
	}

	/**
	 * A {@link TableEntry} containing methods for modification
	 * 
	 * @param <R> The type of rows in the table (if this is an entry in a {@link BetterTable#rows() row} view) or columns (for a
	 *        {@link BetterTable#columns() column} view).
	 * @param <C> The type of columns in the table (if this is an entry in a {@link BetterTable#rows() row} view) or rows (for a
	 *        {@link BetterTable#columns() column} view).
	 * @param <V> The type of values in the table
	 */
	public interface MutableTableEntry<R, C, V> extends TableEntry<R, C, V>, MutableCollectionElement<R> {
		@Override
		String canRemove();

		/**
		 * Removes this entry's row or column from the table
		 * 
		 * @param eachEntry An action to perform on each column/value pair (if this is a row entry) or row/value pair (for a column entry)
		 */
		void remove(BiConsumer<C, V> eachEntry);

		@Override
		default void remove() throws UnsupportedOperationException {
			remove(null);
		}

		@Override
		default MutableTableEntry<R, C, V> reverse() {
			return new ReversedMutableTableEntry<>(this);
		}
	}

	/**
	 * An entry for a specific column in a row {@link TableEntry}, or for a specific column in a column table entry. This entry identifies a
	 * specific value in the table
	 * 
	 * @param <R> The type of rows in the table (if this is an entry in a {@link BetterTable#rows() row} view) or columns (for a
	 *        {@link BetterTable#columns() column} view).
	 * @param <C> The type of columns in the table (if this is an entry in a {@link BetterTable#rows() row} view) or rows (for a
	 *        {@link BetterTable#columns() column} view).
	 * @param <V> The type of values in the table
	 */
	public interface TableValueEntry<R, C, V> extends MapEntryHandle<C, V> {
		/** @return The row in the table that this entry is for */
		R getRow();

		/** @return The element ID of the row in the table that this entry is for */
		ElementId getRowId();

		/** @return The column in the table that this entry is for */
		C getColumn();

		/** @return The element ID of the column in the table that this element is for */
		ElementId getColumnId();

		@Override
		default ElementId getElementId() {
			return getColumnId();
		}

		@Override
		default C getKey() {
			return getColumn();
		}

		@Override
		default TableValueEntry<R, C, V> reverse() {
			return new ReversedTableValueEntry<>(this);
		}
	}

	/**
	 * A {@link TableValueEntry} that exposes methods for modifying the value of the table entry
	 * 
	 * @param <R> The type of rows in the table (if this is an entry in a {@link BetterTable#rows() row} view) or columns (for a
	 *        {@link BetterTable#columns() column} view).
	 * @param <C> The type of columns in the table (if this is an entry in a {@link BetterTable#rows() row} view) or rows (for a
	 *        {@link BetterTable#columns() column} view).
	 * @param <V> The type of values in the table
	 */
	public interface MutableTableValueEntry<R, C, V> extends TableValueEntry<R, C, V>, MutableMapEntryHandle<C, V> {
		@Override
		default MutableTableValueEntry<R, C, V> reverse() {
			return new ReversedMutableTableValueEntry<>(this);
		}

		@Override
		default String canRemove() {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		default void remove() throws UnsupportedOperationException {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}
	}

	/**
	 * All the data in a {@link BetterTable} from a row or column perspective
	 * 
	 * @param <R> The type of rows in the table (if this is a {@link BetterTable#rows() row} view) or columns (for a
	 *        {@link BetterTable#columns() column} view).
	 * @param <C> The type of columns in the table (if this is a {@link BetterTable#rows() row} view) or rows (for a
	 *        {@link BetterTable#columns() column} view).
	 * @param <V> The type of values in the table
	 */
	interface TableView<R, C, V> extends BetterSet<R> {
		@Override
		TableEntry<R, C, V> getElement(R value, boolean first);

		@Override
		TableEntry<R, C, V> getElement(ElementId id);

		@Override
		TableEntry<R, C, V> getTerminalElement(boolean first);

		@Override
		TableEntry<R, C, V> getAdjacentElement(ElementId elementId, boolean next);

		@Override
		MutableTableEntry<R, C, V> mutableElement(ElementId id);

		@Override
		TableEntry<R, C, V> addElement(R value, ElementId after, ElementId before, boolean first)
			throws UnsupportedOperationException, IllegalArgumentException;

		@Override
		default TableEntry<R, C, V> addElement(R value, boolean first) throws UnsupportedOperationException, IllegalArgumentException {
			return addElement(value, null, null, first);
		}

		@Override
		TableEntry<R, C, V> getOrAdd(R value, ElementId after, ElementId before, boolean first, Runnable preAdd, Runnable postAdd);

		@Override
		default TableView<R, C, V> reverse() {
			return new ReversedTableView<>(this);
		}
	}

	/** @return A view of this table from the perspective of the table's rows */
	TableView<R, C, V> rows();

	/** @return A view of this table from the perspective of the table's columns */
	TableView<C, R, V> columns();

	/** @return A pivoted view of this table, whose {@link #rows()} are this table's {@link #columns()} and vice-versa */
	default BetterTable<C, R, V> pivot() {
		return new PivotedTable<>(this);
	}

	/**
	 * Implements {@link BetterTable#pivot()}
	 * 
	 * @param <R> The type of rows in this table (the type of columns in the source table)
	 * @param <C> The type of columns in this table (the type of rows in the source table)
	 * @param <V> The type of values in the table
	 */
	class PivotedTable<R, C, V> extends AbstractIdentifiable implements BetterTable<C, R, V> {
		private final BetterTable<R, C, V> theWrapped;

		public PivotedTable(BetterTable<R, C, V> wrapped) {
			theWrapped = wrapped;
		}

		@Override
		protected Object createIdentity() {
			return Identifiable.wrap(theWrapped.getIdentity(), "pivoted");
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theWrapped.lock(write, cause);
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return theWrapped.tryLock(write, cause);
		}

		@Override
		public CoreId getCoreId() {
			return theWrapped.getCoreId();
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			return theWrapped.getThreadConstraint();
		}

		@Override
		public Collection<Cause> getCurrentCauses() {
			return theWrapped.getCurrentCauses();
		}

		@Override
		public TableView<C, R, V> rows() {
			return theWrapped.columns();
		}

		@Override
		public TableView<R, C, V> columns() {
			return theWrapped.rows();
		}

		@Override
		public BetterTable<R, C, V> pivot() {
			return theWrapped;
		}
	}

	/**
	 * Default implementation of {@link BetterSet#reverse()} for a {@link TableView}
	 * 
	 * @param <R> The type of rows in the table (if this is a {@link BetterTable#rows() row} view) or columns (for a
	 *        {@link BetterTable#columns() column} view).
	 * @param <C> The type of columns in the table (if this is a {@link BetterTable#rows() row} view) or rows (for a
	 *        {@link BetterTable#columns() column} view).
	 * @param <V> The type of values in the table
	 */
	class ReversedTableView<R, C, V> extends BetterSet.ReversedBetterSet<R> implements TableView<R, C, V> {
		public ReversedTableView(TableView<R, C, V> wrap) {
			super(wrap);
		}

		@Override
		protected TableView<R, C, V> getWrapped() {
			return (TableView<R, C, V>) super.getWrapped();
		}

		@Override
		public TableEntry<R, C, V> getElement(R value, boolean first) {
			return (TableEntry<R, C, V>) super.getElement(value, first);
		}

		@Override
		public TableEntry<R, C, V> getElement(ElementId id) {
			return (TableEntry<R, C, V>) super.getElement(id);
		}

		@Override
		public TableEntry<R, C, V> getTerminalElement(boolean first) {
			return (TableEntry<R, C, V>) super.getTerminalElement(first);
		}

		@Override
		public TableEntry<R, C, V> getAdjacentElement(ElementId elementId, boolean next) {
			return (TableEntry<R, C, V>) super.getAdjacentElement(elementId, next);
		}

		@Override
		public MutableTableEntry<R, C, V> mutableElement(ElementId id) {
			return (MutableTableEntry<R, C, V>) super.mutableElement(id);
		}

		@Override
		public TableEntry<R, C, V> addElement(R value, ElementId after, ElementId before, boolean first)
			throws UnsupportedOperationException, IllegalArgumentException {
			return (TableEntry<R, C, V>) super.addElement(value, after, before, first);
		}

		@Override
		public TableEntry<R, C, V> addElement(R value, boolean first) throws UnsupportedOperationException, IllegalArgumentException {
			return addElement(value, null, null, first);
		}

		@Override
		public TableEntry<R, C, V> getOrAdd(R value, ElementId after, ElementId before, boolean first, Runnable preAdd, Runnable postAdd) {
			return (TableEntry<R, C, V>) super.getOrAdd(value, after, before, first, preAdd, postAdd);
		}

		@Override
		public TableView<R, C, V> reverse() {
			return getWrapped();
		}
	}

	/**
	 * Default implementation of {@link BetterMap#reverse()} for a {@link TableEntry}
	 * 
	 * @param <R> The type of rows in the table (if this is an entry in a {@link BetterTable#rows() row} view) or columns (for a
	 *        {@link BetterTable#columns() column} view).
	 * @param <C> The type of columns in the table (if this is an entry in a {@link BetterTable#rows() row} view) or rows (for a
	 *        {@link BetterTable#columns() column} view).
	 * @param <V> The type of values in the table
	 */
	class ReversedTableEntry<R, C, V> extends BetterMap.ReversedMap<C, V> implements TableEntry<R, C, V> {
		public ReversedTableEntry(TableEntry<R, C, V> wrapped) {
			super(wrapped);
		}

		@Override
		protected TableEntry<R, C, V> getWrapped() {
			return (TableEntry<R, C, V>) super.getWrapped();
		}

		@Override
		public ElementId getElementId() {
			return getWrapped().getElementId().reverse();
		}

		@Override
		public TableView<C, R, V> keySet() {
			return getWrapped().keySet().reverse();
		}

		@Override
		public R get() {
			return getWrapped().get();
		}

		@Override
		public TableValueEntry<R, C, V> putEntry(C key, V value, ElementId after, ElementId before, boolean first) {
			return (TableValueEntry<R, C, V>) super.putEntry(key, value, after, before, first);
		}

		@Override
		public TableValueEntry<R, C, V> getEntry(C key) {
			return (TableValueEntry<R, C, V>) super.getEntry(key);
		}

		@Override
		public TableValueEntry<R, C, V> getEntryById(ElementId entryId) {
			return (TableValueEntry<R, C, V>) super.getEntryById(entryId);
		}

		@Override
		public MutableTableValueEntry<R, C, V> mutableEntry(ElementId entryId) {
			return (MutableTableValueEntry<R, C, V>) super.mutableEntry(entryId);
		}

		@Override
		public TableValueEntry<R, C, V> putEntry(C key, V value, boolean first) {
			return (TableValueEntry<R, C, V>) super.putEntry(key, value, first);
		}

		@Override
		public TableValueEntry<R, C, V> getTerminalEntry(boolean first) {
			return (TableValueEntry<R, C, V>) super.getTerminalEntry(first);
		}

		@Override
		public TableValueEntry<R, C, V> getAdjacentEntry(ElementId entryId, boolean next) {
			return (TableValueEntry<R, C, V>) super.getAdjacentEntry(entryId, next);
		}

		@Override
		public TableValueEntry<R, C, V> computeEntryIfAbsent(C key, Function<? super C, ? extends V> value, boolean first) {
			return (TableValueEntry<R, C, V>) super.computeEntryIfAbsent(key, value, first);
		}

		@Override
		public TableValueEntry<R, C, V> getOrPutEntry(C key, Function<? super C, ? extends V> value, ElementId after, ElementId before,
			boolean first, Runnable preAdd, Runnable postAdd) {
			return (TableValueEntry<R, C, V>) super.getOrPutEntry(key, value, after, before, first, preAdd, postAdd);
		}
	}

	/**
	 * Default implementation of {@link BetterMap#reverse()} for a {@link MutableTableEntry}
	 * 
	 * @param <R> The type of rows in the table (if this is an entry in a {@link BetterTable#rows() row} view) or columns (for a
	 *        {@link BetterTable#columns() column} view).
	 * @param <C> The type of columns in the table (if this is an entry in a {@link BetterTable#rows() row} view) or rows (for a
	 *        {@link BetterTable#columns() column} view).
	 * @param <V> The type of values in the table
	 */
	class ReversedMutableTableEntry<R, C, V> extends ReversedTableEntry<R, C, V> implements MutableTableEntry<R, C, V> {
		public ReversedMutableTableEntry(MutableTableEntry<R, C, V> wrapped) {
			super(wrapped);
		}

		@Override
		protected MutableTableEntry<R, C, V> getWrapped() {
			return (MutableTableEntry<R, C, V>) super.getWrapped();
		}

		@Override
		public BetterCollection<R> getCollection() {
			return getWrapped().getCollection().reverse();
		}

		@Override
		public String isEnabled() {
			return getWrapped().isEnabled();
		}

		@Override
		public String isAcceptable(R value) {
			return getWrapped().isAcceptable(value);
		}

		@Override
		public void set(R value) throws UnsupportedOperationException, IllegalArgumentException {
			getWrapped().set(value);
		}

		@Override
		public String canRemove() {
			return getWrapped().canRemove();
		}

		@Override
		public void remove(BiConsumer<C, V> eachEntry) {
			getWrapped().remove(eachEntry);
		}

		@Override
		public TableValueEntry<R, C, V> getEntry(C key) {
			return super.getEntry(key);
		}

		@Override
		public TableValueEntry<R, C, V> getOrPutEntry(C key, Function<? super C, ? extends V> value, ElementId after, ElementId before,
			boolean first, Runnable preAdd, Runnable postAdd) {
			return super.getOrPutEntry(key, value, after, before, first, preAdd, postAdd);
		}
	}

	/**
	 * Default implementation of {@link MapEntryHandle#reverse()} for a {@link TableValueEntry}
	 * 
	 * @param <R> The type of rows in the table (if this is an entry in a {@link BetterTable#rows() row} view) or columns (for a
	 *        {@link BetterTable#columns() column} view).
	 * @param <C> The type of columns in the table (if this is an entry in a {@link BetterTable#rows() row} view) or rows (for a
	 *        {@link BetterTable#columns() column} view).
	 * @param <V> The type of values in the table
	 */
	class ReversedTableValueEntry<R, C, V> extends MapEntryHandle.ReversedMapEntryHandle<C, V> implements TableValueEntry<R, C, V> {
		public ReversedTableValueEntry(TableValueEntry<R, C, V> wrapped) {
			super(wrapped);
		}

		@Override
		protected TableValueEntry<R, C, V> getWrapped() {
			return (TableValueEntry<R, C, V>) super.getWrapped();
		}

		@Override
		public R getRow() {
			return getWrapped().getRow();
		}

		@Override
		public ElementId getRowId() {
			return getWrapped().getRowId();
		}

		@Override
		public C getColumn() {
			return getWrapped().getColumn();
		}

		@Override
		public ElementId getColumnId() {
			return getWrapped().getColumnId();
		}

		@Override
		public TableValueEntry<R, C, V> reverse() {
			return getWrapped();
		}
	}

	/**
	 * Default implementation of {@link MutableMapEntryHandle#reverse()} for a {@link MutableTableValueEntry}
	 * 
	 * @param <R> The type of rows in the table (if this is an entry in a {@link BetterTable#rows() row} view) or columns (for a
	 *        {@link BetterTable#columns() column} view).
	 * @param <C> The type of columns in the table (if this is an entry in a {@link BetterTable#rows() row} view) or rows (for a
	 *        {@link BetterTable#columns() column} view).
	 * @param <V> The type of values in the table
	 */

	class ReversedMutableTableValueEntry<R, C, V> extends ReversedTableValueEntry<R, C, V>
		implements MutableMapEntryHandle<C, V>, MutableTableValueEntry<R, C, V> {
		public ReversedMutableTableValueEntry(MutableTableValueEntry<R, C, V> wrapped) {
			super(wrapped);
		}

		@Override
		protected MutableTableValueEntry<R, C, V> getWrapped() {
			return (MutableTableValueEntry<R, C, V>) super.getWrapped();
		}

		@Override
		public BetterCollection<V> getCollection() {
			return getWrapped().getCollection().reverse();
		}

		@Override
		public String isEnabled() {
			return getWrapped().isEnabled();
		}

		@Override
		public String isAcceptable(V value) {
			return getWrapped().isAcceptable(value);
		}

		@Override
		public void set(V value) throws UnsupportedOperationException, IllegalArgumentException {
			getWrapped().set(value);
		}

		@Override
		public MutableTableValueEntry<R, C, V> reverse() {
			return getWrapped();
		}
	}
}
