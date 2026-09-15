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

/** Knobs for a single {@link IncartaConverter} run. */
public class ConversionOptions {

	private boolean overwrite = false;
	private int series = -1;
	private boolean bigTiff = false;
	private String compression = "Uncompressed";

	/** Overwrite files that already exist in the output directory. */
	public ConversionOptions overwrite(final boolean overwrite) {
		this.overwrite = overwrite;
		return this;
	}

	/** Convert only this series; {@code -1} (the default) converts them all. */
	public ConversionOptions series(final int series) {
		this.series = series;
		return this;
	}

	/** Write BigTIFF, needed for planes beyond the 4 GB TIFF limit. */
	public ConversionOptions bigTiff(final boolean bigTiff) {
		this.bigTiff = bigTiff;
		return this;
	}

	/**
	 * TIFF compression: {@code Uncompressed} (the default, matching the
	 * reference ImageXpress export), {@code LZW}, {@code JPEG-2000}...
	 */
	public ConversionOptions compression(final String compression) {
		this.compression = compression;
		return this;
	}

	public boolean isOverwrite() { return overwrite; }
	public int getSeries() { return series; }
	public boolean isBigTiff() { return bigTiff; }
	public String getCompression() { return compression; }
}
