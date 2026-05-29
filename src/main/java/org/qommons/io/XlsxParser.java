package org.qommons.io;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import org.qommons.StringUtils;
import org.qommons.collect.BetterList;
import org.qommons.io.BetterFile.FileDataSource;
import org.qommons.io.MinML.ComponentParser;
import org.qommons.io.MinML.XmlAttribute;
import org.qommons.io.MinML.XmlComponent;
import org.qommons.io.MinML.XmlElementTerminal;

/**
 * <p>
 * A very simplistic view of an XLSX-formatted spreadsheet. This parser fulfills the {@link TabularFileParser} API, which was originally
 * designed for CSV files, against a spreadsheet.
 * </p>
 * <p>
 * The spreadsheet is parsed line-by-line, without any looking ahead (other than buffering). Previously-encountered data is not kept (except
 * shared data). This allows very large spreadsheets to be parsed very quickly and efficiently. E.g. the spreadsheet's header row can be
 * parsed and inspected, then the parser closed without reading any of the rest of the spreadsheet.
 * </p>
 */
public class XlsxParser implements TabularFileParser {
	/** A directive for how to handle spreadsheet files with multiple sheets in them */
	public enum MultipleSheetHandling {
		/** Use the first sheet */
		UseFirst,
		/** Use the lines from all sheets sequentially */
		Append,
		/** Throw an exception */
		Error;
	}

	/** Represents the location of a cell in a spreadsheet */
	public static class SheetPosition {
		/** A1 cell location */
		public static final SheetPosition ZERO = new SheetPosition(0, 0);

		/**
		 * @param row The row index of the cell
		 * @param column The column index of the cell
		 * @return The cell position
		 */
		public static SheetPosition of(int row, int column) {
			if (row == 0 && column == 0)
				return ZERO;
			else
				return new SheetPosition(row, column);
		}

		/**
		 * @param positionString The content containing the cell reference
		 * @return The parsed cell reference
		 * @throws TextParseException If the cell reference was unrecognized
		 */
		public static SheetPosition parse(PositionedContent positionString) throws TextParseException {
			if (positionString.length() < 2)
				throw new TextParseException("No position found", positionString.getPosition(positionString.length()));
			int rowStart = 1;
			while (true) {
				char c = positionString.charAt(rowStart);
				if (c >= '0' && c <= '9')
					break;
				rowStart++;
				if (rowStart < positionString.length())
					throw new TextParseException("No position found", positionString.getPosition(positionString.length()));
			}
			int column = parseColumn(positionString.subSequence(0, rowStart));
			int row;
			try {
				row = Integer.parseInt(positionString.subSequence(rowStart).toString()) - 1;
			} catch (NumberFormatException e) {
				throw new TextParseException("Bad row", positionString.getPosition(rowStart), e);
			}
			return of(row, column);
		}

		private static int parseColumn(PositionedContent columnText) throws TextParseException {
			int column = 0;
			for (int i = 0; i < columnText.length(); i++) {
				int dig = columnText.charAt(i) - 'A';
				if (dig < 0 || dig >= 26)
					throw new TextParseException("Bad column", columnText.getPosition(i));
				column = column * 26 + dig;
			}
			return column;
		}

		private final int theRow;
		private final int theColumn;

		private SheetPosition(int row, int column) {
			theRow = row;
			theColumn = column;
		}

		/** @return The row index of the cell */
		public int getRow() {
			return theRow;
		}

		/** @return The column index of the cell */
		public int getColumn() {
			return theColumn;
		}

		/**
		 * @param offset The relative offset
		 * @return This sheet position relative to the given offset
		 */
		public SheetPosition relativeTo(SheetPosition offset) {
			if (offset == ZERO)
				return this;
			else
				return new SheetPosition(theRow - offset.theRow, theColumn - offset.theColumn);
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder();
			int column = theColumn;
			do {
				str.insert(0, (char) ('A' + column % 26));
				column /= 26;
			} while (column > 0);
			str.append(theRow + 1);
			return str.toString();
		}
	}

	/** A sheet within a spreadsheet file */
	public static class Sheet implements TabularFileParser {
		private final XlsxParser theXlsxParser;
		private final String theSheetName;
		private InputStream theFileInput;
		private ComponentParser theParser;
		private final int theRowCount;
		private final int theColumnCount;

