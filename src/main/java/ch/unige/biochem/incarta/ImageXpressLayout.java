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

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The layout MetaXpress writes for the ImageXpress instruments that normally
 * feed IN Carta: one flat directory of single-plane TIFFs named
 * {@code t<t>_<well>_s<site>_w<wavelength>_z<z>.tif}, bound together by an
 * {@code .xdce} XML index that lists every plane.
 * <p>
 * All four filename indices are <em>one</em>-based, while the indices inside
 * the {@code .xdce} {@code <Identifier>} elements are zero-based. Reverse
 * engineered from a MetaXpress 6.7.2.290 export of a 96-well plate.
 * <p>
 * Sources without plate metadata are laid out as a single-well plate, with each
 * series becoming a site of well {@code A01}.
 */
public class ImageXpressLayout implements IncartaLayout {

	private static final String[] ROW_LABELS = {
		"A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O",
		"P"
	};

	/**
	 * Last-resort emission wavelength, for a {@link DatasetDescription} built by
	 * hand that leaves one out. IN Carta rejects a {@code <Wavelengths>} filter
	 * without a wavelength ("Wavelength must not be empty"), so something has to
	 * be written. The converter itself always supplies a value, through
	 * {@link EmissionWavelengths}.
	 */
	static final double UNKNOWN_EMISSION_NM = 500;

	/**
	 * Physical geometry of the standard microplate formats, keyed by
	 * {@code rows * columns}: well spacing, centre of well A1 relative to the
	 * plate corner and well size, all in millimetres, plus the well shape.
	 * <p>
	 * The {@code .xdce} declares this geometry and the source rarely does, so it
	 * is filled in from the ANSI/SLAS footprint when the plate has a standard
	 * well count. Otherwise only {@code WellParameters} is written, sized to one
	 * field of view, because IN Carta will not do without it.
	 * <p>
	 * OME's {@code Plate/@WellOriginX/Y} is no substitute: it is the origin of
	 * the fields within a well, not the position of well A1 on the plate, and a
	 * fake plate sets it to 0, which IN Carta rejects ("must be greater than 0").
	 */
	private static final class Footprint {

		final double spacing, originX, originY, size;
		final String shape;

		Footprint(final double spacing, final double originX, final double originY,
			final double size, final String shape)
		{
			this.spacing = spacing;
			this.originX = originX;
			this.originY = originY;
			this.size = size;
			this.shape = shape;
		}

		static Footprint of(final int rows, final int columns) {
			switch (rows * columns) {
				case 6: return new Footprint(39.12, 24.76, 23.16, 34.80, "Round");
				case 12: return new Footprint(26.01, 24.94, 16.79, 22.10, "Round");
				case 24: return new Footprint(19.30, 15.13, 13.49, 15.60, "Round");
				case 48: return new Footprint(13.00, 18.16, 10.08, 11.00, "Round");
				case 96: return new Footprint(9.00, 14.38, 11.24, 6.96, "Round");
				case 384: return new Footprint(4.50, 12.13, 8.99, 3.30, "Square");
				case 1536: return new Footprint(2.25, 11.00, 7.86, 1.70, "Square");
				default: return null;
			}
		}
	}

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern(
		"yyyy-MM-dd", Locale.ROOT).withZone(ZoneOffset.UTC);

	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern(
		"HH:mm:ss.SSS", Locale.ROOT).withZone(ZoneOffset.UTC);

	/**
	 * The order the reference export lists its images in: timepoint, then well,
	 * then site, then wavelength, then z. Whether the importer depends on it is
	 * unknown, so we reproduce it.
	 */
	private static final Comparator<PlaneCoordinates> XDCE_ORDER = Comparator //
		.comparingInt(PlaneCoordinates::t) //
		.thenComparingInt(ImageXpressLayout::row) //
		.thenComparingInt(ImageXpressLayout::column) //
		.thenComparingInt(ImageXpressLayout::site) //
		.thenComparingInt(PlaneCoordinates::channel) //
		.thenComparingInt(PlaneCoordinates::z);

	private final String plateName;

	/**
	 * @param plateName name of the plate, used as the {@code PlateID} and as the
	 *          base name of the {@code .xdce} index file
	 */
	public ImageXpressLayout(final String plateName) {
		this.plateName = plateName;
	}

	public String plateName() {
		return plateName;
	}

	@Override
	public String relativePathFor(final PlaneCoordinates plane) {
		return String.format(Locale.ROOT, "t%d_%s_s%d_w%d_z%d.tif", plane.t() + 1,
			wellLabel(plane), site(plane) + 1, plane.channel() + 1, plane.z() + 1);
	}

