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
import java.util.concurrent.CancellationException;

import ch.unige.biochem.incarta.ConversionOptions;
import ch.unige.biochem.incarta.ImageXpressLayout;
import ch.unige.biochem.incarta.IncartaConverter;
import ch.unige.biochem.incarta.PlaneCoordinates;

import org.scijava.ItemIO;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.task.Task;
import org.scijava.task.TaskService;

/**
 * Converts any Bio-Formats supported image file into an IN Carta compatible
 * format.
 * <p>
 * Progress is reported through a {@link Task} when a {@link TaskService} is
 * present, which is also how the conversion is stopped: cancelling the task
 * stops it before the next plane. A failure is thrown rather than logged, so
 * whoever runs the command - Fiji's menu or a headless caller holding the
 * future - sees it as a failure.
 */
@Plugin(type = Command.class, menuPath = "Plugins>UNIGE>Bio-Formats to IN Carta",
	description = "Converts any Bio-Formats supported image file into an IN Carta compatible format.")
public class BioformatsToIncartaCommand implements Command {

	@Parameter
	LogService logger;

	@Parameter(required = false)
	StatusService status;

	@Parameter(required = false)
	TaskService taskService;

	@Parameter(label = "Input image", description = "Any file Bio-Formats can open",
		style = "open")
	File inputFile;

	@Parameter(label = "Output folder", description = "Where the converted dataset is written",
		style = "directory")
	File outputDirectory;

	@Parameter(label = "Series", description = "Series to convert; -1 converts them all",
		min = "-1", required = false)
	int series = -1;

	@Parameter(label = "Compression", choices = { "Uncompressed", "LZW",
		"JPEG-2000", "JPEG-2000 Lossy", "zlib" }, required = false)
	String compression = "Uncompressed";

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

		final String plate = ImageXpressLayout.baseNameOf(inputFile.getName());
		final IncartaConverter converter = new IncartaConverter(
			new ImageXpressLayout(plate), options);

		final Task task = taskService == null ? null : taskService.createTask(
			"Bio-Formats to IN Carta: " + inputFile.getName());
		if (task != null) {
			// The default callback cancels a future this task does not have; the
			// converter polls isCanceled() between planes instead.
			task.setCancelCallBack(() -> {});
			task.start();
		}

		try {
			final List<PlaneCoordinates> planes = converter.convert(inputFile.toPath(),
				outputDirectory.toPath(), new IncartaConverter.Progress() {

					@Override
					public void update(final int done, final int total,
						final String message)
					{
						if (task != null) {
							// Each setter fires an event: the message goes first, so
							// no listener sees new numbers next to an old message.
							task.setStatusMessage(message);
							task.setProgressMaximum(total);
							task.setProgressValue(done);
						}
						if (status != null) status.showStatus(done, total, message);
					}

					@Override
					public boolean isCanceled() {
						return task != null && task.isCanceled();
					}
				});
			planesWritten = planes.size();
			logger.info("Wrote " + planesWritten + " planes to " + outputDirectory);
		}
		catch (final CancellationException e) {
			logger.warn("Conversion of " + inputFile + " stopped: " + e
				.getMessage() + ". " + outputDirectory +
				" holds a partial dataset, without its .xdce index.");
			if (status != null) status.showStatus("Conversion stopped");
		}
		catch (final Exception e) {
			if (status != null) status.showStatus("Conversion failed: " + e.getMessage());
			throw new IllegalStateException("Conversion of " + inputFile +
				" failed: " + e.getMessage(), e);
		}
		finally {
			if (task != null) task.finish();
		}
	}
}
