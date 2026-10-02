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
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import loci.common.DataTools;
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

import ome.units.UNITS;
import ome.units.quantity.Length;
import ome.units.quantity.Time;
import ome.xml.model.primitives.NonNegativeInteger;
import ome.xml.model.primitives.PositiveInteger;

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
			refuseMultiplePlates(source);
			final Map<Integer, int[]> wells = wellsBySeries(source);
			final DatasetDescription dataset = describeDataset(source, reader);
			final int total = countPlanes(reader);
			int done = 0;

			for (int s = 0; s < reader.getSeriesCount(); s++) {
				if (options.getSeries() >= 0 && s != options.getSeries()) continue;
				reader.setSeries(s);
				final Map<Long, Integer> planeMetadata = planeMetadataIndex(source, s);

				for (int plane = 0; plane < reader.getImageCount(); plane++) {
					final int[] zct = reader.getZCTCoords(plane);
					final PlaneCoordinates coords = coordinatesOf(source, wells, s,
						zct[1], zct[0], zct[2]);
					describePlane(source, s, planeMetadata, coords);
					final Path target = outputDirectory.resolve(layout.relativePathFor(
						coords));

					if (progress != null) {
						progress.update(done, total, target.getFileName().toString());
					}
					if (Files.exists(target) && !options.isOverwrite()) {
						throw new IOException("Refusing to overwrite " + target +
							" - enable the overwrite option to replace existing files.");
					}

					final byte[] bytes = reader.openBytes(plane);
					summarise(reader, bytes, coords);
					writePlane(reader, source, s, bytes, target);
					written.add(coords);
					done++;
				}
			}
			layout.writeDatasetMetadata(outputDirectory, dataset, written);
			if (progress != null) progress.update(done, total, "done");
		}
		finally {
			reader.close();
		}
		return written;
	}

	private void writePlane(final IFormatReader reader, final IMetadata source,
		final int series, final byte[] bytes, final Path target) throws IOException,
		FormatException
	{
		Files.createDirectories(target.getParent());
		Files.deleteIfExists(target);

		final IMetadata meta = createOMEXMLMetadata();
		MetadataTools.populateMetadata(meta, 0, target.getFileName().toString(),
			reader.isLittleEndian(), "XYZCT", FormatTools.getPixelTypeString(reader
				.getPixelType()), reader.getSizeX(), reader.getSizeY(), 1, 1, 1, reader
					.getRGBChannelCount());
		copyPhysicalSize(source, series, meta);

		final TiffWriter writer = new TiffWriter();
		try {
			writer.setMetadataRetrieve(meta);
			writer.setBigTiff(options.isBigTiff());
			writer.setId(target.toString());
			writer.setCompression(options.getCompression());
			writer.saveBytes(0, bytes);
		}
		finally {
			writer.close();
		}
	}

	/**
	 * Carries the source's pixel calibration onto the written plane. Without it
	 * every converted TIFF claims an unknown pixel size, and the importer has no
	 * way to recover the physical scale from the image alone.
	 */
	private static void copyPhysicalSize(final IMetadata source, final int series,
		final IMetadata target)
	{
		final Length x = physicalSize(source, series, 0);
		final Length y = physicalSize(source, series, 1);
		final Length z = physicalSize(source, series, 2);
		if (x != null) target.setPixelsPhysicalSizeX(x, 0);
		if (y != null) target.setPixelsPhysicalSizeY(y, 0);
		if (z != null) target.setPixelsPhysicalSizeZ(z, 0);
	}

	private static Length physicalSize(final IMetadata meta, final int series,
		final int axis)
	{
		try {
			switch (axis) {
				case 0:
					return meta.getPixelsPhysicalSizeX(series);
				case 1:
					return meta.getPixelsPhysicalSizeY(series);
				default:
					return meta.getPixelsPhysicalSizeZ(series);
			}
		}
		catch (final Exception e) {
			return null;
		}
	}

	// -- dataset level metadata --

	/** Plate level facts the layout needs for its index file. */
	private DatasetDescription describeDataset(final IMetadata source,
		final IFormatReader reader)
	{
		final DatasetDescription dataset = new DatasetDescription();
		final int current = reader.getSeries();
		final int first = firstConvertedSeries(reader);
		try {
			reader.setSeries(first);
			dataset.frameSize(reader.getSizeX(), reader.getSizeY());

			int timepoints = 1, slices = 1;
			for (int s = 0; s < reader.getSeriesCount(); s++) {
				if (options.getSeries() >= 0 && s != options.getSeries()) continue;
				reader.setSeries(s);
				timepoints = Math.max(timepoints, reader.getSizeT());
				slices = Math.max(slices, reader.getSizeZ());
			}
			reader.setSeries(first);
			dataset.timepointCount(timepoints);
			dataset.zSlices(slices, micrometres(physicalSize(source, first, 2)));
			dataset.pixelSizeUm(micrometres(physicalSize(source, first, 0)),
				micrometres(physicalSize(source, first, 1)));

			for (int c = 0; c < reader.getEffectiveSizeC(); c++) {
				// IN Carta colours a channel by its emission wavelength, so a source
				// that states none gets one standing in for its display colour.
				final Double emission = nanometres(emissionWavelength(source, first,
					c));
				dataset.addWavelength(c, channelName(source, first, c),
					emission != null ? emission : EmissionWavelengths.forChannel(
						channelColour(source, first, c), c));
			}
			dataset.binning(binning(source, first));
			dataset.acquisitionTime(acquisitionTime(source, first));
			describeObjective(source, dataset);
			describePlate(source, reader, dataset);
		}
		finally {
			reader.setSeries(current);
		}
		return dataset;
	}

	private static void describeObjective(final IMetadata source,
		final DatasetDescription dataset)
	{
		try {
			if (source.getInstrumentCount() == 0) return;
			if (source.getObjectiveCount(0) == 0) return;
			dataset.objective(source.getObjectiveModel(0, 0), source
				.getObjectiveNominalMagnification(0, 0));
		}
		catch (final Exception e) {
			// Leave the objective undescribed rather than guessing.
		}
	}

	/**
	 * IN Carta opens one experiment at a time and an experiment is one plate, so
	 * a source holding several of them has to become several datasets. Until the
	 * converter can split them, it refuses: a single index would carry the first
	 * plate's geometry and every plate's wells, and nothing would say so.
	 */
	private static void refuseMultiplePlates(final IMetadata source)
		throws FormatException
	{
		final int plates;
		try {
			plates = source.getPlateCount();
		}
		catch (final Exception e) {
			return;
		}
		if (plates > 1) {
			throw new FormatException("This source holds " + plates +
				" plates, and one IN Carta experiment is one plate. Converting " +
				"several plates at once is not supported yet: extract one plate per " +
				"file, or convert a single series at a time.");
		}
	}

	private void describePlate(final IMetadata source,
		final IFormatReader reader, final DatasetDescription dataset)
	{
		try {
			if (source.getPlateCount() > 0) {
				final PositiveInteger rows = source.getPlateRows(0);
				final PositiveInteger columns = source.getPlateColumns(0);
				if (rows != null && columns != null) {
					dataset.plateSize(rows.getValue(), columns
						.getValue());
					return;
				}
			}
		}
		catch (final Exception e) {
			// Fall through to the single well plate below.
		}
		// No plate in the source: every series becomes a site of well A01.
		dataset.plateSize(1, 1);
	}

	private int firstConvertedSeries(final IFormatReader reader) {
		if (options.getSeries() >= 0 && options.getSeries() < reader
			.getSeriesCount())
		{
			return options.getSeries();
		}
		return 0;
	}

	// -- plane level metadata --

	/**
	 * Maps {@code (z, c, t)} of a series onto the index of the matching
	 * {@code <Plane>} element in the OME store, which is not necessarily the
	 * reader's plane order.
	 */
	private static Map<Long, Integer> planeMetadataIndex(final IMetadata meta,
		final int series)
	{
		final Map<Long, Integer> index = new HashMap<>();
		try {
			for (int p = 0; p < meta.getPlaneCount(series); p++) {
				final Integer z = value(meta.getPlaneTheZ(series, p));
				final Integer c = value(meta.getPlaneTheC(series, p));
				final Integer t = value(meta.getPlaneTheT(series, p));
				if (z == null || c == null || t == null) continue;
				index.putIfAbsent(zctKey(z, c, t), p);
			}
		}
		catch (final Exception e) {
			return index;
		}
		return index;
	}

	private static long zctKey(final int z, final int c, final int t) {
		return ((long) z << 40) | ((long) c << 20) | t;
	}

	/** Copies stage position, exposure and timestamp onto the plane record. */
	private static void describePlane(final IMetadata source, final int series,
		final Map<Long, Integer> planeMetadata, final PlaneCoordinates coords)
	{
		final Integer p = planeMetadata.get(zctKey(coords.z(), coords.channel(),
			coords.t()));
		if (p == null) return;
		try {
			coords.positionUm(micrometres(source.getPlanePositionX(series, p)),
				micrometres(source.getPlanePositionY(series, p)), micrometres(source
					.getPlanePositionZ(series, p)));
			coords.exposureMs(milliseconds(source.getPlaneExposureTime(series, p)));
			coords.timestampSeconds(timestampOf(source, series, p));
		}
		catch (final Exception e) {
			// Optional values: leave whatever was already set.
		}
	}

	/**
	 * Absolute acquisition time of a plane, as seconds since the Unix epoch:
	 * the image acquisition date plus the plane's delta T.
	 */
	private static Double timestampOf(final IMetadata source, final int series,
		final int plane)
	{
		final Instant acquired = acquisitionTime(source, series);
		if (acquired == null) return null;
		final Double delta = seconds(source.getPlaneDeltaT(series, plane));
		return acquired.toEpochMilli() / 1000.0 + (delta == null ? 0 : delta);
	}

	private static Instant acquisitionTime(final IMetadata meta,
		final int series)
	{
		try {
			if (meta.getImageAcquisitionDate(series) == null) return null;
			final String value = meta.getImageAcquisitionDate(series).getValue();
			if (value == null) return null;
			try {
				return OffsetDateTime.parse(value).toInstant();
			}
			catch (final Exception notOffset) {
				return LocalDateTime.parse(value).toInstant(ZoneOffset.UTC);
			}
		}
		catch (final Exception e) {
			return null;
		}
	}

	/** Detector binning as a single factor: {@code 2x2} becomes {@code 2}. */
	private static int binning(final IMetadata meta, final int series) {
		try {
			final Object binning = meta.getDetectorSettingsBinning(series, 0);
			if (binning == null) return 1;
			final String text = binning.toString();
			final int x = text.toLowerCase().indexOf('x');
			return Integer.parseInt(x > 0 ? text.substring(0, x).trim() : text.trim());
		}
		catch (final Exception e) {
			return 1;
		}
	}

	private static Length emissionWavelength(final IMetadata meta,
		final int series, final int channel)
	{
		try {
			return meta.getChannelEmissionWavelength(series, channel);
		}
		catch (final Exception e) {
			return null;
		}
	}

	/** The channel's display colour, which LIF and CZI carry but OME-TIFF may not. */
	private static ome.xml.model.primitives.Color channelColour(
		final IMetadata meta, final int series, final int channel)
	{
		try {
			return meta.getChannelColor(series, channel);
		}
		catch (final Exception e) {
			return null;
		}
	}

	// -- pixel statistics --

	/**
	 * Records the plane's minimum, maximum and mean, which the {@code .xdce}
	 * declares per image. Computed here because the bytes are already in hand;
	 * re-reading them later would double the IO of a conversion.
	 */
	private static void summarise(final IFormatReader reader, final byte[] bytes,
		final PlaneCoordinates coords)
	{
		final int type = reader.getPixelType();
		final int bytesPerPixel = FormatTools.getBytesPerPixel(type);
		final boolean floatingPoint = FormatTools.isFloatingPoint(type);
		final boolean signed = FormatTools.isSigned(type);
		final Object pixels = DataTools.makeDataArray(bytes, bytesPerPixel,
			floatingPoint, reader.isLittleEndian());
		if (pixels == null) return;

		double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
		double sum = 0;
		int count = 0;
		if (pixels instanceof byte[]) {
			for (final byte v : (byte[]) pixels) {
				final double d = signed ? v : v & 0xff;
				min = Math.min(min, d);
				max = Math.max(max, d);
				sum += d;
				count++;
			}
		}
		else if (pixels instanceof short[]) {
			for (final short v : (short[]) pixels) {
				final double d = signed ? v : v & 0xffff;
				min = Math.min(min, d);
				max = Math.max(max, d);
				sum += d;
				count++;
			}
		}
		else if (pixels instanceof int[]) {
			for (final int v : (int[]) pixels) {
				final double d = signed ? v : v & 0xffffffffL;
				min = Math.min(min, d);
				max = Math.max(max, d);
				sum += d;
				count++;
			}
		}
		else if (pixels instanceof long[]) {
			for (final long v : (long[]) pixels) {
				min = Math.min(min, v);
				max = Math.max(max, v);
				sum += v;
				count++;
			}
		}
		else if (pixels instanceof float[]) {
			for (final float v : (float[]) pixels) {
				min = Math.min(min, v);
				max = Math.max(max, v);
				sum += v;
				count++;
			}
		}
		else if (pixels instanceof double[]) {
			for (final double v : (double[]) pixels) {
				min = Math.min(min, v);
				max = Math.max(max, v);
				sum += v;
				count++;
			}
		}
		if (count == 0) return;
		coords.statistics(min, max, sum / count, !floatingPoint);
	}

	// -- plane coordinates --

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
			// LIF files leave some names empty, which would put name="" in the index.
			return name == null || name.trim().isEmpty() ? "channel" + channel : name;
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

	// -- unit conversion --

	private static Double micrometres(final Length length) {
		return convert(length, UNITS.MICROMETER);
	}

	private static Double nanometres(final Length length) {
		return convert(length, UNITS.NANOMETER);
	}

	private static Double convert(final Length length,
		final ome.units.unit.Unit<ome.units.quantity.Length> unit)
	{
		if (length == null) return null;
		try {
			final Number value = length.value(unit);
			return value == null ? null : value.doubleValue();
		}
		catch (final Exception e) {
			return null;
		}
	}

	private static Double milliseconds(final Time time) {
		if (time == null) return null;
		try {
			final Number value = time.value(UNITS.MILLISECOND);
			return value == null ? null : value.doubleValue();
		}
		catch (final Exception e) {
			return null;
		}
	}

	private static Double seconds(final Time time) {
		if (time == null) return null;
		try {
			final Number value = time.value(UNITS.SECOND);
			return value == null ? null : value.doubleValue();
		}
		catch (final Exception e) {
			return null;
		}
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
		final String plate = ImageXpressLayout.baseNameOf(input.getName());
		return new IncartaConverter(new ImageXpressLayout(plate)).convert(input
			.toPath(), outputDirectory.toPath(), null);
	}
}