	@Override
	public void writeDatasetMetadata(final Path outputDirectory,
		final DatasetDescription dataset, final List<PlaneCoordinates> planes)
		throws IOException
	{
		final Path index = outputDirectory.resolve(plateName + ".xdce");
		try (Writer out = new BufferedWriter(Files.newBufferedWriter(index,
			StandardCharsets.ISO_8859_1)))
		{
			writeXdce(out, outputDirectory, dataset, planes);
		}
	}

	private void writeXdce(final Writer out, final Path outputDirectory,
		final DatasetDescription dataset, final List<PlaneCoordinates> planes)
		throws IOException
	{
		final Instant created = dataset.acquisitionTime() == null ? Instant.now()
			: dataset.acquisitionTime();
		final String user = System.getProperty("user.name", "unknown");

		out.write(
			"<?xml version=\"1.0\" encoding=\"ISO-8859-1\" standalone=\"yes\"?>\n");
		out.write("<ImageStack PlateID=\"" + xml(plateName) + "\">\n");
		out.write("\t<UUID value=\"" + UUID.randomUUID() + "\"/>\n");
		out.write("\t<Application name=\"bioformats-to-incarta\" software_label=\"" +
			xml(version()) + "\" software_version=\"" + xml(version()) + "\"/>\n");
		out.write("\t<Creation date=\"" + DATE.format(created) + "\" time=\"" + TIME
			.format(created) + "\" timezoneoffset=\"0\"/>\n");
		out.write("\t<AutoLeadAcquisitionProtocol name=\"" + xml(plateName) +
			"\">\n");
		out.write("\t\t<Camera>\n");
		out.write("\t\t\t<Binning value=\"" + dataset.binning() + " X " + dataset
			.binning() + "\"/>\n");
		out.write("\t\t\t<Size height=\"" + dataset.sizeY() + "\" width=\"" + dataset
			.sizeX() + "\"/>\n");
		out.write("\t\t</Camera>\n");
		writeObjectiveCalibration(out, dataset);
		writePlate(out, dataset);
		writePlateMap(out, dataset);
		writeWavelengths(out, dataset);
		out.write("\t\t<ProjectInformation>\n");
		out.write("\t\t\t<User name=\"" + xml(user) + "\"/>\n");
		out.write("\t\t\t<Project name=\"" + xml(plateName) + "\"/>\n");
		out.write("\t\t</ProjectInformation>\n");
		out.write("\t</AutoLeadAcquisitionProtocol>\n");
		out.write("\t<Operator name=\"" + xml(user) + "\"/>\n");
		out.write("\t<SpecimenHolder label=\"" + xml(specimenHolder(dataset)) +
			"\" type=\"plate\"/>\n");
		out.write("\t<Images image_format=\"TIFF\" path=\"" + xml(outputDirectory
			.toAbsolutePath().toString()) + "\">\n");

		final List<PlaneCoordinates> ordered = new ArrayList<>(planes);
		ordered.sort(XDCE_ORDER);
		for (final PlaneCoordinates plane : ordered) {
			writeImage(out, plane, created);
		}

		out.write("\t</Images>\n");
		out.write("</ImageStack>\n");
	}

	private static void writeObjectiveCalibration(final Writer out,
		final DatasetDescription dataset) throws IOException
	{
		if (dataset.pixelWidthUm() == null && dataset.objectiveName() == null) {
			return;
		}
		final StringBuilder tag = new StringBuilder("\t\t<ObjectiveCalibration");
		if (dataset.pixelWidthUm() != null) {
			tag.append(" pixel_width=\"").append(decimal(dataset.pixelWidthUm()))
				.append('"');
		}
		if (dataset.pixelHeightUm() != null) {
			tag.append(" pixel_height=\"").append(decimal(dataset.pixelHeightUm()))
				.append('"');
		}
		if (dataset.pixelWidthUm() != null || dataset.pixelHeightUm() != null) {
			tag.append(" unit=\"um\"");
		}
		if (dataset.objectiveName() != null) {
			tag.append(" objective_name=\"").append(xml(dataset.objectiveName()))
				.append('"');
		}
		if (dataset.magnification() != null) {
			tag.append(" magnification=\"").append(decimal(dataset.magnification()))
				.append('"');
		}
		out.write(tag.append("/>\n").toString());
	}

