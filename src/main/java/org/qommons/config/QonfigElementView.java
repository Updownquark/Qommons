package org.qommons.config;

import java.text.ParseException;
import java.util.*;

import org.qommons.MultiInheritanceSet;
import org.qommons.collect.BetterList;
import org.qommons.config.QonfigElement.AttributeValue;
import org.qommons.config.QonfigElement.QonfigValue;
import org.qommons.io.FilePosition;
import org.qommons.io.LocatedFilePosition;
import org.qommons.io.LocatedPositionedContent;
import org.qommons.io.PositionedContent;

/**
 * This class provides an easier way to access members (attributes, element values, and child elements) of {@link QonfigElement}s, making it
 * easier to refer to data by name
 * 
 * @param <E> The sub-type of {@link PartialQonfigElement} this view is for
 * @param <X> The type of exception this view may throw if the creation of a derived view fails
 * @param <V> The sub type of view that this view creates as sub-views
 */
public interface QonfigElementView<E extends PartialQonfigElement, X extends Throwable, V extends QonfigElementView<E, X, V>> {
	/**
	 * A view of a member (an attribute, element value, or child element) of a {@link QonfigElement} or {@link PartialQonfigElement}
	 * 
	 * @param <M> The type of the member definition
	 * @param <V> The type of the member
	 */
	public interface MemberView<M extends QonfigElementOwned, V> {
		/** @return The element view owning this member view */
		QonfigElementView<?, ?, ?> getOwner();

		/** @return The definition of this member */
		M getDefinition();

		/** @return The member this is a view of */
		V get();
	}

	/**
	 * A view of a member with a value (an attribute or element value)
	 * 
	 * @param <M> The type of the member definition
	 * @param <V> The type of the member
	 */
	public class ValueMember<M extends QonfigValueDef, V extends QonfigValue> implements MemberView<M, V> {
		private final QonfigElementView<?, ?, ?> theOwner;
		private final M theDefinition;
		private final V theValue;

		/**
		 * @param owner The element view owning this member view
		 * @param definition The definition of this member
		 * @param value The member this is a view of
		 */
		public ValueMember(QonfigElementView<?, ?, ?> owner, M definition, V value) {
			theOwner = owner;
			theDefinition = definition;
			theValue = value;
		}

		@Override
		public QonfigElementView<?, ?, ?> getOwner() {
			return theOwner;
		}

		@Override
		public M getDefinition() {
			return theDefinition;
		}

		@Override
		public V get() {
			return theValue;
		}

		/** @return The member's value */
		public Object getValue() {
			QonfigValue value = get();
			return value == null ? null : value.value;
		}

		/** @return The text of the member's value */
		public String getText() {
			QonfigValue value = get();
			return value == null ? null : value.text;
		}

		/** @return The member's value type */
		public QonfigValueType getType() {
			return getDefinition().getType();
		}

		/**
		 * @param <T> The type of the member's value
		 * @param type The type of the member's value
		 * @return The member's value, as the given type
		 */
		public <T> T getValue(Class<T> type) {
			return getValue(type, null);
		}

		/**
		 * @param <T> The type of the member's value
		 * @param type The type of the member's value
		 * @param defaultValue The value to return if none was specified for the member
		 * @return The member's value, as the given type
		 */
		public <T> T getValue(Class<T> type, T defaultValue) {
			QonfigValue value = get();
			if (value == null) {
				if (!getOwner().getElement().isInstance(getDefinition().getDeclared().getOwner()))
					throw new IllegalArgumentException("This element (type " + getType().getName() + ") does not "
						+ (getDefinition().getDeclared().getOwner() instanceof QonfigAddOn ? "inherit add-on" : "extend element-def") + " '"
						+ getDefinition().getDeclared().getOwner().getName() + "' and cannot have a value for " + this);
				if (defaultValue == null && type.isPrimitive())
					throw new IllegalArgumentException(
						"No value for " + this + " for element " + getOwner().getElement().toLocatedString());
				return defaultValue;
			} else if (type != null && !type.isInstance(value.value)) {
				boolean match = false;
				if (type.isPrimitive()) {
					if (type == boolean.class)
						match = value.value instanceof Boolean;
					else if (type == char.class)
						match = value.value instanceof Character;
					else if (type == byte.class)
						match = value.value instanceof Byte;
					else if (type == short.class)
						match = value.value instanceof Short;
					else if (type == int.class)
						match = value.value instanceof Integer;
					else if (type == long.class)
						match = value.value instanceof Long;
					else if (type == float.class)
						match = value.value instanceof Float;
					else if (type == double.class)
						match = value.value instanceof Double;
					else
						throw new IllegalStateException("Unaccounted primitive type " + type.getName());
				}
				if (!match)
					throw new IllegalArgumentException(
						"Value '" + value + "' for " + this + " is typed " + value.value.getClass().getName() + ", not " + type.getName());
			}
			return (T) value.value;
		}

		/** @return The content in the source file where the member's value was specified */
		public PositionedContent getContent() {
			QonfigValue value = get();
			return value == null ? null : value.position;
		}

		/** @return The content in the source file where the member's value was specified */
		public LocatedPositionedContent getLocatedContent() {
			QonfigValue value = get();
			return value == null ? null : LocatedPositionedContent.of(value.fileLocation, value.position);
		}

		/** @return The start of the content in the source file where the member's value was specified */
		public FilePosition getPosition() {
			QonfigValue value = get();
			return value == null ? null : value.position.getPosition(0);
		}

