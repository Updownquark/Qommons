package org.qommons;

import java.util.*;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.qommons.fn.FunctionUtils;

/**
 * <p>
 * An object provides visibility into its identity. Objects with the same identity are guaranteed to provide the same value(s) from its
 * interface(s).
 * </p>
 * 
 * <p>
 * The identity of an object should also implement {@link Object#toString()} to provide a human-readable representation of where its value
 * comes from.
 * </p>
 */
public interface Identifiable {
	/** @return A representation of this object's identity */
	Object getIdentity();

	/**
	 * Adds an alias to this identifiable. This may be used in the toString().
	 * 
	 * @param alias The alias for this identifiable
	 * @return This object
	 */
	Identifiable alias(String alias);

	/** @return All aliases {@link #alias(String) added} to this identifiable */
	Set<String> getAliases();

	/**
	 * Creates an identity object for an object whose identity depends upon one or more other identities
	 * 
	 * @param otherId The primary identity object to wrap
	 * @param op The name of the operation on the primary object
	 * @param params Other objects that are components of the identity
	 * @return The new identity
	 */
	static Object wrap(Object otherId, String op, Object... params) {
		return new WrappingIdentity(otherId, op, params);
	}

	/**
	 * Creates an identity for an object that does not depend on other identities
	 * 
	 * @param descrip The description of the object
	 * @param obj The identity object
	 * @return The new identity
	 */
	static Object baseId(String descrip, Object obj) {
		return new BaseIdentity(descrip, obj);
	}

	/**
	 * Creates an identity for an object with custom hash and equals
	 * 
	 * @param source The identity object
	 * @param descrip Describes the operation
	 * @param hashCode Computes the hash code
	 * @param equals Tests for equals against sources
	 * @return The new identity
	 */
	static Object idFor(Object source, Supplier<String> descrip, IntSupplier hashCode, Predicate<Object> equals) {
		return new SpecialIdentity(source, descrip, hashCode, equals);
	}

	/** @return A builder for a custom ID object */
	static CustomIdentityBuilder buildId() {
		return new CustomIdentityBuilder();
	}

	/** An abstract Identifiable implementation that caches its identity object */
	public abstract class AbstractIdentifiable implements Identifiable {
		private Object theIdentity;

		/** @return The identity for this object */
		protected abstract Object createIdentity();

		@Override
		public Object getIdentity() {
			if (theIdentity == null)
				theIdentity = createIdentity();
			return theIdentity;
		}

		/**
		 * Initializes the identity of this identifiable. If this is called before {@link #getIdentity()}, {@link #getIdentity()} will
		 * return the given object. Otherwise, this will have no effect.
		 * 
		 * @param identity The identity for this object
		 * @return Whether the initialization was successful such that the given identity will be returned from {@link #getIdentity()}.
		 *         False if the identity had previously been initialized.
		 */
		protected boolean initIdentity(Object identity) {
			if (theIdentity == null) {
				theIdentity = identity;
				return true;
			} else
				return false;
		}

		@Override
		public Identifiable alias(String alias) {
			theIdentity = AliasedIdentity.alias(getIdentity(), alias);
			return this;
		}

		@Override
		public Set<String> getAliases() {
			return AliasedIdentity.getAliases(theIdentity);
		}

		@Override
		public int hashCode() {
			return getIdentity().hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			return obj instanceof Identifiable && getIdentity().equals(((Identifiable) obj).getIdentity());
		}

		@Override
		public String toString() {
			return getIdentity().toString();
		}
	}

	/** Implements {@link Identifiable#wrap(Object, String, Object...)} */
	public class WrappingIdentity {
		private final Object theWrappedId;
		private final String theOp;
		private final Object[] theParams;
		private int hashCode;

		/**
		 * @param wrappedId The primary identity object to wrap
		 * @param op The name of the operation on the primary object
		 * @param params Other objects that are components of the identity
		 */
		public WrappingIdentity(Object wrappedId, String op, Object[] params) {
			theWrappedId = wrappedId;
			theOp = op;
			theParams = params;
			hashCode = -1;
		}

		/** @return The primary identity object to wrap */
		public Object getWrappedId() {
			return theWrappedId;
		}

		/** @return The name of the operation on the primary object */
		public String getOp() {
			return theOp;
		}

