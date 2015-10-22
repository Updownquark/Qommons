package org.qommons;

import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * <p>
 * A utility for parsing command-line arguments passed to a program.
 * </p>
 * <p>
 * To use this class, instantiate an {@link ArgumentParser}. Then, create a pattern. The {@link ArgumentParser#forDefaultPattern() default}
 * </p>
 * <p>
 * The results of parsing are an {@link Arguments} object, which is a useful way of getting and further parsing individual arguments.
 * </p>
 * <p>
 * See the source of {@link #sample(String[])} for more help parsing arguments.
 * </p>
 */
public class ArgumentParsing {
	/** Creates argument definitions */
	public interface ArgumentDefCreator {
		/**
		 * Creates a string-type argument
		 *
		 * @param name The name of the argument
		 * @return The new argument definition
		 */
		StringArgumentDef stringArg(String name);
		/**
		 * Creates a string-type argument constrained to a pattern
		 *
		 * @param name The name of the argument
		 * @param pattern The pattern that the values of the argument must match
		 * @return The new argument definition
		 */
		default PatternArgumentDef patternArg(String name, String pattern) {
			return patternArg(name, Pattern.compile(pattern));
		}
		/**
		 * Creates a string-type argument constrained to a pattern
		 *
		 * @param name The name of the argument
		 * @param pattern The pattern that the values of the argument must match
		 * @return The new argument definition
		 */
		PatternArgumentDef patternArg(String name, Pattern pattern);

		/**
		 * Creates an integer-typed argument
		 *
		 * @param name The name of the argument
		 * @return The new argument definition
		 */
		IntArgumentDef intArg(String name);
		/**
		 * Creates an floating-point-typed argument
		 *
		 * @param name The name of the argument
		 * @return The new argument definition
		 */
		FloatArgumentDef floatArg(String name);

		/**
		 * Creates an enum-type argument
		 *
		 * @param <E> The (compile-time) enum type for the argument
		 * @param name The name of the argument
		 * @param type The enum type for the argument
		 * @return The new argument definition
		 */
		<E extends Enum<E>> EnumArgumentDef<E> enumArg(String name, Class<E> type);

		/**
		 * Creates a boolean-type argument
		 *
		 * @param name The name of the argument
		 * @return The new argument definition
		 */
		BooleanArgumentDef booleanArg(String name);
		/**
		 * Creates a flag argument
		 *
		 * @param name The name of the argument
		 * @return The new argument definition
		 */
		FlagArgumentDef flagArg(String name);
	}

	/**
	 * Represents an argument definition
	 *
	 * @param <A> The sub-type of the argument definition
	 */
	public interface ArgumentDef<A extends ArgumentDef<A>> extends ArgumentDefCreator {
		/** @return This argument's name */
		String getName();

		/** @return The argument definition set that this argument is a part of */
		ArgumentDefSet getArgDefSet();

		/**
		 * Sets constraints on the number of times this argument can occur in an argument set
		 *
		 * @param min The minimum number of times the argument can occur
		 * @param max The maximum number of times the argument can occur
		 * @return This argument, for chaining
		 */
		A times(int min, int max);

		/** @return The minimum number of times the argument can occur in an argument set */
		int getMinTimes();
		/** @return The maximum number of times the argument can occur in an argument set */
		int getMaxTimes();

		/**
		 * @param constraint The constraint to add to this argument
		 * @return This argument, for chaining
		 */
		A addConstraint(ArgumentConstraint constraint);
		/** @return All constraints on this argument */
		Collection<ArgumentConstraint> getConstraints();

		/**
		 * Asserts that this argument cannot be present without another argument
		 *
		 * @param targetArgName The name of the argument to require if an argument of this type is present
		 * @return This argument, for chaining
		 */
		default A requires(String targetArgName){
			return addConstraint(new ArgumentConstraint(this, targetArgName, false, true));
		}
		/**
		 * Asserts that this argument cannot be present without a specific value of another argument
		 *
		 * @param targetArgName The name of the argument to require if an argument of this type is present
		 * @param targetValue The value that the argument must have if an argument of this type is present
		 * @return This argument, for chaining
		 */
		default A requires(String targetArgName, Object targetValue){
			return addConstraint(new ArgumentConstraint(this, targetArgName, targetValue, false, true));
		}
		/**
		 * Asserts that this argument cannot be present without a the value of another argument passing a test
		 *
		 * @param targetArgName The name of the argument to require if an argument of this type is present
		 * @param targetValueCheck The check that the value of the argument must pass if an argument of this type is present
		 * @return This argument, for chaining
		 */
		default A requires(String targetArgName, Predicate<Object> targetValueCheck){
			return addConstraint(new ArgumentConstraint(this, targetArgName, targetValueCheck, false, true));
		}

		/**
		 * Asserts that this argument cannot be present if another argument is present
		 *
		 * @param targetArgName The name of the argument that must be absent if an argument of this type is present
		 * @return This argument, for chaining
		 */
		default A requiresNot(String targetArgName){
			return addConstraint(new ArgumentConstraint(this, targetArgName, false, false));
		}
		/**
		 * Asserts that this argument cannot take a specific value if another argument is present
		 *
		 * @param targetArgName The name of the argument that may not take the given value if an argument of this type is present
		 * @param targetValue The value that the argument may not take if an argument of this type is present
		 * @return This argument, for chaining
		 */
		default A requiresNot(String targetArgName, Object targetValue){
			return addConstraint(new ArgumentConstraint(this, targetArgName, targetValue, false, false));
		}
		/**
		 * Asserts that this argument cannot be present if the value of another argument passes a test
		 *
		 * @param targetArgName The name of the argument whose value may constrain the availability of this argument
		 * @param targetValueCheck The test that, if the target argument passes, forbids this argument from being present
		 * @return This argument, for chaining
		 */
		default A requiresNot(String targetArgName, Predicate<Object> targetValueCheck){
			return addConstraint(new ArgumentConstraint(this, targetArgName, targetValueCheck, false, false));
		}

		/**
		 * Asserts that this argument must be present at least once in an argument set
		 *
		 * @return This argument, for chaining
		 */
		default A required() {
			return times(1, getMaxTimes());
		}
		/**
		 * Asserts that an argument of this type must be present in an argument set if an argument of a different type is present
		 *
		 * @param targetArgName The name of the argument that, if present, makes this argument required
		 * @return This argument, for chaining
		 */
		default A requiredIf(String targetArgName){
			return addConstraint(new ArgumentConstraint(this, targetArgName, true, true));
		}
		/**
		 * Asserts that an argument of this type must be present in an argument set if an argument of a different type has a specified value
		 *
		 * @param targetArgName The name of the argument that, if present with the given value, makes this argument required
		 * @param targetValue The value of the target argument that makes this argument required
		 * @return This argument, for chaining
		 */
		default A requiredIf(String targetArgName, Object targetValue){
			return addConstraint(new ArgumentConstraint(this, targetArgName, targetValue, true, true));
		}
		/**
		 * Asserts that an argument of this type must be present in an argument set if the value of an argument of a different type passes a
		 * test
		 *
		 * @param targetArgName The name of the argument that, if present with a value passing the test, makes this argument required
		 * @param targetValueCheck The test for the value of the target argument that makes this argument required if it passes
		 * @return This argument, for chaining
		 */
		default A requiredIf(String targetArgName, Predicate<Object> targetValueCheck){
			return addConstraint(new ArgumentConstraint(this, targetArgName, targetValueCheck, true, true));
		}

		/**
		 * Asserts that an argument of this type must be present in an argument set if an argument of a different type is not present
		 *
		 * @param targetArgName The name of the argument that, if not present, makes this argument required
		 * @return This argument, for chaining
		 */
		default A requiredIfNot(String targetArgName){
			return addConstraint(new ArgumentConstraint(this, targetArgName, true, false));
		}
		/**
		 * Asserts that an argument of this type must be present in an argument set if an argument of a different type does not have a
		 * specified value
		 *
		 * @param targetArgName The name of the argument that, if present with the given value, makes this argument required
		 * @param targetValue The value of the target argument that makes this argument required
		 * @return This argument, for chaining
		 */
		default A requiredIfNot(String targetArgName, Object targetValue){
			return addConstraint(new ArgumentConstraint(this, targetArgName, targetValue, true, false));
		}
		/**
		 * Asserts that an argument of this type must be present in an argument set if the value of an argument of a different type does not
		 * pass a test
		 *
		 * @param targetArgName The name of the argument that, if present with a value failing the test, makes this argument required
		 * @param targetValueCheck The test for the value of the target argument that makes this argument required if it fails
		 * @return This argument, for chaining
		 */
		default A requiredIfNot(String targetArgName, Predicate<Object> targetValueCheck){
			return addConstraint(new ArgumentConstraint(this, targetArgName, targetValueCheck, true, false));
		}

		/**
		 * Validates instances of this argument in an argument set
		 *
		 * @param args The argument set to validate
		 * @throws IllegalStateException If the argument set violates one of this argument's constraints
		 */
		default void validate(Arguments args) throws IllegalStateException {
			int times = args.getArguments(getName()).size();
			if (getMinTimes() == getMaxTimes() && times != getMinTimes()) {
				String msg = getName() + " must be specified exactly ";
				switch (times) {
				case 1:
					msg += "once";
					break;
				case 2:
					msg += "twice";
					break;
				default:
					msg += times + " times";
				}
				throw new IllegalStateException(msg);
			}
			if (times < getMinTimes()) {
				String msg = getName() + " must be specified at least ";
				switch (times) {
				case 1:
					msg += "once";
					break;
				case 2:
					msg += "twice";
					break;
				default:
					msg += times + " times";
				}
				throw new IllegalStateException(msg);
			}
			if (times > getMaxTimes()) {
				String msg = getName() + " must be specified at most ";
				switch (times) {
				case 1:
					msg += "once";
					break;
				case 2:
					msg += "twice";
					break;
				default:
					msg += times + " times";
				}
				throw new IllegalStateException(msg);
			}
			for (ArgumentConstraint constraint : getConstraints()) {
				if (!constraint.isValidWith(args)) {
					throw new IllegalStateException(constraint.getInvalidMessage());
				}
			}
		}

		/**
		 * Creates an argument of this type
		 *
		 * @param pattern The pattern that matched the argument
		 * @param match The matcher of the argument against the pattern
		 * @param value The argument's value text
		 * @return The new argument
		 * @throws IllegalArgumentException If the argument was unacceptable for this type for some reason
		 */
		Argument parse(ArgumentPattern pattern, Matcher match, String value) throws IllegalArgumentException;
	}

	/** Represents a constraint on an argument's existence */
	public static class ArgumentConstraint{
		private final ArgumentDef<?> theArgument;
		private final String theTarget;
		private final Object theTargetValue;
		private final Predicate<Object> theTargetValueCheck;
		private final boolean isArgumentRequired;
		private final boolean isTargetRequired;

		/**
		 * @param arg The argument definition whose existence may violate the constraint
		 * @param target The argument that this constraint is a dependency on
		 * @param argReq Whether the argument is required or forbidden if the target condition is met
		 * @param targetReq Whether the target argument's presence (as opposed to absence) meets the target condition
		 */
		public ArgumentConstraint(ArgumentDef<?> arg, String target, boolean argReq, boolean targetReq) {
			theArgument = arg;
			theTarget = target;
			theTargetValue = null;
			theTargetValueCheck = null;
			isArgumentRequired = argReq;
			isTargetRequired = targetReq;
		}

		/**
		 * @param arg The argument definition whose existence may violate the constraint
		 * @param target The argument that this constraint is a dependency on
		 * @param targetValue The test value for the target argument
		 * @param argReq Whether the argument is required or forbidden if the target condition is met
		 * @param targetReq Whether the target argument's presence (as opposed to absence) meets the target condition
		 */
		public ArgumentConstraint(ArgumentDef<?> arg, String target, Object targetValue, boolean argReq, boolean targetReq) {
			theArgument = arg;
			theTarget = target;
			theTargetValue = targetValue;
			theTargetValueCheck=theTargetValue::equals;
			isArgumentRequired = argReq;
			isTargetRequired = targetReq;
		}

		/**
		 * @param arg The argument definition whose existence may violate the constraint
		 * @param target The argument that this constraint is a dependency on
		 * @param targetValueCheck The test for the value of the target argument
		 * @param argReq Whether the argument is required or forbidden if the target condition is met
		 * @param targetReq Whether the target argument's presence (as opposed to absence) meets the target condition
		 */
		public ArgumentConstraint(ArgumentDef<?> arg, String target, Predicate<Object> targetValueCheck, boolean argReq, boolean targetReq) {
			theArgument = arg;
			theTarget = target;
			theTargetValue = null;
			theTargetValueCheck = targetValueCheck;
			isArgumentRequired = argReq;
			isTargetRequired = targetReq;
		}

		/**@return The argument definition that this constraint is specified on */
		public ArgumentDef<?> getArgument(){
			return theArgument;
		}

		/** @return The name argument that this constraint references */
		public String getTarget(){
			return theTarget;
		}

		/** @return The test value for the target argument */
		public Object getTargetValue(){
			return theTargetValue;
		}

		/** @return The test for the target argument's value */
		public Predicate<Object> getTargetValueCheck(){
			return theTargetValueCheck;
		}

		/** @return Whether the argument is required or forbidden if the target condition is met*/
		public boolean isArgumentRequired(){
			return isArgumentRequired;
		}

		/** @return Whether the target condition must be true or false for this constraint to be satisfied */
		public boolean isTargetRequired(){
			return isTargetRequired;
		}

		/**
		 * Tests this constraint on an argument set
		 *
		 * @param args The argument set to test
		 * @return Whether the argument set passes this constraint
		 */
		public boolean isValidWith(Arguments args){
			if (isArgumentRequired) {
				// required
				if (isTargetConditionActive(args) && !isArgumentConstraintSatisfied(args)) {
					return false;
				}
			} else {
				// requires
				if (isArgumentConstraintSatisfied(args) && !isTargetConditionActive(args)) {
					return false;
				}
			}
			return true;
		}

		/**
		 * @param args The argument set to test
		 * @return Whether the argument condition of this constraint is true
		 */
		public boolean isArgumentConstraintSatisfied(Arguments args){
			return !args.getArguments(theArgument.getName()).isEmpty();
		}

		/**
		 * @param args The argument set to test
		 * @return Whether the target argument condition of this constraint is true
		 */
		public boolean isTargetConditionActive(Arguments args){
			return isTargetRequired == isConstraintSatisfied(args, theTarget, theTargetValue, theTargetValueCheck);
		}

		/**
		 * Checks a constraint on an argument
		 *
		 * @param args The argument set to check
		 * @param argName The name of the argument to check the constraint against
		 * @param value The value of the argument to check (may be null if test is non-null)
		 * @param test The test to apply to the argument value (may be null if value is non-null)
		 * @return Whether the argument set passes the constraint
		 */
		protected boolean isConstraintSatisfied(Arguments args, String argName, Object value, Predicate<?> test) {
			List<Argument> targetArgs = args.getArguments(theArgument.getArgDefSet().getArgument(argName));
			if (test == null) {
				return !targetArgs.isEmpty();
			} else {
				boolean found = false;
				for (Argument targetArg : targetArgs) {
					if (targetArg instanceof ValuedArgument && ((Predicate<Object>) test).test(((ValuedArgument<?>) targetArg).getValue())) {
						found = true;
						break;
					}
				}
				return found;
			}
		}

		/** @return The message to use to communicate to the user what's wrong if an argument set fails this constraint */
		public String getInvalidMessage() {
			if (theTargetValueCheck == null) {
				if (isTargetRequired) {
					if (isArgumentRequired) {
						return "Argument " + getArgument().getName() + " must be specified when " + getTarget() + " is specified";
					} else {
						return "Argument " + getArgument().getName() + " may not be specified with " + getTarget();
					}
				} else {
					if (isArgumentRequired) {
						return "Argument " + getArgument().getName() + " must be specified when " + getTarget() + " is absent";
					} else {
						return "Argument " + getArgument().getName() + " may not be specified without " + getTarget();
					}
				}
			} else {
				if (isArgumentRequired) {
					String msg = "Argument " + getArgument().getName() + " must be specified when " + getTarget()
					+ (isTargetRequired ? " has" : " does not have");
					if (getTargetValue() != null) {
						msg += " value " + theTargetValue;
					} else {
						msg += " an appropriate value";
					}
					return msg;
				} else {
					String msg = "Argument " + getArgument().getName() + " may not be specified with" + (isTargetRequired ? "out" : "");
					if (getTargetValue() != null) {
						msg += " a " + getTarget() + " value of " + getTargetValue();
					} else {
						msg += " an appropriate " + getTarget() + " value";
					}
					return msg;
				}
			}
		}

		@Override
		public String toString() {
			return getInvalidMessage();
		}
	}

	/**
	 * @param <T> The type of value that the argument may take
	 * @param <A> The sub-type of the argument
	 */
	public interface ValuedArgumentDef<T, A extends ValuedArgumentDef<T, A>> extends ArgumentDef<A> {
		default A requires(T value, String targetArgName) {
			return addConstraint(new ArgumentValueConstraint<>(this, value, targetArgName, false, true));
		}

		default A requires(Predicate<? super T> valueCheck, String targetArgName) {
			return addConstraint(new ArgumentValueConstraint<>(this, valueCheck, targetArgName, false, true));
		}

		default A requires(T value, String targetArgName, Object targetValue) {
			return addConstraint(new ArgumentValueConstraint<>(this, value, targetArgName, targetValue, false, true));
		}

		default A requires(T value, String targetArgName, Predicate<Object> targetValueCheck) {
			return addConstraint(new ArgumentValueConstraint<>(this, value, targetArgName, targetValueCheck, false, true));
		}

		default A requires(Predicate<? super T> valueCheck, String targetArgName, Object targetValue) {
			return addConstraint(new ArgumentValueConstraint<>(this, valueCheck, targetArgName, targetValue, false, true));
		}

		default A requires(Predicate<? super T> valueCheck, String targetArgName, Predicate<Object> targetValueCheck) {
			return addConstraint(new ArgumentValueConstraint<>(this, valueCheck, targetArgName, targetValueCheck, false, true));
		}

		default A requiresNot(T value, String targetArgName) {
			return addConstraint(new ArgumentValueConstraint<>(this, value, targetArgName, false, false));
		}

		default A requiresNot(Predicate<? super T> valueCheck, String targetArgName) {
			return addConstraint(new ArgumentValueConstraint<>(this, valueCheck, targetArgName, false, false));
		}

		default A requiresNot(T value, String targetArgName, Object targetValue) {
			return addConstraint(new ArgumentValueConstraint<>(this, value, targetArgName, targetValue, false, false));
		}

		default A requiresNot(T value, String targetArgName, Predicate<Object> targetValueCheck) {
			return addConstraint(new ArgumentValueConstraint<>(this, value, targetArgName, targetValueCheck, false, false));
		}

		default A requiresNot(Predicate<? super T> valueCheck, String targetArgName, Object targetValue) {
			return addConstraint(new ArgumentValueConstraint<>(this, valueCheck, targetArgName, targetValue, false, false));
		}

		default A requiresNot(Predicate<? super T> valueCheck, String targetArgName, Predicate<Object> targetValueCheck) {
			return addConstraint(new ArgumentValueConstraint<>(this, valueCheck, targetArgName, targetValueCheck, false, false));
		}

		default A requiredIf(T value, String targetArgName){
			return addConstraint(new ArgumentValueConstraint<>(this, value, targetArgName, true, true));
		}
		default A requiredIf(Predicate<? super T> valueCheck, String targetArgName){
			return addConstraint(new ArgumentValueConstraint<>(this, valueCheck, targetArgName, true, true));
		}
		default A requiredIf(T value, String targetArgName, Object targetValue){
			return addConstraint(new ArgumentValueConstraint<>(this, value, targetArgName, targetValue, true, true));
		}
		default A requiredIf(T value, String targetArgName, Predicate<Object> targetValueCheck){
			return addConstraint(new ArgumentValueConstraint<>(this, value, targetArgName, targetValueCheck, true, true));
		}
		default A requiredIf(Predicate<? super T> valueCheck, String targetArgName, Object targetValue){
			return addConstraint(new ArgumentValueConstraint<>(this, valueCheck, targetArgName, targetValue, true, true));
		}
		default A requiredIf(Predicate<? super T> valueCheck, String targetArgName, Predicate<Object> targetValueCheck){
			return addConstraint(new ArgumentValueConstraint<>(this, valueCheck, targetArgName, targetValueCheck, true, true));
		}

		default A requiredIfNot(T value, String targetArgName){
			return addConstraint(new ArgumentValueConstraint<>(this, value, targetArgName, true, false));
		}
		default A requiredIfNot(Predicate<? super T> valueCheck, String targetArgName){
			return addConstraint(new ArgumentValueConstraint<>(this, valueCheck, targetArgName, true, false));
		}
		default A requiredIfNot(T value, String targetArgName, Object targetValue){
			return addConstraint(new ArgumentValueConstraint<>(this, value, targetArgName, targetValue, true, false));
		}
		default A requiredIfNot(T value, String targetArgName, Predicate<Object> targetValueCheck){
			return addConstraint(new ArgumentValueConstraint<>(this, value, targetArgName, targetValueCheck, true, false));
		}
		default A requiredIfNot(Predicate<? super T> valueCheck, String targetArgName, Object targetValue){
			return addConstraint(new ArgumentValueConstraint<>(this, valueCheck, targetArgName, targetValue, true, false));
		}
		default A requiredIfNot(Predicate<? super T> valueCheck, String targetArgName, Predicate<Object> targetValueCheck){
			return addConstraint(new ArgumentValueConstraint<>(this, valueCheck, targetArgName, targetValueCheck, true, false));
		}

		/**
		 * @param constraint The constraint on values of arguments of this type
		 * @return This argument, for chaining
		 */
		default A constrain(Predicate<? super T> constraint) {
			return constrain(null, constraint);
		}
		/**
		 * @param constraintName The name for the new constraint
		 * @param constraint The constraint on values of arguments of this type
		 * @return This argument, for chaining
		 */
		A constrain(String constraintName, Predicate<? super T> constraint);
		/**
		 * Constrains values of this argument to the given set
		 *
		 * @param values The set of values that arguments of this type may have
		 * @return This argument, for chaining
		 */
		A only(T... values);

		/**
		 * Gives this argument a default value if it is not specified in an argument set
		 *
		 * @param defValue The value to use for this argument if not specified
		 * @return This argument, for chaining
		 */
		A defValue(T defValue);
		/**
		 * Gives this argument a default value supplier if it is not specified in an argument set
		 *
		 * @param defCreator A supplier to generate a value for this argument if it is not supplied
		 * @return This argument, for chaining
		 */
		A defValue(Supplier<? extends T> defCreator);

		/** @return This argument's default value, or null if no default is configured */
		T getDefault();

		@Override
		default Argument parse(ArgumentPattern pattern, Matcher match, String value) {
			return createArgument(pattern, match, parse(value));
		}

		/**
		 * Creates an argument of this type
		 *
		 * @param pattern The pattern that matched the argument
		 * @param match The matcher of the argument against the pattern
		 * @param value The value for the argument
		 * @return The new argument
		 */
		Argument createArgument(ArgumentPattern pattern, Matcher match, T value);

		/**
		 * @param value The argument value text to parse
		 * @return The parsed value
		 * @throws IllegalArgumentException If the text cannot be parsed to this argument's type
		 */
		T parse(String value) throws IllegalArgumentException;
	}

	/**
	 * An argument whose value is comparable
	 *
	 * @param <T> The type of value that the argument may take
	 * @param <A> The sub-type of the argument
	 */
	public interface ComparableArgumentDef<T extends Comparable<T>, A extends ComparableArgumentDef<T, A>> extends ValuedArgumentDef<T, A> {
		/**
		 * @param min The minimum value for this argument
		 * @return This argument, for chaining
		 */
		A atLeast(T min);
		/**
		 * @param max The maximum value for this argument
		 * @return This argument, for chaining
		 */
		A atMost(T max);
		/**
		 * @param min The minimum value for this argument
		 * @param max The maximum value for this argument
		 * @return This argument, for chaining
		 */
		A between(T min, T max);
	}

	/**
	 * A constraint on an argument's value
	 * 
	 * @param <T> The type of the value
	 */
	public static class ArgumentValueConstraint<T> extends ArgumentConstraint {
		private final T theArgumentValue;

		private final Predicate<? super T> theArgumentValueCheck;

		public ArgumentValueConstraint(ValuedArgumentDef<T, ?> arg, T argValue, String target, boolean argReq, boolean targetReq) {
			super(arg, target, argReq, targetReq);
			theArgumentValue = argValue;
			theArgumentValueCheck = theArgumentValue::equals;
		}

		public ArgumentValueConstraint(ValuedArgumentDef<T, ?> arg, Predicate<? super T> argValueCheck, String target, boolean argReq,
			boolean targetReq) {
			super(arg, target, argReq, targetReq);
			theArgumentValue = null;
			theArgumentValueCheck = argValueCheck;
		}

		public ArgumentValueConstraint(ValuedArgumentDef<T, ?> arg, T argValue, String target, Object targetValue, boolean argReq,
			boolean targetReq) {
			super(arg, target, targetValue, argReq, targetReq);
			theArgumentValue = argValue;
			theArgumentValueCheck = theArgumentValue::equals;
		}

		public ArgumentValueConstraint(ValuedArgumentDef<T, ?> arg, Predicate<? super T> argValueCheck, String target, Object targetValue,
			boolean argReq, boolean targetReq) {
			super(arg, target, targetValue, argReq, targetReq);
			theArgumentValue = null;
			theArgumentValueCheck = argValueCheck;
		}

		public ArgumentValueConstraint(ValuedArgumentDef<T, ?> arg, T argValue, String target, Predicate<Object> targetValueCheck,
			boolean argReq, boolean targetReq) {
			super(arg, target, targetValueCheck, argReq, targetReq);
			theArgumentValue = argValue;
			theArgumentValueCheck = theArgumentValue::equals;
		}

		public ArgumentValueConstraint(ValuedArgumentDef<T, ?> arg, Predicate<? super T> argValueCheck, String target,
			Predicate<Object> targetValueCheck, boolean argReq, boolean targetReq) {
			super(arg, target, targetValueCheck, argReq, targetReq);
			theArgumentValue = null;
			theArgumentValueCheck = argValueCheck;
		}

		/** @return The argument value that is part of this constraint */
		public T getArgValue() {
			return theArgumentValue;
		}

		/** @return The test for the argument value that is part of this constraint */
		public Predicate<? super T> getArgValueCheck() {
			return theArgumentValueCheck;
		}

		@Override
		public boolean isArgumentConstraintSatisfied(Arguments args){
			return isConstraintSatisfied(args, getArgument().getName(), theArgumentValue, theArgumentValueCheck);
		}

		@Override
		public String getInvalidMessage() {
			if (getTargetValueCheck() != null) {
				if (isTargetRequired()) {
					if (theArgumentValue == null) {
						return "Argument " + getArgument().getName() + " is constrained when " + getTarget() + " is specified";
					} else if (isArgumentRequired()) {
						return "Argument " + getArgument().getName() + " must be " + theArgumentValue + " when " + getTarget()
						+ " is specified";
					} else {
						return "Argument " + getArgument().getName() + " may not be " + theArgumentValue + " when " + getTarget()
						+ " is specified";
					}
				} else {
					if (theArgumentValue == null) {
						return "Argument " + getArgument().getName() + " is constrained when " + getTarget() + " is absent";
					} else if (isArgumentRequired()) {
						return "Argument " + getArgument().getName() + " must be " + theArgumentValue + " when " + getTarget()
						+ " is absent";
					} else {
						return "Argument " + getArgument().getName() + " may not be " + theArgumentValue + " when " + getTarget()
						+ " is absent";
					}
				}
			} else {
				if (theArgumentValue == null) {
					String msg = "Argument " + getArgument().getName() + " is constrained when " + getTarget()
					+ (isTargetRequired() ? " has" : " does not have");
					if (getTargetValue() != null) {
						msg += " value " + getTargetValue();
					} else {
						msg += " an appropriate value";
					}
					return msg;
				} else if (isArgumentRequired()) {
					String msg = "Argument " + getArgument().getName() + " must be " + theArgumentValue + " when " + getTarget()
					+ (isTargetRequired() ? " has" : " does not have");
					if (getTargetValue() != null) {
						msg += " value " + getTargetValue();
					} else {
						msg += " an appropriate value";
					}
					return msg;
				} else {
					String msg = "Argument " + getArgument().getName() + " may not be " + theArgumentValue + " with"
						+ (isTargetRequired() ? "out" : "");
					if (getTargetValue() != null) {
						msg += " a " + getTarget() + " value of " + getTargetValue();
					} else {
						msg += " an appropriate " + getTarget() + " value";
					}
					return msg;
				}
			}
		}
	}

	private static abstract class ArgumentDefImpl<A extends ArgumentDefImpl<A>> implements ArgumentDef<A>{
		private final ArgumentDefSet theDefSet;
		private final String theName;
		private int theMinTimes = 0;
		private int theMaxTimes = 1;
		private List<ArgumentConstraint> theConstraints;

		protected ArgumentDefImpl(ArgumentDefSet argDefs, String name) {
			theDefSet = argDefs;
			theName = name;
			theConstraints = new ArrayList<>();
		}

		@Override
		public ArgumentDefSet getArgDefSet() {
			return theDefSet;
		}

		@Override
		public String getName() {
			return theName;
		}

		@Override
		public A times(int min, int max) {
			theMinTimes = min;
			theMaxTimes = max;
			return (A) this;
		}

		@Override
		public int getMinTimes() {
			return theMinTimes;
		}

		@Override
		public int getMaxTimes() {
			return theMaxTimes;
		}

		@Override
		public A addConstraint(ArgumentConstraint constraint) {
			theConstraints.add(constraint);
			return (A) this;
		}

		@Override
		public Collection<ArgumentConstraint> getConstraints() {
			return theConstraints;
		}

		@Override
		public StringArgumentDef stringArg(String name) {
			return theDefSet.stringArg(name);
		}

		@Override
		public PatternArgumentDef patternArg(String name, Pattern pattern) {
			return theDefSet.patternArg(name, pattern);
		}

		@Override
		public IntArgumentDef intArg(String name) {
			return theDefSet.intArg(name);
		}

		@Override
		public FloatArgumentDef floatArg(String name) {
			return theDefSet.floatArg(name);
		}

		@Override
		public <E extends Enum<E>> EnumArgumentDef<E> enumArg(String name, Class<E> type) {
			return theDefSet.enumArg(name, type);
		}

		@Override
		public BooleanArgumentDef booleanArg(String name) {
			return theDefSet.booleanArg(name);
		}

		@Override
		public FlagArgumentDef flagArg(String name) {
			return theDefSet.flagArg(name);
		}

		@Override
		public String toString() {
			return theName;
		}
	}

	private static abstract class ValuedArgumentDefImpl<T, A extends ValuedArgumentDefImpl<T, A>> extends ArgumentDefImpl<A> implements
	ValuedArgumentDef<T, A> {
		private class NamedConstraint {
			final String theName;
			final Predicate<? super T> theConstraint;

			public NamedConstraint(String name, Predicate<? super T> constraint) {
				super();
				theName = name;
				theConstraint = constraint;
			}
		}

		private final List<NamedConstraint> theValueConstraints;
		private Supplier<? extends T> theDefaultCreator;
		private T[] theOnlyValues;

		protected ValuedArgumentDefImpl(ArgumentDefSet argDefs, String name) {
			super(argDefs, name);
			theValueConstraints = new ArrayList<>();
		}

		@Override
		public A constrain(String constraintName, Predicate<? super T> constraint) {
			theValueConstraints.add(new NamedConstraint(constraintName, constraint));
			return (A) this;
		}

		@Override
		public A only(T... values) {
			theOnlyValues = values;
			return (A) this;
		}

		@Override
		public A defValue(T defValue) {
			theDefaultCreator = () -> defValue;
			return (A) this;
		}

		@Override
		public A defValue(Supplier<? extends T> defCreator) {
			theDefaultCreator = defCreator;
			return (A) this;
		}

		@Override
		public T getDefault() {
			return theDefaultCreator == null ? null : theDefaultCreator.get();
		}

		@Override
		public T parse(String value) throws IllegalArgumentException {
			T ret = parseValue(value);
			checkValue(value, ret);
			return ret;
		}

		protected abstract T parseValue(String text) throws IllegalArgumentException;

		protected void checkValue(String text, T value) throws IllegalArgumentException {
			for (NamedConstraint c : theValueConstraints) {
				if (!c.theConstraint.test(value)) {
					throw new IllegalArgumentException(getName() + " value " + value + " violates constraint " + c.theName);
				}
			}
			if (theOnlyValues != null && !ArrayUtils.contains(theOnlyValues, value)) {
				throw new IllegalArgumentException(getName()+" value "+value+" does not match any of the possible values");
			}
		}
	}

	private static abstract class ComparableArgumentDefImpl<T extends Comparable<T>, A extends ComparableArgumentDefImpl<T, A>> extends
	ValuedArgumentDefImpl<T, A> implements ComparableArgumentDef<T, A> {
		private T theMin;
		private T theMax;

		protected ComparableArgumentDefImpl(ArgumentDefSet argDefs, String name) {
			super(argDefs, name);
		}

		@Override
		public A atLeast(T min) {
			theMin = min;
			return (A) this;
		}

		@Override
		public A atMost(T max) {
			theMax = max;
			return (A) this;
		}

		@Override
		public A between(T min, T max) {
			theMin = min;
			theMax = max;
			return (A) this;
		}

		@Override
		protected void checkValue(String text, T value) throws IllegalArgumentException {
			super.checkValue(text, value);
			if (theMin != null && theMin.compareTo(value) > 0) {
				throw new IllegalArgumentException(getName() + " must be no less than " + theMin);
			}
			if (theMax != null && theMax.compareTo(value) < 0) {
				throw new IllegalArgumentException(getName() + " must be no greater than " + theMax);
			}
		}
	}

	/** An argument that is just a flag--either present or not */
	public static class FlagArgumentDef extends ArgumentDefImpl<FlagArgumentDef> {
		/**
		 * @param argDefs The argument definition set to create the argument for
		 * @param name The name for the argument
		 */
		public FlagArgumentDef(ArgumentDefSet argDefs, String name) {
			super(argDefs, name);
		}

		@Override
		public Argument parse(ArgumentPattern pattern, Matcher match, String value) {
			if(value!=null) {
				throw new IllegalStateException("Flag argument \"" + getName() + "\" cannot be created with a value");
			}
			return new FlagArgument(this, pattern, match);
		}

		@Override
		public String toString() {
			return super.toString() + " (flag)";
		}
	}

	/** A string-typed argument defintion */
	public static class StringArgumentDef extends ValuedArgumentDefImpl<String, StringArgumentDef> {
		/**
		 * @param argDefs The definition set that this argument definition is a part of
		 * @param name The name of this argument
		 */
		public StringArgumentDef(ArgumentDefSet argDefs, String name) {
			super(argDefs, name);
		}

		@Override
		public Argument createArgument(ArgumentPattern pattern, Matcher match, String value) {
			return new ValuedArgument<>(this, pattern, match, value);
		}

		@Override
		protected String parseValue(String text) throws IllegalArgumentException {
			return text;
		}

		@Override
		public String toString() {
			return super.toString() + " (string)";
		}
	}

	/** A string-typed, pattern-constrained argument definition */
	public static class PatternArgumentDef extends ValuedArgumentDefImpl<String, PatternArgumentDef> {
		private final Pattern thePattern;

		/**
		 * @param argDefs The definition set that this argument definition is a part of
		 * @param name The name of this argument
		 * @param pattern The pattern that the values of arguments of this type must match
		 */
		public PatternArgumentDef(ArgumentDefSet argDefs, String name, Pattern pattern) {
			super(argDefs, name);
			thePattern = pattern;
		}

		/** @return The pattern that the values of arguments of this type must match */
		public Pattern getPattern() {
			return thePattern;
		}

		@Override
		public Argument createArgument(ArgumentPattern pattern, Matcher match, String value) {
			return new ValuedArgument<>(this, pattern, match, value);
		}

		@Override
		protected String parseValue(String text) throws IllegalArgumentException {
			Matcher match = thePattern.matcher(text);
			if (!match.matches()) {
				throw new IllegalArgumentException(getName() + " value \"" + text + "\" does not match pattern " + thePattern.pattern());
			}
			return match.group();
		}

		@Override
		public String toString() {
			return super.toString() + " (pattern " + thePattern.pattern() + ")";
		}
	}

	/** An integer-type argument definition */
	public static class IntArgumentDef extends ComparableArgumentDefImpl<Long, IntArgumentDef> {
		/**
		 * @param argDefs The definition set that this argument definition is a part of
		 * @param name The name of this argument
		 */
		public IntArgumentDef(ArgumentDefSet argDefs, String name) {
			super(argDefs, name);
		}

		@Override
		public Argument createArgument(ArgumentPattern pattern, Matcher match, Long value) {
			return new ValuedArgument<>(this, pattern, match, value);
		}

		@Override
		protected Long parseValue(String text) throws IllegalArgumentException {
			try {
				return Long.valueOf(text);
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException(getName() + " value \"" + text + "\" is not an integer");
			}
		}

		@Override
		public String toString() {
			return super.toString() + " (int)";
		}
	}

	/** A floating-point-type argument definition */
	public static class FloatArgumentDef extends ComparableArgumentDefImpl<Double, FloatArgumentDef> {
		/**
		 * @param argDefs The definition set that this argument definition is a part of
		 * @param name The name of this argument
		 */
		public FloatArgumentDef(ArgumentDefSet argDefs, String name) {
			super(argDefs, name);
		}

		@Override
		public Argument createArgument(ArgumentPattern pattern, Matcher match, Double value) {
			return new ValuedArgument<>(this, pattern, match, value);
		}

		@Override
		protected Double parseValue(String text) throws IllegalArgumentException {
			try {
				return Double.valueOf(text);
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException(getName() + " value \"" + text + "\" is not a floating point number");
			}
		}

		@Override
		public String toString() {
			return super.toString() + " (float)";
		}
	}

	/**
	 * An enum-type argument definition
	 *
	 * @param <E> The type of the enum
	 */
	public static class EnumArgumentDef<E extends Enum<E>> extends ComparableArgumentDefImpl<E, EnumArgumentDef<E>> {
		private final Class<E> theType;

		/**
		 * @param argDefs The definition set that this argument definition is a part of
		 * @param name The name of this argument
		 * @param type The type of enum for this argument
		 */
		public EnumArgumentDef(ArgumentDefSet argDefs, String name, Class<E> type) {
			super(argDefs, name);
			theType = type;
		}

		/** @return The type of enum that this argument's values may be */
		public Class<E> getType() {
			return theType;
		}

		@Override
		public Argument createArgument(ArgumentPattern pattern, Matcher match, E value) {
			return new ValuedArgument<>(this, pattern, match, value);
		}

		@Override
		protected E parseValue(String text) throws IllegalArgumentException {
			try {
				return Enum.valueOf(theType, text);
			} catch (IllegalArgumentException e) {
				throw new IllegalArgumentException(getName() + " value \"" + text + "\" is not a constant of enum type "
					+ theType.getName());
			}
		}

		@Override
		public String toString() {
			return super.toString() + " (enum<" + theType + ">)";
		}
	}

	/** A boolean-type argument definition */
	public static class BooleanArgumentDef extends ValuedArgumentDefImpl<Boolean, BooleanArgumentDef> {
		/**
		 * @param argDefs The definition set that this argument definition is a part of
		 * @param name The name of this argument
		 */
		public BooleanArgumentDef(ArgumentDefSet argDefs, String name) {
			super(argDefs, name);
		}

		@Override
		public Argument createArgument(ArgumentPattern pattern, Matcher match, Boolean value) {
			return new ValuedArgument<>(this, pattern, match, value);
		}

		@Override
		protected Boolean parseValue(String text) throws IllegalArgumentException {
			try {
				return Boolean.valueOf(text);
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException(getName() + " value \"" + text + "\" is not a boolean");
			}
		}

		@Override
		public String toString() {
			return super.toString() + " (boolean)";
		}
	}

	/** Represents an instance of an argument in an argument set */
	public interface Argument {
		/** @return The type definition of this argument */
		ArgumentDef<?> getDefinition();
		/** @return The pattern that this argument matched */
		ArgumentPattern getPattern();

		/** @return The name of this argument */
		String getName();
		/** @return The matcher of this argument against the pattern */
		Matcher getMatch();
	}

	private static abstract class ArgumentImpl implements Argument{
		private ArgumentDef<?> theDefinition;
		private ArgumentPattern thePattern;
		private final Matcher theMatch;

		protected ArgumentImpl(ArgumentDef<?> def, ArgumentPattern pattern, Matcher match) {
			theDefinition=def;
			thePattern=pattern;
			theMatch=match;
		}

		@Override
		public ArgumentDef<?> getDefinition(){
			return theDefinition;
		}

		@Override
		public ArgumentPattern getPattern(){
			return thePattern;
		}

		@Override
		public String getName() {
			return theDefinition.getName();
		}

		@Override
		public Matcher getMatch(){
			return theMatch;
		}

		@Override
		public String toString() {
			return theDefinition.toString();
		}
	}

	/**
	 * An instance of a valued argument
	 *
	 * @param <T> The type of the argument's value
	 */
	public static class ValuedArgument<T> extends ArgumentImpl {
		private final T theValue;

		/**
		 * @param def The type of the argument
		 * @param pattern The pattern that created the argument
		 * @param match The match of the argument against the pattern
		 * @param value The argument's value
		 */
		public ValuedArgument(ValuedArgumentDef<T, ?> def, ArgumentPattern pattern, Matcher match, T value) {
			super(def, pattern, match);
			theValue = value;
		}

		@Override
		public ValuedArgumentDef<T, ?> getDefinition(){
			return (ValuedArgumentDef<T, ?>) super.getDefinition();
		}

		/** @return This argument's value */
		public T getValue() {
			return theValue;
		}

		/** @return This argument's value, asserting an integer type */
		public long getInt() {
			return ((Number) theValue).longValue();
		}

		/** @return This argument's value, asserting a floating-point type */
		public double getFloat() {
			return ((Number) theValue).doubleValue();
		}

		@Override
		public String toString() {
			return super.toString() + "=" + theValue;
		}
	}

	/** An instance of a flag argument */
	public static class FlagArgument extends ArgumentImpl {
		/**
		 * @param def The type of this argument
		 * @param pattern The pattern that matched the argument
		 * @param match The matcher of this argument against the pattern
		 */
		public FlagArgument(FlagArgumentDef def, ArgumentPattern pattern, Matcher match) {
			super(def, pattern, match);
		}

		@Override
		public FlagArgumentDef getDefinition(){
			return (FlagArgumentDef) super.getDefinition();
		}
	}

	/** Represents an argument that matched a flag pattern, but did not match a type (ArgumentDefinition) */
	public static class UnmatchedArgument implements Argument {
		private ArgumentPattern thePattern;
		private Matcher theMatch;
		private final String theName;

		/**
		 * @param pattern The pattern that matched this argument
		 * @param match The matcher of this argument against the pattern
		 * @param name The name of this argument
		 */
		public UnmatchedArgument(ArgumentPattern pattern, Matcher match, String name) {
			thePattern = pattern;
			theName = name;
		}

		@Override
		public ArgumentDef<?> getDefinition() {
			return null;
		}

		@Override
		public ArgumentPattern getPattern() {
			return thePattern;
		}

		@Override
		public Matcher getMatch() {
			return theMatch;
		}

		@Override
		public String getName() {
			return theName;
		}
	}

	/** Represents a valued argument with no type */
	public static class UnmatchedValuedArgument extends ValuedArgument<String> {
		private static class UnmatchedValuedArgumentDef extends ValuedArgumentDefImpl<String, UnmatchedValuedArgumentDef> {
			public UnmatchedValuedArgumentDef(ArgumentDefSet argDefs, String name) {
				super(argDefs, name);
			}

			@Override
			public Argument createArgument(ArgumentPattern pattern, Matcher match, String value) {
				return new UnmatchedValuedArgument(pattern, match, getName(), value);
			}

			@Override
			public Argument parse(ArgumentPattern pattern, Matcher match, String value) throws IllegalArgumentException {
				return new UnmatchedValuedArgument(pattern, match, getName(), value);
			}

			@Override
			protected String parseValue(String text) throws IllegalArgumentException {
				return text;
			}
		}

		/**
		 * @param pattern The pattern that matched this argument
		 * @param match The match of the argument against the pattern
		 * @param name The name of the argument
		 * @param value The value of the argument
		 */
		public UnmatchedValuedArgument(ArgumentPattern pattern, Matcher match, String name, String value) {
			super(new UnmatchedValuedArgumentDef(null, name), pattern, match, value);
		}
	}

	/** A set of argument definitions */
	public static class ArgumentDefSet implements ArgumentDefCreator {
		private Map<String, ArgumentDef<?>> theArgDefs;

		/** Creates the set */
		public ArgumentDefSet(){
			theArgDefs = new LinkedHashMap<>();
		}

		/**
		 * @param name The name of the argument definition to get
		 * @return The argument definition in this set with the given name, or null if none exists
		 */
		public ArgumentDef<?> getArgument(String name) {
			return theArgDefs.get(name);
		}

		@Override
		public StringArgumentDef stringArg(String name) {
			if(theArgDefs.containsKey(name))
				throw new IllegalArgumentException("Argument "+name+" already defined in this set");
			StringArgumentDef ret = new StringArgumentDef(this, name);
			theArgDefs.put(name, ret);
			return ret;
		}

		@Override
		public PatternArgumentDef patternArg(String name, Pattern pattern) {
			if(theArgDefs.containsKey(name))
				throw new IllegalArgumentException("Argument "+name+" already defined in this set");
			PatternArgumentDef ret = new PatternArgumentDef(this, name, pattern);
			theArgDefs.put(name, ret);
			return ret;
		}

		@Override
		public IntArgumentDef intArg(String name) {
			if(theArgDefs.containsKey(name))
				throw new IllegalArgumentException("Argument "+name+" already defined in this set");
			IntArgumentDef ret = new IntArgumentDef(this, name);
			theArgDefs.put(name, ret);
			return ret;
		}

		@Override
		public FloatArgumentDef floatArg(String name) {
			if(theArgDefs.containsKey(name))
				throw new IllegalArgumentException("Argument "+name+" already defined in this set");
			FloatArgumentDef ret = new FloatArgumentDef(this, name);
			theArgDefs.put(name, ret);
			return ret;
		}

		@Override
		public <E extends Enum<E>> EnumArgumentDef<E> enumArg(String name, Class<E> type) {
			if(theArgDefs.containsKey(name))
				throw new IllegalArgumentException("Argument "+name+" already defined in this set");
			EnumArgumentDef<E> ret = new EnumArgumentDef<>(this, name, type);
			theArgDefs.put(name, ret);
			return ret;
		}

		@Override
		public BooleanArgumentDef booleanArg(String name) {
			if(theArgDefs.containsKey(name))
				throw new IllegalArgumentException("Argument "+name+" already defined in this set");
			BooleanArgumentDef ret = new BooleanArgumentDef(this, name);
			theArgDefs.put(name, ret);
			return ret;
		}

		@Override
		public FlagArgumentDef flagArg(String name) {
			if(theArgDefs.containsKey(name))
				throw new IllegalArgumentException("Argument "+name+" already defined in this set");
			FlagArgumentDef ret = new FlagArgumentDef(this, name);
			theArgDefs.put(name, ret);
			return ret;
		}

		/**
		 * Parses an argument
		 *
		 * @param pattern The pattern that matched the argument
		 * @param match The match of the argument against the pattern
		 * @param name The name of the argument
		 * @param value The value of the argument
		 * @return The parsed argument, or null if the name does not match an argument definition in this set
		 */
		public Argument parse(ArgumentPattern pattern, Matcher match, String name, String value) {
			ArgumentDef<?> argDef = theArgDefs.get(name);
			if(argDef == null)
				return null;
			return argDef.parse(pattern, match, value);
		}

		/**
		 * Validates all argument constraints in this definition set against an argument set
		 *
		 * @param args The argument set to validate
		 */
		public void validate(Arguments args) {
			// Populate default values
			for (ArgumentDef<?> arg : theArgDefs.values()) {
				if (arg instanceof ValuedArgumentDef) {
					if (args.getArguments(arg).isEmpty()) {
						Object def = ((ValuedArgumentDef<?, ?>) arg).getDefault();
						if(def != null)
							args.add(((ValuedArgumentDef<Object, ?>) arg).createArgument(null, null, def));
					}
				}
			}
			// Check argument constraints
			for(ArgumentDef<?> arg : theArgDefs.values())
				arg.validate(args);
		}

		@Override
		public String toString() {
			return theArgDefs.values().toString();
		}
	}

	/** @return An argument definition matching the default patterns */
	public static ArgumentDefSet defArgs() {
		return new ArgumentDefSet();
	}

	/** Represents a set of parsed arguments */
	public static class Arguments {
		@SuppressWarnings("unused")
		private final ArgumentParser theParser;
		private final List<Argument> theArguments;

		/** @param parser The parser that parsed this argument set */
		public Arguments(ArgumentParser parser) {
			theParser = parser;
			theArguments = new ArrayList<>();
		}

		/** @return All arguments in this set */
		public List<Argument> getArguments() {
			return theArguments;
		}

		/**
		 * @param name The name of the arguments to get
		 * @return All arguments in this set with the given name
		 */
		public List<Argument> getArguments(String name) {
			return theArguments.stream().filter(arg -> arg.getName().equals(name)).collect(Collectors.toList());
		}

		/**
		 * @param name The name of the argument to get
		 * @return The first argument in this set with the given name, or null if none exists
		 */
		public Argument getArgument(String name) {
			return theArguments.stream().filter(arg -> arg.getName().equals(name)).findFirst().orElse(null);
		}

		/**
		 * @param argDef The type to get arguments for
		 * @return All arguments in this set of the given type
		 */
		public List<Argument> getArguments(ArgumentDef<?> argDef) {
			return theArguments.stream().filter(arg -> arg.getDefinition().equals(argDef)).collect(Collectors.toList());
		}

		/**
		 * @param patternName The name of the pattern to get arguments for
		 * @return All arguments in this set that were matched by the given pattern
		 */
		public List<Argument> forPattern(String patternName) {
			return theArguments.stream().filter(arg -> arg.getPattern() != null && arg.getPattern().getName().equals(patternName))
				.collect(Collectors.toList());
		}

		/**
		 * @param name The name of the argument to get values for
		 * @return The values of all arguments with the given name in this set
		 */
		public List<?> getAll(String name) {
			return getArguments(name).stream().map(arg -> ((ValuedArgument<Object>) arg).getValue()).collect(Collectors.toList());
		}

		/** @param arg The argument to add to this set */
		public void add(Argument arg) {
			theArguments.add(arg);
		}

		/**
		 * @param argName The name of the argument to get the value of
		 * @return The value of the argument with the given name in this set
		 */
		public Object get(String argName) {
			Argument argument = getArgument(argName);
			if (argument == null) {
				return null;
			}
			if (!(argument instanceof ValuedArgument)) {
				throw new IllegalArgumentException("Argument " + argName + " does not have a value");
			}
			return ((ValuedArgument<?>) argument).getValue();
		}

		/**
		 * @param <T> The type of the argument to get
		 * @param argName The name of the argument to get the value of
		 * @param def The value to use for the argument value if the argument is missing in this argument set
		 * @return The argument value
		 */
		public <T> T get(String argName, T def) {
			Argument argument = getArgument(argName);
			if (argument == null) {
				return def;
			}
			if (!(argument instanceof ValuedArgument)) {
				throw new IllegalArgumentException("Argument " + argName + " does not have a value");
			}
			return ((ValuedArgument<T>) argument).getValue();
		}

		/**
		 * @param argName The name of the argument to get
		 * @return The value of the given argument, asserting string-type
		 */
		public String getString(String argName) {
			return (String) get(argName);
		}

		/**
		 * @param argName The name of the argument to get
		 * @return The value of the given argument, asserting integer-type
		 */
		public long getInt(String argName) {
			return ((Number) get(argName)).longValue();
		}

		/**
		 * @param argName The name of the argument to get
		 * @param def The value to use for the argument if the argument is missing in this set
		 * @return The value of the given argument, asserting integer-type
		 */
		public long getInt(String argName, int def) {
			return ((Number) get(argName, def)).longValue();
		}

		/**
		 * @param argName The name of the argument to get
		 * @return The value of the given argument, asserting floating-point-type
		 */
		public double getFloat(String argName) {
			return ((Number) get(argName)).doubleValue();
		}

		/**
		 * @param argName The name of the argument to get
		 * @param def The value to use for the argument if the argument is missing in this set
		 * @return The value of the given argument, asserting floating-poitn-type
		 */
		public double getFloat(String argName, double def) {
			return ((Number) get(argName, def)).doubleValue();
		}

		/**
		 * @param argName The name of the flag argument to check
		 * @return Whether this argument set contains the given flag
		 */
		public boolean hasFlag(String argName) {
			Argument argument = getArgument(argName);
			if (argument == null) {
				return false;
			}
			if (!(argument instanceof FlagArgument)) {
				throw new IllegalArgumentException("Argument " + argName + " is not a flag");
			}
			return true;
		}
	}

	/** Creates argument patterns */
	public static interface ArgumentPatternCreator {
		/** @return This pattern creator's parser */
		ArgumentParser getParser();

		/**
		 * Gets or creates a pattern to add arguments to
		 *
		 * @param pattern The pattern to match arguments with
		 * @return The pattern
		 */
		default ArgumentPattern forPattern(String pattern){
			return forPattern(null, pattern);
		}

		/**
		 * Gets or creates a pattern to add arguments to
		 *
		 * @param name The name of the pattern
		 * @param pattern The pattern to match arguments with
		 * @return The pattern
		 */
		default ArgumentPattern forPattern(String name, String pattern){
			return forPattern(name, Pattern.compile(pattern));
		}

		/**
		 * Gets or creates a pattern to add arguments to
		 *
		 * @param pattern The pattern to match arguments with
		 * @return The pattern
		 */
		default ArgumentPattern forPattern(Pattern pattern){
			return forPattern(null, pattern);
		}

		/**
		 * Gets or creates a pattern to add arguments to
		 *
		 * @param name The name of the pattern
		 * @param pattern The pattern to match arguments with
		 * @return The pattern
		 */
		default ArgumentPattern forPattern(String name, Pattern pattern){
			return getParser().forPattern(name, pattern);
		}

		/** @return The default pattern ("--(.+)=(.+)") */
		default ArgumentPattern forDefaultPattern() {
			return forPattern("DEFAULT", "--(.+)=(.+)");
		}

		/** @return The default flag pattern ("--(.+)") */
		default ArgumentPattern forDefaultFlagPattern() {
			return forPattern("DEFAULT", "--(.+)");
		}
	}

	/** Represents a text pattern that may match arguments */
	public static class ArgumentPattern implements ArgumentPatternCreator {
		private final ArgumentParser theParser;
		private final String theName;
		private final Pattern thePattern;

		private List<ArgumentDefSet> theAcceptedArguments;
		private boolean isAcceptingUnmatched;

		/**
		 * @param parser The parser for this pattern
		 * @param name The name of this pattern
		 * @param pattern The text pattern to match arguments with
		 */
		public ArgumentPattern(ArgumentParser parser, String name, Pattern pattern) {
			theParser=parser;
			theName=name;
			thePattern=pattern;
			theAcceptedArguments=new ArrayList<>();
		}

		@Override
		public ArgumentParser getParser() {
			return theParser;
		}

		/** @return This pattern's name */
		public String getName() {
			return theName;
		}

		/** @return The pattern that arguments are parsed with */
		public Pattern getPattern() {
			return thePattern;
		}

		/** @return Whether this pattern accepts arguments that do not match any of its accepted arguments */
		public ArgumentPattern acceptUnmatched() {
			isAcceptingUnmatched = true;
			return this;
		}

		/**
		 * @param argDefs The argument definitions to accept with this pattern
		 * @return This argument pattern, for chaining
		 */
		public ArgumentPattern accept(ArgumentDefSet argDefs){
			theAcceptedArguments.add(argDefs);
			return this;
		}

		/**
		 * Accepts not only the given argument, but all arguments in the given argument's set
		 *
		 * @param arg
		 *            The argument to accept the set for
		 * @return This pattern for chaining
		 */
		public ArgumentPattern accept(ArgumentDef<?> arg) {
			return accept(arg.getArgDefSet());
		}

		/**
		 * Attempts to parse an argument by this pattern
		 *
		 * @param arg The argument to parse
		 * @return The parsed argument, or null if the argument does not match this pattern
		 * @throws IllegalArgumentException If this pattern is misconfigured
		 */
		protected Argument parse(String arg) throws IllegalArgumentException {
			Matcher match = thePattern.matcher(arg);
			if(!match.matches())
				return null;

			String argName;
			String argValue;
			if (match.groupCount() == 0) {
				throw new IllegalStateException("Argument pattern \"" + theName + "\" (" + thePattern.pattern()
				+ ") does not have any capturing groups."
				+ " Capturing groups are required to get the name and possibly value of arguments.");
			}

			if (match.groupCount() ==1) {
				argName=match.group(1);
				argValue=null;
			} else{
				try {
					argName = match.group("name");
					if(match.groupCount()==2){
						//Use the other one for the value
						if(match.start(1) == match.start("name"))
							argValue=match.group(2);
						else
							argValue=match.group(1);
					} else {
						try {
							argValue = match.group("value");
						} catch (IllegalArgumentException e) {
							throw new IllegalStateException("Argument pattern \"" + theName + "\" (" + thePattern.pattern()
							+ ") has more than 2 groups and does not possess groups named \"name\" and \"value\".");
						}
					}
				} catch (IllegalArgumentException e) {
					if(match.groupCount()==2){
						argName=match.group(1);
						argValue=match.group(2);
					} else {
						throw new IllegalStateException("Argument pattern \"" + theName + "\" (" + thePattern.pattern()
						+ ") has more than 2 groups and does not possess a group named \"name\" and \"value\".");
					}
				}
			}
			for(ArgumentDefSet argDef : theAcceptedArguments){
				Argument ret = argDef.parse(this, match, argName, argValue);
				if(ret != null)
					return ret;
			}
			if (isAcceptingUnmatched) {
				if(argValue == null)
					return new UnmatchedArgument(this, match, argName);
				else
					return new UnmatchedValuedArgument(this, match, argName, argValue);
			}
			return null;
		}

		/**
		 * Validates a set of arguments against the constraints configured in this structure
		 *
		 * @param args The arguments to validate
		 */
		protected void validate(Arguments args) {
			for (ArgumentDefSet argDefs : theAcceptedArguments) {
				argDefs.validate(args);
			}
		}

		@Override
		public String toString() {
			return thePattern.pattern();
		}
	}

	/** Parses arguments */
	public static class ArgumentParser implements ArgumentPatternCreator {
		private List<ArgumentPattern> theArgPatterns;
		private boolean isAcceptingUnmatched;

		/** Creates an argument parser with default settings: Parsing according to "--(.+)=(.*)" and not accepting generic arguments */
		public ArgumentParser() {
			theArgPatterns = new ArrayList<>();
			reset();
		}

		@Override
		public ArgumentParser getParser() {
			return this;
		}

		/**
		 * Removes all settings from this argument parser
		 *
		 * @return This parser, for chaining
		 */
		public ArgumentParser clear() {
			theArgPatterns.clear();
			return this;
		}

		/**
		 * Returns this parser to default settings
		 *
		 * @return This parser, for chaining
		 * @see ArgumentParsing#defArgs()
		 */
		public ArgumentParser reset() {
			clear();
			forPattern("DEFAULT", "--(.+)=(.*)");
			isAcceptingUnmatched = false;
			return this;
		}

		/**
		 * Causes this parser to accept arguments not matching any configured patterns
		 *
		 * @return This parser, for chaining
		 */
		public ArgumentParser acceptUnmatched() {
			isAcceptingUnmatched = true;
			return this;
		}

		/**
		 * Adds a parsing pattern to this parser
		 *
		 * @param name The name for the argument pattern
		 * @param pattern The pattern to add. Must have exactly 2 capturing groups--one for the argument name and one for the value. The
		 *            groups may be named ("name" and "value"); otherwise it will be assumed that the name is the first group.
		 * @return This parser, for chaining
		 */
		@Override
		public ArgumentPattern forPattern(String name, Pattern pattern) {
			for (ArgumentPattern p : theArgPatterns) {
				if(p.getName().equals(name)) {
					return p;
				}
			}
			ArgumentPattern ret = new ArgumentPattern(this, name, pattern);
			theArgPatterns.add(ret);
			return ret;
		}

		/** @return All argument patterns configured in this parser */
		public List<ArgumentPattern> getPatterns() {
			return theArgPatterns;
		}

		/** @return Whether this parser accepts generic (unnamed) arguments. False by default. */
		public boolean isAcceptingUnmatched() {
			return isAcceptingUnmatched;
		}

		/**
		 * @param args
		 *            The command-line arguments to parse
		 * @return The parsed argument set
		 */
		public Arguments parse(String... args) {
			Arguments ret = new Arguments(this);
			for (String arg : args) {
				boolean found = false;
				for (ArgumentPattern pattern : theArgPatterns) {
					Argument argument = pattern.parse(arg);
					if (argument != null) {
						found = true;
						ret.add(argument);
						break;
					}
				}
				if (!found) {
					if (isAcceptingUnmatched) {
						ret.add(new UnmatchedArgument(null, null, arg));
					} else {
						throw new IllegalStateException("Argument \"" + arg + "\" does not match any specified argument patterns");
					}
				}
			}
			for (ArgumentPattern pattern : theArgPatterns) {
				pattern.validate(ret);
			}
			return ret;
		}
	}

	/**
	 * Sample code for parsing arguments
	 * @param args The arguments to parse
	 */
	public static void sample(String [] args){
		ArgumentParser argParser = new ArgumentParser();
		Predicate<Object> isJar = obj -> ((String) obj).contains("jar");
		argParser
		/**/.forDefaultPattern().accept(defArgs()
			/*    */.stringArg("lookup").times(0, Integer.MAX_VALUE)
			/*    */.stringArg("project").required()
			/*    */.stringArg("op").required().only("build", "jar", "build,jar")
			/*    */.stringArg("target-class").requires("op", isJar)
			/*    */.stringArg("dest").requiredIf("op", isJar).requires("op", isJar))
		/**/.forPattern("MF", "--mf:(.+)=(.*)").acceptUnmatched()
		/**/.forDefaultFlagPattern().accept(defArgs()
			/*    */.flagArg("solo"));
		@SuppressWarnings("unused")
		Arguments argSet;
		try {
			argSet = argParser.parse(args);
		} catch (RuntimeException e) {
			System.out.println(ArrayUtils.toString(args));
			e.printStackTrace();
			return;
		}
	}
}
