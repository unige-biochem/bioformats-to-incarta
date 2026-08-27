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

/**
 * Placeholder layout: one TIFF per plane in a flat directory, named
 * {@code <base>_<well>_s<field>_w<channel>_z<z>_t<t>.tif}.
 * <p>
 * TODO: replace with the layout IN Carta actually expects (file naming,
 * directory nesting and the accompanying metadata file). This class exists so
 * the reading and writing pipeline can be exercised end to end in the meantime;
 * the output it produces is <em>not</em> guaranteed to import.
 */
public class DefaultIncartaLayout implements IncartaLayout {

	private static final String[] ROW_LABELS = {
		"A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O",
		"P"
	};

	private final String baseName;

	public DefaultIncartaLayout(final String baseName) {
		this.baseName = baseName;
	}

	@Override
	public String relativePathFor(final PlaneCoordinates plane) {
		final StringBuilder name = new StringBuilder(baseName);
		if (plane.hasWell()) name.append('_').append(wellLabel(plane));
		else name.append("_s").append(plane.series());
		name.append("_f").append(plane.field());
		name.append("_w").append(plane.channel() + 1);
		name.append("_z").append(plane.z());
		name.append("_t").append(plane.t());
		return name.append(".tif").toString();
	}

	/**
	 * Strips the extension off a file name, to use it as the prefix of every
	 * converted plane: { plate1.nd2} -> { plate1}.
	 */
	public static String baseNameOf(final String fileName) {
		final int dot = fileName.lastIndexOf('.');
		return dot > 0 ? fileName.substring(0, dot) : fileName;
	}

	/** Well label in the usual {@code A01} form. */
	static String wellLabel(final PlaneCoordinates plane) {
		final int row = plane.wellRow();
		final String rowLabel = row < ROW_LABELS.length ? ROW_LABELS[row]
			: String.valueOf(row);
		return String.format("%s%02d", rowLabel, plane.wellColumn() + 1);
	}
}
