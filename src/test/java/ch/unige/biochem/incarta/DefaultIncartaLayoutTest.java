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

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DefaultIncartaLayoutTest {

	private final DefaultIncartaLayout layout = new DefaultIncartaLayout("plate1");

	@Test
	public void namesPlanesOfAPlateByWell() {
		final PlaneCoordinates plane = new PlaneCoordinates(7, "Well B3", 1, 2, 0, 1,
			"DAPI", 0, 3);
		assertEquals("plate1_B03_f0_w2_z0_t3.tif", layout.relativePathFor(plane));
	}

	@Test
	public void fallsBackToTheSeriesIndexWithoutPlateMetadata() {
		final PlaneCoordinates plane = new PlaneCoordinates(4, "series4", -1, -1, 0,
			0, "channel0", 2, 0);
		assertEquals("plate1_s4_f0_w1_z2_t0.tif", layout.relativePathFor(plane));
	}
}
