/*
 Copyright (c) 2023, Stephen Gold

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions are met:

 1. Redistributions of source code must retain the above copyright notice, this
    list of conditions and the following disclaimer.

 2. Redistributions in binary form must reproduce the above copyright notice,
    this list of conditions and the following disclaimer in the documentation
    and/or other materials provided with the distribution.

 3. Neither the name of the copyright holder nor the names of its
    contributors may be used to endorse or promote products derived from
    this software without specific prior written permission.

 THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.github.stephengold.wrench.test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jme3utilities.MyString;

/**
 * ModelGroup for assets downloaded from Sketchfab.
 *
 * @author Stephen Gold sgold@sonic.net
 */
class SketchfabData implements ModelGroup {
    // *************************************************************************
    // constants and loggers

    /**
     * message logger for this class
     */
    final static Logger logger
            = Logger.getLogger(SketchfabData.class.getName());
    // *************************************************************************
    // fields

    /**
     * true if accessible, otherwise false
     */
    final private boolean isAccessible;
    /**
     * file extension for assets
     */
    final private String extension;
    /**
     * for generating the filesystem path to an asset root, with forward slashes
     */
    final private String rootPathFormat;
    /**
     * names of the assets in ascending lexicographic order
     */
    final private String[] namesArray;
    // *************************************************************************
    // constructors

    /**
     * Instantiate a group for the specified file format.
     *
     * @param format which file format ("blend", "dae", "fbx", "glb", "glTF", or
     * "obj")
     */
    SketchfabData(String format) {
        String testPath;
        switch (format) {
            case "blend":
            case "dae":
            case "fbx":
            case "obj":
                testPath = "../downloads/Sketchfab/" + format + "/";
                this.extension = "." + format;
                this.rootPathFormat = testPath + "%s.zip";
                break;

            case "glb":
                testPath = "../downloads/Sketchfab/glb/";
                this.extension = ".glb";
                this.rootPathFormat = testPath;
                break;

            case "glTF":
                testPath = "../downloads/Sketchfab/glTF/";
                this.extension = ".gltf";
                this.rootPathFormat = testPath + "%s.zip";
                break;

            default:
                throw new IllegalArgumentException("format = " + format);
        }

        // Test for accessibility:
        String fileSeparator = System.getProperty("file.separator");
        testPath = testPath.replace("/", fileSeparator);
        File testDir = new File(testPath);
        this.isAccessible = testDir.isDirectory() && testDir.canRead();
        if (!isAccessible) {
            String cwd = System.getProperty("user.dir");
            logger.log(Level.WARNING, "{0} is not accessible from {1}.",
                    new Object[]{
                        MyString.quote(testPath), MyString.quote(cwd)
                    });
        }

        // Populate the list of asset names:
        String[] fileNames = testDir.list();
        int numNames = fileNames.length;
        List<String> namesList = new ArrayList<>(numNames);
        for (String fileName : fileNames) {
            if (fileName.endsWith(".zip")) {
                String name = MyString.removeSuffix(fileName, ".zip");
                namesList.add(name);
            } else if (fileName.endsWith(".glb")) {
                String name = MyString.removeSuffix(fileName, ".glb");
                namesList.add(name);
            }
        }
        assert !namesList.isEmpty() : "format = " + format;

        numNames = namesList.size();
        this.namesArray = new String[numNames];
        namesList.toArray(namesArray);
        Arrays.sort(namesArray);
    }
    // *************************************************************************
    // ModelGroup methods

    /**
     * Return the path to the specified asset.
     *
     * @param assetName the name of the asset (not null)
     * @return the asset path, or null if the name is not recognized
     */
    @Override
    public String assetPath(String assetName) {
        if (extension.equals(".gltf")) {
            return "scene.gltf";
        } else if (extension.equals(".glb")) {
            return assetName + ".glb";
        }

        String fileName;
        switch (assetName) {
            case "2014-chevrolet-corvette-c7-stingray-rigged":
                // TODO materials look wrong
                fileName = "2014 - Chevrolet Covette C7 Stingray - Rigged";
                break;

            case "9a-91-assault-rifle-gameready-lowpoly":
                // TODO materials look wrong
                fileName = "9a91 anim ready";
                break;

            case "hazmat-ussr-backrooms":
                // TODO materials look wrong
                fileName = "lox";
                break;

            case "house-and-forge":
                fileName = "Forge";
                break;

            case "japanese-food-pack-sushi-free":
                fileName = "All";
                break;

            case "little-duck":
                fileName = "Pollito";
                break;

            case "neon-gun-v1":
                // TODO materials look wrong
                fileName = "neon_gun_v01";
                break;

            case "old-brick-warehouse":
                // TODO diffuse texture not found (.jpg -> .jpeg)
                fileName = "frtr";
                break;

            case "original-techno-cat-girl":
                // TODO materials look wrong
                fileName = "Cat_girl";
                break;

            case "phoenix-bird":
                // TODO textures not found (.tga -> .tga.png)
                fileName = "fly";
                break;

            case "skull-sword":
                fileName = "Sword";
                break;

            case "the-strawberry-elephant":
                // TODO textures not found (completely different names)
                // TODO materials render too dark
                fileName = "Elephant";
                break;

            default:
                return null;
        }
        String result = "source/" + fileName + extension;

        return result;
    }

    /**
     * Test whether this group is accessible.
     *
     * @return true if readable, otherwise false
     */
    @Override
    public boolean isAccessible() {
        return isAccessible;
    }

    /**
     * Enumerate the asset names.
     *
     * @return a pre-existing array of asset names (not null, all elements
     * non-null, in ascending lexicographic order)
     */
    @Override
    public String[] listModels() {
        return namesArray;
    }

    /**
     * Return the asset root for the specified asset.
     *
     * @param assetName the name of the asset (not null)
     * @return a filesystem path (not empty) or null if the name is not
     * recognized
     */
    @Override
    public String rootPath(String assetName) {
        String path = String.format(rootPathFormat, assetName);
        String fileSeparator = System.getProperty("file.separator");
        String result = path.replace("/", fileSeparator);

        return result;
    }
}
