package org.qommons;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TimeZone;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToIntBiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.qommons.ArgumentParsing.ArgumentTest.BiArgumentTest;
import org.qommons.ArgumentParsing.Impl.ArgumentTypeHolder;
import org.qommons.collect.BetterList;
import org.qommons.collect.QuickSet.QuickMap;
import org.qommons.ex.ExFunction;
import org.qommons.io.BetterFile;
import org.qommons.io.BetterFile.FileBacking;
import org.qommons.io.BetterFile.FileDataSource;
import org.qommons.io.NativeFileSource;

/**
 * <p>
 * Command-line argument parsing, rethought.
 * </p>
 * <p>
 * To use this class:
 * <ol>
 * <li>Call {@link #build()} to get a {@link ParserBuilder} object.</li>
 * <li>On this object, install an {@link ArgumentPattern}. This determines what the argument specification will look like, independent of
 * the individual arguments. Several default patterns are provided:
 * <ul>
 * <li>{@link #DEFAULT_VALUE_PATTERN} for specifying single-valued arguments, each in a single command-line argument. E.g.
 * "--argName=value". Specified with {@link ParserBuilder#forValuePattern(Consumer) forValuePattern()}.</li>
 * <li>{@link #DEFAULT_MULTI_VALUE_PATTERN} for specifying multi-valued arguments, each in a singled command-line arguments. E.g.
 * "-argName=value1,value2,...". Specified with {@link ParserBuilder#forMultiValuePattern(Consumer) forMultiValuePattern()}.</li>
 * <li>{@link #DEFAULT_FLAG_PATTERN} for specifying flag-type arguments. E.g. "--flag". Specified with
 * {@link ParserBuilder#forFlagPattern(Consumer) forFlagPattern()}.</li>
 * <li>{@link #SPLIT_VALUE_PATTERN} for specifying single-valued arguments across multiple command-line arguments. E.g. "--argName"
 * "value".</li>
 * <li>{@link #SPLIT_MULTI_VALUE_PATTERN} for specifying multi-valued arguments across multiple command-line arguments. E.g. "--argName"
 * "value1" "value2"....</li>
 * </ul>
 * Implement the {@link ArgumentPattern} interface to create your own patterns. All non-default patterns are specified with
 * {@link ParserBuilder#forFlagPattern(ArgumentPattern, Consumer) forFlagPattern(ArgumentPattern, Consumer)} or
 * {@link ParserBuilder#forValuePattern(ArgumentPattern, Consumer) forValuePattern(ArgumentPattern, Consumer)}</li>
 * <li>
 * <p>
 * In the Consumer's {@link Consumer#accept(Object) accept()} method, call one or more of the <code>addArgument</code> methods on the
 * argument set builder argument (either {@link FlagArgumentSetBuilder} or {@link ValuedArgumentSetBuilder}) to add arguments. Several
 * argument types are available by default, and any type of argument can be specified with the
 * {@link ValuedArgumentSetBuilder#addArgument(String, Class, ArgumentValueParser, Consumer)} method.
 * </p>
 * <p>
 * The last argument to all the addArgument methods is a consumer that allows the caller to build out the argument's options. Some special
 * argument types (e.g. {@link ValuedArgumentSetBuilder#addFileArgument(String, Consumer) file}- and
 * {@link ValuedArgumentSetBuilder#addInstantArgument(String, Consumer) date}-type arguments) have special options. All arguments can be
 * customized for the number of times they may be specified. Valued (non-flag) arguments can have a
 * {@link ArgumentBuilder#defaultValue(Supplier)}.
 * </p>
 * <p>
 * The number of times an argument may be specified and its value may also be constrained conditionally based on the presence, absence, or
 * value of other arguments already specified by the developer. Use the {@link ArgumentBuilder#when(String, Class, Function)} method.
 * </p>
 * </li>
 * <li>Call {@link ParserBuilder#build()} to obtain an {@link ArgumentParser}. On that parser, call one of the
 * {@link ArgumentParser#parse(String...) parse} methods to parse the command-line argument list. The resulting {@link Arguments} object
 * contains the parsed argument values.</li>
 * <li>Call {@link Arguments#get(String, Class)} or {@link Arguments#getAll(String, Class)} to get typed argument values specified by the
 * user.</li>
 * </ol>
 * </p>
 * <p>
 * <b>Example:</b><br>
 * <code><pre>
	static void main(String... clArgs) {
		ArgumentParsing.Arguments args = ArgumentParsing.build()//
			.forValuePattern(pattern -> pattern//
				.addIntArgument("a", arg -> arg.required())//
				.addIntArgument("b", arg -> arg//
					.when("a", Integer.class, c -> c.matches(v -> v.lt(0)).forbidden())//
					.when("a", Integer.class, c -> c.matches(v -> v.between(0, 100)).required())//
					.when("a", Integer.class, c -> c.matches(v -> v.between(0, 100)).then(v -> v.between(0, 100)))//
					.when("a", Integer.class, c -> c.matches(v -> v.gt(100)).required())//
					.when("a", Integer.class, c -> c.matches(v -> v.gt(100)).then(v -> v.gt(100)))//
				)//
				.addIntArgument("c", arg -> arg.defaultValue(25))//
			).build().parse(clArgs);

		int a = args.get("a", Integer.class);
		Integer b = args.get("b", Integer.class);
		int c = args.get("c", Integer.class);

		System.out.println("a=" + a + ", b=" + b + ", c=" + c);
	}
 * </pre></code> In the above example, 3 integer arguments are specified. The first, "a", is required and unconstrained. The second, "b", is
 * constrained by the value of a. If <code>a&lt;0</code>, b may not be specified. If <code>a>=0 && a&lt;=100</code>, b is required and must
 * also be between 0 and 100. If <code>a>100</code> b is still required and must also be greater than 100.<br>
 * The third argument, "c", may or may not be specified by the user. If unspecified, a value of 25 will be used.<br>
 * The values of each argument are then retrieved. a and c are primitive-typed because they will always have values, either because they are
 * required or defaulted. b is Integer-typed because if <code>a&lt;0</code> it may not be specified, and hence will be null.
 * </p>
 * <p>
 * An additional feature of this class is documentation. The argument set as a whole as well as each argument may be given a description
 * (via {@link ParserBuilder#withDescription(String)} and {@link ArgumentBuilder#withDescription(String)} and the built parser may be
 * configured to print a description of the argument set when the arguments specified by the user violate one or more constraints (this is
 * enabled by default) or when no arguments are specified (not enabled by default). Besides the configured descriptions (if provided), the
 * printed "help" message will contain the name and type of each argument as well as descriptions of all their constraints and default
 * values.
 * </p>
 */
public class ArgumentParsing {
	private ArgumentParsing() {
	}

	/** Default pattern for parsing flag-type arguments */
	public static final ArgumentPattern DEFAULT_FLAG_PATTERN = new ArgumentPattern() {
		@Override
		public int getMaxValues() {
			return 0;
		}

		@Override
		public void validate(String argumentName) {}

		@Override
		public MatchedArgument match(String argument, Supplier<String> moreArgs) {
			if (argument.length() > 2 && argument.startsWith("--"))
				return new MatchedArgument(argument.substring(2), Collections.emptyList());
			return null;
		}

		@Override
		public String print(MatchedArgument argument) {
			return "--" + argument.getName();
		}

		@Override
		public String toString() {
			return "--flag";
		}
	};

	/** Default pattern for parsing single-value arguments */
	public static final ArgumentPattern DEFAULT_VALUE_PATTERN = new ArgumentPattern() {
		@Override
		public int getMaxValues() {
			return 1;
		}

		@Override
		public void validate(String argumentName) {
			if (argumentName.indexOf('=') >= 0)
				throw new IllegalArgumentException("Argument name cannot contain '='");
		}

		@Override
		public MatchedArgument match(String argument, Supplier<String> moreArgs) {
			if (argument.length() < 3 || !argument.startsWith("--"))
				return null;
			int equalIdx = argument.indexOf('=', 2);
			if (equalIdx < 0)
				return null;
			return new MatchedArgument(argument.substring(2, equalIdx), Arrays.asList(argument.substring(equalIdx + 1)));
		}

		@Override
		public String print(MatchedArgument argument) {
			return "--" + argument.getName() + "=" + argument.getValues().get(0);
		}

		@Override
		public String toString() {
			return "--argument=value";
		}
	};

	/**
	 * A pattern for parsing single-value arguments that uses 2 actual command-line arguments, the first for the name (as "--name") and the
	 * second for the value
	 */
	public static final ArgumentPattern SPLIT_VALUE_PATTERN = new ArgumentPattern() {
		@Override
		public int getMaxValues() {
			return 1;
		}

		@Override
		public void validate(String argumentName) {
		}

		@Override
		public MatchedArgument match(String argument, Supplier<String> moreArgs) {
			if (argument.length() < 2 || !argument.startsWith("--"))
				return null;
			String nextArg = moreArgs.get();
			if (nextArg == null)
				return null;
			return new MatchedArgument(argument.substring(2), Arrays.asList(nextArg));
		}

		@Override
		public String print(MatchedArgument argument) {
			return "--" + argument.getName() + "=" + argument.getValues().get(0);
		}

		@Override
		public String toString() {
			return "--argument value";
		}
	};

	/** Default pattern for parsing multi-value arguments */
	public static final ArgumentPattern DEFAULT_MULTI_VALUE_PATTERN = new ArgumentPattern() {
		@Override
		public int getMaxValues() {
			return Integer.MAX_VALUE;
		}

		@Override
		public void validate(String argumentName) {
			if (argumentName.indexOf('=') >= 0)
				throw new IllegalArgumentException("Argument name cannot contain '='");
			else if (argumentName.indexOf(',') >= 0)
				throw new IllegalArgumentException("Argument name cannot contain ','");
		}

		@Override
		public MatchedArgument match(String argument, Supplier<String> moreArgs) {
			if (argument.length() < 3 || !argument.startsWith("--"))
				return null;
			int equalIdx = argument.indexOf('=', 2);
			if (equalIdx < 0)
				return null;
			String name = argument.substring(2, equalIdx);
			List<String> values = new ArrayList<>(5);
			int valueStart = equalIdx + 1;
			for (int i = valueStart; i < argument.length(); i++) {
				if (argument.charAt(i) == ',') {
					values.add(argument.substring(valueStart, i));
					valueStart = i + 1;
				}
			}
			if (valueStart < argument.length())
				values.add(argument.substring(valueStart));
			return new MatchedArgument(name, Collections.unmodifiableList(values));
		}

		@Override
		public String print(MatchedArgument argument) {
			StringBuilder str = new StringBuilder("--").append(argument.getName()).append('=');
			StringUtils.print(str, ",", argument.getValues(), StringBuilder::append);
			return str.toString();
		}

		@Override
		public String toString() {
			return "--argument=value1,value2,value3";
		}
	};

	/**
	 * A pattern for parsing multi-value arguments that uses 2 actual command-line arguments, the first for the name (as "--name") and the
	 * second for the comma-separated values
	 */
	public static final ArgumentPattern SPLIT_MULTI_VALUE_PATTERN = new ArgumentPattern() {
		@Override
		public int getMaxValues() {
			return Integer.MAX_VALUE;
		}

		@Override
		public void validate(String argumentName) {}

		@Override
		public MatchedArgument match(String argument, Supplier<String> moreArgs) {
			if (argument.length() < 2 || !argument.startsWith("--"))
				return null;
			String nextArg = moreArgs.get();
			if (nextArg == null)
				return null;
			List<String> values = new ArrayList<>(5);
			int valueStart = 0;
			for (int i = valueStart; i < nextArg.length(); i++) {
				if (nextArg.charAt(i) == ',') {
					values.add(nextArg.substring(valueStart, i));
					valueStart = i + 1;
				}
			}
			if (valueStart < nextArg.length())
				values.add(nextArg.substring(valueStart));
			return new MatchedArgument(argument.substring(2), Collections.unmodifiableList(values));
		}

		@Override
		public String print(MatchedArgument argument) {
			return "--" + argument.getName() + "=" + argument.getValues().get(0);
		}

		@Override
		public String toString() {
			return "--argument value1,value2,value3";
		}
	};

	/** @return A builder to configure an argument parser */
	public static ParserBuilder build() {
		return new Impl.DefaultParserBuilder();
	}

	/** Parses the structure of an argument */
	public interface ArgumentPattern {
		/**
		 * @return The maximum number of values that can be parsed by this pattern. This will typically be either:
		 *         <ul>
		 *         <li>0 for flag patterns</li>
		 *         <li>1 for single-valued pattern</li>
		 *         <li>or {@link Integer#MAX_VALUE} for multi-valued patterns</li>
		 *         </ul>
		 */
		int getMaxValues();

		/**
		 * @param argumentName The name of the argument to test
		 * @throws IllegalArgumentException If the given argument cannot be used with this pattern
		 */
		void validate(String argumentName) throws IllegalArgumentException;

		/**
		 * @param argument The command-line argument to parse
		 * @param moreArgs Allows this pattern to use more than 1 command-line argument, E.g. "--version 0.2.3"
		 * @return The parsed structure of the argument, or null if the given argument does not match this pattern
		 */
		MatchedArgument match(String argument, Supplier<String> moreArgs);

		/**
		 * @param argument The matched argument to print
		 * @return The argument as it would look via command-line for this pattern
		 */
		String print(MatchedArgument argument);
	}

	/** An argument structure parsed by an {@link ArgumentPattern} */
	public static class MatchedArgument implements Named {
		private final String theName;
		private final List<String> theValues;

		/**
		 * @param name The name of the argument
		 * @param values The argument values
		 */
		public MatchedArgument(String name, List<String> values) {
			theName = name;
			theValues = QommonsUtils.unmodifiableCopy(values);
		}

		@Override
		public String getName() {
			return theName;
		}

		/** @return This argument's values */
		public List<String> getValues() {
			return theValues;
		}

		@Override
		public String toString() {
			return theName + theValues;
		}
	}

	/**
	 * A configured argument to parse from command-line arguments
	 * 
	 * @param <T> The type of the argument's values, or {@link Void} if the argument is value-less (e.g. a flag)
	 */
	public interface ArgumentType<T> extends Named {
		/** @return The argument pattern that parses this argument from a command-line string */
		ArgumentPattern getPattern();

		/** @return The type of the argument's values, or {@link void} if the argument is value-less (a flag) */
		Class<T> getType();

		/** @return Whether this argument type is value-less */
		default boolean isFlag() {
			return getType() == void.class;
		}

		/** @return All argument constraints that this argument must obey */
		List<ArgumentConstraint<T>> getConstraints();

		/** @return A description of the meaning of this argument */
		String getDescription();
	}

	/**
	 * A constraint on either how many times an argument may specified, or on its value
	 * 
	 * @param <T> The type of the constrained argument
	 */
	public interface ArgumentConstraint<T> {
		/** @return A condition that determines whether this constraint applies, or null if it always applies */
		ArgumentCondition<T> getCondition();

		/** @return The minimum number of times the argument must be specified, if the condition applies */
		int getMinTimes();

		/** @return The maximum number of times the argument may be specified, if the condition applies */
		int getMaxTimes();

		/** @return A test that must be satisfied by all values of the argument, if the condition applies */
		ArgumentTest<T> getValueTest();
	}

	/**
	 * A condition test on a set of argument values that, if met, require a constraint on a {@link #getSubject() subject} argument
	 * 
	 * @param <S> The type of the argument the constraint with this condition is on
	 */
	public static abstract class ArgumentCondition<S> {
		private final ParserBuilder theBuilder;
		private final ArgumentType<S> theSubject;
		private final Function<String, Impl.ArgumentTypeHolder<?>> theArgGetter;

		ArgumentCondition(ParserBuilder builder, ArgumentType<S> subject, Function<String, Impl.ArgumentTypeHolder<?>> argGetter) {
			theBuilder = builder;
			theSubject = subject;
			theArgGetter = argGetter;
		}

		/** @return That argument that the constraint with this condition is on */
		public ArgumentType<S> getSubject() {
			return theSubject;
		}

		/**
		 * @param args The set of argument values to use to test whether this condition is met
		 * @return Whether this condition is met for the given arguments
		 */
		abstract boolean applies(Arguments args);

		/** @return A constraint that will require the subject argument to be specified exactly once if this condition is met */
		public ArgumentConstraint<S> required() {
			return times(1, 1);
		}

		/** @return A constraint that will forbid the subject argument being specified if this condition is met */
		public ArgumentConstraint<S> forbidden() {
			return times(0, 0);
		}

		/** @return A constraint that will require the subject argument to be specified at least once if this condition is met */
		public ArgumentConstraint<S> atLeastOnce() {
			return times(1, Integer.MAX_VALUE);
		}

		/**
		 * @param minTimes The minimum number of times the subject must be specified if this condition is met
		 * @param maxTimes The maximum number of times the subject may be specified if this condition is met
		 * @return A constraint that will restrict the number of times the subject argument may be specified (or how many values it may have
		 *         for a multi-valued argument) if this condition is met
		 */
		public ArgumentConstraint<S> times(int minTimes, int maxTimes) {
			return new Impl.DefaultArgConstraint<>(this, minTimes, maxTimes, null);
		}

		/**
		 * @param test A function that creates a value test for the subject argument
		 * @return A constraint that will ensure the subject argument's value passes the given value test if this condition is met
		 */
		public ArgumentConstraint<S> then(Function<ArgumentTestBuilder<S>, ArgumentTest<S>> test) {
			Impl.ArgumentTypeHolder<S> arg = (ArgumentTypeHolder<S>) theArgGetter.apply(theSubject.getName());
			ArgumentTest<S> argTest = test.apply(new Impl.DefaultArgTestBuilder<>(theBuilder, theSubject, arg.parser));
			return new Impl.DefaultArgConstraint<>(this, 0, Integer.MAX_VALUE, argTest);
		}

