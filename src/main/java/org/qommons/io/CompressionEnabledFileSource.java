package org.qommons.io;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipInputStream;

import org.qommons.QommonsUtils;
import org.qommons.StringUtils;
import org.qommons.collect.QuickSet;
import org.qommons.collect.QuickSet.QuickMap;
import org.qommons.io.BetterFile.FileBacking;
import org.qommons.io.BetterFile.FileBooleanAttribute;
import org.qommons.io.FileUtils.DirectorySyncResults;
import org.qommons.threading.QommonsTimer;

import sun.nio.cs.ArrayDecoder;

public class CompressionEnabledFileSource implements BetterFile.FileDataSource {
	private final BetterFile.FileDataSource theWrapped;
	private final List<CompressionEnabledFileSource.FileCompression> theCompressions;
	private int theMaxZipDepth;

	public CompressionEnabledFileSource(BetterFile.FileDataSource wrapped) {
		theWrapped = wrapped;
		theCompressions = new ArrayList<>();
		theMaxZipDepth = 10;
	}

	public CompressionEnabledFileSource withCompression(CompressionEnabledFileSource.FileCompression compression) {
		theCompressions.add(compression);
		return this;
	}

	public int getMaxZipDepth() {
		return theMaxZipDepth;
	}

	public CompressionEnabledFileSource setMaxZipDepth(int maxZipDepth) {
		theMaxZipDepth = maxZipDepth;
		return this;
	}

	static File getNativeFile(BetterFile.FileBacking backing, boolean[] tempFile) {
		if (backing instanceof NativeFileSource.NativeFileBacking) {
			return ((NativeFileSource.NativeFileBacking) backing).getFile();
		} else {
			tempFile[0] = true;
			File file = null;
			try {
				file = java.io.File.createTempFile(FileUtils.class.getName(), "zipTemp");
			} catch (IOException e) {
				return null;
			}
			try (InputStream in = new BufferedInputStream(backing.read());
				OutputStream out = new BufferedOutputStream(new FileOutputStream(file))) {
				byte[] buffer = new byte[64 * 1024];
				int read = in.read(buffer);
				while (read >= 0) {
					out.write(buffer, 0, read);
					read = in.read(buffer);
				}
			} catch (IOException e) {
				file.delete();
				file = null;
				return null;
			}
			return file;
		}
	}

	boolean isCompressed(BetterFile.FileBacking backing) {
		for (CompressionEnabledFileSource.FileCompression compression : theCompressions) {
			if (compression.detectPossibleCompressedFile(backing.getName()) && backing.exists()
				&& !backing.get(FileBooleanAttribute.Directory)) {
				try {
					if (compression.detectCompressedFile(backing))
						return true;
				} catch (IOException e) {}
			}
		}
		return false;
	}

	CompressedFileData getCompressed(BetterFile.FileBacking backing) {
		File file = null;
		boolean[] tempFile = new boolean[1];
		for (CompressionEnabledFileSource.FileCompression compression : theCompressions) {
			if (compression.detectPossibleCompressedFile(backing.getName()) && backing.exists()
				&& !backing.get(FileBooleanAttribute.Directory)) {
				if (file == null) {
					file = getNativeFile(backing, tempFile);
					if (file == null)
						return null;
				}
				try {
					CompressionEnabledFileSource.CompressedFile root = compression.parseStructure(file);
					return new CompressedFileData(backing, compression, root, file, tempFile[0]);
				} catch (IOException e) {
					System.err.print(""); // TODO DEBUG
				}
			}
		}
		return null;
	}

	@Override
	public List<FileBacking> getRoots() {
		return QommonsUtils.map(theWrapped.getRoots(), r -> new CompressionEnabledFileBacking(null, r), true);
	}

	class CompressionEnabledFileBacking implements BetterFile.FileBacking {
		private final CompressionEnabledFileBacking theParent;
		private final BetterFile.FileBacking theBacking;
		private final CompressedFileData theZipData;
		private final int theZipDepth;

