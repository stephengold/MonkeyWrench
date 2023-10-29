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
            | Assimp.aiProcess_ValidateDataStructure
            | Assimp.aiProcess_RemoveRedundantMaterials
            | Assimp.aiProcess_SortByPType //| Assimp.aiProcess_FlipUVs
            ;
    /**
     * message logger for this class
     */
    final private static Logger logger
            = Logger.getLogger(LwjglAssetKey.class.getName());
    // *************************************************************************
    // fields - TODO include property store?

    /**
     * true to enable verbose logging, otherwise false
     * <p>
     * Note: does not affect {@code equals()} or {@code hashCode()}!
     */
    private boolean isVerboseLogging = true;
    /**
     * post-processing options, to be passed to {@code aiImportFile()}
     */
    final private int flags;
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
        this(assetPath, defaultFlags);
    }

    /**
     * Instantiate a key with the specified post-processing options.
     *
     * @param assetPath the name of (path to) the asset (not null)
     * @param flags the desired post-processing flag values, ORed together
     * (default=0x940b)
     */
    public LwjglAssetKey(String assetPath, int flags) {
        super(assetPath);
        this.flags = flags;
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
     * @param setting true to enable, false to disable (default=true)
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
            result = super.equals(otherKey) && (flags == otherKey.flags());
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

        return result;
    }
}
