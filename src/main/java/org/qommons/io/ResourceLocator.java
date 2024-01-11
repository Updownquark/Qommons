package org.qommons.io;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

public class ResourceLocator {
	private final Set<Object> theRelativeItems;
	private final StringBuilder theRelativePath;
	private boolean isWithQommonsClasspath;
	private boolean isWithLocalDir;

	public ResourceLocator() {
		theRelativeItems = new LinkedHashSet<>();
		theRelativePath = new StringBuilder();
		isWithQommonsClasspath = true;
		isWithLocalDir = true;
	}

	public ResourceLocator withLocalDir(boolean withLocalDir) {
		isWithLocalDir = withLocalDir;
		return this;
	}

	public ResourceLocator withQommonsClasspath(boolean withQommonsClasspath) {
		isWithQommonsClasspath = withQommonsClasspath;
		return this;
	}

	public ResourceLocator relativeTo(Class<?>... classes) {
		theRelativeItems.addAll(Arrays.asList(classes));
		return this;
	}

	public ResourceLocator relativeTo(ClassLoader... classLoaders) {
		theRelativeItems.addAll(Arrays.asList(classLoaders));
		return this;
	}

	public ResourceLocator relativeTo(File... files) {
		for (File file : files) {
			try {
				theRelativeItems.add(file.toURI().toURL());
			} catch (MalformedURLException e) {
				System.err.println("Could not transform " + file.getPath() + " to a URL");
				e.printStackTrace();
			}
		}
		return this;
	}

	public ResourceLocator relativeTo(URL... locations) {
		theRelativeItems.addAll(Arrays.asList(locations));
		return this;
	}

	public ResourceLocator relativeTo(String... relativeLocations) {
		for (String location : relativeLocations) {
			if (location.isEmpty())
				continue;
			location = location.replace('\\', '/');
			if (location.charAt(0) == '/') {
				if (location.length() == 1)
					continue;
				location = location.substring(1);
			}
			if (theRelativePath.length() > 0 && theRelativePath.charAt(theRelativePath.length() - 1) != '/')
				theRelativePath.append('/');
			theRelativePath.append(location);
		}
		return this;
	}

	public URL findResource(String resourceLocation) throws IOException {
		if (resourceLocation.isEmpty())
			throw new IllegalArgumentException("Empty resource location string");
		resourceLocation = resourceLocation.replace('\\', '/');

		if (resourceLocation.startsWith("classpath://")) {
			String resPath = resourceLocation.substring("classpath:/".length());
			return findFromClassPath(resPath);
		} else if (resourceLocation.contains("://") || resourceLocation.startsWith("file:/") || resourceLocation.startsWith("jar:file:/")) {
			return new URL(resourceLocation);
		} else if (resourceLocation.charAt(0) == '/') {
			if (hasAnyFiles()) {
				File file = new File(resourceLocation);
				if (file.exists())
					return file.toURI().toURL();
			}
			return findFromClassPath(resourceLocation);
		} else {
			if (theRelativePath.length() > 0)
				resourceLocation = new StringBuilder(theRelativePath.toString()).append(resourceLocation).toString();
			if (isWithLocalDir) {
				File file = new File(new File(System.getProperty("user.dir")), resourceLocation);
				if (file.exists())
					return file.toURI().toURL();
			}
			if (isWithQommonsClasspath) {
				URL resource = ResourceLocator.class.getClassLoader().getResource(resourceLocation);
				if (resource != null)
					return resource;
			}
			for (Object item : theRelativeItems) {
				if (item instanceof Class) {
					URL resource = ((Class<?>) item).getResource(resourceLocation);
					if (resource != null)
						return resource;
				} else if (item instanceof ClassLoader) {
					URL resource = ((ClassLoader) item).getResource(resourceLocation);
					if (resource != null)
						return resource;
				} else if (item instanceof File) {
					File file = new File((File) item, resourceLocation);
					if (file.exists())
						return file.toURI().toURL();
				} else if (item instanceof URL) {
					URL resource = evaluateRelativeResource((URL) item, resourceLocation);
					if (resource != null)
						return resource;
				}
			}
		}
		return null;
	}

	private URL findFromClassPath(String resPath) {
		if (isWithQommonsClasspath) {
			URL resource = ResourceLocator.class.getClassLoader().getResource(resPath);
			if (resource != null)
				return resource;
		}
		for (Object item : theRelativeItems) {
			if (item instanceof Class) {
				URL resource = ((Class<?>) item).getResource(resPath);
				if (resource != null)
					return resource;
			} else if (item instanceof ClassLoader) {
				URL resource = ((ClassLoader) item).getResource(resPath);
				if (resource != null)
					return resource;
			}
		}
		return null;
	}

	private boolean hasAnyFiles() {
		if (isWithLocalDir)
			return true;
		for (Object item : theRelativeItems) {
			if (item instanceof File)
				return true;
		}
		return false;
	}

	private URL evaluateRelativeResource(URL relativeTo, String path) {
		StringBuilder resourcePath = new StringBuilder();
		if (relativeTo.getPath() != null)
			resourcePath.append(relativeTo.getPath());
		int origFileLen = resourcePath.length();
		if (resourcePath.length() > 0 && resourcePath.charAt(resourcePath.length() - 1) == '/')
			resourcePath.setLength(resourcePath.length() - 1);

		int index = 0;
		int slash = path.indexOf('/');
		while (slash >= 0) {
			if (slash - index == 2 && path.charAt(index) == '.' && path.charAt(index + 1) == '.') { // ".."
				int lastSlash = resourcePath.lastIndexOf("/");
				if (lastSlash < 0)
					return null;
				resourcePath.setLength(lastSlash);
			} else if (slash - index == 1 && path.charAt(index) == '.') { // "."
			} else
				resourcePath.append('/').append(path, index, slash);
			index = slash + 1;
			slash = path.indexOf('/', index);
		}
		resourcePath.append(path, index, path.length());

		URL resource;
		try {
			String prefix = relativeTo.toString();
			prefix = prefix.substring(0, prefix.length() - origFileLen);
			resource = new URL(prefix + resourcePath.toString());
		} catch (MalformedURLException e) {
			return null;
		}
		return resource;
	}
}