		/** @return A condition that is true whenever this condition is false and vice versa */
		public ArgumentCondition<S> not() {
			return new NotArgumentCondition<>(theBuilder, this);
		}

		/**
		 * @param <A2> The type of the target argument for the new condition
		 * @param argument The name target argument for the new condition
		 * @param argType The type of the target argument for the new condition
		 * @param other A function that creates a condition on the given argument
		 * @return A condition that is true whenever this condition or the new condition is true
		 */
		public <A2> ArgumentCondition<S> or(String argument, Class<A2> argType,
			Function<ArgumentConditionBuilder<S, A2>, ArgumentCondition<S>> other) {
			Impl.ArgumentTypeHolder<?> arg = theArgGetter.apply(argument);
			if (argType != null && !argType.equals(arg.argument.getType()))
				throw new ClassCastException(
					"Wrong type (" + argType.getName() + ") for argument " + argument + " (" + arg.argument.getType().getName() + ")");
			ArgumentCondition<S> otherCondition = other
				.apply(new Impl.DefaultArgConditionBuilder<>(theBuilder, getSubject(), (ArgumentType<A2>) arg.argument, theArgGetter));
			if (otherCondition instanceof CompositeArgumentCondition && !((CompositeArgumentCondition<S>) otherCondition).isAnd()) {
				List<ArgumentCondition<S>> components = new ArrayList<>(
					((CompositeArgumentCondition<S>) otherCondition).getComponents().size() + 1);
				components.add(this);
				components.addAll(((CompositeArgumentCondition<S>) otherCondition).getComponents());
				return new CompositeArgumentCondition<>(theBuilder, Collections.unmodifiableList(components), false);
			}
			return new CompositeArgumentCondition<>(theBuilder, Collections.unmodifiableList(Arrays.asList(this, otherCondition)), false);
		}

		/**
		 * @param <A2> The type of the target argument for the new condition
		 * @param argument The name target argument for the new condition
		 * @param argType The type of the target argument for the new condition
		 * @param other A function that creates a condition on the given argument
		 * @return A condition that is true only if both this condition and the new condition are true
		 */
		public <A2> ArgumentCondition<S> and(String argument, Class<A2> argType,
			Function<ArgumentConditionBuilder<S, A2>, ArgumentCondition<S>> other) {
			Impl.ArgumentTypeHolder<?> arg = theArgGetter.apply(argument);
			if (argType != null && !argType.equals(arg.argument.getType()))
				throw new ClassCastException(
					"Wrong type (" + argType.getName() + ") for argument " + argument + " (" + arg.argument.getType().getName() + ")");
			ArgumentCondition<S> otherCondition = other
				.apply(new Impl.DefaultArgConditionBuilder<>(theBuilder, getSubject(), (ArgumentType<A2>) arg.argument, theArgGetter));
			if (otherCondition instanceof CompositeArgumentCondition && ((CompositeArgumentCondition<S>) otherCondition).isAnd()) {
				List<ArgumentCondition<S>> components = new ArrayList<>(
					((CompositeArgumentCondition<S>) otherCondition).getComponents().size() + 1);
				components.add(this);
				components.addAll(((CompositeArgumentCondition<S>) otherCondition).getComponents());
				return new CompositeArgumentCondition<>(theBuilder, Collections.unmodifiableList(components), true);
			}
			return new CompositeArgumentCondition<>(theBuilder, Collections.unmodifiableList(Arrays.asList(this, otherCondition)), true);
		}

		/**
		 * A condition on the value of an argument
		 * 
		 * @param <S> The type of the argument the constraint with this condition is on
		 * @param <T> The type of the argument that this condition tests
		 */
		public static class ArgumentValueCondition<S, T> extends ArgumentCondition<S> {
			private final ArgumentType<T> theTarget;
			private final ArgumentTest<T> theValueTest;

			ArgumentValueCondition(ParserBuilder builder, ArgumentType<S> subject, ArgumentType<T> target, ArgumentTest<T> valueTest,
				Function<String, Impl.ArgumentTypeHolder<?>> argGetter) {
				super(builder, subject, argGetter);
				theTarget = target;
				theValueTest = valueTest;
			}

			/** @return The argument that this condition tests */
			public ArgumentType<T> getTarget() {
				return theTarget;
			}

			/** @return The test against the target argument's value */
			public ArgumentTest<T> getValueTest() {
				return theValueTest;
			}

			@Override
			public boolean applies(Arguments args) {
				for (T value : args.getAll(theTarget)) {
					if (theValueTest.test(value, args))
						return true;
				}
				return false;
			}

			@Override
			public String toString() {
				return theValueTest.toString(theTarget.getName());
			}
		}

		/**
		 * A condition that is always true
		 * 
		 * @param <S> The type of the argument the constraint with this condition is on
		 */
		public static class TrueCondition<S> extends ArgumentCondition<S> {
			TrueCondition(ParserBuilder builder, ArgumentType<S> subject, Function<String, ArgumentTypeHolder<?>> argGetter) {
				super(builder, subject, argGetter);
			}

			@Override
			boolean applies(Arguments args) {
				return true;
			}

			@Override
			public <A2> ArgumentCondition<S> or(String argument, Class<A2> argType,
				Function<ArgumentConditionBuilder<S, A2>, ArgumentCondition<S>> other) {
				return this;
			}

			@Override
			public <A2> ArgumentCondition<S> and(String argument, Class<A2> argType,
				Function<ArgumentConditionBuilder<S, A2>, ArgumentCondition<S>> other) {
				return ((ArgumentCondition.CompositeArgumentCondition<S>) super.and(argument, argType, other)).getComponents().get(1);
			}
		}

		/**
		 * A condition on whether or how many times (or values) an argument is specified
		 * 
		 * @param <S> The type of the argument the constraint with this condition is on
		 * @param <T> The type of the argument that this condition tests
		 */
		public static class ArgumentSpecifiedCondition<S, T> extends ArgumentCondition<S> {
			private final ArgumentType<T> theTarget;
			private final int theMinSpecified;
			private final int theMaxSpecified;

			ArgumentSpecifiedCondition(ParserBuilder builder, ArgumentType<S> subject, ArgumentType<T> target, int minSpecified,
				int maxSpecified, Function<String, Impl.ArgumentTypeHolder<?>> argGetter) {
				super(builder, subject, argGetter);
				theTarget = target;
				theMinSpecified = minSpecified;
				theMaxSpecified = maxSpecified;
			}

			/** @return The argument that this condition tests */
			public ArgumentType<T> getTarget() {
				return theTarget;
			}

			/**
			 * @return The minimum number of times the target argument may be specified (or the number of its values) for this condition to
			 *         be met
			 */
			public int getMinSpecified() {
				return theMinSpecified;
			}

			/**
			 * @return The maximum number of times the target argument may be specified (or the number of its values) for this condition to
			 *         be met
			 */
			public int getMaxSpecified() {
				return theMaxSpecified;
			}

			@Override
			public boolean applies(Arguments args) {
				int specified = 0;
				for (Argument<?> arg : args.getArguments(theTarget)) {
					if (arg.getMatch() != null)
						specified++;
				}
				return specified >= theMinSpecified && specified <= theMaxSpecified;
			}

			@Override
			public String toString() {
				return theTarget + "{" + theMinSpecified + "," + theMaxSpecified + "}";
			}
		}

		/**
		 * Implements {@link ArgumentCondition#not()}
		 * 
		 * @param <S> The type of the argument the constraint with this condition is on
		 */
		public static class NotArgumentCondition<S> extends ArgumentCondition<S> {
			private final ArgumentCondition<S> theWrapped;

			NotArgumentCondition(ParserBuilder builder, ArgumentCondition<S> wrapped) {
				super(builder, wrapped.getSubject(), wrapped.theArgGetter);
				theWrapped = wrapped;
			}

			/** @return The condition that this is the anti-condition of */
			public ArgumentCondition<S> getWrapped() {
				return theWrapped;
			}

			@Override
			boolean applies(Arguments args) {
				return !theWrapped.applies(args);
			}

			@Override
			public String toString() {
				return "NOT(" + theWrapped + ")";
			}
		}

		/**
		 * Implements {@link ArgumentCondition#or(String, Class, Function)} and {@link ArgumentCondition#and(String, Class, Function)}
		 * 
		 * @param <S> The type of the argument the constraint with this condition is on
		 */
		public static class CompositeArgumentCondition<S> extends ArgumentCondition<S> {
			private final List<ArgumentCondition<S>> theComponents;
			private final boolean isAnd;

			CompositeArgumentCondition(ParserBuilder builder, List<ArgumentCondition<S>> components, boolean and) {
				super(builder, components.get(0).getSubject(), components.get(0).theArgGetter);
				theComponents = components;
				isAnd = and;
			}

			/** @return The components of this condition */
			public List<ArgumentCondition<S>> getComponents() {
				return theComponents;
			}

			/**
			 * @return Whether this is an {@link ArgumentCondition#and(String, Class, Function) AND} or an
			 *         {@link ArgumentCondition#or(String, Class, Function) OR} condition
			 */
			public boolean isAnd() {
				return isAnd;
			}

			@Override
			public boolean applies(Arguments args) {
				for (ArgumentCondition<S> component : theComponents) {
					boolean matches = component.applies(args);
					if (isAnd) {
						if (!matches)
							return false;
					} else {
						if (matches)
							return true;
					}
				}
				if (isAnd)
					return true;
				else
					return false;
			}

			@Override
			public String toString() {
				StringBuilder str = new StringBuilder();
				for (ArgumentCondition<S> component : theComponents) {
					if (str.length() > 0) {
						str.append(isAnd ? " AND " : " OR ");
					}
					if (component instanceof CompositeArgumentCondition) {
						str.append('(');
					}
					str.append(component);
					if (component instanceof CompositeArgumentCondition) {
						str.append(')');
					}
				}
				return str.toString();
			}
		}
	}

	/**
	 * A test against an argument's value
	 * 
	 * @param <T> The type of the argument whose value to test
	 */
	public static abstract class ArgumentTest<T> {
		final ParserBuilder theBuilder;
		final ArgumentType<T> theSubject;
		final ArgumentValueParser<? extends T> theParser;

		ArgumentTest(ParserBuilder builder, ArgumentType<T> subject, ArgumentValueParser<? extends T> parser) {
			theBuilder = builder;
			theSubject = subject;
			theParser = parser;
		}

		/** @return The argument type that this test is for */
		public ArgumentType<T> getSubject() {
			return theSubject;
		}

		// This interface doesn't extend Predicate because it makes the .and() and .or() syntax confusing
		abstract boolean test(T value, Arguments args);

		/** @return A test that is true when this test is false and vice versa */
		public ArgumentTest<T> not() {
			return new NotArgumentTest<>(this);
		}

		/**
		 * @param other A function that creates another test on this test's argument
		 * @return A test that is true whenever this test or the new test is true
		 */
		public ArgumentTest<T> or(Function<ArgumentTestBuilder<T>, ArgumentTest<T>> other) {
			ArgumentTest<T> otherTest = other.apply(new Impl.DefaultArgTestBuilder<>(theBuilder, theSubject, theParser));
			if (otherTest instanceof CompositeArgumentTest && !((CompositeArgumentTest<T>) otherTest).isAnd()) {
				List<ArgumentTest<T>> components = new ArrayList<>(((CompositeArgumentTest<T>) otherTest).getComponents().size() + 1);
				components.add(this);
				components.addAll(((CompositeArgumentTest<T>) otherTest).getComponents());
				return new CompositeArgumentTest<>(theBuilder, theSubject, Collections.unmodifiableList(components), false);
			}
			return new CompositeArgumentTest<>(theBuilder, theSubject, Collections.unmodifiableList(Arrays.asList(this, otherTest)), false);
		}

		/**
		 * @param other A function that creates another test on this test's argument
		 * @return A test that is true only when both this test and the new test is true
		 */
		public ArgumentTest<T> and(Function<ArgumentTestBuilder<T>, ArgumentTest<T>> other) {
			ArgumentTest<T> otherTest = other.apply(new Impl.DefaultArgTestBuilder<>(theBuilder, theSubject, theParser));
			if (otherTest instanceof CompositeArgumentTest && ((CompositeArgumentTest<T>) otherTest).isAnd()) {
				List<ArgumentTest<T>> components = new ArrayList<>(((CompositeArgumentTest<T>) otherTest).getComponents().size() + 1);
				components.add(this);
				components.addAll(((CompositeArgumentTest<T>) otherTest).getComponents());
				return new CompositeArgumentTest<>(theBuilder, theSubject, Collections.unmodifiableList(components), true);
			}
			return new CompositeArgumentTest<>(theBuilder, theSubject, Collections.unmodifiableList(Arrays.asList(this, otherTest)), true);
		}

		abstract String toString(String argumentName);

		/** The type of an argument value comparison */
		public enum ComparisonType {
			/** Equality test */
			EQ("="),
			/** Inequality test */
			NEQ("!="),
			/** Exclusive maximum test */
			LT("<"),
			/** Inclusive maximum test */
			LTE("<="),
			/** Exclusive minimum test */
			GT(">"),
			/** Inclusive minimum test */
			GTE(">=");
		
			/** The code descriptor of this type (e.g. ">=") */
			public final String descrip;
		
			private ComparisonType(String descrip) {
				this.descrip = descrip;
			}
		}

		/**
		 * A comparison test against an argument's value
		 * 
		 * @param <T> The type of the argument whose value to test
		 */
		public static class ValueTest<T> extends ArgumentTest<T> {
			private final T theTestValue;
			private final ComparisonType theComparison;
			private final Comparator<? super T> theComparator;

			ValueTest(ParserBuilder builder, ArgumentType<T> subject, T testValue, ComparisonType comparison,
				Comparator<? super T> comparator,
				ArgumentValueParser<? extends T> parser) {
				super(builder, subject, parser);
				theTestValue = testValue;
				theComparison = comparison;

				if (comparator == null) {
					switch (comparison) {
					case LT:
					case LTE:
					case GT:
					case GTE:
						if (testValue instanceof Comparable)
							comparator = (v1, v2) -> -((Comparable<T>) v2).compareTo(v1);
						else
							throw new IllegalArgumentException(
								"No comparator configured for \"" + getComparison().descrip + "\" comparison");
						break;
					default:
						break;
					}
				}
				theComparator = comparator;
			}

			/** @return The value being tested against */
			public T getTestValue() {
				return theTestValue;
			}

			/** @return The type of this test's comparison */
			public ComparisonType getComparison() {
				return theComparison;
			}

			/** @return The comparator used for non-equality tests */
			public Comparator<? super T> getComparator() {
				return theComparator;
			}

			@Override
			public boolean test(T value, Arguments args) {
				T testValue = getTestValue();
				switch (getComparison()) {
				case EQ:
					return Objects.equals(testValue, value);
				case NEQ:
					return !Objects.equals(testValue, value);
				case LT:
					return theComparator.compare(value, testValue) < 0;
				case LTE:
					return theComparator.compare(value, testValue) <= 0;
				case GT:
					return theComparator.compare(value, testValue) > 0;
				case GTE:
					return theComparator.compare(value, testValue) >= 0;
				default:
					throw new IllegalStateException("Unrecognized comparison: " + getComparison());
				}
			}

			@Override
			public String toString(String argumentName) {
				return argumentName + getComparison().descrip + getTestValue();
			}

			@Override
			public String toString() {
				return getComparison().descrip + getTestValue();
			}
		}

		/**
		 * A test that is true only if an argument's value is equal to one of a set of values
		 * 
		 * @param <T> The type of the argument whose value to test
		 */
		public static class OneOfValueTest<T> extends ArgumentTest<T> {
			private final Set<T> theValues;

			OneOfValueTest(ParserBuilder builder, ArgumentType<T> subject, Collection<? extends T> values,
				ArgumentValueParser<? extends T> parser) {
				super(builder, subject, parser);
				theValues = Collections.unmodifiableSet(new LinkedHashSet<>(values));
			}

			/** @return The set of values to check */
			public Set<T> getValues() {
				return theValues;
			}

			@Override
			public boolean test(T value, Arguments args) {
				return theValues.contains(value);
			}

			@Override
			public String toString(String argumentName) {
				return argumentName + " IN(" + StringUtils.print(", ", theValues, String::valueOf) + ")";
			}

			@Override
			public String toString() {
				return "IN(" + StringUtils.print(", ", theValues, String::valueOf) + ")";
			}
		}

		/**
		 * Implements {@link ArgumentTest#not()}
		 * 
		 * @param <T> The type of the argument whose value to test
		 */
		public static class NotArgumentTest<T> extends ArgumentTest<T> {
			private final ArgumentTest<T> theWrapped;

			NotArgumentTest(ArgumentTest<T> wrapped) {
				super(wrapped.theBuilder, wrapped.theSubject, wrapped.theParser);
				theWrapped = wrapped;
			}

			@Override
			public boolean test(T value, Arguments args) {
				return !theWrapped.test(value, args);
			}

			@Override
			public String toString(String argumentName) {
				return "NOT(" + theWrapped.toString(argumentName) + ")";
			}

			@Override
			public String toString() {
				return "NOT(" + theWrapped + ")";
			}
		}

		/**
		 * Implements {@link ArgumentTest#or(Function)} and {@link ArgumentTest#and(Function)}
		 * 
		 * @param <T> The type of the argument whose value to test
		 */
		public static class CompositeArgumentTest<T> extends ArgumentTest<T> {
			private final List<ArgumentTest<T>> theComponents;
			private final boolean isAnd;

			CompositeArgumentTest(ParserBuilder builder, ArgumentType<T> subject, List<ArgumentTest<T>> components, boolean and) {
				super(builder, subject, components.get(0).theParser);
				theComponents = components;
				isAnd = and;
			}

