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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Checks the layout against the naming and index rules read off the reference
 * MetaXpress export at
 * {@code F:/user-projects/data/access-facility/bioformats-to-incarta}. The
 * dataset itself is not needed: the rules it taught are asserted here.
 */
public class ImageXpressLayoutTest {

	@Rule
	public final TemporaryFolder folder = new TemporaryFolder();

	private final ImageXpressLayout layout = new ImageXpressLayout("plate1");

	@Test
	public void namesEveryFilenameIndexOneBased() {
		final PlaneCoordinates plane = new PlaneCoordinates(7, "Well B3", 1, 2, 0, 1,
			"DAPI", 0, 3);
		assertEquals("t4_B03_s1_w2_z1.tif", layout.relativePathFor(plane));
	}

	@Test
	public void putsTheTimepointFirstAndZLast() {
		final PlaneCoordinates plane = new PlaneCoordinates(0, "Well E3", 4, 2, 15,
			2, "Texas Red - S", 7, 3);
		assertEquals("t4_E03_s16_w3_z8.tif", layout.relativePathFor(plane));
	}

	@Test
	public void treatsAPlatelessSourceAsOneWellOfManySites() {
		final PlaneCoordinates plane = new PlaneCoordinates(4, "series4", -1, -1, 0,
			0, "channel0", 2, 0);
		assertEquals("t1_A01_s5_w1_z3.tif", layout.relativePathFor(plane));
	}

	@Test
	public void spellsTheWellDifferentlyInTheIndex() {
		final PlaneCoordinates plane = new PlaneCoordinates(0, "Well E10", 4, 9, 0,
			0, "DAPI", 0, 0);
		assertEquals("E10", ImageXpressLayout.wellLabel(plane));
		assertEquals("E - 10", ImageXpressLayout.xdceWellLabel(plane));
	}

	@Test
	public void writesAnIndexNamedAfterThePlate() throws Exception {
		final Path output = folder.newFolder("out").toPath();
		layout.writeDatasetMetadata(output, description(), planes());

		final String xdce = read(output.resolve("plate1.xdce"));
		assertTrue(xdce.startsWith(
			"<?xml version=\"1.0\" encoding=\"ISO-8859-1\" standalone=\"yes\"?>"));
		assertTrue(xdce.contains("<ImageStack PlateID=\"plate1\">"));
		assertTrue(xdce.contains("<Plate columns=\"12\" rows=\"8\""));
		assertTrue(xdce.contains("pixel_width=\"0.170400\""));
		assertTrue(xdce.contains("<WellSpacing horizontal=\"9.000000\""));
		assertTrue(xdce.contains(
			"<TopLeftWellCenterOffset horizontal=\"14.380000\" vertical=\"11.240000\" unit=\"mm\"/>"));
		assertTrue(xdce.contains(
			"<EmissionFilter name=\"DAPI\" wavelength=\"447\" unit=\"nm\"/>"));
		assertTrue(xdce.endsWith("</ImageStack>\n"));
	}

	/** IN Carta requires a sized, shaped well even on a plate with no footprint. */
	@Test
	public void sizesTheWellOfANonStandardPlateToOneFieldOfView()
		throws Exception
	{
		final Path output = folder.newFolder("oneWell").toPath();
		final DatasetDescription dataset = new DatasetDescription() //
			.plateSize(1, 1) //
			.frameSize(1024, 512) //
			.pixelSizeUm(0.5, 0.5) //
			.addWavelength(0, "channel0", null);
		layout.writeDatasetMetadata(output, dataset, planes());
		final String xdce = read(output.resolve("plate1.xdce"));

		assertTrue(xdce.contains(
			"<WellParameters width=\"0.512000\" unit=\"mm\" height=\"0.512000\" size=\"0.512000\" shape=\"Square\"/>"));
	}

	/** IN Carta refuses a protocol wavelength without an emission value. */
	@Test
	public void declaresAnEmissionWavelengthEvenWhenTheSourceHasNone()
		throws Exception
	{
		final Path output = folder.newFolder("noEmission").toPath();
		final DatasetDescription dataset = new DatasetDescription() //
			.plateSize(8, 12) //
			.addWavelength(0, "channel0", null);
		layout.writeDatasetMetadata(output, dataset, planes());
		final String xdce = read(output.resolve("plate1.xdce"));

		assertTrue(xdce.contains(
			"<EmissionFilter name=\"channel0\" wavelength=\"500\" unit=\"nm\"/>"));
	}

	@Test
	public void listsOneImagePerPlaneInAcquisitionOrder() throws Exception {
		final Path output = folder.newFolder("ordered").toPath();
		layout.writeDatasetMetadata(output, description(), planes());

		final List<String> names = filenames(read(output.resolve("plate1.xdce")));
		assertEquals(planes().size(), names.size());
		// timepoint, then well, then site, then wavelength, then z.
		assertEquals("t1_B03_s1_w1_z1.tif", names.get(0));
		assertEquals("t1_B03_s1_w1_z2.tif", names.get(1));
		assertEquals("t1_B03_s1_w2_z1.tif", names.get(2));
		assertEquals("t1_B03_s2_w1_z1.tif", names.get(4));
		assertEquals("t1_B04_s1_w1_z1.tif", names.get(8));
		assertEquals("t2_B03_s1_w1_z1.tif", names.get(16));
	}

