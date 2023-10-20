package org.qommons.config;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.qommons.MultiInheritanceSet;
import org.qommons.collect.BetterList;
import org.qommons.config.QonfigElement.AttributeValue;
import org.qommons.config.QonfigElement.QonfigValue;
import org.qommons.io.FilePosition;
import org.qommons.io.LocatedFilePosition;
import org.qommons.io.LocatedPositionedContent;
import org.qommons.io.PositionedContent;

public interface QonfigElementView<E extends PartialQonfigElement, X extends Throwable, V extends QonfigElementView<E, X, V>> {
	public interface MemberView<M extends QonfigElementOwned, V> {
		QonfigElementView<?, ?, ?> getOwner();

		M getDefinition();

		V get();
	}

	public class ValueMember<M extends QonfigValueDef, V extends QonfigValue> implements MemberView<M, V> {
		private final QonfigElementView<?, ?, ?> theOwner;
		private final M theDefinition;
		private final V theValue;

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

		public Object getValue() {
			QonfigValue value = get();
			return value == null ? null : value.value;
		}

		public String getText() {
			QonfigValue value = get();
			return value == null ? null : value.text;
		}

		public QonfigValueType getType() {
			return getDefinition().getType();
		}

		public <T> T getValue(Class<T> type) {
			return getValue(type, null);
		}

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

		public PositionedContent getContent() {
			QonfigValue value = get();
			return value == null ? null : value.position;
		}

		public LocatedPositionedContent getLocatedContent() {
			QonfigValue value = get();
			return value == null ? null : LocatedPositionedContent.of(value.fileLocation, value.position);
		}

		public FilePosition getPosition() {
			QonfigValue value = get();
			return value == null ? null : value.position.getPosition(0);
		}

		public LocatedFilePosition getLocatedPosition() {
			QonfigValue value = get();
			return value == null ? null : LocatedFilePosition.of(value.fileLocation, value.position.getPosition(0));
		}
	}

	public class AttributeView extends ValueMember<QonfigAttributeDef, AttributeValue> {
		public AttributeView(QonfigElementView<?, ?, ?> owner, QonfigAttributeDef attribute, AttributeValue value) {
			super(owner, attribute, value);
		}

		public PositionedContent getName() {
			return get().getNamePosition();
		}

		public LocatedPositionedContent getLocatedName() {
			return LocatedPositionedContent.of(get().fileLocation, get().getNamePosition());
		}

		public FilePosition getNameStart() {
			return getName().getPosition(0);
		}

		public LocatedFilePosition getLocatedNameStart() {
			return getLocatedName().getPosition(0);
		}

		@Override
		public String toString() {
			return getOwner().getElement().toLocatedString() + "." + getDefinition().getName();
		}
	}

	public class ElementValueView extends ValueMember<QonfigValueDef, QonfigValue> {
		public ElementValueView(QonfigElementView<?, ?, ?> owner, QonfigValueDef definition, QonfigValue value) {
			super(owner, definition, value);
		}
	}