	private static void writePlate(final Writer out,
		final DatasetDescription dataset) throws IOException
	{
		out.write("\t\t<Plate columns=\"" + dataset.plateColumns() + "\" rows=\"" +
			dataset.plateRows() + "\" name=\"" + xml(specimenHolder(dataset)) +
			"\">\n");
		final Footprint footprint = Footprint.of(dataset.plateRows(), dataset
			.plateColumns());
		if (footprint != null) {
			out.write("\t\t\t<WellParameters width=\"" + decimal(footprint.size) +
				"\" unit=\"mm\" height=\"" + decimal(footprint.size) + "\" size=\"" +
				decimal(footprint.size) + "\" shape=\"" + footprint.shape + "\"/>\n");
			out.write("\t\t\t<TopLeftWellCenterOffset horizontal=\"" + decimal(
				footprint.originX) + "\" vertical=\"" + decimal(footprint.originY) +
				"\" unit=\"mm\"/>\n");
			out.write("\t\t\t<WellSpacing horizontal=\"" + decimal(footprint.spacing) +
				"\" vertical=\"" + decimal(footprint.spacing) + "\" unit=\"mm\"/>\n");
		}
		else {
			// IN Carta requires WellParameters with a positive size and a shape,
			// but did not ask for the offset or spacing. A square well exactly one
			// field of view wide is the least invented size available.
			final double size = fieldOfViewMm(dataset);
			out.write("\t\t\t<WellParameters width=\"" + decimal(size) +
				"\" unit=\"mm\" height=\"" + decimal(size) + "\" size=\"" + decimal(
					size) + "\" shape=\"Square\"/>\n");
		}
		out.write("\t\t</Plate>\n");
	}

	private static void writePlateMap(final Writer out,
		final DatasetDescription dataset) throws IOException
	{
		out.write("\t\t<PlateMap>\n");
		out.write("\t\t\t<TimeSchedule enabled=\"" + (dataset.timepointCount() > 1) +
			"\">\n");
		out.write("\t\t\t\t<Times>\n");
		for (int t = 0; t < dataset.timepointCount(); t++) {
			out.write("\t\t\t\t\t<TimePoint index=\"" + t + "\"/>\n");
		}
		out.write("\t\t\t\t</Times>\n");
		out.write("\t\t\t</TimeSchedule>\n");
		out.write("\t\t\t<ZDimensionParameters enabled=\"" + (dataset
			.zSliceCount() > 1) + "\" number_of_slices=\"" + dataset.zSliceCount() +
			"\" step=\"" + decimal(zStep(dataset)) + "\"/>\n");
		out.write("\t\t</PlateMap>\n");
	}

	private static void writeWavelengths(final Writer out,
		final DatasetDescription dataset) throws IOException
	{
		out.write("\t\t<Wavelengths>\n");
		final String mode = dataset.zSliceCount() > 1 ? "3-D" : "2-D";
		for (final DatasetDescription.Wavelength w : dataset.wavelengths()) {
			out.write("\t\t\t<Wavelength index=\"" + w.index() +
				"\" imaging_mode=\"" + mode + "\" z_slice=\"" + dataset.zSliceCount() +
				"\" z_step=\"" + decimal(zStep(dataset)) + "\">\n");
			final StringBuilder filter = new StringBuilder(
				"\t\t\t\t<EmissionFilter name=\"").append(xml(w.name())).append('"');
			final double emission = w.emissionNm() == null ? UNKNOWN_EMISSION_NM : w
				.emissionNm();
			filter.append(" wavelength=\"").append(integer(emission)).append(
				"\" unit=\"nm\"");
			out.write(filter.append("/>\n").toString());
			out.write("\t\t\t</Wavelength>\n");
		}
		out.write("\t\t</Wavelengths>\n");
	}

	private void writeImage(final Writer out, final PlaneCoordinates plane,
		final Instant created) throws IOException
	{
		final double timestamp = plane.timestampSeconds() == null ? created
			.toEpochMilli() / 1000.0 : plane.timestampSeconds();
		out.write("\t\t<Image GUID=\"" + UUID.randomUUID() + "\" filename=\"" + xml(
			relativePathFor(plane)) + "\" timestamp_sec=\"" + String.format(
				Locale.ROOT, "%.2f", timestamp) + "\">\n");
		out.write("\t\t\t<Well label=\"" + xml(xdceWellLabel(plane)) + "\">\n");
		out.write("\t\t\t\t<Row number=\"" + (row(plane) + 1) + "\"/>\n");
		out.write("\t\t\t\t<Column number=\"" + (column(plane) + 1) + "\"/>\n");
		out.write("\t\t\t</Well>\n");
		if (plane.positionZUm() != null) {
			out.write("\t\t\t<FocusPosition unit=\"um\" z=\"" + decimal(plane
				.positionZUm()) + "\"/>\n");
		}
		if (plane.positionXUm() != null && plane.positionYUm() != null) {
			out.write("\t\t\t<PlatePosition_um x=\"" + decimal(plane.positionXUm()) +
				"\" y=\"" + decimal(plane.positionYUm()) + "\"/>\n");
		}
		if (plane.exposureMs() != null) {
			out.write("\t\t\t<Exposure time=\"" + integer(plane.exposureMs()) +
				"\" unit=\"ms\"/>\n");
		}
		out.write("\t\t\t<EmissionFilter name=\"" + xml(plane.channelName()) +
			"\"/>\n");
		out.write("\t\t\t<ExcitationFilter name=\"" + xml(plane.channelName()) +
			"\"/>\n");
		out.write("\t\t\t<Identifier field_index=\"" + site(plane) +
			"\" time_index=\"" + plane.t() + "\" wave_index=\"" + plane.channel() +
			"\" z_index=\"" + plane.z() + "\"/>\n");
		if (plane.min() != null && plane.max() != null && plane.mean() != null) {
			out.write("\t\t\t<MinMaxMean max=\"" + statistic(plane, plane.max()) +
				"\" mean=\"" + statistic(plane, plane.mean()) + "\" min=\"" + statistic(
					plane, plane.min()) + "\"/>\n");
		}
		out.write("\t\t</Image>\n");
	}