		@Override
		public int hashCode() {
			if (hashCode == -1)
				hashCode = Objects.hash(theWrappedId, theOp);
			return hashCode;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (!(obj instanceof WrappingIdentity))
				return false;
			WrappingIdentity other = (WrappingIdentity) obj;
			if (!theWrappedId.equals(other.theWrappedId) || !theOp.equals(other.theOp) || theParams.length != other.theParams.length)
				return false;
			for (int i = 0; i < theParams.length; i++)
				if (!Objects.equals(theParams[i], other.theParams[i]))
					return false;
			return true;
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder().append(theWrappedId);
			if (!theOp.isEmpty())
				str.append('.').append(theOp);
			if (theParams.length > 0) {
				str.append('(');
				for (int i = 0; i < theParams.length; i++) {
					if (i > 0)
						str.append(",");
					str.append(theParams[i]);
				}
				str.append(')');
			}
			return str.toString();
		}
	}

	/** Implements {@link Identifiable#baseId(String, Object)} */
	public class BaseIdentity {
		private final String theDescrip;
		private final Object theObject;
		private int hashCode;

		/**
		 * @param descrip The description of the object
		 * @param object The identity object
		 */
		public BaseIdentity(String descrip, Object object) {
			theDescrip = descrip;
			theObject = object;
			hashCode = -1;
		}

		@Override
		public int hashCode() {
			if (hashCode == -1)
				hashCode = System.identityHashCode(theObject);
			return hashCode;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			return obj instanceof BaseIdentity && ((BaseIdentity) obj).theObject == theObject;
		}

		@Override
		public String toString() {
			return theDescrip;
		}
	}

	/** Implements {@link Identifiable#idFor(Object, Supplier, IntSupplier, Predicate)} */
	public class SpecialIdentity {
		private final Object theSource;
		private final Supplier<String> theDescription;
		private final IntSupplier theHashCode;
		private final Predicate<Object> theEquals;

		private int theConcreteHashCode;
		private String theConcreteDescription;

		/**
		 * @param source The identity object
		 * @param descrip Describes the operation
		 * @param hashCode Computes the hash code
		 * @param equals Tests for equals against sources
		 */
		public SpecialIdentity(Object source, Supplier<String> descrip, IntSupplier hashCode, Predicate<Object> equals) {
			theSource = source;
			theDescription = descrip;
			theHashCode = hashCode;
			theEquals = equals;

			theConcreteHashCode = -1;
		}

		@Override
		public int hashCode() {
			if (theConcreteHashCode == -1)
				theConcreteHashCode = theHashCode.getAsInt();
			return theConcreteHashCode;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else if (!(obj instanceof SpecialIdentity))
				return false;
			return theEquals.test(((SpecialIdentity) obj).theSource);
		}

		@Override
		public String toString() {
			if (theConcreteDescription == null)
				theConcreteDescription = theDescription.get();
			return theConcreteDescription;
		}
	}

	/**
	 * Builds a custom identity. This builder gives the developer a great deal of customizability for how the identity will compare itself
	 * with other objects and how it will render with {@link Object#toString()}.
	 */
	public class CustomIdentityBuilder {
		private final List<Supplier<?>> theIdentityComponents;
		private final List<Supplier<?>> theToStringComponents;

		CustomIdentityBuilder() {
			theIdentityComponents = new ArrayList<>();
			theToStringComponents = new ArrayList<>();
		}

		/**
		 * Adds a supplier for an object that will contribute to the object's identity (with {@link Object#hashCode()} and
		 * {@link Object#equals(Object)}). This call does not affect the identity's {@link Object#toString()}.
		 * 
		 * @param id A supplier for a value that will be a component of the object's identity
		 * @return This builder
		 */
		public CustomIdentityBuilder idS(Supplier<?> id) {
			if (id == null)
				throw new NullPointerException();
			theIdentityComponents.add(id);
			return this;
		}

		/**
		 * Adds an object that will contribute to the object's identity (with {@link Object#hashCode()} and {@link Object#equals(Object)}).
		 * This call does not affect the identity's {@link Object#toString()}.
		 * 
		 * @param id A value that will be a component of the object's identity
		 * @return This builder
		 */
		public CustomIdentityBuilder id(Object id) {
			theIdentityComponents.add(FunctionUtils.constantSupplier(id));
			return this;
		}

		/**
		 * Adds a supplier for an object that will be appended to the identity's {@link Object#toString()}, but will not affect its identity
		 * ({@link Object#hashCode()} or {@link Object#equals(Object)}).
		 * 
		 * @param toString A supplier for a value that will be a component of the object's {@link Object#toString()}
		 * @return This builder
		 */
		public CustomIdentityBuilder appendS(Supplier<?> toString) {
			if (toString == null)
				throw new NullPointerException();
			theToStringComponents.add(toString);
			return this;
		}

