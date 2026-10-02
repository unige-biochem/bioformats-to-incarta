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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import ome.xml.model.primitives.Color;
import org.junit.Test;

/**
 * The colour to wavelength table, which decides what colour IN Carta gives a
 * channel the source states no emission wavelength for.
 */
public class EmissionWavelengthsTest {

	private static Color rgb(final int r, final int g, final int b) {
		return new Color(r, g, b, 255);
	}

	@Test
	public void readsThePrimaryChannelColours() {
		assertEquals(450.0, EmissionWavelengths.ofColour(rgb(0, 0, 255)), 0);
		assertEquals(520.0, EmissionWavelengths.ofColour(rgb(0, 255, 0)), 0);
		assertEquals(620.0, EmissionWavelengths.ofColour(rgb(255, 0, 0)), 0);
		assertEquals(490.0, EmissionWavelengths.ofColour(rgb(0, 255, 255)), 0);
		assertEquals(570.0, EmissionWavelengths.ofColour(rgb(255, 255, 0)), 0);
	}

	/** Magenta is not spectral; microscopy uses it for the far-red dyes. */
	@Test
	public void readsMagentaAsFarRed() {
		assertEquals(670.0, EmissionWavelengths.ofColour(rgb(255, 0, 255)), 0);
	}

	@Test
	public void matchesOffHuesToTheNearestEntry() {
		// A dim, unsaturated green is still green.
		assertEquals(520.0, EmissionWavelengths.ofColour(rgb(40, 90, 40)), 0);
		// Orange sits between red and yellow, and is nearer red.
		assertEquals(620.0, EmissionWavelengths.ofColour(rgb(255, 100, 0)), 0);
	}

	@Test
	public void hasNoWavelengthForAColourlessColour() {
		assertNull(EmissionWavelengths.ofColour(null));
		assertNull(EmissionWavelengths.ofColour(rgb(0, 0, 0)));
		assertNull(EmissionWavelengths.ofColour(rgb(255, 255, 255)));
		assertNull(EmissionWavelengths.ofColour(rgb(128, 128, 128)));
	}

	/** With no colour either, channels get different wavelengths by index. */
	@Test
	public void fallsBackToThePaletteByChannelIndex() {
		assertEquals(450.0, EmissionWavelengths.forChannel(null, 0), 0);
		assertEquals(520.0, EmissionWavelengths.forChannel(null, 1), 0);
		assertEquals(405.0, EmissionWavelengths.forChannel(null, 6), 0);
		// The palette holds seven entries and then repeats.
		assertEquals(450.0, EmissionWavelengths.forChannel(null, 7), 0);
		assertEquals(520.0, EmissionWavelengths.forChannel(null, 8), 0);
	}

	@Test
	public void prefersTheColourOverThePalette() {
		assertEquals(620.0, EmissionWavelengths.forChannel(rgb(255, 0, 0), 0), 0);
	}
}
