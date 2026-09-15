# Open questions about the IN Carta export contract

The `.xdce` layout in `ImageXpressLayout` was reverse-engineered from **one**
reference dataset: a MetaXpress 6.7.2.290 export of a 4-timepoint, 4-well,
16-site, 3-wavelength 96-well plate, at
`F:\user-projects\data\access-facility\bioformats-to-incarta\cherry dynein`.

One example cannot separate *what the format requires* from *what that
particular instrument happened to write*. Everything below is a decision made
without evidence. Each entry says what we do today and what would settle it —
usually a trial import in IN Carta, sometimes a second example dataset.

Two constraints make this list unavoidable rather than lazy:

- **Bio-Formats cannot read an `.xdce`.** `InCellReader` claims the suffix but
  its `isThisType` requires the literal string `IN Cell Analyzer` or `Cytell`
  in the first 2048 bytes, and MetaXpress files say `MetaXpress` instead. So we
  cannot round-trip our own output to validate it, and we will not inject a
  vendor magic string to force detection.
- **The reference folder is not usable as converter input.** Its individual
  TIFFs are picked up by the Metamorph TIFF reader as one series of one plane
  with no plate metadata, so it exercises the plateless fallback, not the plate
  path.

---

## Trial import log

What IN Carta has actually said. Newest first.

### 2026-09-15 — `trial1-minimal-96well` (synthetic `.fake` plate)

96-well, 2 sites, 2 channels, 2-D, 512×512 uint16, 0.65 µm/px. The `.xdce`
was *validated* and three problems were reported, yet the import still went
ahead and images were displayed, so these read as warnings rather than
rejections:

```
ImageStack::AutoLeadAcquisitionProtocol::Wavelengths::EmissionFilter::Unit must not be empty
ImageStack::AutoLeadAcquisitionProtocol::Wavelengths::EmissionFilter::Wavelength must not be empty
ImageStack::AutoLeadAcquisitionProtocol::Plate::TopLeftWellCenterOffset::Horizontal must be greater than 0
```

What it settles:

- **IN Carta validates the `.xdce` against rules**, attribute by attribute,
  including value ranges. Omitting an attribute is not automatically safe.
- **`<Wavelengths>/<Wavelength>/<EmissionFilter>` needs `wavelength` and
  `unit`.** The per-image `<EmissionFilter>` was not flagged. Fixed: when the
  source has no emission wavelength we now write
  `ImageXpressLayout.UNKNOWN_EMISSION_NM` (500 nm). **That value is fiction.**
- **`TopLeftWellCenterOffset` must be positive.** The fake plate's OME
  `WellOriginX` was 0, and we used it. OME's `WellOrigin` is the origin of the
  fields inside a well, not the position of well A1, so it was the wrong source
  anyway. Fixed: the offset now always comes from the footprint table (B1).
- **Not flagged:** `<Application name="bioformats-to-incarta">` (A2), the
  missing MetaMorph TIFF block (A1), a 2-D wavelength (B4), missing
  `<FocusPosition>`/`<PlatePosition_um>` and missing `<Exposure>` on some
  images (A4). They may still matter at a later stage than validation; whether
  the images shown were the right ones is not yet checked.

Corrected output: `trial1b-minimal-96well-fixed`, not yet imported.

---

## A. Settled only by a trial import

These need a human at the instrument PC to import a converted dataset.
Suggested order: get a minimal export in first (correct names + `.xdce` + plain
OME-derived TIFF headers), then add things only if the import rejects them.

### A1. Is the MetaSeries `ImageDescription` block required?

Every reference TIFF carries a ~6.4 kB MetaMorph `<MetaData>` XML block in tag
270: `spatial-calibration-x/y`, `stage-position-x/y`, `stage-label`
(`"E03 : Site 1"`), `z-position`, `wavelength`, plus dozens of `custom-prop`
entries (objective, timepoint, Z step, site coordinates, lamp settings).

**We write:** nothing of the sort. `TiffWriter` emits an OME-XML description,
and we set `PhysicalSizeX/Y/Z` on it so the calibration is at least present.

**Settled by:** a trial import. If the plate imports and the scale bar is
right, the block is inert and we are done. If calibration is lost but the
import succeeds, we need only the `spatial-calibration-*` props. If the import
rejects the files outright, the block is required and this becomes a large
piece of work. **Ask before building it.**

### A2. Must `<Application name>` say `MetaXpress`?

**We write:** `<Application name="bioformats-to-incarta" software_label="…"
software_version="…"/>` — the honest thing. If the importer keys off the vendor
name, this fails.

**Settled by:** a trial import. If it fails, the fix is a decision for a human,
not for the converter: claiming to be MetaXpress is a vendor-identity claim.

### A3. Does the `<Image>` ordering matter?

The reference lists images strictly as timepoint → well → site → wavelength →
z: 24 entries per site, 384 per well, 1536 per timepoint, 6144 total.

**We write:** the same order, produced by sorting the write-order plane list in
`ImageXpressLayout.XDCE_ORDER`. It costs nothing, so we reproduce it whether or
not it is required. No action needed unless an import fails in a way that
points here.

### A4. Are the optional per-image elements required?

`<FocusPosition>`, `<PlatePosition_um>`, `<Exposure>` and `<MinMaxMean>` are
present on every reference image, because the instrument knows them.

**We write:** each element only when the source describes it, and omit it
otherwise. `<MinMaxMean>` we always have — it is computed from the pixels while
they are in hand. `<EmissionFilter>`/`<ExcitationFilter>` always carry the
channel name, and `<Identifier>` and `<Well>` are always written.