			/** @return The components of this test */
			public List<ArgumentTest<T>> getComponents() {
				return theComponents;
			}

			/** @return Whether this is an {@link ArgumentTest#and(Function) AND} test or an {@link ArgumentTest#or(Function) OR} test */
			public boolean isAnd() {
				return isAnd;
			}

			@Override
			public boolean test(T value, Arguments args) {
				for (ArgumentTest<T> component : theComponents) {
					boolean passes = component.test(value, args);
					if (isAnd) {
						if (!passes)
							return false;
					} else {
						if (passes)
							return true;
					}
				}
				if (isAnd)
					return true;
				else
					return false;
			}

			@Override
			public ArgumentTest<T> or(Function<ArgumentTestBuilder<T>, ArgumentTest<T>> other) {
				ArgumentTest<T> otherTest = other.apply(new Impl.DefaultArgTestBuilder<>(theBuilder, theSubject, theParser));
				if (isAnd)
					return new CompositeArgumentTest<>(theBuilder, theSubject, Collections.unmodifiableList(Arrays.asList(this, otherTest)),
						false);
				List<ArgumentTest<T>> components = new ArrayList<>(theComponents.size() + 1);
				components.addAll(theComponents);
				components.add(otherTest);
				return new CompositeArgumentTest<>(theBuilder, theSubject, Collections.unmodifiableList(components), false);
			}

			@Override
			public ArgumentTest<T> and(Function<ArgumentTestBuilder<T>, ArgumentTest<T>> other) {
				ArgumentTest<T> otherTest = other.apply(new Impl.DefaultArgTestBuilder<>(theBuilder, theSubject, theParser));
				if (!isAnd)
					return new CompositeArgumentTest<>(theBuilder, theSubject, Collections.unmodifiableList(Arrays.asList(this, otherTest)),
						true);
				List<ArgumentTest<T>> components = new ArrayList<>(theComponents.size() + 1);
				components.addAll(theComponents);
				components.add(otherTest);
				return new CompositeArgumentTest<>(theBuilder, theSubject, Collections.unmodifiableList(components), true);
			}

			@Override
			public String toString(String argumentName) {
				StringBuilder str = new StringBuilder();
				for (ArgumentTest<T> component : theComponents) {
					if (str.length() > 0) {
						str.append(isAnd ? " AND " : " OR ");
					}
					if (component instanceof CompositeArgumentTest) {
						str.append('(');
					}
					str.append(component.toString(argumentName));
					if (component instanceof CompositeArgumentTest) {
						str.append(')');
					}
				}
				return str.toString();
			}

			@Override
			public String toString() {
				StringBuilder str = new StringBuilder();
				for (ArgumentTest<T> component : theComponents) {
					if (str.length() > 0) {
						str.append(isAnd ? " AND " : " OR ");
					}
					if (component instanceof CompositeArgumentTest) {
						str.append('(');
					}
					str.append(component);
					if (component instanceof CompositeArgumentTest) {
						str.append(')');
					}
				}
				return str.toString();
			}
		}

		/**
		 * An argument test that tests the {@link #getSubject() subject}'s value against that of a {@link #getTarget() target} argument's
		 * 
		 * @param <T> The type of the subject argument
		 * @param <A> The type of the target argument
		 */
		public static abstract class BiArgumentTest<T, A> extends ArgumentTest<T> {
			private final ArgumentType<A> theTarget;

			BiArgumentTest(ParserBuilder builder, ArgumentType<T> subject, ArgumentValueParser<? extends T> parser,
				ArgumentType<A> otherArg) {
				super(builder, subject, parser);
				theTarget = otherArg;
			}

			/** @return The target argument */
			public ArgumentType<A> getTarget() {
				return theTarget;
			}
		}

		/**
		 * Compares the value of the {@link #getSubject() subject} argument against a singular {@link #getTarget() target} argument value
		 * 
		 * @param <T> The type of the subject argument
		 * @param <A> The type of the target argument
		 */
		public static class BiArgumentCompareTest<T, A> extends BiArgumentTest<T, A> {
			private final ComparisonType theComparison;
			private final ToIntBiFunction<? super T, ? super A> theComparator;

			BiArgumentCompareTest(ParserBuilder builder, ArgumentType<T> subject, ArgumentValueParser<? extends T> parser,
				ArgumentType<A> otherArg, ComparisonType comparison, ToIntBiFunction<? super T, ? super A> comparator) {
				super(builder, subject, parser, otherArg);
				theComparison = comparison;
				theComparator = comparator;
			}

			/** @return The function to compare the 2 argument values */
			public ComparisonType getComparison() {
				return theComparison;
			}

			@SuppressWarnings("unlikely-arg-type")
			@Override
			boolean test(T value, Arguments args) {
				Argument<A> testArg = args.getArgument(getTarget());
				if (testArg == null || testArg.getValues().isEmpty())
					return true;
				A testValue = testArg.getValues().getFirst();
				switch (getComparison()) {
				case EQ:
					if (theComparator == null)
						return Objects.equals(testValue, value);
					else
						return theComparator.applyAsInt(value, testValue) == 0;
				case NEQ:
					if (theComparator == null)
						return !Objects.equals(testValue, value);
					else
						return theComparator.applyAsInt(value, testValue) != 0;
				case LT:
					return theComparator.applyAsInt(value, testValue) < 0;
				case LTE:
					return theComparator.applyAsInt(value, testValue) <= 0;
				case GT:
					return theComparator.applyAsInt(value, testValue) > 0;
				case GTE:
					return theComparator.applyAsInt(value, testValue) >= 0;
				default:
					throw new IllegalStateException("Unrecognized comparison: " + getComparison());
				}
			}

			@Override
			String toString(String argumentName) {
				return argumentName + getComparison().descrip + getTarget().getName();
			}

			@Override
			public String toString() {
				return getComparison().descrip + getTarget().getName();
			}
		}

		/**
		 * Ensures that the value of the {@link #getSubject() subject} argument is contained in the set of {@link #getTarget() target}
		 * arguments
		 * 
		 * @param <T> The type of the subject argument
		 * @param <A> The type of the target argument
		 */
		public static class BiArgumentOneOfTest<T, A> extends BiArgumentTest<T, A> {
			private final ToIntBiFunction<? super T, ? super A> theEqualsTest;

			BiArgumentOneOfTest(ParserBuilder builder, ArgumentType<T> subject, ArgumentValueParser<? extends T> parser,
				ArgumentType<A> otherArg, ToIntBiFunction<? super T, ? super A> equalsTest) {
				super(builder, subject, parser, otherArg);
				theEqualsTest = equalsTest;
			}

			@SuppressWarnings("unlikely-arg-type")
			@Override
			boolean test(T value, Arguments args) {
				Argument<A> testArg = args.getArgument(getTarget());
				if (testArg == null)
					return false;
				for (A testValue : testArg.getValues()) {
					boolean equals;
					if (theEqualsTest == null)
						equals = Objects.equals(testValue, value);
					else
						equals = theEqualsTest.applyAsInt(value, testValue) == 0;
					if (equals)
						return true;
				}
				return false;
			}

			@Override
			String toString(String argumentName) {
				return argumentName + " IN " + getTarget().getName() + " values";
			}

			@Override
			public String toString() {
				return "IN " + getTarget().getName() + " values";
			}
		}
	}

	/**
	 * An argument parsed by an {@link ArgumentParser}
	 * 
	 * @param <T> The type of the argument's value, or <code>void.class</code> if it is a flag argument
	 */
	public interface Argument<T> {
		/** @return The type of this argument */
		ArgumentType<T> getType();

		/** @return This argument's values */
		BetterList<T> getValues();

		/**
		 * @return The command-line argument String that this argument was parsed from, or null if this argument represents a
		 *         {@link ArgumentBuilder#defaultValue(Supplier) default value}
		 */
		MatchedArgument getMatch();
	}

	/** A parser for parsing command-line arguments */
	public static interface ArgumentParser {
		/** @return The argument types configured in this parser */
		List<ArgumentType<?>> getArguments();

		/**
		 * @param name The name of the argument
		 * @return The argument type with the given name configured in this parser
		 * @throws IllegalArgumentException If no such argument has been configured in this parser
		 */
		ArgumentType<?> getArgument(String name) throws IllegalArgumentException;

		/**
		 * @param name The name of the argument
		 * @return The argument type with the given name configured in this parser, or null if no such argument has been configured in this
		 *         parser
		 */
		ArgumentType<?> getArgumentIfExists(String name);

		/** @return Whether argument Strings not matching any argument type in this parser are acceptable */
		boolean isAcceptingUnmatched();

		/**
		 * @param <T> The compile-time type of the argument to get
		 * @param name The name of the argument to get
		 * @param type The run-time type of the argument to get
		 * @return The argument type with the given name configured in this parser
		 * @throws IllegalArgumentException If no such argument has been configured in this parser
		 * @throws ClassCastException If the argument does not match the given type
		 */
		default <T> ArgumentType<? extends T> getArgument(String name, Class<T> type) throws IllegalArgumentException, ClassCastException {
			ArgumentType<?> arg = getArgument(name);
			if (type.isPrimitive())
				type = Impl.wrap(type);
			Class<?> argType = arg.getType();
			if (argType.isPrimitive())
				argType = Impl.wrap(argType);
			if (!type.isAssignableFrom(argType))
				throw new ClassCastException(
					"Argument " + name + " (" + arg.getType().getName() + ") cannot be used as a " + type.getName());
			return (ArgumentType<? extends T>) arg;
		}

		/**
		 * @param args The command-line arguments to parse
		 * @return The parsed argument set
		 * @throws IllegalArgumentException If the arguments couldn't be parsed for any reason
		 */
		default Arguments parse(String... args) throws IllegalArgumentException {
			return parse(Arrays.asList(args));
		}

		/**
		 * @param args The command-line arguments to parse
		 * @return The parsed argument set
		 * @throws IllegalArgumentException If the arguments couldn't be parsed for any reason
		 */
		default Arguments parse(Iterable<String> args) throws IllegalArgumentException {
			List<String> argList = new ArrayList<>();
			for (String arg : args)
				argList.add(arg);
			return parse(argList, true);
		}

		/**
		 * @param args The command-line arguments to parse. This collection will be drained as the arguments are successfully parsed.
		 * @param completely If true, arguments that do not match any argument types in this parser will be added to the result's
		 *        {@link Arguments#getUnmatched() unmatched} list (if so configured) or an exception will be thrown. If false, the arguments
		 *        will be left in the collection.
		 * @return The parsed argument set
		 * @throws IllegalArgumentException If the arguments couldn't be parsed for any reason
		 */
		Arguments parse(List<String> args, boolean completely) throws IllegalArgumentException;

		/**
		 * @param print Whether this parser should print it's {@link #printHelp() help} message to {@link System#out} if
		 *        {@link #parse(List, boolean)} is called with an empty collection. This is true by default.
		 * @return This parser
		 */
		ArgumentParser printHelpOnEmpty(boolean print);

		/**
		 * @param print Whether this parser should print it's {@link #printHelp() help} message to {@link System#out} if argument
		 *        {@link #parse(List, boolean) parsing} fails. This is true by default.
		 * @return This parser
		 */
		ArgumentParser printHelpOnError(boolean print);

		/** @return A help message detailing each argument type in this parser */
		String printHelp();
	}

	/** A set of command-line arguments parsed by an {@link ArgumentParser} */
	public interface Arguments {
		/** @return The parser that parsed this argument set */
		ArgumentParser getParser();

		/**
		 * @param <T> The type of the argument
		 * @param type The argument type to get the arguments for
		 * @return All arguments specified in the command-line input that matched the given argument type. If no such command-line arguments
		 *         were given, this list will be empty or, if {@link ArgumentBuilder#defaultValue(Supplier) defaulted} the default value.
		 */
		<T> BetterList<Argument<T>> getArguments(ArgumentType<T> type);

		/**
		 * @param <T> The type of the argument
		 * @param type The argument type to get the argument for
		 * @return The first argument specified in the command-line input that matched the given argument type. If no such command-line
		 *         arguments were given, this will null or, if {@link ArgumentBuilder#defaultValue(Supplier) defaulted} the default value.
		 */
		default <T> Argument<T> getArgument(ArgumentType<T> type) {
			return getArguments(type).peekFirst();
		}

		/** @return All command-line input arguments that did not match any argument type in the parser */
		BetterList<String> getUnmatched();

		/**
		 * @param argumentName The name of the argument
		 * @return The first argument specified in the command-line input that matched the given argument type. If no such command-line
		 *         arguments were given, this will null or, if {@link ArgumentBuilder#defaultValue(Supplier) defaulted} the default value.
		 * @throws IllegalArgumentException If no such argument has been configured in this parser
		 */
		default Argument<?> getArgument(String argumentName) throws IllegalArgumentException {
			return getArgument(getParser().getArgument(argumentName));
		}

		/**
		 * @param <T> The type of the argument
		 * @param argumentName The name of the argument to get
		 * @param type The type of the argument to get
		 * @return The first argument specified in the command-line input that matched the given argument type. If no such command-line
		 *         arguments were given, this will null or, if {@link ArgumentBuilder#defaultValue(Supplier) defaulted} the default value.
		 * @throws IllegalArgumentException If no such argument has been configured in this parser
		 * @throws ClassCastException If the argument does not match the given type
		 */
		default <T> Argument<? extends T> getArgument(String argumentName, Class<T> type)
			throws IllegalArgumentException, ClassCastException {
			return getArgument(getParser().getArgument(argumentName, type));
		}

		/**
		 * @param argumentName The name of the argument to get
		 * @return All arguments specified in the command-line input that matched the given argument type. If no such command-line arguments
		 *         were given, this list will be empty or, if {@link ArgumentBuilder#defaultValue(Supplier) defaulted} the default value.
		 * @throws IllegalArgumentException If no such argument has been configured in this parser
		 */
		default BetterList<? extends Argument<?>> getArguments(String argumentName) throws IllegalArgumentException {
			return getArguments(getParser().getArgument(argumentName));
		}

		/**
		 * @param <T> The type of the argument to get
		 * @param argumentName The name of the argument to get
		 * @param type The type of the argument to get
		 * @return All arguments specified in the command-line input that matched the given argument type. If no such command-line arguments
		 *         were given, this list will be empty or, if {@link ArgumentBuilder#defaultValue(Supplier) defaulted} the default value.
		 * @throws IllegalArgumentException If no such argument has been configured in this parser
		 * @throws ClassCastException If the argument does not match the given type
		 */
		default <T> BetterList<Argument<? extends T>> getArguments(String argumentName, Class<T> type)
			throws IllegalArgumentException, ClassCastException {
			ArgumentType<? extends T> argType = getParser().getArgument(argumentName, type);
			// Not sure why this generic expression doesn't just flow
			return (BetterList<Argument<? extends T>>) (BetterList<?>) getArguments(argType);
		}

		/**
		 * @param argumentName The name of the argument
		 * @return The value of the first argument specified in the command-line input that matched the given argument type. If no such
		 *         command-line arguments were given, this will null or, if {@link ArgumentBuilder#defaultValue(Supplier) defaulted} the
		 *         default value.
		 * @throws IllegalArgumentException If no such argument has been configured in this parser
		 */
		default Object get(String argumentName) throws IllegalArgumentException {
			Argument<?> arg = getArgument(argumentName);
			return arg == null ? null : arg.getValues().peekFirst();
		}

		/**
		 * @param <T> The type of the argument
		 * @param argumentName The name of the argument to get
		 * @param type The type of the argument to get
		 * @return The value of the first argument specified in the command-line input that matched the given argument type. If no such
		 *         command-line arguments were given, this will null or, if {@link ArgumentBuilder#defaultValue(Supplier) defaulted} the
		 *         default value.
		 * @throws IllegalArgumentException If no such argument has been configured in this parser
		 * @throws ClassCastException If the argument does not match the given type
		 */
		default <T> T get(String argumentName, Class<T> type) throws IllegalArgumentException, ClassCastException {
			Argument<? extends T> arg = getArgument(argumentName, type);
			return arg == null ? null : arg.getValues().peekFirst();
		}

		/**
		 * @param argumentName The name of the argument to get
		 * @return The value of all arguments specified in the command-line input that matched the given argument type. If no such
		 *         command-line arguments were given, this list will be empty or, if {@link ArgumentBuilder#defaultValue(Supplier)
		 *         defaulted} the default value.
		 * @throws IllegalArgumentException If no such argument has been configured in this parser
		 */
		default BetterList<?> getAll(String argumentName) throws IllegalArgumentException {
			return BetterList.of(getArguments(argumentName).stream().flatMap(arg -> arg.getValues().stream()));
		}

		/**
		 * @param <T> The type of the argument to get
		 * @param argumentName The name of the argument to get
		 * @param type The type of the argument to get
		 * @return The value of all arguments specified in the command-line input that matched the given argument type. If no such
		 *         command-line arguments were given, this list will be empty or, if {@link ArgumentBuilder#defaultValue(Supplier)
		 *         defaulted} the default value.
		 * @throws IllegalArgumentException If no such argument has been configured in this parser
		 * @throws ClassCastException If the argument does not match the given type
		 */
		default <T> BetterList<? extends T> getAll(String argumentName, Class<T> type) throws IllegalArgumentException, ClassCastException {
			return BetterList.of(getArguments(argumentName, type).stream().flatMap(arg -> arg.getValues().stream()));
		}

		/**
		 * @param <T> The type of the argument
		 * @param type The argument type to get the values for
		 * @return All values specified in the command-line input that matched the given argument type. If no such command-line arguments
		 *         were given, this list will be empty or, if {@link ArgumentBuilder#defaultValue(Supplier) defaulted} the default value.
		 */
		default <T> BetterList<T> getAll(ArgumentType<T> type) {
			return BetterList.of(getArguments(type).stream().flatMap(arg -> arg.getValues().stream()));
		}

