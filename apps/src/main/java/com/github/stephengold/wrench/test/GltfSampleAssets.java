/*
 Copyright (c) 2023-2025 Stephen Gold

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

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.jme3.asset.AssetLoadException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import jme3utilities.MyString;

/**
 * AssetGroup for a subset of the glTF sample assets at
 * https://github.com/KhronosGroup/glTF-Sample-Assets
 *
 * @author Stephen Gold sgold@sonic.net
 */
class GltfSampleAssets implements AssetGroup {
    // *************************************************************************
    // constants and loggers

    /**
     * message logger for this class
     */
    final private static Logger logger
            = Logger.getLogger(GltfSampleAssets.class.getName());
    // *************************************************************************
    // fields

    /**
     * true if accessible, otherwise false
     */
    final private boolean isAccessible;
    /**
     * metadata for the available assets
     */
    final private Collection<SampleAsset> assets;
    /**
     * JSON parser
     */
    final private static Gson parser = new Gson();
    /**
     * filesystem path to the asset root (the "Models" directory)
     */
    private String rootPath;
    /**
     * selected asset variant ("glTF", "glTF-Binary", "glTF-Draco",
     * "glTF-Embedded", "glTF-IBL", "glTF-KTX-BasisU", or "glTF-Quantized")
     */
    final private String variant;
    /**
     * names of the assets in ascending lexicographic order (not empty)
     */
    final private String[] namesArray;
    // *************************************************************************
    // constructors

    /**
     * Instantiate a group for the specified variant, ignoring tags.
     *
     * @param variant which asset variant ("glTF", "glTF-Binary", "glTF-Draco",
     * "glTF-Embedded", "glTF-IBL", "glTF-KTX-BasisU", or "glTF-Quantized")
     */
    GltfSampleAssets(String variant) {
        this(new TreeSet<String>(), variant);
    }

    /**
     * Instantiate a group for the specified tags and variant.
     *
     * @param requiredTags tags for filtering (may include "core", "extension",
     * "issues", "showcase", "testing", or "written")
     * @param variant which asset variant ("glTF", "glTF-Binary", "glTF-Draco",
     * "glTF-Embedded", "glTF-IBL", "glTF-KTX-BasisU", or "glTF-Quantized")
     */
    GltfSampleAssets(Set<String> requiredTags, String variant) {
        switch (variant) {
            case "glTF":
            case "glTF-Binary":
            case "glTF-Draco":
            case "glTF-Embedded":
            case "glTF-IBL":
            case "glTF-KTX-BasisU":
            case "glTF-Quantized":
                this.variant = variant;
                break;

            default:
                throw new IllegalArgumentException("variant = " + variant);
        }

        this.rootPath = String.format("../../ext/glTF-Sample-Assets/Models");
        String fileSeparator = System.getProperty("file.separator");
        this.rootPath = rootPath.replace("/", fileSeparator);

        // Test for accessibility of the JSON file:
        File jsonFile = new File(rootPath, "model-index.json");
        this.isAccessible = jsonFile.isFile() && jsonFile.canRead();
        if (!isAccessible) {
            String cwd = System.getProperty("user.dir");
            logger.log(Level.WARNING, "{0} is not accessible from {1}.",
                    new Object[]{
                        MyString.quote(rootPath), MyString.quote(cwd)
                    });
        }

        // Open the JSON file as an InputStream:
        InputStream stream;
        try {
            stream = new FileInputStream(jsonFile);
        } catch (FileNotFoundException exception) {
            throw new AssetLoadException(
                    "Failed to open file: " + jsonFile, exception);
        }

        // Read the JSON data into one long text string:
        Charset charset = StandardCharsets.UTF_8;
        String charsetName = charset.name();
        String jsonString;
        try (Scanner scanner = new Scanner(stream, charsetName)) {
            scanner.useDelimiter("\\Z");
            jsonString = scanner.next();
        } catch (Exception exception) {
            throw new AssetLoadException(
                    "Failed to read file: " + jsonFile, exception);
        }

        // Parse the JSON string into a collection:
        TypeToken<Collection<SampleAsset>> assetCollection =
                new TypeToken<Collection<SampleAsset>>() {};
        this.assets = parser.fromJson(jsonString, assetCollection.getType());

        // Populate the array of asset names:
        Set<String> nameSet = new TreeSet<>();
        for (SampleAsset asset : assets) {
            if (!asset.hasTags(requiredTags)) {
                continue;
            }
            if (asset.providesVariant(variant)) {
                nameSet.add(asset.name());
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
     * @return the asset path (not null)
     */
    @Override
    public String assetPath(String assetName) {
        for (SampleAsset asset : assets) {
            if (asset.name().equals(assetName)) {
                String fileName = asset.fileName(variant);
                String result = String.format(
                        "%s/%s/%s", assetName, variant, fileName);

                return result;
            }
        }

        throw new RuntimeException("assetName = " + assetName);
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
