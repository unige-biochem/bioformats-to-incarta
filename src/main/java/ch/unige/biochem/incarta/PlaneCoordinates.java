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
 * A single 2D plane of the source dataset: where it sits (series, well, field,
 * channel, z, t) and what the acquisition recorded about it (stage position,
 * exposure, pixel statistics).
 * <p>
 * All indices are zero-based. {@link #wellRow()} and {@link #wellColumn()} are
 * {@code -1} when the source carries no plate metadata. The acquisition values
 * are optional: they are {@code null} when the source does not describe them,
 * and a layout must then decide whether to omit or default them.
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

	private Double positionXUm;
	private Double positionYUm;
	private Double positionZUm;
	private Double exposureMs;
	private Double timestampSeconds;
	private Double min;
	private Double max;
	private Double mean;
	private boolean integerPixels = true;

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

	/** Stage position of this plane, in micrometres. */
	public PlaneCoordinates positionUm(final Double x, final Double y,
		final Double z)
	{
		this.positionXUm = x;
		this.positionYUm = y;
		this.positionZUm = z;
		return this;
	}

	public PlaneCoordinates exposureMs(final Double exposureMs) {
		this.exposureMs = exposureMs;
		return this;
	}

	/** Acquisition time of this plane as seconds since the Unix epoch. */
	public PlaneCoordinates timestampSeconds(final Double timestampSeconds) {
		this.timestampSeconds = timestampSeconds;
		return this;
	}

	/**
	 * Pixel statistics of the plane.
	 *
	 * @param integerPixels whether the pixel type is integral, so that the
	 *          statistics can be written without a fractional part
	 */
	public PlaneCoordinates statistics(final Double min, final Double max,
		final Double mean, final boolean integerPixels)
	{
		this.min = min;
		this.max = max;
		this.mean = mean;
		this.integerPixels = integerPixels;
		return this;
	}

	public Double positionXUm() { return positionXUm; }
	public Double positionYUm() { return positionYUm; }
	public Double positionZUm() { return positionZUm; }
	public Double exposureMs() { return exposureMs; }
	public Double timestampSeconds() { return timestampSeconds; }
	public Double min() { return min; }
	public Double max() { return max; }
	public Double mean() { return mean; }
	public boolean hasIntegerPixels() { return integerPixels; }

	/** True when the source dataset described a plate this plane belongs to. */
	public boolean hasWell() { return wellRow >= 0 && wellColumn >= 0; }

	@Override
	public String toString() {
		return "series=" + series + " well=" + wellRow + "," + wellColumn +
			" field=" + field + " c=" + channel + " z=" + z + " t=" + t;
	}
}
