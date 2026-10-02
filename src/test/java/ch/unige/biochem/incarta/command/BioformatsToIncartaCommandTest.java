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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.scijava.Context;
import org.scijava.event.EventHandler;
import org.scijava.event.EventService;
import org.scijava.log.LogService;
import org.scijava.task.TaskService;
import org.scijava.task.event.TaskEvent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Runs the command against a minimal SciJava context: no UI service, so no
 * input harvester is instantiated and the test stays fast and quiet.
 */
public class BioformatsToIncartaCommandTest {

	@Rule
	public final TemporaryFolder folder = new TemporaryFolder();

	@Test
	public void convertsEveryPlaneOfTheInput() throws Exception {
		final Context context = new Context(LogService.class);
		try {
			final BioformatsToIncartaCommand command = command(context,
				"cmd&sizeX=8&sizeY=8&sizeC=2&sizeZ=2.fake");
			command.run();
			assertEquals(4, command.planesWritten);
			// Four planes plus the .xdce index that binds them together.
			assertEquals(5, command.outputDirectory.list().length);
			assertTrue(Stream.of(command.outputDirectory.list()).anyMatch(n -> n
				.endsWith(".xdce")));
		}
		finally {
			context.dispose();
		}
	}

	@Test
	public void reportsProgressThroughATask() throws Exception {
		final Context context = new Context(LogService.class, TaskService.class);
		final TaskListener listener = new TaskListener(-1);
		context.service(EventService.class).subscribe(listener);
		try {
			command(context, "task&sizeX=8&sizeY=8&sizeC=2&sizeZ=3.fake").run();
			assertEquals(6, listener.maximum);
			assertTrue(listener.values.contains(6L));
			assertTrue(listener.finished);
		}
		finally {
			context.dispose();
		}
	}

	@Test
	public void stopsWhenItsTaskIsCanceled() throws Exception {
		final Context context = new Context(LogService.class, TaskService.class);
		final TaskListener listener = new TaskListener(2);
		context.service(EventService.class).subscribe(listener);
		try {
			final BioformatsToIncartaCommand command = command(context,
				"cancel&sizeX=8&sizeY=8&sizeC=2&sizeZ=3.fake");
			command.run();
			assertEquals(0, command.planesWritten);
			assertFalse(Stream.of(command.outputDirectory.list()).anyMatch(n -> n
				.endsWith(".xdce")));
			assertTrue(listener.finished);
		}
		finally {
			context.dispose();
		}
	}

	/** A failure is thrown, so a headless caller cannot mistake it for success. */
	@Test
	public void throwsWhenTheConversionFails() throws Exception {
		final Context context = new Context(LogService.class);
		try {
			final BioformatsToIncartaCommand command = command(context,
				"once&sizeX=8&sizeY=8&sizeC=1.fake");
			command.run();
			command.run(); // the second run would overwrite
			fail("A refused overwrite should fail the command");
		}
		catch (final IllegalStateException e) {
			assertTrue(e.getMessage().contains("Refusing to overwrite"));
		}
		finally {
			context.dispose();
		}
	}

	private BioformatsToIncartaCommand command(final Context context,
		final String fakeName) throws Exception
	{
		final File input = folder.getRoot().toPath().resolve(fakeName).toFile();
		Files.createFile(input.toPath());
		final BioformatsToIncartaCommand command = new BioformatsToIncartaCommand();
		context.inject(command);
		command.inputFile = input;
		command.outputDirectory = folder.newFolder();
		return command;
	}

	/** Records what the task said, and cancels it at a given plane if asked. */
	public static class TaskListener {

		private final long cancelAt;
		final List<Long> values = new ArrayList<>();
		long maximum;
		boolean finished;

		TaskListener(final long cancelAt) {
			this.cancelAt = cancelAt;
		}

		@EventHandler
		public void onEvent(final TaskEvent event) {
			values.add(event.getTask().getProgressValue());
			maximum = Math.max(maximum, event.getTask().getProgressMaximum());
			finished |= event.getTask().isDone();
			// cancel() fires an event of its own, which lands back here.
			if (event.getTask().getProgressValue() == cancelAt && !event.getTask()
				.isCanceled())
			{
				event.getTask().cancel("test");
			}
		}
	}
}
