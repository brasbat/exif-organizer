# EXIF Organizer

EXIF Organizer is a desktop application for sorting photographs into folders based on their embedded EXIF metadata. It provides a dark JavaFX interface for scanning image folders, reviewing metadata, previewing the resulting folder structure, and organizing selected files.

## Features

- Scan one or more source folders, optionally including subfolders.
- Organize supported RAW and compressed image formats.
- Select which discovered image formats should be organized.
- Build folder structures from EXIF properties such as:
  - `{Year}`, `{Month}`, `{Day}`
  - `{Hour}`, `{Minute}`, `{Second}`
  - `{Make}`, `{Model}`, `{Location}`
  - `{DateTimeOriginal:yyyy-MM-dd}`
  - Any other available non-empty EXIF property.
- Preview the generated folder structure before organizing files.
- Display raw EXIF property names and occurrence statistics.
- Sanitize EXIF values for safe folder names.
- Copy files without overwriting existing files. Existing destination files are reported as skipped.
- Move files using a unique filename when the requested destination filename already exists.
- Reuse existing destination folders; folders are created only when needed.
- Show scan and organization progress, including the file currently being processed.
- Use a local location database to resolve GPS-based locations.

## Supported image formats

### RAW formats

`3FR`, `ARW`, `CR2`, `CR3`, `DCR`, `DNG`, `ERF`, `FFF`, `IIQ`, `K25`, `KDC`, `MEF`, `MOS`, `MRW`, `NEF`, `NRW`, `ORF`, `PEF`, `RAF`, `RAW`, `RW2`, `RWL`, `SR2`, `SRF`, `SRW`, `X3F`

### Compressed and common image formats

`JPG`, `JPEG`, `JPE`, `PNG`, `GIF`, `BMP`, `TIF`, `TIFF`, `WEBP`, `HEIC`

## Download

The latest beta release is available on GitHub:

**[Download EXIF Organizer v0.5 Beta](https://github.com/brasbat/exif-organizer/releases/tag/v0.5-beta)**

Available packages:

- [Windows x64 installer (`.exe`)](https://github.com/brasbat/exif-organizer/releases/download/v0.5-beta/exif-organizer-windows-x64.exe)
- [macOS disk image (`.dmg`)](https://github.com/brasbat/exif-organizer/releases/download/v0.5-beta/exif-organizer-macos.dmg)

This release is a beta. Keep backups of important files before using move mode.

## Usage

1. Start EXIF Organizer.
2. Add one or more source folders.
3. Choose a destination folder, or use the default destination shown by the application.
4. Choose whether subfolders should be scanned.
5. Select the EXIF properties that should form the folder structure.
6. Rescan if the source or scan options change.
7. Use the format checkboxes to include or exclude discovered image formats.
8. Review the preview and EXIF statistics.
9. Leave **Move files instead of copying** disabled to copy files, or enable it to move them.
10. Click **Organize files**.

Copy mode never replaces an existing file at the target path. Move mode adds a numeric suffix when necessary, such as `image_1.jpg`.

## Building from source

### Requirements

- Java 25
- Windows, macOS, or Linux for development
- Internet access on the first build so Gradle can download dependencies

The project includes the Gradle wrapper, so a separate Gradle installation is not required.

### Run the application

On Windows:

```powershell
.\gradlew.bat run
```

On macOS or Linux:

```bash
./gradlew run
```

### Build and test

```bash
./gradlew build
```

On Windows, use `.\gradlew.bat build` if the Unix-style wrapper command is unavailable.

### Build an application distribution

```bash
./gradlew installDist
```

The distribution is written to `build/install/exif-organizer`.

## Technology

- Java 25
- JavaFX
- Gradle
- Atlantafx Primer Dark theme
- metadata-extractor for EXIF metadata
- SQLite for the location database

## License

No license has currently been specified for this repository.
