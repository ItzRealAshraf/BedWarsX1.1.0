# How to build BedWarsX (the jar MUST contain dev/bedwarsx/*.class, normal size ~100-200 KB)

## Option A - your PC (JDK 17+ and Maven installed)
    cd BedWarsX
    mvn clean package
    unzip -l target/BedWarsX.jar        # must list dev/bedwarsx/BedWarsX.class etc.
The jar is target/BedWarsX.jar. If it is ~12 KB the code was NOT compiled - read the red errors above it in the Maven output.

## Option B - no PC / phone: build on GitHub (free)
1. Create a new PRIVATE GitHub repository and upload the whole BedWarsX folder content (pom.xml, src/, .github/ ...).
2. Open the repo -> "Actions" tab -> "Build BedWarsX" -> the run finishes in ~1 minute.
3. Open the finished run -> "Artifacts" -> download BedWarsX (it is the jar).
If the build is red, open it and send the red error text to Claude.
