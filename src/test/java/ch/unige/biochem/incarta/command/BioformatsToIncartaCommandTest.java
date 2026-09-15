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
package ch.unige.biochem.incarta.command;

import java.io.File;
import java.nio.file.Files;
import java.util.stream.Stream;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.scijava.Context;
import org.scijava.log.LogService;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Runs the command against a minimal SciJava context: no UI service, so no
 * input harvester is instantiated and the test stays fast and quiet.
 */
public class BioformatsToIncartaCommandTest {

	@Rule
	public final TemporaryFolder folder = new TemporaryFolder();

	@Test
	public void convertsEveryPlaneOfTheInput() throws Exception {
		final File input = folder.getRoot().toPath().resolve(
			"cmd&sizeX=8&sizeY=8&sizeC=2&sizeZ=2.fake").toFile();
		Files.createFile(input.toPath());
		final File output = folder.newFolder("converted");

		final Context context = new Context(LogService.class);
		try {
			final BioformatsToIncartaCommand command =
				new BioformatsToIncartaCommand();
			context.inject(command);
			command.inputFile = input;
			command.outputDirectory = output;
			command.run();
			assertEquals(4, command.planesWritten);
			// Four planes plus the .xdce index that binds them together.
			assertEquals(5, output.list().length);
			assertTrue(Stream.of(output.list()).anyMatch(n -> n.endsWith(".xdce")));
		}
		finally {
			context.dispose();
		}
	}
}
