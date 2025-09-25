package org.qommons.collect;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.qommons.Identifiable;
import org.qommons.Identifiable.AbstractIdentifiable;
import org.qommons.Lockable.CoreId;
import org.qommons.QommonsUtils;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.tree.BetterTreeList;
import org.qommons.tree.MutableBinaryTreeNode;

/**
 * Default {@link BetterTable} implementation
 * 
 * @param <R> The type of rows in the table
 * @param <C> The type of columns in the table
 * @param <V> The type of values in the table
 */
public class DefaultTable<R, C, V> extends AbstractIdentifiable implements BetterTable<R, C, V> {
	private final BiFunction<? super R, ? super C, ? extends V> theFill;
	private final BetterMap<R, MutableBinaryTreeNode<List<V>>> theRowMap;
	private final BetterMap<C, ElementId> theColumnMap;
	private final BetterTreeList<List<V>> theRows;
	private final BetterTreeList<ElementId> theColumns;
	private final CollectionLockingStrategy theLock;

	DefaultTable(BiFunction<? super R, ? super C, ? extends V> fill, String description,
		Function<Object, CollectionLockingStrategy> locking) {
		theFill = fill;
		theRowMap = BetterHashMap.build().build();
		theColumnMap = BetterHashMap.build().build();
		theRows = BetterTreeList.<List<V>> build().build();
		theColumns = BetterTreeList.<ElementId> build().build();
		theLock = locking.apply(this);
		initIdentity(Identifiable.baseId(description, this));
	}

	@Override
	public CoreId getCoreId() {
		return theLock.getCoreId();
	}

	@Override
	protected Object createIdentity() {
		throw new IllegalStateException("Should be initialized");
	}

	@Override
	public ThreadConstraint getThreadConstraint() {
		return theLock.getThreadConstraint();
	}

	@Override
	public boolean isLockSupported() {
		return theLock.isLockSupported();
	}

	@Override
	public Transaction lock(boolean write, Object cause) {
		return theLock.lock(write, cause);
	}

	@Override
	public Transaction tryLock(boolean write, Object cause) {
		return theLock.tryLock(write, cause);
	}

	@Override
	public Collection<Cause> getCurrentCauses() {
		return theLock.getCurrentCauses();
	}

	@Override
	public TableView<R, C, V> rows() {
		return new Rows();
	}

	@Override
	public TableView<C, R, V> columns() {
		return new Columns();
	}

	@Override
	public String toString() {
		try (Transaction t = lock(false, null)) {
			List<String> rows = QommonsUtils.map(theRowMap.keySet(), String::valueOf, false);
			List<String> columns = QommonsUtils.map(theColumnMap.keySet(), String::valueOf, false);
			List<List<String>> values = QommonsUtils.map(theRows, r -> QommonsUtils.map(r, String::valueOf, false), false);
			int[] maxColWidths = new int[columns.size() + 1];
			for (int r = 0; r < rows.size(); r++) {
				if (rows.get(r).length() > maxColWidths[0])
					maxColWidths[0] = rows.get(r).length();
				for (int c = 0; c < columns.size(); c++) {
					if (values.get(r).get(c).length() > maxColWidths[c + 1])
						maxColWidths[c + 1] = values.get(r).get(c).length();
				}
			}
			StringBuilder str = new StringBuilder();
			printLine("", columns, maxColWidths, str);
			str.append('\n');
			for (int i = 0; i < maxColWidths[0]; i++)
				str.append('-');
			str.append("-|");
			for (int i = 1; i < maxColWidths.length; i++) {
				str.append('-');
				for (int j = 0; j < maxColWidths[i]; j++)
					str.append('-');
			}
			for (int r = 0; r < rows.size(); r++) {
				str.append('\n');
				printLine(rows.get(r), values.get(r), maxColWidths, str);
			}
			return str.toString();
		}
	}

	private static void printLine(String row, List<String> values, int[] maxColumnWidths, StringBuilder str) {
		str.append(row);
		for (int i = row.length(); i < maxColumnWidths[0]; i++)
			str.append(' ');
		str.append(" |");
		for (int c = 0; c < values.size(); c++) {
			str.append(' ');
			str.append(values.get(c));
			for (int i = values.get(c).length(); i < maxColumnWidths[c + 1]; i++)
				str.append(' ');
		}
	}

	/** @return A builder for the table */
	public static Builder build() {
		return new Builder();
	}

	class Rows extends AbstractIdentifiable implements TableView<R, C, V> {
		@Override
		public CoreId getCoreId() {
			return DefaultTable.this.getCoreId();
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			return DefaultTable.this.getThreadConstraint();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return DefaultTable.this.lock(write, cause);
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return DefaultTable.this.tryLock(write, cause);
		}

		@Override
		public TableEntry<R, C, V> getOrAdd(R value, ElementId after, ElementId before, boolean first, Runnable preAdd, Runnable postAdd) {
			try (Transaction t = lock(true, null)) {
				return new RowEntry(theRowMap.getOrPutEntry(value, __ -> {
					List<V> newRowValues = new CircularArrayList<>();
					for (ElementId column : theColumns)
						newRowValues.add(theFill.apply(value, theColumnMap.keySet().getElement(column).get()));
					return theRows.addElement2(new CircularArrayList<>(), false);
				}, after, before, first, preAdd, postAdd));
			}
		}

