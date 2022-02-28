package org.qommons;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.AbstractQueue;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.TimeZone;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Parses a set of command-line arguments passed to a program.
 * 
 * @deprecated Use {@link ArgumentParsing2} instead.
 * @see ArgumentParsing2#build()
 */
@Deprecated
public class ArgumentParsing {
	/** Matches field1.field2.field3, etc. for referring to sub-fields */
	public static final Pattern MEMBER_PATTERN = Pattern.compile("([^\\.]*)\\.(.+)");

	private ArgumentParsing() {}

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
		 * @param <E> The type of the enum
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
		 * Creates a duration-type argument
		 * 
		 * @param name The name of the argument
		 * @return The new argument definition
		 */
		DurationArgumentDef durationArg(String name);

		/**
		 * Creates an instant-type argument
		 * 
		 * @param name The name of the argument
		 * @return The new argument definition
		 */
		InstantArgumentDef instantArg(String name);

		/**
		 * Creates a file- or directory-type argument
		 * 
		 * @param name The name of the argument
		 * @return The new argument definition
		 */
		FileArgumentDef fileArg(String name);

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
	public interface ArgumentDef<A extends ArgumentDef<A>> extends ArgumentDefCreator, ArgumentPatternCreator {
		/** @return This argument's name */
		String getName();

		/** @return The described purpose of this argument */
		String getDescription();

		/** @return The argument pattern set that this argument is a part of */
		ArgumentPattern getArgPattern();

		/**
		 * @param description The description for this argument
		 * @return This argument definition, for chaining
		 */
		A describe(String description);

		/**
		 * Sets constraints on the number of times this argument can occur in an argument set
		 * 
		 * @param min The minimum number of times the argument can occur
		 * @param max The maximum number of times the argument can occur
		 * @return This definition, for chaining
		 */
		A times(int min, int max);

		/**
		 * Specifies that this argument may be present any number of times in an argument list. The {@link #getMinTimes() minimum} value is
		 * preserved.
		 * 
		 * @return This definition, for chaining
		 */
		default A anyTimes() {
			return times(getMinTimes(), Integer.MAX_VALUE);
		}

		/** @return The minimum number of times this argument must be specified for the argument set to be valid */
		int getMinTimes();

		/** @return The maximum number of times this argument may be specified for the argument set to be valid */
		int getMaxTimes();

		/**
		 * @param constraint The constraint to add to this definition
		 * @return This definition, for chaining
		 */
		A addConstraint(ArgumentConstraint constraint);

		/** @return All constraints added to this definition */
		Collection<ArgumentConstraint> getConstraints();

		// Positive constraints requiring a target argument depending on this argument's presence
		/**
		 * Asserts that the given argument must be present when this one is
		 * 
		 * @param targetArgName The name of the target argument
		 * @return This definition, for chaining
		 */
		default A requires(String targetArgName) {
			return addConstraint(new ArgumentConstraint(this, targetArgName, false, true));
		}

		/**
		 * Asserts that the given argument must have the given value when this argument is present
		 * 
		 * @param targetArgName The name of the target argument
		 * @param targetValue The value for the target argument
		 * @return This definition, for chaining
		 */
		default A requires(String targetArgName, Object targetValue) {
			return addConstraint(new ArgumentConstraint(this, targetArgName, targetValue, false, true));
		}

		/**
		 * Asserts that the given argument's value must pass the given test when this argument is present
		 * 
		 * @param targetArgName The name of the target argument
		 * @param targetValueCheck The value check for the target argument
		 * @return This definition, for chaining
		 */
		default A requires(String targetArgName, Predicate<Object> targetValueCheck) {
			return addConstraint(new ArgumentConstraint(this, targetArgName, targetValueCheck, false, true));
		}

		// Negative constraints on a target argument depending on this argument's presence
		/**
		 * Asserts that the given argument must be absent when this one is present
		 * 
		 * @param targetArgName The name of the target argument
		 * @return This definition, for chaining
		 */
		default A requiresNot(String targetArgName) {
			return addConstraint(new ArgumentConstraint(this, targetArgName, false, false));
		}

		/**
		 * Asserts that the given argument must not have the given value when this argument is present
		 * 
		 * @param targetArgName The name of the target argument
		 * @param targetValue The value for the target argument
		 * @return This definition, for chaining
		 */
		default A requiresNot(String targetArgName, Object targetValue) {
			return addConstraint(new ArgumentConstraint(this, targetArgName, targetValue, false, false));
		}

		/**
		 * Asserts that the given argument's value must not pass the given test when this argument is present
		 * 
		 * @param targetArgName The name of the target argument
		 * @param targetValueCheck The value check for the target argument
		 * @return This definition, for chaining
		 */
		default A requiresNot(String targetArgName, Predicate<Object> targetValueCheck) {
			return addConstraint(new ArgumentConstraint(this, targetArgName, targetValueCheck, false, false));
		}

		/**
		 * Makes this definition required, so that every argument set must specify at this argument at least once to be valid
		 * 
		 * @return This definition, for chaining
		 */
		default A required() {
			return times(1, getMaxTimes());
		}

		// Positive constraints on this argument's presence depending on a target argument
		/**
		 * Asserts that this argument must be present if the given one is
		 * 
		 * @param targetArgName The name of the target argument
		 * @return This definition, for chaining
		 */
		default A requiredIf(String targetArgName) {
			return addConstraint(new ArgumentConstraint(this, targetArgName, true, true));
		}

		/**
		 * Asserts that this argument must be present if the given argument has the given value
		 * 
		 * @param targetArgName The name of the target argument
		 * @param targetValue The value for the target argument
		 * @return This definition, for chaining
		 */
		default A requiredIf(String targetArgName, Object targetValue) {
			return addConstraint(new ArgumentConstraint(this, targetArgName, targetValue, true, true));
		}

		/**
		 * Asserts that this argument must be present if the given argument's value passes the given test
		 * 
		 * @param targetArgName The name of the target argument
		 * @param targetValueCheck The value check for the target argument
		 * @return This definition, for chaining
		 */
		default A requiredIf(String targetArgName, Predicate<Object> targetValueCheck) {
			return addConstraint(new ArgumentConstraint(this, targetArgName, targetValueCheck, true, true));
		}

		// Negative constraints on this argument's presence depending on a target argument
		/**
		 * Asserts that this argument must be present if the given one is absent
		 * 
		 * @param targetArgName The name of the target argument
		 * @return This definition, for chaining
		 */
		default A requiredIfNot(String targetArgName) {
			return addConstraint(new ArgumentConstraint(this, targetArgName, true, false));
		}

		/**
		 * Asserts that this argument must be present if the given argument's value does not match the given value
		 * 
		 * @param targetArgName The name of the target argument
		 * @param targetValue The value for the target argument
		 * @return This definition, for chaining
		 */
		default A requiredIfNot(String targetArgName, Object targetValue) {
			return addConstraint(new ArgumentConstraint(this, targetArgName, targetValue, true, false));
		}

		/**
		 * Asserts that this argument must be present if the given argument's value does not pass the given test
		 * 
		 * @param targetArgName The name of the target argument
		 * @param targetValueCheck The value check for the target argument
		 * @return This definition, for chaining
		 */
		default A requiredIfNot(String targetArgName, Predicate<Object> targetValueCheck) {
			return addConstraint(new ArgumentConstraint(this, targetArgName, targetValueCheck, true, false));
		}

		/**
		 * Checks the argument set against this definition's constraints
		 * 
		 * @param args The argument set
		 * @throws IllegalArgumentException If one or more arguments in the set violate constraints defined in this argument definition
		 */
		default void validate(Arguments args) throws IllegalArgumentException {
			int times = args.getArguments(getName()).size();
			if (getMinTimes() == getMaxTimes() && times != getMinTimes()) {
				String msg = getName() + " must be specified exactly ";
				switch (getMinTimes()) {
				case 1:
					msg += "once";
					break;
				case 2:
					msg += "twice";
					break;
				default:
					msg += times + " times";
				}
				msg += ", not " + times;
				throw new IllegalArgumentException(msg);
			}
			if (times < getMinTimes()) {
				String msg = getName() + " must be specified at least ";
				switch (getMinTimes()) {
				case 1:
					msg += "once";
					break;
				case 2:
					msg += "twice";
					break;
				default:
					msg += getMinTimes() + " times";
				}
				msg += ", not " + times;
				throw new IllegalArgumentException(msg);
			}
			if (times > getMaxTimes()) {
				String msg = getName() + " must be specified at most ";
				switch (getMaxTimes()) {
				case 1:
					msg += "once";
					break;
				case 2:
					msg += "twice";
					break;
				default:
					msg += getMaxTimes() + " times";
				}
				msg += ", not " + times;
				throw new IllegalArgumentException(msg);
			}
			for (ArgumentConstraint constraint : getConstraints()) {
				if (!constraint.isValidWith(args)) {
					throw new IllegalArgumentException(constraint.getInvalidMessage());
				}
			}
		}

		/**
		 * @param matcher The matcher from the pattern that parsed this argument
		 * @param value The argument's value text
		 * @param parsed The arguments parsed so far
		 * @param moreArgs Subsequent arguments in the argument list
		 * @return The parsed argument
		 * @throws IllegalArgumentException If the value text cannot be parsed according to this definition's expectations
		 */
		Argument parse(String value, Arguments parsed, Queue<String> moreArgs) throws IllegalArgumentException;
	}

	/** A constraint on the presence of an argument dependent on the presence or value of a different argument */
	public static class ArgumentConstraint {
		private final ArgumentDef<?> theArgument;
		private final String theTarget;
		private final Object theTargetValue;
		private final Predicate<Object> theTargetValueCheck;
		private final boolean isArgumentRequired;
		private final boolean isTargetRequired;

		/**
		 * Makes a constraint that is triggered by the presence or absence of the target argument
		 * 
		 * @param arg The argument whose presence is constrained
		 * @param target The argument whose presence determines if this constraint's argument is required or forbidden
		 * @param argReq Whether this constraint's argument is required or forbidden if the target argument condition is met
		 * @param targetReq Whether the target argument's presence or absence satisfies the target condition
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
		 * Makes a constraint that is triggered by a target argument's value
		 * 
		 * @param arg The argument whose presence is constrained
		 * @param target The argument whose presence determines if this constraint's argument is required or forbidden
		 * @param targetValue The value that is checked for the target argument condition
		 * @param argReq Whether this constraint's argument is required or forbidden if the target argument condition is met
		 * @param targetReq Whether the target argument's value must match or not match the target value to satisfy the target condition
		 */
		public ArgumentConstraint(ArgumentDef<?> arg, String target, Object targetValue, boolean argReq, boolean targetReq) {
			theArgument = arg;
			theTarget = target;
			theTargetValue = targetValue;
			theTargetValueCheck = theTargetValue::equals;
			isArgumentRequired = argReq;
			isTargetRequired = targetReq;
		}

		/**
		 * Makes a constraint that is triggered by a test against target argument's value
		 * 
		 * @param arg The argument whose presence is constrained
		 * @param target The argument whose presence determines if this constraint's argument is required or forbidden
		 * @param targetValueCheck The value check for the target argument condition
		 * @param argReq Whether this constraint's argument is required or forbidden if the target argument condition is met
		 * @param targetReq Whether the target argument's value must pass or fail the target value check to satisfy the target condition
		 */
		public ArgumentConstraint(ArgumentDef<?> arg, String target, Predicate<Object> targetValueCheck, boolean argReq,
			boolean targetReq) {
			theArgument = arg;
			theTarget = target;
			theTargetValue = null;
			theTargetValueCheck = targetValueCheck;
			isArgumentRequired = argReq;
			isTargetRequired = targetReq;
		}

		/** @return The argument definition that this constraint is specified on */
		public ArgumentDef<?> getArgument() {
			return theArgument;
		}

		/** @return The name argument that this constraint references */
		public String getTarget() {
			return theTarget;
		}

		/** @return The target value to satisfy the target condition, or null if this constraint is not for a specific target value */
		public Object getTargetValue() {
			return theTargetValue;
		}

		/** @return The target value check to satisfy the target condition, or null if this constraint does not use a value predicate */
		public Predicate<Object> getTargetValueCheck() {
			return theTargetValueCheck;
		}

		/** @return Whether this constraint's argument is required or forbidden if the target argument condition is met */
		public boolean isArgumentRequired() {
			return isArgumentRequired;
		}

