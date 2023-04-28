package org.qommons;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.junit.Assert;
import org.junit.Test;
import org.qommons.QuarkApplicationVersioning.ApplicationVersionDeployment;
import org.qommons.io.ArchiveEnabledFileSource;
import org.qommons.io.BetterFile;
import org.qommons.io.FileUtils;
import org.qommons.io.InMemoryFileSystem;
import org.qommons.io.TextParseException;

/** Tests {@link QuarkApplicationVersioning} functionality */
public class QuarkApplicationVersioningTests {
	private static final String APP_NAME = "Test-App";
	private static final List<String> DISTRIBUTIONS = Arrays.asList(APP_NAME + "_1-2-3-rc-4.exe", APP_NAME + "-1.2.3-rc.4.tar.gz");
	private static final List<String> SHELL_FILES = Arrays.asList("run", "backup");
	private static final List<String> PLUGINS = Arrays.asList("observe", "qommons", "app-specific");
	private static final List<String> IGNORED = Arrays.asList("application.config");

	private final Random theRandom = new Random();
	private final byte[] theBuffer1 = new byte[1024];
	private final byte[] theBuffer2 = new byte[1024];

	/**
	 * Tests {@link QuarkApplicationVersioning} functionality
	 * 
	 * @throws IOException If the memory-backed internal files throw an exception
	 * @throws TextParseException If the test file or one of the internally-generated files cannot be parsed
	 */
	@Test
	public void test() throws IOException, TextParseException {
		BetterFile root = BetterFile.getRoots(new ArchiveEnabledFileSource(new InMemoryFileSystem())//
			.withArchival(new ArchiveEnabledFileSource.ZipCompression())).get(0);
		
		//Set up an application
		BetterFile distributions = root.at("distrib").create(true);
		for (String distrib : DISTRIBUTIONS)
			addRandomContent(distributions.at(distrib).write());
		BetterFile source = root.at("source").create(true);
		for (String batchFile : SHELL_FILES)
			addRandomContent(source.at(batchFile + ".sh").write());
		BetterFile plugins=source.at("plugins").create(true);
		for(String plugin : PLUGINS)
			addRandomContent(plugins.at(plugin+".jar").write());
		for(String ignored : IGNORED)
			addRandomContent(source.at(ignored).write());
		
		//Too hard to configure this part, just hard-coding it
		BetterFile manual=source.at("manual").create(true);
		addRandomContent(manual.at("index.html").write());
		addRandomContent(manual.at("another-page.html").write());
		BetterFile images=manual.at("images").create(true);
		addRandomContent(images.at("image.png").write());
		
		System.out.println("Source:");
		FileUtils.printStructure(source, System.out, 1);

		QuarkApplicationVersioning versioning = new QuarkApplicationVersioning();
		ApplicationVersionDeployment deployment;
		try (InputStream in = QuarkApplicationVersioningTests.class.getResourceAsStream("qav-application-deploy.xml")) {
			deployment = versioning.parseDeployment(in, distributions);
		}

		//Create the staged deployment
		BetterFile deployRoot = root.at("deployed").create(true);
		BetterFile deployed = deployRoot.at(APP_NAME + "-1.2.3-rc.4");
		versioning.deployVersion(deployment, source, deployRoot, "distrib", null);
		// Verify the deployment
		System.out.println("Deployed:");
		FileUtils.printStructure(deployed, System.out, 1);
		for (String batchFile : SHELL_FILES)
			compareContent(source.at(batchFile + ".sh"), deployed.at(batchFile + ".sh2"), true);
		for (String plugin : PLUGINS)
			compareContent(plugins.at(plugin + ".jar"), deployed.at("plugins/" + plugin + ".j"), true);
		for (BetterFile sourceFile : source.listFiles()) {
			if (sourceFile.getName().endsWith(".sh"))
				continue;
			else if (IGNORED.contains(sourceFile.getName()))
				Assert.assertFalse(deployed.at(sourceFile.getName()).exists());
			else if (!sourceFile.isDirectory())
				compareContent(sourceFile, deployed.at(sourceFile.getName()), true);
		}

		// TODO Create an application instance to update
		BetterFile toUpdate = root.at("to-update").create(true);
		addRandomContent(toUpdate.at(IGNORED.get(0)).write());
		addRandomContent(toUpdate.at(SHELL_FILES.get(0) + ".sh").write());
		System.out.println("To Update:");
		FileUtils.printStructure(toUpdate, System.out, 1);

		// Update the application instance
		BetterFile tempDir = root.at("temp").create(true);
		versioning.downloadUpdate(deployed.getParent(), deployment, toUpdate, tempDir, null);
		System.out.println("Update Temporary:");
		FileUtils.printStructure(tempDir, System.out, 1);
		// Verify the temporary update directory
		for (BetterFile sourceFile : source.listFiles()) {
			if (IGNORED.contains(sourceFile.getName())) {//
			} else if (!sourceFile.isDirectory())
				compareContent(sourceFile, tempDir.at(sourceFile.getName()), true);
			else
				compareDeepContent(sourceFile, tempDir.at(sourceFile.getName()));
		}
		Assert.assertTrue(versioning.doUpdate(toUpdate, tempDir, v -> root.at("backup").create(true)));
		System.out.println("Updated:");
		FileUtils.printStructure(toUpdate, System.out, 1);
		// Verify the update
		for (BetterFile sourceFile : source.listFiles()) {
			if (IGNORED.contains(sourceFile.getName())) {//
			} else if (!sourceFile.isDirectory())
				compareContent(sourceFile, toUpdate.at(sourceFile.getName()), true);
			else
				compareDeepContent(sourceFile, toUpdate.at(sourceFile.getName()));
		}
		for (BetterFile updateFile : toUpdate.listFiles()) {
			if (IGNORED.contains(updateFile.getName()) || updateFile.getName().equals("application-version.xml")) {//
				BetterFile sourceFile = source.at(updateFile.getName());
				if (sourceFile.exists())
					compareContent(updateFile, sourceFile, false);
			} else if (!updateFile.isDirectory())
				compareContent(updateFile, source.at(updateFile.getName()), true);
			else
				compareDeepContent(updateFile, source.at(updateFile.getName()));
		}
	}

	private void addRandomContent(OutputStream write) throws IOException {
		theRandom.nextBytes(theBuffer1);
		write.write(theBuffer1);
		write.close();
	}

	private void compareContent(BetterFile source, BetterFile target, boolean equal) throws IOException {
		try (InputStream in = source.read()) {
			in.read(theBuffer1);
		}
		try (InputStream in = target.read()) {
			in.read(theBuffer2);
		}
		if (equal != Arrays.equals(theBuffer1, theBuffer2))
			throw new AssertionError("Content is " + (equal ? "different" : "identical"));
	}

	private void compareDeepContent(BetterFile source, BetterFile target) throws IOException {
		Assert.assertTrue(target.getName(), target.isDirectory());
		for (BetterFile sf : source.listFiles()) {
			if (sf.isDirectory())
				compareDeepContent(sf, target.at(sf.getName()));
			else
				compareContent(sf, target.at(sf.getName()), true);
		}
	}
}