		CompressionEnabledFileBacking(CompressionEnabledFileBacking parent, BetterFile.FileBacking wrapped) {
			theParent = parent;
			theBacking = wrapped;
			if (theParent == null || theParent.getZipDepth() < theMaxZipDepth)
				theZipData = getCompressed(wrapped);
			else
				theZipData = null;
			if (theParent == null)
				theZipDepth = 0;
			else if (theZipData == null)
				theZipDepth = theParent.theZipDepth;
			else
				theZipDepth = theParent.theZipDepth + 1;
		}

		public int getZipDepth() {
			return theZipDepth;
		}

		@Override
		public String getName() {
			return theBacking.getName();
		}

		@Override
		public boolean check() {
			if (!theBacking.check())
				return false;
			if (theZipData != null && !theZipData.check())
				return false;
			else if (theZipData == null && (theParent == null || theParent.getZipDepth() < theMaxZipDepth))
				return !isCompressed(theBacking);
			return true;
		}

		@Override
		public boolean isRoot() {
			return theBacking.isRoot();
		}

		@Override
		public boolean exists() {
			return theBacking.exists();
		}

		@Override
		public long getLastModified() {
			return theBacking.getLastModified();
		}

		@Override
		public boolean get(FileBooleanAttribute attribute) {
			if (attribute == FileBooleanAttribute.Directory && theZipData != null)
				return true;
			return theBacking.get(attribute);
		}

		@Override
		public List<? extends FileBacking> listFiles() {
			if (theZipData != null)
				return QommonsUtils.map(theZipData.getRoot().listFiles(), f -> new CompressionEnabledFileBacking(this, f), true);
			List<? extends FileBacking> list = theBacking.listFiles();
			if (list == null)
				return null;
			return QommonsUtils.map(list, f -> new CompressionEnabledFileBacking(this, f), true);
		}

		@Override
		public FileBacking getChild(String fileName) {
			if (theZipData != null) {
				FileBacking f = theZipData.getRoot().getChild(fileName);
				return f == null ? null : new CompressionEnabledFileBacking(this, f);
			}
			FileBacking f = theBacking.getChild(fileName);
			return f == null ? null : new CompressionEnabledFileBacking(this, f);
		}

		@Override
		public long length() {
			if (theZipData != null)
				return 0;
			return theBacking.length();
		}

		@Override
		public InputStream read() throws IOException {
			return theBacking.read();
		}

		@Override
		public BetterFile.FileBacking createChild(String fileName, boolean directory) throws IOException {
			if (theZipData != null)
				return new CompressionEnabledFileBacking(this, theZipData.getRoot().createChild(fileName, directory));
			FileBacking f = theBacking.createChild(fileName, directory);
			return f == null ? null : new CompressionEnabledFileBacking(this, f);
		}

		@Override
		public boolean delete(DirectorySyncResults results) {
			return false;
		}

		@Override
		public OutputStream write() throws IOException {
			throw new IOException("Cannot overwrite zip entries");
		}

		@Override
		public boolean set(BetterFile.FileBooleanAttribute attribute, boolean value, boolean ownerOnly) {
			return get(attribute) != value;
		}

		@Override
		public boolean setLastModified(long lastModified) {
			return false;
		}

		@Override
		public int hashCode() {
			return theBacking.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof CompressionEnabledFileBacking && theBacking.equals(((CompressionEnabledFileBacking) obj).theBacking);
		}

		@Override
		public String toString() {
			return theBacking.toString();
		}
	}

	static final long FILE_CHECK_INTERVAL = 10;

	class CompressedFileData {
		private final BetterFile.FileBacking theSource;
		private final CompressionEnabledFileSource.FileCompression theCompression;
		private final long theLastModified;
		private long theLastCheck;
		private final CompressionEnabledFileSource.CompressedFile theRootEntry;
		private File theExtractedFile;
		private final boolean isTempFile;
		private final AtomicInteger isInUse;

