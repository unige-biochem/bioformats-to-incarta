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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.common.services.ServiceFactory;
import loci.formats.FormatException;
import loci.formats.FormatTools;
import loci.formats.IFormatReader;
import loci.formats.ImageReader;
import loci.formats.MetadataTools;
import loci.formats.meta.IMetadata;
import loci.formats.out.TiffWriter;
import loci.formats.services.OMEXMLService;
import ome.xml.model.primitives.NonNegativeInteger;

/**
 * Reads any Bio-Formats supported file and writes it out as one TIFF per plane,
 * laid out by an {@link IncartaLayout}.
 * <p>
 * The converter keeps no state between calls; instantiate it once and reuse it.
 */
public class IncartaConverter {

	/** Called as the conversion proceeds, so callers can drive a progress bar. */
	public interface Progress {
		void update(int done, int total, String message);
	}

	private final IncartaLayout layout;
	private final ConversionOptions options;

	public IncartaConverter(final IncartaLayout layout) {
		this(layout, new ConversionOptions());
	}

	public IncartaConverter(final IncartaLayout layout,
		final ConversionOptions options)
	{
		this.layout = layout;
		this.options = options;
	}

	/**
	 * Converts {@code input} into {@code outputDirectory}, creating it if needed.
	 *
	 * @return the coordinates of every plane written, in write order
	 */
	public List<PlaneCoordinates> convert(final Path input,
		final Path outputDirectory, final Progress progress) throws IOException,
		FormatException
	{
		Files.createDirectories(outputDirectory);

		final IMetadata source = createOMEXMLMetadata();
		final ImageReader reader = new ImageReader();
		reader.setMetadataStore(source);
		reader.setId(input.toString());

		final List<PlaneCoordinates> written = new ArrayList<>();
		try {
			final Map<Integer, int[]> wells = wellsBySeries(source);
			final int total = countPlanes(reader);
			int done = 0;

			for (int s = 0; s < reader.getSeriesCount(); s++) {
				if (options.getSeries() >= 0 && s != options.getSeries()) continue;
				reader.setSeries(s);

				for (int plane = 0; plane < reader.getImageCount(); plane++) {
					final int[] zct = reader.getZCTCoords(plane);
					final PlaneCoordinates coords = coordinatesOf(source, wells, s,
						zct[1], zct[0], zct[2]);
					final Path target = outputDirectory.resolve(layout.relativePathFor(
						coords));

					if (progress != null) {
						progress.update(done, total, target.getFileName().toString());
					}
					writePlane(reader, plane, coords, target);
					written.add(coords);
					done++;
				}
			}
			layout.writeDatasetMetadata(outputDirectory, written);
			if (progress != null) progress.update(done, total, "done");
		}
		finally {
			reader.close();
		}
		return written;
	}

	private void writePlane(final IFormatReader reader, final int plane,
		final PlaneCoordinates coords, final Path target) throws IOException,
		FormatException
	{
		if (Files.exists(target) && !options.isOverwrite()) {
			throw new IOException("Refusing to overwrite " + target +
				" - enable the overwrite option to replace existing files.");
		}
		Files.createDirectories(target.getParent());
		Files.deleteIfExists(target);

		final IMetadata meta = createOMEXMLMetadata();
		MetadataTools.populateMetadata(meta, 0, coords.toString(), reader
			.isLittleEndian(), "XYZCT", FormatTools.getPixelTypeString(reader
				.getPixelType()), reader.getSizeX(), reader.getSizeY(), 1, 1, 1, reader
					.getRGBChannelCount());

		final TiffWriter writer = new TiffWriter();
		try {
			writer.setMetadataRetrieve(meta);
			writer.setBigTiff(options.isBigTiff());
			writer.setId(target.toString());
			writer.setCompression(options.getCompression());
			writer.saveBytes(0, reader.openBytes(plane));
		}
		finally {
			writer.close();
		}
	}

	private static PlaneCoordinates coordinatesOf(final IMetadata source,
		final Map<Integer, int[]> wells, final int series, final int channel,
		final int z, final int t)
	{
		final int[] well = wells.get(series);
		final int row = well == null ? -1 : well[0];
		final int column = well == null ? -1 : well[1];
		final int field = well == null ? 0 : well[2];
		return new PlaneCoordinates(series, imageName(source, series), row, column,
			field, channel, channelName(source, series, channel), z, t);
	}

	private static String imageName(final IMetadata meta, final int series) {
		try {
			final String name = meta.getImageName(series);
			return name == null ? "series" + series : name;
		}
		catch (final Exception e) {
			return "series" + series;
		}
	}

	private static String channelName(final IMetadata meta, final int series,
		final int channel)
	{
		try {
			final String name = meta.getChannelName(series, channel);
			return name == null ? "channel" + channel : name;
		}
		catch (final Exception e) {
			return "channel" + channel;
		}
	}

	/**
	 * Maps each series index to {@code {wellRow, wellColumn, fieldIndex}}, for
	 * sources that carry plate metadata. Series with no plate entry are absent.
	 */
	private static Map<Integer, int[]> wellsBySeries(final IMetadata meta) {
		final Map<Integer, int[]> bySeries = new HashMap<>();
		final int plates;
		try {
			plates = meta.getPlateCount();
		}
		catch (final Exception e) {
			return bySeries;
		}
		for (int p = 0; p < plates; p++) {
			for (int w = 0; w < meta.getWellCount(p); w++) {
				final Integer row = value(meta.getWellRow(p, w));
				final Integer column = value(meta.getWellColumn(p, w));
				if (row == null || column == null) continue;
				for (int ws = 0; ws < meta.getWellSampleCount(p, w); ws++) {
					final int series = seriesOf(meta.getWellSampleImageRef(p, w, ws));
					if (series >= 0) bySeries.put(series, new int[] { row, column, ws });
				}
			}
		}
		return bySeries;
	}

	private static Integer value(final NonNegativeInteger i) {
		return i == null ? null : i.getValue();
	}

	/** Turns an image reference such as {@code Image:3} into the series index. */
	private static int seriesOf(final String imageRef) {
		if (imageRef == null) return -1;
		final int colon = imageRef.lastIndexOf(':');
		try {
			return Integer.parseInt(imageRef.substring(colon + 1));
		}
		catch (final NumberFormatException e) {
			return -1;
		}
	}

	private int countPlanes(final IFormatReader reader) {
		int total = 0;
		final int current = reader.getSeries();
		for (int s = 0; s < reader.getSeriesCount(); s++) {
			if (options.getSeries() >= 0 && s != options.getSeries()) continue;
			reader.setSeries(s);
			total += reader.getImageCount();
		}
		reader.setSeries(current);
		return total;
	}

	private static IMetadata createOMEXMLMetadata() throws IOException {
		try {
			return new ServiceFactory().getInstance(OMEXMLService.class)
				.createOMEXMLMetadata();
		}
		catch (final DependencyException | ServiceException e) {
			throw new IOException("Could not initialise OME-XML metadata", e);
		}
	}

	/** Convenience for the common case: convert one file into one directory. */
	public static List<PlaneCoordinates> convert(final File input,
		final File outputDirectory) throws IOException, FormatException
	{
		final String base = DefaultIncartaLayout.baseNameOf(input.getName());
		return new IncartaConverter(new DefaultIncartaLayout(base)).convert(input
			.toPath(), outputDirectory.toPath(), null);
	}
}