		/** @return The start of the content in the source file where the member's value was specified */
		public LocatedFilePosition getLocatedPosition() {
			QonfigValue value = get();
			return value == null ? null : LocatedFilePosition.of(value.fileLocation, value.position.getPosition(0));
		}
	}

	/** A view of an attribute */
	public class AttributeView extends ValueMember<QonfigAttributeDef, AttributeValue> {
		/**
		 * @param owner The element view owning this attribute view
		 * @param attribute The attribute definition
		 * @param value The attribute value
		 */
		public AttributeView(QonfigElementView<?, ?, ?> owner, QonfigAttributeDef attribute, AttributeValue value) {
			super(owner, attribute, value);
		}

		/** @return The positioned content of the name of the attribute in the file where the attribute value was declared */
		public PositionedContent getName() {
			return get().getNamePosition();
		}

		/** @return The positioned content of the name of the attribute in the file where the attribute value was declared */
		public LocatedPositionedContent getLocatedName() {
			return LocatedPositionedContent.of(get().fileLocation, get().getNamePosition());
		}

		/** @return The start of the positioned content of the name of the attribute in the file where the attribute value was declared */
		public FilePosition getNameStart() {
			return getName().getPosition(0);
		}

		/** @return The start of the positioned content of the name of the attribute in the file where the attribute value was declared */
		public LocatedFilePosition getLocatedNameStart() {
			return getLocatedName().getPosition(0);
		}

		@Override
		public String toString() {
			return getOwner().getElement().toLocatedString() + "." + getDefinition().getName();
		}
	}

	/** A view of an element value */
	public class ElementValueView extends ValueMember<QonfigValueDef, QonfigValue> {
		/**
		 * @param owner The element view owning this attribute view
		 * @param definition The element value definition
		 * @param value The element value
		 */
		public ElementValueView(QonfigElementView<?, ?, ?> owner, QonfigValueDef definition, QonfigValue value) {
			super(owner, definition, value);
		}
	}

	/**
	 * A view of the children fulfilling a single role in element
	 * 
	 * @param <E> The sub-type of {@link PartialQonfigElement} this view is for
	 * @param <X> The type of exception this view may throw if the creation of a derived view fails
	 * @param <V> The sub type of view that this view creates as sub-views
	 */
	public class ChildView<E extends PartialQonfigElement, X extends Throwable, V extends QonfigElementView<E, X, V>>
		implements MemberView<QonfigChildDef, BetterList<V>> {
		private final V theOwner;
		private final QonfigChildDef theChild;
		private final BetterList<V> theChildren;

		/**
		 * @param owner The parent element view owning this child view
		 * @param child The child role definition
		 * @param children The views for each child fulfilling the role in the parent element
		 */
		public ChildView(V owner, QonfigChildDef child, BetterList<V> children) {
			theOwner = owner;
			theChild = child;
			theChildren = children;
		}

		@Override
		public V getOwner() {
			return theOwner;
		}

		@Override
		public QonfigChildDef getDefinition() {
			return theChild;
		}

		@Override
		public BetterList<V> get() {
			return theChildren;
		}

		@Override
		public String toString() {
			return getOwner().getElement().toLocatedString() + "." + theChild.getName();
		}
	}

	/**
	 * Represents a set of members in an element view
	 * 
	 * @param <D> The type of the members this set is for
	 * @param <V> The sub type of view that this view creates as sub-views
	 * @param <M> The type of view this member set exposes
	 * @param <X> The type of exception this view may throw if the creation of a derived view fails
	 */
	public interface MemberSet<D extends QonfigElementOwned, V, M extends MemberView<?, V>, X extends Throwable> {
		/** @return The element view owning this member set */
		QonfigElementView<?, ?, ?> getOwner();

		/**
		 * @param owner The element or add-on which is extended or inherited by this member set's owner that declared the member
		 * @param member The name of the member
		 * @return The definition of the member declared by the given type with the given name
		 * @throws IllegalArgumentException If
		 *         <ul>
		 *         <li>this member set's owner does not extend or inherit the given type</li>
		 *         <li>no such member of this member set's type was declared for the given element or add-on or its super types</li>
		 *         <li>no member with the given name is declared by the type and multiple members with the given name are declared by its
		 *         super-types</li>
		 *         </ul>
		 */
		D getDefinition(QonfigElementOrAddOn owner, String member) throws IllegalArgumentException;

		/**
		 * @param member The name of the member
		 * @return The definition of the member
		 * @throws IllegalArgumentException If
		 *         <ul>
		 *         <li>no such member of this member set's type was declared by any type visible to this member set's owner</li>
		 *         <li>no member with the given name is declared by this member set's owner's {@link QonfigElementView#getFocusType() focus
		 *         type} and multiple members with the given name are declared by other types visible to the owner</li>
		 *         </ul>
		 */
		D getDefinition(String member) throws IllegalArgumentException;

		/**
		 * @param definition The Qonfig definition of the member
		 * @return A view of the given member on this member set's owner
		 * @throws X If the view could not be created
		 */
		M get(D definition) throws X;

		/**
		 * @param owner The element or add-on which is extended or inherited by this member set's owner that declared the member
		 * @param member The name of the member
		 * @return A view of the member declared by the given type with the given name on this member set's owner
		 * @throws IllegalArgumentException If
		 *         <ul>
		 *         <li>this member set's owner does not extend or inherit the given type</li>
		 *         <li>no such member of this member set's type was declared for any type visible to this member set's owner</li>
		 *         <li>no member with the given name is declared by this member set's owner's {@link QonfigElementView#getFocusType() focus
		 *         type} and multiple members with the given name are declared by other types visible to the owner</li>
		 *         </ul>
		 * @throws X If the view could not be created
		 */
		default M get(QonfigElementOrAddOn owner, String member) throws IllegalArgumentException, X {
			return get(getDefinition(owner, member));
		}