		/**
		 * @param argument The name of the argument
		 * @return Whether the given argument was specified (or {@link ArgumentBuilder#defaultValue(Supplier) defaulted})
		 */
		default boolean has(String argument) {
			return !getArguments(argument).isEmpty();
		}

		/** @return All arguments in this argument set */
		BetterList<Argument<?>> getAllArguments();
	}

	/** Builds an {@link ArgumentParser} */
	public interface ParserBuilder {
		/** @return An {@link ArgumentParser} built with the argument types specified on this builder */
		ArgumentParser build();

		/**
		 * @param argName The name of the argument to get
		 * @return The argument already defined in this parser builder with the given name, or null if no such argument has been defined
		 */
		ArgumentType<?> getArgument(String argName);

		/**
		 * Allows the addition of flag-type arguments with the given pattern
		 * 
		 * @param pattern The pattern to match argument strings
		 * @param args A builder to specify flag arguments
		 * @return This builder
		 */
		ParserBuilder forFlagPattern(ArgumentPattern pattern, Consumer<FlagArgumentSetBuilder> args);

		/**
		 * Allows the addition of valued arguments with the given pattern
		 * 
		 * @param pattern The pattern to match argument strings
		 * @param args A builder to specify value arguments
		 * @return This builder
		 */
		ParserBuilder forValuePattern(ArgumentPattern pattern, Consumer<ValuedArgumentSetBuilder> args);

		/**
		 * Allows the addition of flag-type arguments with the default flag pattern (--argument)
		 * 
		 * @param args A builder to specify flag arguments
		 * @return This builder
		 */
		default ParserBuilder forFlagPattern(Consumer<FlagArgumentSetBuilder> args) {
			return forFlagPattern(DEFAULT_FLAG_PATTERN, args);
		}

		/**
		 * Allows the addition of single-valued arguments with the default single-value pattern (--argument=value)
		 * 
		 * @param args A builder to specify single-value arguments
		 * @return This builder
		 */
		default ParserBuilder forValuePattern(Consumer<ValuedArgumentSetBuilder> args) {
			return forValuePattern(DEFAULT_VALUE_PATTERN, args);
		}

		/**
		 * Allows the addition of multi-valued arguments with the default multi-value pattern (--argument=value1,value2...)
		 * 
		 * @param args A builder to specify multi-value arguments
		 * @return This builder
		 */
		default ParserBuilder forMultiValuePattern(Consumer<ValuedArgumentSetBuilder> args) {
			return forValuePattern(DEFAULT_MULTI_VALUE_PATTERN, args);
		}

		/**
		 * @param accept Whether command-line arguments not matching any configured argument type should be acceptable (and included in the
		 *        result's {@link Arguments#getUnmatched()} field). False by default.
		 * @return This builder
		 */
		ParserBuilder acceptUnmatched(boolean accept);

		/**
		 * @return Whether command-line arguments not matching any configured argument type should be acceptable (and included in the
		 *         result's {@link Arguments#getUnmatched()} field)
		 * @see #acceptUnmatched(boolean)
		 */
		boolean isAcceptingUnmatched();

		/**
		 * Copies all argument types from a different parser
		 * 
		 * @param parser The parser to copy argument types from
		 * @return This builder
		 */
		ParserBuilder copy(ArgumentParser parser);

		/**
		 * Adds a description to this builder. Will be printed out as part of the parser's {@link ArgumentParser#printHelp() help} message.
		 * 
		 * @param descrip A description of the purpose of this argument set
		 * @return This builder
		 */
		ParserBuilder withDescription(String descrip);

		/**
		 * @return The description for the parser
		 * @see #withDescription(String)
		 */
		String getDescription();
	}

	/**
	 * A builder to configure flag arguments
	 * 
	 * @see ParserBuilder#forFlagPattern(ArgumentPattern, Consumer)
	 */
	public interface FlagArgumentSetBuilder {
		/**
		 * @param name The name for the new argument
		 * @param configure Configures the argument (optional)
		 * @return This argument set builder
		 */
		FlagArgumentSetBuilder add(String name, Consumer<ArgumentBuilder<Void, ?>> configure);
	}

	/**
	 * Parses argument values
	 * 
	 * @param <T> The type of the argument to parse
	 */
	public interface ArgumentValueParser<T> {
		/**
		 * @param text The text representing the value as specified in the command-line argument
		 * @param otherArgs Other arguments in the argument set. If arguments are requested that have not been parsed yet, they will be
		 *        parsed upon request. Circularities will result in an exception. Arguments returned from this {@link Arguments} object have
		 *        <b>NOT</b> been validated against any conditional constraints (those that depend on other argument values).
		 * @return The parsed value
		 * @throws ParseException If the value cannot be parsed (either this or {@link IllegalArgumentException} may be thrown)
		 * @throws IllegalArgumentException If the value cannot be parsed (either this or {@link ParseException} may be thrown)
		 */
		T parse(String text, Arguments otherArgs) throws ParseException, IllegalArgumentException;
	}

	/**
	 * A builder to configure valued arguments
	 * 
	 * @see ParserBuilder#forValuePattern(ArgumentPattern, Consumer)
	 */
	public interface ValuedArgumentSetBuilder {
		/**
		 * Adds an argument type for the parser
		 * 
		 * @param <T> The type of the argument's value
		 * @param name The name for the argument
		 * @param type The type of the argument's value
		 * @param parser The parser to parse the argument's values
		 * @param configure Configures the argument (optional), adding constraints and/or a default value
		 * @return This builder
		 */
		<T> ValuedArgumentSetBuilder addArgument(String name, Class<T> type, ArgumentValueParser<? extends T> parser,
			Consumer<? super ArgumentBuilder<T, ?>> configure);

		/**
		 * Adds an argument type for the parser
		 * 
		 * @param <T> The type of the argument's value
		 * @param name The name for the argument
		 * @param type The type of the argument's value
		 * @param parser The parser to parse the argument's values
		 * @param configure Configures the argument (optional), adding constraints and/or a default value
		 * @return This builder
		 */
		default <T> ValuedArgumentSetBuilder addSimpleArgument(String name, Class<T> type, Function<String, T> parser,
			Consumer<? super ArgumentBuilder<T, ?>> configure) {
			return addArgument(name, type, (text, otherArgs) -> parser.apply(text), configure);
		}

		/**
		 * Adds a {@link String}-type argument type for the parser
		 * 
		 * @param name The name for the argument
		 * @param configure Configures the argument (optional), adding constraints and/or a default value
		 * @return This builder
		 */
		default ValuedArgumentSetBuilder addStringArgument(String name, Consumer<? super ArgumentBuilder<String, ?>> configure) {
			return addSimpleArgument(name, String.class, v -> v, configure);
		}

		/**
		 * Adds an {@link Integer}-type argument type for the parser
		 * 
		 * @param name The name for the argument
		 * @param configure Configures the argument (optional), adding constraints and/or a default value
		 * @return This builder
		 */
		default ValuedArgumentSetBuilder addIntArgument(String name, Consumer<? super ArgumentBuilder<Integer, ?>> configure) {
			return addSimpleArgument(name, Integer.class, Integer::parseInt, configure);
		}

		/**
		 * Adds a {@link Long}-type argument type for the parser
		 * 
		 * @param name The name for the argument
		 * @param configure Configures the argument (optional), adding constraints and/or a default value
		 * @return This builder
		 */
		default ValuedArgumentSetBuilder addLongArgument(String name, Consumer<? super ArgumentBuilder<Long, ?>> configure) {
			return addSimpleArgument(name, Long.class, Long::parseLong, configure);
		}

		/**
		 * Adds a {@link Double}-type argument type for the parser
		 * 
		 * @param name The name for the argument
		 * @param configure Configures the argument (optional), adding constraints and/or a default value
		 * @return This builder
		 */
		default ValuedArgumentSetBuilder addDoubleArgument(String name, Consumer<? super ArgumentBuilder<Double, ?>> configure) {
			return addSimpleArgument(name, Double.class, Double::parseDouble, configure);
		}

		/**
		 * Adds a {@link Boolean}-type argument type for the parser
		 * 
		 * @param name The name for the argument
		 * @param configure Configures the argument (optional), adding constraints and/or a default value
		 * @return This builder
		 */
		default ValuedArgumentSetBuilder addBooleanArgument(String name, Consumer<? super ArgumentBuilder<Boolean, ?>> configure) {
			return addArgument(name, Boolean.class, (txt, __) -> {
				switch (txt.toLowerCase()) {
				case "t":
				case "true":
				case "y":
				case "yes":
					return true;
				case "f":
				case "false":
				case "n":
				case "no":
					return false;
				default:
					throw new ParseException("Unrecognized boolean: " + txt, 0);
				}
			}, configure);
		}

		/**
		 * Adds an enum-type argument type for the parser
		 * 
		 * @param <E> The enum type
		 * @param name The name for the argument
		 * @param enumType The enum type
		 * @param configure Configures the argument (optional), adding constraints and/or a default value
		 * @return This builder
		 */
		default <E extends Enum<?>> ValuedArgumentSetBuilder addEnumArgument(String name, Class<E> enumType,
			Consumer<? super ArgumentBuilder<E, ?>> configure) {
			return addSimpleArgument(name, enumType, s -> {
				for (E value : enumType.getEnumConstants()) {
					if (value.name().equals(s))
						return value;
				}
				// Enums may implement a "prettier" toString(). Allow the user to specify that instead.
				for (E value : enumType.getEnumConstants()) {
					if (value.toString().equals(s))
						return value;
				}
				throw new IllegalArgumentException("No such " + enumType.getName() + " value named \"" + s + "\"");
			}, configure);
		}

		/**
		 * Adds a pattern-matched string argument type for the parser
		 * 
		 * @param name The name for the argument
		 * @param pattern The pattern to match argument values with
		 * @param configure Configures the argument (optional), adding constraints and/or a default value
		 * @return This builder
		 */
		default ValuedArgumentSetBuilder addPatternArgument(String name, String pattern,
			Consumer<? super ArgumentBuilder<Matcher, ?>> configure) {
			return addPatternArgument(name, Pattern.compile(pattern), configure);
		}

		/**
		 * Adds a pattern-matched string argument type for the parser
		 * 
		 * @param name The name for the argument
		 * @param pattern The pattern to match argument values with
		 * @param configure Configures the argument (optional), adding constraints and/or a default value
		 * @return This builder
		 */
		default ValuedArgumentSetBuilder addPatternArgument(String name, Pattern pattern,
			Consumer<? super ArgumentBuilder<Matcher, ?>> configure) {
			return addArgument(name, Matcher.class, new PatternParser(pattern), configure);
		}

		/**
		 * Adds an {@link Instant}-type argument type for the parser
		 * 
		 * @param name The name for the argument
		 * @param configure Configures the argument (optional), adding constraints, a default value, and/or configuring a few other
		 *        Instant-specific options
		 * @return This builder
		 */
		ValuedArgumentSetBuilder addInstantArgument(String name, Consumer<? super InstantArgumentBuilder<?>> configure);

		/**
		 * Adds a {@link Double}-type argument type for the parser
		 * 
		 * @param name The name for the argument
		 * @param configure Configures the argument (optional), adding constraints and/or a default value
		 * @return This builder
		 */
		default ValuedArgumentSetBuilder addDurationArgument(String name, Consumer<? super ArgumentBuilder<Duration, ?>> configure) {
			return addArgument(name, Duration.class, (txt, __) -> {
				return TimeUtils.parseDuration(txt).asDuration();
			}, configure);
		}

		/**
		 * Adds a {@link File}-type argument type for the parser
		 * 
		 * @param name The name for the argument
		 * @param configure Configures the argument (optional), adding constraints, a default value, and/or configuring a few other
		 *        file-specific options
		 * @return This builder
		 */
		ValuedArgumentSetBuilder addFileArgument(String name, Consumer<? super FileArgumentBuilder<?>> configure);

		/**
		 * Adds a {@link BetterFile}-type argument type for the parser
		 * 
		 * @param name The name for the argument
		 * @param configure Configures the argument (optional), adding constraints, a default value, and/or configuring a few other
		 *        file-specific options
		 * @return This builder
		 */
		ValuedArgumentSetBuilder addBetterFileArgument(String name, Consumer<? super BetterFileArgumentBuilder<?>> configure);
	}

	/** Parses pattern-matched argument strings for {@link ValuedArgumentSetBuilder#addPatternArgument(String, Pattern, Consumer)} */
	public static class PatternParser implements ArgumentValueParser<Matcher> {
		private final Pattern thePattern;

		/** @param pattern The pattern to use to match command-line arguments */
		public PatternParser(Pattern pattern) {
			thePattern = pattern;
		}

		/** @return The pattern used to match command-line arguments */
		public Pattern getPattern() {
			return thePattern;
		}

		@Override
		public Matcher parse(String text, Arguments otherArgs) throws ParseException {
			Matcher m = thePattern.matcher(text);
			if (!m.matches())
				throw new ParseException("Argument does not match \"" + thePattern.pattern() + "\": " + text, 0);
			return m;
		}

		@Override
		public String toString() {
			return thePattern.pattern();
		}
	}

	/**
	 * Allows configuration for an argument type
	 * 
	 * @param <T> The type of the argument
	 * @param <B> The sub-type of this builder
	 */
	public interface ArgumentBuilder<T, B extends ArgumentBuilder<T, B>> {
		/** @return The argument type being configured */
		ArgumentType<T> getArgument();

		/**
		 * Adds a description to the argument type. This will be printed out with the parser's {@link ArgumentParser#printHelp() help}
		 * message.
		 * 
		 * @param descrip The description of the purpose of this argument
		 * @return This builder
		 */
		B withDescription(String descrip);

		/**
		 * <p>
		 * This call has slightly different effects when it is called on a {@link ArgumentPattern#getMaxValues() multi-valued} argument than
		 * when called on a flag or single-valued argument
		 * </p>
		 * <p>
		 * If this argument is parsed using a multi-value pattern, this call will require it to be specified at least once. If the argument
		 * is not specified in command-line arguments, parsing the argument set will throw an exception.
		 * </p>
		 * <p>
		 * If this argument is not parsed using a multi-value pattern, this call will require it to be specified exactly once. If the
		 * argument is not specified in command-line arguments or is given more than once, parsing the argument set will throw an exception.
		 * </p>
		 * 
		 * @return This builder
		 */
		default B required() {
			if (getArgument().getPattern().getMaxValues() > 1)
				return times(1, Integer.MAX_VALUE);
			else
				return times(1, 1);
		}

		/**
		 * <p>
		 * This call has different effects when it is called on a {@link ArgumentPattern#getMaxValues() multi-valued} argument than when
		 * called on a flag or single-valued argument
		 * </p>
		 * <p>
		 * If this argument is parsed using a multi-value pattern, this call has no effect, as multi-valued arguments cannot be implicitly
		 * prevented from having multiple values or being specified multiple times. Since arguments are not required to be specified by
		 * default, this operation has no effect.
		 * </p>
		 * <p>
		 * If this argument is not parsed using a multi-value pattern, this call will allow it to be specified at most once. If the argument
		 * is specified in command-line arguments more than once, parsing the argument set will throw an exception.
		 * </p>
		 * 
		 * @return This builder
		 */
		default B optional() {
			if (getArgument().getPattern().getMaxValues() <= 1)
				times(0, 1);
			return (B) this;
		}

		/** @return Allows this argument to be specified any number of times, or not at all */
		default B anyTimes() {
			return times(0, Integer.MAX_VALUE);
		}

		/**
		 * Adds limits on the number of times this argument may be specified, or the total number of values if it is
		 * {@link ArgumentPattern#getMaxValues() multi-valued}. If the argument is specified fewer times than the given minimum or more
		 * times than the given maximum, parsing the argument set will throw an exception.
		 * 
		 * @param minTimes The minimum number of times the argument must be specified or, for a {@link ArgumentPattern#getMaxValues()
		 *        multi-valued} argument, the minimum number of values that must be specified for it
		 * @param maxTimes The maximum number of times the argument may be specified or, for a {@link ArgumentPattern#getMaxValues()
		 *        multi-valued} argument, the maximum number of values that may be specified for it
		 * @return This builder
		 */
		B times(int minTimes, int maxTimes);

		/**
		 * @param value The default value for the argument if one is not specified in the command-line input
		 * @return This builder
		 */
		default B defaultValue(T value) {
			return defaultValue(LambdaUtils.constantSupplier(value, value::toString, null));
		}

		/**
		 * @param value Supplies a default value for the argument if one is not specified in the command-line input
		 * @return This builder
		 */
		B defaultValue(Supplier<? extends T> value);

		/**
		 * @param value The default value for the argument (specified in the same format as an argument value) if one is not specified in
		 *        the command-line input
		 * @return This builder
		 */
		B parseDefaultValue(String value);

		/**
		 * @param constraint Creates an unconditional constraint on this argument's value
		 * @return This builder
		 */
		B constrain(Function<ArgumentTestBuilder<T>, ArgumentTest<T>> constraint);

		/**
		 * @param <A> The type of the target argument
		 * @param argument The name of the target argument
		 * @param argType The type of the target argument
		 * @param constraint Creates a constraint on this argument's value if a condition against the given target argument (and potentially
		 *        others) is met
		 * @return This builder
		 */
		<A> B when(String argument, Class<A> argType, Function<ArgumentConditionBuilder<T, A>, ArgumentConstraint<T>> constraint);
	}

	/**
	 * Allows configuration for an Instant-typed argument type
	 * 
	 * @param <B> The sub-type of this builder
	 */
	public interface InstantArgumentBuilder<B extends InstantArgumentBuilder<B>> extends ArgumentBuilder<Instant, B> {
		/**
		 * @param timeZone The time zone to use while parsing times. The default is local time.
		 * @return This builder
		 */
		B withTimeZone(TimeZone timeZone);

