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
 * AssetGroup for the glTF sample models.
 *
 * @author Stephen Gold sgold@sonic.net
 */
class GltfSampleModels implements AssetGroup {
    // *************************************************************************
    // constants and loggers

    /**
     * message logger for this class
     */
    final static Logger logger
            = Logger.getLogger(GltfSampleModels.class.getName());
    // *************************************************************************
    // fields

    /**
     * true if accessible, otherwise false
     */
    final private boolean isAccessible;
    /**
     * for generating the asset path to an asset
     */
    final private String assetPathFormat;
    /**
     * filesystem path to the asset root
     */
    final private String rootPath;
    /**
     * names of the assets in ascending lexicographic order (not empty)
     */
    final private String[] namesArray;
    // *************************************************************************
    // constructors

    /**
     * Instantiate a group for the specified specification and asset form.
     *
     * @param version which version of the glTF specification ("1.0" or "2.0")
     * @param form which asset form ("glTF", "glTF-Binary", "glTF-Draco",
     * "glTF-Embedded", or "glTF-MaterialsCommon")
     */
    GltfSampleModels(String version, String form) {
        String path
                = String.format("../../ext/glTF-Sample-Models/%s/", version);
        String fileSeparator = System.getProperty("file.separator");
        this.rootPath = path.replace("/", fileSeparator);

        // Test for accessibility:
        File testDir = new File(rootPath);
        this.isAccessible = testDir.isDirectory() && testDir.canRead();
        if (!isAccessible) {
            String cwd = System.getProperty("user.dir");
            logger.log(Level.WARNING, "{0} is not accessible from {1}.",
                    new Object[]{
                        MyString.quote(rootPath), MyString.quote(cwd)
                    });
        }

        String pathFormat;
        switch (form) {
            case "glTF":
                pathFormat = "%s/glTF/%s.gltf";
                break;

            case "glTF-Binary":
                pathFormat = "%s/glTF-Binary/%s.glb";
                break;

            case "glTF-Draco":
                pathFormat = "%s/glTF-Draco/%s.gltf";
                break;

            case "glTF-Embedded":
                pathFormat = "%s/glTF-Embedded/%s.gltf";
                break;

            case "glTF-MaterialsCommon":
                pathFormat = "%s/glTF-MaterialsCommon/%s.gltf";
                break;

            default:
                throw new IllegalArgumentException("form = " + form);
        }
        this.assetPathFormat = pathFormat;

        // Populate the list of asset names:
        String[] fileNames = testDir.list();
        int numNames = fileNames.length;
        List<String> namesList = new ArrayList<>(numNames);
        for (String fileName : fileNames) {
            if (!fileName.contains(".")) {
                String assetPath
                        = String.format(assetPathFormat, fileName, fileName);
                assetPath = assetPath.replace("/", fileSeparator);
                File mainFile = new File(testDir, assetPath);
                if (mainFile.canRead()) {
                    namesList.add(fileName);
                }
            }
        }
        assert !namesList.isEmpty() :
                "version = " + version + ", form = " + form;

        numNames = namesList.size();
        this.namesArray = new String[numNames];
        namesList.toArray(namesArray);
        Arrays.sort(namesArray);
    }
    // *************************************************************************
    // AssetGroup methods

    /**
     * Return the path to the specified asset.
     *
     * @param assetName the name of the asset (not null)
     * @return the asset path (not null)
     */
    @Override
    public String assetPath(String assetName) {
        String result = String.format(assetPathFormat, assetName, assetName);
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
        return rootPath;
    }
}