		/**
		 * @param toolkit The toolkit declaring the type declaring the member to get the view for
		 * @param type The name of the type declaring the member to get the view for
		 * @param member The name of the member to get the view for
		 * @return A view of the member declared by the given type with the given name on this member set's owner
		 * @throws IllegalArgumentException If
		 *         <ul>
		 *         <li>No such type with the given name is declared by the given toolkit or its dependencies</li>
		 *         <li>No such type is declared by the given toolkit and multiple such types are declared by its dependencies</li>
		 *         <li>this member set's owner does not extend or inherit the given type</li>
		 *         <li>no such member of this member set's type was declared for any type visible to this member set's owner</li>
		 *         <li>no member with the given name is declared by this member set's owner's {@link QonfigElementView#getFocusType() focus
		 *         type} and multiple members with the given name are declared by other types visible to the owner</li>
		 *         </ul>
		 * @throws X If the view could not be created
		 */
		default M get(QonfigToolkit toolkit, String type, String member) throws IllegalArgumentException, X {
			return get(getOwner().getType(toolkit, type), member);
		}

		/**
		 * @param toolkitDef A reference to the toolkit declaring the type declaring the member to get the view for. This can be:
		 *        <ul>
		 *        <li>null to use the toolkit that declared this member set's owner's {@link QonfigElementView#getFocusType() focus
		 *        type}</li>
		 *        <li>The name of a dependency in the focus type's toolkit</li>
		 *        <li>Of the form 'Toolkit-Name vM.m', the formal definition of a toolkit</li>
		 *        </ul>
		 * @param type The name of the type declaring the member to get the view for
		 * @param member The name of the member to get the view for
		 * @return A view of the member declared by the given type with the given name on this member set's owner
		 * @throws IllegalArgumentException If
		 *         <ul>
		 *         <li>The toolkit reference could not be resolved</li>
		 *         <li>No such type with the given name is declared by the given toolkit or its dependencies</li>
		 *         <li>No such type is declared by the given toolkit and multiple such types are declared by its dependencies</li>
		 *         <li>this member set's owner does not extend or inherit the given type</li>
		 *         <li>no such member of this member set's type was declared for any type visible to this member set's owner</li>
		 *         <li>no member with the given name is declared by this member set's owner's {@link QonfigElementView#getFocusType() focus
		 *         type} and multiple members with the given name are declared by other types visible to the owner</li>
		 *         </ul>
		 * @throws X If the view could not be created
		 */
		default M get(String toolkitDef, String type, String member) throws IllegalArgumentException, X {
			return get(getOwner().getType(toolkitDef, type), member);
		}

		/**
		 * @param owner The name of the type declaring the member to get the view for
		 * @param member The name of the member to get the view for
		 * @return A view of the member declared by the given type with the given name on this member set's owner
		 * @throws IllegalArgumentException If
		 *         <ul>
		 *         <li>No such type with the given name is declared by the {@link QonfigElementView#getFocusType() focus type} of this
		 *         member set's owner or its dependencies</li>
		 *         <li>No such type is declared by the focus type's toolkit and multiple such types are declared by its dependencies</li>
		 *         <li>this member set's owner does not extend or inherit the given type</li>
		 *         <li>no such member of this member set's type was declared for any type visible to this member set's owner</li>
		 *         <li>no member with the given name is declared by this member set's owner's {@link QonfigElementView#getFocusType() focus
		 *         type} and multiple members with the given name are declared by other types visible to the owner</li>
		 *         </ul>
		 * @throws X If the view could not be created
		 */
		default M get(String owner, String member) throws IllegalArgumentException, X {
			return get(getOwner().getType(owner), member);
		}

		/**
		 * @param member The name of the member to get the view for
		 * @return A view of the member declared by the given type with the given name on this member set's owner
		 * @throws IllegalArgumentException If
		 *         <ul>
		 *         <li>no such member of this member set's type was declared for any type visible to this member set's owner</li>
		 *         <li>no member with the given name is declared by this member set's owner's {@link QonfigElementView#getFocusType() focus
		 *         type} and multiple members with the given name are declared by other types visible to the owner</li>
		 *         </ul>
		 * @throws X If the view could not be created
		 */
		default M get(String member) throws IllegalArgumentException, X {
			return get(getDefinition(member));
		}
	}

	/** A view of the set of attributes on an element */
	public class AttributeSet implements MemberSet<QonfigAttributeDef, AttributeValue, AttributeView, RuntimeException> {
		private final QonfigElementView<?, ?, ?> theOwner;

		/** @param owner The element view that this attribute set is for */
		public AttributeSet(QonfigElementView<?, ?, ?> owner) {
			theOwner = owner;
		}

		@Override
		public QonfigElementView<?, ?, ?> getOwner() {
			return theOwner;
		}

		@Override
		public QonfigAttributeDef getDefinition(String member) throws IllegalArgumentException {
			QonfigAttributeDef attr = theOwner.getFocusType() == null ? null : theOwner.getFocusType().getAttribute(member);
			if (attr != null)
				return attr;
			for (QonfigElementOrAddOn type : theOwner.getTypes().values()) {
				QonfigAttributeDef typeAttr = type.getAttribute(member);
				if (typeAttr != null) {
					if (attr != null)
						throw new IllegalArgumentException("Multiple attributes in view named '" + member + "'");
					attr = typeAttr;
				}
			}
			if (attr == null)
				throw new IllegalArgumentException("No such attribute '" + member + "' in view");
			return attr;
		}