	// -- naming --

	/** Well label as it appears in a file name: {@code E03}. */
	public static String wellLabel(final PlaneCoordinates plane) {
		return String.format(Locale.ROOT, "%s%02d", rowLabel(row(plane)), column(
			plane) + 1);
	}

	/**
	 * Well label as it appears in the {@code .xdce}: spaced and unpadded,
	 * {@code E - 3}. Deliberately different from {@link #wellLabel} - the
	 * reference export uses both forms.
	 */
	static String xdceWellLabel(final PlaneCoordinates plane) {
		return rowLabel(row(plane)) + " - " + (column(plane) + 1);
	}

	private static String rowLabel(final int row) {
		return row < ROW_LABELS.length ? ROW_LABELS[row] : String.valueOf(row + 1);
	}

	/** Zero-based plate row; sources without plate metadata all land in row A. */
	private static int row(final PlaneCoordinates plane) {
		return plane.hasWell() ? plane.wellRow() : 0;
	}

	/** Zero-based plate column; without plate metadata, column 1. */
	private static int column(final PlaneCoordinates plane) {
		return plane.hasWell() ? plane.wellColumn() : 0;
	}

	/**
	 * Zero-based site (field) within its well. Without plate metadata the series
	 * index takes its place, so that a multi-series file becomes one well of many
	 * sites rather than a pile of colliding names.
	 */
	private static int site(final PlaneCoordinates plane) {
		return plane.hasWell() ? plane.field() : plane.series();
	}

	/**
	 * Strips the extension off a file name, to use it as the plate name:
	 * {@code plate1.nd2} -> {@code plate1}.
	 */
	public static String baseNameOf(final String fileName) {
		final int dot = fileName.lastIndexOf('.');
		return dot > 0 ? fileName.substring(0, dot) : fileName;
	}

	// -- formatting --

	private static String specimenHolder(final DatasetDescription dataset) {
		if (dataset.plateModel() != null) return dataset.plateModel();
		return dataset.plateRows() * dataset.plateColumns() + "-well plate";
	}

	/**
	 * The larger side of one image, in millimetres, or 1 mm when the source has
	 * no pixel size.
	 */
	private static double fieldOfViewMm(final DatasetDescription dataset) {
		if (dataset.pixelWidthUm() == null || dataset.pixelHeightUm() == null) {
			return 1;
		}
		final double side = Math.max(dataset.sizeX() * dataset.pixelWidthUm(),
			dataset.sizeY() * dataset.pixelHeightUm()) / 1000;
		return side > 0 ? side : 1;
	}

	private static double zStep(final DatasetDescription dataset) {
		return dataset.zStepUm() == null ? 0 : dataset.zStepUm();
	}

	/** The six-decimal form the reference export uses for physical quantities. */
	private static String decimal(final double value) {
		return String.format(Locale.ROOT, "%.6f", value);
	}

	private static String integer(final double value) {
		return String.valueOf(Math.round(value));
	}

	private static String statistic(final PlaneCoordinates plane,
		final double value)
	{
		return plane.hasIntegerPixels() ? integer(value) : decimal(value);
	}

	private static String version() {
		final String version = ImageXpressLayout.class.getPackage()
			.getImplementationVersion();
		return version == null ? "dev" : version;
	}

	/**
	 * Escapes for an XML attribute, and replaces anything outside ISO-8859-1 with
	 * a character reference - the header declares that encoding.
	 */
	private static String xml(final String text) {
		if (text == null) return "";
		final StringBuilder escaped = new StringBuilder(text.length());
		for (int i = 0; i < text.length(); i++) {
			final char c = text.charAt(i);
			switch (c) {
				case '&':
					escaped.append("&amp;");
					break;
				case '<':
					escaped.append("&lt;");
					break;
				case '>':
					escaped.append("&gt;");
					break;
				case '"':
					escaped.append("&quot;");
					break;
				case '\'':
					escaped.append("&apos;");
					break;
				default:
					if (c > 0xff) escaped.append("&#").append((int) c).append(';');
					else escaped.append(c);
			}
		}
		return escaped.toString();
	}
}