		@Override
		public boolean isConsistent(ElementId element) {
			return theRowMap.isConsistent(element);
		}

		@Override
		public boolean checkConsistency() {
			return theRowMap.checkConsistency();
		}

		@Override
		public <X> boolean repair(ElementId element, RepairListener<R, X> listener) {
			return theRowMap.keySet().repair(element, listener);
		}

		@Override
		public <X> boolean repair(RepairListener<R, X> listener) {
			return theRowMap.keySet().repair(listener);
		}

		@Override
		public BetterList<CollectionElement<R>> getElementsBySource(ElementId sourceEl, BetterCollection<?> sourceCollection) {
			// TODO
			return BetterList.empty();
		}

		@Override
		public BetterList<ElementId> getSourceElements(ElementId localElement, BetterCollection<?> sourceCollection) {
			// TODO
			return BetterList.empty();
		}

		@Override
		public ElementId getEquivalentElement(ElementId equivalentEl) {
			// TODO
			return null;
		}

		@Override
		public String canAdd(R value, ElementId after, ElementId before) {
			return null;
		}

		@Override
		public TableEntry<R, C, V> getElement(R value, boolean first) {
			try (Transaction t = lock(false, null)) {
				MapEntryHandle<R, MutableBinaryTreeNode<List<V>>> row = theRowMap.getEntry(value);
				if (row == null)
					return null;
				return new RowEntry(row);
			}
		}

		@Override
		public TableEntry<R, C, V> getElement(ElementId id) {
			try (Transaction t = lock(false, null)) {
				MapEntryHandle<R, MutableBinaryTreeNode<List<V>>> row = theRowMap.getEntryById(id);
				return new RowEntry(row);
			}
		}

		@Override
		public TableEntry<R, C, V> getTerminalElement(boolean first) {
			try (Transaction t = lock(false, null)) {
				MapEntryHandle<R, MutableBinaryTreeNode<List<V>>> row = theRowMap.getTerminalEntry(first);
				if (row == null)
					return null;
				return new RowEntry(row);
			}
		}

		@Override
		public TableEntry<R, C, V> getAdjacentElement(ElementId elementId, boolean next) {
			try (Transaction t = lock(false, null)) {
				MapEntryHandle<R, MutableBinaryTreeNode<List<V>>> row = theRowMap.getAdjacentEntry(elementId, next);
				if (row == null)
					return null;
				return new RowEntry(row);
			}
		}

		@Override
		public MutableTableEntry<R, C, V> mutableElement(ElementId id) {
			try (Transaction t = lock(false, null)) {
				MapEntryHandle<R, MutableBinaryTreeNode<List<V>>> row = theRowMap.getEntryById(id);
				return new MutableRowEntry(row);
			}
		}

		@Override
		public TableEntry<R, C, V> addElement(R value, ElementId after, ElementId before, boolean first)
			throws UnsupportedOperationException, IllegalArgumentException {
			try (Transaction t = lock(true, null)) {
				if (theRowMap.containsKey(value))
					return null;
				List<V> newRowValues = new CircularArrayList<>();
				MutableBinaryTreeNode<List<V>> newRowValuesEl = theRows.addElement2(newRowValues, false);
				MapEntryHandle<R, MutableBinaryTreeNode<List<V>>> row = theRowMap.putEntry(value, newRowValuesEl, after, before, first);
				for (ElementId column : theColumns)
					newRowValues.add(theFill.apply(value, theColumnMap.keySet().getElement(column).get()));
				return new RowEntry(row);
			}
		}

		@Override
		public String canMove(ElementId valueEl, ElementId after, ElementId before) {
			try (Transaction t = lock(false, null)) {
				return theRowMap.keySet().canMove(valueEl, after, before);
			}
		}

		@Override
		public CollectionElement<R> move(ElementId valueEl, ElementId after, ElementId before, boolean first, Runnable afterRemove)
			throws UnsupportedOperationException, IllegalArgumentException {
			try (Transaction t = lock(true, null)) {
				return getElement(theRowMap.keySet().move(valueEl, after, before, first, afterRemove).getElementId());
			}
		}

		@Override
		public void clear() {
			try (Transaction t = lock(true, null)) {
				theRowMap.clear();
				theRows.clear();
			}
		}

		@Override
		public <T> T[] toArray(T[] a) {
			try (Transaction t = lock(false, null)) {
				return theRowMap.keySet().toArray(a);
			}
		}

		@Override
		protected Object createIdentity() {
			return Identifiable.wrap(DefaultTable.this.getIdentity(), "rows");
		}

		@Override
		public Collection<Cause> getCurrentCauses() {
			return DefaultTable.this.getCurrentCauses();
		}

		@Override
		public boolean isEmpty() {
			return theRowMap.isEmpty();
		}

