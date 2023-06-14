package org.qommons.config;

import java.util.function.Supplier;

/** Values for a hierarchical session */
public interface SessionValues {
	/**
	 * @param sessionKey The key to get data for
	 * @return Data stored for the given key in this session
	 */
	default Object get(String sessionKey) {
		return get(sessionKey, false);
	}

	/**
	 * @param sessionKey The key to get data for
	 * @param localOnly Whether, if no data is present for the given key, to search for inherited values in its parent sessions
	 * @return Data stored for the given key in this session
	 */
	Object get(String sessionKey, boolean localOnly);

	/**
	 * @param <T> The expected type of the value
	 * @param sessionKey The key to get data for
	 * @param type The expected type of the value. The super wildcard is to accommodate generics.
	 * @return Data stored for the given key in this session
	 */
	<T> T get(String sessionKey, Class<? super T> type);

	/**
	 * Puts a value into this session that will be visible to all descendants of this session (created after this call)
	 * 
	 * @param sessionKey The key to store data for
	 * @param value The data to store for the given key in this session
	 * @return This session
	 */
	SessionValues put(String sessionKey, Object value);

	/**
	 * Puts a value into this session that will be visible only to sessions "parallel" to this session--sessions for the same element
	 * 
	 * @param sessionKey The key to store data for
	 * @param value The data to store for the given key in this session
	 * @return This session
	 */
	SessionValues putLocal(String sessionKey, Object value);

	/**
	 * @param <T> The type of the data
	 * @param sessionKey The key to store data for
	 * @param creator Creates data to store for the given key in this session (if absent)
	 * @return The previous or new value
	 */
	<T> T computeIfAbsent(String sessionKey, Supplier<T> creator);

	/** An enum describing where a value session is from */
	enum ValueSource {
		/**
		 * The value was set on the session itself (or another session for the same element) and is also available to child sessions via
		 * inheritance
		 */
		Own,
		/** The value is inherited from a parent session */
		Inherited,
		/**
		 * The value was set on the session itself (or another session for the same element) and is NOT available to child sessions via
		 * inheritance
		 */
		Local
	}
}