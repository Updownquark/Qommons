package org.qommons.io;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import org.qommons.ArgumentParsing;
import org.qommons.ArgumentParsing.ArgumentParser;
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
		 * @return True if the given content is complete, false if more lines are expected
		 * @throws ParseException If the content could not be parsed
		 */
		public boolean input(CharSequence content) throws ParseException;
	}

	/** Handles command-line argument-style plugin input */
	public interface QonsoleArgsHandler {
		/**
		 * @param args The parsed arguments given by the user
		 * @throws ParseException If the input could not be handled
		 */
		public void input(ArgumentParsing.Arguments args) throws ParseException;
	}

	/**
	 * A plugin to handle command-line argument-style plugin input
	 * 
	 * @see Qonsole#addPlugin(String, ArgumentParser, QonsoleArgsHandler)
	 */
	public class QonsoleArgsPlugin implements QonsolePlugin {
		private final ArgumentParsing.ArgumentParser theParser;
		private final QonsoleArgsHandler theHandler;

		/**
		 * @param parser The parser to parse the command-line-style arguments
		 * @param handler The handler to handle the parsed input
		 */
		public QonsoleArgsPlugin(ArgumentParser parser, QonsoleArgsHandler handler) {
			theParser = parser;
			theHandler = handler;
		}

		@Override
		public boolean input(CharSequence content) throws ParseException {
			List<String> argList = new ArrayList<>(5);
			StringBuilder currentArg = new StringBuilder();
			boolean quoted = false;
			boolean escaped = false;
			for (int i = 0; i < content.length(); i++) {
				char ch = content.charAt(i);
				switch (ch) {
				case '"':
					if (escaped) {
						currentArg.append(ch);
						escaped = false;
					}
					if (quoted && !escaped) {
						quoted = false;
						argList.add(currentArg.toString());
						currentArg.setLength(0);
					} else if (currentArg.length() == 0)
						quoted = true;
					break;
				case ' ':
				case '\t':
					if (escaped) {
						currentArg.append(ch);
						escaped = false;
					} else if (quoted)
						currentArg.append(ch);
					else {
						argList.add(currentArg.toString());
						currentArg.setLength(0);
					}
					break;
				case '\r':
					break;
				case '\\':
					if (escaped) {
						currentArg.append(ch);
						escaped = false;
					} else
						escaped = true;
					break;
				case 't':
					if (escaped) {
						currentArg.append('\t');
						escaped = false;
					} else
						currentArg.append(ch);
					break;
				case 'n':
					if (escaped) {
						currentArg.append('\n');
						escaped = false;
					} else
						currentArg.append(ch);
					break;
				case 'r':
					if (escaped) {
						currentArg.append('\r');
						escaped = false;
					} else
						currentArg.append(ch);
					break;
				default:
					if (escaped)
						throw new ParseException("Cannot escape '" + ch + "'", i - 1);
					else
						currentArg.append(ch);
					break;
				}
			}
			if (quoted || escaped)
				return false;
			if (currentArg.length() > 0)
				argList.add(currentArg.toString());
			ArgumentParsing.Arguments args;
			try {
				args = theParser.parse(argList);
			} catch (IllegalArgumentException e) {
				throw new ParseException(e.getMessage(), 0);
			}
			theHandler.input(args);
			return true;
		}
	}

	private static Qonsole SYSTEM_QONSOLE;

	/** @return A colon-delimited Qonsole monitoring System.in that does not interfere with other (later) users of System.in */
	public static Qonsole getSystemConsole() {
		Qonsole qonsole = SYSTEM_QONSOLE;
		if (qonsole == null) {
			synchronized (Qonsole.class) {
				qonsole = SYSTEM_QONSOLE;
				if (qonsole == null) {
					qonsole = new Qonsole("System", new InputStreamReader(System.in), ":", () -> false);
					System.setIn(new ReaderInputStream(qonsole.read()));
					SYSTEM_QONSOLE = qonsole;
				}
			}
		}
		return qonsole;
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

	/**
	 * @param pluginName The name of the plugin, by which the user may address arguments to it
	 * @param parser The argument parser to structure the plugin's input
	 * @param handler The handler to receive the parsed arguments
	 * @return This Qonsole
	 */
	public Qonsole addPlugin(String pluginName, ArgumentParsing.ArgumentParser parser, QonsoleArgsHandler handler) {
		return addPlugin(pluginName, new QonsoleArgsPlugin(parser, handler));
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
						} catch (ParseException e) {
							if (e.getErrorOffset() > 0) {
								for (int off = 0; off < e.getErrorOffset(); off++)
									System.out.print(' ');
								System.out.println("^");
							}
							System.err.println(e.getMessage());
							done = true;
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
