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
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import jme3utilities.MyString;

/**
 * AssetGroup for a subset of the Assimp test models.
 * <p>
 * https://github.com/assimp/assimp
 *
 * @author Stephen Gold sgold@sonic.net
 */
class AssimpTest implements AssetGroup {
    // *************************************************************************
    // constants and loggers

    /**
     * message logger for this class
     */
    final private static Logger logger
            = Logger.getLogger(AssimpTest.class.getName());
    // *************************************************************************
    // fields

    /**
     * true if accessible, otherwise false
     */
    final private boolean isAccessible;
    /**
     * file extension for assets (".gltf", for example)
     */
    final private String fileExtension;
    /**
     * which file format ("3DS", "3MF", "BLEND", "Collada", "FBX", "glTF",
     * "glTF2", "LWO", "OBJ", "Ogre", "PLY", or "STL")
     */
    final private String format;
    /**
     * names of the assets in ascending lexicographic order (not empty)
     */
    final private String[] namesArray;
    // *************************************************************************
    // constructors

    /**
     * Instantiate a group for the specified file format.
     *
     * @param format which file format ("3DS", "3MF", "BLEND", "Collada", "FBX",
     * "glTF", "glTF2", "LWO", "OBJ", "Ogre", "PLY", or "STL")
     */
    AssimpTest(String format) {
        switch (format) {
            case "3DS":
            case "3MF":
            case "BLEND":
            case "FBX":
            case "glTF":
            case "LWO":
            case "OBJ":
            case "PLY":
            case "STL":
                this.fileExtension = "." + format.toLowerCase();
                break;

            case "Collada":
                this.fileExtension = ".dae";
                break;

            case "glTF2":
                this.fileExtension = ".gltf";
                break;

            case "Ogre":
                this.fileExtension = ".mesh.xml";
                break;

            default:
                throw new IllegalArgumentException("format = " + format);
        }
        this.format = format;
        String testPath
                = "../../ext/assimp/test/models/" + format + "/";

        // Test for overall accessibility:
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

        // Populate the array of asset names:
        String[] fileNames = testDir.list();
        if (fileNames == null) {
            this.namesArray = null;
            return;
        }
        Set<String> nameSet = new TreeSet<>();
        for (String fileName : fileNames) {
            File file = new File(testDir, fileName);
            if (!file.canRead()) {
                continue;
            }

            if (file.isDirectory()) { // Scan the subdirectory:
                String[] fileNames2 = file.list();
                for (String fn2 : fileNames2) {
                    if (fn2.endsWith(fileExtension)) {
                        String name = MyString.removeSuffix(fn2, fileExtension);
                        nameSet.add(fileName + "/" + name);
                    }
                }

            } else if (fileName.endsWith(fileExtension)) {
                String name
                        = MyString.removeSuffix(fileName, fileExtension);
                nameSet.add(name);

            }
        }
        int numNames = nameSet.size();
        if (numNames == 0) {
            this.namesArray = null;
            return;
        }

        this.namesArray = new String[numNames];
        nameSet.toArray(namesArray);
    }
    // *************************************************************************
    // AssetGroup methods

    /**
     * Return the path to the specified asset.
     *
     * @param assetName the name of the asset (not null)
     * @return the asset path, or null if the path is unknown
     */
    @Override
    public String assetPath(String assetName) {
        String result = format + "/" + assetName + fileExtension;
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
    public String[] listAssets() {
        return namesArray;
    }

    /**
     * Return the asset root for the specified asset.
     *
     * @param assetName the name of the asset (not null)
     * @return a filesystem path (not null, not empty)
     */
    @Override
    public String rootPath(String assetName) {
        String path = "../../ext/assimp/test/models/";
        String fileSeparator = System.getProperty("file.separator");
        String result = path.replace("/", fileSeparator);

        return result;
    }
}
