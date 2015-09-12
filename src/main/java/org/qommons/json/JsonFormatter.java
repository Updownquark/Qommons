/* JsonUtils.java Created Aug 25, 2010 by Andrew Butler, PSL */
package org.qommons.json;

import java.io.IOException;
import java.io.Reader;

import org.qommons.json.SAJParser.ParseState;

/** Formats a JSON file to be more easily read by a human */
public class JsonFormatter {
	/**
	 * Formats a file
	 *
	 * @param args The location of the file to format
	 * @throws IOException If an error occurs reading or writing the file
	 */
	public static void main(String [] args) throws IOException {
		java.io.Reader fileReader = new java.io.InputStreamReader(new org.qommons.FileSegmentizerInputStream(args[0]));
		String outFileName;
		int dotIdx = args[0].lastIndexOf('.');
		if(dotIdx >= 0)
			outFileName = args[0].substring(0, dotIdx) + ".fmt.json";
		else
			outFileName = args[0] + ".fmt.json";
		final java.io.Writer [] fileWriter = new java.io.Writer[] {
				new java.io.OutputStreamWriter(new org.qommons.FileSegmentizerOutputStream(outFileName))};
		java.io.Writer writer = new java.io.Writer() {
			@Override
			public void write(char [] cbuf, int off, int len) throws IOException {
				System.out.print(new String(cbuf, off, len));
				fileWriter[0].write(cbuf, off, len);
			}

			@Override
			public void flush() throws IOException {
				System.out.flush();
				fileWriter[0].flush();
			}

			@Override
			public void close() throws IOException {
			}
		};
		final JsonStreamWriter jsonWriter = new JsonStreamWriter(writer);
		jsonWriter.setFormatIndent("\t");
		SAJParser.ParseHandler handler = new SAJParser.ParseHandler() {
			@Override
			public void startObject(ParseState state) {
				try {
					jsonWriter.startObject();
				} catch(IOException e) {
					throw new IllegalStateException("Could not write JSON", e);
				}
			}

			@Override
			public void startProperty(ParseState state, String name) {
				try {
					jsonWriter.startProperty(name);
				} catch(IOException e) {
					throw new IllegalStateException("Could not write JSON", e);
				}
			}

			@Override
			public void separator(ParseState state) {
			}

			@Override
			public void endProperty(ParseState state, String propName) {
			}

			@Override
			public void endObject(ParseState state) {
				try {
					jsonWriter.endObject();
				} catch(IOException e) {
					throw new IllegalStateException("Could not write JSON", e);
				}
			}

			@Override
			public void startArray(ParseState state) {
				try {
					jsonWriter.startArray();
				} catch(IOException e) {
					throw new IllegalStateException("Could not write JSON", e);
				}
			}

			@Override
			public void endArray(ParseState state) {
				try {
					jsonWriter.endArray();
				} catch(IOException e) {
					throw new IllegalStateException("Could not write JSON", e);
				}
			}

			@Override
			public void valueBoolean(ParseState state, boolean value) {
				try {
					jsonWriter.writeBoolean(value);
				} catch(IOException e) {
					throw new IllegalStateException("Could not write JSON", e);
				}
			}

			@Override
			public void valueString(ParseState state, Reader value) throws IOException {
				java.io.Writer stringWriter = jsonWriter.writeStringAsWriter();
				int read = value.read();
				while(read >= 0) {
					stringWriter.write(read);
					read = value.read();
				}
				value.close();
				stringWriter.close();
			}

			@Override
			public void valueNumber(ParseState state, Number value) {
				try {
					jsonWriter.writeNumber(value);
				} catch(IOException e) {
					throw new IllegalStateException("Could not write JSON", e);
				}
			}

			@Override
			public void valueNull(ParseState state) {
				try {
					jsonWriter.writeNull();
				} catch(IOException e) {
					throw new IllegalStateException("Could not write JSON", e);
				}
			}

			@Override
			public void whiteSpace(ParseState state, String ws) {
			}

			@Override
			public void comment(ParseState state, String fullComment, String content) {
			}

			@Override
			public Object finalValue() {
				return null;
			}

			@Override
			public void error(ParseState state, String error) {
				System.err.println("\nError at Line " + state.getLineNumber() + ", char " + state.getCharNumber() + "--(Line "
					+ jsonWriter.getLineNumber() + " formatted)");
			}
		};
		try {
			new SAJParser().parse(fileReader, handler);
		} catch(SAJParser.ParseException e) {
			System.out.println(e + "  File may be exported. Trying import.");
			fileReader.close();
			fileWriter[0].close();
			try {
				fileReader = new java.io.InputStreamReader(
					new org.qommons.ImportStream(new org.qommons.FileSegmentizerInputStream(args[0])));
				fileWriter[0] = new java.io.OutputStreamWriter(new org.qommons.FileSegmentizerOutputStream(outFileName));
				new SAJParser().parse(fileReader, handler);
			} catch(SAJParser.ParseException e2) {
				e.printStackTrace();
				e2.printStackTrace();
			}
		} finally {
			fileReader.close();
			fileWriter[0].flush();
			fileWriter[0].close();
		}
	}
}