		private int theEncounteredRowCount;
		private Row theLastRow;
		private int theNonEmptyRowCount;
		private int thePassedBlankLines;

		Sheet(XlsxParser xlsxParser, String sheetName, InputStream fileInput, String path) throws IOException, TextParseException {
			theXlsxParser = xlsxParser;
			theSheetName = sheetName;
			theFileInput = fileInput;
			theParser = theXlsxParser.getXmlParser().parseByComponent(path, fileInput);
			theParser.startNextElement("worksheet", true);
			theParser.startNextElement("dimension", true);
			SheetPosition dim = parseDimension(theParser.getAttribute("ref", true).getValueContent());
			theRowCount = dim.getRow() + 1;
			theColumnCount = dim.getColumn() + 1;
			theParser.closeCurrentElement();
			theParser.startNextElement("sheetData", true);
		}

		private static SheetPosition parseDimension(PositionedContent dim) throws TextParseException {
			String str = dim.toString();
			int colon = str.indexOf(':');
			if (colon < 0)
				throw new TextParseException("':' expected in dimension ref", dim.getPosition(dim.length()));
			SheetPosition start = SheetPosition.parse(dim.subSequence(0, colon));
			SheetPosition end = SheetPosition.parse(dim.subSequence(colon + 1));
			return end.relativeTo(start);
		}

		@Override
		public long getFileLength() {
			return theRowCount;
		}

		@Override
		public long getParseProgress() {
			return theEncounteredRowCount;
		}

		/**
		 * @param passEmpties Whether to pass {@link Row#isEmpty() empty} rows
		 * @return The next row (or non-empty row) in the file, or null if there were no more
		 * @throws IOException If the file could not be read
		 * @throws TextParseException If the file could not be parsed
		 */
		public Row parseNextRow(boolean passEmpties) throws IOException, TextParseException {
			thePassedBlankLines = 0;
			Row row = parseNextRow();
			if (passEmpties) {
				while (row != null && row.isEmpty()) {
					thePassedBlankLines++;
					row = parseNextRow();
				}
				if (row != null)
					theNonEmptyRowCount++;
			} else if (row != null && !row.isEmpty())
				theNonEmptyRowCount++;
			return row;
		}

		private Row parseNextRow() throws IOException, TextParseException {
			if (theParser == null)
				return null;
			else if (theParser.startNextElement("row", false) == null) {
				close();
				return null;
			}
			theEncounteredRowCount++;
			int rowDigits = getRowDigits(theEncounteredRowCount);
			try {
				StringBuilder columnId = new StringBuilder("A");
				Cell[] cells = new Cell[theColumnCount];
				int cellIndex = 0;
				XmlAttribute[] t = new XmlAttribute[1];
				XmlElementTerminal cellElement;
				while ((cellElement = theParser.startNextElement("c", false)) != null) {
					t[0] = null;
					PositionedContent cellId = theParser.<XmlAttribute> parseUntil(component -> {
						if (component instanceof XmlAttribute) {
							XmlAttribute attr = (XmlAttribute) component;
							switch (attr.getName()) {
							case "r":
								return attr;
							case "t":
								t[0] = attr;
								//$FALL-THROUGH$
							default:
								return null;
							}
						}
						if (isCellClose(component)) {
							throw new TextParseException("'r' attribute expected", component.getContent().getPosition(0));
						}
						return null;
					}).getValueContent();
					PositionedContent column = cellId.subSequence(0, cellId.length() - rowDigits);
					while (!columnIdMatches(column, columnId)) {
						cells[cellIndex] = Cell.MISSING;
						cellIndex++;
						incrementColumnId(columnId);
					}
					if (cellIndex >= cells.length)
						break;
					if (t[0] == null)
						t[0] = theParser.getAttribute("t", false);
					cells[cellIndex] = parseCell(cellElement.getContent().getPosition(0), t[0] == null ? null : t[0].getValueContent());
					while (theParser.getCurrentElement().getDepth() > 2)
						theParser.closeCurrentElement();
					cellIndex++;
					incrementColumnId(columnId);
				}
				while (cellIndex < cells.length)
					cells[cellIndex++] = Cell.MISSING;
				theLastRow = new Row(theEncounteredRowCount - 1, cells);
				return theLastRow;
			} finally {
				while (theParser.getCurrentElement().getDepth() > 1)
					theParser.closeCurrentElement();
			}
		}