		@Override
		public QonfigAttributeDef getDefinition(QonfigElementOrAddOn owner, String member) throws IllegalArgumentException {
			QonfigAttributeDef attr = owner.getAttribute(member);
			if (attr == null)
				throw new IllegalArgumentException(
					"No such attribute '" + member + "' on " + (owner instanceof QonfigAddOn ? "add-on" : "element") + " " + owner);
			return attr;
		}

		@Override
		public AttributeView get(QonfigAttributeDef definition) {
			AttributeValue value = theOwner.getElement().getAttributes().get(definition.getDeclared());
			return new AttributeView(theOwner, definition, value);
		}

		@Override
		public String toString() {
			return getOwner().getElement().toLocatedString() + ".attributes";
		}
	}

	/**
	 * A view of an element's children
	 * 
	 * @param <E> The sub-type of {@link PartialQonfigElement} this view is for
	 * @param <X> The type of exception this view may throw if the creation of a derived view fails
	 * @param <V> The sub type of view that this view creates as sub-views
	 */
	public class ChildSet<E extends PartialQonfigElement, X extends Throwable, V extends QonfigElementView<E, X, V>>
		implements MemberSet<QonfigChildDef, BetterList<V>, ChildView<E, X, V>, X> {
		private final V theOwner;

		/** @param owner The element view that this child set is for */
		public ChildSet(V owner) {
			theOwner = owner;
		}

		@Override
		public V getOwner() {
			return theOwner;
		}

		@Override
		public QonfigChildDef getDefinition(String member) throws IllegalArgumentException {
			QonfigChildDef child = theOwner.getFocusType() == null ? null : theOwner.getFocusType().getChild(member);
			if (child != null)
				return child;
			for (QonfigElementOrAddOn type : theOwner.getTypes().values()) {
				QonfigChildDef typeChild = type.getChild(member);
				if (typeChild != null) {
					if (child != null)
						throw new IllegalArgumentException("Multiple child roles in view named '" + member + "'");
					child = typeChild;
				}
			}
			if (child == null)
				throw new IllegalArgumentException("No such child role '" + member + "' in view");
			return child;
		}

		@Override
		public QonfigChildDef getDefinition(QonfigElementOrAddOn owner, String member) throws IllegalArgumentException {
			QonfigChildDef child = owner.getChild(member);
			if (child == null)
				throw new IllegalArgumentException(
					"No such child role '" + member + "' on " + (owner instanceof QonfigAddOn ? "add-on" : "element") + " " + owner);
			return child;
		}

		@Override
		public ChildView<E, X, V> get(QonfigChildDef definition) throws IllegalArgumentException, X {
			List<E> children = getChildren(definition);
			BetterList<V> view;
			if (children.isEmpty())
				view = BetterList.empty();
			else {
				List<V> viewList = new ArrayList<>(children.size());
				for (E ch : children)
					viewList.add(getOwner().forChild(ch, Collections.singleton(definition)));
				view = BetterList.of(viewList);
			}
			return new ChildView<>(theOwner, definition, view);
		}

		/**
		 * @param child The child to get the children for
		 * @return The actual element children of this child set's owner in the given role
		 */
		protected List<E> getChildren(QonfigChildDef child) {
			return (List<E>) theOwner.getElement().getChildrenByRole().get(child.getDeclared());
		}

		@Override
		public String toString() {
			return getOwner().getElement().toLocatedString() + ".children";
		}
	}

	/**
	 * A view of an element's metadata
	 * 
	 * @param <E> The sub-type of {@link PartialQonfigElement} this view is for
	 * @param <X> The type of exception this view may throw if the creation of a derived view fails
	 * @param <V> The sub type of view that this view creates as sub-views
	 */
	public class ElementMetadata<E extends PartialQonfigElement, X extends Throwable, V extends QonfigElementView<E, X, V>>
		extends ChildSet<E, X, V> {
		/** @param owner The element view that this metadata view is for */
		public ElementMetadata(V owner) {
			super(owner);
		}

		@Override
		public QonfigChildDef getDefinition(String member) throws IllegalArgumentException {
			QonfigChildDef child = getOwner().getFocusType() == null ? null : getOwner().getFocusType().getMetaSpec().getChild(member);
			if (child != null)
				return child;
			for (QonfigElementOrAddOn type : getOwner().getTypes().values()) {
				QonfigChildDef typeChild = type.getMetaSpec().getChild(member);
				if (typeChild != null) {
					if (child != null)
						throw new IllegalArgumentException("Multiple metadata roles in view named '" + member + "'");
					child = typeChild;
				}
			}
			if (child == null)
				throw new IllegalArgumentException("No such metadata role '" + member + "' in view");
			return child;
		}

		@Override
		public QonfigChildDef getDefinition(QonfigElementOrAddOn owner, String member) throws IllegalArgumentException {
			QonfigChildDef child = owner.getMetaSpec().getChild(member);
			if (child == null)
				throw new IllegalArgumentException(
					"No such metadata role '" + member + "' on " + (owner instanceof QonfigAddOn ? "add-on" : "element") + " " + owner);
			return child;
		}

		@Override
		protected List<E> getChildren(QonfigChildDef child) {
			List<QonfigElement> metadata = new ArrayList<>();
			Set<QonfigAddOn> addOns = new HashSet<>();
			addMetadata(child, metadata, addOns, getOwner().getElement().getType());
			for (QonfigAddOn inh : getOwner().getElement().getInheritance().getExpanded(QonfigAddOn::getInheritance))
				addMetadata(child, metadata, addOns, inh);
			return (List<E>) metadata;
		}

		static void addMetadata(QonfigChildDef child, List<QonfigElement> metadata, Set<QonfigAddOn> addOns, QonfigElementOrAddOn type) {
			if (!child.getOwner().isAssignableFrom(type.getMetaSpec()))
				return;
			else if (type instanceof QonfigAddOn && !addOns.add((QonfigAddOn) type))
				return;
			if (type instanceof QonfigElementDef && type.getSuperElement() != null)
				addMetadata(child, metadata, addOns, type.getSuperElement());
			metadata.addAll(type.getMetadata().getRoot().getChildrenByRole().get(child.getDeclared()));
			for (QonfigAddOn inh : type.getInheritance())
				addMetadata(child, metadata, addOns, inh);
		}

		@Override
		public String toString() {
			return getOwner() + ".<value>";
		}
	}

