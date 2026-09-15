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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Plate-level facts about a dataset being converted: everything an
 * {@link IncartaLayout} needs for its index file that is not per-plane.
 * <p>
 * Built by {@link IncartaConverter} from the source's OME metadata. Fields that
 * the source does not describe stay {@code null}, and it is up to the layout to
 * decide whether to omit the corresponding attribute or substitute a default.
 */
public class DatasetDescription {

	/** One acquisition channel, as the plate protocol describes it. */
	public static class Wavelength {

		private final int index;
		private final String name;
		private final Double emissionNm;

		public Wavelength(final int index, final String name,
			final Double emissionNm)
		{
			this.index = index;
			this.name = name;
			this.emissionNm = emissionNm;
		}

		public int index() { return index; }
		public String name() { return name; }

		/** Emission wavelength in nanometres, or {@code null} if unknown. */
		public Double emissionNm() { return emissionNm; }
	}

	private String plateModel;
	private int plateRows = 1;
	private int plateColumns = 1;
	private Double wellOriginXMm;
	private Double wellOriginYMm;
	private int sizeX;
	private int sizeY;
	private Double pixelWidthUm;
	private Double pixelHeightUm;
	private String objectiveName;
	private Double magnification;
	private int binning = 1;
	private int timepointCount = 1;
	private int zSliceCount = 1;
	private Double zStepUm;
	private Instant acquisitionTime;
	private final List<Wavelength> wavelengths = new ArrayList<>();

	/** Vendor name of the plate type, e.g. {@code Ibidi-uPlate-96}. */
	public DatasetDescription plateModel(final String plateModel) {
		this.plateModel = plateModel;
		return this;
	}

	public DatasetDescription plateSize(final int rows, final int columns) {
		this.plateRows = rows;
		this.plateColumns = columns;
		return this;
	}

	/** Centre of well A1 relative to the plate corner, in millimetres. */
	public DatasetDescription wellOriginMm(final Double x, final Double y) {
		this.wellOriginXMm = x;
		this.wellOriginYMm = y;
		return this;
	}

	public DatasetDescription frameSize(final int sizeX, final int sizeY) {
		this.sizeX = sizeX;
		this.sizeY = sizeY;
		return this;
	}

	public DatasetDescription pixelSizeUm(final Double width, final Double height) {
		this.pixelWidthUm = width;
		this.pixelHeightUm = height;
		return this;
	}

	public DatasetDescription objective(final String name,
		final Double magnification)
	{
		this.objectiveName = name;
		this.magnification = magnification;
		return this;
	}

	public DatasetDescription binning(final int binning) {
		this.binning = binning;
		return this;
	}

	public DatasetDescription timepointCount(final int timepointCount) {
		this.timepointCount = timepointCount;
		return this;
	}

	public DatasetDescription zSlices(final int zSliceCount, final Double zStepUm) {
		this.zSliceCount = zSliceCount;
		this.zStepUm = zStepUm;
		return this;
	}

	public DatasetDescription acquisitionTime(final Instant acquisitionTime) {
		this.acquisitionTime = acquisitionTime;
		return this;
	}

	public DatasetDescription addWavelength(final int index, final String name,
		final Double emissionNm)
	{
		wavelengths.add(new Wavelength(index, name, emissionNm));
		return this;
	}

	public String plateModel() { return plateModel; }
	public int plateRows() { return plateRows; }
	public int plateColumns() { return plateColumns; }
	public Double wellOriginXMm() { return wellOriginXMm; }
	public Double wellOriginYMm() { return wellOriginYMm; }
	public int sizeX() { return sizeX; }
	public int sizeY() { return sizeY; }
	public Double pixelWidthUm() { return pixelWidthUm; }
	public Double pixelHeightUm() { return pixelHeightUm; }
	public String objectiveName() { return objectiveName; }
	public Double magnification() { return magnification; }
	public int binning() { return binning; }
	public int timepointCount() { return timepointCount; }
	public int zSliceCount() { return zSliceCount; }
	public Double zStepUm() { return zStepUm; }
	public Instant acquisitionTime() { return acquisitionTime; }
	public List<Wavelength> wavelengths() { return wavelengths; }
}