		/**
		 * @param timeZone The ID of the time zone to use while parsing times. The default is local time. If this value is not understood,
		 *        GMT will be used.
		 * @return This builder
		 */
		default B withTimeZone(String timeZone) {
			return withTimeZone(TimeZone.getTimeZone(timeZone));
		}

		/**
		 * Specifies that times should be parsed using the GMT time zone
		 * 
		 * @return This builder
		 */
		default B gmt() {
			return withTimeZone(TimeZone.getTimeZone("GMT"));
		}

		/**
		 * @param reference Supplies a reference time to use when parsing values that do not specify a date. E.g. if "12:30" is parsed, the
		 *        day portion of the time must be provided. The default reference is the current time at which the values are parsed.
		 * @return This builder
		 */
		B withReference(Supplier<Instant> reference);

		/**
		 * @param referenceTime Supplies a reference time to use when parsing values that do not specify a date, formatted like an argument
		 *        value. E.g. if "12:30" is parsed, the day portion of the time must be provided. The default reference is the current time
		 *        at which the values are parsed.
		 * @return This builder
		 */
		B withReference(String referenceTime);
	}

	/**
	 * Allows configuration for a file-typed argument type
	 * 
	 * @param <F> The file type of the argument
	 * @param <B> The sub-type of this builder
	 */
	public interface AbstractFileArgumentBuilder<F, B extends AbstractFileArgumentBuilder<F, B>> extends ArgumentBuilder<F, B> {
		/**
		 * Specifies that file values parsed with this type must be either a directory or a file. By default, either is acceptable.
		 * 
		 * @param dir Whether to require values to be directories or files
		 * @return This builder
		 */
		B directory(boolean dir);

		/**
		 * @param mustExist Whether values parsed by this argument must exist on the file system
		 * @return This builder
		 */
		B mustExist(boolean mustExist);

		/**
		 * @param create Whether to create file values parsed by this argument on the file system if it does not already exist. This can
		 *        only be used in conjunction with the {@link #directory(boolean) directory} option.
		 * @return This builder
		 */
		B create(boolean create);

		/**
		 * @param path The root of the file path that will be used to parse values. If values are specified absolutely, they will be used
		 *        as-is.
		 * @return This builder
		 */
		B relativeTo(String path);

		/**
		 * @param path The root of the file path that will be used to parse values. If values are specified absolutely, they will be used
		 *        as-is.
		 * @return This builder
		 */
		B relativeTo(F path);

		/**
		 * @param fileArgName The name of a file-type argument previously defined on the parser that will be the root of the file path used
		 *        to parse values for this argument. If values are specified absolutely, they will be used as-is.
		 * @return This builder
		 */
		B relativeToFileArg(String fileArgName);
	}

	/**
	 * Allows configuration for a File-typed argument type
	 * 
	 * @param <B> The sub-type of this builder
	 */
	public interface FileArgumentBuilder<B extends FileArgumentBuilder<B>> extends AbstractFileArgumentBuilder<File, B> {
		/**
		 * Specifies that file values of this argument should be {@link File#getCanonicalFile() canonical}
		 * 
		 * @return This builder
		 */
		B canonical();
	}

	/**
	 * Allows configuration for a BetterFile-typed argument type
	 * 
	 * @param <B> The sub-type of this builder
	 */
	public interface BetterFileArgumentBuilder<B extends BetterFileArgumentBuilder<B>> extends AbstractFileArgumentBuilder<BetterFile, B> {
		/**
		 * @param dataSource The file data source that the argument values should be from
		 * @return This builder
		 */
		B fromSource(BetterFile.FileDataSource dataSource);
	}

	/**
	 * Builds a constraint on an argument's value
	 * 
	 * @param <T> The type of the argument
	 */
	public interface ArgumentTestBuilder<T> {
		/**
		 * @param test The arbitrary test on the argument value
		 * @return The argument test
		 */
		ArgumentTest<T> check(Predicate<? super T> test);

		/**
		 * @param value The value to test against
		 * @return An argument test that passes only for the given value
		 */
		ArgumentTest<T> eq(T value);

		/**
		 * @param value The value to test against
		 * @return An argument test that passes for all values except the given value
		 */
		ArgumentTest<T> neq(T value);

		/**
		 * @param compare The comparator to use to compare values. Required for non-equality comparisons on argument types that are not
		 *        {@link Comparable}.
		 * @return This builder
		 */
		ArgumentTestBuilder<T> compareWith(Comparator<? super T> compare);

		/**
		 * @param value The value to test against
		 * @return An argument test that passes for all values less than the given value
		 */
		ArgumentTest<T> lt(T value);

		/**
		 * @param value The value to test against
		 * @return An argument test that passes for all values less than or equal to the given value
		 */
		ArgumentTest<T> lte(T value);

		/**
		 * @param value The value to test against
		 * @return An argument test that passes for all values greater than the given value
		 */
		ArgumentTest<T> gt(T value);

		/**
		 * @param value The value to test against
		 * @return An argument test that passes for all values greater than or equal tothe given value
		 */
		ArgumentTest<T> gte(T value);

		/**
		 * @param low The low value to test against
		 * @param high The high value to test against
		 * @return An argument test that passes for all values greater than or equal to the given low and less than or equal to the given
		 *         high
		 */
		default ArgumentTest<T> between(T low, T high) {
			return gte(low).and(b -> b.lte(high));
		}

		/**
		 * @param values The values to test against
		 * @return An argument test that passes only for values {@link Object#equals(Object) equal} to one of the given values
		 */
		default ArgumentTest<T> oneOf(T... values) {
			return oneOf(Arrays.asList(values));
		}

		/**
		 * @param values The values to test against
		 * @return An argument test that passes only for values {@link Object#equals(Object) equal} to one of the given values
		 */
		ArgumentTest<T> oneOf(Collection<? extends T> values);

		/**
		 * @param value The value to test against, formatted the same as an argument
		 * @return An argument test that passes only for the given value
		 */
		ArgumentTest<T> parseEq(String value);

		/**
		 * @param value The value to test against, formatted the same as an argument
		 * @return An argument test that passes for all values except the given value
		 */
		ArgumentTest<T> parseNeq(String value);

		/**
		 * @param value The value to test against, formatted the same as an argument
		 * @return An argument test that passes for all values less than the given value
		 */
		ArgumentTest<T> parseLt(String value);

		/**
		 * @param value The value to test against, formatted the same as an argument
		 * @return An argument test that passes for all values less than or equal to the given value
		 */
		ArgumentTest<T> parseLte(String value);

		/**
		 * @param value The value to test against, formatted the same as an argument
		 * @return An argument test that passes for all values greater than the given value
		 */
		ArgumentTest<T> parseGt(String value);

		/**
		 * @param value The value to test against, formatted the same as an argument
		 * @return An argument test that passes for all values greater than or equal to the given value
		 */
		ArgumentTest<T> parseGte(String value);

		/**
		 * @param low The low value to test against, formatted the same as an argument
		 * @param high The high value to test against, formatted the same as an argument
		 * @return An argument test that passes for all values greater than or equal to the given low and less than or equal to the given
		 *         high
		 */
		ArgumentTest<T> parseBetween(String low, String high);

		/**
		 * @param values The values to test against, formatted the same as an argument
		 * @return An argument test that passes only for values {@link Object#equals(Object) equal} to one of the given values
		 */
		ArgumentTest<T> parseOneOf(String... values);

		/**
		 * @param <A> The type of the other argument
		 * @param otherArg The name of the other argument whose value(s) to test against
		 * @param argType The type of the argument
		 * @return The builder for the test against the values 2 arguments
		 */
		<A> BiArgumentTestBuilder<T, A> against(String otherArg, Class<A> argType);
	}

	/**
	 * @param <T> The type of the argument being tested
	 * @param <A> The type of the argument being tested against
	 */
	public interface BiArgumentTestBuilder<T, A> {
		/**
		 * Tests the value of the two arguments in a custom way. This test can only be used when the second argument may have at most a
		 * single value, and the test will <b>PASS</b> if the second argument is <b>NOT</b> specified.
		 * 
		 * @param test The arbitrary test against the 2 arguments
		 * @return The test
		 */
		ArgumentTest.BiArgumentTest<T, A> checkAgainst(BiPredicate<? super T, ? super A> test);

		/**
		 * Requires the value of the first argument to be the same as that of the second. This test can only be used when the second
		 * argument may have at most a single value, and the test will <b>PASS</b> if the second argument is <b>NOT</b> specified.
		 * 
		 * @return An argument test that passes when the value of the first argument is the same as that of the second, if it is specified
		 */
		ArgumentTest.BiArgumentCompareTest<T, A> eq();

		/**
		 * Requires the value of the first argument to be the different from that of the second. This test can only be used when the second
		 * argument may have at most a single value, and the test will <b>PASS</b> if the second argument is <b>NOT</b> specified.
		 * 
		 * @return An argument test that passes when the value of the first argument is not the same as that of the second, if it is
		 *         specified
		 */
		ArgumentTest.BiArgumentCompareTest<T, A> neq();

		/**
		 * @param compare The comparator to use to compare the 2 argument values. Required for non-equality comparisons on argument with
		 *        different types.
		 * @return This builder
		 */
		BiArgumentTestBuilder<T, A> compareWith(ToIntBiFunction<? super T, ? super A> compare);

		/**
		 * Requires the value of the first argument to be less than that of the second. This test can only be used when the second argument
		 * may have at most a single value, and the test will <b>PASS</b> if the second argument is <b>NOT</b> specified.
		 * 
		 * @return An argument test that passes when the value of the first argument is less than that of the second, if it is specified
		 */
		ArgumentTest.BiArgumentCompareTest<T, A> lt();

		/**
		 * Requires the value of the first argument to be less than or equal to that of the second. This test can only be used when the
		 * second argument may have at most a single value, and the test will <b>PASS</b> if the second argument is <b>NOT</b> specified.
		 * 
		 * @return An argument test that passes when the value of the first argument is less than or equal to that of the second, if it is
		 *         specified
		 */
		ArgumentTest.BiArgumentCompareTest<T, A> lte();

		/**
		 * Requires the value of the first argument to be greater than that of the second. This test can only be used when the second
		 * argument may have at most a single value, and the test will <b>PASS</b> if the second argument is <b>NOT</b> specified.
		 * 
		 * @return An argument test that passes when the value of the first argument is greater than that of the second, if it is specified
		 */
		ArgumentTest.BiArgumentCompareTest<T, A> gt();

		/**
		 * Requires the value of the first argument to be greater than or equal to that of the second. This test can only be used when the
		 * second argument may have at most a single value, and the test will <b>PASS</b> if the second argument is <b>NOT</b> specified.
		 * 
		 * @return An argument test that passes when the value of the first argument is greater than or equal to that of the second, if it
		 *         is specified
		 */
		ArgumentTest.BiArgumentCompareTest<T, A> gte();

		/**
		 * Requires the value of the first argument to be the same as one of the values of the second. The test will <b>FAIL</b> if the
		 * second argument is <b>NOT</b> specified.
		 * 
		 * @return An argument test that passes when the value of the first argument is the same as one of the values of the second argument
		 */
		ArgumentTest.BiArgumentOneOfTest<T, A> oneOf();
	}

	/**
	 * Builds a {@link ArgumentCondition condition} which, if met, may result in a constraint against a subject argument's value
	 * 
	 * @param <S> The type of the subject argument
	 * @param <T> The type of the target argument
	 */
	public interface ArgumentConditionBuilder<S, T> {
		/**
		 * @return An argument condition that is met if the target argument is specified at least once or, in the case of a
		 *         {@link ArgumentPattern#getMaxValues() multi-valued} argument, has at least one value. A
		 *         {@link ArgumentBuilder#defaultValue(Supplier) default} value also counts.
		 */
		default ArgumentCondition<S> specified() {
			return times(1, Integer.MAX_VALUE);
		}

		/**
		 * @return An argument condition that is met only if the target argument is not specified, in the case of a
		 *         {@link ArgumentPattern#getMaxValues() multi-valued} argument, has no specified values. A
		 *         {@link ArgumentBuilder#defaultValue(Supplier) default} value will also mean the condition is not met.
		 */
		default ArgumentCondition<S> missing() {
			return times(0, 0);
		}

		/**
		 * @param minTimes The minimum number of times the target argument must be specified for the built condition to be met
		 * @param maxTimes The maximum number of times the target argument may be specified for the built condition to be met
		 * @return An argument condition that is met if the target argument is specified a number of times between the two given values,
		 *         inclusively, or, in the case of a {@link ArgumentPattern#getMaxValues() multi-valued} argument, has a number of arguments
		 *         between the two given values. A {@link ArgumentBuilder#defaultValue(Supplier) default} value also counts.
		 */
		ArgumentCondition<S> times(int minTimes, int maxTimes);

		/**
		 * @param test Makes a value test against the target argument
		 * @return A condition that is met when any value of the target argument matches the test
		 */
		ArgumentCondition<S> matches(Function<ArgumentTestBuilder<T>, ArgumentTest<T>> test);
	}

	static class Impl {
		static <T> Class<T> wrap(Class<T> primitive) {
			if (primitive == boolean.class)
				return (Class<T>) Boolean.class;
			else if (primitive == char.class)
				return (Class<T>) Character.class;
			else if (primitive == byte.class)
				return (Class<T>) Byte.class;
			else if (primitive == short.class)
				return (Class<T>) Short.class;
			else if (primitive == int.class)
				return (Class<T>) Integer.class;
			else if (primitive == long.class)
				return (Class<T>) Long.class;
			else if (primitive == float.class)
				return (Class<T>) Float.class;
			else if (primitive == double.class)
				return (Class<T>) Double.class;
			else
				return primitive;
		}

		static <T> ArgumentTest.ValueTest<T> testFor(ParserBuilder builder, ArgumentType<T> subject, T value, int comp, boolean eq,
			Comparator<? super T> compare,
			ArgumentValueParser<? extends T> parser) {
			ArgumentTest.ComparisonType type;
			if (comp == 0)
				type = eq ? ArgumentTest.ComparisonType.EQ : ArgumentTest.ComparisonType.NEQ;
			else if (comp < 0)
				type = eq ? ArgumentTest.ComparisonType.LTE : ArgumentTest.ComparisonType.LT;
			else
				type = eq ? ArgumentTest.ComparisonType.GTE : ArgumentTest.ComparisonType.GT;
			return new ArgumentTest.ValueTest<>(builder, subject, value, type, compare, parser);
		}

		static class DefaultArgConstraint<T> implements ArgumentConstraint<T> {
			private final ArgumentCondition<T> theCondition;
			private final int theMinTimes;
			private final int theMaxTimes;
			private final ArgumentTest<T> theValueTest;

			public DefaultArgConstraint(ArgumentCondition<T> condition, int minTimes, int maxTimes, ArgumentTest<T> valueTest) {
				if (condition == null)
					throw new NullPointerException("Condition is null");
				theCondition = condition;
				if (minTimes < 0)
					throw new IllegalArgumentException("Cannot have a minimum count <0: " + minTimes);
				else if (maxTimes < 0)
					throw new IllegalArgumentException("Cannot have a maximum count <0: " + maxTimes);
				else if (minTimes > maxTimes)
					throw new IllegalArgumentException("Cannot have a minimum count>maximum count: " + minTimes + "..." + maxTimes);
				theMinTimes = minTimes;
				theMaxTimes = maxTimes;
				theValueTest = valueTest;
			}

			@Override
			public ArgumentCondition<T> getCondition() {
				return theCondition;
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
			public ArgumentTest<T> getValueTest() {
				return theValueTest;
			}

			public void print(StringBuilder str, boolean defaulted) {
				if (!(theCondition instanceof ArgumentCondition.TrueCondition))
					str.append("if(").append(theCondition).append("): ");
				if (theValueTest != null)
					str.append(theValueTest.toString(""));
				else if (theMinTimes == 0) {
					if (theMaxTimes == 0)
						str.append("forbidden");
					else if (theMaxTimes == 1) {
						if (!defaulted)
							str.append("optional");
					} else if (theMaxTimes == Integer.MAX_VALUE)
						str.append("0...\u221E times");
					else
						str.append("0...").append(theMaxTimes).append(" times");
				} else if (theMinTimes == 1) {
					if (theMaxTimes == 1) {
						if (!defaulted)
							str.append("required");
					} else if (theMaxTimes == Integer.MAX_VALUE)
						str.append("required at least once");
					else
						str.append("1...").append(theMaxTimes).append(" times");
				} else {
					if (theMaxTimes == theMinTimes)
						str.append("exactly ").append(theMinTimes).append(" times");
					else {
						str.append(theMinTimes).append("...");
						if (theMaxTimes == Integer.MAX_VALUE)
							str.append('\u221E');
						else
							str.append(theMaxTimes);
						str.append(" times");
					}
				}
			}

			@Override
			public String toString() {
				StringBuilder str = new StringBuilder();
				print(str, false);
				return str.toString();
			}
		}

		static class DefaultArgTestBuilder<T> implements ArgumentTestBuilder<T> {
			private final ParserBuilder theBuilder;
			private final ArgumentType<T> theSubject;
			private final ArgumentValueParser<? extends T> theParser;
			private Comparator<? super T> theCompare;

			DefaultArgTestBuilder(ParserBuilder builder, ArgumentType<T> subject, ArgumentValueParser<? extends T> parser) {
				theBuilder = builder;
				theSubject = subject;
				if (parser == null)
					throw new IllegalArgumentException("Cannot perform value checks against a flag argument");
				theParser = parser;
			}

			@Override
			public ArgumentTest<T> check(Predicate<? super T> test) {
				return new ArgumentTest<T>(theBuilder, theSubject, theParser) {
					@Override
					public boolean test(T value, Arguments args) {
						return test.test(value);
					}

					@Override
					public String toString(String argumentName) {
						return test.toString() + "(" + argumentName + ")";
					}

					@Override
					public String toString() {
						return test.toString();
					}
				};
			}

