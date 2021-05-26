package org.qommons.osgi;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.jdom2.Element;
import org.qommons.ArgumentParsing2;
import org.qommons.Named;
import org.qommons.Range;
import org.qommons.StringUtils;
import org.qommons.Version;
import org.qommons.collect.BetterHashSet;
import org.qommons.collect.CollectionElement;
import org.qommons.config.QommonsConfig;
import org.qommons.ex.CheckedExceptionWrapper;
import org.qommons.io.ArchiveEnabledFileSource;
import org.qommons.io.BetterFile;
import org.qommons.io.CircularByteBuffer;
import org.qommons.io.NativeFileSource;

/** A utility capable of loading classes according to the OSGi specification */
public class OsgiBundleSet {
	/** The path to the manifest file in a bundle */
	public static final String MANIFEST_PATH = "META-INF/MANIFEST.MF";

	/** An item that has (or may have) a name and a version */
	public interface VersionedItem extends Named {
		/** @return The item's version */
		Version getVersion();
	}

	/** Represents an OSGi bundle */
	public static class Bundle extends URLClassLoader implements VersionedItem {
		private static class LoadedClass {
			final Class<?> clazz;
			final Bundle owner;

			LoadedClass(Class<?> clazz, Bundle owner) {
				this.clazz = clazz;
				this.owner = owner;
			}

			@Override
			public int hashCode() {
				return clazz.getName().hashCode();
			}

			@Override
			public boolean equals(Object obj) {
				return obj instanceof LoadedClass && clazz == ((LoadedClass) obj).clazz;
			}

			@Override
			public String toString() {
				return clazz.getName();
			}
		}

		private final OsgiBundleSet theBundleSet;
		private final BetterFile theBundle;
		private final OsgiManifest theManifest;
		private final String theName;
		private final Version theVersion;
		private final List<Bundle> theImportedBundles;
		private final BitSet theReExportedBundles;
		private final Set<ExportedPackage> theExportedPackages;
		private boolean packagesCompiled;
		private final Map<String, ExportedPackage> thePackagesByName;
		private final Map<String, ResourceSource> theClassPath;
		private final Map<String, ResourceSource> theAvailablePackages;
		private final BetterHashSet<LoadedClass> theLoadedClasses;
		private final Set<String> theLoadedPackages;
		private final Map<String, BetterFile> theNativeLibraries;
		private long theLastModified;

		/**
		 * @param bundleSet The bundle set that is loading this bundle
		 * @param bundle The directory containing the bundle
		 * @param nativeDir The directory containing previously-exported (via {@link #exportJar(File, BetterFile, boolean, Set, Set)})
		 *        native libraries, or null to use the ones embedded in the bundle
		 * @param manifest The manifest of the bundle
		 */
		public Bundle(OsgiBundleSet bundleSet, BetterFile bundle, BetterFile nativeDir, OsgiManifest manifest) {
			super(new URL[0]);
			theBundleSet=bundleSet;
			theLastModified = bundle.at(MANIFEST_PATH).getLastModified();
			theName = manifest.get("Bundle-SymbolicName", true).getValue();
			theVersion = Version.parse(manifest.get("Bundle-Version", true).getValue());
			theBundle = bundle;
			theManifest = manifest;
			theImportedBundles = new ArrayList<>();
			theReExportedBundles = new BitSet();
			theExportedPackages = new LinkedHashSet<>();
			thePackagesByName = new HashMap<>();
			theClassPath = new HashMap<>();
			theAvailablePackages = new HashMap<>();
			theLoadedClasses = BetterHashSet.build().unsafe().buildSet();
			theLoadedPackages = new HashSet<>();
			theNativeLibraries = new LinkedHashMap<>();

			// Compile the packages sourced and exported from this bundle
			for (OsgiManifest.ManifestEntry entry : manifest.getAll("Export-Package")) {
				if (entry.getValue().equals(".")) {
					continue; // I don't know what this means
				}
				String version = entry.getAttributes().get("version");
				Version v = version == null ? null : Version.parse(version);
				String pkgName = entry.getValue().replaceAll("\\.", "/");
				ExportedPackage exPkg = new ExportedPackage(this, pkgName, v);
				theExportedPackages.add(exPkg);
				thePackagesByName.put(pkgName, exPkg);
			}

			// Compile the bundle's classpath
			StringBuilder pkgPath = new StringBuilder();
			// Always check the bundle's output for packages
			BetterFile cpFile = theBundle.at(".classpath");
			if (cpFile.exists()) {
				try {
					Element cp = QommonsConfig.getRootElement(cpFile.read());
					for (Element el : cp.getChildren("classpathentry")) {
						if ("output".equals(el.getAttributeValue("kind"))) {
							findPackages(//
								theBundle.at(el.getAttributeValue("path")), pkgPath);
						}
					}
				} catch (IOException e) {
					System.err.println(toString() + ": Could not read classpath for output");
					e.printStackTrace();
				}
			} else { // No classpath, assume it's a jar export or something where the classes are rooted at the bundle level
				findPackages(theBundle, pkgPath);
			}
			for (OsgiManifest.ManifestEntry entry : theManifest.getAll("Bundle-ClassPath")) {
				BetterFile location;
				if (entry.getValue().equals(".")) {
					location = theBundle;
				} else {
					location = theBundle.at(entry.getValue());
				}
				findPackages(location, pkgPath);
			}
			for (ExportedPackage pkg : theExportedPackages) {
				if (pkg.getExporter() == this && !theClassPath.containsKey(pkg.getName())) {
					System.err.println(toString() + ": Exported package " + pkg.getName() + " not found");
				}
			}
			theAvailablePackages.putAll(theClassPath);

			// Add native libraries
			if (!theManifest.getAll("Bundle-NativeCode").isEmpty()) {
				String os = System.getProperty("os.name").toLowerCase();
				if (os.startsWith("lin")) {
					os = "linux";
				} else if (os.startsWith("win")) {
					os = "win32";
				} else {
					os = null;
				}
				if (os != null) {
					String processor = System.getProperty("os.arch").toLowerCase();
					switch (processor) {
					case "amd":
						processor = "x86";
						break;
					case "amd64":
						processor = "x86-64";
						break;
					}
					for (OsgiManifest.ManifestEntry entry : theManifest.getAll("Bundle-NativeCode")) {
						String entryOS = entry.getAttributes().get("osname");
						if (entryOS != null && !entryOS.equals(os)) {
							continue;
						}
						String entryArch = entry.getAttributes().get("processor");
						if (entryArch != null && !entryArch.equals(processor)) {
							continue;
						}
						for (String path : entry.getValue().split(";")) {
							BetterFile file = null;
							if (nativeDir != null) {
								file = nativeDir.at(toString() + "/" + path);
							}
							if (file == null || !file.exists()) {
								file=theBundle.at(path);
							}
							int lastDot = file.getName().lastIndexOf('.');
							String libName = lastDot < 0 ? file.getName() : file.getName().substring(0, lastDot);
							if (libName.startsWith("lib") && os.equals("linux")) {
								libName = libName.substring(3);
							}
							theNativeLibraries.put(libName, file);
						}
					}
				}
			}
		}