		@Override
		public long getStamp() {
			return theRowMap.getStamp();
		}

		@Override
		public int size() {
			return theRowMap.size();
		}
	}

	class Columns extends AbstractIdentifiable implements TableView<C, R, V> {
		@Override
		public CoreId getCoreId() {
			return DefaultTable.this.getCoreId();
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			return DefaultTable.this.getThreadConstraint();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return DefaultTable.this.lock(write, cause);
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return DefaultTable.this.tryLock(write, cause);
		}

		@Override
		public TableEntry<C, R, V> getOrAdd(C value, ElementId after, ElementId before, boolean first, Runnable preAdd, Runnable postAdd) {
			try (Transaction t = lock(true, null)) {
				MapEntryHandle<C, ElementId> column = theColumnMap.getEntry(value);
				if (column != null)
					return new ColumnEntry(column);
				if (preAdd != null)
					preAdd.run();
				TableEntry<C, R, V> newEl = addElement(value, after, before, first);
				if (postAdd != null)
					postAdd.run();
				return newEl;
			}
		}

		@Override
		public boolean isConsistent(ElementId element) {
			return theColumnMap.isConsistent(element);
		}

		@Override
		public boolean checkConsistency() {
			return theColumnMap.checkConsistency();
		}

		@Override
		public <X> boolean repair(ElementId element, RepairListener<C, X> listener) {
			return theColumnMap.keySet().repair(element, listener);
		}

		@Override
		public <X> boolean repair(RepairListener<C, X> listener) {
			return theColumnMap.keySet().repair(listener);
		}

		@Override
		public BetterList<CollectionElement<C>> getElementsBySource(ElementId sourceEl, BetterCollection<?> sourceCollection) {
			// TODO
			return BetterList.empty();
		}

		@Override
		public BetterList<ElementId> getSourceElements(ElementId localElement, BetterCollection<?> sourceCollection) {
			// TODO
			return BetterList.empty();
		}

		@Override
		public ElementId getEquivalentElement(ElementId equivalentEl) {
			// TODO
			return null;
		}

		@Override
		public String canAdd(C value, ElementId after, ElementId before) {
			return null;
		}

		@Override
		public TableEntry<C, R, V> getElement(C value, boolean first) {
			try (Transaction t = lock(false, null)) {
				MapEntryHandle<C, ElementId> column = theColumnMap.getEntry(value);
				if (column == null)
					return null;
				return new ColumnEntry(column);
			}
		}

		@Override
		public TableEntry<C, R, V> getElement(ElementId id) {
			try (Transaction t = lock(false, null)) {
				MapEntryHandle<C, ElementId> column = theColumnMap.getEntryById(id);
				return new ColumnEntry(column);
			}
		}

		@Override
		public TableEntry<C, R, V> getTerminalElement(boolean first) {
			try (Transaction t = lock(false, null)) {
				MapEntryHandle<C, ElementId> column = theColumnMap.getTerminalEntry(first);
				if (column == null)
					return null;
				return new ColumnEntry(column);
			}
		}

		@Override
		public TableEntry<C, R, V> getAdjacentElement(ElementId elementId, boolean next) {
			try (Transaction t = lock(false, null)) {
				MapEntryHandle<C, ElementId> column = theColumnMap.getAdjacentEntry(elementId, next);
				if (column == null)
					return null;
				return new ColumnEntry(column);
			}
		}

		@Override
		public MutableTableEntry<C, R, V> mutableElement(ElementId id) {
			try (Transaction t = lock(false, null)) {
				MapEntryHandle<C, ElementId> column = theColumnMap.getEntryById(id);
				return new MutableColumnEntry(column);
			}
		}

		@Override
		public TableEntry<C, R, V> addElement(C value, ElementId after, ElementId before, boolean first)
			throws UnsupportedOperationException, IllegalArgumentException {
			try (Transaction t = lock(true, null)) {
				if (theColumnMap.containsKey(value))
					return null;
				MapEntryHandle<C, ElementId> column = theColumnMap.putEntry(value, null, after, before, first);
				ElementId newColumn = theColumns.addElement(column.getElementId(), false).getElementId();
				theColumnMap.mutableEntry(column.getElementId()).set(newColumn);
				for (MapEntryHandle<R, MutableBinaryTreeNode<List<V>>> row = theRowMap.getTerminalEntry(true); //
					row != null; //
					row = theRowMap.getAdjacentEntry(row.getElementId(), true)) {
					row.getValue().get().add(theFill.apply(row.getKey(), value));
				}
				return new ColumnEntry(column);
			}
		}

		@Override
		public String canMove(ElementId valueEl, ElementId after, ElementId before) {
			try (Transaction t = lock(false, null)) {
				return theRowMap.keySet().canMove(valueEl, after, before);
			}
		}

		@Override
		public CollectionElement<C> move(ElementId valueEl, ElementId after, ElementId before, boolean first, Runnable afterRemove)
			throws UnsupportedOperationException, IllegalArgumentException {
			try (Transaction t = lock(true, null)) {
				return getElement(theColumnMap.keySet().move(valueEl, after, before, first, afterRemove).getElementId());
			}
		}