	/** @return The element this view is for */
	E getElement();

	/**
	 * @return The element or add-on type that the element is being interpreted as. Attributes and children of this type may always be
	 *         referred to by name only (e.g. from {@link #getAttribute(String, Class)} or {@link #forChildren(String)}), as long as there
	 *         are no name conflicts within this element/add-on.
	 */
	QonfigElementOrAddOn getFocusType();

	/**
	 * @return All types that this session is aware (excluding the {@link #getFocusType()}) of the element being an instance of. Attributes
	 *         and children of these types may be referred to by name only (e.g. from {@link #getAttribute(String, Class)} or
	 *         {@link #forChild(PartialQonfigElement, Collection)}), as long as there are no name conflicts within these elements/add-ons.
	 */
	MultiInheritanceSet<QonfigElementOrAddOn> getTypes();

	/**
	 * @param focusType The element/add-on type to interpret the element as (the new {@link #getFocusType()} focus type)
	 * @return A session based off this session but being interpreted as the given element
	 * @throws X If the view could not be created
	 */
	default V asElement(QonfigElementOrAddOn focusType) throws X {
		if (!getElement().isInstance(focusType))
			throw new IllegalArgumentException("Element does not inherit '" + focusType + "'");
		MultiInheritanceSet<QonfigElementOrAddOn> types;
		if (getTypes().contains(focusType))
			types = getTypes();
		else {
			types = MultiInheritanceSet.create(QonfigElementOrAddOn::isAssignableFrom);
			types.add(focusType);
			types.addAll(getTypes().values());
			types = MultiInheritanceSet.unmodifiable(types);
		}
		return forChild(getElement(), focusType, types);
	}

	/**
	 * @param type The element/add-on type to interpret the element as (the new {@link #getFocusType()} focus type)
	 * @return A session based off this session, but being interpreted as the given element, with no other {@link #getTypes()} known to it
	 * @throws X If the view could not be created
	 */
	default V asElementOnly(QonfigElementOrAddOn type) throws X {
		if (!getElement().isInstance(type))
			throw new IllegalArgumentException("Element does not inherit '" + type + "'");
		return forChild(getElement(), type, MultiInheritanceSet.singleton(type, QonfigElementOrAddOn::isAssignableFrom));
	}

	/**
	 * @param toolkitDef A reference to a toolkit. This may be:
	 *        <ul>
	 *        <li>null to use the toolkit that declared this member set's owner's {@link QonfigElementView#getFocusType() focus type}</li>
	 *        <li>The name of a dependency in the focus type's toolkit</li>
	 *        <li>Of the form 'Toolkit-Name vM.m', the formal definition of a toolkit</li>
	 *        </ul>
	 * @return The resolved toolkit
	 * @throws IllegalArgumentException If the toolkit could not be resolved
	 */
	default QonfigToolkit getToolkit(String toolkitDef) throws IllegalArgumentException {
		QonfigToolkit tk;
		if (toolkitDef.indexOf(' ') < 0) { // Dependency name on the focus type's toolkit
			if (getFocusType() == null)
				throw new IllegalArgumentException("No focus type to retrieve toolkit by dependency name");
			tk = getFocusType().getDeclarer().getDependencies().get(toolkitDef);
			if (tk == null)
				throw new IllegalArgumentException("No such dependency '" + toolkitDef + "' of toolkit '" + getFocusType().getDeclarer()
					+ " of focus type " + getFocusType().getName());
			return tk;
		}
		QonfigToolkit.ToolkitDef def;
		try {
			def = QonfigToolkit.ToolkitDef.parse(toolkitDef);
		} catch (ParseException e) {
			throw new IllegalArgumentException("Bad toolkit definition: " + toolkitDef, e);
		}
		tk = getElement().getDocument().getDocToolkit().getDependenciesByDefinition()
			.getOrDefault(def.name, Collections.emptyNavigableMap())//
			.get(def);
		if (tk != null)
			return tk;
		for (QonfigElementOrAddOn type : getTypes().values()) {
			if (getFocusType().getDeclarer().dependsOn(type.getDeclarer()))
				continue;
			tk = type.getDeclarer().getDependenciesByDefinition().getOrDefault(def.name, Collections.emptyNavigableMap())//
				.get(def);
			if (tk != null)
				return tk;
		}
		throw new IllegalArgumentException("No such toolkit found in view: " + toolkitDef);
	}

