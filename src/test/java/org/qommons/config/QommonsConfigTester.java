package org.qommons.config;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;

/** A couple tests for {@link QommonsConfig} */
public class QommonsConfigTester {
	/**
	 * Tests basic persistence and storage
	 * 
	 * @throws IOException If the test XML file cannot be read
	 */
	@Test
	public void testBasicConfigPersistence() throws IOException {
		MutableConfig config = new MutableConfig(null, QommonsConfig.fromXml(getClass().getResource("Test.xml")));
		checkContent(config);

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		MutableConfig.writeAsXml(config, out);
		MutableConfig newConfig = new MutableConfig(null,
			QommonsConfig.fromXml(QommonsConfig.getRootElement(new ByteArrayInputStream(out.toByteArray()))));
		checkContent(newConfig);
		Assert.assertEquals(config, newConfig);
	}

	private static void checkContent(MutableConfig config) {
		Assert.assertEquals("test-root", config.getName());
		Assert.assertEquals("test-el1", config.subConfigs()[0].getName());
		Assert.assertEquals("test-el2", config.subConfigs()[1].getName());
		Assert.assertEquals("attr-value", config.get("test-el1/attr1"));
		Assert.assertEquals("attr-value1", config.get("test-el2/attr1"));
		Assert.assertEquals("attr-value2", config.get("test-el2/attr2"));
		Assert.assertEquals("Text content", config.get("test-el2/inner-el"));
	}

	/**
	 * Tests persistence and storage with a much larger test file than {@link #testBasicConfigPersistence()}
	 * 
	 * @throws IOException If the test XML file cannot be read
	 */
	@Test
	public void testAdvancedConfigPersistence() throws IOException {
		MutableConfig config = new MutableConfig(null, QommonsConfig.fromXml(getClass().getResource("BiggerTest.xml")));

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		MutableConfig.writeAsXml(config, out);
		MutableConfig newConfig = new MutableConfig(null,
			QommonsConfig.fromXml(QommonsConfig.getRootElement(new ByteArrayInputStream(out.toByteArray()))));
		Assert.assertEquals(config, newConfig);
	}
}