		/**
		 * Adds an object that will be appended to the identity's {@link Object#toString()}, but will not affect its identity
		 * ({@link Object#hashCode()} or {@link Object#equals(Object)}).
		 * 
		 * @param toString A value that will be a component of the object's {@link Object#toString()}
		 * @return This builder
		 */
		public CustomIdentityBuilder append(Object toString) {
			theToStringComponents.add(FunctionUtils.constantSupplier(toString));
			return this;
		}

		/**
		 * Adds a supplier for an object that will contribute to the object's identity (with {@link Object#hashCode()} and
		 * {@link Object#equals(Object)}) as well as being appended to its {@link Object#toString()}.
		 * 
		 * @param id A supplier for a value that will be a component of the object's identity and {@link Object#toString()}
		 * @return This builder
		 */
		public CustomIdentityBuilder withPrintedIdS(Supplier<?> id) {
			if (id == null)
				throw new NullPointerException();
			theIdentityComponents.add(id);
			theToStringComponents.add(id);
			return this;
		}

		/**
		 * Adds an object that will contribute to the object's identity (with {@link Object#hashCode()} and {@link Object#equals(Object)})
		 * as well as being appended to its {@link Object#toString()}.
		 * 
		 * @param id A value that will be a component of the object's identity and {@link Object#toString()}
		 * @return This builder
		 */
		public CustomIdentityBuilder withPrintedId(Object id) {
			return withPrintedIdS(FunctionUtils.constantSupplier(id));
		}

		/** @return The built identity object */
		public CustomIdentity build() {
			if (theIdentityComponents.isEmpty() || theToStringComponents.isEmpty())
				throw new IllegalStateException("A custom identity must include both identity and toString components");
			return new CustomIdentity(theIdentityComponents.toArray(new Supplier[theIdentityComponents.size()]),
				theToStringComponents.toArray(new Supplier[theToStringComponents.size()]));
		}
	}

	/** A custom identity built from a {@link CustomIdentityBuilder} */
	public class CustomIdentity {
		private final Supplier<?>[] theIdentityComponents;
		private final Supplier<?>[] theToStringComponents;

		CustomIdentity(Supplier<?>[] identityComponents, Supplier<?>[] toStringComponents) {
			theIdentityComponents = identityComponents;
			theToStringComponents = toStringComponents;
		}

		@Override
		public int hashCode() {
			int result = 1;

			for (Supplier<?> component : theIdentityComponents) {
				Object element = component.get();
				result = 31 * result + (element == null ? 0 : element.hashCode());
			}

			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else if (!(obj instanceof CustomIdentity))
				return false;
			CustomIdentity other = (CustomIdentity) obj;
			if (theIdentityComponents.length != other.theIdentityComponents.length)
				return false;
			for (int i = 0; i < theIdentityComponents.length; i++) {
				if (!Objects.equals(theIdentityComponents[i].get(), other.theIdentityComponents[i].get()))
					return false;
			}
			return true;
		}

		@Override
		public String toString() {
			if (theIdentityComponents.length == 1)
				return String.valueOf(theIdentityComponents[0].get());
			StringBuilder str = new StringBuilder();
			for (Supplier<?> component : theToStringComponents)
				str.append(component.get());
			return str.toString();
		}
	}

	/** An identity object that has aliases. The aliases do not affect its identity, but only its {@link #toString()}. */
	public class AliasedIdentity {
		private final Object theIdentity;
		private final Set<String> theAliases;

		private AliasedIdentity(Object identity, String alias) {
			theIdentity = identity;
			theAliases = new LinkedHashSet<>(3);
			theAliases.add(alias);
		}

		/** @return All aliases added to this identity */
		public Set<String> getAliases() {
			return Collections.unmodifiableSet(theAliases);
		}

		@Override
		public int hashCode() {
			return theIdentity.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof AliasedIdentity)
				return theIdentity.equals(((AliasedIdentity) obj).theIdentity);
			else
				return theIdentity.equals(obj);
		}

		@Override
		public String toString() {
			return theAliases.iterator().next();
		}

		/**
		 * @param identity The identity to alias
		 * @param alias The alias to add
		 * @return The aliased identity
		 */
		public static AliasedIdentity alias(Object identity, String alias) {
			if (identity instanceof AliasedIdentity) {
				AliasedIdentity aliased = (AliasedIdentity) identity;
				synchronized (aliased) {
					aliased.theAliases.add(alias);
				}
				return aliased;
			} else
				return new AliasedIdentity(identity, alias);
		}

		/**
		 * @param identity The identity to get the aliases of
		 * @return The aliases of the identity, if it is an instance of this class. The empty set otherwise.
		 */
		public static Set<String> getAliases(Object identity) {
			if (identity instanceof AliasedIdentity)
				return ((AliasedIdentity) identity).getAliases();
			else
				return Collections.emptySet();
		}
	}
}
