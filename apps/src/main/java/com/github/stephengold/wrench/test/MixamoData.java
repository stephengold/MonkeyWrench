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
import java.util.logging.Level;
import java.util.logging.Logger;
import jme3utilities.MyString;

/**
 * ModelGroup for assets downloaded from Mixamo.
 *
 * @author Stephen Gold sgold@sonic.net
 */
class MixamoData implements ModelGroup {
    // *************************************************************************
    // constants and loggers

    /**
     * message logger for this class
     */
    final static Logger logger = Logger.getLogger(MixamoData.class.getName());
    /**
     * names of the models/scenes in ascending lexicographic order
     */
    final private static String[] namesArray = {
        "Drake", "Erika", "Remy"
    };
    // *************************************************************************
    // fields

    /**
     * true if accessible, otherwise false
     */
    final private boolean isAccessible;
    /**
     * file extension for model/scene assets
     */
    final private String fileExtension;
    /**
     * for generating the filesystem path to an asset root, with forward slashes
     */
    final private String rootPathFormat;
    // *************************************************************************
    // constructors

    /**
     * Instantiate a group for the specified file format.
     *
     * @param format which file format ("dae", "fbx74", or "fbxBinary")
     */
    MixamoData(String format) {
        String testPath;
        switch (format) {
            case "dae":
                testPath = "../downloads/Mixamo/dae/";
                this.fileExtension = ".dae";
                this.rootPathFormat = testPath + "%s.zip";
                break;

            case "fbx74":
                testPath = "../downloads/Mixamo/fbx74/";
                this.fileExtension = ".fbx";
                this.rootPathFormat = testPath;
                break;

            case "fbxBinary":
                testPath = "../downloads/Mixamo/fbxBinary/";
                this.fileExtension = ".fbx";
                this.rootPathFormat = testPath;
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
    }
    // *************************************************************************
    // ModelGroup methods

    /**
     * Return the asset path to the specified model/scene.
     *
     * @param modelName the name of the model/scene (not null)
     * @return the asset path, or null if the name is not recognized
     */
    @Override
    public String assetPath(String modelName) {
        String result = fullName(modelName);
        if (result != null) {
            result += fileExtension;
        }

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
     * Enumerate the model/scene names.
     *
     * @return a pre-existing array of model/scene names (not null, all elements
     * non-null, in ascending lexicographic order)
     */
    @Override
    public String[] listModels() {
        return namesArray;
    }

    /**
     * Return the asset root for the specified model/scene.
     *
     * @param modelName the name of the model/scene (not null)
     * @return a filesystem path (not empty) or null if the name is not
     * recognized
     */
    @Override
    public String rootPath(String modelName) {
        String result = fullName(modelName);
        if (result != null) {
            String path = String.format(rootPathFormat, result);

            String fileSeparator = System.getProperty("file.separator");
            result = path.replace("/", fileSeparator);
        }

        return result;
    }
    // *************************************************************************
    // private methods

    /**
     * Return the full name the specified animation/model/scene.
     *
     * @param modelName the name of the animation/model/scene (not null)
     * @return the full name, or null if the name is not recognized
     */
    private String fullName(String modelName) {
        String result;
        switch (modelName) {
            case "Drake":
                result = "Ch25_nonPBR";
                break;

            case "Erika":
                result = "Erika Archer With Bow Arrow";
                break;

            case "Remy":
                result = "Remy";
                break;

            default:
                result = null;
        }

        return result;
    }
}
