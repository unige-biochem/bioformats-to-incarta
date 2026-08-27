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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
			new DefaultIncartaLayout("stack")).convert(input, output, null);

		assertEquals(6, planes.size());
		assertEquals(6, tiffNames(output).size());
		assertTrue(tiffNames(output).contains("stack_s0_f0_w2_z2_t0.tif"));
	}

	@Test
	public void convertsOnlyTheRequestedSeries() throws Exception {
		final Path input = fake("multi&sizeX=8&sizeY=8&sizeC=1&series=3.fake");
		final Path output = folder.newFolder("one-series").toPath();

		final List<PlaneCoordinates> planes = new IncartaConverter(
			new DefaultIncartaLayout("multi"), new ConversionOptions().series(1))
				.convert(input, output, null);

		assertEquals(1, planes.size());
		assertEquals(1, planes.get(0).series());
	}

	@Test(expected = IOException.class)
	public void refusesToOverwriteByDefault() throws Exception {
		final Path input = fake("once&sizeX=8&sizeY=8&sizeC=1.fake");
		final Path output = folder.newFolder("twice").toPath();
		final IncartaConverter converter = new IncartaConverter(
			new DefaultIncartaLayout("once"));

		converter.convert(input, output, null);
		converter.convert(input, output, null);
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
				.collect(Collectors.toList());
		}
	}
}
