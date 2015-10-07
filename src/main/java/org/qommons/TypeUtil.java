package org.qommons;

/** Utility methods for types */
public class TypeUtil {
	/**
	 * @param wrapper The wrapper for a primitive type
	 * @return The unwrapped primitive type, or null if the given type is not primitive or a primitive wrapper
	 */
	public static Class<?> getPrimitiveType(Class<?> wrapper) {
		if(wrapper.isPrimitive())
			return wrapper;
		if(wrapper.equals(Double.class))
			return Double.TYPE;
		else if(wrapper.equals(Float.class))
			return Float.TYPE;
		else if(wrapper.equals(Long.class))
			return Long.TYPE;
		else if(wrapper.equals(Integer.class))
			return Integer.TYPE;
		else if(wrapper.equals(Character.class))
			return Character.TYPE;
		else if(wrapper.equals(Short.class))
			return Short.TYPE;
		else if(wrapper.equals(Byte.class))
			return Byte.TYPE;
		else if(wrapper.equals(Boolean.class))
			return Boolean.TYPE;
		else
			return null;
	}

	/**
	 * @param primitive The primitive type
	 * @return The wrapped primitive type, or null if the given type is not primitive or a primitive wrapper
	 */
	public static Class<?> getWrapperType(Class<?> primitive) {
		if(primitive.equals(Double.TYPE))
			return Double.class;
		else if(primitive.equals(Float.TYPE))
			return Float.class;
		else if(primitive.equals(Long.TYPE))
			return Long.class;
		else if(primitive.equals(Integer.TYPE))
			return Integer.class;
		else if(primitive.equals(Character.TYPE))
			return Character.class;
		else if(primitive.equals(Short.TYPE))
			return Short.class;
		else if(primitive.equals(Byte.TYPE))
			return Byte.class;
		else if(primitive.equals(Boolean.TYPE))
			return Boolean.class;
		else if(getPrimitiveType(primitive) != null)
			return primitive;
		else
			return null;
	}

	/**
	 * @param type The type to check
	 * @return Whether instances of the type can be used in floating-point math operations
	 */
	public static boolean isMathable(Class<?> type) {
		Class<?> prim = getPrimitiveType(type);
		if(prim == null)
			return false;
		return prim == Double.TYPE || prim == Float.TYPE || prim == Long.TYPE || prim == Integer.TYPE || prim == Short.TYPE
			|| prim == Byte.TYPE || prim == Character.TYPE;
	}

	/**
	 * @param type The type to check
	 * @return Whether instances of the type can be used in integer-only math operations
	 */
	public static boolean isIntMathable(Class<?> type) {
		Class<?> prim = getPrimitiveType(type);
		if(prim == null)
			return false;
		return prim == Long.TYPE || prim == Integer.TYPE || prim == Short.TYPE || prim == Byte.TYPE || prim == Character.TYPE;
	}
}