		/**
		 * @return
		 *         <ul>
		 *         <li>If {@link #getTargetValue() target value} and {@link #getTargetValueCheck() target value check} are both null,
		 *         whether the target argument's presence satisfies the target condition</li>
		 *         <li>If {@link #getTargetValue() target value} is non-null, whether the target value must match or not match the target to
		 *         satisfy the target condition</li>
		 *         <li>If {@link #getTargetValueCheck() target value check} is non-null, whether the target value must pass or fail the
		 *         check to satisfy the target condition</li>
		 *         </ul>
		 */
		public boolean isTargetRequired() {
			return isTargetRequired;
		}

		/**
		 * @param args The argument set to test
		 * @return Whether the arguments pass this constraint check
		 */
		public boolean isValidWith(Arguments args) {
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
		 * @return Whether this constraint's argument is present (potentially with a valid value) in the argument set
		 */
		public boolean isArgumentConstraintSatisfied(Arguments args) {
			return args.getArguments(theArgument.getName()).stream().anyMatch(arg -> arg.isSpecified());
		}

		/**
		 * @param args The argument set to test
		 * @return Whether the target argument is present (potentially with a valid value) in the argument set
		 */
		public boolean isTargetConditionActive(Arguments args) {
			return isTargetRequired == isConstraintSatisfied(args, theTarget, theTargetValue, theTargetValueCheck);
		}

		/**
		 * @param args The argument set to test
		 * @param argName The name of the argument to test
		 * @param value The value expected for the argument, or null if the value is not to be checked against a specific value
		 * @param test The value check for the argument, or null if the value is not to be checked against a predicate
		 * @return Whether the constraint is satisfied
		 */
		@SuppressWarnings("static-method")
		protected boolean isConstraintSatisfied(Arguments args, String argName, Object value, Predicate<?> test) {
			List<Argument> targetArgs = args.getArguments(argName);
			if (test == null) {
				return !targetArgs.isEmpty();
			} else {
				boolean found = false;
				for (Argument targetArg : targetArgs) {
					if (targetArg instanceof ValuedArgument
						&& ((Predicate<Object>) test).test(((ValuedArgument<?>) targetArg).getValue())) {
						found = true;
						break;
					}
				}
				return found;
			}
		}

		/** @return A message to convey to the user why this constraint check failed against an argument set */
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
	 * An argument definition that has a value
	 * 
	 * @param <T> The type of the argument's value
	 * @param <A> The sub-type of this argument definition
	 */
	public interface ValuedArgumentDef<T, A extends ValuedArgumentDef<T, A>> extends ArgumentDef<A> {
		// Positive constraints on a target argument based on this argument's value
		/**
		 * Asserts that a target argument must be present if this argument's value matches a given value
		 * 
		 * @param value The value for this argument
		 * @param targetArgName The name of the target argument
		 * @return This definition, for chaining
		 */
		default A requiresIfValue(T value, String targetArgName) {
			return addConstraint(new ArgumentValueConstraint<>(this, value, targetArgName, false, true));
		}

		/**
		 * Asserts that a target argument must be present if this argument's value passes a test
		 * 
		 * @param valueCheck The test for this argument's value
		 * @param targetArgName The name of the target argument
		 * @return This definition, for chaining
		 */
		default A requiresIfValue(Predicate<? super T> valueCheck, String targetArgName) {
			return addConstraint(new ArgumentValueConstraint<>(this, valueCheck, targetArgName, false, true));
		}

		/**
		 * Asserts that a target argument must have a given value if this argument's value matches a given value
		 * 
		 * @param value The value for this argument
		 * @param targetArgName The name of the target argument
		 * @param targetValue The value for the target argument if this argument has the given value
		 * @return This definition, for chaining
		 */
		default A requiresIfValue(T value, String targetArgName, Object targetValue) {
			return addConstraint(new ArgumentValueConstraint<>(this, value, targetArgName, targetValue, false, true));
		}

		/**
		 * Asserts that a target argument's value must pass a test if this argument's value matches a given value
		 * 
		 * @param value The value for this argument
		 * @param targetArgName The name of the target argument
		 * @param targetValueCheck The value check that the target argument's value must pass if this argument has the given value
		 * @return This definition, for chaining
		 */
		default A requiresIfValue(T value, String targetArgName, Predicate<Object> targetValueCheck) {
			return addConstraint(new ArgumentValueConstraint<>(this, value, targetArgName, targetValueCheck, false, true));
		}

		/**
		 * Asserts that a target argument's value must match a given value if this argument's value passes the test
		 * 
		 * @param valueCheck The value check for this argument's value
		 * @param targetArgName The name of the target argument
		 * @param targetValue The value for the target argument if this argument's value passes the given test
		 * @return This definition, for chaining
		 */
		default A requiresIfValue(Predicate<? super T> valueCheck, String targetArgName, Object targetValue) {
			return addConstraint(new ArgumentValueConstraint<>(this, valueCheck, targetArgName, targetValue, false, true));
		}

		/**
		 * Asserts that a target argument's value must pass a test if this argument's value passes a different test
		 * 
		 * @param valueCheck The value check for this argument's value
		 * @param targetArgName The name of the target argument
		 * @param targetValueCheck The value check that the target argument's value must pass if this argument's value passes its test
		 * @return This definition, for chaining
		 */
		default A requiresIfValue(Predicate<? super T> valueCheck, String targetArgName, Predicate<Object> targetValueCheck) {
			return addConstraint(new ArgumentValueConstraint<>(this, valueCheck, targetArgName, targetValueCheck, false, true));
		}

		// Negative constraints on a target argument based on this argument's value
		/**
		 * The opposite of {@link #requiresIfValue(Object, String)}. Asserts that a target argument must NOT be present if this argument's
		 * value matches a given value.
		 * 
		 * @param value The value for this argument
		 * @param targetArgName The name of the target argument
		 * @return This definition, for chaining
		 */
		default A requiresIfNotValue(T value, String targetArgName) {
			return addConstraint(new ArgumentValueConstraint<>(this, value, targetArgName, false, false));
		}

		/**
		 * The opposite of {@link #requiresIfValue(Predicate, String)}. Asserts that a target argument must NOT be present if this
		 * argument's value passes a test.
		 * 
		 * @param valueCheck The test for this argument's value
		 * @param targetArgName The name of the target argument
		 * @return This definition, for chaining
		 */
		default A requiresNotIfValue(Predicate<? super T> valueCheck, String targetArgName) {
			return addConstraint(new ArgumentValueConstraint<>(this, valueCheck, targetArgName, false, false));
		}

		/**
		 * The opposite of {@link #requiresIfValue(Object, String, Object)}. Asserts that a target argument must NOT have a given value if
		 * this argument's value matches a given value.
		 * 
		 * @param value The value for this argument
		 * @param targetArgName The name of the target argument
		 * @param targetValue The forbidden value for the target argument if this argument has the given value
		 * @return This definition, for chaining
		 */
		default A requiresNotIfValue(T value, String targetArgName, Object targetValue) {
			return addConstraint(new ArgumentValueConstraint<>(this, value, targetArgName, targetValue, false, false));
		}

		/**
		 * The opposite of {@link #requiresIfValue(Object, String, Predicate)}. Asserts that a target argument's value must NOT pass a test
		 * if this argument's value matches a given value.
		 * 
		 * @param value The value for this argument
		 * @param targetArgName The name of the target argument
		 * @param targetValueCheck The value check that the target argument's value must fail if this argument has the given value
		 * @return This definition, for chaining
		 */
		default A requiresNotIfValue(T value, String targetArgName, Predicate<Object> targetValueCheck) {
			return addConstraint(new ArgumentValueConstraint<>(this, value, targetArgName, targetValueCheck, false, false));
		}

		/**
		 * The opposite of {@link #requiresIfValue(Predicate, String, Object)}. Asserts that a target argument's value must NOT match a
		 * given value if this argument's value passes the test.
		 * 
		 * @param valueCheck The value check for this argument's value
		 * @param targetArgName The name of the target argument
		 * @param targetValue The forbidden value for the target argument if this argument's value passes the given test
		 * @return This definition, for chaining
		 */
		default A requiresNotIfValue(Predicate<? super T> valueCheck, String targetArgName, Object targetValue) {
			return addConstraint(new ArgumentValueConstraint<>(this, valueCheck, targetArgName, targetValue, false, false));
		}

		/**
		 * The opposite of {@link #requiresIfValue(Predicate, String, Predicate)}. Asserts that a target argument's value must NOT pass a
		 * test if this argument's value passes a different test.
		 * 
		 * @param valueCheck The value check for this argument's value
		 * @param targetArgName The name of the target argument
		 * @param targetValueCheck The value check that the target argument's value must fail if this argument's value passes its test
		 * @return This definition, for chaining
		 */
		default A requiresNotIfValue(Predicate<? super T> valueCheck, String targetArgName, Predicate<Object> targetValueCheck) {
			return addConstraint(new ArgumentValueConstraint<>(this, valueCheck, targetArgName, targetValueCheck, false, false));
		}

		// Positive constraints on this argument's value based on a target argument
		/**
		 * Asserts that this argument's value must match a given value if a target argument is present
		 * 
		 * @param value The value for this argument if the target argument is present
		 * @param targetArgName The name of the target argument
		 * @return This definition, for chaining
		 */
		default A requiresValueIf(T value, String targetArgName) {
			return addConstraint(new ArgumentValueConstraint<>(this, value, targetArgName, true, true));
		}

		/**
		 * Asserts that this argument's value must pass a given test if a target argument is present
		 * 
		 * @param valueCheck The check that this argument's value must pass if the target argument is present
		 * @param targetArgName The name of the target argument
		 * @return This definition, for chaining
		 */
		default A requiresValueIf(Predicate<? super T> valueCheck, String targetArgName) {
			return addConstraint(new ArgumentValueConstraint<>(this, valueCheck, targetArgName, true, true));
		}

		/**
		 * Asserts that this argument's value must match a given value if a target argument's value matches a different value
		 * 
		 * @param value The value for this argument if the target argument's value matches the value given for it
		 * @param targetArgName The name of the target argument
		 * @param targetValue The value for the target argument
		 * @return This definition, for chaining
		 */
		default A requiresValueIf(T value, String targetArgName, Object targetValue) {
			return addConstraint(new ArgumentValueConstraint<>(this, value, targetArgName, targetValue, true, true));
		}

		/**
		 * Asserts that this argument's value must match a given value if a target argument's value passes a test
		 * 
		 * @param value The value for this argument if the target argument's value passes the given test
		 * @param targetArgName The name of the target argument
		 * @param targetValueCheck The value check for the target argument
		 * @return This definition, for chaining
		 */
		default A requiresValueIf(T value, String targetArgName, Predicate<Object> targetValueCheck) {
			return addConstraint(new ArgumentValueConstraint<>(this, value, targetArgName, targetValueCheck, true, true));
		}

		/**
		 * Asserts that this argument's value must pass a given test if a target argument's value matches a given value
		 * 
		 * @param valueCheck The check that this argument's value must pass if the target argument's value matches the value given for it
		 * @param targetArgName The name of the target argument
		 * @param targetValue The value for the target argument
		 * @return This definition, for chaining
		 */
		default A requiresValueIf(Predicate<? super T> valueCheck, String targetArgName, Object targetValue) {
			return addConstraint(new ArgumentValueConstraint<>(this, valueCheck, targetArgName, targetValue, true, true));
		}

		/**
		 * Asserts that this argument's value must pass a given test if a target argument's value passes a different test
		 * 
		 * @param valueCheck The check that this argument's value must pass if the target argument's value passes the test given for it
		 * @param targetArgName The name of the target argument
		 * @param targetValueCheck The value check for the target argument
		 * @return This definition, for chaining
		 */
		default A requiresValueIf(Predicate<? super T> valueCheck, String targetArgName, Predicate<Object> targetValueCheck) {
			return addConstraint(new ArgumentValueConstraint<>(this, valueCheck, targetArgName, targetValueCheck, true, true));
		}

		// Positive constraints on this argument's value based on a target argument
		/**
		 * The opposite of {@link #requiresValueIf(Object, String)}. Asserts that this argument's value must NOT match a given value if a
		 * target argument is present.
		 * 
		 * @param value The forbidden value for this argument if the target argument is present
		 * @param targetArgName The name of the target argument
		 * @return This definition, for chaining
		 */
		default A requiresNotValueIf(T value, String targetArgName) {
			return addConstraint(new ArgumentValueConstraint<>(this, value, targetArgName, true, false));
		}

		/**
		 * The opposite of {@link #requiresValueIf(Predicate, String)}. Asserts that this argument's value must NOT pass a given test if a
		 * target argument is present.
		 * 
		 * @param valueCheck The check that this argument's value must fail if the target argument is present
		 * @param targetArgName The name of the target argument
		 * @return This definition, for chaining
		 */
		default A requiresNotValueIf(Predicate<? super T> valueCheck, String targetArgName) {
			return addConstraint(new ArgumentValueConstraint<>(this, valueCheck, targetArgName, true, false));
		}

		/**
		 * The opposite of {@link #requiresValueIf(Object, String, Object)}. Asserts that this argument's value must match NOT a given value
		 * if a target argument's value matches a different value.
		 * 
		 * @param value The forbidden value for this argument if the target argument's value matches the value given for it
		 * @param targetArgName The name of the target argument
		 * @param targetValue The value for the target argument
		 * @return This definition, for chaining
		 */
		default A requiresNotValueIf(T value, String targetArgName, Object targetValue) {
			return addConstraint(new ArgumentValueConstraint<>(this, value, targetArgName, targetValue, true, false));
		}

		/**
		 * The opposite of {@link #requiresValueIf(Object, String, Predicate)}. Asserts that this argument's value must NOT match a given
		 * value if a target argument's value passes a test.
		 * 
		 * @param value The forbidden value for this argument if the target argument's value passes the given test
		 * @param targetArgName The name of the target argument
		 * @param targetValueCheck The value check for the target argument
		 * @return This definition, for chaining
		 */
		default A requiresNotValueIf(T value, String targetArgName, Predicate<Object> targetValueCheck) {
			return addConstraint(new ArgumentValueConstraint<>(this, value, targetArgName, targetValueCheck, true, false));
		}

		/**
		 * The opposite of {@link #requiresValueIf(Predicate, String, Object)}. Asserts that this argument's value must NOT pass a given
		 * test if a target argument's value matches a given value.
		 * 
		 * @param valueCheck The check that this argument's value must fail if the target argument's value matches the value given for it
		 * @param targetArgName The name of the target argument
		 * @param targetValue The value for the target argument
		 * @return This definition, for chaining
		 */
		default A requiresNotValueIf(Predicate<? super T> valueCheck, String targetArgName, Object targetValue) {
			return addConstraint(new ArgumentValueConstraint<>(this, valueCheck, targetArgName, targetValue, true, false));
		}

		/**
		 * The opposite of {@link #requiresValueIf(Predicate, String, Predicate)}. Asserts that this argument's value must NOT pass a given
		 * test if a target argument's value passes a different test.
		 * 
		 * @param valueCheck The check that this this argument's value must fail if the target argument's value passes the test given for it
		 * @param targetArgName The name of the target argument
		 * @param targetValueCheck The value check for the target argument
		 * @return This definition, for chaining
		 */
		default A requiresNotValueIf(Predicate<? super T> valueCheck, String targetArgName, Predicate<Object> targetValueCheck) {
			return addConstraint(new ArgumentValueConstraint<>(this, valueCheck, targetArgName, targetValueCheck, true, false));
		}

		/**
		 * Asserts that this argument's value must pass a test
		 * 
		 * @param constraint The test that the value of arguments of this type must pass
		 * @return This definition, for chaining
		 */
		default A constrain(Predicate<? super T> constraint) {
			return constrain(null, constraint);
		}

		/**
		 * Asserts that this argument must pass a test. With this method, as opposed to {@link #constrain(Predicate)}, the constraint may be
		 * named, which may improve debuggability if the constraint fails.
		 * 
		 * @param constraintName The name for the constraint in error messages
		 * @param constraint The test that the value of arguments of this type must pass
		 * @return This definition, for chaining
		 */
		A constrain(String constraintName, Predicate<? super T> constraint);

		/**
		 * Asserts that arguments of this type must have values matching one of the given values
		 * 
		 * @param values The possible values for arguments of this type
		 * @return This definition, for chaining
		 */
		A only(T... values);

		/**
		 * @param defValue The value that this argument will have if the argument is not specified in the argument list
		 * @return This definition, for chaining
		 */
		A defValue(T defValue);

		/**
		 * @param defCreator Dynamically supplies the value for this argument if it is not specified in the argument list
		 * @return This definition, for chaining
		 */
		A defValue(Supplier<? extends T> defCreator);

		/** @return The default value to use for this argument if it is not specified in the argument list */
		T getDefault();

		@Override
		default Argument parse(String value, Arguments parsed, Queue<String> moreArgs) {
			return createArgument(parse(value, parsed), true);
		}

		/**
		 * Creates an argument of this type from text and a parsed value
		 * 
		 * @param value The value of the argument
		 * @param specified Whether this argument was specified by the user or supplied by the argument definition as a default
		 * @return The new argument
		 */
		Argument createArgument(T value, boolean specified);

		/**
		 * Parses the value of an argument of this type from text
		 * 
		 * @param value The value text for the argument
		 * @param parsed The arguments parsed so far
		 * @return The parsed value
		 * @throws IllegalArgumentException If the value could not be parsed
		 */
		T parse(String value, Arguments parsed) throws IllegalArgumentException;
	}

	/**
	 * An argument definition that has a comparable value
	 * 
	 * @param <T> The type of the value
	 * @param <A> The sub-type of the argument definition
	 */
	public interface ComparableArgumentDef<T extends Comparable<T>, A extends ComparableArgumentDef<T, A>> extends ValuedArgumentDef<T, A> {
		/**
		 * @param min The minimum value for arguments of this type
		 * @return This definition, for chaining
		 */
		A atLeast(T min);

		/**
		 * @param max The maximum value for arguments of this type
		 * @return This definition, for chaining
		 */
		A atMost(T max);

		/**
		 * @param min The minimum value for arguments of this type
		 * @param max The maximum value for arguments of this type
		 * @return This definition, for chaining
		 */
		A between(T min, T max);
	}

	/**
	 * A constraint on the value of an argument
	 * 
	 * @param <T> The type of the value of the argument to constrain
	 */
	public static class ArgumentValueConstraint<T> extends ArgumentConstraint {
		private final T theArgumentValue;
		private final Predicate<? super T> theArgumentValueCheck;

		/**
		 * Makes a value constraint that is triggered by the presence or absence of the target argument
		 * 
		 * @param arg The argument to constraint
		 * @param argValue The value for the argument
		 * @param target The name of the target argument
		 * @param argReq Whether this constraint's argument must match or not match the given value if the target argument condition is met
		 * @param targetReq Whether the target argument's presence or absence satisfies the target condition
		 */
		public ArgumentValueConstraint(ValuedArgumentDef<T, ?> arg, T argValue, String target, boolean argReq, boolean targetReq) {
			super(arg, target, argReq, targetReq);
			theArgumentValue = argValue;
			theArgumentValueCheck = theArgumentValue::equals;
		}

		/**
		 * Makes a value check constraint that is triggered by the presence or absence of the target argument
		 * 
		 * @param arg The argument to constraint
		 * @param argValueCheck The value check for the argument
		 * @param target The name of the target argument
		 * @param argReq Whether this constraint's argument must pass or fail the given test if the target argument condition is met
		 * @param targetReq Whether the target argument's value must pass or fail the target value check to satisfy the target condition
		 */
		public ArgumentValueConstraint(ValuedArgumentDef<T, ?> arg, Predicate<? super T> argValueCheck, String target, boolean argReq,
			boolean targetReq) {
			super(arg, target, argReq, targetReq);
			theArgumentValue = null;
			theArgumentValueCheck = argValueCheck;
		}

		/**
		 * Makes a value constraint that is triggered by a target argument's value
		 * 
		 * @param arg The argument to constraint
		 * @param argValue The value for the argument
		 * @param target The name of the target argument
		 * @param targetValue The value that is checked for the target argument condition
		 * @param argReq Whether this constraint's argument must match or not match the given value if the target argument condition is met
		 * @param targetReq Whether the target argument's value must match or not match the target value to satisfy the target condition
		 */
		public ArgumentValueConstraint(ValuedArgumentDef<T, ?> arg, T argValue, String target, Object targetValue, boolean argReq,
			boolean targetReq) {
			super(arg, target, targetValue, argReq, targetReq);
			theArgumentValue = argValue;
			theArgumentValueCheck = theArgumentValue::equals;
		}

		/**
		 * Makes a value check constraint that is triggered by a target argument's value
		 * 
		 * @param arg The argument to constraint
		 * @param argValueCheck The value check for the argument
		 * @param target The name of the target argument
		 * @param targetValue The value that is checked for the target argument condition
		 * @param argReq Whether this constraint's argument must pass or fail the given test if the target argument condition is met
		 * @param targetReq Whether the target argument's value must match or not match the target value to satisfy the target condition
		 */
		public ArgumentValueConstraint(ValuedArgumentDef<T, ?> arg, Predicate<? super T> argValueCheck, String target, Object targetValue,
			boolean argReq, boolean targetReq) {
			super(arg, target, targetValue, argReq, targetReq);
			theArgumentValue = null;
			theArgumentValueCheck = argValueCheck;
		}

		/**
		 * Makes a value constraint that is triggered by a test against target argument's value
		 * 
		 * @param arg The argument to constraint
		 * @param argValue The value for the argument
		 * @param target The name of the target argument
		 * @param targetValueCheck The value check for the target argument condition
		 * @param argReq Whether this constraint's argument must match or not match the given value if the target argument condition is met
		 * @param targetReq Whether the target argument's value must pass or fail the target value check to satisfy the target condition
		 */
		public ArgumentValueConstraint(ValuedArgumentDef<T, ?> arg, T argValue, String target, Predicate<Object> targetValueCheck,
			boolean argReq, boolean targetReq) {
			super(arg, target, targetValueCheck, argReq, targetReq);
			theArgumentValue = argValue;
			theArgumentValueCheck = theArgumentValue::equals;
		}

		/**
		 * Makes a value check constraint that is triggered by a test against target argument's value
		 * 
		 * @param arg The argument to constraint
		 * @param argValueCheck The value check for the argument
		 * @param target The name of the target argument
		 * @param targetValueCheck The value check for the target argument condition
		 * @param argReq Whether this constraint's argument must pass or fail the given test if the target argument condition is met
		 * @param targetReq Whether the target argument's value must pass or fail the target value check to satisfy the target condition
		 */
		public ArgumentValueConstraint(ValuedArgumentDef<T, ?> arg, Predicate<? super T> argValueCheck, String target,
			Predicate<Object> targetValueCheck, boolean argReq, boolean targetReq) {
			super(arg, target, targetValueCheck, argReq, targetReq);
			theArgumentValue = null;
			theArgumentValueCheck = argValueCheck;
		}

		/** @return The value to constrain the argument to, or null if this constraint is not for a specific value */
		public T getArgValue() {
			return theArgumentValue;
		}

		/** @return The value check to constrain the argument with, or null if this constraint is not for a predicate check */
		public Predicate<? super T> getArgValueCheck() {
			return theArgumentValueCheck;
		}

		@Override
		public boolean isArgumentConstraintSatisfied(Arguments args) {
			return isConstraintSatisfied(args, getArgument().getName(), theArgumentValue, theArgumentValueCheck);
		}

		@Override
		public String getInvalidMessage() {
			if (getTargetValueCheck() != null) {
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
			} else {
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
			}
		}
	}

	private static abstract class ArgumentDefImpl<A extends ArgumentDefImpl<A>> implements ArgumentDef<A> {
		private final ArgumentPattern thePattern;
		private final String theName;
		private String theDescription;
		private int theMinTimes = 0;
		private int theMaxTimes = 1;
		private List<ArgumentConstraint> theConstraints;

		protected ArgumentDefImpl(ArgumentPattern pattern, String name) {
			thePattern = pattern;
			theName = name;
			theConstraints = new ArrayList<>();
		}

		@Override
		public ArgumentPattern getArgPattern() {
			return thePattern;
		}

		@Override
		public String getName() {
			return theName;
		}

		@Override
		public String getDescription() {
			return theDescription;
		}

		@Override
		public A describe(String description) {
			theDescription = description;
			return (A) this;
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
			return thePattern.stringArg(name);
		}

		@Override
		public PatternArgumentDef patternArg(String name, Pattern pattern) {
			return thePattern.patternArg(name, pattern);
		}

		@Override
		public IntArgumentDef intArg(String name) {
			return thePattern.intArg(name);
		}

		@Override
		public FloatArgumentDef floatArg(String name) {
			return thePattern.floatArg(name);
		}

		@Override
		public <E extends Enum<E>> EnumArgumentDef<E> enumArg(String name, Class<E> type) {
			return thePattern.enumArg(name, type);
		}

		@Override
		public BooleanArgumentDef booleanArg(String name) {
			return thePattern.booleanArg(name);
		}

		@Override
		public DurationArgumentDef durationArg(String name) {
			return thePattern.durationArg(name);
		}

		@Override
		public InstantArgumentDef instantArg(String name) {
			return thePattern.instantArg(name);
		}

		@Override
		public FileArgumentDef fileArg(String name) {
			return thePattern.fileArg(name);
		}

		@Override
		public FlagArgumentDef flagArg(String name) {
			return thePattern.flagArg(name);
		}

		@Override
		public ArgumentParser getParser() {
			return thePattern.getParser();
		}

		@Override
		public String toString() {
			StringBuilder ret = new StringBuilder(theName);
			if (theMinTimes > 0 || theMaxTimes != 1) {
				ret.append('(').append(theMinTimes);
				if (theMaxTimes == Integer.MAX_VALUE) {
					ret.append('+');
				} else {
					ret.append("...").append(theMaxTimes);
				}
				ret.append(')');
			}
			return theName;
		}
	}

	private static abstract class ValuedArgumentDefImpl<T, A extends ValuedArgumentDefImpl<T, A>> extends ArgumentDefImpl<A>
		implements ValuedArgumentDef<T, A> {
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

		protected ValuedArgumentDefImpl(ArgumentPattern pattern, String name) {
			super(pattern, name);
			if (pattern != null) {
				if (!pattern.isWithValue())
					throw new IllegalArgumentException(
						"Valued arguments (\"" + getName() + "\") cannot be used by un-valued patterns (\"" + pattern + "\")");
				else if (name != null && !pattern.isWithName())
					throw new IllegalArgumentException(
						"Named arguments (\"" + getName() + "\") cannot be used by un-named patterns (\"" + pattern + "\")");
			}
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
		public T parse(String value, Arguments parsed) throws IllegalArgumentException {
			T ret = parseValue(value, parsed);
			checkValue(value, ret);
			return ret;
		}

		protected abstract T parseValue(String text, Arguments parsed) throws IllegalArgumentException;

		protected void checkValue(String text, T value) throws IllegalArgumentException {
			for (NamedConstraint c : theValueConstraints) {
				if (!c.theConstraint.test(value)) {
					throw new IllegalArgumentException(getName() + " value " + value + " violates constraint " + c.theName);
				}
			}
			if (theOnlyValues != null && !ArrayUtils.contains(theOnlyValues, value)) {
				throw new IllegalArgumentException(getName() + " value " + value + " does not match any of the possible values");
			}
		}
	}

	private static abstract class ComparableArgumentDefImpl<T extends Comparable<T>, A extends ComparableArgumentDefImpl<T, A>>
		extends ValuedArgumentDefImpl<T, A> implements ComparableArgumentDef<T, A> {
		private T theMin;
		private T theMax;

		protected ComparableArgumentDefImpl(ArgumentPattern pattern, String name) {
			super(pattern, name);
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

	/** The definition of a flag argument */
	public static class FlagArgumentDef extends ArgumentDefImpl<FlagArgumentDef> {
		private ArgumentParser theSubArgParser;

		/**
		 * @param pattern The argument pattern that this argument is for
		 * @param name This argument's name
		 */
		public FlagArgumentDef(ArgumentPattern pattern, String name) {
			super(pattern, name);
			if (pattern != null && pattern.isWithValue()) {
				throw new IllegalArgumentException("Flag arguments cannot be used by valued patterns (\"" + getName() + "\")");
			}
		}

		/**
		 * Adds a parser for sub-arguments that are evaluated as a part of this argument. The sub argument parser is greedy, so it will
		 * consume as many subsequent arguments as it can understand, so care is necessary in coordinating the patterns in the argument set
		 * and the sub-argument set. In particular, if the given parser is {@link ArgumentParser#isAcceptingUnmatched() accepting unmatched}
		 * arguments, this argument will always be the last argument parsed and any subsequent arguments will be sub-arguments.
		 * 
		 * @param subArgsParser The parser to parse sub-arguments for this argument
		 * @return This definition, for chaining
		 */
		public FlagArgumentDef sub(ArgumentParser subArgsParser) {
			if (subArgsParser == getParser()) {
				throw new IllegalArgumentException("Cannot set an argument's own parser as its sub-argument parser.  Create a new parser.");
			}
			theSubArgParser = subArgsParser;
			return this;
		}

		/**
		 * A shortcut to {@link #sub(ArgumentParser)} that allows sub-arguments to be added to the parser inline
		 * 
		 * @param subArgs An argument definition belonging to an argument parser to parse sub-arguments for this argument
		 * @return This definition, for chaining
		 */
		public FlagArgumentDef sub(ArgumentDef<?> subArgs) {
			return sub(subArgs.getParser());
		}

		/** @return This argument's sub-argument parser */
		public ArgumentParser getSubArgs() {
			return theSubArgParser;
		}

		@Override
		public Argument parse(String value, Arguments parsed, Queue<String> moreArgs) {
			Arguments subArgs = null;
			if (theSubArgParser != null) {
				subArgs = theSubArgParser.parse(moreArgs, false);
			}
			return new FlagArgument(this, subArgs, true);
		}

		@Override
		public String toString() {
			return super.toString() + " (flag)";
		}
	}

	/** The definition of a string-valued argument */
	public static class StringArgumentDef extends ValuedArgumentDefImpl<String, StringArgumentDef> {
		/**
		 * @param pattern The argument pattern that this argument is for
		 * @param name This argument's name
		 */
		public StringArgumentDef(ArgumentPattern pattern, String name) {
			super(pattern, name);
		}

		@Override
		public Argument createArgument(String value, boolean specified) {
			return new ValuedArgument<>(this, value, specified);
		}

		@Override
		protected String parseValue(String text, Arguments parsed) throws IllegalArgumentException {
			return text;
		}

		@Override
		public String toString() {
			return super.toString() + " (string)";
		}
	}

	/** The definition of a string-valued argument whose value must match a pattern */
	public static class PatternArgumentDef extends ValuedArgumentDefImpl<String, PatternArgumentDef> {
		private final Pattern thePattern;

		/**
		 * @param argPattern The argument pattern that this argument is for
		 * @param name This argument's name
		 * @param pattern The pattern that values of this argument must obey
		 */
		public PatternArgumentDef(ArgumentPattern argPattern, String name, Pattern pattern) {
			super(argPattern, name);
			thePattern = pattern;
		}

		/** @return The pattern that values of this argument must obey */
		public Pattern getPattern() {
			return thePattern;
		}

		@Override
		public Argument createArgument(String value, boolean specified) {
			return new ValuedArgument<>(this, value, specified);
		}

		@Override
		protected String parseValue(String text, Arguments parsed) throws IllegalArgumentException {
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

	/** The definition of a integer-valued argument */
	public static class IntArgumentDef extends ComparableArgumentDefImpl<Long, IntArgumentDef> {
		/**
		 * @param pattern The argument pattern that this argument is for
		 * @param name This argument's name
		 */
		public IntArgumentDef(ArgumentPattern pattern, String name) {
			super(pattern, name);
		}

		/**
		 * @param min The minimum value for arguments of this type
		 * @return This definition, for chaining
		 */
		public IntArgumentDef atLeast(int min) {
			return super.atLeast(Long.valueOf(min));
		}

		/**
		 * @param max The maximum value for arguments of this type
		 * @return This definition, for chaining
		 */
		public IntArgumentDef atMost(int max) {
			return super.atMost(Long.valueOf(max));
		}

		/**
		 * @param min The minimum value for arguments of this type
		 * @param max The maximum value for arguments of this type
		 * @return This definition, for chaining
		 */
		public IntArgumentDef between(int min, int max) {
			return super.between(Long.valueOf(min), Long.valueOf(max));
		}

		@Override
		public Argument createArgument(Long value, boolean specified) {
			return new ValuedArgument<>(this, value, specified);
		}

		@Override
		protected Long parseValue(String text, Arguments parsed) throws IllegalArgumentException {
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

	/** The definition of a floating-point-valued argument */
	public static class FloatArgumentDef extends ComparableArgumentDefImpl<Double, FloatArgumentDef> {
		/**
		 * @param pattern The argument pattern that this argument is for
		 * @param name This argument's name
		 */
		public FloatArgumentDef(ArgumentPattern pattern, String name) {
			super(pattern, name);
		}

		@Override
		public Argument createArgument(Double value, boolean specified) {
			return new ValuedArgument<>(this, value, specified);
		}

		@Override
		protected Double parseValue(String text, Arguments parsed) throws IllegalArgumentException {
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
	 * The definition of a enumeration-valued argument
	 * 
	 * @param <E> The enumeration type of this argument
	 */
	public static class EnumArgumentDef<E extends Enum<E>> extends ComparableArgumentDefImpl<E, EnumArgumentDef<E>> {
		private final Class<E> theType;

		/**
		 * @param pattern The argument pattern that this argument is for
		 * @param name This argument's name
		 * @param type The enumeration type of this argument
		 */
		public EnumArgumentDef(ArgumentPattern pattern, String name, Class<E> type) {
			super(pattern, name);
			theType = type;
		}

		/** @return The enumeration type of this argument */
		public Class<E> getType() {
			return theType;
		}

		@Override
		public Argument createArgument(E value, boolean specified) {
			return new ValuedArgument<>(this, value, specified);
		}

		@Override
		protected E parseValue(String text, Arguments parsed) throws IllegalArgumentException {
			try {
				return Enum.valueOf(theType, text);
			} catch (IllegalArgumentException e) {
				throw new IllegalArgumentException(
					getName() + " value \"" + text + "\" is not a constant of enum type " + theType.getName());
			}
		}

		@Override
		public String toString() {
			return super.toString() + " (enum<" + theType + ">)";
		}
	}

	/** The definition of a boolean-valued argument */
	public static class BooleanArgumentDef extends ValuedArgumentDefImpl<Boolean, BooleanArgumentDef> {
		/**
		 * @param pattern The argument pattern that this argument is for
		 * @param name This argument's name
		 */
		public BooleanArgumentDef(ArgumentPattern pattern, String name) {
			super(pattern, name);
		}

		@Override
		public Argument createArgument(Boolean value, boolean specified) {
			return new ValuedArgument<>(this, value, specified);
		}

		@Override
		protected Boolean parseValue(String text, Arguments parsed) throws IllegalArgumentException {
			return Boolean.valueOf(text);
		}

		@Override
		public String toString() {
			return super.toString() + " (boolean)";
		}
	}

	/** The definition of a duration-valued argument */
	public static class DurationArgumentDef extends ValuedArgumentDefImpl<Duration, DurationArgumentDef> {
		/**
		 * @param pattern The argument pattern that this argument is for
		 * @param name This argument's name
		 */
		public DurationArgumentDef(ArgumentPattern pattern, String name) {
			super(pattern, name);
		}

		@Override
		public Argument createArgument(Duration value, boolean specified) {
			return new ValuedArgument<>(this, value, specified);
		}

		@Override
		protected Duration parseValue(String text, Arguments parsed) throws IllegalArgumentException {
			try {
				return TimeUtils.parseDuration(text).asDuration();
			} catch (java.text.ParseException e) {
				throw new IllegalArgumentException(getName() + " value \"" + text + "\" is not a duration: " + e.getMessage());
			}
		}
	}

	/** The definition of an instant-valued argument */
	public static class InstantArgumentDef extends ValuedArgumentDefImpl<Instant, InstantArgumentDef> {
		private TimeZone theTimeZone;
		/**
		 * @param pattern The argument pattern that this argument is for
		 * @param name This argument's name
		 */
		public InstantArgumentDef(ArgumentPattern pattern, String name) {
			super(pattern, name);
			theTimeZone = TimeZone.getDefault();
		}

		@Override
		public Argument createArgument(Instant value, boolean specified) {
			return new ValuedArgument<>(this, value, specified);
		}

		/** @return The time zone that this argument type parses dates with */
		public TimeZone getTimeZone() {
			return theTimeZone;
		}

		/**
		 * @param timeZone The time zone for this argument type to parse dates with
		 * @return This argument type
		 */
		public InstantArgumentDef withTimeZone(TimeZone timeZone) {
			theTimeZone = timeZone;
			return this;
		}

		@Override
		protected Instant parseValue(String text, Arguments parsed) throws IllegalArgumentException {
			try {
				return TimeUtils.parseInstant(text, true, true, opts -> opts.withTimeZone(theTimeZone)).evaluate(Instant::now);
			} catch (java.text.ParseException e) {
				throw new IllegalArgumentException(getName() + " value \"" + text + "\" is not a duration: " + e.getMessage());
			}
		}
	}

	/** The definition of a file- or directory-valued argument */
	public static class FileArgumentDef extends ValuedArgumentDefImpl<File, FileArgumentDef> {
		private boolean isFileOrDirAsserted;
		private boolean isDirectory;
		private boolean mustExist;
		private boolean createDir;
		private File theRelativeFile;
		private String theRelativeFileArg;
		private boolean convertToCanonical;

		/**
		 * @param pattern The argument pattern that this argument is for
		 * @param name This argument's name
		 */
		public FileArgumentDef(ArgumentPattern pattern, String name) {
			super(pattern, name);
		}

		@Override
		public Argument createArgument(File value, boolean specified) {
			return new ValuedArgument<>(this, value, specified);
		}

		/**
		 * Asserts that the value of this argument must be either a file or a directory. This test will pass if the file does not exist and
		 * {@link #mustExist()} is not configured.
		 * 
		 * @param dir Whether the value of this argument must be a directory or a file
		 * @return This definition, for chaining
		 */
		public FileArgumentDef directory(boolean dir) {
			if (createDir && !dir) {
				throw new IllegalArgumentException("Cannot create a directory for a file that is asserted to not be a directory");
			}
			isFileOrDirAsserted = true;
			isDirectory = dir;
			return this;
		}

		/**
		 * Asserts that the file value of this argument must exist and be reachable
		 * 
		 * @return This definition, for chaining
		 */
		public FileArgumentDef mustExist() {
			if (createDir) {
				throw new IllegalArgumentException("mkDir() and mustExist() are exclusive--only one may be used");
			}
			mustExist = true;
			return this;
		}

		/**
		 * Causes the directory value of this argument to be created if it does not exist
		 * 
		 * @return This definition, for chaining
		 */
		public FileArgumentDef mkDir() {
			if (isFileOrDirAsserted && !isDirectory) {
				throw new IllegalArgumentException("Cannot create a directory for a file that is asserted to not be a directory");
			} else if (mustExist) {
				throw new IllegalArgumentException("mkDir() and mustExist() are exclusive--only one may be used");
			}
			createDir = true;
			return this;
		}

		/**
		 * Causes file values of this argument to be interpreted as relative to the given directory
		 * 
		 * @param relative The relative directory
		 * @return This definition, for chaining
		 */
		public FileArgumentDef relativeTo(File relative) {
			if (theRelativeFileArg != null) {
				throw new IllegalArgumentException(
					"The relativeTo(File) and relativeTo(String arg) methods are exclusive" + "--only one may be used");
			}
			theRelativeFile = relative;
			return this;
		}

		/**
		 * Causes file values of this argument to be interpreted as relative to the directory value of another file-type argument. The
		 * relative argument must be specified in the argument list PAST this argument.
		 * 
		 * @param otherFileArgument The name of the file argument whose value will be used as a relative directory
		 * @return This definition, for chaining
		 */
		public FileArgumentDef relativeTo(String otherFileArgument) {
			if (theRelativeFile != null) {
				throw new IllegalArgumentException(
					"The relativeTo(File) and relativeTo(String arg) methods are exclusive" + "--only one may be used");
			}
			theRelativeFileArg = otherFileArgument;
			return this;
		}

		/**
		 * Causes file values of this argument to be converted to their canonical path
		 * 
		 * @return This definition, for chaining
		 */
		public FileArgumentDef toCanonical() {
			convertToCanonical = true;
			return this;
		}

		@Override
		protected File parseValue(String text, Arguments parsed) throws IllegalArgumentException {
			File file = new File(text);
			if (!file.getAbsolutePath().equals(file.getPath())) { // If not specified as absolute, resolve as relative if configured
				File relative;
				if (theRelativeFileArg != null) {
					Object argValue = parsed.get(theRelativeFileArg);
					if (argValue == null) {
						throw new IllegalArgumentException("No \"" + theRelativeFileArg
							+ "\" argument (for relative directory) specified before this argument (\"" + getName() + "\")");
					} else if (!(argValue instanceof File)) {
						throw new IllegalArgumentException("Argument \"" + theRelativeFileArg + "\" (for relative directory of argument \""
							+ getName() + "\") is not a file-type argument");
					} else {
						relative = (File) argValue;
					}
				} else if (theRelativeFile != null) {
					relative = theRelativeFile;
				} else {
					relative = null;
				}
				if (relative != null) {
					file = new File(relative, text);
				}
			}
			if (convertToCanonical) {
				try {
					file = new File(file.getCanonicalPath());
				} catch (IOException e) {
					throw new IllegalArgumentException("Could not canonize file " + file.getPath(), e);
				}
			}
			if (mustExist && !file.exists()) {
				throw new IllegalArgumentException(file.getPath() + " does not exist");
			}
			if (file.exists() && isFileOrDirAsserted && file.isDirectory() != isDirectory) {
				throw new IllegalArgumentException(file.getPath() + " is " + (isDirectory ? "not " : "") + "a directory");
			}
			if (createDir && !file.exists() && !file.mkdirs()) {
				throw new IllegalArgumentException("Could not create directory " + file.getPath());
			}
			return file;
		}

		@Override
		public String toString() {
			return super.toString() + " (file)";
		}
	}

	/** An instance of an argument as parsed from a set of arguments */
	public interface Argument {
		/**
		 * @return The argument definition that this argument was parsed for. May be null if this argument is
		 *         {@link ArgumentPattern#isAcceptingUnmatched() unmatched}.
		 */
		ArgumentDef<?> getDefinition();

		/** @return Whether this argument has been specified in the arguments (as opposed to just a default value) */
		boolean isSpecified();

		/** @return The name of the argument */
		String getName();
	}

	private static abstract class ArgumentImpl implements Argument {
		private final ArgumentDef<?> theDefinition;
		private final boolean isSpecified;

		protected ArgumentImpl(ArgumentDef<?> def, boolean specified) {
			theDefinition = def;
			isSpecified = specified;
		}

		@Override
		public ArgumentDef<?> getDefinition() {
			return theDefinition;
		}

		@Override
		public boolean isSpecified() {
			return isSpecified;
		}

		@Override
		public String getName() {
			return theDefinition.getName();
		}

		@Override
		public String toString() {
			return theDefinition.toString();
		}
	}

	/**
	 * An argument that has a value
	 * 
	 * @param <T> The type of the argument's value
	 */
	public static class ValuedArgument<T> extends ArgumentImpl {
		private final T theValue;

		/**
		 * @param def The argument definition of this argument
		 * @param value The value for this argument
		 * @param specified Whether this argument was specified by the user or supplied by the argument definition as a default
		 */
		public ValuedArgument(ValuedArgumentDef<T, ?> def, T value, boolean specified) {
			super(def, specified);
			theValue = value;
		}

		@Override
		public ValuedArgumentDef<T, ?> getDefinition() {
			return (ValuedArgumentDef<T, ?>) super.getDefinition();
		}

		/** @return This argument's value */
		public T getValue() {
			return theValue;
		}

		/** @return This argument's value as an integer */
		public long getInt() {
			return ((Number) theValue).longValue();
		}

		/** @return This argument's value as a floating-point number */
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
		private final Arguments theSubArgs;

		/**
		 * @param def The argument definition of this argument
		 * @param subArgs The sub-arguments for this argument
		 * @param specified Whether this argument was specified by the user or supplied by the argument definition as a default
		 */
		public FlagArgument(FlagArgumentDef def, Arguments subArgs, boolean specified) {
			super(def, specified);
			theSubArgs = subArgs;
		}

		@Override
		public FlagArgumentDef getDefinition() {
			return (FlagArgumentDef) super.getDefinition();
		}

		/** @return All sub-arguments that were parsed for this argument */
		public Arguments getSubArgs() {
			return theSubArgs;
		}
	}

	/** An accepted un-valued argument that does not match any argument definition */
	public static class UnmatchedArgument extends ArgumentImpl {
		private static class UnmatchedArgumentDef extends ArgumentDefImpl<UnmatchedArgumentDef> {
			private UnmatchedArgumentDef(ArgumentPattern pattern, String name) {
				super(pattern, name);
			}

			@Override
			public Argument parse(String value, Arguments parsed, Queue<String> moreArgs) throws IllegalArgumentException {
				return new UnmatchedArgument(this, true);
			}
		}

		private UnmatchedArgument(UnmatchedArgumentDef def, boolean specified) {
			super(def, specified);
		}

		/**
		 * @param pattern The pattern that matched this argument, or null if the argument was not pattern-matched
		 * @param name This argument's name, or the entire argument text if the pattern is null.
		 * @param specified Whether this argument was specified by the user or supplied by the argument definition as a default
		 * @return The new argument
		 */
		public static UnmatchedArgument create(ArgumentPattern pattern, String name, boolean specified) {
			return new UnmatchedArgument(new UnmatchedArgumentDef(pattern, name), specified);
		}
	}

	/** An accepted valued argument that does not match any argument definition */
	public static class UnmatchedValuedArgument extends ValuedArgument<String> {
		private static class UnmatchedValuedArgumentDef extends ValuedArgumentDefImpl<String, UnmatchedValuedArgumentDef> {
			private UnmatchedValuedArgumentDef(ArgumentPattern pattern, String name) {
				super(pattern, name);
			}

			@Override
			public Argument createArgument(String value, boolean specified) {
				return new UnmatchedValuedArgument(this, value, specified);
			}

			@Override
			public Argument parse(String value, Arguments parsed, Queue<String> moreArgs) throws IllegalArgumentException {
				return new UnmatchedValuedArgument(this, value, true);
			}

			@Override
			protected String parseValue(String text, Arguments parsed) throws IllegalArgumentException {
				return text;
			}
		}

		private UnmatchedValuedArgument(UnmatchedValuedArgumentDef def, String value, boolean specified) {
			super(def, value, specified);
		}

		/**
		 * @param pattern The pattern that matched this argument
		 * @param name The name of the argument
		 * @param value The value of the argument
		 * @param specified Whether this argument was specified by the user or supplied by the argument definition as a default
		 * @return The new argument
		 */
		public static UnmatchedValuedArgument create(ArgumentPattern pattern, String name, String value, boolean specified) {
			return new UnmatchedValuedArgument(new UnmatchedValuedArgumentDef(pattern, name), value, specified);
		}
	}

	/** A set of argument instances, parsed from argument strings */
	public static class Arguments {
		private final ArgumentParser theParser;
		private final List<Argument> theArguments;

		/**
		 * @param parser The parser that parsed this argument set
		 */
		public Arguments(ArgumentParser parser) {
			theParser = parser;
			theArguments = new ArrayList<>();
		}

		/**
		 * @param name The name of the argument to check
		 * @throws IllegalArgumentException If no argument with the given name was acceptable to this argument set's parser
		 */
		public void checkArgument(String name) throws IllegalArgumentException {
			if (!theParser.isArgValid(name)) {
				throw new IllegalArgumentException("No such argument named \"" + name + "\"");
			}
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
			checkArgument(name);
			Matcher memberMatcher = MEMBER_PATTERN.matcher(name);
			List<Argument> ret = new ArrayList<>();
			for (Argument arg : theArguments) {
				if (arg.getName().equals(name)) {
					ret.add(arg);
				}
			}
			// Second pass for sub-arguments
			for (Argument arg : theArguments) {
				if (arg instanceof FlagArgument && ((FlagArgument) arg).getSubArgs() != null && memberMatcher.matches()
					&& arg.getName().equals(memberMatcher.group(1))) {
					ret.addAll(((FlagArgument) arg).getSubArgs().getArguments(memberMatcher.group(2)));
				}
			}
			return ret;
		}

		/**
		 * @param name The name of the argument to get
		 * @return The first argument in this set with the given name, or null if no such argument is present in this set
		 */
		public Argument getArgument(String name) {
			Matcher memberMatcher = MEMBER_PATTERN.matcher(name);
			for (Argument arg : theArguments) {
				if (arg.getName().equals(name)) {
					return arg;
				}
			}
			for (Argument arg : theArguments) {
				if (arg instanceof FlagArgument && ((FlagArgument) arg).getSubArgs() != null && memberMatcher.matches()
					&& arg.getName().equals(memberMatcher.group(1))) {
					Argument ret = ((FlagArgument) arg).getSubArgs().getArgument(memberMatcher.group(2));
					if (ret != null) {
						return ret;
					}
				}
			}
			checkArgument(name);
			return null;
		}

		/**
		 * @param argDef The argument definition to get arguments of
		 * @return All arguments with the given definition
		 */
		public List<Argument> getArguments(ArgumentDef<?> argDef) {
			return theArguments.stream().filter(arg -> arg.getDefinition().equals(argDef)).collect(Collectors.toList());
		}

		/**
		 * @param patternName The name of the pattern to get the arguments for
		 * @return All arguments parsed by the pattern with the given name
		 */
		public List<Argument> forPattern(String patternName) {
			boolean hasPattern = false;
			for (ArgumentPattern pattern : theParser.getPatterns()) {
				if (pattern.getName().equals(patternName)) {
					hasPattern = true;
					break;
				}
			}
			if (!hasPattern) {
				throw new IllegalArgumentException("No such argument pattern named \"" + patternName + "\"");
			}
			return theArguments.stream()
				.filter(
					arg -> arg.getDefinition().getArgPattern() != null && arg.getDefinition().getArgPattern().getName().equals(patternName))
				.collect(Collectors.toList());
		}

		/**
		 * @param name The name of the arguments to get
		 * @return The values of all arguments with the given name
		 */
		public List<?> getAll(String name) {
			return getArguments(name).stream().map(arg -> ((ValuedArgument<Object>) arg).getValue()).collect(Collectors.toList());
		}

		/**
		 * @param <T> The type of the arguments to get
		 * @param name The name of the arguments to get
		 * @param type The type of the arguments
		 * @return The values of all arguments with the given name
		 */
		public <T> List<T> getAll(String name, Class<T> type) {
			return getArguments(name).stream().map(arg -> ((ValuedArgument<Object>) arg).getValue()).map(type::cast)
				.collect(Collectors.toList());
		}

		/**
		 * @param arg The argument to add to this argument set
		 */
		public void add(Argument arg) {
			theArguments.add(arg);
		}

		/**
		 * @param argName The name of the argument to get
		 * @return The value of the first argument with the given name, or null if no such argument exists in this set
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
		 * @param argName The name of the argument to get
		 * @param def The value to return if no such argument exists in this set
		 * @return The value of the first argument with the given name, or def if no such argument exists in this set
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
		 * @return The value of the first argument with the given name, as a string
		 */
		public String getString(String argName) {
			return (String) get(argName);
		}

		/**
		 * @param argName The name of the argument to get
		 * @return The value of the first argument with the given name, as an integer
		 */
		public int getInt(String argName) {
			return ((Number) get(argName)).intValue();
		}

		/**
		 * @param argName The name of the argument to get
		 * @return The value of the first argument with the given name, as a long integer
		 */
		public long getLong(String argName) {
			return ((Number) get(argName)).longValue();
		}

		/**
		 * @param argName The name of the argument to get
		 * @param def The value to return if no such argument exists in this set
		 * @return The value of the first argument with the given name, as an integer
		 */
		public long getInt(String argName, int def) {
			return ((Number) get(argName, def)).longValue();
		}

		/**
		 * @param argName The name of the argument to get
		 * @return The value of the first argument with the given name, as a floating-point number
		 */
		public double getFloat(String argName) {
			return ((Number) get(argName)).doubleValue();
		}

		/**
		 * @param argName The name of the argument to get
		 * @param def The value to return if no such argument exists in this set
		 * @return The value of the first argument with the given name, as a floating-point number
		 */
		public double getFloat(String argName, double def) {
			return ((Number) get(argName, def)).doubleValue();
		}

		/**
		 * @param argName The name of the argument to get
		 * @param def The value to return if no such argument exists in this set
		 * @return The value of the first argument with the given name, as a duration
		 */
		public Duration getDuration(String argName, Duration def) {
			return get(argName, def);
		}

		/**
		 * @param argName The name of the argument to get
		 * @param def The value to return if no such argument exists in this set
		 * @return The value of the first argument with the given name, as an instant
		 */
		public Instant getInstant(String argName, Instant def) {
			return get(argName, def);
		}

		/**
		 * @param argName The name of the argument to get
		 * @return The value of the first argument with the given name
		 */
		public File getFile(String argName) {
			return (File) get(argName);
		}

		/**
		 * @param argName The name of the flag to check
		 * @return Whether the given flag is present in this argument set
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

		// Normal name/value patterns
		/**
		 * Gets or creates a pattern to add arguments to. The new pattern will support arguments with individual names and values.
		 * 
		 * @param pattern The pattern to match arguments with
		 * @return The pattern
		 */
		default ArgumentPattern forPattern(String pattern) {
			return forPattern(pattern, pattern);
		}

		/**
		 * Gets or creates a pattern to add arguments to. The new pattern will support arguments with individual names and values.
		 * 
		 * @param name The name of the pattern
		 * @param pattern The pattern to match arguments with
		 * @return The pattern
		 */
		default ArgumentPattern forPattern(String name, String pattern) {
			return forPattern(name, Pattern.compile(pattern));
		}

		/**
		 * Gets or creates a pattern to add arguments to. The new pattern will support arguments with individual names and values.
		 * 
		 * @param name The name of the pattern
		 * @param pattern The pattern to match arguments with
		 * @return The pattern
		 */
		default ArgumentPattern forPattern(String name, Pattern pattern) {
			return forPattern(name, pattern, true, true, false);
		}

		/**
		 * Gets or creates a pattern to add arguments to. The new pattern will support arguments with individual names and values.
		 * 
		 * @param pattern The pattern to match arguments with
		 * @return The pattern
		 */
		default ArgumentPattern forPattern(Pattern pattern) {
			return forPattern(pattern.pattern(), pattern, true, true, false);
		}

		// Name-only (flag) patterns
		/**
		 * Gets or creates a flag pattern to add arguments to. The new pattern will support arguments with individual names but no values.
		 * 
		 * @param pattern The pattern to match arguments with
		 * @return The pattern
		 */
		default ArgumentPattern forFlagPattern(String pattern) {
			return forFlagPattern("flag:" + pattern, pattern);
		}

		/**
		 * Gets or creates a flag pattern to add arguments to. The new pattern will support arguments with individual names but no values.
		 * 
		 * @param name The name of the pattern
		 * @param pattern The pattern to match arguments with
		 * @return The pattern
		 */
		default ArgumentPattern forFlagPattern(String name, String pattern) {
			return forFlagPattern(name, Pattern.compile(pattern));
		}

		/**
		 * Gets or creates a flag pattern to add arguments to. The new pattern will support arguments with individual names but no values.
		 * 
		 * @param name The name of the pattern
		 * @param pattern The pattern to match arguments with
		 * @return The pattern
		 */
		default ArgumentPattern forFlagPattern(String name, Pattern pattern) {
			return forPattern(name, pattern, true, false, false);
		}

		// Value-only patterns
		/**
		 * Gets or creates a value-only pattern to add arguments to. The new pattern will support arguments with values, but not individual
		 * names.
		 * 
		 * @param pattern The pattern to match arguments with
		 * @return The pattern
		 */
		default ArgumentPattern forValueOnlyPattern(String pattern) {
			return forValueOnlyPattern(pattern, pattern);
		}

		/**
		 * Gets or creates a value-only pattern to add arguments to. The new pattern will support arguments with values, but not individual
		 * names.
		 * 
		 * @param name The name of the pattern
		 * @param pattern The pattern to match arguments with
		 * @return The pattern
		 */
		default ArgumentPattern forValueOnlyPattern(String name, String pattern) {
			return forValueOnlyPattern("value:" + pattern, pattern);
		}

		/**
		 * Gets or creates a value-only pattern to add arguments to. The new pattern will support arguments with values, but not individual
		 * names.
		 * 
		 * @param name The name of the pattern
		 * @param pattern The pattern to match arguments with
		 * @return The pattern
		 */
		default ArgumentPattern forValueOnlyPattern(String name, Pattern pattern) {
			return forPattern(name, pattern, false, true, false);
		}

		// Multi-value patterns
		/**
		 * Gets or creates a multi-value pattern to add arguments to. The new pattern will support arguments with individual names and any
		 * number of values as supported by the pattern's capturing groups.
		 * 
		 * @param pattern The pattern to match arguments with
		 * @return The pattern
		 */
		default ArgumentPattern forMultiValuePattern(String pattern) {
			return forMultiValuePattern(pattern, pattern);
		}

		/**
		 * Gets or creates a multi-value pattern to add arguments to. The new pattern will support arguments with individual names and any
		 * number of values as supported by the pattern's capturing groups.
		 * 
		 * @param name The name of the pattern
		 * @param pattern The pattern to match arguments with
		 * @return The pattern
		 */
		default ArgumentPattern forMultiValuePattern(String name, String pattern) {
			return forMultiValuePattern(name, Pattern.compile(pattern));
		}

		/**
		 * Gets or creates a multi-value pattern to add arguments to. The new pattern will support arguments with individual names and any
		 * number of values as supported by the pattern's capturing groups.
		 * 
		 * @param name The name of the pattern
		 * @param pattern The pattern to match arguments with
		 * @return The pattern
		 */
		default ArgumentPattern forMultiValuePattern(String name, Pattern pattern) {
			return forPattern(name, pattern, true, true, true);
		}

		// Unnamed, multi-value patterns
		/**
		 * Gets or creates a multi-value pattern to add arguments to. The new pattern will not support individual names on arguments. It
		 * will support any number of values as supported by the pattern's capturing groups.
		 * 
		 * @param pattern The pattern to match arguments with
		 * @return The pattern
		 */
		default ArgumentPattern forMultiValueOnlyPattern(String pattern) {
			return forMultiValueOnlyPattern(pattern, pattern);
		}

		/**
		 * Gets or creates a multi-value pattern to add arguments to. The new pattern will not support individual names on arguments. It
		 * will support any number of values as supported by the pattern's capturing groups.
		 * 
		 * @param name The name of the pattern
		 * @param pattern The pattern to match arguments with
		 * @return The pattern
		 */
		default ArgumentPattern forMultiValueOnlyPattern(String name, String pattern) {
			return forMultiValuePattern(name, Pattern.compile(pattern));
		}

		/**
		 * Gets or creates a multi-value pattern to add arguments to. The new pattern will not support individual names on arguments. It
		 * will support any number of values as supported by the pattern's capturing groups.
		 * 
		 * @param name The name of the pattern
		 * @param pattern The pattern to match arguments with
		 * @return The pattern
		 */
		default ArgumentPattern forMultiValueOnlyPattern(String name, Pattern pattern) {
			return forPattern(name, pattern, false, true, true);
		}

		/**
		 * Gets or creates a pattern to add arguments to
		 * 
		 * @param name The name of the pattern
		 * @param pattern The pattern to match arguments with
		 * @param withName Whether arguments matching this pattern will have individual names. If true, the pattern must have a capturing
		 *        group for the name. This can be a capturing group named "name" or the first capturing group.
		 * @param withValue Whether arguments matching this pattern have a value. If true, the pattern must have a capturing group for the
		 *        value. This can be a capturing group named "value", the first capturing group (if <code>withName</code> is false), or the
		 *        second capturing group (if <code>withName</code> is true).
		 * @param multiValues Whether the pattern supports multiple values in a single argument
		 * @return The pattern
		 */
		default ArgumentPattern forPattern(String name, Pattern pattern, boolean withName, boolean withValue, boolean multiValues) {
			return getParser().forPattern(new RegexArgumentPattern(getParser(), name, pattern, withName, withValue, multiValues));
		}

		// Default patterns
		/** @return The default pattern ("--(.+)=(.+)") */
		default ArgumentPattern forDefaultPattern() {
			return forPattern("default", Pattern.compile("--(.+)=(.*)"));
		}

		/** @return The default flag pattern ("--(.+)") */
		default ArgumentPattern forDefaultFlagPattern() {
			return forFlagPattern("flag", Pattern.compile("--(.+)"));
		}

		/** @return The default value-only pattern ("(.+)") */
		default ArgumentPattern forDefaultValueOnlyPattern() {
			return forValueOnlyPattern("value", Pattern.compile("(.+)"));
		}

		/** @return The default value-only pattern ("--(?&lt;name&gt;.+)=(?:(.+),)*(.+)") */
		default ArgumentPattern forDefaultMultiValuePattern() {
			// There seems to be a bug in the regex with a capturing group within a non-capturing group, so these multi-value
			// patterns actually only parse 2 args maximum. If more are specified, all but the last 2 are ignored.
			return getParser().forPattern(new MultiValueArgumentPattern(getParser(), "multi-value", true));
			// return forMultiValuePattern("multi-value", Pattern.compile("--(?<name>.+)=(?:([^,]+),)*([^,]+)"));
		}

		/** @return The default value-only pattern ("(?:(.+),)*(.+)") */
		default ArgumentPattern forDefaultMultiValueOnlyPattern() {
			// Can't use the regex parser here--see comment above
			return getParser().forPattern(new MultiValueArgumentPattern(getParser(), "multi-value-only", false));
			// return forMultiValueOnlyPattern("multi-value", Pattern.compile("(?:([^,]+),)*([^,]+)"));
		}
	}

	/** Parses individual arguments according to a set pattern */
	public interface ArgumentPattern extends ArgumentPatternCreator, ArgumentDefCreator {
		/** @return This pattern's name */
		String getName();

		/** @return Whether this pattern's arguments are individually named */
		boolean isWithName();

		/** @return Whether this pattern's arguments have a value */
		boolean isWithValue();

		/** @return Whether this pattern supports multiple values for a single argument */
		boolean isMultiValue();

		/** @return All argument definition sets accepted for this pattern */
		Collection<ArgumentDef<?>> getAcceptedArguments();

		/** @return Whether this argument pattern accepts arguments that don't match any of its explicitly accepted definitions */
		boolean isAcceptingUnmatched();

		/** @return Whether this pattern accepts arguments that do not match any of its accepted arguments */
		AbstractPattern acceptUnmatched();

		/**
		 * @param name The name of the argument
		 * @return The argument definition of the given name, or null if no such argument definition is defined in this set
		 */
		ArgumentDef<?> getArgument(String name);

		/**
		 * Attempts to parse an argument by this pattern
		 * 
		 * @param arg The argument to parse
		 * @param parsed The arguments parsed so far
		 * @param moreArgs Subsequent arguments in the argument list
		 * @return The parsed argument, or null if the argument does not match this pattern
		 * @throws IllegalArgumentException If this pattern is misconfigured
		 */
		List<Argument> parse(String arg, Arguments parsed, Queue<String> moreArgs) throws IllegalArgumentException;

		/**
		 * @param parsed The arguments being parsed
		 * @param argument The argument to parse
		 * @param argName The name of the argument
		 * @param argValues The values(s) of the argument
		 * @param moreArgs More arguments in the argument sequence that may also be consumed as part of the parsed argument
		 * @return The parsed argument(s)
		 */
		List<Argument> parseArgument(Arguments parsed, String argument, String argName, List<String> argValues, Queue<String> moreArgs);

		/**
		 * Validates a set of arguments against the constraints configured in this structure
		 * 
		 * @param args The arguments to validate
		 */
		void validate(Arguments args);
	}

	/** Abstract argument pattern implementation */
	public static abstract class AbstractPattern implements ArgumentPattern {
		private final ArgumentParser theParser;
		private final String theName;
		private final boolean hasName;
		private final boolean hasValue;
		private final boolean isMultiValue;

		/**
		 * @param parser The argument parser that this pattern belongs to
		 * @param name The name of the pattern for later recall
		 * @param withName Whether this pattern's arguments are named individually
		 * @param withValue Whether this pattern's arguments have values
		 * @param multiValue Whether this pattern supports multiple values for a single argument
		 */
		public AbstractPattern(ArgumentParser parser, String name, boolean withName, boolean withValue, boolean multiValue) {
			theParser = parser;
			theName = name;
			this.hasName = withName;
			this.hasValue = withValue;
			isMultiValue = multiValue;
			if (!withName && !withValue) {
				throw new IllegalArgumentException("A pattern must have either a name or a value or both");
			}
			if (isMultiValue && !withValue) {
				throw new IllegalArgumentException("Cannot create a multi-valued pattern with no value");
			}
			theAcceptedArguments = new LinkedHashMap<>();
		}

		private Map<String, ArgumentDef<?>> theAcceptedArguments;
		private boolean isAcceptingUnmatched;

		@Override
		public ArgumentParser getParser() {
			return theParser;
		}

		@Override
		public String getName() {
			return theName;
		}

		@Override
		public boolean isWithName() {
			return hasName;
		}

		@Override
		public boolean isWithValue() {
			return hasValue;
		}

		@Override
		public boolean isMultiValue() {
			return isMultiValue;
		}

		@Override
		public Collection<ArgumentDef<?>> getAcceptedArguments() {
			return Collections.unmodifiableCollection(theAcceptedArguments.values());
		}

		@Override
		public boolean isAcceptingUnmatched() {
			return isAcceptingUnmatched;
		}

		@Override
		public AbstractPattern acceptUnmatched() {
			isAcceptingUnmatched = true;
			return this;
		}

		@Override
		public ArgumentDef<?> getArgument(String name) {
			return theAcceptedArguments.get(name);
		}

		@Override
		public StringArgumentDef stringArg(String name) {
			if (theAcceptedArguments.containsKey(name)) {
				throw new IllegalArgumentException("Argument " + name + " already defined in this set");
			}
			StringArgumentDef ret = new StringArgumentDef(this, name);
			theAcceptedArguments.put(name, ret);
			return ret;
		}

		@Override
		public PatternArgumentDef patternArg(String name, Pattern pattern) {
			if (theAcceptedArguments.containsKey(name)) {
				throw new IllegalArgumentException("Argument " + name + " already defined in this set");
			}
			PatternArgumentDef ret = new PatternArgumentDef(this, name, pattern);
			theAcceptedArguments.put(name, ret);
			return ret;
		}

		@Override
		public IntArgumentDef intArg(String name) {
			if (theAcceptedArguments.containsKey(name)) {
				throw new IllegalArgumentException("Argument " + name + " already defined in this set");
			}
			IntArgumentDef ret = new IntArgumentDef(this, name);
			theAcceptedArguments.put(name, ret);
			return ret;
		}

		@Override
		public FloatArgumentDef floatArg(String name) {
			if (theAcceptedArguments.containsKey(name)) {
				throw new IllegalArgumentException("Argument " + name + " already defined in this set");
			}
			FloatArgumentDef ret = new FloatArgumentDef(this, name);
			theAcceptedArguments.put(name, ret);
			return ret;
		}

		@Override
		public <E extends Enum<E>> EnumArgumentDef<E> enumArg(String name, Class<E> type) {
			if (theAcceptedArguments.containsKey(name)) {
				throw new IllegalArgumentException("Argument " + name + " already defined in this set");
			}
			EnumArgumentDef<E> ret = new EnumArgumentDef<>(this, name, type);
			theAcceptedArguments.put(name, ret);
			return ret;
		}

		@Override
		public BooleanArgumentDef booleanArg(String name) {
			if (theAcceptedArguments.containsKey(name)) {
				throw new IllegalArgumentException("Argument " + name + " already defined in this set");
			}
			BooleanArgumentDef ret = new BooleanArgumentDef(this, name);
			theAcceptedArguments.put(name, ret);
			return ret;
		}

		@Override
		public DurationArgumentDef durationArg(String name) {
			if (theAcceptedArguments.containsKey(name)) {
				throw new IllegalArgumentException("Argument " + name + " already defined in this set");
			}
			DurationArgumentDef ret = new DurationArgumentDef(this, name);
			theAcceptedArguments.put(name, ret);
			return ret;
		}

		@Override
		public InstantArgumentDef instantArg(String name) {
			if (theAcceptedArguments.containsKey(name)) {
				throw new IllegalArgumentException("Argument " + name + " already defined in this set");
			}
			InstantArgumentDef ret = new InstantArgumentDef(this, name);
			theAcceptedArguments.put(name, ret);
			return ret;
		}

		@Override
		public FileArgumentDef fileArg(String name) {
			if (theAcceptedArguments.containsKey(name)) {
				throw new IllegalArgumentException("Argument " + name + " already defined in this set");
			}
			FileArgumentDef ret = new FileArgumentDef(this, name);
			theAcceptedArguments.put(name, ret);
			return ret;
		}

		@Override
		public FlagArgumentDef flagArg(String name) {
			if (theAcceptedArguments.containsKey(name)) {
				throw new IllegalArgumentException("Argument " + name + " already defined in this set");
			}
			FlagArgumentDef ret = new FlagArgumentDef(this, name);
			theAcceptedArguments.put(name, ret);
			return ret;
		}

		@Override
		@SuppressWarnings("rawtypes")
		public void validate(Arguments args) {
			// Populate default values
			for (ArgumentDef<?> arg : theAcceptedArguments.values()) {
				if (arg instanceof ValuedArgumentDef) {
					if (args.getArguments(arg).isEmpty()) {
						Object def = ((ValuedArgumentDef<?, ?>) arg).getDefault();
						if (def != null) {
							args.add(((ValuedArgumentDef) arg).createArgument(def, false));
						}
					}
				}
			}
			// Check argument constraints
			for (ArgumentDef<?> arg : theAcceptedArguments.values()) {
				arg.validate(args);
			}
		}

		private static final Queue<String> EMPTY_QUEUE = new AbstractQueue<String>() {
			@Override
			public boolean offer(String e) {
				return false;
			}

			@Override
			public String poll() {
				return null;
			}

			@Override
			public String peek() {
				return null;
			}

			@Override
			public Iterator<String> iterator() {
				return Collections.<String> emptyList().iterator();
			}

			@Override
			public int size() {
				return 0;
			}
		};

		@Override
		public List<Argument> parseArgument(Arguments parsed, String argument, String argName, List<String> argValues,
			Queue<String> moreArgs) {
			List<Argument> ret = new ArrayList<>(argValues.size());
			for (int i = 0; i < argValues.size(); i++) {
				boolean found = false;
				if (argName != null) {
					ArgumentDef<?> argDef = theAcceptedArguments.get(argName);
					if (argDef != null) {
						found = true;
						ret.add(argDef.parse(argValues.get(i), parsed, isMultiValue ? EMPTY_QUEUE : moreArgs));
					}
				} else {
					IllegalArgumentException toThrow = null;
					for (ArgumentDef<?> argDef : theAcceptedArguments.values()) {
						int count = parsed.getArguments(argDef).size();
						if (argDef.getMaxTimes() < Integer.MAX_VALUE && count >= argDef.getMaxTimes()) {
							continue;
						}
						try {
							ret.add(argDef.parse(argValues.get(i), parsed, isMultiValue ? EMPTY_QUEUE : moreArgs));
							found = true;
							break;
						} catch (IllegalArgumentException e) {
							if (count < argDef.getMinTimes()) {
								toThrow = e;
							}
						}
					}
					if (found) {} else if (toThrow != null) {
						throw toThrow;
					}
				}
				if (found) {} else if (isAcceptingUnmatched) {
					if (argValues.get(i) == null) {
						ret.add(UnmatchedArgument.create(this, argName, true));
					} else {
						ret.add(UnmatchedValuedArgument.create(this, argName, argValues.get(i), true));
					}
				} else if (i == 0) {
					return null;
				} else {
					throw new IllegalArgumentException("Parsed first value in multi-values but could not parse subsequent? " + argument);
				}
			}
			return ret;
		}
	}

	/** Parses multi-value arguments */
	public static class MultiValueArgumentPattern extends AbstractPattern {
		/**
		 * @param parser The argument parser
		 * @param name The name of this pattern
		 * @param hasName Whether this pattern's arguments are individually named
		 */
		public MultiValueArgumentPattern(ArgumentParser parser, String name, boolean hasName) {
			super(parser, name, hasName, true, true);
		}

		@Override
		public List<Argument> parse(String arg, Arguments parsed, Queue<String> moreArgs) throws IllegalArgumentException {
			if (!arg.startsWith("--"))
				return null;
			String name;
			int valueStart;
			if (isWithName()) {
				int equalIdx = arg.indexOf('=');
				if (equalIdx < 0)
					return null;
				name = arg.substring(2, equalIdx);
				valueStart = equalIdx + 1;
			} else {
				name = null;
				valueStart = 2;
			}
			List<String> argValues = new ArrayList<>();
			int commaIdx = arg.indexOf(',', valueStart);
			while (commaIdx >= 0) {
				argValues.add(arg.substring(valueStart, commaIdx));
				valueStart = commaIdx + 1;
				commaIdx = arg.indexOf(',', valueStart);
			}
			argValues.add(arg.substring(valueStart));
			return parseArgument(parsed, arg, name, argValues, moreArgs);
		}

		@Override
		public int hashCode() {
			return 7;
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof MultiValueArgumentPattern;
		}

		@Override
		public String toString() {
			return "--name=value,value...";
		}
	}

	/** Parses arguments according to a regular expression */
	public static class RegexArgumentPattern extends AbstractPattern {
		private final Pattern thePattern;

		/**
		 * @param parser The argument parser that this pattern belongs to
		 * @param name The name of the pattern for later recall
		 * @param pattern The regular expression that this pattern will use to parse arguments
		 * @param withName Whether this pattern's arguments are named individually
		 * @param withValue Whether this pattern's arguments have values
		 * @param multiValue Whether this pattern supports multiple values for a single argument
		 */
		public RegexArgumentPattern(ArgumentParser parser, String name, Pattern pattern, boolean withName, boolean withValue,
			boolean multiValue) {
			super(parser, name, withName, withValue, multiValue);
			thePattern = pattern;
		}

		/** @return The pattern that arguments are parsed with */
		public Pattern getPattern() {
			return thePattern;
		}

		@Override
		public List<Argument> parse(String arg, Arguments parsed, Queue<String> moreArgs) throws IllegalArgumentException {
			Matcher match = thePattern.matcher(arg);
			if (!match.matches()) {
				return null;
			}

			String argName;
			String[] argValues;
			int groups = 0;
			if (isWithName()) {
				groups++;
			}
			if (isWithValue() && !isMultiValue()) {
				groups++;
			}
			if (match.groupCount() < groups) {
				throw new IllegalArgumentException(
					"Argument pattern \"" + getName() + "\" (" + thePattern.pattern() + ") does not have enough capturing groups."
						+ " Capturing groups are required to get the name and/or value of arguments.");
			}

			if (match.groupCount() == 1) {
				if (isWithName()) {
					argName = match.group(1);
					argValues = null;
				} else {
					argValues = new String[] { match.group(1) };
					argName = null;
				}
			} else {
				int nameGroup;
				if (isWithName()) {
					try {
						argName = match.group("name");
						nameGroup = 1;
						for (int i = 1; i < match.groupCount() + 1; i++) {
							if (match.start(i) == match.start("name")) {
								nameGroup = i;
								break;
							}
						}
					} catch (IllegalArgumentException e) {
						argName = match.group(1);
						nameGroup = 1; // Use the first group for the name if no group named "name"
					}
				} else {
					argName = null;
					nameGroup = -1;
				}
				if (match.groupCount() == groups || isMultiValue()) {
					List<String> valueList = new ArrayList<>();

					for (int i = 1; i <= match.groupCount(); i++) {
						if (i == nameGroup) {
							continue;
						}
						String group = match.group(i);
						// If an optional group in the pattern is not met in the match, the match will succeed but the group will be null
						if (group != null) {
							valueList.add(group);
						}
					}
					argValues = valueList.toArray(new String[valueList.size()]);
				} else {
					try {
						argValues = new String[] { match.group("value") };
					} catch (IllegalArgumentException e) {
						throw new IllegalArgumentException(
							"Argument pattern \"" + getName() + "\" (" + thePattern.pattern() + ") has more than " + groups + " group"
								+ (groups > 1 ? "s" : "") + " and does not possess groups named \"name\" and \"value\".");
					}
				}
			}
			if (argValues == null) {
				argValues = new String[] { null };
			}
			if (argValues.length == 0) {
				return Collections.EMPTY_LIST;
			}
			return parseArgument(parsed, arg, argName, Arrays.asList(argValues), moreArgs);
		}

		@Override
		public int hashCode() {
			return thePattern.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof RegexArgumentPattern && thePattern.equals(((RegexArgumentPattern) obj).thePattern);
		}

		@Override
		public String toString() {
			return thePattern.pattern();
		}
	}

	/** Parses sets of arguments, e.g. command-line arguments */
	public static class ArgumentParser implements ArgumentPatternCreator {
		private List<ArgumentPattern> theArgPatterns;
		private boolean isAcceptingUnmatched;
		private boolean printHelpIfNoArgs;

		/** Creates an empty argument parser */
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
		 * @see ArgumentParser#ArgumentParser()
		 */
		public ArgumentParser reset() {
			clear();
			isAcceptingUnmatched = false;
			return this;
		}

		/**
		 * @param print Whether this parser should print an argument usage text to standard out and then exit the process if
		 *        {@link #parse(String...)} is called with no arguments
		 * @return This parser, for chaining
		 */
		public ArgumentParser setPrintHelpIfNoArgs(boolean print) {
			printHelpIfNoArgs = print;
			return this;
		}

		/**
		 * Causes this parser to accept arguments that don't match any pattern
		 * 
		 * @return This parser, for chaining
		 */
		public ArgumentParser acceptUnmatched() {
			isAcceptingUnmatched = true;
			return this;
		}

		/**
		 * @param pattern The argument pattern to use with this parser
		 * @return The pattern
		 */
		public ArgumentPattern forPattern(ArgumentPattern pattern) {
			for (ArgumentPattern p : theArgPatterns) {
				if (p.getName().equals(pattern.getName())) {
					if (p.equals(pattern))
						return p;
					else
						throw new IllegalArgumentException("Cannot have 2 different patterns with the same name: " + pattern.getName()
							+ " (" + p + " and " + pattern + ")");
				}
			}
			theArgPatterns.add(pattern);
			return pattern;
		}

		/** @return All argument patterns defined in this parser */
		public List<ArgumentPattern> getPatterns() {
			return theArgPatterns;
		}

		/** @return Whether this parser accepts generic (unnamed) arguments. False by default. */
		public boolean isAcceptingUnmatched() {
			return isAcceptingUnmatched;
		}

		/**
		 * @param args The command-line arguments to parse
		 * @return The parsed argument set
		 * @throws IllegalArgumentException If an error occurs parsing an argument or an argument constraint is violated
		 */
		public Arguments parse(String... args) throws IllegalArgumentException {
			return parse(//
				new ArrayDeque<>(Arrays.asList(args)));
		}

		/**
		 * @param args The command-line arguments to parse
		 * @return The parsed argument set
		 * @throws IllegalArgumentException If an error occurs parsing an argument or an argument constraint is violated
		 */
		public Arguments parse(Queue<String> args) throws IllegalArgumentException {
			return parse(args, true);
		}

		/**
		 * @param args The command-line arguments to parse
		 * @param completely If false, this parser will only parse as many arguments
		 * @return The parsed argument set
		 * @throws IllegalArgumentException If an error occurs parsing an argument or an argument constraint is violated
		 */
		public Arguments parse(Queue<String> args, boolean completely) throws IllegalArgumentException {
			if (args.isEmpty() && printHelpIfNoArgs) {
				System.out.println(this);
				System.exit(1);
				return null; // Will never actually get here
			}
			Arguments ret = new Arguments(this);
			Queue<String> copy = new ArrayDeque<>(args);
			while (!args.isEmpty()) {
				String arg = copy.poll();
				boolean found = false;
				for (ArgumentPattern pattern : theArgPatterns) {
					List<Argument> patternArgs = pattern.parse(arg, ret, copy);
					if (patternArgs != null) {
						found = true;
						for (Argument argument : patternArgs) {
							ret.add(argument);
						}
						break;
					}
				}
				if (!found) {
					if (isAcceptingUnmatched) {
						ret.add(UnmatchedArgument.create(null, arg, true));
					} else if (completely) {
						throw new IllegalArgumentException("Argument \"" + arg + "\" does not match any specified argument patterns");
					} else {
						break; // Can't understand this argument and the flag indicates we don't need to. Stop parsing.
					}
				}
				while (copy.size() < args.size()) {
					args.poll(); // Parsed the argument successfully. Remove it and potential sub-args from the parameter.
				}
			}
			for (ArgumentPattern pattern : theArgPatterns) {
				pattern.validate(ret);
			}
			return ret;
		}

		/**
		 * @param name The name of the argument
		 * @return Whether the argument may be a valid argument name in one of this parser's configured patterns
		 */
		public boolean isArgValid(String name) {
			if (isAcceptingUnmatched()) {
				return true;
			}
			Matcher memberMatcher = MEMBER_PATTERN.matcher(name);
			for (ArgumentPattern pattern : getPatterns()) {
				if (pattern.isAcceptingUnmatched()) {
					return true;
				}
				if (pattern.getArgument(name) != null) {
					return true;
				} else if (memberMatcher.matches() && pattern.getArgument(memberMatcher.group(1)) instanceof FlagArgumentDef) {
					FlagArgumentDef flag = (FlagArgumentDef) pattern.getArgument(memberMatcher.group(1));
					if (flag.getSubArgs() != null && flag.getSubArgs().isArgValid(memberMatcher.group(2))) {
						return true;
					}
				}
			}
			return false;
		}

		/** @return A help string for the user to understand this parser's defined arguments and what they do */
		@Override
		public String toString() {
			String indent = "    ";
			int maxWidth = 10000;// Just make this ridiculously large for now and allow the console to word wrap it
			StringBuilder ret = new StringBuilder();
			for (ArgumentPattern pattern : theArgPatterns) {
				ret.append("For pattern ").append(pattern).append(":\n");
				for (ArgumentDef<?> arg : pattern.getAcceptedArguments()) {
					String str = arg.toString();
					ret.append(indent).append(str).append('\n');
					String descrip = arg.getDescription();
					if (descrip != null) {
						int descripW = maxWidth - indent.length() * 2;
						for (int i = 0; i < descrip.length(); i += descripW) {
							ret.append(indent).append(indent);
							if (descrip.length() < i + descripW) {
								ret.append(descrip, i, descrip.length());
							} else {
								ret.append(descrip, i, i + descripW);
							}
							ret.append('\n');
						}
					}
					// sub-arguments on flags
					if (arg instanceof FlagArgumentDef && ((FlagArgumentDef) arg).getSubArgs() != null) {
						String subArgString = ((FlagArgumentDef) arg).getSubArgs().toString();
						if (subArgString.length() > 0) {
							subArgString.replace("\n", "\n" + indent + indent);
							ret.append(indent).append("(sub-arguments)\n");
							ret.append(subArgString);
						}
					}
				}
				if (pattern.isAcceptingUnmatched()) {
					ret.append(indent).append("(any)\n");
				}
			}
			return ret.toString();
		}
	}

	/** @return A blank argument parser to configure */
	public static ArgumentParser create() {
		return new ArgumentParser();
	}

	/**
	 * Tests this class
	 * 
	 * @param args Command-line arguments, ironically ignored
	 */
	public static void main(String[] args) {
		Predicate<Object> isJar = obj -> ((String) obj).contains("jar");
		ArgumentParser argParser = new ArgumentParser();
		argParser//
			/**/.forDefaultPattern()//
			/*    */.fileArg("lookup").times(0, Integer.MAX_VALUE)//
			/*    */.fileArg("project").times(0, Integer.MAX_VALUE).requiredIfNot("all-projects").requiresNot("all-projects")//
			/*    */.stringArg("target-class").requires("op", isJar)//
			/*    */.fileArg("dest").requiredIf("op", isJar).requires("op", isJar)//
			/*    */.stringArg("source")/*    */.stringArg("encoding")//
			/**/.forDefaultMultiValuePattern()//
			/*    */.stringArg("op").required().anyTimes().only("clean", "build", "clean-build", "jar").defValue("build")//
			/**/.forPattern("MF", "--mf:(.+)=(.*)").acceptUnmatched()/**/.forDefaultFlagPattern()//
			/*    */.flagArg("solo")//
			/*    */.flagArg("all-projects")//
			/*    */.flagArg("wait-for-user")/*    */.flagArg("with-subs")//
			/*        */.sub(new ArgumentParser()//
				/*        */.forDefaultValueOnlyPattern()/*            */.intArg("int-sub1").times(1, 1)//
				/*            */.intArg("int-sub2")//
				/*            */.stringArg("string-sub"));

		Arguments arguments;
		try {
			arguments = argParser.parse("--all-projects", "--op=clean-build,jar", "--lookup=..", "--lookup=../../libs", "--dest=export.jar",
				"--encoding=cp1252", "--wait-for-user", "--with-subs", "0", "sub", "--mf:property1=value1");
		} catch (RuntimeException e) {
			throw e;
		}
		if (arguments.getAll("lookup", File.class).size() != 2) {
			throw new IllegalStateException();
		}
		if (arguments.getAll("op", String.class).size() != 2) {
			throw new IllegalStateException();
		}
		if (arguments.getInt("with-subs.int-sub1") != 0) {
			throw new IllegalStateException();
		}
		if (!arguments.getAll("with-subs.int-sub2").isEmpty()) {
			throw new IllegalStateException();
		}
		if (!arguments.get("with-subs.string-sub").equals("sub")) {
			throw new IllegalStateException();
		}
		if (arguments.forPattern("MF").size() != 1 || !arguments.forPattern("MF").get(0).getName().equals("property1")
			|| !((ValuedArgument<String>) arguments.forPattern("MF").get(0)).getValue().equals("value1")) {
			throw new IllegalStateException();
		}

		arguments = argParser.parse("--op=build", "--all-projects", "--lookup=..", "--lookup=../../libs", "--dest=export.jar",
			"--encoding=cp1252", "--wait-for-user", "--with-subs", "0", "sub", "--mf:property1=value1");
	}
}