			@Override
			public ArgumentTest<T> eq(T value) {
				return testFor(theBuilder, theSubject, value, 0, true, theCompare, theParser);
			}

			@Override
			public ArgumentTest<T> neq(T value) {
				return testFor(theBuilder, theSubject, value, 0, false, theCompare, theParser);
			}

			@Override
			public ArgumentTestBuilder<T> compareWith(Comparator<? super T> compare) {
				theCompare = compare;
				return this;
			}

			@SuppressWarnings("rawtypes")
			private void requireCompare() {
				if (theCompare == null)
					return;
				else if (Comparable.class.isAssignableFrom(theSubject.getType()))
					theCompare = (Comparator<? super T>) (Comparator<Comparable>) Comparable::compareTo;
				else
					throw new IllegalStateException("Cannot create a comparison test against argument " + theSubject.getName() + " ("
						+ theSubject.getType().getName() + ") without specifying a comparator.  Use compareWith(Comparator<? super "
						+ theSubject.getType().getName() + ">)");
			}

			@Override
			public ArgumentTest<T> lt(T value) {
				requireCompare();
				return testFor(theBuilder, theSubject, value, -1, false, theCompare, theParser);
			}

			@Override
			public ArgumentTest<T> lte(T value) {
				return testFor(theBuilder, theSubject, value, -1, true, theCompare, theParser);
			}

			@Override
			public ArgumentTest<T> gt(T value) {
				return testFor(theBuilder, theSubject, value, 1, false, theCompare, theParser);
			}

			@Override
			public ArgumentTest<T> gte(T value) {
				return testFor(theBuilder, theSubject, value, 1, true, theCompare, theParser);
			}

			@Override
			public ArgumentTest<T> oneOf(Collection<? extends T> values) {
				return new ArgumentTest.OneOfValueTest<>(theBuilder, theSubject, values, theParser);
			}

			@Override
			public ArgumentTest<T> parseEq(String value) {
				T parsed;
				try {
					parsed = theParser.parse(value, EMPTY_ARGS);
				} catch (ParseException e) {
					throw new IllegalArgumentException(e);
				}
				return eq(parsed);
			}

			@Override
			public ArgumentTest<T> parseNeq(String value) {
				T parsed;
				try {
					parsed = theParser.parse(value, EMPTY_ARGS);
				} catch (ParseException e) {
					throw new IllegalArgumentException(e);
				}
				return neq(parsed);
			}

			@Override
			public ArgumentTest<T> parseLt(String value) {
				T parsed;
				try {
					parsed = theParser.parse(value, EMPTY_ARGS);
				} catch (ParseException e) {
					throw new IllegalArgumentException(e);
				}
				return lt(parsed);
			}

			@Override
			public ArgumentTest<T> parseLte(String value) {
				T parsed;
				try {
					parsed = theParser.parse(value, EMPTY_ARGS);
				} catch (ParseException e) {
					throw new IllegalArgumentException(e);
				}
				return lte(parsed);
			}

			@Override
			public ArgumentTest<T> parseGt(String value) {
				T parsed;
				try {
					parsed = theParser.parse(value, EMPTY_ARGS);
				} catch (ParseException e) {
					throw new IllegalArgumentException(e);
				}
				return gt(parsed);
			}

			@Override
			public ArgumentTest<T> parseGte(String value) {
				T parsed;
				try {
					parsed = theParser.parse(value, EMPTY_ARGS);
				} catch (ParseException e) {
					throw new IllegalArgumentException(e);
				}
				return gte(parsed);
			}

			@Override
			public ArgumentTest<T> parseBetween(String low, String high) {
				T parsedLow, parsedHigh;
				try {
					parsedLow = theParser.parse(low, EMPTY_ARGS);
					parsedHigh = theParser.parse(high, EMPTY_ARGS);
				} catch (ParseException e) {
					throw new IllegalArgumentException(e);
				}
				return between(parsedLow, parsedHigh);
			}

			@Override
			public ArgumentTest<T> parseOneOf(String... values) {
				List<T> parsed = new ArrayList<>(values.length);
				try {
					for (int i = 0; i < values.length; i++)
						parsed.add(theParser.parse(values[i], EMPTY_ARGS));
				} catch (ParseException e) {
					throw new IllegalArgumentException(e);
				}
				return oneOf(parsed);
			}

			@Override
			public <A> BiArgumentTestBuilder<T, A> against(String otherArg, Class<A> argType) {
				ArgumentType<A> otherArgType = (ArgumentType<A>) theBuilder.getArgument(otherArg);
				if (otherArgType == null)
					throw new IllegalArgumentException("No such argument: " + otherArg);
				else if (argType != null && !argType.equals(otherArgType.getType()))
					throw new ClassCastException(
						"Wrong type (" + argType.getName() + ") for argument " + otherArg + " (" + otherArgType.getType().getName() + ")");
				return new DefaultBiArgTestBuilder<>(theBuilder, theSubject, theParser, otherArgType);
			}
		}

		static class DefaultBiArgTestBuilder<T, A> implements BiArgumentTestBuilder<T, A> {
			private final ParserBuilder theBuilder;
			private final ArgumentType<T> theSubject;
			private final ArgumentValueParser<? extends T> theParser;
			private final ArgumentType<A> theTarget;
			private ToIntBiFunction<? super T, ? super A> theCompare;

			public DefaultBiArgTestBuilder(ParserBuilder builder, ArgumentType<T> subject, ArgumentValueParser<? extends T> parser,
				ArgumentType<A> secondArg) {
				theBuilder = builder;
				theSubject = subject;
				theParser = parser;
				theTarget = secondArg;
			}

			private void requireSingle() {
				boolean single = false;
				for (ArgumentConstraint<A> constraint : theTarget.getConstraints()) {
					if (constraint instanceof DefaultArgConstraint
						&& ((DefaultArgConstraint<A>) constraint).getCondition() instanceof ArgumentCondition.TrueCondition
						&& ((DefaultArgConstraint<A>) constraint).getMaxTimes() == 1) {
						single = true;
						break;
					}
				}
				if (!single)
					throw new IllegalArgumentException(
						"This check cannot be used against an argument that can be specified multiple times: " + theTarget.getName());
			}

			private void requireCompare() {
				if (theCompare == null)
					return;
				else if (theSubject.getType().isAssignableFrom(theTarget.getType())
					&& Comparable.class.isAssignableFrom(theSubject.getType()))
					theCompare = (t, a) -> ((Comparable<? super A>) t).compareTo(a);
				else if (theTarget.getType().isAssignableFrom(theSubject.getType())
					&& Comparable.class.isAssignableFrom(theTarget.getType()))
					theCompare = (t, a) -> ((Comparable<? super T>) a).compareTo(t);
				else
					throw new IllegalStateException("Cannot create a comparison test against arguments "//
						+ theSubject.getName() + " (" + theSubject.getType().getName() + ") and "//
						+ theTarget.getName() + " (" + theTarget.getType().getName() + ") without specifying a comparator."
						+ "  Use compareWith(ToIntBiFunction<? super " + theSubject.getType().getName() + ", ? super "
						+ theTarget.getType().getName() + ">)");
			}

			@Override
			public BiArgumentTest<T, A> checkAgainst(BiPredicate<? super T, ? super A> test) {
				requireSingle();
				return new ArgumentTest.BiArgumentTest<T, A>(theBuilder, theSubject, theParser, theTarget) {
					@Override
					public boolean test(T value, Arguments args) {
						Argument<A> otherArgInstance = args.getArgument(theTarget);
						if (otherArgInstance == null || otherArgInstance.getValues().isEmpty())
							return true;
						return test.test(value, otherArgInstance.getValues().getFirst());
					}

					@Override
					public String toString(String argumentName) {
						return test.toString() + "(" + argumentName + ")";
					}

					@Override
					public String toString() {
						return test.toString();
					}
				};
			}

			@SuppressWarnings("unlikely-arg-type")
			@Override
			public ArgumentTest.BiArgumentCompareTest<T, A> eq() {
				requireSingle();
				ToIntBiFunction<? super T, ? super A> compare = theCompare;
				if (compare == null)
					compare = (t, a) -> Objects.equals(t, a) ? 0 : 1;
				return new ArgumentTest.BiArgumentCompareTest<>(theBuilder, theSubject, theParser, theTarget,
					ArgumentTest.ComparisonType.EQ, compare);
			}

			@SuppressWarnings("unlikely-arg-type")
			@Override
			public ArgumentTest.BiArgumentCompareTest<T, A> neq() {
				requireSingle();
				ToIntBiFunction<? super T, ? super A> compare = theCompare;
				if (compare == null)
					compare = (t, a) -> Objects.equals(t, a) ? 0 : 1;
				return new ArgumentTest.BiArgumentCompareTest<>(theBuilder, theSubject, theParser, theTarget,
					ArgumentTest.ComparisonType.NEQ, compare);
			}

			@Override
			public BiArgumentTestBuilder<T, A> compareWith(ToIntBiFunction<? super T, ? super A> compare) {
				theCompare = compare;
				return this;
			}

			@Override
			public ArgumentTest.BiArgumentCompareTest<T, A> lt() {
				requireSingle();
				requireCompare();
				return new ArgumentTest.BiArgumentCompareTest<>(theBuilder, theSubject, theParser, theTarget,
					ArgumentTest.ComparisonType.LT, theCompare);
			}

			@Override
			public ArgumentTest.BiArgumentCompareTest<T, A> lte() {
				requireSingle();
				requireCompare();
				return new ArgumentTest.BiArgumentCompareTest<>(theBuilder, theSubject, theParser, theTarget,
					ArgumentTest.ComparisonType.LTE, theCompare);
			}

			@Override
			public ArgumentTest.BiArgumentCompareTest<T, A> gt() {
				requireSingle();
				requireCompare();
				return new ArgumentTest.BiArgumentCompareTest<>(theBuilder, theSubject, theParser, theTarget,
					ArgumentTest.ComparisonType.GT, theCompare);
			}

			@Override
			public ArgumentTest.BiArgumentCompareTest<T, A> gte() {
				requireSingle();
				requireCompare();
				return new ArgumentTest.BiArgumentCompareTest<>(theBuilder, theSubject, theParser, theTarget,
					ArgumentTest.ComparisonType.GTE, theCompare);
			}

			@SuppressWarnings("unlikely-arg-type")
			@Override
			public ArgumentTest.BiArgumentOneOfTest<T, A> oneOf() {
				ToIntBiFunction<? super T, ? super A> compare = theCompare;
				if (compare == null)
					compare = (t, a) -> Objects.equals(t, a) ? 0 : 1;
				return new ArgumentTest.BiArgumentOneOfTest<>(theBuilder, theSubject, theParser, theTarget, compare);
			}
		}

		static class DefaultArgConditionBuilder<S, T> implements ArgumentConditionBuilder<S, T> {
			private final ParserBuilder theBuilder;
			private final ArgumentType<S> theSubject;
			private final ArgumentType<T> theTarget;
			private final Function<String, ArgumentTypeHolder<?>> theArgGetter;

			DefaultArgConditionBuilder(ParserBuilder builder, ArgumentType<S> subject, ArgumentType<T> target,
				Function<String, Impl.ArgumentTypeHolder<?>> argGetter) {
				theBuilder = builder;
				theSubject = subject;
				theTarget = target;
				theArgGetter = argGetter;
			}

			@Override
			public ArgumentCondition<S> times(int minTimes, int maxTimes) {
				return new ArgumentCondition.ArgumentSpecifiedCondition<>(theBuilder, theSubject, theTarget, minTimes, maxTimes,
					theArgGetter);
			}

			@Override
			public ArgumentCondition<S> matches(Function<ArgumentTestBuilder<T>, ArgumentTest<T>> test) {
				ArgumentTypeHolder<T> arg = (ArgumentTypeHolder<T>) theArgGetter.apply(theTarget.getName());
				ArgumentTest<T> newTest = test.apply(new DefaultArgTestBuilder<>(theBuilder, theTarget, arg.parser));
				return new ArgumentCondition.ArgumentValueCondition<>(theBuilder, theSubject, theTarget, newTest, theArgGetter);
			}
		}

		static class DefaultParserBuilder implements ParserBuilder {
			private final Map<String, ArgumentTypeHolder<?>> theArguments;
			private boolean isAcceptingUnmatched;
			private String theDescription;
			private DefaultArgParser theBuiltParser;
			private DefaultArgBuilder<?, ?> theBuildingArgument;

			DefaultParserBuilder() {
				theArguments = new LinkedHashMap<>();
			}

			Transaction building(DefaultArgBuilder<?, ?> arg) {
				theBuildingArgument = arg;
				return () -> theBuildingArgument = null;
			}

			@Override
			public ArgumentParser build() {
				if (theBuiltParser == null)
					theBuiltParser = new DefaultArgParser(theArguments, isAcceptingUnmatched, theDescription);
				return theBuiltParser;
			}

			@Override
			public ParserBuilder forFlagPattern(ArgumentPattern pattern, Consumer<FlagArgumentSetBuilder> args) {
				args.accept(new DefaultFlagArgSetBuilder(pattern, this));
				return this;
			}

			@Override
			public ParserBuilder forValuePattern(ArgumentPattern pattern, Consumer<ValuedArgumentSetBuilder> args) {
				args.accept(new DefaultValueArgSetBuilder(pattern, this));
				return this;
			}

			@Override
			public ArgumentType<?> getArgument(String argName) {
				ArgumentTypeHolder<?> arg = theArguments.get(argName);
				return arg == null ? null : arg.argument;
			}

			ArgumentTypeHolder<?> getHolder(String name) {
				ArgumentTypeHolder<?> arg = theArguments.get(name);
				if (arg == null) {
					if (theBuildingArgument != null)
						throw new IllegalArgumentException("No such argument \"" + name + "\" configured. Declare \"" + name
							+ "\" before \"" + theBuildingArgument.getArgument().getName() + "\".");
					else
						throw new IllegalArgumentException("No such argument \"" + name + "\" configured");
				}
				return arg;
			}

			void addArg(ArgumentTypeHolder<?> holder) {
				if (theBuiltParser != null)
					throw new IllegalStateException("This builder has already built its parser");
				if (theArguments.containsKey(holder.argument.getName()))
					throw new IllegalArgumentException("An argument named \"" + holder.argument.getName() + "\" already exists");
				theArguments.put(holder.argument.getName(), holder);
			}

			@Override
			public ParserBuilder acceptUnmatched(boolean accept) {
				if (theBuiltParser != null)
					throw new IllegalStateException("This builder has already built its parser");
				isAcceptingUnmatched = accept;
				return this;
			}

			@Override
			public boolean isAcceptingUnmatched() {
				return isAcceptingUnmatched;
			}

			@Override
			public ParserBuilder copy(ArgumentParser parser) {
				if (parser instanceof DefaultArgParser) {
					for (ArgumentType<?> a : parser.getArguments()) {
						ArgumentTypeHolder<?> arg = ((DefaultArgParser) parser).theArguments.get(((DefaultArgType<?>) a).index);
						theArguments.computeIfAbsent(arg.argument.getName(), __ -> arg.copy());
					}
				} else
					throw new IllegalArgumentException(
						"Unrecognized parser implementation " + parser.getClass().getName() + "--cannot copy");
				return this;
			}

			@Override
			public ParserBuilder withDescription(String descrip) {
				theDescription = descrip;
				return this;
			}

			@Override
			public String getDescription() {
				return theDescription;
			}
		}

		static class ArgumentTypeHolder<T> {
			final DefaultArgType<T> argument;
			final ArgumentValueParser<? extends T> parser;
			final ExFunction<Arguments, ? extends T, ParseException> defaultValue;

			ArgumentTypeHolder(DefaultArgType<T> argument, ArgumentValueParser<? extends T> parser,
				ExFunction<Arguments, ? extends T, ParseException> defaultValue) {
				this.argument = argument;
				this.parser = parser;
				this.defaultValue = defaultValue;
			}

			ArgumentTypeHolder<T> copy() {
				return new ArgumentTypeHolder<>(argument.copy(), parser, defaultValue);
			}

			void printHelp(StringBuilder str) {
				MatchedArgument ma;
				switch (argument.getPattern().getMaxValues()) {
				case 0:
					ma = new MatchedArgument(argument.getName(), Collections.emptyList());
					break;
				case 1:
					ma = new MatchedArgument(argument.getName(), Arrays.asList("?"));
					break;
				default:
					ma = new MatchedArgument(argument.getName(), Arrays.asList("?", "?", "?"));
					break;
				}
				if (str.length() > 0)
					str.append('\n');
				str.append('\t').append(argument.getPattern().print(ma));
				boolean printedParen = false;
				if (!ma.getValues().isEmpty()) {
					if (!printedParen) {
						printedParen = true;
						str.append(" (");
					} else
						str.append(", ");
					Class<T> type = argument.getType();
					if (type == String.class)
						str.append("string");
					else if (type == int.class || type == long.class || type == Integer.class || type == Long.class)
						str.append("integer");
					else if (type == double.class || type == Double.class)
						str.append("number");
					else if (type == boolean.class || type == Boolean.class)
						str.append("boolean");
					else if (type == Matcher.class)
						str.append("string matching ").append(parser);
					else if (type == Instant.class)
						str.append("date");
					else if (type == Duration.class)
						str.append("duration");
					else if (type == File.class || type == BetterFile.class)
						str.append(parser);
					else if (type.isEnum() && type.getEnumConstants().length <= 10) {
						str.append("one of {");
						boolean first = true;
						for (T value : type.getEnumConstants()) {
							if (first)
								first = false;
							else
								str.append(", ");
							str.append(value);
						}
						str.append('}');
					} else
						str.append(type.getName());
					if (defaultValue instanceof SimpleDefaultValue)
						str.append(", default ").append(defaultValue);
					for (ArgumentConstraint<T> constraint : argument.getConstraints()) {
						if (constraint.getCondition() instanceof ArgumentCondition.TrueCondition) {
							str.append(", ");
							((DefaultArgConstraint<T>) constraint).print(str, defaultValue != null);
						}
					}
				}
				if (printedParen)
					str.append(')');
				if (argument.getDescription() != null)
					str.append("\n\t\t").append(argument.getDescription());
				for (ArgumentConstraint<T> constraint : argument.getConstraints()) {
					if (!(constraint.getCondition() instanceof ArgumentCondition.TrueCondition))
						str.append("\n\t\t").append(constraint);
				}
			}