		CompressedFileData(BetterFile.FileBacking source, CompressionEnabledFileSource.FileCompression compression,
			CompressionEnabledFileSource.CompressedFile root, File extractedFile, boolean tempFile) {
			theSource = source;
			theCompression = compression;
			theLastModified = source.getLastModified();
			theLastCheck = System.currentTimeMillis();
			theRootEntry = root;
			isInUse = new AtomicInteger();
			theExtractedFile = extractedFile;
			this.isTempFile = tempFile;
			destroyTempData();
		}

		boolean check() {
			if (!theSource.check())
				return false;
			long now = System.currentTimeMillis();
			if (now - theLastCheck > FILE_CHECK_INTERVAL) {
				if (theLastModified != theSource.getLastModified())
					return false;
				theLastCheck = now;
			}
			return true;
		}

		BetterFile.FileBacking getRoot() {
			return new ZippedFileBacking(this, theRootEntry);
		}

		InputStream read(CompressionEnabledFileSource.CompressedFile entry) throws IOException {
			return wrap(theCompression.read(extractTempData(), entry));
		}

		File extractTempData() {
			isInUse.getAndIncrement();
			File file = theExtractedFile;
			if (!file.exists()) {
				try {
					file = java.io.File.createTempFile(FileUtils.class.getName(), "zipTemp");
				} catch (IOException e) {
					return null;
				}
				theExtractedFile = file;
				try (InputStream in = new BufferedInputStream(theSource.read());
					OutputStream out = new BufferedOutputStream(new FileOutputStream(file))) {
					byte[] buffer = new byte[64 * 1024];
					int read = in.read(buffer);
					while (read >= 0) {
						out.write(buffer, 0, read);
						read = in.read(buffer);
					}
				} catch (IOException e) {
					file.delete();
					return null;
				}
			}
			return file;
		}

		void destroyTempData() {
			if (!isTempFile)
				return;
			QommonsTimer.getCommonInstance().doAfterInactivity(this, () -> {
				if (isInUse.get() > 0) {
					destroyTempData();
					return;
				}
				java.io.File file = theExtractedFile;
				if (file.exists())
					file.delete();
			}, 1000);
		}

		InputStream wrap(InputStream compressedIS) {
			return new InputStream() {
				private boolean isClosed;

				@Override
				public int read() throws IOException {
					int read = compressedIS.read();
					if (read < 0)
						done();
					return read;
				}

				@Override
				public int read(byte[] b) throws IOException {
					int read = compressedIS.read(b);
					if (read < 0)
						done();
					return read;
				}

				@Override
				public int read(byte[] b, int off, int len) throws IOException {
					int read = compressedIS.read(b, off, len);
					if (read < 0)
						done();
					return read;
				}

				@Override
				public long skip(long n) throws IOException {
					return compressedIS.skip(n);
				}

				@Override
				public int available() throws IOException {
					return compressedIS.available();
				}

				@Override
				public void close() throws IOException {
					done();
					compressedIS.close();
				}

				@Override
				public synchronized void mark(int readlimit) {
					compressedIS.mark(readlimit);
				}

				@Override
				public synchronized void reset() throws IOException {
					compressedIS.reset();
				}

				@Override
				public boolean markSupported() {
					return compressedIS.markSupported();
				}

				@Override
				protected void finalize() throws Throwable {
					done();
					super.finalize();
				}

				private void done() {
					if (!isClosed) {
						isClosed = true;
						if (isInUse.decrementAndGet() == 0)
							destroyTempData();
					}
				}
			};
		}
	}

	class ZippedFileBacking implements BetterFile.FileBacking {
		private final CompressedFileData theZipData;
		private final CompressionEnabledFileSource.CompressedFile theEntry;

