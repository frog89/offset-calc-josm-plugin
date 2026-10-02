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

1. Render the selected imagery layer.
2. Detect edges using OpenCV Canny.
3. Rasterize geometries from the selected JOSM data layer.
4. Calculate a distance transform of the data geometries.
5. Search for the X/Y translation that minimizes the distance between
   imagery edges and data geometries.
6. Convert the pixel translation into the current JOSM projection.
7. Apply the result as a JOSM imagery offset.

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

    ...\offset-calc-josm-plugin> & "C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.1.3\plugins\maven\lib\maven3\bin\mvn.cmd" `
    install:install-file "-Dfile=D:\frank\prj\java\josm-projects\josm\dist\josm-custom.jar" "-DgroupId=org.openstreetmap.josm" "-DartifactId=josm" `
    "-Dversion=19627" "-Dpackaging=jar"