		@Override
		public void clear() {
			try (Transaction t = lock(true, null)) {
				theColumnMap.clear();
				for (List<V> row : theRows)
					row.clear();
			}
		}

		@Override
		public <T> T[] toArray(T[] a) {
			try (Transaction t = lock(false, null)) {
				return theColumnMap.keySet().toArray(a);
			}
		}

		@Override
		protected Object createIdentity() {
			return Identifiable.wrap(DefaultTable.this.getIdentity(), "columns");
		}

		@Override
		public Collection<Cause> getCurrentCauses() {
			return DefaultTable.this.getCurrentCauses();
		}

		@Override
		public boolean isEmpty() {
			return theColumnMap.isEmpty();
		}

		@Override
		public long getStamp() {
			return theColumnMap.getStamp();
		}

		@Override
		public int size() {
			return theColumnMap.size();
		}
	}

	class RowEntry extends AbstractIdentifiable implements TableEntry<R, C, V> {
		private final MapEntryHandle<R, MutableBinaryTreeNode<List<V>>> theRowEntry;

		RowEntry(MapEntryHandle<R, MutableBinaryTreeNode<List<V>>> rowEntry) {
			theRowEntry = rowEntry;
		}

		MapEntryHandle<R, MutableBinaryTreeNode<List<V>>> getRowEntry() {
			return theRowEntry;
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return DefaultTable.this.lock(write, cause);
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return DefaultTable.this.tryLock(write, cause);
		}

		@Override
		public ElementId getElementId() {
			return theRowEntry.getElementId();
		}

		@Override
		public R get() {
			return theRowEntry.getKey();
		}

		@Override
		public TableView<C, R, V> keySet() {
			return columns();
		}

		@Override
		public String canPut(C key, V value) {
			TableValueEntry<R, C, V> entry = getEntry(key);
			if (entry == null)
				return StdMsg.NOT_FOUND;
			else
				return mutableEntry(entry.getElementId()).isAcceptable(value);
		}

		@Override
		protected Object createIdentity() {
			return Identifiable.wrap(DefaultTable.this.getIdentity(), "row", theRowEntry.getElementId());
		}

