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
package com.github.stephengold.wrench;

import com.jme3.asset.AssetManager;
import com.jme3.asset.AssetNotFoundException;
import com.jme3.asset.TextureKey;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.util.PlaceholderAssets;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;
import jme3utilities.Validate;

/**
 * Load non-embedded textures (such as PNG files) by making specific
 * modifications to the asset path. Instances are immutable.
 * <p>
 * Format strings incorporate the following semantics:<ul>
 * <li>%1$s gives the asset folder, the path to the folder from which the main
 * asset was loaded</li>
 * <li>%2$s gives the base name of the texture, the portion prior to the final
 * dot (".")</li>
 * <li>%3$s gives the extension of the texture, the portion from the final dot
 * (".") to the end of the asset path</li>
 * </ul>
 *
 * @author Stephen Gold sgold@sonic.net
 */
public class TextureLoader {
    // *************************************************************************
    // constants and loggers

    /**
     * message logger for this class
     */
    final private static Logger logger
            = Logger.getLogger(TextureLoader.class.getName());
    /**
     * default format string, for an asset path relative to the main asset
     */
    final private static String defaultFormat = "%s%s%s";
    // *************************************************************************
    // fields

    /**
     * to transform asset paths
     */
    final private PathEdit pathEdit;
    /**
     * search path for texture assets (a series of format strings to try on
     * transformed asset paths)
     */
    final private String[] searchPath;
    // *************************************************************************
    // constructors

    /**
     * Instantiate a loader with the minimal asset-path modifications.
     */
    public TextureLoader() {
        this(PathEdit.NoOp);
    }

    /**
     * Instantiate a loader with the specified asset-path modifications.
     *
     * @param pathEdit to transform asset paths (not null)
     * @param formats the search path for texture assets (an array of format
     * strings to try on transformed asset paths)
     */
    public TextureLoader(PathEdit pathEdit, String... formats) {
        Validate.nonNull(pathEdit, "path edit");
        this.pathEdit = pathEdit;

        int numFormats = formats.length;
        if (numFormats == 0) {
            this.searchPath = new String[]{defaultFormat};
        } else {
            this.searchPath = new String[numFormats];
            System.arraycopy(formats, 0, searchPath, 0, numFormats);
        }
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Load the specified texture asset, generating a placeholder if the asset
     * isn't found.
     *
     * @param assetPath a raw asset path obtained from an AIMaterialProperty
     * (not null)
     * @param assetFolder the asset path to the folder from which the main asset
     * was loaded (not null)
     * @param flipY true to reverse the Y coordinate when loading the image,
     * false to load them unflipped
     * @param assetManager for loading textures, including placeholders (not
     * null)
     * @return a new Texture instance (not null)
     */
    Texture load(String assetPath, String assetFolder, boolean flipY,
            AssetManager assetManager) throws IOException {
        assert assetFolder != null;
        assert assetManager != null;

        // Smoothe out any Assimp "wrinkles" in the texture path:
        if (assetPath.startsWith("1 1 ")) { // TODO what does this mean?
            logger.warning("texture asset path starts with 1 1");
            assetPath = assetPath.substring(4);
        } else if (assetPath.startsWith("//")) { // TODO what does this mean?
            logger.warning("texture asset path starts with //");
            assetPath = assetPath.substring(2);
        } else if (assetPath.startsWith("$//")) { // TODO what does this mean?
            assetPath = assetPath.substring(3);
        }

        // Apply URL decoding to the texture path:
        String charset = StandardCharsets.UTF_8.name();
        try {
            assetPath = URLDecoder.decode(assetPath, charset);
            // Decoding is needed to pass the "Box With Spaces" test!
        } catch (UnsupportedEncodingException exception) {
            // do nothing
        }

        if (pathEdit == PathEdit.LastComponent) {
            // Use only the last component of Windows-style asset paths:
            int charPos = assetPath.lastIndexOf("\\");
            if (charPos >= 0) {
                assetPath = assetPath.substring(charPos + 1);
            }
        }

        int numFormats = searchPath.length;
        List<AssetNotFoundException> exceptionList
                = new ArrayList<>(numFormats);

        // Attempt to load the texture using each format in the search path:
        Texture result = null;
        for (String apFormat : searchPath) {
            TextureKey textureKey
                    = createKey(assetPath, apFormat, assetFolder, flipY);
            try {
                result = assetManager.loadTexture(textureKey);
                break;
            } catch (AssetNotFoundException exception) {
                exceptionList.add(exception);
            }
        }

        // If not found anywhere on the search path, generate a placeholder:
        if (result == null) {
            for (AssetNotFoundException exception : exceptionList) {
                System.err.println(exception);
            }
            String apFormat = searchPath[0];
            TextureKey textureKey
                    = createKey(assetPath, apFormat, assetFolder, flipY);

            Image image = PlaceholderAssets.getPlaceholderImage(assetManager);
            result = new Texture2D(image);
            result.setKey(textureKey);
        }

        return result;
    }
    // *************************************************************************
    // Object methods

    /**
     * Test for equivalence with another Object.
     *
     * @param other the object to compare to (may be null, unaffected)
     * @return true if the objects are equivalent, otherwise false
     */
    @Override
    public boolean equals(Object other) {
        boolean result;
        if (other == this) {
            result = true;
        } else if (other == null || getClass() != other.getClass()) {
            result = false;
        } else {
            TextureLoader otherLoader = (TextureLoader) other;
            result = (pathEdit == otherLoader.pathEdit)
                    && Arrays.equals(searchPath, otherLoader.searchPath);
        }

        return result;
    }

    /**
     * Generate the hash code for the loader.
     *
     * @return a 32-bit value for use in hashing
     */
    @Override
    public int hashCode() {
        int result = pathEdit.hashCode();
        result = 31 * result + Arrays.hashCode(searchPath);

        return result;
    }
    // *************************************************************************
    // private methods

    /**
     * Create an asset key using the specified asset-path format and texture
     * path.
     *
     * @param assetPath the asset path of the texture (not null)
     * @param apFormat the asset-path format to use (not null)
     * @param assetFolder the asset path to the folder from which the main asset
     * was loaded (not null)
     * @param flipY true to reverse the image Y coordinate
     * @return a new key for texture loading (not null)
     */
    private static TextureKey createKey(String assetPath, String apFormat,
            String assetFolder, boolean flipY) {
        // Split the asset path into a base name and an extension:
        String baseName;
        String extension;
        int charPos = assetPath.lastIndexOf(".");
        if (charPos >= 0) {
            baseName = assetPath.substring(0, charPos);
            extension = assetPath.substring(charPos);
        } else {
            baseName = assetPath;
            extension = "";
        }

        String loadPath
                = String.format(apFormat, assetFolder, baseName, extension);

        TextureKey result = new TextureKey(loadPath);
        result.setFlipY(flipY);
        result.setGenerateMips(true);

        return result;
    }
}
