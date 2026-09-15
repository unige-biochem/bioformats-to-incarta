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
import java.nio.file.Path;
import java.util.List;

/**
 * Decides how a converted dataset is laid out on disk so that IN Carta can
 * import it: the relative path of every plane file, and whatever plate-level
 * index file has to sit next to them.
 * <p>
 * Everything IN Carta specific lives behind this interface;
 * {@link IncartaConverter} only knows how to read planes and hand them over.
 */
public interface IncartaLayout {

	/**
	 * Relative path, under the output directory, of the file holding this plane.
	 * Use forward slashes for sub-directories; the converter creates them.
	 */
	String relativePathFor(PlaneCoordinates plane);

	/**
	 * Called once after every plane has been written, to emit the index file the
	 * importer expects alongside the images.
	 * <p>
	 * Implementations must name the images by calling
	 * {@link #relativePathFor(PlaneCoordinates)} again rather than recomputing
	 * the names, so that the index and the files on disk cannot drift apart.
	 *
	 * @param outputDirectory root of the converted dataset
	 * @param dataset plate-level metadata of the source
	 * @param planes every plane that was written, in write order
	 */
	default void writeDatasetMetadata(final Path outputDirectory,
		final DatasetDescription dataset, final List<PlaneCoordinates> planes)
		throws IOException
	{
		// No index file by default.
	}
}
