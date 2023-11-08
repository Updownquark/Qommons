package org.qommons.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Map;

import org.qommons.QommonsUtils;
import org.qommons.io.BetterFile.CheckSumType;

/** A method of determining the checksum of a file via command-line */
public interface CheckSumCommand{
	/** A {@link CheckSumCommand} for each of several common operating systems */
	public static final Map<String, CheckSumCommand> CHECK_SUM_COMMANDS = QommonsUtils.<String, CheckSumCommand> buildMap(null)//
		.with("Linux", new CheckSumCommand() {
			@Override
			public String getCommand(CheckSumType type, String filePath) {
				switch (type) {
				case CRC32:
					return null; // No checksum supported generally on command-line on Linux
				case MD5:
					return "md5sum " + filePath;
				case SHA1:
					return "sha1sum " + filePath;
				case SHA256:
					return "sha256sum " + filePath;
				case SHA512:
					return "sha512sum " + filePath;
				}
				return null;
			}

			@Override
			public String readOutput(Reader commandOut, CheckSumType type) throws IOException {
				BufferedReader br = commandOut instanceof BufferedReader ? (BufferedReader) commandOut : new BufferedReader(commandOut);
				String line = br.readLine();
				if (line == null)
					return line;
				int space = line.indexOf(' ');
				return space < 0 ? line : line.substring(0, space);
			}
		})//
		.with("Windows", new CheckSumCommand() {
			@Override
			public String getCommand(CheckSumType type, String filePath) {
				switch (type) {
				case CRC32:
					return null; // No checksum supported generally on command-line on Windows
				case MD5:
					return "certutil -hashfile " + filePath + " MD5";
				case SHA1:
					return "certutil -hashfile " + filePath + " SHA1";
				case SHA256:
					return "certutil -hashfile " + filePath + " SHA256";
				case SHA512:
					return "certutil -hashfile " + filePath + " SHA512";
				}
				return null;
			}

			@Override
			public String readOutput(Reader commandOut, CheckSumType type) throws IOException {
				BufferedReader br = commandOut instanceof BufferedReader ? (BufferedReader) commandOut : new BufferedReader(commandOut);
				String line = br.readLine();
				while (line != null) {
					if (line.length() >= type.hexChars) {
						boolean isHash = true;
						for (int i = 0; isHash && i < type.hexChars; i++) {
							isHash = (line.charAt(i) >= '0' && line.charAt(i) <= '9') || (line.charAt(i) >= 'a' && line.charAt(i) <= 'f');
						}
						if (isHash)
							return line.substring(0, type.hexChars);
					}
					line = br.readLine();
				}
				throw new IOException("Could not read checksum from command output");
			}
		})//
		.with("Mac", new CheckSumCommand() {
			@Override
			public String getCommand(CheckSumType type, String filePath) {
				switch (type) {
				case CRC32:
					// Not sure if this would actually work, but if it fails it's not much worse than not trying.
					return "crc32 " + filePath;
				case MD5:
					return "md5 " + filePath;
				case SHA1:
					return "shasum -a 1 " + filePath;
				case SHA256:
					return "shasum -a 256 " + filePath;
				case SHA512:
					return "shasum -a 512 " + filePath;
				}
				return null;
			}

			@Override
			public String readOutput(Reader commandOut, CheckSumType type) throws IOException {
				BufferedReader br = commandOut instanceof BufferedReader ? (BufferedReader) commandOut : new BufferedReader(commandOut);
				String line = br.readLine();
				if (line == null)
					return line;
				int space = line.indexOf(' ');
				return space < 0 ? line : line.substring(0, space);
			}
		})//
		.getUnmodifiable();

	/**
	 * @param type The type of the checksum to create a command for
	 * @param filePath The path of the file to create the checksum for
	 * @return The system command to execute to compute the file checksum, or null if such an operation is not supported
	 */
	String getCommand(CheckSumType type, String filePath);
	
	/**
	 * @param commandOut The output of the process being executed against the command given type {@link #getCommand(CheckSumType, String)}
	 * @param type The type of the checksum
	 * @return The hex-encoded checksum output
	 * @throws IOException If the checksum output could not be read or parsed
	 */
	String readOutput(Reader commandOut, CheckSumType type) throws IOException;
}