			@Override
			public String toString() {
				return argument.toString();
			}
		}

		static class DefaultFlagArgSetBuilder implements FlagArgumentSetBuilder {
			private final ArgumentPattern thePattern;
			private final DefaultParserBuilder theParser;

			DefaultFlagArgSetBuilder(ArgumentPattern pattern, DefaultParserBuilder parser) {
				if (pattern.getMaxValues() != 0)
					throw new IllegalArgumentException("Cannot call forFlagPattern() with a non-flag argument pattern: " + pattern);
				thePattern = pattern;
				theParser = parser;
			}

			@Override
			public FlagArgumentSetBuilder add(String name, Consumer<ArgumentBuilder<Void, ?>> configure) {
				if (name == null)
					throw new NullPointerException("Name must not be null");
				thePattern.validate(name);
				DefaultArgBuilder<Void, ?> builder = new DefaultArgBuilder<>(name, thePattern, void.class, null, theParser);
				builder.times(0, 1);
				if (configure != null) {
					try (Transaction t = theParser.building(builder)) {
						configure.accept(builder);
					}
				}
				theParser.addArg(builder.build());
				return this;
			}
		}

		static class DefaultValueArgSetBuilder implements ValuedArgumentSetBuilder {
			private final ArgumentPattern thePattern;
			private final DefaultParserBuilder theParser;

			DefaultValueArgSetBuilder(ArgumentPattern pattern, DefaultParserBuilder parser) {
				thePattern = pattern;
				theParser = parser;
			}

			@Override
			public <T> ValuedArgumentSetBuilder addArgument(String name, Class<T> type, ArgumentValueParser<? extends T> parser,
				Consumer<? super ArgumentBuilder<T, ?>> configure) {
				DefaultArgBuilder<T, ?> builder = new DefaultArgBuilder<>(name, thePattern, type, parser, theParser);
				return add3(builder, configure);
			}

			<T, B extends DefaultArgBuilder<T, ?>> ValuedArgumentSetBuilder add3(B builder, Consumer<? super B> configure) {
				if (builder.getArgument().getName() == null)
					throw new NullPointerException("Name must not be null");
				if (builder.getArgument().getType() == null || builder.getArgument().getType() == void.class
					|| builder.getArgument().getType() == Void.class)
					throw new NullPointerException("Type must not be null or void");
				if (builder.getValueParser() == null)
					throw new NullPointerException("No parser specified");
				thePattern.validate(builder.getArgument().getName());
				if (configure != null) {
					try (Transaction t = theParser.building(builder)) {
						configure.accept(builder);
					}
				}
				theParser.addArg(builder.build());
				return this;
			}

			@Override
			public ValuedArgumentSetBuilder addInstantArgument(String name, Consumer<? super InstantArgumentBuilder<?>> configure) {
				return add3(new DefaultInstantArgBuilder(name, thePattern, theParser), configure);
			}

			@Override
			public ValuedArgumentSetBuilder addFileArgument(String name, Consumer<? super FileArgumentBuilder<?>> configure) {
				return add3(new DefaultFileArgBuilder(name, thePattern, theParser), configure);
			}

			@Override
			public ValuedArgumentSetBuilder addBetterFileArgument(String name, Consumer<? super BetterFileArgumentBuilder<?>> configure) {
				return add3(new DefaultBetterFileArgBuilder(name, thePattern, theParser), configure);
			}
		}

		static class DefaultArgBuilder<T, B extends DefaultArgBuilder<T, B>> implements ArgumentBuilder<T, B> {
			private final DefaultParserBuilder theArgParser;
			private final DefaultArgType<T> theArgument;
			private final ArrayList<ArgumentConstraint<T>> theConstraints;
			private final ArgumentValueParser<? extends T> theValueParser;
			private ExFunction<Arguments, ? extends T, ParseException> theDefault;
			private boolean isBuilt;

			DefaultArgBuilder(String name, ArgumentPattern pattern, Class<T> type, ArgumentValueParser<? extends T> parser,
				DefaultParserBuilder argParser) {
				theArgParser = argParser;
				theConstraints = new ArrayList<>(3);
				theArgument = new DefaultArgType<>(name, pattern, type, theConstraints, null);
				theValueParser = parser;
			}

			void assertNotBuilt() {
				if (isBuilt)
					throw new IllegalStateException("This argument has already been built");
			}

			DefaultParserBuilder getArgParser() {
				return theArgParser;
			}

			@Override
			public DefaultArgType<T> getArgument() {
				return theArgument;
			}

			B addConstraint(ArgumentConstraint<T> constraint) {
				assertNotBuilt();
				theConstraints.add(constraint);
				return (B) this;
			}

			@Override
			public B withDescription(String descrip) {
				assertNotBuilt();
				theArgument.theDescription = descrip;
				return (B) this;
			}

			ArgumentValueParser<? extends T> getValueParser() {
				return theValueParser;
			}

			@Override
			public B times(int minTimes, int maxTimes) {
				if (maxTimes > 1 && theValueParser == null)
					throw new IllegalArgumentException("Flag arguments cannot have multiplicity");
				return addConstraint(new DefaultArgConstraint<>(
					new ArgumentCondition.TrueCondition<>(theArgParser, theArgument, theArgParser::getHolder), minTimes, maxTimes, null));
			}

			@Override
			public B defaultValue(Supplier<? extends T> value) {
				if (theValueParser == null)
					throw new IllegalArgumentException("Flag arguments cannot have a default value");
				assertNotBuilt();
				theDefault = new SimpleDefaultValue<>(value);
				return (B) this;
			}

			@Override
			public B parseDefaultValue(String value) {
				if (theValueParser == null)
					throw new IllegalArgumentException("Flag arguments cannot have a default value");
				theDefault = args -> theValueParser.parse(value, args);
				T parsed;
				try {
					parsed = theValueParser.parse(value, EMPTY_ARGS);
				} catch (ParseException e) {
					throw new IllegalArgumentException(e);
				}
				return defaultValue(parsed);
			}

			@Override
			public B constrain(Function<ArgumentTestBuilder<T>, ArgumentTest<T>> constraint) {
				ArgumentTest<T> newConstraint = constraint.apply(new DefaultArgTestBuilder<>(theArgParser, theArgument, theValueParser));
				return addConstraint(
					new DefaultArgConstraint<>(
						new ArgumentCondition.TrueCondition<>(theArgParser, getArgument(), getArgParser()::getHolder), 0, Integer.MAX_VALUE,
						newConstraint));
			}

			@Override
			public <A> B when(String argument, Class<A> argType,
				Function<ArgumentConditionBuilder<T, A>, ArgumentConstraint<T>> constraint) {
				ArgumentTypeHolder<?> target = getArgParser().getHolder(argument);
				if (argType != null && !argType.equals(target.argument.getType()))
					throw new ClassCastException("Wrong type (" + argType.getName() + ") for argument " + argument + " ("
						+ target.argument.getType().getName() + ")");
				ArgumentConstraint<T> newConstraint = constraint.apply(new DefaultArgConditionBuilder<>(theArgParser, getArgument(),
					(ArgumentType<A>) target.argument, getArgParser()::getHolder));
				return addConstraint(newConstraint);
			}

			ArgumentTypeHolder<T> build() {
				assertNotBuilt();
				isBuilt = true;
				return new ArgumentTypeHolder<>(getArgument(), theValueParser, theDefault);
			}
		}

		static class SimpleDefaultValue<T> implements ExFunction<Arguments, T, ParseException> {
			private final Supplier<? extends T> theDefaultValue;

			SimpleDefaultValue(Supplier<? extends T> defaultValue) {
				theDefaultValue = defaultValue;
			}

			public Supplier<? extends T> getSupplier() {
				return theDefaultValue;
			}

			@Override
			public T apply(Arguments args) {
				return theDefaultValue.get();
			}

			@Override
			public int hashCode() {
				return theDefaultValue == null ? 0 : theDefaultValue.hashCode();
			}

			@Override
			public boolean equals(Object obj) {
				return obj instanceof SimpleDefaultValue && theDefaultValue.equals(((SimpleDefaultValue<?>) obj).theDefaultValue);
			}

			@Override
			public String toString() {
				return theDefaultValue.toString();
			}
		}

		static class DefaultInstantArgBuilder extends DefaultArgBuilder<Instant, DefaultInstantArgBuilder>
			implements InstantArgumentBuilder<DefaultInstantArgBuilder> {

			DefaultInstantArgBuilder(String name, ArgumentPattern pattern, DefaultParserBuilder argParser) {
				super(name, pattern, Instant.class, new Parser(), argParser);
			}

			@Override
			public DefaultInstantArgBuilder withTimeZone(TimeZone timeZone) {
				assertNotBuilt();
				((Parser) getValueParser()).theTimeZone = timeZone;
				return this;
			}

			@Override
			public DefaultInstantArgBuilder withReference(Supplier<Instant> reference) {
				assertNotBuilt();
				((Parser) getValueParser()).theReference = reference;
				return this;
			}

			@Override
			public DefaultInstantArgBuilder withReference(String referenceTime) {
				assertNotBuilt();
				Instant ref;
				try {
					ref = getValueParser().parse(referenceTime, EMPTY_ARGS);
				} catch (ParseException e) {
					throw new IllegalArgumentException(e);
				}
				return withReference(() -> ref);
			}

			static class Parser implements ArgumentValueParser<Instant> {
				private TimeZone theTimeZone = TimeZone.getDefault();
				private Supplier<Instant> theReference = Instant::now;

				@Override
				public Instant parse(String text, Arguments otherArgs) throws ParseException {
					return TimeUtils.parseInstant(text, true, true, opts -> opts.withTimeZone(theTimeZone)).evaluate(theReference);
				}
			}
		}

		static abstract class DefaultAbstractFileArgBuilder<F, B extends DefaultAbstractFileArgBuilder<F, B>>
			extends DefaultArgBuilder<F, B> implements AbstractFileArgumentBuilder<F, B> {
			DefaultAbstractFileArgBuilder(String name, ArgumentPattern pattern, Class<F> type, FileParser<F> parser,
				DefaultParserBuilder argParser) {
				super(name, pattern, type, parser, argParser);
			}

			@Override
			FileParser<F> getValueParser() {
				return (FileParser<F>) super.getValueParser();
			}

			@Override
			public B directory(boolean dir) {
				assertNotBuilt();
				getValueParser().directory = dir;
				return (B) this;
			}

			@Override
			public B mustExist(boolean mustExist) {
				assertNotBuilt();
				getValueParser().mustExist = mustExist;
				return (B) this;
			}

			@Override
			public B create(boolean create) {
				assertNotBuilt();
				getValueParser().create = create;
				return (B) this;
			}

			@Override
			public B relativeTo(String path) {
				assertNotBuilt();
				getValueParser().relativeTo = null;
				getValueParser().relativeToArgument = null;
				try {
					getValueParser().relativeTo = getValueParser().parse(path, EMPTY_ARGS);
				} catch (ParseException e) {
					throw new IllegalArgumentException(e);
				}
				return (B) this;
			}

			@Override
			public B relativeTo(F path) {
				assertNotBuilt();
				getValueParser().relativeTo = path;
				return (B) this;
			}

			@Override
			public B relativeToFileArg(String fileArgName) {
				assertNotBuilt();
				if (fileArgName.equals(getArgument().getName()))
					throw new IllegalArgumentException("Self-reference: " + fileArgName);
				ArgumentTypeHolder<?> relArg = getArgParser().getHolder(fileArgName);
				if (relArg.argument.getType() != getArgument().getType())
					throw new IllegalArgumentException(
						getArgument().getType().getName() + " argument cannot be specified relative to argument "
							+ relArg.argument.getName() + ", type " + relArg.argument.getType().getName());
				getValueParser().relativeToArgument = fileArgName;
				return (B) this;
			}

			@Override
			ArgumentTypeHolder<F> build() {
				if (getValueParser().create && getValueParser().directory == null)
					throw new IllegalArgumentException("If create(true) is used, directory(boolean) must be specified");
				return super.build();
			}

			static abstract class FileParser<F> implements ArgumentValueParser<F> {
				final String argumentName;
				final Class<F> theType;
				Boolean directory;
				boolean mustExist;
				boolean create;
				F relativeTo;
				String relativeToArgument;

				FileParser(String argumentName, Class<F> type) {
					this.argumentName = argumentName;
					theType = type;
				}

				@Override
				public F parse(String value, Arguments otherArgs) throws ParseException {
					F file;
					if (isAbsolute(value))
						file = getFile(value, null);
					else { // If not specified as absolute, resolve as relative if configured
						F relative;
						if (relativeToArgument != null) {
							F argValue = otherArgs.get(relativeToArgument, theType);
							if (argValue == null) {
								throw new IllegalArgumentException("No \"" + relativeToArgument
									+ "\" argument (for relative directory) specified before this argument (\"" + argumentName + "\")");
							} else {
								relative = argValue;
							}
						} else if (relativeTo != null) {
							relative = relativeTo;
						} else {
							relative = null;
						}
						if (relative != null) {
							file = getFile(value, relative);
						} else
							file = getFile(value, null);
					}
					if (mustExist && !exists(file)) {
						throw new IllegalArgumentException(file + " does not exist");
					}
					if (exists(file) && directory != null && isDirectory(file) != directory.booleanValue()) {
						throw new IllegalArgumentException(file + " is " + (directory ? "not " : "") + "a directory");
					}
					if (create && !exists(file)) {
						create(file, directory);
					}
					return file;
				}

				protected abstract boolean isAbsolute(String path);

				protected abstract F getFile(String path, F relative);

				protected abstract boolean exists(F file);

				protected abstract boolean isDirectory(F file);

				protected abstract void create(F file, boolean dir);

				@Override
				public String toString() {
					StringBuilder str = new StringBuilder();
					if (mustExist)
						str.append("Existing ");
					if (directory == null)
						str.append("File or directory");
					else if (directory)
						str.append("Directory");
					else
						str.append("File");
					if (relativeToArgument != null)
						str.append(" under the ").append(relativeToArgument).append(" argument");
					else if (relativeTo != null)
						str.append(" under ").append(relativeTo);
					return str.toString();
				}
			}
		}

		static class DefaultFileArgBuilder extends DefaultAbstractFileArgBuilder<File, DefaultFileArgBuilder>
			implements FileArgumentBuilder<DefaultFileArgBuilder> {
			DefaultFileArgBuilder(String name, ArgumentPattern pattern, DefaultParserBuilder argParser) {
				super(name, pattern, File.class, new Parser(name), argParser);
			}

			@Override
			public DefaultFileArgBuilder canonical() {
				assertNotBuilt();
				((Parser) getValueParser()).canonical = true;
				return this;
			}

			static class Parser extends FileParser<File> {
				boolean canonical;

				Parser(String argumentName) {
					super(argumentName, File.class);
				}

				@Override
				protected boolean isAbsolute(String path) {
					File file = new File(path);
					return file.getAbsolutePath().equals(file.getPath());
				}

				@Override
				protected File getFile(String path, File relative) {
					return relative == null ? new File(path) : new File(relative, path);
				}

				@Override
				protected boolean exists(File file) {
					return file.exists();
				}

				@Override
				protected boolean isDirectory(File file) {
					return file.isDirectory();
				}

				@Override
				protected void create(File file, boolean dir) {
					if (dir) {
						if (!file.mkdirs())
							throw new IllegalArgumentException("Could not create directory " + file.getPath());
					} else if (!file.setLastModified(System.currentTimeMillis()))
						throw new IllegalArgumentException("Could not create empty file " + file.getPath());
				}
			}
		}

		static class DefaultBetterFileArgBuilder extends DefaultAbstractFileArgBuilder<BetterFile, DefaultBetterFileArgBuilder>
			implements BetterFileArgumentBuilder<DefaultBetterFileArgBuilder> {
			DefaultBetterFileArgBuilder(String name, ArgumentPattern pattern, DefaultParserBuilder argParser) {
				super(name, pattern, BetterFile.class, new Parser(name), argParser);
			}

			@Override
			Parser getValueParser() {
				return (Parser) super.getValueParser();
			}

			@Override
			public DefaultBetterFileArgBuilder fromSource(FileDataSource dataSource) {
				getValueParser().dataSource = dataSource;
				return this;
			}

			static class Parser extends FileParser<BetterFile> {
				BetterFile.FileDataSource dataSource;

				Parser(String argumentName) {
					super(argumentName, BetterFile.class);
					dataSource = new NativeFileSource();
				}

				@Override
				protected boolean isAbsolute(String path) {
					for (FileBacking root : dataSource.getRoots()) {
						if (path.startsWith(root.getName()) //
							&& (path.length() == root.getName().length() || path.charAt(root.getName().length()) == '/'
								|| path.charAt(root.getName().length()) == '\\'))
							return true;
					}
					return false;
				}

				@Override
				protected BetterFile getFile(String path, BetterFile relative) {
					return relative != null ? relative.at(path) : BetterFile.at(dataSource, path);
				}

				@Override
				protected boolean exists(BetterFile file) {
					return file.exists();
				}

				@Override
				protected boolean isDirectory(BetterFile file) {
					return file.isDirectory();
				}

				@Override
				protected void create(BetterFile file, boolean dir) {
					try {
						file.create(dir);
					} catch (IOException e) {
						throw new IllegalArgumentException(
							"Could not create " + file.getPath() + " as a " + (directory ? "directory" : "file"));
					}
				}
			}
		}

