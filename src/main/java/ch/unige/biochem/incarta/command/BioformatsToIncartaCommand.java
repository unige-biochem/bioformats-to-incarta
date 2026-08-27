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
import java.util.List;

import ch.unige.biochem.incarta.ConversionOptions;
import ch.unige.biochem.incarta.DefaultIncartaLayout;
import ch.unige.biochem.incarta.IncartaConverter;
import ch.unige.biochem.incarta.PlaneCoordinates;

import net.imagej.ImageJ;
import org.scijava.ItemIO;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

/**
 * Converts any Bio-Formats supported image file into an IN Carta compatible
 * format.
 */
@Plugin(type = Command.class, menuPath = "Plugins>UNIGE>Bio-Formats to IN Carta",
	description = "Converts any Bio-Formats supported image file into an IN Carta compatible format.")
public class BioformatsToIncartaCommand implements Command {

	@Parameter
	LogService logger;

	@Parameter(required = false)
	StatusService status;

	@Parameter(label = "Input image", description = "Any file Bio-Formats can open",
		style = "open")
	File inputFile;

	@Parameter(label = "Output folder", description = "Where the converted dataset is written",
		style = "directory")
	File outputDirectory;

	@Parameter(label = "Series", description = "Series to convert; -1 converts them all",
		min = "-1", required = false)
	int series = -1;

	@Parameter(label = "Compression", choices = { "LZW", "Uncompressed",
		"JPEG-2000", "JPEG-2000 Lossy", "zlib" }, required = false)
	String compression = "LZW";

	@Parameter(label = "BigTIFF", description = "Needed for planes above the 4 GB TIFF limit",
		required = false)
	boolean bigTiff = false;

	@Parameter(label = "Overwrite existing files", required = false)
	boolean overwrite = false;

	@Parameter(type = ItemIO.OUTPUT, label = "Planes written")
	int planesWritten;

	@Override
	public void run() {
		final ConversionOptions options = new ConversionOptions() //
			.series(series) //
			.compression(compression) //
			.bigTiff(bigTiff) //
			.overwrite(overwrite);

		final String base = DefaultIncartaLayout.baseNameOf(inputFile.getName());
		final IncartaConverter converter = new IncartaConverter(
			new DefaultIncartaLayout(base), options);

		try {
			final List<PlaneCoordinates> planes = converter.convert(inputFile.toPath(),
				outputDirectory.toPath(), (done, total, message) -> {
					if (status != null) status.showStatus(done, total, message);
				});
			planesWritten = planes.size();
			logger.info("Wrote " + planesWritten + " planes to " + outputDirectory);
		}
		catch (final Exception e) {
			logger.error("Conversion of " + inputFile + " failed", e);
			if (status != null) status.showStatus("Conversion failed: " + e.getMessage());
		}
	}

	/** Launches Fiji and this command - handy for debugging from the IDE. */
	public static void main(String... args) {
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();
		ij.command().run(BioformatsToIncartaCommand.class, true);
	}
}