	/**
	 * @param toolkit The toolkit to get the type from
	 * @param name The name of the type
	 * @return The type with the given name declared by the given toolkit or one of its dependencies
	 * @throws IllegalArgumentException If
	 *         <ul>
	 *         <li>no such type was declared by the given toolkit or any of its dependencies</li>
	 *         <li>no such type was declared by the given toolkit and multiple such types are declared by its dependencies</li>
	 *         </ul>
	 */
	default QonfigElementOrAddOn getType(QonfigToolkit toolkit, String name) {
		QonfigElementOrAddOn type = toolkit.getElementOrAddOn(name);
		if (type == null)
			throw new IllegalArgumentException("No such element '" + name + "' in toolkit " + toolkit);
		return type;
	}

	/**
	 * @param toolkitDef A reference to the toolkit declaring the type declaring the member to get the view for. This can be:
	 *        <ul>
	 *        <li>null to use the toolkit that declared this member set's owner's {@link QonfigElementView#getFocusType() focus type}</li>
	 *        <li>The name of a dependency in the focus type's toolkit</li>
	 *        <li>Of the form 'Toolkit-Name vM.m', the formal definition of a toolkit</li>
	 *        </ul>
	 * @param name The name of the type to get the definition for
	 * @return The Qonfig type of the referenced element or add-on
	 * @throws IllegalArgumentException If
	 *         <ul>
	 *         <li>The toolkit reference could not be resolved</li>
	 *         <li>No such type with the given name is declared by the given toolkit or its dependencies</li>
	 *         <li>No such type is declared by the given toolkit and multiple such types are declared by its dependencies</li>
	 *         </ul>
	 */
	default QonfigElementOrAddOn getType(String toolkitDef, String name) {
		QonfigToolkit toolkit = toolkitDef == null ? getFocusType().getDeclarer() : getToolkit(toolkitDef);
		return getType(toolkit, name);
	}

	/**
	 * @param name The name of the type to get the definition for
	 * @return The Qonfig type of the referenced element or add-on
	 * @throws IllegalArgumentException If
	 *         <ul>
	 *         <li>No such type with the given name is declared by any type visible to this view</li>
	 *         <li>No such type is declared by this view's {@link #getFocusType() focus type} and multiple such types are declared by other
	 *         types visible to this view</li>
	 *         </ul>
	 */
	default QonfigElementOrAddOn getType(String name) {
		QonfigElementOrAddOn type = getFocusType() == null ? null : getFocusType().getDeclarer().getElementOrAddOn(name);
		if (type != null)
			return type;
		for (QonfigElementOrAddOn myType : getTypes().values()) {
			if (myType.getName().equals(name)) {
				if (type != null)
					throw new IllegalArgumentException("Multiple types named '" + name + "' are applicable");
				type = myType;
			}
		}
		if (type != null)
			return type;
		for (QonfigElementOrAddOn myType : getTypes().values()) {
			QonfigElementOrAddOn found = myType.getDeclarer().getElementOrAddOn(name);
			if (found != null) {
				if (type != null)
					throw new IllegalArgumentException("Multiple types named '" + name + "' are applicable");
				type = found;
			}
		}
		if (type != null)
			return type;
		throw new IllegalArgumentException("No such element '" + name + "' in view");
	}

	/**
	 * @param toolkitDef A reference to the toolkit declaring the type declaring the member to get the view for. This can be:
	 *        <ul>
	 *        <li>null to use the toolkit that declared this member set's owner's {@link QonfigElementView#getFocusType() focus type}</li>
	 *        <li>The name of a dependency in the focus type's toolkit</li>
	 *        <li>Of the form 'Toolkit-Name vM.m', the formal definition of a toolkit</li>
	 *        </ul>
	 * @param focusTypeName The name of the element/add-on type to interpret the element as (the new {@link #getFocusType()} focus type)
	 * @return A view based off this view but being interpreted as the given element
	 * @throws IllegalArgumentException If
	 *         <ul>
	 *         <li>The toolkit reference could not be resolved</li>
	 *         <li>No such type with the given name is declared by the given toolkit or its dependencies</li>
	 *         <li>No such type is declared by the given toolkit and multiple such types are declared by its dependencies</li>
	 *         </ul>
	 * @throws X If the view could not be created
	 */
	default V asElement(String toolkitDef, String focusTypeName) throws IllegalArgumentException, X {
		return asElement(getType(toolkitDef, focusTypeName));
	}

	/**
	 * @param focusTypeName The name of the element/add-on type to interpret the element as (the new {@link #getFocusType()} focus type)
	 * @return A session based off this session but being interpreted as the given element
	 * @throws IllegalArgumentException If
	 *         <ul>
	 *         <li>No such type with the given name is declared by any type visible to this view</li>
	 *         <li>No such type is declared by this view's {@link #getFocusType() focus type} and multiple such types are declared by other
	 *         types visible to this view</li>
	 *         </ul>
	 * @throws X If the view could not be created
	 */
	default V asElement(String focusTypeName) throws IllegalArgumentException, X {
		return asElement(getType(focusTypeName));
	}

	/**
	 * @param toolkit The toolkit of the element to focus on
	 * @param focusTypeName The name of the element to focus on
	 * @return A session based off this session but being interpreted as the given element
	 * @throws IllegalArgumentException If
	 *         <ul>
	 *         <li>no such type was declared by the given toolkit or any of its dependencies</li>
	 *         <li>no such type was declared by the given toolkit and multiple such types are declared by its dependencies</li>
	 *         </ul>
	 * @throws X If the view could not be created
	 */
	default V asElement(QonfigToolkit toolkit, String focusTypeName) throws IllegalArgumentException, X {
		return asElement(getType(toolkit, focusTypeName));
	}

	/** @return A view of this element's attributes */
	default AttributeSet attributes() {
		return new AttributeSet(this);
	}