		ZippedFileBacking(CompressedFileData zipData, CompressionEnabledFileSource.CompressedFile entry) {
			theZipData = zipData;
			theEntry = entry;
		}

		@Override
		public boolean check() {
			return theZipData.check();
		}

		@Override
		public boolean isRoot() {
			return false;
		}

		@Override
		public boolean exists() {
			return check();
		}

		@Override
		public long getLastModified() {
			if (!theZipData.check())
				return 0;
			return theEntry.getLastModified();
		}

		@Override
		public boolean get(BetterFile.FileBooleanAttribute attribute) {
			if (!theZipData.check())
				return false;
			switch (attribute) {
			case Directory:
				return theEntry.isDirectory();
			case Hidden:
				return false;
			case Readable:
				return true;
			case Writable:
				return false;
			case Symbolic:
				return false;
			}
			throw new IllegalStateException("" + attribute);
		}

		@Override
		public long length() {
			if (!theZipData.check())
				return 0;
			return theEntry.length();
		}

		@Override
		public InputStream read() throws IOException {
			if (!theZipData.check())
				throw new FileNotFoundException("Data may have changed");
			return theZipData.read(theEntry);
		}

		@Override
		public String getName() {
			return theEntry.getName();
		}

		@Override
		public List<? extends BetterFile.FileBacking> listFiles() {
			List<? extends CompressionEnabledFileSource.CompressedFile> list = theEntry.listFiles();
			return list == null ? null : QommonsUtils.map(list, f -> new ZippedFileBacking(theZipData, f), true);
		}

		@Override
		public BetterFile.FileBacking getChild(String fileName) {
			CompressionEnabledFileSource.CompressedFile file = theEntry.getFile(fileName);
			if (file != null)
				return new ZippedFileBacking(theZipData, file);
			return new DanglingZipEntry(this, fileName);
		}

		@Override
		public BetterFile.FileBacking createChild(String fileName, boolean directory) throws IOException {
			for (CompressionEnabledFileSource.CompressedFile file : theEntry.listFiles()) {
				if (file.getName().equals(fileName))
					return new ZippedFileBacking(theZipData, file);
			}
			throw new IOException("Cannot create zip entries");
		}

		@Override
		public boolean delete(DirectorySyncResults results) {
			return false;
		}

		@Override
		public OutputStream write() throws IOException {
			throw new IOException("Cannot overwrite zip entries");
		}

		@Override
		public boolean set(BetterFile.FileBooleanAttribute attribute, boolean value, boolean ownerOnly) {
			return get(attribute) != value;
		}

		@Override
		public boolean setLastModified(long lastModified) {
			return false;
		}
	}

	class DanglingZipEntry implements BetterFile.FileBacking {
		private final ZippedFileBacking theAncestor;
		private final String theName;

		DanglingZipEntry(ZippedFileBacking ancestor, String name) {
			theAncestor = ancestor;
			theName = name;
		}

		@Override
		public boolean check() {
			return theAncestor.check();
		}

		@Override
		public boolean isRoot() {
			return false;
		}

		@Override
		public boolean exists() {
			return false;
		}

		@Override
		public long getLastModified() {
			return 0;
		}

		@Override
		public boolean get(BetterFile.FileBooleanAttribute attribute) {
			return false;
		}

		@Override
		public long length() {
			return 0;
		}

		@Override
		public InputStream read() throws IOException {
			throw new FileNotFoundException("No such entry");
		}

		@Override
		public String getName() {
			return theName;
		}

		@Override
		public List<? extends BetterFile.FileBacking> listFiles() {
			return null;
		}

		@Override
		public BetterFile.FileBacking getChild(String fileName) {
			return new DanglingZipEntry(theAncestor, fileName);
		}

		@Override
		public BetterFile.FileBacking createChild(String fileName, boolean directory) throws IOException {
			throw new IOException("Cannot create zip entries this way");
		}