		static class DefaultArgType<T> implements ArgumentType<T> {
			private final String theName;
			private final ArgumentPattern thePattern;
			private final Class<T> theType;
			private final List<ArgumentConstraint<T>> theConstraints;
			private String theDescription;
			int index;

			DefaultArgType(String name, ArgumentPattern pattern, Class<T> type, List<ArgumentConstraint<T>> constraints, String descrip) {
				theName = name;
				thePattern = pattern;
				theType = type;
				theConstraints = Collections.unmodifiableList(constraints);
				theDescription = descrip;
				index = -1;
			}

			@Override
			public String getName() {
				return theName;
			}

			@Override
			public ArgumentPattern getPattern() {
				return thePattern;
			}

			@Override
			public Class<T> getType() {
				return theType;
			}

			@Override
			public List<ArgumentConstraint<T>> getConstraints() {
				return theConstraints;
			}

			@Override
			public String getDescription() {
				return theDescription;
			}

			DefaultArgType<T> copy() {
				return new DefaultArgType<>(theName, thePattern, theType, theConstraints, theDescription);
			}

			@Override
			public String toString() {
				return theName + " (" + (theType == void.class ? "flag" : theType.getName()) + ")";
			}
		}

		static class DefaultArgParser implements ArgumentParser {
			private final QuickMap<String, ArgumentTypeHolder<?>> theArguments;
			private final List<ArgumentType<?>> theArgumentsList;
			private final boolean isAcceptingUnmatched;
			private final String theDescription;
			private boolean isPrintingHelpOnEmpty;
			private boolean isPrintingHelpOnError;

			DefaultArgParser(Map<String, ArgumentTypeHolder<?>> arguments, boolean acceptUnmatched, String description) {
				theArguments = QuickMap.of(arguments, StringUtils.DISTINCT_NUMBER_TOLERANT);
				for (int a = 0; a < theArguments.keySize(); a++)
					theArguments.get(a).argument.index = a;
				theArgumentsList = QommonsUtils.map(arguments.values(), v -> v.argument, true);
				isAcceptingUnmatched = acceptUnmatched;
				theDescription = description;
				isPrintingHelpOnError = true;
			}

			ArgumentType<?> getArgument(int index) {
				if (index < 0)
					return null;
				else if (index >= theArguments.keySize())
					return null;
				return theArguments.get(index).argument;
			}

			ArgumentTypeHolder<?> getHolder(String name) {
				ArgumentTypeHolder<?> arg = theArguments.getIfPresent(name);
				if (arg == null)
					throw new IllegalArgumentException("No such argument: \"" + name + "\"");
				return arg;
			}

			@Override
			public List<ArgumentType<?>> getArguments() {
				return theArgumentsList;
			}

			@Override
			public ArgumentType<?> getArgument(String name) throws IllegalArgumentException {
				ArgumentTypeHolder<?> arg = theArguments.getIfPresent(name);
				if (arg == null)
					throw new IllegalArgumentException("No such argument: \"" + name + "\"");
				return arg.argument;
			}

			@Override
			public ArgumentType<?> getArgumentIfExists(String name) {
				ArgumentTypeHolder<?> arg = theArguments.getIfPresent(name);
				return arg == null ? null : arg.argument;
			}

			@Override
			public boolean isAcceptingUnmatched() {
				return isAcceptingUnmatched;
			}

			@Override
			public ArgumentParser printHelpOnEmpty(boolean print) {
				isPrintingHelpOnEmpty = print;
				return this;
			}

			@Override
			public ArgumentParser printHelpOnError(boolean print) {
				isPrintingHelpOnError = print;
				return this;
			}

			@Override
			public String printHelp() {
				StringBuilder str = new StringBuilder();
				if (theDescription != null)
					str.append(theDescription);
				for (ArgumentType<?> arg : theArgumentsList)
					theArguments.get(((DefaultArgType<?>) arg).index).printHelp(str);
				return str.toString();
			}

			@Override
			public Arguments parse(List<String> args, boolean completely) {
				boolean printedHelp = false;
				if (args.isEmpty() && isPrintingHelpOnEmpty) {
					printedHelp=true;
					System.out.println(printHelp());
				}
				try {
					ParsingArguments parsingArgs = new ParsingArguments(this, theArguments.keySet().createMap(), args);
					for (ArgumentType<?> at : theArgumentsList) {
						parsingArgs.getArgumentsImpl(at, true);
					}
					List<String> errors = new LinkedList<>();
					List<String> unmatched;
					if (!args.isEmpty() && completely) {
						if (isAcceptingUnmatched) {
							unmatched = QommonsUtils.unmodifiableCopy(args);
						} else {
							unmatched = Collections.emptyList();
							for (String arg : args)
								errors.add("Unrecognized argument: \"" + arg + "\"");
						}
					} else
						unmatched = Collections.emptyList();
					// If there are errors already, don't bother checking the constraints
					if (!errors.isEmpty()) {
						throw new IllegalArgumentException(StringUtils.print("\n", errors, e -> e).toString());
					}
					// Check constraints
					Arguments parsedArgs = new DefaultArgs(this, parsingArgs.theMatches, unmatched);
					for (int a = 0; a < theArguments.keySize(); a++) {
						checkConstraints(theArguments.get(a).argument, parsedArgs, errors, true);
					}
					if (!errors.isEmpty()) {
						throw new IllegalArgumentException(StringUtils.print("\n", errors, e -> e).toString());
					}
					return parsedArgs;
				} catch (RuntimeException e) {
					if (!printedHelp && isPrintingHelpOnError)
						System.err.println(printHelp());
					throw e;
				}
			}
		}

		static <T> void checkConstraints(DefaultArgType<T> argument, Arguments parsedArgs, List<String> errors, //
			boolean conditionalConstraints) {
			for (ArgumentConstraint<T> constraint : argument.getConstraints()) {
				if (constraint.getCondition() != null && (!conditionalConstraints || !constraint.getCondition().applies(parsedArgs)))
					continue;
				int specified;
				if (argument.getPattern().getMaxValues() == 0)
					specified = parsedArgs.getArguments(argument).size();
				else {
					specified = 0;
					for (Argument<?> arg : parsedArgs.getArguments(argument)) {
						if (arg.getMatch() != null)
							specified++;
					}
				}
				if (specified < constraint.getMinTimes()) {
					if (constraint.getMinTimes() == 1)
						errors.add(argument.getName() + " is required but was not specified" + printCondition(constraint.getCondition()));
					else if (specified == 0)
						errors.add(argument.getName() + " is required " + constraint.getMinTimes() + " times but was not specified"
							+ printCondition(constraint.getCondition()));
					else
						errors.add(argument.getName() + " was specified " + specified + " time" + (specified == 1 ? "" : "s")
							+ " but is required " + constraint.getMinTimes() + " times" + printCondition(constraint.getCondition()));
				} else if (specified > constraint.getMaxTimes()) {
					if (constraint.getMaxTimes() == 0)
						errors.add(argument.getName() + " was specified, but is forbidden" + printCondition(constraint.getCondition()));
					else
						errors.add(argument.getName() + " was specified " + specified + " times, which is more than the maximum of "
							+ constraint.getMaxTimes() + printCondition(constraint.getCondition()));
				}
				if (constraint.getValueTest() != null && !parsedArgs.getAll(argument).isEmpty()) {
					for (Argument<T> arg : parsedArgs.getArguments(argument)) {
						if (arg.getMatch() == null)
							continue; // Default--don't check against constraints
						for (int v = 0; v < arg.getValues().size(); v++) {
							if (!constraint.getValueTest().test(arg.getValues().get(v), parsedArgs))
								errors.add(argument.getName() + " must obey [" + constraint.getValueTest() + "]: "
									+ arg.getMatch().getValues().get(v) + printCondition(constraint.getCondition()));
						}
					}
				}
			}
		}

		private static String printCondition(ArgumentCondition<?> condition) {
			if (condition instanceof ArgumentCondition.TrueCondition)
				return "";
			return " if " + condition.toString();
		}

		static class DefaultArg<T> implements Argument<T> {
			private final DefaultArgType<T> theType;
			private final MatchedArgument theMatch;
			private final BetterList<T> theValues;

			DefaultArg(DefaultArgType<T> type, MatchedArgument match, List<T> values) {
				theType = type;
				theMatch = match;
				theValues = BetterList.of(values);
			}

			@Override
			public ArgumentType<T> getType() {
				return theType;
			}

			@Override
			public BetterList<T> getValues() {
				return theValues;
			}

			@Override
			public MatchedArgument getMatch() {
				return theMatch;
			}

			@Override
			public String toString() {
				MatchedArgument match = theMatch;
				if (match == null) // Default
					match = new MatchedArgument(theType.getName(), Arrays.asList(String.valueOf(theValues.getFirst())));
				String str = theType.getPattern().print(match);
				if (theMatch == null)
					str += "(default)";
				return str;
			}
		}

		static class DefaultArgs implements Arguments {
			private final DefaultArgParser theParser;
			private final QuickMap<String, BetterList<? extends Argument<?>>> theMatches;
			private final BetterList<String> theUnmatched;

			DefaultArgs(DefaultArgParser parser, QuickMap<String, List<? extends Argument<?>>> matches, List<String> unmatched) {
				theParser = parser;
				theMatches = matches.keySet().createMap();
				for (int a = 0; a < matches.keySize(); a++) {
					if (matches.get(a) == null)
						theMatches.put(a, BetterList.empty());
					else
						theMatches.put(a, BetterList.of(matches.get(a)));
				}
				theUnmatched = unmatched == null ? BetterList.empty() : BetterList.of(unmatched);
			}

			@Override
			public ArgumentParser getParser() {
				return theParser;
			}

			@Override
			public <T> Argument<T> getArgument(ArgumentType<T> type) {
				return getArguments(type).peekFirst();
			}

			@Override
			public <T> BetterList<Argument<T>> getArguments(ArgumentType<T> type) {
				if (!(type instanceof DefaultArgType))
					throw new IllegalArgumentException("Unrecognized argument: " + type);
				DefaultArgType<T> argImpl = (DefaultArgType<T>) type;
				if (theParser.getArgument(argImpl.index) != type)
					throw new IllegalArgumentException("Unrecognized argument: " + type);
				return (BetterList<Argument<T>>) theMatches.get(argImpl.index);
			}

			@Override
			public BetterList<String> getUnmatched() {
				return theUnmatched;
			}

			@Override
			public BetterList<Argument<?>> getAllArguments() {
				return BetterList.of(theMatches.allValues().stream().flatMap(args -> args.stream()));
			}
		}

		static class ParsingArguments implements Arguments {
			private final DefaultArgParser theParser;
			private final QuickMap<String, List<? extends Argument<?>>> theMatches;
			private final List<String> theCLArgs;
			private final Set<String> theParsingArgs;

			ParsingArguments(DefaultArgParser parser, QuickMap<String, List<? extends Argument<?>>> matches, List<String> clArgs) {
				theParser = parser;
				theMatches = matches;
				theCLArgs = clArgs;
				theParsingArgs = new LinkedHashSet<>();
			}

			@Override
			public ArgumentParser getParser() {
				return theParser;
			}

			@Override
			public boolean has(String argument) {
				ArgumentTypeHolder<?> holder = theParser.getHolder(argument);
				if (theMatches.get(holder.argument.index) != null)
					return !theMatches.get(holder.argument.index).isEmpty();
				if (theParsingArgs.contains(argument))
					throw new IllegalArgumentException("Circularity detected: " + theParsingArgs + "+" + argument);
				Iterator<String> argIter = theCLArgs.iterator();
				while (argIter.hasNext()) {
					MatchedArgument matched = holder.argument.getPattern().match(argIter.next(), () -> {
						return argIter.hasNext() ? argIter.next() : null;
					});
					if (matched != null && matched.getName().equals(argument))
						return true;
				}
				return false;
			}

			@Override
			public <T> BetterList<Argument<T>> getArguments(ArgumentType<T> type) {
				return getArgumentsImpl(type, false);
			}

			<T> BetterList<Argument<T>> getArgumentsImpl(ArgumentType<T> type, boolean fromRootParser) {
				ArgumentTypeHolder<T> holder = (ArgumentTypeHolder<T>) theParser.getHolder(type.getName());
				if (holder.argument != type)
					throw new IllegalArgumentException("Unrecognized argument: " + type);
				List<? extends Argument<?>> matches = theMatches.get(holder.argument.index);
				if (theMatches.get(holder.argument.index) == null) {
					if (!theParsingArgs.add(type.getName()))
						throw new IllegalArgumentException(theParsingArgs + "+" + type.getName());
					matches = parseArgument(holder);
					theMatches.put(holder.argument.index, matches);
					theParsingArgs.remove(type.getName());
					if (!fromRootParser) {
						// If the arguments are requested by the parser of another argument,
						// check the unconditional constraints so the other argument parser can use it safely
						List<String> errors = new LinkedList<>();
						checkConstraints(holder.argument, this, errors, false);
						if (!errors.isEmpty()) {
							throw new IllegalArgumentException(StringUtils.print("\n", errors, e -> e).toString());
						}
					}
				}
				return BetterList.of((List<Argument<T>>) matches);
			}

			@Override
			public BetterList<String> getUnmatched() {
				return BetterList.empty();
			}

			private <T> List<Argument<T>> parseArgument(ArgumentTypeHolder<T> argType) {
				boolean found = false, foundAny = false;
				List<Argument<T>> matches = null;
				do {
					found = false;
					for (int i = 0; i < theCLArgs.size(); i++) {
						String arg = theCLArgs.get(i);
						int[] extraArgIndex = new int[] { i + 1 };
						MatchedArgument matchedArg = argType.argument.getPattern().match(arg, () -> {
							if (extraArgIndex[0] < theCLArgs.size())
								return theCLArgs.get(extraArgIndex[0]++);
							else
								return null;
						});
						if (matchedArg == null || !matchedArg.getName().equals(argType.argument.getName())) {
							continue;
						}

						foundAny = found = true;
						for (int j = extraArgIndex[0] - 1; j >= i; j--) {
							theCLArgs.remove(j);
						}
						i--;
						Argument<T> argument;
						if (matchedArg.getValues().isEmpty()) {
							argument = new DefaultArg<>(argType.argument, matchedArg, Collections.emptyList());
						} else if (argType.parser != null) {
							List<T> values = new ArrayList<>(matchedArg.getValues().size());
							for (int v = 0; v < matchedArg.getValues().size(); v++) {
								try {
									values.add(argType.parser.parse(matchedArg.getValues().get(v), this));
								} catch (ParseException e) {
									throw new IllegalArgumentException(e.getMessage(), e);
								}
							}
							argument = new DefaultArg<>(argType.argument, matchedArg, values);
						} else
							throw new IllegalStateException("Values found for flag argument " + argType.argument.getName());
						if (matches == null)
							matches = new ArrayList<>(theCLArgs.size());
						matches.add(argument);
					}
				} while (found);
				if (!foundAny && argType.defaultValue != null) {
					T defValue;
					try {
						defValue = argType.defaultValue.apply(this);
					} catch (ParseException e) {
						throw new IllegalArgumentException(e.getMessage(), e);
					}
					if (matches == null)
						matches = new ArrayList<>(theCLArgs.size());
					matches.add(new DefaultArg<>(argType.argument, null, Arrays.asList(defValue)));
				}
				return matches == null ? Collections.emptyList() : matches;
			}

			@Override
			public BetterList<Argument<?>> getAllArguments() {
				return BetterList.of(theMatches.allValues().stream().flatMap(args -> args == null ? Stream.empty() : args.stream()));
			}
		}

		static final Arguments EMPTY_ARGS = new Arguments() {
			@Override
			public ArgumentParser getParser() {
				throw new UnsupportedOperationException("Arguments are not supported here");
			}

			@Override
			public <T> BetterList<Argument<T>> getArguments(ArgumentType<T> type) {
				throw new UnsupportedOperationException("Arguments are not supported here");
			}

			@Override
			public BetterList<String> getUnmatched() {
				return BetterList.empty();
			}

			@Override
			public BetterList<Argument<?>> getAllArguments() {
				return BetterList.empty();
			}
		};
	}

	/**
	 * This main method is just to supply the source for the example in the class javadoc. It's here so that any time anything needs to
	 * change in that description, the compiler will catch it here so I can fix it and update the description.
	 * 
	 * @param args Command-line arguments
	 */
	static void main(String... clArgs) {
		ArgumentParsing.Arguments args = ArgumentParsing.build()//
			.forValuePattern(pattern -> pattern//
				.addIntArgument("a", arg -> arg.required())//
				.addIntArgument("b", arg -> arg//
					.when("a", Integer.class, c -> c.matches(v -> v.lt(0)).forbidden())//
					.when("a", Integer.class, c -> c.matches(v -> v.between(0, 100)).required())//
					.when("a", Integer.class, c -> c.matches(v -> v.between(0, 100)).then(v -> v.between(0, 100)))//
					.when("a", Integer.class, c -> c.matches(v -> v.gt(100)).required())//
					.when("a", Integer.class, c -> c.matches(v -> v.gt(100)).then(v -> v.gt(100)))//
				)//
				.addIntArgument("c", arg -> arg.defaultValue(25))//
			).build().parse(clArgs);

		int a = args.get("a", Integer.class);
		Integer b = args.get("b", Integer.class);
		int c = args.get("c", Integer.class);

		System.out.println("a=" + a + ", b=" + b + ", c=" + c);
	}
}
