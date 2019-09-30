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

	static Object wrap(Object otherId, String op, Object... params) {
		return new WrappingIdentity(otherId, op, params);
	}

	static Object baseId(String descrip, Object obj) {
		return new BaseIdentity(descrip, obj);
	}

	static Object idFor(Object source, Supplier<String> descrip, IntSupplier hashCode, Predicate<Object> equals) {
		return new SpecialIdentity(source, descrip, hashCode, equals);
	}

	public class BaseIdentity {
		private final String theDescrip;
		private final Object theObject;
		private int hashCode;

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

	public class SpecialIdentity {
		private final Object theSource;
		private final Supplier<String> theDescription;
		private final IntSupplier theHashCode;
		private final Predicate<Object> theEquals;

		private int theConcreteHashCode;
		private String theConcreteDescription;

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

	public class WrappingIdentity {
		private final Object theWrappedId;
		private final String theOp;
		private final Object[] theParams;
		private int hashCode;

		public WrappingIdentity(Object wrappedId, String op, Object[] params) {
			theWrappedId = wrappedId;
			theOp = op;
			theParams = params;
			hashCode = -1;
		}

		public Object getWrappedId() {
			return theWrappedId;
		}

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
}
