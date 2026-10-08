# offset-calc-josm-plugin

A JOSM plugin for calculating an offset between OSM geometry
and imagery.

## License

This project is licensed under the GNU General Public License
version 3 or later.

See the LICENSE file for the complete license text.

## Third-party software

This project uses the following open-source libraries:

- JOSM
- OpenPnP OpenCV
- Gson

See the respective project documentation and license information
for details.

## Algorithm

This plugin automatically calculates the offset between a chosen OSM data layer and a chosen imagery layer.
    
### Prerequisites:
- A data layer with building outlines (tag "building", closed ways) must be loaded.
- An imagery layer (e.g. DOP or satellite imagery) must be loaded.
    
### Steps:
1. Select a data layer and an imagery layer.
2. Optionally enable "Selected buildings only" to use only the currently selected buildings.
3. Press "OK" to start the calculation.
    
### The calculation:
- Edges are extracted from the imagery crop (Canny edge detection).
- Building outlines from the data layer are rasterized.
- The plugin searches for the X/Y shift at which the imagery edges and the building edges match best.
- The resulting shift is converted into the current JOSM projection and applied as an imagery offset.
    
### Notes:
- The calculation requires a visible map view with loaded buildings.
- A very small area or missing building edges can degrade the result.

## Description about the "Error:" number

The "Error:" number is displayed at the bottom of the message box at the end of the calculation.

### Summary
The Error value is the trimmed RMS distance in pixels between the imagery edges and the building edges – averaged over the best 75 % of buildings. It is a good measure of match quality, but not an absolute verdict: a low value does not automatically mean the offset is correct, and a high value does not necessarily mean it is wrong.

### What the number technically is
In BuildingEdgeMatcher.calculateError(dx, dy), the following happens:

1. Per building, for each geometry point, the distance to the nearest imagery edge is determined in pixels – via the precomputed distance-transform map.
2. That distance is truncated at 12 pixels (TRUNCATION_PIXELS = 12.0). Anything farther away counts as "no match" with a value of 12 – so it does not pull the optimum in a wrong direction.
3. From the squared distances, a per-building mean is computed and the square root is taken – the RMS error of that building.
4. The buildings are sorted by their RMS error. The worst 25 % are discarded (TRIM_FRACTION_KEPT = 0.75) – those are buildings with wrong geometry, roof lean, or clutter.
5. From the remaining buildings, the RMS across all buildings is computed. That is the number shown in the message box.

**In short:** It is the average pixel distance between the building edge and the nearest imagery edge, measured over the buildings that are good enough for matching.

### How to interpret the number

Error value	meaning:
- 0.0 to 0.5 px: Very good match. The building edges lie almost exactly on the imagery edges. The offset is very likely correct.
- 0.5 to 1.5 px: Good match. Small deviations due to antialiasing, roof edges, or slightly shifted geometry. The offset is usable.
- 1.5 to 3 px: Acceptable, but with caution. There are systematic deviations – e.g. roof slopes that run differently in the image than the building outline, or a mix of buildings with different quality. The offset may still be correct.
- 3 to 6 px: Weak match. Either the imagery is heavily distorted, the building geometry is inaccurate, or wrong edges were matched. The result should be verified manually.
- \> 6 px:	Poor match. The found offset is probably not trustworthy.
- = 12 px (truncation)	No meaningful match possible. All buildings were truncated – the imagery edges do not fit the buildings anywhere.

### Why the number is not an absolute verdict
1. It is relative to zoom. One pixel corresponds to about 1 m at a scale of 1 m/px, but only 20 cm at 0.2 m/px. An error of 1 px therefore means different things depending on zoom. For a proper assessment, you would multiply the error by metersPerPixel – that gives you an error in real meters.

2. It says nothing about the direction of the error. A low error may mean the offset is correct – or that the edges happen to be shifted in the same direction. The per-building diagnostic (logPerBuildingOffsets) shows whether all buildings prefer the same offset (then it is globally correct) or whether they point in different directions (then the offset is uncertain).

3. The 12 px truncation dilutes the value. Buildings with a very poor match contribute at most 12 px – that is intentional to dampen outliers, but it makes the absolute value less meaningful for the "worst" cases.

4. The 75 % trimming hides bad buildings. If 25 % of buildings match very poorly, you will not see that directly in the error value. The per-building statistics (building error statistics: count=…, min=…, median=…, mean=…, p90=…, p95=…, max=…) show you the distribution.

### When you can trust the result

Rules of thumb for a trustworthy result:

- Error < 1 px and the per-building statistics show a median close to the mean (no large spread).
- Median of per-building offsets is close to the global offset (global-to-median in the log output is small).
- building error statistics: p90 and p95 are not dramatically above the median.

If these three conditions are met, the offset is very likely correct – regardless of the exact error value.

## Plugin Build Process

Requirements:

- Java 11+
- Maven 3.8+ (3.9.9 als IntelliJ Bundle)
- JOSM

Run:

    mvn clean package

The resulting plugin is:

    target/offset-calc-josm-plugin.jar

Copy it to the JOSM plugins directory or install it through JOSM's
plugin manager during development.

Execute following command in Powershell to allow to use the josm jar file as Maven dependency in the plugin's pom.xml:

To develop against current (2026-10-07) version 19627 do following:

    ...\offset-calc-josm-plugin> & "C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.1.3\plugins\maven\lib\maven3\bin\mvn.cmd" `
    install:install-file `
    "-Dfile=D:\frank\prj\java\josm-projects\josm\dist\josm-custom.jar" `
    "-DgroupId=org.openstreetmap.josm" `
    "-DartifactId=josm" `
    "-Dversion=19627" `
    "-Dpackaging=jar"

To develop against 19613 do following:

    ...\offset-calc-josm-plugin> & "C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.1.3\plugins\maven\lib\maven3\bin\mvn.cmd" `
    install:install-file `
    "-Dfile=D:\frank\prog\josm\josm-tested.jar" `
    "-DgroupId=org.openstreetmap.josm" `
    "-DartifactId=josm" `
    "-Dversion=19613" `
    "-Dpackaging=jar"

# Test Hint

I have tested this plugin in my Windows 11 computer only. No guarantee that it works in other operating systems also.
