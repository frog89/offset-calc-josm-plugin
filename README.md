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

            This plugin automatically calculates the offset between
            a chosen OSM data layer and a chosen imagery layer.
    
            Prerequisites:
            - A data layer with building outlines (tag "building",
              closed ways) must be loaded.
            - An imagery layer (e.g. DOP or satellite imagery) must
              be loaded.
    
            Steps:
            1. Select a data layer and an imagery layer.
            2. Optionally enable "Selected buildings only" to use
               only the currently selected buildings.
            3. Press "OK" to start the calculation.
    
            The calculation:
            - Edges are extracted from the imagery crop
              (Canny edge detection).
            - Building outlines from the data layer are rasterized.
            - The plugin searches for the X/Y shift at which the
              imagery edges and the building edges match best.
            - The resulting shift is converted into the current
              JOSM projection and applied as an imagery offset.
    
            Notes:
            - The calculation requires a visible map view with
              loaded buildings.
            - A very small area or missing building edges can
              degrade the result.

## Build

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