		private void findPackages(BetterFile location, StringBuilder pkgPath) {
			if (!location.exists() || !location.isDirectory()) {
				return;
			}
			String pkgName = pkgPath.toString();
			theClassPath.compute(pkgPath.toString(), (__, old) -> {
				return CompositeSource.composite(old, new PackageImpl(this, pkgName, location));
			});
			for (BetterFile file : location.listFiles()) {
				if (file.isDirectory()) {
					int preLen = pkgPath.length();
					if (pkgPath.length() > 0) {
						pkgPath.append('/');
					}
					pkgPath.append(file.getName());
					findPackages(file, pkgPath);
					pkgPath.setLength(preLen);
				} else {
					long lastMod = file.getLastModified();
					if (lastMod > theLastModified) {
						theLastModified = lastMod;
					}
				}
			}
		}

		// init0
		void compileBundleDependencies(OsgiBundleSet bundles) {
			for (OsgiManifest.ManifestEntry entry : theManifest.getAll("Require-Bundle")) {
				Bundle bundle = bundles.getBundle(entry.getValue(), entry.getVersion());
				if (bundle == null) {
					if (!"optional".equals(entry.getAttributes().get("resolution"))) {
						System.err.println(toString() + ": Required bundle " + entry + " not found");
					}
					continue;
				}
				if (!theImportedBundles.contains(bundle)) {
					theImportedBundles.add(bundle);
					if ("reexport".equals(entry.getAttributes().get("visibility"))) {
						theReExportedBundles.set(theImportedBundles.size() - 1);
					}
					theNativeLibraries.putAll(bundle.theNativeLibraries);
				}
			}

			for (OsgiManifest.ManifestEntry entry : theManifest.getAll("Import-Package")) {
				ExportedPackage found = bundles.getExportedPackage(entry.getValue(), entry.getVersion());
				if (found == null) {
					if (!"optional".equals(entry.getAttributes().get("resolution"))) {
						System.err.println(toString() + ": Required package " + entry + " not exported");
					}
					continue;
				}
				ResourceSource source = found.getExporter().getResourcePackage(found.getName());
				theAvailablePackages.compute(entry.getValue().replaceAll("\\.", "/"), (pkg, old) -> CompositeSource.composite(old, source));
				if ("reexport".equals(entry.getAttributes().get("visibility"))) {
					theExportedPackages.add(found);
				}
			}
		}

		// init1
		Set<ExportedPackage> compileExportedPackages(Set<Bundle> bundlePath) {
			if (packagesCompiled) {
				return theExportedPackages;
			}
			if (!bundlePath.add(this)) {
				// I think it actually might be possible to support cycles some day, but not now
				throw new IllegalStateException("Dependency cycle detected for exported packages: "
					+ StringUtils.print(", ", bundlePath, Bundle::toString).append(", ").append(toString()));
			}
			for (int i = 0; i < theImportedBundles.size(); i++) {
				Bundle imported = theImportedBundles.get(i);
				boolean reExport = theReExportedBundles.get(i);
				for (ExportedPackage exported : imported.compileExportedPackages(bundlePath)) {
					theAvailablePackages.compute(exported.getName(), (pkg, old) -> {
						return CompositeSource.composite(old, imported.getResourcePackage(pkg));
					});
					if (reExport) {
						theExportedPackages.add(exported);
					}
				}
			}
			// Sort the packages for better debuggability
			List<ExportedPackage> packages = new ArrayList<>(theExportedPackages.size());
			packages.addAll(theExportedPackages);
			Collections.sort(packages, (p1, p2) -> {
				int comp = StringUtils.compareNumberTolerant(p1.getName(), p2.getName(), true, true);
				if (comp == 0) {
					if (p1.getVersion() != null) {
						if (p2.getVersion() != null) {
							comp = p1.getVersion().compareTo(p2.getVersion());
						} else {
							comp = -1;
						}
					} else if (p2.getVersion() != null) {
						comp = 1;
					}
				}
				return comp;
			});
			theExportedPackages.clear();
			theExportedPackages.addAll(packages);

			bundlePath.remove(this);
			return theExportedPackages;
		}
		
		/** @return The bundle set that loaded this bundle */
		public OsgiBundleSet getBundleSet() {
			return theBundleSet;
		}

		@Override
		public String getName() {
			return theName;
		}

		/** @return The bundle directory */
		public BetterFile getLocation() {
			return theBundle;
		}

		/** @return The latest last modified time of all files in the bundle */
		public long getLastModified() {
			return theLastModified;
		}