	@Test
	public void splitsTheIndexBasesBetweenNameAndIdentifier() throws Exception {
		final Path output = folder.newFolder("bases").toPath();
		layout.writeDatasetMetadata(output, description(), planes());
		final String xdce = read(output.resolve("plate1.xdce"));

		// The first entry: well B3, site 1, first wavelength, first slice.
		assertTrue(xdce.contains("filename=\"t1_B03_s1_w1_z1.tif\""));
		assertTrue(xdce.contains("<Well label=\"B - 3\">"));
		assertTrue(xdce.contains("<Row number=\"2\"/>"));
		assertTrue(xdce.contains("<Column number=\"3\"/>"));
		assertTrue(xdce.contains(
			"<Identifier field_index=\"0\" time_index=\"0\" wave_index=\"0\" z_index=\"0\"/>"));
	}

	@Test
	public void omitsAcquisitionValuesTheSourceDoesNotDescribe() throws Exception {
		final Path output = folder.newFolder("sparse").toPath();
		final List<PlaneCoordinates> bare = new ArrayList<>();
		bare.add(new PlaneCoordinates(0, "Well B3", 1, 2, 0, 0, "DAPI", 0, 0));
		layout.writeDatasetMetadata(output, description(), bare);
		final String xdce = read(output.resolve("plate1.xdce"));

		assertTrue(xdce.contains("<EmissionFilter name=\"DAPI\"/>"));
		assertTrue(!xdce.contains("<Exposure"));
		assertTrue(!xdce.contains("<PlatePosition_um"));
		assertTrue(!xdce.contains("<FocusPosition"));
		assertTrue(!xdce.contains("<MinMaxMean"));
	}

	@Test
	public void carriesTheAcquisitionValuesTheSourceDoesDescribe()
		throws Exception
	{
		final Path output = folder.newFolder("rich").toPath();
		final List<PlaneCoordinates> rich = new ArrayList<>();
		rich.add(new PlaneCoordinates(0, "Well B3", 1, 2, 0, 0, "DAPI", 0, 0) //
			.positionUm(30476.54, 45276.54, 8491.92) //
			.exposureMs(200.0) //
			.statistics(155.0, 65535.0, 2494.3, true));
		layout.writeDatasetMetadata(output, description(), rich);
		final String xdce = read(output.resolve("plate1.xdce"));

		assertTrue(xdce.contains("<FocusPosition unit=\"um\" z=\"8491.920000\"/>"));
		assertTrue(xdce.contains(
			"<PlatePosition_um x=\"30476.540000\" y=\"45276.540000\"/>"));
		assertTrue(xdce.contains("<Exposure time=\"200\" unit=\"ms\"/>"));
		assertTrue(xdce.contains(
			"<MinMaxMean max=\"65535\" mean=\"2494\" min=\"155\"/>"));
	}

	@Test
	public void escapesNamesForTheDeclaredEncoding() throws Exception {
		final Path output = folder.newFolder("escaped").toPath();
		final ImageXpressLayout awkward = new ImageXpressLayout("a&b \u00b5m \u4e2d");
		awkward.writeDatasetMetadata(output, description(), planes());
		final String xdce = read(output.resolve("a&b \u00b5m \u4e2d.xdce"));

		assertTrue(xdce.contains("PlateID=\"a&amp;b \u00b5m &#20013;\""));
	}

	@Test
	public void stripsTheExtensionForThePlateName() {
		assertEquals("plate1", ImageXpressLayout.baseNameOf("plate1.nd2"));
		assertEquals("plate1.ome", ImageXpressLayout.baseNameOf("plate1.ome.tif"));
		assertEquals("noextension", ImageXpressLayout.baseNameOf("noextension"));
	}

	// -- fixtures --

	/** A 96-well plate protocol, calibrated like the reference export. */
	private static DatasetDescription description() {
		return new DatasetDescription() //
			.plateSize(8, 12) //
			.frameSize(2048, 2048) //
			.pixelSizeUm(0.1704, 0.1704) //
			.objective("40X Water Apo LambdaS LWD", 38.0) //
			.timepointCount(2) //
			.zSlices(2, 1.0) //
			.addWavelength(0, "DAPI", 447.0) //
			.addWavelength(1, "FITC", 536.0);
	}

	/** Two timepoints, two wells, two sites, two wavelengths, two slices. */
	private static List<PlaneCoordinates> planes() {
		final List<PlaneCoordinates> planes = new ArrayList<>();
		// Deliberately built well-major, the order the converter writes in, so
		// that the index writer has to sort them back into acquisition order.
		for (int column = 2; column <= 3; column++) {
			for (int field = 0; field < 2; field++) {
				for (int t = 0; t < 2; t++) {
					for (int c = 0; c < 2; c++) {
						for (int z = 0; z < 2; z++) {
							planes.add(new PlaneCoordinates(0, "Well", 1, column, field, c,
								c == 0 ? "DAPI" : "FITC", z, t));
						}
					}
				}
			}
		}
		return planes;
	}

	private static String read(final Path file) throws Exception {
		return new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
	}

	private static List<String> filenames(final String xdce) {
		final Matcher matcher = Pattern.compile("filename=\"([^\"]+)\"").matcher(
			xdce);
		final List<String> names = new ArrayList<>();
		while (matcher.find())
			names.add(matcher.group(1));
		return names;
	}
}
