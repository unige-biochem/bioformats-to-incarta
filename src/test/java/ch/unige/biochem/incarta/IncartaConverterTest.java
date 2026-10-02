/*-
 * #%L
 * Converts any Bio-Formats supported image file into an IN Carta compatible format.
 * %%
 * Copyright (C) 2026 University of Geneva
 * %%
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * #L%
 */
package ch.unige.biochem.incarta;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import loci.formats.FormatException;
import loci.formats.ImageReader;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * End-to-end conversion against Bio-Formats' {@code .fake} reader, so the test
 * needs no data file: the dimensions are encoded in the file name.
 */
public class IncartaConverterTest {

	@Rule
	public final TemporaryFolder folder = new TemporaryFolder();

	@Test
	public void writesOneTiffPerPlane() throws Exception {
		final Path input = fake("stack&sizeX=16&sizeY=16&sizeC=2&sizeZ=3&sizeT=1.fake");
		final Path output = folder.newFolder("out").toPath();

		final List<PlaneCoordinates> planes = new IncartaConverter(
			new ImageXpressLayout("stack")).convert(input, output, null);

		assertEquals(6, planes.size());
		assertEquals(6, tiffNames(output).size());
		assertTrue(tiffNames(output).contains("t1_A01_s1_w2_z3.tif"));
	}

	@Test
	public void convertsOnlyTheRequestedSeries() throws Exception {
		final Path input = fake("multi&sizeX=8&sizeY=8&sizeC=1&series=3.fake");
		final Path output = folder.newFolder("one-series").toPath();

		final List<PlaneCoordinates> planes = new IncartaConverter(
			new ImageXpressLayout("multi"), new ConversionOptions().series(1))
				.convert(input, output, null);

		assertEquals(1, planes.size());
		assertEquals(1, planes.get(0).series());
	}

	@Test(expected = IOException.class)
	public void refusesToOverwriteByDefault() throws Exception {
		final Path input = fake("once&sizeX=8&sizeY=8&sizeC=1.fake");
		final Path output = folder.newFolder("twice").toPath();
		final IncartaConverter converter = new IncartaConverter(
			new ImageXpressLayout("once"));

		converter.convert(input, output, null);
		converter.convert(input, output, null);
	}

	@Test
	public void namesEveryPlaneOfAPlateByWellAndSite() throws Exception {
		final Path output = convertPlate("plate");
		final List<String> names = tiffNames(output);

		// 1 plate x (2 x 3) wells x 2 fields x 4 z x 2 c x 3 t.
		assertEquals(288, names.size());
		assertTrue(names.contains("t1_A01_s1_w1_z1.tif"));
		assertTrue(names.contains("t3_B03_s2_w2_z4.tif"));
	}

	@Test
	public void writesAnXdceIndexingEveryPlane() throws Exception {
		final Path output = convertPlate("indexed");
		final Path xdce = output.resolve("indexed.xdce");
		assertTrue(Files.exists(xdce));

		final String text = new String(Files.readAllBytes(xdce),
			StandardCharsets.ISO_8859_1);
		final List<String> indexed = filenames(text);
		assertEquals(288, indexed.size());
		assertTrue(text.contains("<Plate columns=\"3\" rows=\"2\""));
		assertTrue(text.contains("number_of_slices=\"4\""));
		assertEquals(3, count(text, "<TimePoint index="));
		assertEquals(2, count(text, "<Wavelength index="));

		// Every name in the index is a file that was actually written.
		for (final String name : indexed) {
			assertTrue(name + " is indexed but missing", Files.exists(output.resolve(
				name)));
		}
	}

	@Test
	public void listsTheIndexInAcquisitionOrder() throws Exception {
		final Path output = convertPlate("ordered");
		final List<String> indexed = filenames(new String(Files.readAllBytes(output
			.resolve("ordered.xdce")), StandardCharsets.ISO_8859_1));

		assertEquals("t1_A01_s1_w1_z1.tif", indexed.get(0));
		assertEquals("t1_A01_s1_w1_z2.tif", indexed.get(1));
		assertEquals("t1_A01_s1_w2_z1.tif", indexed.get(4));
		assertEquals("t1_A01_s2_w1_z1.tif", indexed.get(8));
		assertEquals("t1_A02_s1_w1_z1.tif", indexed.get(16));
	}

	@Test
	public void recordsPixelStatisticsForEveryPlane() throws Exception {
		final Path input = fake("stats&sizeX=8&sizeY=8&sizeC=1&pixelType=uint16.fake");
		final Path output = folder.newFolder("stats").toPath();

		final List<PlaneCoordinates> planes = new IncartaConverter(
			new ImageXpressLayout("stats")).convert(input, output, null);

		final PlaneCoordinates plane = planes.get(0);
		assertNotNull(plane.min());
		assertNotNull(plane.max());
		assertNotNull(plane.mean());
		assertTrue(plane.min() <= plane.mean() && plane.mean() <= plane.max());
	}

	@Test
	public void writesUncompressedTiffsByDefault() throws Exception {
		final Path input = fake("plain&sizeX=8&sizeY=8&sizeC=1.fake");
		final Path output = folder.newFolder("plain").toPath();
		new IncartaConverter(new ImageXpressLayout("plain")).convert(input, output,
			null);

		assertEquals("Uncompressed", new ConversionOptions().getCompression());
		// The written plane must still be readable as a plain TIFF.
		final ImageReader reader = new ImageReader();
		try {
			reader.setId(output.resolve("t1_A01_s1_w1_z1.tif").toString());
			assertEquals(1, reader.getImageCount());
		}
		finally {
			reader.close();
		}
	}

	/** One IN Carta experiment is one plate, so several plates are refused. */
	@Test
	public void refusesASourceWithMoreThanOnePlate() throws Exception {
		final Path input = fake(
			"two&plates=2&plateRows=2&plateCols=3&fields=1&sizeX=8&sizeY=8&sizeC=1.fake");
		final Path output = folder.newFolder("two").toPath();
		try {
			new IncartaConverter(new ImageXpressLayout("two")).convert(input, output,
				null);
			fail("A two-plate source should not convert");
		}
		catch (final FormatException e) {
			assertTrue(e.getMessage().contains("2 plates"));
		}
	}

	/** A 2 x 3 well plate with two fields per well, four slices, two channels. */
	private Path convertPlate(final String name) throws Exception {
		final Path input = fake(name +
			"&plates=1&plateRows=2&plateCols=3&fields=2&sizeX=8&sizeY=8&sizeZ=4&sizeC=2&sizeT=3.fake");
		final Path output = folder.newFolder(name).toPath();
		new IncartaConverter(new ImageXpressLayout(name)).convert(input, output,
			null);
		return output;
	}

	/** An empty {@code .fake} file - the reader takes its shape from the name. */
	private Path fake(final String name) throws IOException {
		final Path input = folder.getRoot().toPath().resolve(name);
		Files.createFile(input);
		return input;
	}

	private static List<String> tiffNames(final Path directory) throws IOException {
		try (Stream<Path> files = Files.walk(directory)) {
			return files.filter(Files::isRegularFile) //
				.map(p -> p.getFileName().toString()) //
				.filter(n -> n.endsWith(".tif")) //
				.collect(Collectors.toList());
		}
	}

	private static List<String> filenames(final String xdce) {
		final Matcher matcher = Pattern.compile("filename=\"([^\"]+)\"").matcher(
			xdce);
		return matcher.results().map(m -> m.group(1)).collect(Collectors.toList());
	}

	private static int count(final String text, final String needle) {
		int found = 0;
		for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at +
			1)) found++;
		return found;
	}
}