		/** @return The bundle manifest */
		public OsgiManifest getManifest() {
			return theManifest;
		}

		@Override
		public Version getVersion() {
			return theVersion;
		}

		/** @return All bundles imported by this bundle set */
		public List<Bundle> getImportedBundles() {
			return Collections.unmodifiableList(theImportedBundles);
		}

		/** @return All packages exported by this bundle set */
		public Set<ExportedPackage> getExportedPackages() {
			return Collections.unmodifiableSet(theExportedPackages);
		}

		Set<String> getAvailablePackages() {
			return theAvailablePackages.keySet();
		}

		/**
		 * @param packageName The name of the package to get (separated by '/' slashes instead of dots)
		 * @return A source to use to get resources from the package folder(s) available to this bundle
		 */
		public ResourceSource getResourcePackage(String packageName) {
			return theAvailablePackages.get(packageName);
		}

		/**
		 * Exports this bundle to a jar
		 *
		 * @param jarFile The jar file to export to
		 * @param nativeDir The directory to put natives in, or null to bundle them in the jar
		 * @param force Whether to export the jar even if the jar's last modified time is more recent than this bundle's (see
		 *        {@link #getLastModified()})
		 * @param os The set of operating systems to export any available natives for
		 * @param processor The set of processor architectures to export any available natives for
		 * @return The exported jar file
		 * @throws IOException If an error occurs reading resources or writing the jar
		 */
		public File exportJar(File jarFile, BetterFile nativeDir, boolean force, Set<String> os, Set<String> processor) throws IOException {
			StringBuilder path = new StringBuilder();
			Set<String> paths = new HashSet<>();
			if (jarFile == null) {
				String jarName = theName + "_" + theVersion + ".jar";
				jarFile = new File(new File(theBundle.getPath()), jarName);
				paths.add(jarName);
			}
			byte[] buffer = new byte[1024 * 1024];
			if (nativeDir != null) {
				BetterFile myNativeDir = nativeDir.at(toString());
				Set<BetterFile> oldNatives = new HashSet<>();
				addDeepContents(myNativeDir, oldNatives);
				for (OsgiManifest.ManifestEntry entry : theManifest.getAll("Bundle-NativeCode")) {
					String entryOS = entry.getAttributes().get("osname");
					if (!os.isEmpty() && entryOS != null && !os.contains(entryOS)) {
						continue;
					}
					String entryArch = entry.getAttributes().get("processor");
					if (!processor.isEmpty() && entryArch != null && !processor.contains(entryArch)) {
						continue;
					}
					for (String entryPath : entry.getValue().split(";")) {
						BetterFile location;
						if (entryPath.equals(".")) {
							location = theBundle;
						} else {
							location = theBundle.at(entryPath);
							path.append(entryPath);
						}
						if (location.exists()) {
							BetterFile nativeFile = myNativeDir.at(entryPath);
							if (oldNatives.remove(nativeFile)) {
								if (nativeFile.getLastModified() == location.getLastModified())
									continue;
							}
							if (!nativeFile.getParent().exists()) {
								nativeFile.getParent().create(true);
							}
							try (InputStream in = location.read(); //
								OutputStream out = nativeFile.write()) {
								copy(location.length(), in, out, buffer);
							}
							nativeFile.setLastModified(location.getLastModified());
						}
						path.setLength(0);
					}
				}
				for (BetterFile oldNative : oldNatives) {
					oldNative.delete(null);
				}
				deleteEmptyDirs(myNativeDir);
			}
			if (!force && jarFile.lastModified() >= theLastModified) {
				return jarFile;
			}
			try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(jarFile)))) {
				{
					zip.putNextEntry(new ZipEntry(MANIFEST_PATH));
					BetterFile manifest = theBundle.at(MANIFEST_PATH);
					try (InputStream in = manifest.read()) {
						copy(manifest.length(), in, zip, buffer);
					}
					paths.add(MANIFEST_PATH);
				}

				BetterFile cpFile = theBundle.at(".classpath");
				if (cpFile.exists()) {
					try {
						Element cp = QommonsConfig.getRootElement(cpFile.read());
						for (Element el : cp.getChildren("classpathentry")) {
							if ("output".equals(el.getAttributeValue("kind"))) {
								compress(//
									theBundle.at(el.getAttributeValue("path")), zip, path, paths, buffer);
							}
						}
					} catch (IOException e) {
						System.err.println(toString() + ": Could not read classpath for output");
						e.printStackTrace();
					}
				}
				for (OsgiManifest.ManifestEntry entry : theManifest.getAll("Bundle-ClassPath")) {
					BetterFile location;
					if (entry.getValue().equals(".")) {
						location = theBundle;
					} else {
						location = theBundle.at(entry.getValue());
					}
					compress(location, zip, path, paths, buffer);
				}
				for (OsgiManifest.ManifestEntry entry : theManifest.getAll("Bundle-NativeCode")) {
					String entryOS = entry.getAttributes().get("osname");
					if (!os.isEmpty() && entryOS != null && !os.contains(entryOS)) {
						continue;
					}
					String entryArch = entry.getAttributes().get("processor");
					if (!processor.isEmpty() && entryArch != null && !processor.contains(entryArch)) {
						continue;
					}
					for (String entryPath : entry.getValue().split(";")) {
						BetterFile location;
						if (entryPath.equals(".")) {
							location = theBundle;
						} else {
							location = theBundle.at(entryPath);
							path.append(entryPath);
						}
						if (location.exists()) {
							compress(location, zip, path, paths, buffer);
						}
						path.setLength(0);
					}
				}
			}
			return jarFile;
		}

		private void addDeepContents(BetterFile file, Set<BetterFile> files) {
			if (file.isDirectory()) {
				for (BetterFile child : file.listFiles())
					addDeepContents(child, files);
			} else
				files.add(file);
		}

		private void deleteEmptyDirs(BetterFile file) throws IOException {
			if (!file.isDirectory())
				return;
			List<? extends BetterFile> children = file.listFiles();
			if (children.isEmpty())
				file.delete(null);
			else {
				for (BetterFile child : children)
					deleteEmptyDirs(child);
			}
		}

		private void compress(BetterFile file, ZipOutputStream zip, StringBuilder path, Set<String> paths, byte[] buffer)
			throws IOException {
			if (file.isDirectory()) {
				int preLen = path.length();
				for (BetterFile child : file.listFiles()) {
					if (path.length() > 0) {
						path.append('/');
					}
					path.append(child.getName());
					compress(child, zip, path, paths, buffer);
					path.setLength(preLen);
				}
			} else if (file.exists() && !".classpath".equals(file.getName()) && !".java".equals(file.getName())) {
				String newPath = path.toString();
				if (paths.add(newPath)) {
					zip.putNextEntry(new ZipEntry(path.toString()));
					try (InputStream in = file.read()) {
						copy(file.length(), in, zip, buffer);
					}
				}
			}
		}

		@Override
		protected Class<?> findClass(String name) throws ClassNotFoundException {
			if (name.startsWith("java."))
				return super.findClass(name);
			LoadedClass loaded = findClass0(name);
			if (loaded == null)
				throw new ClassNotFoundException("Class not found or available to bundle " + toString() + ": " + name);
			return loaded.clazz;
		}

		Class<?> findClassIfAvailable(String name) throws ClassNotFoundException {
			LoadedClass loaded = findClass0(name);
			return loaded == null ? null : loaded.clazz;
		}

		private LoadedClass findClass0(String name) throws ClassNotFoundException {
			LoadedClass loaded = getPreviouslyLoaded(name);
			if (loaded == null) {
				String path = name.replaceAll("\\.", "/");
				int lastSlash = path.lastIndexOf('/');
				String pkg = lastSlash < 0 ? "" : path.substring(0, lastSlash);
				String file = (lastSlash < 0 ? path : path.substring(lastSlash + 1)) + ".class";
				ResourceSource source = theAvailablePackages.get(pkg);
				if (source == null) {
					return null;
				}
				Bundle owner = source.getOwner(file);
				if (owner == null) {
					return null;
				}
				synchronized (this) {
					loaded = getPreviouslyLoaded(name);
					if (loaded == null) {
						loaded = new LoadedClass(owner.findClass1(source, file, name), owner);
						theLoadedClasses.add(loaded);
					}
				}
			}
			return loaded;
		}

		private LoadedClass getPreviouslyLoaded(String className) {
			return CollectionElement.get(theLoadedClasses.getElement(className.hashCode(), lc -> lc.clazz.getName().equals(className)));
		}

		private Class<?> findClass1(ResourceSource source, String fileName, String className) throws ClassNotFoundException {
			LoadedClass loaded = getPreviouslyLoaded(className);
			if (loaded == null) {
				URL res = source.getResource(fileName);
				CircularByteBuffer buffer = new CircularByteBuffer(0);
				try (InputStream in = res.openStream()) {
					while (buffer.appendFrom(in, -1) >= 0) {
					}
				} catch (IOException e) {
					throw new ClassNotFoundException(fileName, e);
				}
				synchronized (this) {
					loaded = getPreviouslyLoaded(className);
					if (loaded == null) {
						try {
							loaded = new LoadedClass(defineClass(className, buffer.toByteArray(), 0, buffer.length()), this);
						} catch (ClassFormatError | SecurityException e) {
							throw e;
						}
						theLoadedClasses.add(loaded);
					}
				}
			}
			return loaded.clazz;
		}

		@Override
		protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
			if (name.startsWith("java."))
				return super.loadClass(name, resolve);
			LoadedClass loaded = findClass0(name);
			if (loaded == null || loaded.owner == this)
				return super.loadClass(name, resolve);
			else
				return loaded.owner.superLoadClass(name, resolve);
		}

		private Class<?> superLoadClass(String name, boolean resolve) throws ClassNotFoundException {
			return super.loadClass(name, resolve);
		}

		@Override
		protected String findLibrary(String libname) {
			BetterFile file = theNativeLibraries.get(libname);
			if (file != null) {
				return file.getPath();
			}
			return super.findLibrary(libname);
		}

		@Override
		public URL findResource(String name) {
			if (name.isEmpty()) {
				return null;
			} else if (name.charAt(0) == '/') {
				name=name.substring(1);
			}
			int lastSlash = name.lastIndexOf('/');
			String pkg = lastSlash < 0 ? "" : name.substring(0, lastSlash);
			String file = lastSlash < 0 ? name : name.substring(lastSlash + 1);
			ResourceSource source = theAvailablePackages.get(pkg);
			if (source == null) {
				return null;
			}
			return source.getResource(file);
		}

		@Override
		public Enumeration<URL> findResources(String name) throws IOException {
			int lastSlash = name.lastIndexOf('/');
			String pkg = lastSlash < 0 ? "" : name.substring(0, lastSlash);
			String file = lastSlash < 0 ? name : name.substring(lastSlash + 1);
			ResourceSource source = theAvailablePackages.get(pkg);
			if (source == null) {
				return Collections.emptyEnumeration();
			}
			return Collections.enumeration(source.getResources(file));
		}

		@Override
		protected Package getPackage(String name) {
			String path = name.replaceAll("\\.", "/");
			if (theLoadedPackages.contains(path)) {
				return super.getPackage(name);
			}
			ResourceSource source = theAvailablePackages.get(path);
			if (source != null) {
				Package pkg = source.getPackage();
				if (pkg != null) {
					return pkg;
				}
			}
			return super.getPackage(name);
		}

		Package createPackage(String name) {
			if (theLoadedPackages.add(name)) {
				ExportedPackage pkg = thePackagesByName.get(name);
				String version = (pkg == null || pkg.getVersion() == null) ? null : pkg.getVersion().toString();
				OsgiManifest.ManifestEntry vendor = theManifest.get("Bundle-Vendor", false);
				OsgiManifest.ManifestEntry bundleName = theManifest.get("Bundle-Name", false);
				return definePackage(name, //
					theName, //
					version, vendor == null ? null : vendor.getValue(), //
						bundleName == null ? null : bundleName.getValue(), //
							version, //
							bundleName == null ? null : bundleName.getValue(), null);
			} else {
				return super.getPackage(name);
			}
		}

		@Override
		public String toString() {
			if (theVersion != null) {
				return theName + "." + theVersion;
			} else {
				return theName;
			}
		}

		private static void copy(long size, InputStream in, OutputStream out, byte[] buffer) throws IOException {
			if (size == 0) {
				return;
			}
			int read = in.read(buffer);
			while (read >= 0) {
				out.write(buffer, 0, read);
				read = in.read(buffer);
			}
		}
	}

	/** Represents a package exported from a bundle */
	public static class ExportedPackage implements VersionedItem {
		private final Bundle theExporter;
		private final String theName;
		private final Version theVersion;

		/**
		 * @param exporter The bundle exporting the package
		 * @param name The name of the package (slash-separated, not dot-separated)
		 * @param version The version of the package (may be null if unspecified)
		 */
		public ExportedPackage(Bundle exporter, String name, Version version) {
			theExporter = exporter;
			theName = name;
			theVersion = version;
		}

		@Override
		public String getName() {
			return theName;
		}

		@Override
		public Version getVersion() {
			return theVersion;
		}

		/** @return The bundle exporting this package */
		public Bundle getExporter() {
			return theExporter;
		}

		@Override
		public int hashCode() {
			return Objects.hash(theName, theVersion, theExporter);
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this) {
				return true;
			} else if (!(obj instanceof ExportedPackage)) {
				return false;
			}
			return theName.equals(((ExportedPackage) obj).theName)//
				&& Objects.equals(theVersion, ((ExportedPackage) obj).theVersion)//
				&& theExporter == ((ExportedPackage) obj).theExporter;
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder(theName);
			if (theVersion != null) {
				str.append(" (").append(theVersion).append(')');
			}
			str.append(" by ").append(theExporter);
			return str.toString();
		}
	}

	/** Represents a set of resources in a package */
	public interface ResourceSource extends Named {
		/**
		 * @param name The name of the file
		 * @return The location of the file in this package, or null if not found
		 */
		URL getResource(String name);

		/**
		 * @param name The name of the file
		 * @return The locations of any files with the given name found in this package
		 */
		List<URL> getResources(String name);

		/**
		 * @param name The name of the file
		 * @return The bundle containing the given file, or null if no such file was found in this package
		 */
		Bundle getOwner(String name);

		/** @return The package that this resource represents */
		Package getPackage();
	}

	static class PackageImpl implements ResourceSource {
		private final Bundle theOwner;
		private final String theName;
		private final BetterFile theFile;

		public PackageImpl(Bundle owner, String name, BetterFile file) {
			theOwner = owner;
			theName = name;
			theFile = file;
		}

		public Bundle getOwner() {
			return theOwner;
		}

		@Override
		public String getName() {
			return theName;
		}

		@Override
		public URL getResource(String name) {
			BetterFile file = theFile.at(name);
			if (file.exists()) {
				try {
					return new URL(file.toUrl(null).toString());
				} catch (MalformedURLException e) {
					e.printStackTrace();
				}
			}
			return null;
		}

		@Override
		public List<URL> getResources(String name) {
			BetterFile file = theFile.at(name);
			if (file.exists()) {
				try {
					return Arrays.asList(new URL(file.toUrl(null).toString()));
				} catch (MalformedURLException e) {
					e.printStackTrace();
				}
			}
			return Collections.emptyList();
		}

		@Override
		public Bundle getOwner(String name) {
			BetterFile file = theFile.at(name);
			if (file.exists()) {
				return theOwner;
			} else {
				return null;
			}
		}

		@Override
		public Package getPackage() {
			return theOwner.createPackage(theName);
		}

		@Override
		public int hashCode() {
			return theFile.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof PackageImpl && theFile.equals(((PackageImpl) obj).theFile);
		}

		@Override
		public String toString() {
			return theFile.getPath();
		}
	}

	static class CompositeSource implements ResourceSource {
		private final List<ResourceSource> thePackages;

		CompositeSource(ResourceSource one, ResourceSource two) {
			thePackages = new ArrayList<>(3);
			thePackages.add(one);
			if (two instanceof CompositeSource) {
				for (ResourceSource rs : ((CompositeSource) two).thePackages)
					add(rs);
			} else
				add(two);
		}

		CompositeSource add(ResourceSource other) {
			if (!thePackages.contains(other))
				thePackages.add(other);
			return this;
		}

		@Override
		public String getName() {
			return thePackages.get(0).getName();
		}

		@Override
		public URL getResource(String name) {
			for (ResourceSource pkg : thePackages) {
				URL found = pkg.getResource(name);
				if (found != null) {
					return found;
				}
			}
			return null;
		}

		@Override
		public List<URL> getResources(String name) {
			List<URL> res = null;
			boolean composite = false;
			for (ResourceSource pkg : thePackages) {
				List<URL> pkgRes = pkg.getResources(name);
				if (!pkgRes.isEmpty()) {
					if (res == null) {
						res = pkgRes;
					} else {
						if (!composite) {
							composite = true;
							List<URL> c = new ArrayList<>(res.size() + pkgRes.size());
							c.addAll(res);
							res = c;
						}
						res.addAll(pkgRes);
					}
				}
			}
			return res == null ? Collections.emptyList() : res;
		}

		@Override
		public Bundle getOwner(String name) {
			for (ResourceSource pkg : thePackages) {
				Bundle found = pkg.getOwner(name);
				if (found != null) {
					return found;
				}
			}
			return null;
		}

		@Override
		public Package getPackage() {
			for (ResourceSource pkg : thePackages) {
				Package found = pkg.getPackage();
				if (found != null) {
					return found;
				}
			}
			return null;
		}

		static ResourceSource composite(ResourceSource one, ResourceSource two) {
			if (one == null || one.equals(two)) {
				return two;
			} else if (one instanceof CompositeSource) {
				return ((CompositeSource) one).add(two);
			} else {
				return new CompositeSource(one, two);
			}
		}
	}

	private BetterFile theNativeDir;
	private final Map<String, List<Bundle>> theBundles;
	private final Map<String, Set<ExportedPackage>> theExportedPackages;
	private final Map<String, List<Bundle>> theAvailablePackages;
	private boolean isInitialized;

	/** Creates the bundle set */
	public OsgiBundleSet() {
		theBundles = new LinkedHashMap<>();
		theExportedPackages = new HashMap<>();
		theAvailablePackages = new HashMap<>();
	}

	/**
	 * @param nativeDir The directory that native libraries are exported to
	 * @return This bundle set
	 */
	public OsgiBundleSet withNativeDir(BetterFile nativeDir) {
		if (!theBundles.isEmpty()) {
			throw new IllegalStateException("Native directory must be set before any bundles are added");
		}
		theNativeDir = nativeDir;
		return this;
	}

	/** @return All bundles in this bundle set */
	public List<Bundle> getBundles() {
		return theBundles.values().stream().flatMap(List::stream).collect(Collectors.toList());
	}

	/**
	 * @param name The name of the bundle to get
	 * @param version The range of versions acceptable for the bundle
	 * @return The bundle in this bundle set with the given name and a version in the given range, or null if none exists
	 */
	public Bundle getBundle(String name, Range<Version> version) {
		List<Bundle> bundles = theBundles.get(name);
		if (bundles == null) {
			return null;
		}
		for (Bundle bundle : bundles) {
			if (version.contains(bundle.getVersion())) {
				return bundle;
			}
		}
		return null;
	}

	/**
	 * @param name The name of the package to get
	 * @param version The range of versions acceptable for the package
	 * @return The package exported in this bundle set with the given name and a version in the given range, or null if none exists
	 */
	public ExportedPackage getExportedPackage(String name, Range<Version> version) {
		name = name.replaceAll("\\.", "/");
		Set<ExportedPackage> packages = theExportedPackages.get(name);
		if (packages == null) {
			return null;
		}
		for (ExportedPackage bundle : packages) {
			if (version.contains(bundle.getVersion())) {
				return bundle;
			}
		}
		return null;
	}

	/**
	 * @param bundle The directory containing the bundle
	 * @return This bundle set
	 * @throws IllegalArgumentException If an error occurs constructing the bundle
	 * @throws IOException If an error occurs reading the bundle's definition or resources
	 */
	public OsgiBundleSet addBundle(BetterFile bundle) throws IllegalArgumentException, IOException {
		return maybeAddBundle(bundle, null);
	}

	/**
	 * @param bundle The directory containing the bundle
	 * @param test Tests the bundle after creation to see if it should be loaded into this bundle set
	 * @return This bundle set
	 * @throws IllegalArgumentException If an error occurs constructing the bundle
	 * @throws IOException If an error occurs reading the bundle's definition or resources
	 */
	public OsgiBundleSet maybeAddBundle(BetterFile bundle, Predicate<Bundle> test) throws IllegalArgumentException, IOException {
		if (isInitialized) {
			throw new IllegalStateException("Cannot add new bundles after initialization");
		}
		BetterFile mf = bundle.at(MANIFEST_PATH);
		if (!mf.exists()) {
			throw new IllegalArgumentException(bundle.getPath() + " is not a bundle: No " + MANIFEST_PATH);
		}
		OsgiManifest manifest;
		try {
			manifest = OsgiManifest.build().read(new InputStreamReader(mf.read())).build();
		} catch (RuntimeException e) {
			throw new IllegalStateException("Could not load manifest of bundle " + bundle.getPath(), e);
		}
		Bundle newBundle = new Bundle(this, bundle, theNativeDir, manifest);
		if (test == null || test.test(newBundle)) {
			theBundles.compute(newBundle.getName(), (name, old) -> {
				if (old == null) {
					old = new ArrayList<>(3);
				}
				old.add(newBundle);
				return old;
			});
		}
		for (ExportedPackage pkg : newBundle.getExportedPackages()) {
			theExportedPackages.computeIfAbsent(pkg.getName(), __ -> new LinkedHashSet<>(5)).add(pkg);
		}
		for (String pkg : newBundle.getAvailablePackages()) {
			theAvailablePackages.computeIfAbsent(pkg, __ -> new ArrayList<>(5)).add(newBundle);
		}
		return this;
	}

	/**
	 * @param bundleDir The directory containing bundles to add
	 * @param recursive Whether to descend into the directory's contents recursively to look for bundles
	 * @param test Tests each bundle after creation to see if it should be loaded into this bundle set
	 * @return This Bundle set
	 * @throws IllegalArgumentException If an error occurs constructing a bundle
	 * @throws IOException If an error occurs reading a bundle's definition or resources
	 */
	public OsgiBundleSet addBundlesIn(BetterFile bundleDir, boolean recursive, Predicate<Bundle> test)
		throws IllegalArgumentException, IOException {
		if (isInitialized) {
			throw new IllegalStateException("Cannot add new bundles after initialization");
		}
		for (BetterFile bundle : bundleDir.listFiles()) {
			if (bundle.at(MANIFEST_PATH).exists()) {
				maybeAddBundle(bundle, test);
			} else if (recursive) {
				addBundlesIn(bundle, recursive, test);
			}
		}
		return this;
	}

	/**
	 * @param className The name of the class to load
	 * @return The loaded class
	 * @throws ClassNotFoundException If no bundle in this bundle set contains the definition of the given class
	 */
	public Class<?> loadClass(String className) throws ClassNotFoundException {
		if (!isInitialized) {
			init();
		}
		String classPath = className.replaceAll("\\.", "/");
		int lastSlash = classPath.lastIndexOf('/');
		String pkgName = lastSlash < 0 ? "" : classPath.substring(0, lastSlash);
		List<Bundle> bundles = theAvailablePackages.get(pkgName);
		if (bundles == null) {
			throw new ClassNotFoundException("No such package found: " + pkgName);
		}
		for (Bundle bundle : bundles) {
			Class<?> clazz = bundle.findClassIfAvailable(className);
			if (clazz != null) {
				return clazz;
			}
		}
		throw new ClassNotFoundException(className);
	}

	/**
	 * Initializes the bundles in this bundle set
	 *
	 * @return This bundle set
	 */
	public OsgiBundleSet init() {
		if (isInitialized) {
			return this;
		}
		isInitialized = true;
		for (List<Bundle> bundles : theBundles.values()) {
			for (Bundle bundle : bundles) {
				bundle.compileBundleDependencies(this);
			}
		}
		Set<Bundle> bundlePath = new LinkedHashSet<>();
		for (List<Bundle> bundles : theBundles.values()) {
			for (Bundle bundle : bundles) {
				bundle.compileExportedPackages(bundlePath);
			}
		}
		return this;
	}

	/**
	 * Main method
	 *
	 * @param clArgs
	 *        <ul>
	 *        <li><b>--osgi-bundle=??</b> Points to a bundle directory to load. Argument may contain multiple comma-separated values and may
	 *        be specified multiple times.</li>
	 *        <li><b>--osgi-bundles-in=??</b> Points to a directory containing bundles to load. Argument may contain multiple
	 *        comma-separated values and may be specified multiple times.</li>
	 *        <li><b>--exclude-bundles=??</b> Names of bundles to NOT load when using --osgi-bundles-in.</li>
	 *        <li><b>--jar</b> Flag argument to export the bundle set's bundles to jar files.</li>
	 *        <li><b>--jar-dir=??</b> Specifies the directory to put the exported bundle jars in when using --jar. If not specified, jars
	 *        will be put in each bundle's directory.</b>
	 *        <li><b>--osgi-main-class=??</b> The fully-qualified name of the main class to execute. Command line arguments for the given
	 *        class can be specified along with arguments specific to this class.</li>
	 *        <li><b>--start-ds</b> Flag argument to start a dependency service, loading components advertised by each bundle's
	 *        manifest.</li>
	 *        <li><b>--exclude-bundles=??</b> Names of bundles to NOT load when using --osgi-bundles-in.</li>
	 *        </ul>
	 */
	public static void main(String[] clArgs) {
		BetterFile.FileDataSource fds = new ArchiveEnabledFileSource(new NativeFileSource())//
			.withArchival(new ArchiveEnabledFileSource.ZipCompression());
		ArgumentParsing2.Arguments args = ArgumentParsing2.build()//
			.forFlagPattern(argSet->{
				argSet.add("jar", null)//
				;
			})//
			.forValuePattern(argSet -> {
				argSet//
					.addStringArgument("start-ds", arg -> arg.when("jar", void.class, c -> c.specified().forbidden()))//
					.addStringArgument("osgi-main-class", m -> m//
					.when("jar", void.class, c -> c.specified().forbidden())//
						.when("start-ds", String.class, c -> c.specified().forbidden())
						.when("jar", void.class, c -> c.missing().and("start-ds", String.class, c2 -> c2.missing()).required()))//
				.addFileArgument("jar-dir", arg -> arg.optional().directory(true).create(true)//
					.when("jar", void.class, c -> c.missing().forbidden()))//
				.addBetterFileArgument("native-dir", arg -> arg.optional().directory(true))//
				.addBooleanArgument("force",
					arg -> arg.defaultValue(true).when("jar", void.class, c -> c.missing().forbidden()))//
				;
			})//
			.forMultiValuePattern(argSet -> {
				argSet.addBetterFileArgument("osgi-bundle", f -> f.fromSource(fds).anyTimes().directory(true))
				.addBetterFileArgument("osgi-bundles-in", f -> f.fromSource(fds).anyTimes().directory(true)//
					.when("osgi-bundle", BetterFile.class, c -> c.missing().atLeastOnce()))//
				.addStringArgument("exclude-bundles", a -> a.anyTimes()//
					.when("osgi-bundles-in", BetterFile.class, c -> c.missing().forbidden()))//
				.addStringArgument("os", arg -> arg.optional().constrain(c -> c.oneOf("win32", "linux", "macosx"))//
					.when("jar", void.class, c -> c.missing().forbidden()))//
				.addStringArgument("processor", arg -> arg.optional().constrain(c -> c.oneOf("x86", "x86-64"))//
					.when("jar", void.class, c -> c.missing().forbidden()))//
					.addStringArgument("start-components", a -> a.when("start-ds", String.class, c -> c.missing().forbidden()))//
				;
			})//
			.acceptUnmatched(true)//
			.build()//
			.parse(clArgs);
		BetterFile nativeDir = args.get("native-dir", BetterFile.class);
		OsgiBundleSet bundles = new OsgiBundleSet();
		if (!args.has("jar")) {
			bundles.withNativeDir(nativeDir);
		}
		Set<String> excludeBundles = new HashSet<>(args.getAll("exclude-bundles", String.class));
		try {
			for (BetterFile file : args.getAll("osgi-bundle", BetterFile.class)) {
				bundles.addBundle(file);
			}
			for (BetterFile file : args.getAll("osgi-bundles-in", BetterFile.class)) {
				bundles.addBundlesIn(file, false, bundle -> !excludeBundles.contains(bundle.getName()));
			}
		} catch (IOException e) {
			throw new IllegalStateException("Misconfigured bundle set", e);
		}
		bundles.init();

		if (args.has("jar")) {
			long now = System.currentTimeMillis();
			boolean force = args.get("force", Boolean.class);
			File jarDir = args.get("jar-dir", File.class);
			Set<String> os = new HashSet<>(args.getAll("os", String.class));
			Set<String> processor = new HashSet<>(args.getAll("processor", String.class));
			System.out.println("Exporting bundle jars to " + (jarDir == null ? "each bundle" : jarDir.getPath()));
			for (Bundle bundle : bundles.getBundles()) {
				System.out.print("Exporting jar for " + bundle + "...");
				System.out.flush();
				try {
					File exported = bundle.exportJar(
						jarDir == null ? null : new File(jarDir, bundle.getName() + "_" + bundle.getVersion() + ".jar"), //
							nativeDir, force, os, processor);
					if (exported.lastModified() > now) {
						System.out.print("Exported");
					} else {
						System.out.print("Preserved");
					}
					System.out.println(" " + exported.getName());
				} catch (IOException e) {
					System.err.println("Export failed");
					e.printStackTrace();
				}
			}
		} else if (args.get("start-ds") != null) {
			if (!args.getUnmatched().isEmpty()) {
				throw new IllegalStateException("Unrecognized arguments: " + args.getUnmatched());
			}
			Class<?> serviceType;
			try {
				serviceType = bundles.loadClass(args.get("start-ds", String.class));
			} catch (ClassNotFoundException e) {
				throw new IllegalArgumentException("No such DS service type found: " + args.get("start-ds", String.class), e);
			}
			Object service;
			try {
				service = serviceType.newInstance();
			} catch (InstantiationException | IllegalAccessException e) {
				throw new IllegalArgumentException("Could not instantiate DS service " + serviceType.getName(), e);
			}
			Class<?> executorIntf = getExecutorIntf(serviceType);
			if (executorIntf == null)
				throw new IllegalStateException(
					"DS service " + serviceType.getName() + " must implement " + ComponentBasedExecutor.class.getName());
			Method loadComponentMethod, completeMethod;
			try {
				loadComponentMethod = executorIntf.getMethod("loadComponent", Class.class);
				completeMethod = executorIntf.getMethod("loadingComplete", Set.class);
			} catch (NoSuchMethodException | SecurityException e) {
				throw new IllegalStateException("Bad signatures coded for " + ComponentBasedExecutor.class.getName() + " reflection", e);
			}
			Set<String> startBundles = new HashSet<>(args.getAll("start-components", String.class));
			for (Bundle bundle : bundles.getBundles()) {
				for (OsgiManifest.ManifestEntry component : bundle.getManifest().getAll("Service-Component")) {
					String componentName = component.getValue();
					if (componentName.endsWith(".xml")) {
						System.err.println("Unconverted service component: " + componentName);
						continue;
					}
					Class<?> componentType;
					try {
						componentType = bundle.loadClass(componentName);
						loadComponentMethod.invoke(service, componentType);
					} catch (ClassNotFoundException e) {
						System.err.println("Could not find class " + componentName + ": " + e.getMessage());
					} catch (InvocationTargetException e) {
						System.err.println("Exception loading component " + componentName);
						e.getTargetException().printStackTrace();
					} catch (IllegalAccessException | IllegalArgumentException e) {
						throw new IllegalStateException("Could not access " + loadComponentMethod.getName(), e);
					}
				}
			}
			try {
				completeMethod.invoke(service, startBundles);
			} catch (IllegalAccessException | IllegalArgumentException e) {
				throw new IllegalStateException("Could not access " + completeMethod.getName(), e);
			} catch (InvocationTargetException e) {
				System.err.println("Exception initialing DS service " + serviceType.getName());
				e.printStackTrace();
			}
		} else {
			String mainClassName = args.get("osgi-main-class", String.class);
			Class<?> mainClass;
			try {
				mainClass = bundles.loadClass(mainClassName);
			} catch (ClassNotFoundException e) {
				throw new IllegalStateException(e.getMessage(), e);
			}

			String[] mainArgs = args.getUnmatched().toArray(new String[args.getUnmatched().size()]);
			try {
				mainClass.getMethod("main", String[].class).invoke(null, new Object[] { mainArgs });
			} catch (NoSuchMethodException e) {
				throw new IllegalStateException("Class contains no main method " + mainClassName, e);
			} catch (IllegalAccessException | SecurityException e) {
				throw new IllegalStateException("Could not access main method of class " + mainClassName, e);
			} catch (IllegalArgumentException e) {
				throw new IllegalStateException("Error invoking main method of class " + mainClassName, e);
			} catch (InvocationTargetException e) {
				if (e.getTargetException() instanceof RuntimeException) {
					throw (RuntimeException) e.getTargetException();
				} else if (e.getTargetException() instanceof Error) {
					throw (Error) e.getTargetException();
				} else {
					throw new CheckedExceptionWrapper(e.getTargetException());
				}
			}
		}
	}

	private static Class<?> getExecutorIntf(Class<?> serviceType) {
		if (serviceType == null)
			return null;
		if (serviceType.getName().equals(ComponentBasedExecutor.class.getName()))
			return serviceType;
		else if (serviceType == Object.class)
			return null;
		for (Class<?> intf : serviceType.getInterfaces()) {
			Class<?> found = getExecutorIntf(intf);
			if (found != null)
				return found;
		}
		return getExecutorIntf(serviceType.getSuperclass());
	}
}