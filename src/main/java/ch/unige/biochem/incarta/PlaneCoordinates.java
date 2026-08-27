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
 * Where a single 2D plane sits inside the source dataset: which series it came
 * from, which well and field of the plate (when the source describes a plate),
 * and its channel / z / timepoint indices.
 * <p>
 * All indices are zero-based. {@link #wellRow()} and {@link #wellColumn()} are
 * {@code -1} when the source carries no plate metadata.
 */
public class PlaneCoordinates {

	private final int series;
	private final String seriesName;
	private final int wellRow;
	private final int wellColumn;
	private final int field;
	private final int channel;
	private final String channelName;
	private final int z;
	private final int t;

	public PlaneCoordinates(final int series, final String seriesName,
		final int wellRow, final int wellColumn, final int field,
		final int channel, final String channelName, final int z, final int t)
	{
		this.series = series;
		this.seriesName = seriesName;
		this.wellRow = wellRow;
		this.wellColumn = wellColumn;
		this.field = field;
		this.channel = channel;
		this.channelName = channelName;
		this.z = z;
		this.t = t;
	}

	public int series() { return series; }
	public String seriesName() { return seriesName; }
	public int wellRow() { return wellRow; }
	public int wellColumn() { return wellColumn; }
	public int field() { return field; }
	public int channel() { return channel; }
	public String channelName() { return channelName; }
	public int z() { return z; }
	public int t() { return t; }

	/** True when the source dataset described a plate this plane belongs to. */
	public boolean hasWell() { return wellRow >= 0 && wellColumn >= 0; }

	@Override
	public String toString() {
		return "series=" + series + " well=" + wellRow + "," + wellColumn +
			" field=" + field + " c=" + channel + " z=" + z + " t=" + t;
	}
}
