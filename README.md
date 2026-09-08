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

The reading side is complete: any Bio-Formats supported file is opened, plate
metadata is resolved to well / field where the source carries it, and every
plane is written out as a TIFF.

The **output layout is still a placeholder**. `DefaultIncartaLayout` invents a
flat `<base>_<well>_f<field>_w<channel>_z<z>_t<t>.tif` naming; it exercises the
pipeline but is not guaranteed to import. Replace it with the layout IN Carta
actually expects — everything IN Carta specific lives behind the `IncartaLayout`
interface, so nothing else has to change.

## Architecture

| Class | Role |
|---|---|
| `IncartaConverter` | Reads with Bio-Formats, writes one TIFF per plane |
| `IncartaLayout` | Decides file naming, nesting, and the dataset metadata file |
| `DefaultIncartaLayout` | Placeholder implementation of the above |
| `PlaneCoordinates` | series / well / field / c / z / t of a single plane |
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