		private Cell parseCell(FilePosition cellPosition, PositionedContent t) throws IOException, TextParseException {
			PositionedContent v;
			if (t == null) {
				if (theParser.startNextElement("v", false) != null) {
					v = theParser.getElementContent(true);
					return new Cell(cellPosition, CellType.Unspecified, v.toString());
				} else
					return new Cell(cellPosition, CellType.Empty, "");
			}
			CellType cellType = CellType.getType(t.toString());
			switch (cellType) {
			case Boolean:
				theParser.startNextElement("v", true);
				v = theParser.getElementContent(true);
				String boolStr;
				// This is always "0" or "1", but what the heck
				switch (v.toString().trim().toUpperCase()) {
				case "0":
				case "F":
				case "FALSE":
				case "N":
				case "NO":
					boolStr = "FALSE";
					break;
				case "1":
				case "T":
				case "TRUE":
				case "Y":
				case "YES":
					boolStr = "TRUE";
					break;
				default:
					System.err.println("Unrecognized boolean: '" + v + "'");
					boolStr = "FALSE";
					break;
				}
				return new Cell(cellPosition, cellType, boolStr);
			case Date:
			case Formula:
			case Number:
				theParser.startNextElement("v", true);
				v = theParser.getElementContent(true);
				return new Cell(cellPosition, cellType, v.toString());
			case InlineString:
				theParser.startNextElement("is", true);
				theParser.startNextElement("t", true);
				v = theParser.getElementContent(true);
				return new Cell(cellPosition, cellType, v.toString());
			case SharedString:
				theParser.startNextElement("v", true);
				v = theParser.getElementContent(true);
				theParser.closeCurrentElement();
				String sharedString = theXlsxParser.getSharedStrings().getString(v);
				return new Cell(cellPosition, cellType, sharedString);
			case Error:
			case Unrecognized:
				if (theParser.startNextElement("v", false) != null) {
					v = theParser.getElementContent(true);
					return new Cell(cellPosition, cellType, v.toString());
				} else
					return new Cell(cellPosition, cellType, "");
			case Empty:
			case Unspecified:
			case Missing:
				break;
			}
			throw new IllegalStateException("Unrecognized cell type '" + t.toString() + "'");
		}

		private static int getRowDigits(int rowNumber) {
			if (rowNumber < 10)
				return 1;
			else if (rowNumber < 100)
				return 2;
			else if (rowNumber < 1000)
				return 3;
			else if (rowNumber < 10000)
				return 4;
			int digits = 5;
			rowNumber /= 10000;
			while (rowNumber > 10) {
				digits++;
				rowNumber /= 10;
			}
			return digits;
		}

		private static boolean columnIdMatches(PositionedContent column, StringBuilder columnId) throws TextParseException {
			if (column.length() < columnId.length())
				throw new TextParseException("We don't handle mixed up columns within a row: " + column + " vs " + columnId,
					column.getPosition(0));
			else if (column.length() > columnId.length())
				return false;
			for (int i = 0; i < columnId.length(); i++) {
				char c1 = column.charAt(i);
				char c2 = columnId.charAt(i);
				if (c1 < c2)
					throw new TextParseException("We don't handle mixed up columns within a row: " + column + " vs " + columnId,
						column.getPosition(i));
				else if (c1 != c2)
					return false;
			}
			return true;
		}

		private static void incrementColumnId(StringBuilder columnId) {
			for (int i = columnId.length() - 1; i >= 0; i--) {
				char ch = columnId.charAt(i);
				if (ch != 'Z') {
					columnId.setCharAt(i, (char) (ch + 1));
					return;
				} else
					columnId.setCharAt(i, 'A');
			}
			columnId.insert(0, 'A');
		}

		static boolean isCellClose(XmlComponent component) {
			if (!(component instanceof XmlElementTerminal))
				return false;
			XmlElementTerminal el = (XmlElementTerminal) component;
			return !el.isOpen() && el.getDepth() == 3;
		}

		@Override
		public String[] parseNextLineVC(String[] columns) throws IOException, TextParseException {
			Row row = parseNextRow(true);
			if (row == null)
				return null;
			if (columns == null || columns.length < theColumnCount)
				columns = new String[theColumnCount];
			else if (theColumnCount < columns.length)
				Arrays.fill(columns, theColumnCount, columns.length, null);
			return row.getColumnText(columns);
		}