		@Override
		public boolean delete(DirectorySyncResults results) {
			return true;
		}

		@Override
		public OutputStream write() throws IOException {
			throw new IOException("Cannot create zip entries this way");
		}

		@Override
		public boolean set(BetterFile.FileBooleanAttribute attribute, boolean value, boolean ownerOnly) {
			return !value;
		}

		@Override
		public boolean setLastModified(long lastModified) {
			return false;
		}
	}

	public interface CompressedFile {
		String getName();

		long length();

		long getLastModified();

		boolean isDirectory();

		List<? extends CompressedFile> listFiles();

		CompressedFile getFile(String name);
	}

	public interface FileCompression {
		boolean detectPossibleCompressedFile(String fileName);

		boolean detectCompressedFile(FileBacking file) throws IOException;

		CompressedFile parseStructure(java.io.File file) throws IOException;

		InputStream read(java.io.File file, CompressedFile entry) throws IOException;
	}

	public static class ZipCompression implements FileCompression {
		@Override
		public boolean detectPossibleCompressedFile(String fileName) {
			int dotIdx = fileName.lastIndexOf('.');
			if (dotIdx < 0)
				return false;
			String ext = fileName.substring(dotIdx + 1).toLowerCase();
			switch (ext) {
			case "zip":
			case "jar":
				return true;
			default:
				return false;
			}
		}

		@Override
		public boolean detectCompressedFile(FileBacking file) throws IOException {
			try (InputStream in = file.read()) {
				return in.read() == 'P' && in.read() == 'K';
			}
		}

		@Override
		public CompressedFile parseStructure(java.io.File file) throws IOException {
			try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
				byte[] buffer = new byte[(int) Math.min(file.length(), 128)];
				long seekPos = file.length() - buffer.length;
				raf.seek(seekPos);
				fillBuffer(raf, buffer, 0, buffer.length);
				EndOfCentralDirectoryRecord eocd = findEOCD(buffer);
				while (seekPos > 0 && eocd == null) {
					int move = buffer.length - 20;
					if (move > seekPos)
						move = (int) seekPos;
					System.arraycopy(buffer, 0, buffer, move, buffer.length - move);
					seekPos -= move;
					raf.seek(seekPos);
					fillBuffer(raf, buffer, 0, move);
					eocd = findEOCD(buffer);
				}
				if (eocd == null)
					throw new IOException("Not actually a ZIP file");

				return new CDRReader(eocd, raf).readCDR();
			}
		}

		static void fillBuffer(RandomAccessFile raf, byte[] buffer, int offset, int length) throws IOException {
			int read = raf.read(buffer, offset, length);
			while (read >= 0 && read < length) {
				offset += read;
				length -= read;
				read = raf.read(buffer, offset, length);
			}
			if (read < 0)
				throw new EOFException();
		}

		static EndOfCentralDirectoryRecord findEOCD(byte[] buffer) {
			int eocd = -1;
			for (int i = buffer.length - 20; i >= 0; i--) {
				if (buffer[i] == 0x50 && buffer[i + 1] == 0x4b && buffer[i + 2] == 0x05 && buffer[i + 3] == 0x06) {
					eocd = i;
					break;
				}
			}
			if (eocd < 0)
				return null;
			return new EndOfCentralDirectoryRecord(getInt(buffer, eocd + 4), //
				getInt(buffer, eocd + 8), //
				getLong(buffer, eocd + 12), //
				getLong(buffer, eocd + 16));
		}

		static class CDRReader {
			private final EndOfCentralDirectoryRecord theEnd;
			private final RandomAccessFile theFile;
			private long theCdrOffset;
			private int theBufferOffset;

			CDRReader(EndOfCentralDirectoryRecord end, RandomAccessFile file) {
				theEnd = end;
				theFile = file;
			}

