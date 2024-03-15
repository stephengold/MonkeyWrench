/*
 Copyright (c) 2023-2024 Stephen Gold

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

import com.jme3.asset.AssetKey;
import com.jme3.asset.ModelKey;
import java.util.logging.Logger;
import org.lwjgl.assimp.Assimp;

/**
 * A custom AssetKey for the lwjgl-assimp based loader.
 *
 * @author Stephen Gold sgold@sonic.net
 */
public class LwjglAssetKey extends ModelKey {
    // *************************************************************************
    // constants and loggers

    /**
     * default post-processing options
     */
    final private static int defaultFlags
            = Assimp.aiProcess_CalcTangentSpace
            | Assimp.aiProcess_JoinIdenticalVertices
            | Assimp.aiProcess_Triangulate
            | Assimp.aiProcess_GenNormals
            | Assimp.aiProcess_ValidateDataStructure
            | Assimp.aiProcess_RemoveRedundantMaterials
            | Assimp.aiProcess_SortByPType //| Assimp.aiProcess_FlipUVs
            ;
    /**
     * message logger for this class
     */
    final private static Logger logger
            = Logger.getLogger(LwjglAssetKey.class.getName());
    /**
     * default options for loading non-embedded textures
     */
    final private static TextureLoader defaultTextureLoader
            = new TextureLoader();
    // *************************************************************************
    // fields - TODO include property store?

    /**
     * true to enable verbose logging, otherwise false
     * <p>
     * Note: does not affect {@code equals()} or {@code hashCode()}!
     */
    private boolean isVerboseLogging = false;
    /**
     * post-processing options, to be passed to {@code aiImportFile()}
     */
    final private int flags;
    /**
     * options for loading non-embedded textures (not null)
     */
    final private TextureLoader textureLoader;
    // *************************************************************************
    // constructors

    /**
     * Instantiate a key based on a generic AssetKey.
     *
     * @param assetKey the AssetKey to use (not null, unaffected)
     */
    public LwjglAssetKey(AssetKey<?> assetKey) {
        this(assetKey.getName());
    }

    /**
     * Instantiate a key with the default post-processing options.
     *
     * @param assetPath the name of (path to) the asset (not null)
     */
    public LwjglAssetKey(String assetPath) {
        this(assetPath, defaultTextureLoader, defaultFlags);
    }

    /**
     * Instantiate a key with the specified post-processing options.
     *
     * @param assetPath the name of (path to) the asset (not null)
     * @param flags the desired post-processing flag values, ORed together
     * (default=0x942b)
     */
    public LwjglAssetKey(String assetPath, int flags) {
        this(assetPath, defaultTextureLoader, flags);
    }

    /**
     * Instantiate a key with the specified texture loader.
     *
     * @param assetPath the name of (path to) the asset (not null)
     * @param textureLoader the desired texture-load options (not null)
     */
    public LwjglAssetKey(String assetPath, TextureLoader textureLoader) {
        this(assetPath, textureLoader, defaultFlags);
    }

    /**
     * Instantiate a key with the specified post-processing options and texture
     * loader.
     *
     * @param assetPath the name of (path to) the asset (not null)
     * @param textureLoader the desired texture-load options (not null)
     * @param flags the desired post-processing flag values, ORed together
     * (default=0x942b)
     */
    public LwjglAssetKey(
            String assetPath, TextureLoader textureLoader, int flags) {
        super(assetPath);
        assert assetPath != null;
        assert textureLoader != null;

        this.flags = flags;
        this.textureLoader = textureLoader;
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Return the post-processing flags to be passed to {@code aiImportFile()}.
     *
     * @return flag values, ORed together
     */
    public int flags() {
        return flags;
    }

    /**
     * Access the texture-load options.
     *
     * @return the pre-existing instance (not null)
     */
    public TextureLoader getTextureLoader() {
        assert textureLoader != null;
        return textureLoader;
    }

    /**
     * Test whether verbose logging should be enabled.
     *
     * @return true to enable, otherwise false
     */
    public boolean isVerboseLogging() {
        return isVerboseLogging;
    }

    /**
     * Enable or disable verbose logging.
     *
     * @param setting true to enable, false to disable (default=false)
     */
    public void setVerboseLogging(boolean setting) {
        this.isVerboseLogging = setting;
    }
    // *************************************************************************
    // ModelKey methods

    /**
     * Duplicate this key.
     *
     * @return a new instance (not null)
     */
    @Override
    public LwjglAssetKey clone() {
        return (LwjglAssetKey) super.clone();
    }

    /**
     * Test for equivalence with another Object. The {@code isVerboseLogging}
     * parameter is not taken into account because it shouldn't affect the
     * loaded model.
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
            LwjglAssetKey otherKey = (LwjglAssetKey) other;
            result = super.equals(otherKey)
                    && (flags == otherKey.flags())
                    && (textureLoader == otherKey.textureLoader);
        }

        return result;
    }

    /**
     * Generate the hash code for the key. The {@code isVerboseLogging}
     * parameter is not taken into account because it shouldn't affect the
     * loaded model.
     *
     * @return a 32-bit value for use in hashing
     */
    @Override
    public int hashCode() {
        int result = 5;
        result = 31 * result + super.hashCode();
        result = 31 * result + flags;
        result = 31 * result + textureLoader.hashCode();

        return result;
    }
}
