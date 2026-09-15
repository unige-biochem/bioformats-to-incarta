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

**It has never been through a real IN Carta import.** Bio-Formats cannot read an
`.xdce` back, so nothing in this repository can validate the output; only a
trial import can. The decisions taken without evidence are listed in
[UNKNOWNS.md](UNKNOWNS.md) - read it before trusting a converted plate.

## Architecture

| Class | Role |
|---|---|
| `IncartaConverter` | Reads with Bio-Formats, writes one TIFF per plane |
| `IncartaLayout` | Decides file naming, nesting, and the dataset metadata file |
| `ImageXpressLayout` | The MetaXpress naming plus the `.xdce` index writer |
| `DatasetDescription` | Plate-level metadata the index file needs |
| `PlaneCoordinates` | series / well / field / c / z / t of a plane, plus its stage position, exposure and pixel statistics |
| `ConversionOptions` | series selection, compression, BigTIFF, overwrite |
| `command.BioformatsToIncartaCommand` | The Fiji dialog around the converter |

Tests run against Bio-Formats' `.fake` reader, so they need no data files.

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
