/*
 * MIT License
 *
 * Copyright (c) 2026 University of Geneva, Department of Biochemistry
 *
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
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */

package ch.unige.biochem.incarta;

import ome.xml.model.primitives.Color;

/**
 * Emission wavelengths for channels whose source does not state one.
 * <p>
 * IN Carta colours a channel from the emission wavelength in its
 * {@code <EmissionFilter>}: the {@code .xdce} has no colour element at all, and
 * neither has the {@code .JDCE} IN Carta writes from it. A trial import of
 * three channels that all carried the same placeholder wavelength showed three
 * green channels. So the only way a source's channel colours survive the
 * conversion is to turn each colour back into a wavelength.
 * <p>
 * That inverse is not a function. Many colours are not spectral at all
 * (magenta, white, pastels), display colours are chosen by whoever acquired the
 * image rather than measured, and a given hue covers a wide band of
 * wavelengths. So this class does not compute anything: it picks the nearest
 * entry from a table of conventional fluorescence-channel colours, and says so.
 * Every number it returns is an approximation of a display colour, never a
 * measurement.
 */
final class EmissionWavelengths {

	/**
	 * Conventional display colours and the emission wavelength each one stands
	 * for, as hue in degrees. Hue alone decides the match: saturation and
	 * brightness say how the channel was displayed, not which dye it was.
	 * <p>
	 * Magenta is the awkward one. It is not a spectral colour, and microscopy
	 * uses it for far-red dyes (Cy5 and friends), so it maps past red rather
	 * than between blue and red where its hue sits.
	 */
	private static final double[][] ANCHORS = { //
		{ 0, 620 }, // red
		{ 60, 570 }, // yellow
		{ 120, 520 }, // green
		{ 180, 490 }, // cyan
		{ 240, 450 }, // blue
		{ 270, 405 }, // violet
		{ 300, 670 }, // magenta - far red by convention, not by spectrum
	};

	/**
	 * Wavelengths handed out, in order, to channels with no colour either, so
	 * that such channels at least look different from one another. Channel 7
	 * starts again at the first entry.
	 */
	private static final double[] PALETTE = { 450, 520, 620, 670, 490, 570,
		405 };

	/** A colour has to be this saturated to be read as a channel colour. */
	private static final double MIN_SATURATION = 0.1;

	private EmissionWavelengths() {
		// static utility
	}

	/**
	 * The wavelength to declare for a channel the source gives no emission
	 * wavelength for: from its display colour when it has a usable one, else
	 * from the palette, by channel index.
	 */
	static double forChannel(final Color colour, final int channelIndex) {
		final Double fromColour = ofColour(colour);
		return fromColour != null ? fromColour : PALETTE[Math.abs(
			channelIndex) % PALETTE.length];
	}

	/**
	 * The tabulated wavelength whose hue is closest to this colour, or
	 * {@code null} when the colour carries no hue to match: absent, black, or
	 * too grey to have been meant as a channel colour.
	 */
	static Double ofColour(final Color colour) {
		if (colour == null) return null;
		final double r = colour.getRed() / 255.0;
		final double g = colour.getGreen() / 255.0;
		final double b = colour.getBlue() / 255.0;
		final double max = Math.max(r, Math.max(g, b));
		final double min = Math.min(r, Math.min(g, b));
		if (max <= 0 || (max - min) / max < MIN_SATURATION) return null;

		final double hue = hue(r, g, b, max, max - min);
		double best = ANCHORS[0][1];
		double bestDistance = Double.MAX_VALUE;
		for (final double[] anchor : ANCHORS) {
			final double distance = Math.abs(((hue - anchor[0] + 540) % 360) - 180);
			if (distance < bestDistance) {
				bestDistance = distance;
				best = anchor[1];
			}
		}
		return best;
	}

	/** Hue in degrees, the usual way round: 0 red, 120 green, 240 blue. */
	private static double hue(final double r, final double g, final double b,
		final double max, final double chroma)
	{
		final double hue;
		if (max == r) hue = 60 * (((g - b) / chroma) % 6);
		else if (max == g) hue = 60 * ((b - r) / chroma + 2);
		else hue = 60 * ((r - g) / chroma + 4);
		return (hue + 360) % 360;
	}
}