		@Override
		public boolean parseNextLine(String[] columns) throws IOException, TextParseException {
			if (columns.length != theColumnCount)
				throw new TextParseException("Sheet '" + theSheetName + "' has " + theColumnCount + " columns, not " + columns.length,
					theParser.getFilePosition());
			Row row = parseNextRow(true);
			if (row == null)
				return false;
			row.getColumnText(columns);
			return true;
		}

		@Override
		public int getPassedBlankLines() {
			return thePassedBlankLines;
		}

		@Override
		public int getEntryNumber() {
			if (theEncounteredRowCount == 0)
				return -1;
			return theNonEmptyRowCount;
		}

		@Override
		public int getLastLineNumber() {
			return theEncounteredRowCount;
		}

		@Override
		public long getLastLineOffset() {
			if (theLastRow == null)
				return -1;
			return theLastRow.getCells().get(0).getPosition().getPosition();
		}

		@Override
		public int getCurrentLineNumber() {
			if (theLastRow == null)
				return -1;
			else if (theLastRow.getRowIndex() + 1 == theRowCount)
				return theLastRow.getRowIndex();
			else
				return theLastRow.getRowIndex() + 1;
		}

		@Override
		public long getCurrentOffset() {
			return theParser.getPosition();
		}

		@Override
		public long getColumnOffset(int columnIndex) {
			if (theLastRow == null)
				return 0;
			Cell cell = theLastRow.getCells().get(columnIndex);
			if (cell.getPosition() != null)
				return cell.getPosition().getPosition();
			else
				return getLastLineOffset() + columnIndex;
		}

		@Override
		public void close() throws IOException {
			if (theFileInput != null)
				theFileInput.close();
			theFileInput = null;
			theParser = null;
		}

		@Override
		public String toString() {
			return theSheetName;
		}
	}

	/** Represents a row in a spreadsheet */
	public static class Row {
		private final int theRowIndex;
		private final List<Cell> theCells;

		Row(int rowIndex, Cell[] cells) {
			theRowIndex = rowIndex;
			theCells = BetterList.of(cells);
		}

		/** @return The index of this row within its sheet */
		public int getRowIndex() {
			return theRowIndex;
		}

		/** @return The cells in this row */
		public List<Cell> getCells() {
			return theCells;
		}

		/** @return True if this row has no cells with non-empty content */
		public boolean isEmpty() {
			for (Cell cell : theCells) {
				if (!cell.toString().isEmpty())
					return false;
			}
			return true;
		}

		/**
		 * @param columns The array to fill in
		 * @return The text of this row's columns
		 */
		public String[] getColumnText(String[] columns) {
			if (columns.length != theCells.size())
				throw new IllegalArgumentException("Wrong number of columns input: " + columns.length + " vs " + theCells.size());
			int i = 0;
			for (Cell cell : theCells)
				columns[i++] = cell.toString();
			return columns;
		}

		@Override
		public String toString() {
			return StringUtils.print(new StringBuilder(), ", ", theCells, (str, cell) -> cell.append(str)).toString();
		}
	}

	static final Map<String, CellType> CELL_TYPE_BY_SPEC_NAME = new HashMap<>();

	/** Recognized Excel cell types */
	public static enum CellType {
		/** A reference to a shared string */
		SharedString("s"),
		/** A string contained within the cell */
		InlineString("inlineStr"),
		/** A formula */
		Formula("str"),
		/** A boolean value */
		Boolean("b"),
		/** A date value */
		Date("d"),
		/** A number */
		Number("n"),
		/** Excel reported an error in the cell */
		Error("e"),
		/** An empty cell */
		Empty(null),
		/** The cell was missing */
		Missing(null),
		/** The cell's type is not specified */
		Unspecified(null),
		/** We don't recognize the cell's type */
		Unrecognized("?");

		/** The Excel name of this type, if applicable */
		public final String specName;

		private CellType(String specName) {
			this.specName = specName;
			if (specName != null)
				CELL_TYPE_BY_SPEC_NAME.put(specName, this);
		}

		/**
		 * @param t The Excel name of the cell type
		 * @return The cell type corresponding to the Excel name
		 */
		public static CellType getType(String t) {
			if (t == null)
				return Unspecified;
			CellType type = CELL_TYPE_BY_SPEC_NAME.get(t);
			if (type == null)
				return Unrecognized;
			return type;
		}
	}