	public class ChildView<E extends PartialQonfigElement, X extends Throwable, V extends QonfigElementView<E, X, V>>
		implements MemberView<QonfigChildDef, BetterList<V>> {
		private final V theOwner;
		private final QonfigChildDef theChild;
		private final BetterList<V> theChildren;

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

	public interface MemberSet<D extends QonfigElementOwned, V, M extends MemberView<?, V>, X extends Throwable> {
		QonfigElementView<?, ?, ?> getOwner();

		D getDefinition(QonfigElementOrAddOn owner, String member) throws IllegalArgumentException;

		D getDefinition(String member) throws IllegalArgumentException;

		M get(D definition) throws X;

		default M get(QonfigElementOrAddOn owner, String member) throws IllegalArgumentException, X {
			return get(getDefinition(owner, member));
		}

		default M get(QonfigToolkit toolkit, String type, String member) throws IllegalArgumentException, X {
			return get(getOwner().getType(toolkit, type), member);
		}

		default M get(String toolkitDef, String type, String member) throws IllegalArgumentException, X {
			return get(getOwner().getType(toolkitDef, type), member);
		}

		default M get(String owner, String member) throws IllegalArgumentException, X {
			return get(getOwner().getType(owner), member);
		}

		default M get(String member) throws IllegalArgumentException, X {
			return get(getDefinition(member));
		}
	}

	public class AttributeSet implements MemberSet<QonfigAttributeDef, AttributeValue, AttributeView, RuntimeException> {
		private final QonfigElementView<?, ?, ?> theOwner;

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

	public class ChildSet<E extends PartialQonfigElement, X extends Throwable, V extends QonfigElementView<E, X, V>>
		implements MemberSet<QonfigChildDef, BetterList<V>, ChildView<E, X, V>, X> {
		private final V theOwner;

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

		protected List<E> getChildren(QonfigChildDef child) {
			return (List<E>) theOwner.getElement().getChildrenByRole().get(child.getDeclared());
		}

		@Override
		public String toString() {
			return getOwner().getElement().toLocatedString() + ".children";
		}
	}

	public class ElementMetadata<E extends PartialQonfigElement, X extends Throwable, V extends QonfigElementView<E, X, V>>
		extends ChildSet<E, X, V> {
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
	 *         referred to by name only (e.g. from {@link #getAttribute(String, Class)} or {@link #interpretChildren(String, Class)}), as
	 *         long as there are no name conflicts within this element/add-on.
	 */
	QonfigElementOrAddOn getFocusType();

	/**
	 * @return All types that this session is aware (excluding the {@link #getFocusType()}) of the element being an instance of. Attributes
	 *         and children of these types may be referred to by name only (e.g. from {@link #getAttribute(String, Class)} or
	 *         {@link #interpretChildren(String, Class)}), as long as there are no name conflicts within these elements/add-ons.
	 */
	MultiInheritanceSet<QonfigElementOrAddOn> getTypes();

	/**
	 * @param focusType The element/add-on type to interpret the element as (the new {@link #getFocusType()} focus type)
	 * @return A session based off this session but being interpreted as the given element
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
	 */
	default V asElementOnly(QonfigElementOrAddOn type) throws X {
		if (!getElement().isInstance(type))
			throw new IllegalArgumentException("Element does not inherit '" + type + "'");
		return forChild(getElement(), type, MultiInheritanceSet.singleton(type, QonfigElementOrAddOn::isAssignableFrom));
	}

	default QonfigToolkit getToolkit(String toolkitDef) {
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
		tk = getFocusType().getDeclarer().getDependenciesByDefinition().getOrDefault(def.name, Collections.emptyNavigableMap())//
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

	default QonfigElementOrAddOn getType(QonfigToolkit toolkit, String name) {
		QonfigElementOrAddOn type = toolkit.getElementOrAddOn(name);
		if (type == null)
			throw new IllegalArgumentException("No such element '" + name + "' in toolkit " + toolkit);
		return type;
	}

	default QonfigElementOrAddOn getType(String toolkitDef, String name) {
		QonfigToolkit toolkit = toolkitDef == null ? getFocusType().getDeclarer() : getToolkit(toolkitDef);
		return getType(toolkit, name);
	}

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
	 * @param focusTypeName The name of the element/add-on type to interpret the element as (the new {@link #getFocusType()} focus type)
	 * @return A session based off this session but being interpreted as the given element
	 */
	default V asElement(String toolkitDef, String focusTypeName) throws X {
		return asElement(getType(toolkitDef, focusTypeName));
	}

	/**
	 * @param focusTypeName The name of the element/add-on type to interpret the element as (the new {@link #getFocusType()} focus type)
	 * @return A session based off this session but being interpreted as the given element
	 */
	default V asElement(String focusTypeName) throws X {
		return asElement(getType(focusTypeName));
	}

	/**
	 * @param toolkit The toolkit of the element to focus on
	 * @param focusTypeName The name of the element to focus on
	 * @return A session based off this session but being interpreted as the given element
	 */
	default V asElement(QonfigToolkit toolkit, String focusTypeName) throws X {
		return asElement(getType(toolkit, focusTypeName));
	}

	default AttributeSet attributes() {
		return new AttributeSet(this);
	}

	default ElementValueView getValue() {
		QonfigValueDef spec = getElement().getType().getValue();
		if (spec == null)
			return null;
		return new ElementValueView(this, spec, getElement().getValue());
	}

	default ChildSet<E, X, V> children() {
		return new ChildSet<>((V) this);
	}

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

	default String getAttributeText(String attribute) {
		return attributes().get(attribute).getText();
	}

	default <T> T getAttribute(String attribute, Class<T> type) {
		return attributes().get(attribute).getValue(type);
	}

	default String getValueText() {
		return getValue().getText();
	}

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

	V forChild(E child, QonfigElementOrAddOn focusType, MultiInheritanceSet<QonfigElementOrAddOn> types) throws X;

	static Default<?> of(QonfigElement element) {
		return new Default<>(element);
	}

	static DefaultPartial<?> of(PartialQonfigElement element) {
		return new DefaultPartial<>(element);
	}

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