		@Override
		public TableValueEntry<R, C, V> putEntry(C key, V value, boolean first) {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public TableValueEntry<R, C, V> putEntry(C key, V value, ElementId after, ElementId before, boolean first) {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public TableValueEntry<R, C, V> getEntry(C key) {
			try (Transaction t = lock(false, null)) {
				if (!theRowEntry.getElementId().isPresent())
					return null;
				List<V> rowValues = theRowEntry.getValue().get();
				MapEntryHandle<C, ElementId> column = theColumnMap.getEntry(key);
				return column == null ? null : new TableRowValueElement(theRowEntry, column, rowValues);
			}
		}

		@Override
		public TableValueEntry<R, C, V> getOrPutEntry(C key, Function<? super C, ? extends V> value, ElementId after, ElementId before,
			boolean first, Runnable preAdd, Runnable postAdd) {
			try (Transaction t = lock(false, null)) {
				if (!theRowEntry.getElementId().isPresent())
					return null;
				List<V> rowValues = theRowEntry.getValue().get();
				MapEntryHandle<C, ElementId> column = theColumnMap.getEntry(key);
				if (column == null)
					throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
				return new TableRowValueElement(theRowEntry, column, rowValues);
			}
		}

		@Override
		public TableValueEntry<R, C, V> getEntryById(ElementId entryId) {
			try (Transaction t = lock(false, null)) {
				if (!theRowEntry.getElementId().isPresent())
					return null;
				List<V> rowValues = theRowEntry.getValue().get();
				MapEntryHandle<C, ElementId> column = theColumnMap.getEntryById(entryId);
				return new TableRowValueElement(theRowEntry, column, rowValues);
			}
		}

		@Override
		public TableValueEntry<R, C, V> getTerminalEntry(boolean first) {
			try (Transaction t = lock(false, null)) {
				if (!theRowEntry.getElementId().isPresent())
					return null;
				List<V> rowValues = theRowEntry.getValue().get();
				MapEntryHandle<C, ElementId> column = theColumnMap.getTerminalEntry(first);
				return column == null ? null : new TableRowValueElement(theRowEntry, column, rowValues);
			}
		}

		@Override
		public TableValueEntry<R, C, V> getAdjacentEntry(ElementId entryId, boolean next) {
			try (Transaction t = lock(false, null)) {
				if (!theRowEntry.getElementId().isPresent())
					return null;
				List<V> rowValues = theRowEntry.getValue().get();
				MapEntryHandle<C, ElementId> column = theColumnMap.getAdjacentEntry(entryId, next);
				return column == null ? null : new TableRowValueElement(theRowEntry, column, rowValues);
			}
		}

		@Override
		public MutableTableValueEntry<R, C, V> mutableEntry(ElementId entryId) {
			try (Transaction t = lock(false, null)) {
				if (!theRowEntry.getElementId().isPresent())
					return null;
				List<V> rowValues = theRowEntry.getValue().get();
				MapEntryHandle<C, ElementId> column = theColumnMap.getEntryById(entryId);
				return new MutableTableRowValueElement(theRowEntry, column, rowValues);
			}
		}

		@Override
		public TableValueEntry<R, C, V> computeEntryIfAbsent(C key, Function<? super C, ? extends V> value, boolean first) {
			return getOrPutEntry(key, value, getElementId(), getElementId(), first, null, null);
		}
	}

	class MutableRowEntry extends RowEntry implements MutableTableEntry<R, C, V> {
		MutableRowEntry(MapEntryHandle<R, MutableBinaryTreeNode<List<V>>> rowEntry) {
			super(rowEntry);
		}

		@Override
		public BetterCollection<R> getCollection() {
			return rows();
		}

		@Override
		public String isEnabled() {
			try (Transaction t = lock(false, null)) {
				return theRowMap.keySet().mutableElement(getRowEntry().getElementId()).isEnabled();
			}
		}

		@Override
		public String isAcceptable(R value) {
			try (Transaction t = lock(false, null)) {
				return theRowMap.keySet().mutableElement(getRowEntry().getElementId()).isAcceptable(value);
			}
		}

		@Override
		public void set(R value) throws UnsupportedOperationException, IllegalArgumentException {
			try (Transaction t = lock(true, null)) {
				theRowMap.keySet().mutableElement(getRowEntry().getElementId()).set(value);
			}
		}

		@Override
		public String canRemove() {
			try (Transaction t = lock(false, null)) {
				return theRowMap.keySet().mutableElement(getRowEntry().getElementId()).canRemove();
			}
		}

		@Override
		public void remove(BiConsumer<C, V> eachEntry) {
			try (Transaction t = lock(true, null)) {
				if (!getRowEntry().getElementId().isPresent())
					throw new IllegalArgumentException(StdMsg.ELEMENT_REMOVED);
				MutableCollectionElement<List<V>> values = getRowEntry().get();
				if (eachEntry != null) {
					int c = 0;
					for (ElementId column : theColumns) {
						eachEntry.accept(theColumnMap.getEntryById(column).getKey(), values.get().get(c));
						c++;
					}
				}
				values.remove();
				theRowMap.keySet().mutableElement(getRowEntry().getElementId()).remove();
			}
		}
	}

	class ColumnEntry extends AbstractIdentifiable implements TableEntry<C, R, V> {
		private final MapEntryHandle<C, ElementId> theColumnEntry;

		ColumnEntry(MapEntryHandle<C, ElementId> columnEntry) {
			theColumnEntry = columnEntry;
		}

		MapEntryHandle<C, ElementId> getColumnEntry() {
			return theColumnEntry;
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return DefaultTable.this.lock(write, cause);
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return DefaultTable.this.tryLock(write, cause);
		}

		@Override
		public ElementId getElementId() {
			return theColumnEntry.getElementId();
		}

		@Override
		public C get() {
			return theColumnEntry.getKey();
		}

		@Override
		public TableView<R, C, V> keySet() {
			return rows();
		}

		@Override
		public String canPut(R key, V value) {
			TableValueEntry<C, R, V> entry = getEntry(key);
			if (entry == null)
				return StdMsg.NOT_FOUND;
			else
				return mutableEntry(entry.getElementId()).isAcceptable(value);
		}

		@Override
		protected Object createIdentity() {
			return Identifiable.wrap(DefaultTable.this.getIdentity(), "column", theColumnEntry.getElementId());
		}

		@Override
		public TableValueEntry<C, R, V> putEntry(R key, V value, boolean first) {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public TableValueEntry<C, R, V> putEntry(R key, V value, ElementId after, ElementId before, boolean first) {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public TableValueEntry<C, R, V> getEntry(R key) {
			try (Transaction t = lock(false, null)) {
				if (!theColumnEntry.getElementId().isPresent())
					return null;
				MapEntryHandle<R, MutableBinaryTreeNode<List<V>>> row = theRowMap.getEntry(key);
				return row == null ? null : new TableColumnValueElement(row, theColumnEntry, row.get().get());
			}
		}

		@Override
		public TableValueEntry<C, R, V> getOrPutEntry(R key, Function<? super R, ? extends V> value, ElementId after, ElementId before,
			boolean first, Runnable preAdd, Runnable postAdd) {
			try (Transaction t = lock(false, null)) {
				if (!theColumnEntry.getElementId().isPresent())
					return null;
				MapEntryHandle<R, MutableBinaryTreeNode<List<V>>> row = theRowMap.getEntry(key);
				if (row == null)
					throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
				return new TableColumnValueElement(row, theColumnEntry, row.get().get());
			}
		}

		@Override
		public TableValueEntry<C, R, V> getEntryById(ElementId entryId) {
			try (Transaction t = lock(false, null)) {
				if (!theColumnEntry.getElementId().isPresent())
					return null;
				MapEntryHandle<R, MutableBinaryTreeNode<List<V>>> row = theRowMap.getEntryById(entryId);
				return new TableColumnValueElement(row, theColumnEntry, row.get().get());
			}
		}

		@Override
		public TableValueEntry<C, R, V> getTerminalEntry(boolean first) {
			try (Transaction t = lock(false, null)) {
				if (!theColumnEntry.getElementId().isPresent())
					return null;
				MapEntryHandle<R, MutableBinaryTreeNode<List<V>>> row = theRowMap.getTerminalEntry(first);
				return row == null ? null : new TableColumnValueElement(row, theColumnEntry, row.get().get());
			}
		}

		@Override
		public TableValueEntry<C, R, V> getAdjacentEntry(ElementId entryId, boolean next) {
			try (Transaction t = lock(false, null)) {
				if (!theColumnEntry.getElementId().isPresent())
					return null;
				MapEntryHandle<R, MutableBinaryTreeNode<List<V>>> row = theRowMap.getAdjacentEntry(entryId, next);
				return row == null ? null : new TableColumnValueElement(row, theColumnEntry, row.get().get());
			}
		}

		@Override
		public MutableTableValueEntry<C, R, V> mutableEntry(ElementId entryId) {
			try (Transaction t = lock(false, null)) {
				if (!theColumnEntry.getElementId().isPresent())
					return null;
				MapEntryHandle<R, MutableBinaryTreeNode<List<V>>> row = theRowMap.getEntryById(entryId);
				return new MutableTableColumnValueElement(row, theColumnEntry, row.get().get());
			}
		}

		@Override
		public TableValueEntry<C, R, V> computeEntryIfAbsent(R key, Function<? super R, ? extends V> value, boolean first) {
			return getOrPutEntry(key, value, getElementId(), getElementId(), first, null, null);
		}
	}

	class MutableColumnEntry extends ColumnEntry implements MutableTableEntry<C, R, V> {
		MutableColumnEntry(MapEntryHandle<C, ElementId> columnEntry) {
			super(columnEntry);
		}

		@Override
		public BetterCollection<C> getCollection() {
			return columns();
		}

		@Override
		public String isEnabled() {
			try (Transaction t = lock(false, null)) {
				return theColumnMap.keySet().mutableElement(getColumnEntry().getElementId()).isEnabled();
			}
		}

		@Override
		public String isAcceptable(C value) {
			try (Transaction t = lock(false, null)) {
				return theColumnMap.keySet().mutableElement(getColumnEntry().getElementId()).isAcceptable(value);
			}
		}

		@Override
		public void set(C value) throws UnsupportedOperationException, IllegalArgumentException {
			try (Transaction t = lock(true, null)) {
				theColumnMap.keySet().mutableElement(getColumnEntry().getElementId()).set(value);
			}
		}

		@Override
		public String canRemove() {
			try (Transaction t = lock(false, null)) {
				return theColumnMap.keySet().mutableElement(getColumnEntry().getElementId()).canRemove();
			}
		}

		@Override
		public void remove(BiConsumer<R, V> eachEntry) {
			try (Transaction t = lock(true, null)) {
				if (!getColumnEntry().getElementId().isPresent())
					throw new IllegalArgumentException(StdMsg.ELEMENT_REMOVED);
				int columnIndex = theColumns.getElementsBefore(getColumnEntry().get());
				for (MapEntryHandle<R, MutableBinaryTreeNode<List<V>>> row = theRowMap.getTerminalEntry(true); //
					row != null; //
					row = theRowMap.getAdjacentEntry(row.getElementId(), true)) {
					V value = row.get().get().remove(columnIndex);
					if (eachEntry != null)
						eachEntry.accept(row.getKey(), value);
				}
				theColumns.mutableElement(getColumnEntry().get()).remove();
				theRowMap.keySet().mutableElement(getColumnEntry().getElementId()).remove();
			}
		}
	}

	class RowEntryValues extends AbstractIdentifiable implements BetterCollection<V> {
		private final MapEntryHandle<R, MutableBinaryTreeNode<List<V>>> theRowEntry;

		RowEntryValues(MapEntryHandle<R, MutableBinaryTreeNode<List<V>>> rowEntry) {
			theRowEntry = rowEntry;
		}

		@Override
		public boolean isEmpty() {
			return !theRowEntry.getElementId().isPresent() || theColumns.isEmpty();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return DefaultTable.this.lock(write, cause);
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return DefaultTable.this.tryLock(write, cause);
		}

		@Override
		public CoreId getCoreId() {
			return DefaultTable.this.getCoreId();
		}

		@Override
		protected Object createIdentity() {
			return Identifiable.wrap(DefaultTable.this.getIdentity(), "rowValues", theRowEntry.getElementId());
		}

		@Override
		public CollectionElement<V> getElement(V value, boolean first) {
			try (Transaction t = lock(false, null)) {
				if (!theRowEntry.getElementId().isPresent())
					return null;
				List<V> columnValues = theRowEntry.getValue().get();
				int c = 0;
				for (ElementId column : theColumns) {
					if (Objects.equals(columnValues.get(c), value))
						return new TableRowValueElement(theRowEntry, theColumnMap.getEntryById(column), columnValues);
					c++;
				}
			}
			return null;
		}

		@Override
		public CollectionElement<V> getElement(ElementId id) {
			try (Transaction t = lock(false, null)) {
				if (!theRowEntry.getElementId().isPresent())
					return null;
				List<V> columnValues = theRowEntry.getValue().get();
				MapEntryHandle<C, ElementId> column = theColumnMap.getEntryById(id);
				return new TableRowValueElement(theRowEntry, column, columnValues);
			}
		}

		@Override
		public CollectionElement<V> getTerminalElement(boolean first) {
			try (Transaction t = lock(false, null)) {
				if (!theRowEntry.getElementId().isPresent())
					return null;
				List<V> columnValues = theRowEntry.getValue().get();
				MapEntryHandle<C, ElementId> column = theColumnMap.getTerminalEntry(first);
				return column == null ? null : new TableRowValueElement(theRowEntry, column, columnValues);
			}
		}

		@Override
		public CollectionElement<V> getAdjacentElement(ElementId elementId, boolean next) {
			try (Transaction t = lock(false, null)) {
				if (!theRowEntry.getElementId().isPresent())
					return null;
				List<V> columnValues = theRowEntry.getValue().get();
				MapEntryHandle<C, ElementId> column = theColumnMap.getAdjacentEntry(elementId, next);
				return column == null ? null : new TableRowValueElement(theRowEntry, column, columnValues);
			}
		}

		@Override
		public MutableCollectionElement<V> mutableElement(ElementId id) {
			try (Transaction t = lock(false, null)) {
				if (!theRowEntry.getElementId().isPresent())
					return null;
				List<V> rowValues = theRowEntry.getValue().get();
				MapEntryHandle<C, ElementId> column = theColumnMap.getEntryById(id);
				return new MutableTableRowValueElement(theRowEntry, column, rowValues);
			}
		}

		@Override
		public BetterList<CollectionElement<V>> getElementsBySource(ElementId sourceEl, BetterCollection<?> sourceCollection) {
			// TODO
			return BetterList.empty();
		}

		@Override
		public BetterList<ElementId> getSourceElements(ElementId localElement, BetterCollection<?> sourceCollection) {
			// TODO
			return BetterList.empty();
		}

		@Override
		public ElementId getEquivalentElement(ElementId equivalentEl) {
			// TODO
			return null;
		}

		@Override
		public String canAdd(V value, ElementId after, ElementId before) {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public CollectionElement<V> addElement(V value, ElementId after, ElementId before, boolean first)
			throws UnsupportedOperationException, IllegalArgumentException {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public String canMove(ElementId valueEl, ElementId after, ElementId before) {
			return theColumnMap.keySet().canMove(valueEl, after, before);
		}

		@Override
		public CollectionElement<V> move(ElementId valueEl, ElementId after, ElementId before, boolean first, Runnable afterRemove)
			throws UnsupportedOperationException, IllegalArgumentException {
			try (Transaction t = lock(false, null)) {
				if (!theRowEntry.getElementId().isPresent())
					return null;
				List<V> rowValues = theRowEntry.getValue().get();
				MapEntryHandle<C, ElementId> column = theColumnMap
					.getEntryById(theColumnMap.keySet().move(valueEl, after, before, first, afterRemove).getElementId());
				return new TableRowValueElement(theRowEntry, column, rowValues);
			}
		}

		@Override
		public Collection<Cause> getCurrentCauses() {
			return DefaultTable.this.getCurrentCauses();
		}

		@Override
		public long getStamp() {
			return theColumnMap.getStamp();
		}

		@Override
		public int size() {
			return theColumnMap.size();
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			return DefaultTable.this.getThreadConstraint();
		}

		@Override
		public void clear() {
			columns().clear();
		}
	}

	class TableRowValueElement implements TableValueEntry<R, C, V> {
		private final MapEntryHandle<R, MutableBinaryTreeNode<List<V>>> theRow;
		private final MapEntryHandle<C, ElementId> theColumn;
		private final List<V> theColumnValues;

		TableRowValueElement(MapEntryHandle<R, MutableBinaryTreeNode<List<V>>> row, MapEntryHandle<C, ElementId> column,
			List<V> columnValues) {
			theRow = row;
			theColumn = column;
			theColumnValues = columnValues;
		}

		protected MapEntryHandle<R, MutableBinaryTreeNode<List<V>>> getRowHandle() {
			return theRow;
		}

		protected MapEntryHandle<C, ElementId> getColumnHandle() {
			return theColumn;
		}

		protected List<V> getColumnValues() {
			return theColumnValues;
		}

		@Override
		public R getRow() {
			return theRow.getKey();
		}

		@Override
		public ElementId getRowId() {
			return theRow.getElementId();
		}

		@Override
		public C getColumn() {
			return theColumn.getKey();
		}

		@Override
		public ElementId getColumnId() {
			return theColumn.getElementId();
		}

		@Override
		public V get() {
			try (Transaction t = lock(false, null)) {
				if (!theColumn.getElementId().isPresent())
					return null;
				int index = theColumns.getElementsBefore(theColumn.getValue());
				return theColumnValues.get(index);
			}
		}
	}

	class MutableTableRowValueElement extends TableRowValueElement implements MutableTableValueEntry<R, C, V> {
		MutableTableRowValueElement(MapEntryHandle<R, MutableBinaryTreeNode<List<V>>> row, MapEntryHandle<C, ElementId> column,
			List<V> rowValues) {
			super(row, column, rowValues);
		}

		@Override
		public BetterCollection<V> getCollection() {
			return rows().getElement(getRowHandle().getElementId()).values();
		}

		@Override
		public String isEnabled() {
			try (Transaction t = lock(false, null)) {
				if (!getColumnHandle().getElementId().isPresent())
					return StdMsg.ELEMENT_REMOVED;
				return null;
			}
		}

		@Override
		public String isAcceptable(V value) {
			try (Transaction t = lock(false, null)) {
				if (!getColumnHandle().getElementId().isPresent())
					return StdMsg.ELEMENT_REMOVED;
				return null;
			}
		}

		@Override
		public void set(V value) throws UnsupportedOperationException, IllegalArgumentException {
			try (Transaction t = lock(false, null)) {
				if (!getColumnHandle().getElementId().isPresent())
					throw new IllegalArgumentException(StdMsg.ELEMENT_REMOVED);
				int index = theColumns.getElementsBefore(getColumnHandle().getValue());
				getColumnValues().set(index, value);
			}
		}
	}

	class TableColumnValueElement implements TableValueEntry<C, R, V> {
		private final MapEntryHandle<R, MutableBinaryTreeNode<List<V>>> theRow;
		private final MapEntryHandle<C, ElementId> theColumn;
		private final List<V> theRowValues;

		TableColumnValueElement(MapEntryHandle<R, MutableBinaryTreeNode<List<V>>> row, MapEntryHandle<C, ElementId> column,
			List<V> rowValues) {
			theRow = row;
			theColumn = column;
			theRowValues = rowValues;
		}

		protected MapEntryHandle<R, MutableBinaryTreeNode<List<V>>> getRowHandle() {
			return theRow;
		}

		protected MapEntryHandle<C, ElementId> getColumnHandle() {
			return theColumn;
		}

		protected List<V> getRowValues() {
			return theRowValues;
		}

		@Override
		public R getKey() {
			return theRow.getKey();
		}

		@Override
		public ElementId getElementId() {
			return theRow.getElementId();
		}

		@Override
		public V get() {
			try (Transaction t = lock(false, null)) {
				if (!theColumn.getElementId().isPresent())
					return null;
				int index = theColumns.getElementsBefore(theColumn.getValue());
				return theRowValues.get(index);
			}
		}

		@Override
		public C getRow() {
			return theColumn.getKey();
		}

		@Override
		public ElementId getRowId() {
			return theColumn.getElementId();
		}

		@Override
		public R getColumn() {
			return theRow.getKey();
		}

		@Override
		public ElementId getColumnId() {
			return theRow.getElementId();
		}
	}

	class MutableTableColumnValueElement extends TableColumnValueElement implements MutableTableValueEntry<C, R, V> {
		MutableTableColumnValueElement(MapEntryHandle<R, MutableBinaryTreeNode<List<V>>> row, MapEntryHandle<C, ElementId> column,
			List<V> rowValues) {
			super(row, column, rowValues);
		}

		@Override
		public BetterCollection<V> getCollection() {
			return rows().getElement(getRowHandle().getElementId()).values();
		}

		@Override
		public String isEnabled() {
			try (Transaction t = lock(false, null)) {
				if (!getColumnHandle().getElementId().isPresent())
					return StdMsg.ELEMENT_REMOVED;
				return null;
			}
		}

		@Override
		public String isAcceptable(V value) {
			try (Transaction t = lock(false, null)) {
				if (!getColumnHandle().getElementId().isPresent())
					return StdMsg.ELEMENT_REMOVED;
				return null;
			}
		}

		@Override
		public void set(V value) throws UnsupportedOperationException, IllegalArgumentException {
			try (Transaction t = lock(false, null)) {
				if (!getColumnHandle().getElementId().isPresent())
					throw new IllegalArgumentException(StdMsg.ELEMENT_REMOVED);
				int index = theColumns.getElementsBefore(getColumnHandle().getValue());
				getRowValues().set(index, value);
			}
		}
	}

	/** Builds a {@link DefaultTable} */
	public static class Builder extends CollectionBuilder.Default<Builder> {
		Builder() {
			super(DefaultTable.class.getSimpleName());
		}

		/**
		 * Builds a table
		 * 
		 * @param <R> The type of rows in the table
		 * @param <C> The type of columns in the table
		 * @param <V> The type of values in the table
		 * @param fill The function to generate values for each row/column pair in the table
		 * @return The table
		 */
		public <R, C, V> DefaultTable<R, C, V> build(BiFunction<? super R, ? super C, ? extends V> fill) {
			return new DefaultTable<>(fill, getDescription(), getLocker());
		}
	}
}