			CompressedFile readCDR() throws IOException {
				theFile.seek(theEnd.centralDirOffset);
				byte[] buffer = new byte[(int) Math.min(64 * 1028, theEnd.centralDirSize)];
				fillBuffer(theFile, buffer, 0, buffer.length);
				BuildingEntry root = new BuildingEntry("", 0, true, 0, 0, 0);
				CharsetDecoder utfDecoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT);
				for (int i = 0; i < theEnd.centralDirCount; i++) {
					if (buffer[theBufferOffset] != 0x50 || buffer[theBufferOffset + 1] != 0x4b || buffer[theBufferOffset + 2] != 0x01
						|| buffer[theBufferOffset + 3] != 0x02)
						throw new IOException("Could not read Zip file");
					int nameLen = getInt(buffer, theBufferOffset + 28);
					int extraLen = getInt(buffer, theBufferOffset + 30);
					int commentLen = getInt(buffer, theBufferOffset + 32);
					int disk = getInt(buffer, 34);
					if (disk != theEnd.diskNumber) {
						advance(buffer, 46 + nameLen + extraLen + commentLen);
						continue;
					}
					int flag = getInt(buffer, theBufferOffset + 8);
					long modTime = getLong(buffer, theBufferOffset + 12);
					long lastMod = extendedDosToJavaTime(modTime);
					long compressedSize = getLong(buffer, theBufferOffset + 20);
					long uncompressedSize = getLong(buffer, theBufferOffset + 24);
					long position = getLong(buffer, theBufferOffset + 42);
					String name;
					if ((flag & UTF_FLAG) != 0)
						name = readUtf(buffer, theBufferOffset + 46, nameLen, utfDecoder);
					else
						name = read(buffer, theBufferOffset + 46, nameLen);
					String[] path = name.split("/");
					BuildingEntry entry = root;
					for (int p = 0; p < path.length - 1; p++) {
						String pe = path[p];
						entry = entry.theChildren.computeIfAbsent(pe, __ -> new BuildingEntry(pe, 0, true, 0, 0, 0));
					}
					if (name.charAt(name.length() - 1) == '/') {
						entry.theChildren.compute(path[path.length - 1], (k, old) -> {
							if (old == null)
								old = new BuildingEntry(path[path.length - 1], lastMod, true, position, compressedSize, uncompressedSize);
							else
								old.theLM = lastMod;
							return old;
						});
					} else {
						@SuppressWarnings("unused")
						BuildingEntry old = entry.theChildren.put(path[path.length - 1],
							new BuildingEntry(path[path.length - 1], lastMod, false, position, compressedSize, uncompressedSize));
						// Some zips just have duplicates, I guess. Confirmed with other apps. Commenting this out.
						// if (old != null)
						// System.err.println("Duplicate entries: " + name);
					}

					try {
						advance(buffer, 46 + nameLen + extraLen + commentLen);
					} catch (RuntimeException | IOException e) {
						throw e;
					}
				}
				return root.build();
			}