	/** @return A view of this element's value */
	default ElementValueView getValue() {
		QonfigValueDef spec = getElement().getType().getValue();
		if (spec == null)
			return null;
		return new ElementValueView(this, spec, getElement().getValue());
	}

	/** @return A view of this element's children */
	default ChildSet<E, X, V> children() {
		return new ChildSet<>((V) this);
	}

	/** @return A view of this element's metadata */
	default ChildSet<E, X, V> metadata() {
		return new ElementMetadata<>((V) this);
	}

	/**
	 * @param role The role to check
	 * @return Whether this element fulfills the given role in its parent
	 */
	default boolean fulfills(QonfigChildDef role) {
		return getElement().getDeclaredRoles().contains(role.getDeclared());
	}

	/**
	 * @param elementName The name of the element/add-on type to test
	 * @return If the element is an instance of the given type, the referenced type. Otherwise null
	 */
	default QonfigElementOrAddOn isInstance(String elementName) {
		QonfigElementOrAddOn type = getType(elementName);
		return getElement().isInstance(type) ? type : null;
	}

	/**
	 * @param toolkit The name of the toolkit defining the element to test, or null to use the definer of the {@link #getFocusType() focus
	 *        type}
	 * @param elementName The name of the element to test
	 * @return If the element is an instance of the given type, the referenced type. Otherwise null
	 * @throws IllegalArgumentException If no such element exists
	 */
	default QonfigElementOrAddOn isInstance(QonfigToolkit toolkit, String elementName) throws IllegalArgumentException {
		if (getFocusType() != null && (toolkit == null || toolkit == getFocusType().getDeclarer())
			&& getFocusType().getName().equals(elementName))
			return getFocusType();
		QonfigElementOrAddOn type = getType(toolkit == null ? getFocusType().getDeclarer() : toolkit, elementName);
		return getElement().isInstance(type) ? type : null;
	}

	// Code elimination utilities

	/**
	 * @param attribute The name of the attribute to get the text for
	 * @return The text specified for the given attribute, or null if the attribute was not specified
	 * @throws IllegalArgumentException If
	 *         <ul>
	 *         <li>no such attribute was declared for any type visible to this view</li>
	 *         <li>no attribute with the given name is declared by this view's {@link QonfigElementView#getFocusType() focus type} and
	 *         multiple attributes with the given name are declared by other visible types</li>
	 *         </ul>
	 */
	default String getAttributeText(String attribute) throws IllegalArgumentException {
		return attributes().get(attribute).getText();
	}

	/**
	 * @param <T> The type of the attribute value
	 * @param attribute The name of the attribute to get the text for
	 * @param type The type of the attribute value
	 * @return The value specified for the given attribute, or null if the attribute was not specified
	 * @throws IllegalArgumentException If
	 *         <ul>
	 *         <li>no such attribute was declared for any type visible to this view</li>
	 *         <li>no attribute with the given name is declared by this view's {@link QonfigElementView#getFocusType() focus type} and
	 *         multiple attributes with the given name are declared by other visible types</li>
	 *         </ul>
	 */
	default <T> T getAttribute(String attribute, Class<T> type) {
		return attributes().get(attribute).getValue(type);
	}

	/** @return The text specified for this element's value, or null if it was not specified */
	default String getValueText() {
		return getValue().getText();
	}

	/**
	 * @param role The name of the child role to get the children for
	 * @return A view of all children specified in this view's element for the given role
	 * @throws IllegalArgumentException If
	 *         <ul>
	 *         <li>no such role was declared for any type visible to this view</li>
	 *         <li>no role with the given name is declared by this view's {@link QonfigElementView#getFocusType() focus type} and multiple
	 *         roles with the given name are declared by other visible types</li>
	 *         </ul>
	 * @throws X If the views could not be created
	 */
	default BetterList<V> forChildren(String role) throws X {
		return children().get(role).get();
	}

	/**
	 * @return A list of interpretation sessions for each child in this element
	 * @throws X If an error occurs initializing the children's interpretation
	 */
	default BetterList<V> forAllChildren() throws X {
		List<E> children = (List<E>) getElement().getChildren();
		if (children.isEmpty())
			return BetterList.empty();
		QonfigElementView<E, X, V>[] sessions = new QonfigElementView[children.size()];
		int i = 0;
		for (E child : children) {
			sessions[i] = forChild(child, child.getParentRoles());
			i++;
		}
		return BetterList.of((V[]) sessions);
	}