**Settled by:** a trial import of a source with sparse metadata (a plain
multi-series TIFF stack, say). If omission fails, we need to decide on neutral
defaults — `0` positions, `0 ms` exposure — and record that they are fiction.

### A5. Does `Images/@path` have to be right?

The reference's `@path` points at `F:\IN Carta test sample\cherry dynein\cherry
dynein`, which is no longer where the folder sits — evidence the importer
tolerates a stale path, but not proof it ignores it.

**We write:** the absolute path of the actual output directory.

**Settled by:** a trial import of a dataset moved after conversion. Worth
knowing, because it decides whether a converted plate can be copied to a share.

### A6. Are TIFF compression and BigTIFF acceptable?

**We write:** uncompressed by default, matching the reference (tag 259 = 1).
LZW, JPEG-2000 and zlib stay selectable, and BigTIFF is off by default.

**Settled by:** importing an LZW plate. Only worth testing if disk space
becomes a problem — the default is the safe one.

---

## B. Settled by a second example dataset

### B1. A plate that is not 96-well

The `.xdce` declares physical plate geometry: `<WellParameters>`,
`<TopLeftWellCenterOffset>`, `<WellSpacing>`. OME has no equivalent for well
spacing or well shape, so it cannot be derived from the source.

**We write:** the ANSI/SLAS footprint for the well count (6, 12, 24, 48, 96,
384, 1536 are tabulated in `ImageXpressLayout.Footprint`). For any other
plate size we omit all three elements and keep only `<Plate columns rows
name>`.

**Known risk:** IN Carta checks `TopLeftWellCenterOffset` (it must be > 0, see
the trial log), so omitting it probably triggers the same kind of warning. That
includes the 1×1 plate invented for plateless sources (B2).

**Would like:** a MetaXpress export of a **384-well** plate, to check the
footprint numbers and confirm which attributes actually vary. Also useful: any
export on a plate whose geometry is *not* standard, to see what the instrument
writes then.

### B2. A non-plate dataset

**We write:** a plateless source becomes a 1×1 plate, with every series a site
of well `A01`: `t1_A01_s<series+1>_w<c+1>_z<z+1>.tif`. That is an invention —
no evidence says IN Carta accepts a one-well plate, or that it accepts a
dataset with no plate at all.

**Would like:** any dataset IN Carta ingests that did **not** come off a plate
reader — a slide scan, a chamber slide, a single field. It would tell us
whether the plate wrapper is mandatory and, if so, what shape it takes.

### B3. Channels with different Z depths

The reference has a real irregularity: wavelength w1 (DAPI) was acquired as a
single plane, but the protocol declares `z_slice="8"` for it, so the `.xdce`
holds **eight `<Image>` entries with `z_index` 0–7 all pointing at the same
file** `…_w1_z1.tif`. That is where 6144 index entries over 4352 files comes
from.

**We write:** nothing like it. Bio-Formats gives one `SizeZ` per series, so
every channel of a converted dataset has the same depth and the duplication
never arises.

**Would like:** a source where channels genuinely differ in depth (some readers
split these into separate series). Then we would know whether IN Carta requires
the padding entries or merely tolerates them.

### B4. A genuinely 2-D acquisition

Every `<Wavelength>` in the reference says `imaging_mode="3-D" z_slice="8"`,
even for the 2-D wavelength.

**We write:** `imaging_mode="2-D"` and `z_slice="1"` when the source has a
single slice, and `enabled="false"` on `<ZDimensionParameters>`.

**Would like:** a MetaXpress export of a widefield 2-D plate, to see what the
instrument writes for a protocol with no Z at all — including whether
`<ZDimensionParameters>` is present, and what `z_step` becomes. We currently
write `step="0.000000"` when the source has no Z calibration.

### B5. The `_Projection` sibling dataset

The reference has a `cherry dynein_Projection/` folder with its own complete
`.xdce` and its own re-numbered z (`…_w2_z0.tif` for projected wavelengths,
`…_w1_z1.tif` for the un-projected one, both with `z_index="0"`).

**We write:** nothing — the converter has no projection feature.

**Would like:** confirmation from a user that IN Carta needs projections at
all. If it does, note the consequence recorded in the brief: the `filename`
attribute is authoritative and the naming convention is convention, not
contract. Our design already respects that — `ImageXpressLayout` writes both
the files and the index from the same `relativePathFor` call.

### B6. More than one plate in a source

**We write:** `describePlate` reads plate 0 only, though `wellsBySeries` maps
wells from every plate. A two-plate source would therefore get one `.xdce` with
the first plate's geometry and all the wells.

**Would like:** a multi-plate source (an HCS screen export). The likely right
answer is one output directory and one `.xdce` per plate, which is a converter
change, not a layout one.

### B7. Filter-name vocabulary

The reference uses the same string for `<EmissionFilter>` and
`<ExcitationFilter>` on each image — `DAPI - S`, `FITC - S`, `Texas Red - S`,
matching the `<Wavelengths>` block.

**We write:** the OME channel name in all three places, whatever it is
(`channel0` when the source has none).

**Would like:** an example with unusual channel names, or word from a user
whether IN Carta groups channels by these strings. If it matches them against a
known vocabulary, arbitrary channel names will not group correctly.

---

## What the automated tests do and do not cover

`ImageXpressLayoutTest` and `IncartaConverterTest` run against Bio-Formats'
synthetic `.fake` reader, so CI needs no data files. They pin the naming
grammar, the 0-based/1-based split, the index ordering, the entry count, the
omit-when-unknown rule, and the fact that every name in the index is a file
that was actually written.

They cannot tell you whether IN Carta imports the result. Nothing in this
repository can. That is what section A is for.