	/** A cell within a row within a sheet within a spreadsheet file */
	public static class Cell {
		/** Missing cell */
		public static final Cell MISSING = new Cell(null, CellType.Missing, "");
		private final FilePosition thePosition;
		private final CellType theCellType;
		private final String theText;

		Cell(FilePosition position, CellType cellType, String value) {
			thePosition = position;
			theCellType = cellType;
			theText = value;
		}

		/** @return The position of the cell within the file */
		public FilePosition getPosition() {
			return thePosition;
		}

		/** @return The cell's type */
		public CellType getCellType() {
			return theCellType;
		}

		StringBuilder append(StringBuilder str) {
			return str.append(theText);
		}

		@Override
		public String toString() {
			return theText;
		}
	}

	/**
	 * <p>
	 * In XLSX files, strings may be stored in a separate archive entry from the sheet so multiple sheets in the file and multiple cells in
	 * each sheet may re-use the string without reproducing it.
	 * </p>
	 * <p>
	 * This parser provides access to the strings in the shared strings entry. The shared strings entry is read just far enough to discover
	 * the value for the requested reference. Shared strings are cached so the entry does not need to be read multiple times.
	 * </p>
	 */
	public static class SharedStrings {
		private InputStream theFileInput;
		private ComponentParser theParser;
		private final int theUniqueCount;
		private final List<String> theParsedStrings;

		private SharedStrings(InputStream fileInput, String path, MinML xmlParser) throws IOException, TextParseException {
			theFileInput = fileInput;
			theParser = xmlParser.parseByComponent(path, theFileInput);
			theParsedStrings = new ArrayList<>();
			theParser.startNextElement("sst", true);
			XmlAttribute uniqueCount = theParser.getAttribute("uniqueCount", true);
			try {
				theUniqueCount = Integer.parseInt(uniqueCount.getValueContent().toString());
			} catch (NumberFormatException e) {
				throw new TextParseException("Could not parse uniqueCount as a number: \"" + uniqueCount.getValueContent() + "\"",
					uniqueCount.getValueContent().getPosition(0));
			}
			if (theParser.startNextElement("si", false) == null)
				close(true);
		}

		/**
		 * @param source The text reference to a shared string
		 * @return The referenced shared string
		 * @throws IOException If the shared string entry could not be read
		 * @throws TextParseException If the shared string reference or the shared string entry could not be parsed
		 */
		public String getString(PositionedContent source) throws IOException, TextParseException {
			int index;
			try {
				index = Integer.parseInt(source.toString());
			} catch (NumberFormatException e) {
				throw new TextParseException("Could not parse shared string index as a number: \"" + source + "\"", source.getPosition(0));
			}
			return getString(index, source);
		}

		/**
		 * @param index The index of the shared string to get
		 * @param source The text reference to the shared string (for error throwing, if needed)
		 * @return The referenced shared string
		 * @throws IOException If the shared string entry could not be read
		 * @throws TextParseException If the shared string entry could not be read, or if no shared string exists for the given index
		 */
		public String getString(int index, PositionedContent source) throws IOException, TextParseException{
			if(index<0 || index>=theUniqueCount)
				throw new TextParseException("Illegal shared string reference '"+source+"': only "+theUniqueCount+" shared strings", source.getPosition(0));
			while(theParser!=null && index>=theParsedStrings.size()) {
				theParser.startNextElement("t", true);
				theParsedStrings.add(theParser.getElementContent(true).toString());
				theParser.closeCurrentElement();
				theParser.closeCurrentElement();
				if (theParser.startNextElement("si", false) == null)
					close(true);
			}
			if (index >= theParsedStrings.size()) // Ended prematurely
				throw new TextParseException("Shared string reference '"+source+"' cannot be met: only "+theParsedStrings.size()+" of "+theUniqueCount+" shared strings actually present", source.getPosition(0));
			return theParsedStrings.get(index);
		}

		void close(boolean atEnd) throws IOException {
			if (atEnd && theParsedStrings.size() != theUniqueCount)
				System.err.println("Encountered the end of " + theParser.getFileLocation() + " with only " + theParsedStrings.size()
					+ " of " + theUniqueCount + " strings encountered");
			if (theFileInput != null) {
				theFileInput.close();
				theFileInput = null;
				theParser = null;
			}
		}
	}

	private static final String XL = "xl/";
	private static final String WORKBOOK_PATH = XL + "workbook.xml";
	private static final String SHEETS_PATH = XL + "worksheets/";
	private static final String SHARED_STRINGS_PATH = XL + "sharedStrings.xml";

