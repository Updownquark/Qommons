package org.qommons;

import java.util.Objects;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

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

		@Override
		public int hashCode() {
			return getIdentity().hashCode();
		}

		@Override
		public boolean equals(Object obj) {
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
			StringBuilder str = new StringBuilder().append(theWrappedId).append('.').append(theOp).append('(');
			for (int i = 0; i < theParams.length; i++) {
				if (i > 0)
					str.append(",");
				str.append(theParams[i]);
			}
			str.append(')');
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
}
