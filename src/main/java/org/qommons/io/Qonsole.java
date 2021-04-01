package org.qommons.io;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

import org.qommons.Named;

/**
 * The Qonsole class allows callers to monitor a reader (e.g. {@link System#in}) for targeted input without dominating it. Using this class,
 * A caller can register a {@link QonsolePlugin plugin} to be notified when the user calls it by name from System.in, but code that calls
 * System.in or uses this class later is not affected.
 */
public class Qonsole implements Named, AutoCloseable {
	/** A plugin to listen for targeted user input in a Qonsole */
	public interface QonsolePlugin {
		/**
		 * @param content The content to parse
		 * @return True if the content complete, false if more lines are expected
		 */
		public boolean input(CharSequence content);
	}

	private static Qonsole SYSTEM_QONSOLE;

	/** @return A Qonsole monitoring System.in that does not interfere with other (later) users of System.in */
	public static Qonsole getSystemConsole() {
		if (SYSTEM_QONSOLE == null) {
			synchronized (Qonsole.class) {
				if (SYSTEM_QONSOLE == null) {
					SYSTEM_QONSOLE = new Qonsole("System", new InputStreamReader(System.in), ":", () -> false);
					System.setIn(new ReaderInputStream(SYSTEM_QONSOLE.read()));
				}
			}
		}
		return SYSTEM_QONSOLE;
	}

	private final String theName;
	private final Reader theInput;
	private final BooleanSupplier isClosed;
	private final String theCommandSeparator;
	private boolean isDone;
	private final Thread theListener;
	private final Map<String, QonsolePlugin> thePlugins;
	// This buffer is used circularly for efficiency
	private final CircularCharBuffer thePrivateBuffer;
	private final BufferedReaderWriter thePublicBuffer;
	private QonsolePlugin theCurrentPlugin;
	private int theCommandOffset;

	/**
	 * @param name The name of the qonsole
	 * @param in The reader to monitor
	 * @param commandSeparator The command separator to watch for
	 * @param closed Tells this qonsole to stop actively monitoring input
	 */
	public Qonsole(String name, Reader in, String commandSeparator, BooleanSupplier closed) {
		theName = name;
		theInput = in;
		theCommandSeparator = commandSeparator;
		isClosed = closed != null ? closed : () -> false;
		thePlugins = new LinkedHashMap<>();
		thePrivateBuffer = new CircularCharBuffer(-1);
		thePublicBuffer = new BufferedReaderWriter();

		theListener = new Thread(new Runnable() {
			@Override
			public void run() {
				while (!isClosed()) {
					try {
						check();
					} catch (IOException e) {
						System.err.println("Error reading for " + Qonsole.this + ":");
						e.printStackTrace();
						break;
					}
				}
			}
		}, theName + " Listener");
		theListener.start();
	}

	@Override
	public String getName() {
		return theName;
	}

	/** @return The Reader for content not used by any plugin */
	public Reader read() {
		return thePublicBuffer.read();
	}

	/**
	 * @param pluginName The name of the plugin, by which the user may address content to it
	 * @param plugin The plugin to receive the content
	 * @return This Qonsole
	 */
	public synchronized Qonsole addPlugin(String pluginName, QonsolePlugin plugin) {
		QonsolePlugin old = thePlugins.putIfAbsent(pluginName, plugin);
		if (old != null)
			throw new IllegalArgumentException("A plugin named " + pluginName + " is already present");
		return this;
	}

	private void check() throws IOException {
		if (isDone)
			return;
		while (true) {
			int preLen = thePrivateBuffer.length();
			if (thePrivateBuffer.appendFrom(theInput, 1024) < 0) {
				isDone = true;
				break;
			}
			// Interpret the stream for commands, if present
			for (int i = preLen; i < thePrivateBuffer.length(); i++) {
				if (thePrivateBuffer.charAt(i) == '\n') {
					if (theCurrentPlugin != null) {
						CharSequence command = thePrivateBuffer.subSequence(0, thePrivateBuffer.length() - 1);
						boolean done;
						try {
							done = theCurrentPlugin.input(command);
						} catch (RuntimeException | Error e) {
							System.err.println("Error from plugin " + theCurrentPlugin + " for command \"" + command + "\"");
							e.printStackTrace();
							done = true;
						}
						if (done) {
							thePrivateBuffer.delete(0, i + 1);
							i = -1; // We'll increment in a sec
							theCurrentPlugin = null;
						}
					} else {
						thePublicBuffer.write().append(thePrivateBuffer.subSequence(0, i + 1));
						thePrivateBuffer.delete(0, i + 1);
						i = -1;
					}
				} else if (theCurrentPlugin == null && thePrivateBuffer.charAt(i) == theCommandSeparator.charAt(theCommandOffset)) {
					if (++theCommandOffset == theCommandSeparator.length()) {
						theCommandOffset = 0;
						String pluginName = thePrivateBuffer.subSequence(0, i).toString();
						theCurrentPlugin = thePlugins.get(pluginName);
						thePrivateBuffer.delete(0, i + 1);// Erase the plugin name and separator from the buffer
						i = -1; // We'll increment in a sec
					}
				}
			}
		}
	}

	@Override
	public void close() throws IOException {
		theInput.close();
		thePublicBuffer.write().close();
		isDone = true;
	}

	/** @return Whether this Qonsole is closed either due to a {@link #close()} call or because its source reader is exhausted */
	public boolean isClosed() {
		return isDone || isClosed.getAsBoolean();
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + " " + getName();
	}
}