	private static FileDataSource ZIP_FILE_ROOT;

	private static FileDataSource getZipFileRoot() {
		// if (ZIP_FILE_ROOT == null)
			ZIP_FILE_ROOT = new ArchiveEnabledFileSource(new NativeFileSource())//
				.withArchival(new ArchiveEnabledFileSource.ZipCompression()//
				.withFileTest(new ArchiveEnabledFileSource.ExtensionTest("xlsx")));
		return ZIP_FILE_ROOT;
	}

	private final BetterFile theRoot;
	private final MinML theXmlParser;
	private final List<Sheet> theSheets;
	private final long theOverallFileLength;

	private SharedStrings theSharedStrings;

	private final Iterator<Sheet> theSheetIterator;
	private Sheet theCurrentSheet;
	private int theOverallEntryNumber;
	private int theOverallLineNumber;

	/**
	 * @param root The root entry for the XLSX archive
	 * @param multiSheet Directive for how to handle multi-sheet spreadsheet files
	 * @throws IOException If the file could not be opened or read sufficiently to recognize it
	 * @throws TextParseException If the file could not be parsed sufficiently to recognize it
	 */
	public XlsxParser(BetterFile root, MultipleSheetHandling multiSheet) throws IOException, TextParseException {
		if (root.getSource() != getZipFileRoot()) {
			root = getZipFileRoot().at(root.getPath());
		}
		theRoot = root;
		theXmlParser = new MinML();
		theSheets = new ArrayList<>();

		BetterFile workBook = theRoot.at(WORKBOOK_PATH);
		if (!workBook.exists())
			throw new IOException("No " + WORKBOOK_PATH + " found--not a valid XLSX file");
		long overallFileLength = 0;
		try (InputStream in = workBook.read()) {
			ComponentParser parser = theXmlParser.parseByComponent(WORKBOOK_PATH, in);
			parser.startNextElement("workbook", true);
			parser.startNextElement("sheets", true);
			String[] name = new String[1];
			sheetLoop: while (parser.startNextElement("sheet", false) != null) {
				if (!theSheets.isEmpty()) {
					switch (multiSheet) {
					case Error:
						throw new TextParseException("Multiple sheets found in workbook", parser.getFilePosition());
					case UseFirst:
						break sheetLoop;
					default:
						break;
					}
				}
				name[0] = null;
				PositionedContent id = parser.<XmlAttribute> parseUntil(component -> {
					if (isSheetClose(component))
						throw new TextParseException("Expected attribute 'sheetId'", component.getContent().getPosition(0));
					if (!(component instanceof XmlAttribute))
						return null;
					XmlAttribute attr = (XmlAttribute) component;
					switch (attr.getName()) {
					case "sheetId":
						return attr;
					case "name":
						name[0] = attr.getValueContent().toString();
						//$FALL-THROUGH$
					default:
						return null;
					}
				}).getValueContent();
				if (name[0] == null)
					name[0] = parser.getAttribute("name", true).getValueContent().toString();
				String sheetPath = SHEETS_PATH + "sheet" + id + ".xml";
				BetterFile sheetFile = theRoot.at(sheetPath);
				if (!sheetFile.exists())
					throw new TextParseException("Sheet '" + name[0] + "' (ID " + id + ") not found", id.getPosition(0));
				Sheet sheet = new Sheet(this, name[0], sheetFile.read(), sheetPath);
				theSheets.add(sheet);
				overallFileLength += sheet.getFileLength();
			}
		}
		theSheetIterator = theSheets.iterator();
		theOverallFileLength = overallFileLength;
	}

	/**
	 * @param file The XLSX file
	 * @param multiSheet Directive for how to handle multi-sheet spreadsheet files
	 * @throws IOException If the file could not be opened or read sufficiently to recognize it
	 * @throws TextParseException If the file could not be parsed sufficiently to recognize it
	 */
	public XlsxParser(File file, MultipleSheetHandling multiSheet) throws IOException, TextParseException {
		this(getZipFileRoot().at(file.getAbsolutePath()), multiSheet);
	}

	/** @return The XML parser for this spreadsheet */
	public MinML getXmlParser() {
		return theXmlParser;
	}

	/** @return The sheets in this spreadsheet file */
	public List<Sheet> getSheets() {
		return Collections.unmodifiableList(theSheets);
	}