			void advance(byte[] buffer, int recordLength) throws IOException {
				theBufferOffset += recordLength;
				theCdrOffset += recordLength;
				boolean readMore;
				if (theBufferOffset >= buffer.length - 46)
					readMore = true;
				else {
					int nameLen = getInt(buffer, theBufferOffset + 28);
					int extraLen = getInt(buffer, theBufferOffset + 30);
					int commentLen = getInt(buffer, theBufferOffset + 32);
					readMore = theBufferOffset > buffer.length - 46 - nameLen - extraLen - commentLen;
				}
				if (readMore) {
					int remaining = buffer.length - theBufferOffset;
					System.arraycopy(buffer, theBufferOffset, buffer, 0, remaining);
					int needed = (int) Math.min(buffer.length - remaining, theEnd.centralDirSize - theCdrOffset - remaining);
					if (needed > 0)
						fillBuffer(theFile, buffer, remaining, needed);
					theBufferOffset = 0;
				}
			}
		}

		private static final int UTF_FLAG = 0x800;

		private static String read(byte[] buffer, int offset, int length) {
			char[] ch = new char[length];
			for (int i = 0; i < length; i++)
				ch[i] = (char) buffer[offset + i];
			return new String(ch);
		}

		private static String readUtf(byte[] buffer, int offset, int length, CharsetDecoder cd) {
			cd.reset();
			int len = (int) (length * cd.maxCharsPerByte());
			char[] ca = new char[len];
			if (len == 0)
				return new String(ca);
			// UTF-8 only for now. Other ArrayDeocder only handles
			// CodingErrorAction.REPLACE mode. ZipCoder uses
			// REPORT mode.
			if (cd instanceof ArrayDecoder) {
				int clen = ((ArrayDecoder) cd).decode(buffer, offset, length, ca);
				if (clen == -1) // malformed
					throw new IllegalArgumentException("MALFORMED");
				return new String(ca, 0, clen);
			}
			ByteBuffer bb = ByteBuffer.wrap(buffer, offset, length);
			CharBuffer cb = CharBuffer.wrap(ca);
			CoderResult cr = cd.decode(bb, cb, true);
			if (!cr.isUnderflow())
				throw new IllegalArgumentException(cr.toString());
			cr = cd.flush(cb);
			if (!cr.isUnderflow())
				throw new IllegalArgumentException(cr.toString());
			return new String(ca, 0, cb.position());
		}

		private static int getInt(byte[] buffer, int offset) {
			return unsigned(buffer[offset]) + unsigned(buffer[offset + 1]) * 256;
		}

		private static int unsigned(byte b) {
			if (b < 0)
				return b + 256;
			else
				return b;
		}

		private static long getLong(byte[] buffer, int offset) {
			return unsigned(buffer[offset])//
				+ unsigned(buffer[offset + 1]) * 256L//
				+ unsigned(buffer[offset + 2]) * 256L * 256L//
				+ unsigned(buffer[offset + 3]) * 256L * 256L * 256L * 256L;
		}

		/**
		 * Converts DOS time to Java time (number of milliseconds since epoch).
		 */
		private static long dosToJavaTime(long dtime) {
			@SuppressWarnings("deprecation") // Use of date constructor.
			Date d = new Date((int) (((dtime >> 25) & 0x7f) + 80), (int) (((dtime >> 21) & 0x0f) - 1), (int) ((dtime >> 16) & 0x1f),
				(int) ((dtime >> 11) & 0x1f), (int) ((dtime >> 5) & 0x3f), (int) ((dtime << 1) & 0x3e));
			return d.getTime();
		}

		/**
		 * Converts extended DOS time to Java time, where up to 1999 milliseconds might be encoded into the upper half of the returned long.
		 *
		 * @param xdostime the extended DOS time value
		 * @return milliseconds since epoch
		 */
		static long extendedDosToJavaTime(long xdostime) {
			long time = dosToJavaTime(xdostime);
			return time + (xdostime >> 32);
		}

		@Override
		public InputStream read(java.io.File file, CompressedFile entry) throws IOException {
			RandomAccessFile raf = null;
			ZipInputStream zip = null;
			try {
				raf = new RandomAccessFile(file, "r");
				zip = new ZipInputStream(new RandomAccessInputStream(raf));
				raf.seek(((ZipFileEntry) entry).getPosition());
				zip.getNextEntry();

				return new WrappedEntryStream(raf, zip);
			} catch (IOException e) {
				if (zip != null)
					zip.close();
				if (raf != null)
					raf.close();
				throw e;
			}
		}

		static class EndOfCentralDirectoryRecord {
			final int diskNumber;
			final int centralDirCount;
			final long centralDirSize;
			final long centralDirOffset;

			EndOfCentralDirectoryRecord(int diskNumber, int centralDirCount, long centralDirSize, long centralDirOffset) {
				this.diskNumber = diskNumber;
				this.centralDirCount = centralDirCount;
				this.centralDirSize = centralDirSize;
				this.centralDirOffset = centralDirOffset;
			}
		}

		static class BuildingEntry {
			final String theName;
			final long theSize;
			long theLM;
			final long thePosition;
			final long theCompressedSize;
			final Map<String, BuildingEntry> theChildren;

			BuildingEntry(String name, long lastModified, boolean dir, long position, long compressedSize, long size) {
				theName = name;
				thePosition = position;
				theCompressedSize = compressedSize;
				if (dir) {
					theChildren = new LinkedHashMap<>();
					theSize = theLM = -1;
				} else {
					theChildren = null;
					theSize = size;
					theLM = lastModified;
				}
			}

			ZipFileEntry build() {
				return new ZipFileEntry(this);
			}
		}

		static class ZipFileEntry implements CompressedFile {
			private final String theName;
			private final long theSize;
			private final long theLastModified;
			private final long thePosition;
			private final QuickMap<String, ZipFileEntry> theChildren;

			ZipFileEntry(BuildingEntry entry) {
				theName = entry.theName;
				theSize = entry.theSize;
				theLastModified = entry.theLM;
				thePosition = entry.thePosition;
				if (entry.theChildren == null)
					theChildren = null;
				else {
					QuickMap<String, ZipFileEntry> children = QuickSet.of(StringUtils.DISTINCT_NUMBER_TOLERANT, entry.theChildren.keySet())
						.createMap();
					for (int i = 0; i < children.keySize(); i++)
						children.put(i, entry.theChildren.get(children.keySet().get(i)).build());
					theChildren = children.unmodifiable();
				}
			}

			long getPosition() {
				return thePosition;
			}

			@Override
			public String getName() {
				return theName;
			}

			@Override
			public long length() {
				return theSize;
			}

			@Override
			public long getLastModified() {
				return theLastModified;
			}

			@Override
			public boolean isDirectory() {
				return theChildren != null;
			}

			@Override
			public List<? extends CompressedFile> listFiles() {
				return theChildren == null ? null : theChildren.allValues();
			}

			@Override
			public CompressedFile getFile(String name) {
				return theChildren.getIfPresent(name);
			}

			@Override
			public String toString() {
				return theName;
			}
		}

		static class WrappedEntryStream extends InputStream {
			private final RandomAccessFile theFile;
			private final InputStream theWrapped;

			WrappedEntryStream(RandomAccessFile file, InputStream wrapped) {
				theFile = file;
				theWrapped = wrapped;
			}

			@Override
			public int read() throws IOException {
				return theWrapped.read();
			}

			@Override
			public int read(byte[] b) throws IOException {
				return theWrapped.read(b);
			}

			@Override
			public int read(byte[] b, int off, int len) throws IOException {
				return theWrapped.read(b, off, len);
			}

			@Override
			public long skip(long n) throws IOException {
				return theWrapped.skip(n);
			}

			@Override
			public int available() throws IOException {
				return theWrapped.available();
			}

			@Override
			public void close() throws IOException {
				theWrapped.close();
				theFile.close();
			}

			@Override
			public synchronized void mark(int readlimit) {
				theWrapped.mark(readlimit);
			}

			@Override
			public synchronized void reset() throws IOException {
				theWrapped.reset();
			}

			@Override
			public boolean markSupported() {
				return theWrapped.markSupported();
			}
		}
	}

	static class RandomAccessInputStream extends InputStream {
		private final RandomAccessFile theFile;

		RandomAccessInputStream(RandomAccessFile file) {
			theFile = file;
		}

		@Override
		public int read() throws IOException {
			return theFile.read();
		}

		@Override
		public int read(byte[] b) throws IOException {
			return theFile.read(b);
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			return theFile.read(b, off, len);
		}

		@Override
		public long skip(long n) throws IOException {
			return theFile.skipBytes((int) n);
		}
	}
}