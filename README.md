# Bio-Formats to IN Carta

[![Build Status](https://github.com/unige-biochem/bioformats-to-incarta/actions/workflows/build.yml/badge.svg)](https://github.com/unige-biochem/bioformats-to-incarta/actions/workflows/build.yml)

Converts any Bio-Formats supported image file into an IN Carta compatible format.

## Installation

*(Not on an update site yet.)* Build from source and drop the jar into `Fiji.app/plugins`:

```bash
mvn clean install
```

## Usage

In Fiji: `Plugins>UNIGE>Bio-Formats to IN Carta`.

From Java:

```java
List<PlaneCoordinates> planes = IncartaConverter.convert(
    new File("plate1.nd2"), new File("converted"));
```

## Status

Both halves are in place. Any Bio-Formats supported file is opened, plate
metadata is resolved to well / field where the source carries it, every plane is
written out as an uncompressed TIFF carrying the source pixel calibration, and
an `.xdce` index describing every plane is written beside them.

The layout follows the MetaXpress / ImageXpress convention that normally feeds
IN Carta, reverse-engineered from a reference plate export:

```
out/
  plate1.xdce               <- XML index; the importer reads the plate through this
  t1_E03_s1_w1_z1.tif       <- timepoint, well, site, wavelength, z - all 1-based
  t1_E03_s1_w2_z1.tif
  ...
```

**Converted datasets do import into IN Carta.** A synthetic 96-well plate and a
real Leica `.lif` were both imported successfully in September and October 2026,
with the right pixel size, wells, sites, channels and Z. A converted folder also
still imports after being moved.

Known limits:

- **One plate per source.** An IN Carta experiment is one plate, so a source
  holding several is refused rather than silently flattened into one.
- **Channel colours are inferred.** IN Carta colours a channel by its emission
  wavelength, and the `.xdce` has no colour field, so a source without
  wavelengths gets one derived from its display colour. See
  `EmissionWavelengths`.
- **No projections.** The reference export has a `_Projection` sibling dataset;
  the converter does not produce one.
- **Uneven Z across sites** makes IN Carta warn, then import anyway.

Bio-Formats cannot read an `.xdce` back, so nothing in this repository can
validate the output; only a trial import can. What each import settled, and what
is still guesswork, is in [UNKNOWNS.md](UNKNOWNS.md) - read it before trusting a
converted plate.

## Architecture

| Class | Role |
|---|---|
| `IncartaConverter` | Reads with Bio-Formats, writes one TIFF per plane |
| `IncartaLayout` | Decides file naming, nesting, and the dataset metadata file |
| `ImageXpressLayout` | The MetaXpress naming plus the `.xdce` index writer |
| `DatasetDescription` | Plate-level metadata the index file needs |
| `PlaneCoordinates` | series / well / field / c / z / t of a plane, plus its stage position, exposure and pixel statistics |
| `ConversionOptions` | series selection, compression, BigTIFF, overwrite |
| `command.BioformatsToIncartaCommand` | The Fiji dialog around the converter. Reports progress through a SciJava `Task`, and cancelling that task stops the conversion before its next plane. A failure is thrown, not just logged |

Tests run against Bio-Formats' `.fake` reader, so they need no data files.

The command needs only SciJava and Bio-Formats. ImageJ is a test dependency,
used by `LaunchInFiji` (under `src/test`) to open the dialog from the IDE. This
keeps a headless run small: the command line and window in
[bioformats-to-incarta-app](https://github.com/unige-biochem/bioformats-to-incarta-app)
run this artifact through jgo, without Fiji.

## Development

Requires JDK 21 and Maven. The project inherits from
[pom-scijava](https://github.com/scijava/pom-scijava) 45.1.0.

```bash
mvn clean install     # build + run tests
mvn -Dtest=... test   # run one test
```

Releases are cut with [`release-version.sh`](https://github.com/scijava/scijava-scripts)
from scijava-scripts.

## License

MIT License — see [LICENSE.txt](LICENSE.txt).

Note that Bio-Formats' full format support (`ome:formats-gpl`) is GPL licensed:
a distributed bundle that includes it is covered by the GPL, even though this
code is MIT.

## Project links

- GitLab (internal): <https://gitlab.unige.ch/unige-biochem/access-facility/bioformats-to-incarta>
- GitHub (public): <https://github.com/unige-biochem/bioformats-to-incarta>
- Test data: `F:\user-projects\data\access-facility\bioformats-to-incarta`

Created by Nicolas Chiaruttini.