	/**
	 * @return The shared strings accessor for this spreadsheet file
	 * @throws IOException If the shared strings archive entry could not be read
	 * @throws TextParseException If the shared strings archive entry could not be parsed sufficiently to recognize it
	 */
	public SharedStrings getSharedStrings() throws IOException, TextParseException {
		if (theSharedStrings == null) {
			BetterFile sharedStringsFile = theRoot.at(SHARED_STRINGS_PATH);
			if (!sharedStringsFile.exists())
				return null;
			theSharedStrings = new SharedStrings(sharedStringsFile.read(), SHARED_STRINGS_PATH, theXmlParser);
		}
		return theSharedStrings;
	}

	@Override
	public long getFileLength() {
		return theOverallFileLength;
	}

	@Override
	public long getParseProgress() {
		return getCurrentLineNumber();
	}

	@Override
	public String[] parseNextLineVC(String[] columns) throws IOException, TextParseException {
		getSheets(); // Initialize sheets if we haven't yet
		do {
			if (theCurrentSheet != null) {
				String[] line = theCurrentSheet.parseNextLineVC(columns);
				if (line != null)
					return line;
				theOverallEntryNumber += theCurrentSheet.getEntryNumber();
				theOverallLineNumber += theCurrentSheet.getLastLineNumber();
				theCurrentSheet.close();
				theCurrentSheet = null;
			}
			theCurrentSheet = theSheetIterator.hasNext() ? theSheetIterator.next() : null;
		} while (theCurrentSheet != null);
		return null;
	}

	@Override
	public boolean parseNextLine(String[] columns) throws IOException, TextParseException {
		do {
			if (theCurrentSheet != null) {
				if (theCurrentSheet.parseNextLine(columns))
					return true;
				theOverallEntryNumber += theCurrentSheet.getEntryNumber();
				theOverallLineNumber += theCurrentSheet.getLastLineNumber();
				theCurrentSheet.close();
				theCurrentSheet = null;
			}
			theCurrentSheet = theSheetIterator.hasNext() ? theSheetIterator.next() : null;
		} while (theCurrentSheet != null);
		return false;
	}

	@Override
	public int getPassedBlankLines() {
		return theCurrentSheet == null ? 0 : theCurrentSheet.getPassedBlankLines();
	}

	@Override
	public int getEntryNumber() {
		return theOverallEntryNumber + (theCurrentSheet == null ? 0 : theCurrentSheet.getEntryNumber());
	}

	@Override
	public int getLastLineNumber() {
		return theOverallLineNumber + (theCurrentSheet == null ? 0 : theCurrentSheet.getLastLineNumber());
	}

	@Override
	public long getLastLineOffset() {
		return theCurrentSheet == null ? 0 : theCurrentSheet.getLastLineOffset();
	}

	@Override
	public int getCurrentLineNumber() {
		return theOverallLineNumber + (theCurrentSheet == null ? 0 : theCurrentSheet.getCurrentLineNumber());
	}

	@Override
	public long getCurrentOffset() {
		return theCurrentSheet == null ? 0 : theCurrentSheet.getCurrentOffset();
	}

	@Override
	public long getColumnOffset(int columnIndex) {
		return theCurrentSheet == null ? 0 : theCurrentSheet.getColumnOffset(columnIndex);
	}

	@Override
	public void close() throws IOException {
		if (theCurrentSheet != null)
			theCurrentSheet.close();
		while (theSheetIterator.hasNext())
			theSheetIterator.next().close();
		if (theSharedStrings != null)
			theSharedStrings.close(false);
	}

	private static boolean isSheetClose(XmlComponent component) {
		if (!(component instanceof XmlElementTerminal))
			return false;
		XmlElementTerminal el = (XmlElementTerminal) component;
		return !el.isOpen() && el.getDepth() == 2;
	}

	static void checkTagName(PositionedContent tagName, String expected) throws TextParseException {
		if (!tagMatches(tagName, expected))
			throw new TextParseException("Expected '" + expected + "', not '" + tagName + "'", tagName.getPosition(0));
	}

	static boolean tagMatches(CharSequence tagName, String expected) {
		return tagName.length() >= expected.length()//
			&& expected.length() == StringUtils.subSequenceMatches(tagName, tagName.length() - expected.length(), expected, 0,
				expected.length(), false);
	}
}