	/**
	 * @param child The child element of this session's {@link #getElement()} to view
	 * @param roles The roles that the child is known to fulfill in this parent
	 * @return The interpretation session for the child element
	 * @throws X If an error occurs configuring the child view
	 */
	default V forChild(E child, Collection<? extends QonfigChildDef> roles) throws X {
		if (roles.isEmpty())
			throw new IllegalArgumentException("No roles given?");

		// Here we determine what types the child session should be aware of.
		// It should be aware of all the sub-types and inheritance from the roles in all of this session's recognized types
		// that the child fulfills.
		// It should also be aware of all auto-inherited add-ons for toolkits and roles that this session recognizes.
		QonfigElementOrAddOn focusType = null;
		MultiInheritanceSet<QonfigElementOrAddOn> types = MultiInheritanceSet.create(QonfigElementOrAddOn::isAssignableFrom);
		if (getElement().isInstance(roles.iterator().next().getOwner())) { // could be metadata
			// Add auto-inheritance from recognized toolkits and roles
			MultiInheritanceSet<QonfigToolkit> toolkits = MultiInheritanceSet.create(QonfigToolkit::dependsOn);
			if (getFocusType() != null)
				toolkits.add(getFocusType().getDeclarer());
			for (QonfigElementOrAddOn type : getTypes().values())
				toolkits.add(type.getDeclarer());
			QonfigAutoInheritance.Compiler autoInheritance = new QonfigAutoInheritance.Compiler(toolkits.values());
			autoInheritance.addParentType(getElement().getType(), types::add);
			for (QonfigAddOn inh : getElement().getInheritance().values())
				autoInheritance.addParentType(inh, types::add);
			for (QonfigElementOrAddOn type : getTypes().values()) {
				autoInheritance.addParentType(type, types::add);
				for (QonfigAddOn inh : type.getInheritance())
					autoInheritance.addParentType(inh, types::add);
			}
			for (QonfigChildDef role : roles) {
				if (role.getType() != null) {
					if (focusType == null || focusType.isAssignableFrom(role.getType()))
						focusType = role.getType();
				}
				if (focusType == null) {
					for (QonfigAddOn inh : role.getInheritance()) {
						if (focusType == null || focusType.isAssignableFrom(role.getType()))
							focusType = inh;
						types.add(inh);
					}
				}
				autoInheritance.addRole(role.getDeclared(), types::add);
			}
		} else {
			for (QonfigChildDef role : roles) {
				if (role.getType() != null) {
					if (focusType == null || focusType.isAssignableFrom(role.getType()))
						focusType = role.getType();
					types.add(role.getType());
				}
				for (QonfigAddOn inh : role.getInheritance()) {
					if (focusType == null || focusType.isAssignableFrom(role.getType()))
						focusType = inh;
					types.add(inh);
				}
			}
		}
		types = MultiInheritanceSet.unmodifiable(types);
		return forChild(child, focusType, types);
	}

	/**
	 * @param child The child element to create the view for
	 * @param focusType The focus type for the child
	 * @param types The types that the child is known to inherit
	 * @return The view for the child element
	 * @throws X If an error occurs configuring the child view
	 */
	V forChild(E child, QonfigElementOrAddOn focusType, MultiInheritanceSet<QonfigElementOrAddOn> types) throws X;

	/**
	 * @param element The element to create the view for
	 * @return A default view of the given element with the element's type as its {@link #getFocusType() focus type}
	 */
	static Default<?> of(QonfigElement element) {
		return new Default<>(element);
	}

	/**
	 * @param element The element to create the view for
	 * @return A default view of the given element with the element's type as its {@link #getFocusType() focus type}
	 */
	static DefaultPartial<?> of(PartialQonfigElement element) {
		return new DefaultPartial<>(element);
	}

	/**
	 * An abstract {@link QonfigElementView} implementation that doesn't ever fail to create views
	 * 
	 * @param <E> The sub-type of {@link PartialQonfigElement} this view is for
	 * @param <V> The sub type of view that this view creates as sub-views
	 */
	static abstract class Abstract<E extends PartialQonfigElement, V extends Abstract<E, V>>
		implements QonfigElementView<E, RuntimeException, V> {
		private final E theElement;
		private final QonfigElementOrAddOn theFocusType;
		private final MultiInheritanceSet<QonfigElementOrAddOn> theTypes;

		protected Abstract(E element, QonfigElementOrAddOn focusType, MultiInheritanceSet<QonfigElementOrAddOn> types) {
			theElement = element;
			theFocusType = focusType;
			theTypes = types;
		}

		@Override
		public E getElement() {
			return theElement;
		}

		@Override
		public QonfigElementOrAddOn getFocusType() {
			return theFocusType;
		}

		@Override
		public MultiInheritanceSet<QonfigElementOrAddOn> getTypes() {
			return theTypes;
		}
	}

	/**
	 * A default {@link QonfigElementView} implementation for full {@link QonfigElement}s
	 * 
	 * @param <V> The sub type of view that this view creates as sub-views
	 */
	static class Default<V extends Default<V>> extends Abstract<QonfigElement, V> {
		public Default(QonfigElement element) {
			this(element, element.getType(), MultiInheritanceSet.singleton(element.getType(), QonfigElementOrAddOn::isAssignableFrom));
		}

		public Default(QonfigElement element, QonfigElementOrAddOn focusType, MultiInheritanceSet<QonfigElementOrAddOn> types) {
			super(element, focusType, types);
		}

		@Override
		public V forChild(QonfigElement element, QonfigElementOrAddOn focusType, MultiInheritanceSet<QonfigElementOrAddOn> types) {
			return (V) new Default<>(element, focusType, types);
		}
	}

	/**
	 * A default {@link QonfigElementView} implementation for partial {@link PartialQonfigElement}s
	 * 
	 * @param <V> The sub type of view that this view creates as sub-views
	 */
	static class DefaultPartial<V extends DefaultPartial<V>> extends Abstract<PartialQonfigElement, V> {
		public DefaultPartial(PartialQonfigElement element) {
			this(element, element.getType(), MultiInheritanceSet.singleton(element.getType(), QonfigElementOrAddOn::isAssignableFrom));
		}

		public DefaultPartial(PartialQonfigElement element, QonfigElementOrAddOn focusType,
			MultiInheritanceSet<QonfigElementOrAddOn> types) {
			super(element, focusType, types);
		}

		@Override
		public V forChild(PartialQonfigElement element, QonfigElementOrAddOn focusType, MultiInheritanceSet<QonfigElementOrAddOn> types) {
			return (V) new DefaultPartial<>(element, focusType, types);
		}
	}
}
