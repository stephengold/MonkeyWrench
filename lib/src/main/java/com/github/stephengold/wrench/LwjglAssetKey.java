/*
 Copyright (c) 2023-2026 Stephen Gold

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
import com.jme3.math.Matrix4f;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Logger;
import jme3utilities.Validate;
import org.lwjgl.assimp.AIMatrix4x4;
import org.lwjgl.assimp.AIPropertyStore;
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
     * default scale factor for use with the {@code GLOBAL_SCALE}
     * post-processing flag
     */
    final public static float defaultGlobalScale = 1f;
    /**
     * default post-processing flags
     */
    final public static int defaultFlags
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
    // fields

    /**
     * true to enable verbose logging, otherwise false
     * <p>
     * Note: does not affect {@code equals()} or {@code hashCode()}!
     */
    private boolean isVerboseLogging = false;
    /**
     * post-processing flags, to be passed to
     * {@code aiImportFileExWithProperties()}
     */
    final private int flags;
    /**
     * map from config property names to float values, used to generate property
     * stores
     */
    private Map<String, Float> floatProperties = new TreeMap<>();
    /**
     * map from config property names to integer values, used to generate
     * property stores
     */
    private Map<String, Integer> intProperties = new TreeMap<>();
    /**
     * map from config property names to 4x4 matrix values, used to generate
     * property stores
     */
    private Map<String, Matrix4f> matrixProperties = new TreeMap<>();
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
     * Instantiate a key with the default texture loader and post-processing
     * flags.
     *
     * @param assetPath the name of (path to) the asset (not null)
     */
    public LwjglAssetKey(String assetPath) {
        this(assetPath, defaultTextureLoader, defaultFlags);
    }

    /**
     * Instantiate a key with the default texture loader and specified
     * post-processing flags.
     *
     * @param assetPath the name of (path to) the asset (not null)
     * @param flags the desired post-processing flag values, ORed together
     * (default=0x942b)
     */
    public LwjglAssetKey(String assetPath, int flags) {
        this(assetPath, defaultTextureLoader, flags);
    }

    /**
     * Instantiate a key with the specified texture loader and default
     * post-processing flags.
     *
     * @param assetPath the name of (path to) the asset (not null)
     * @param textureLoader the desired texture-load options (not null)
     */
    public LwjglAssetKey(String assetPath, TextureLoader textureLoader) {
        this(assetPath, textureLoader, defaultFlags);
    }

    /**
     * Instantiate a key with the specified texture loader and post-processing
     * flags.
     *
     * @param assetPath the name of (path to) the asset (not null)
     * @param textureLoader the desired texture-load options (not null)
     * @param flags the desired post-processing flag values, ORed together
     * (default=0x942b)
     */
    public LwjglAssetKey(
            String assetPath, TextureLoader textureLoader, int flags) {
        this(assetPath, textureLoader, flags, defaultGlobalScale);
    }

    /**
     * Instantiate a key with the specified specified texture loader,
     * post-processing flags, and global scale factor.
     *
     * @param assetPath the name of (path to) the asset (not null)
     * @param textureLoader the desired texture-load options (not null)
     * @param flags the desired post-processing flag values, ORed together
     * (default=0x942b)
     * @param globalScale the desired scale factor, effective only if
     * {@code GLOBAL_SCALE} is set in the post‑processing flags (default=1)
     */
    public LwjglAssetKey(String assetPath, TextureLoader textureLoader,
            int flags, float globalScale) {
        super(assetPath);
        Validate.nonNull(assetPath, "asset path");
        Validate.nonNull(textureLoader, "texture loader");

        this.flags = flags;
        floatProperties.put(
                Assimp.AI_CONFIG_GLOBAL_SCALE_FACTOR_KEY, globalScale);
        this.textureLoader = textureLoader;
    }

    /**
     * Instantiate a key with the specified texture loader and post-processing
     * flags.
     *
     * @param assetPath the name of (path to) the asset (not null)
     * @param textureLoader the desired texture-load options (not null)
     * @param flags the desired post-processing flags
     */
    public LwjglAssetKey(String assetPath, TextureLoader textureLoader,
            AssimpProcessFlag... flags) {
        super(assetPath);
        Validate.nonNull(assetPath, "asset path");
        Validate.nonNull(textureLoader, "texture loader");

        this.flags = AssimpProcessFlag.combine(flags);
        floatProperties.put(
                Assimp.AI_CONFIG_GLOBAL_SCALE_FACTOR_KEY, defaultGlobalScale);
        this.textureLoader = textureLoader;
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Create and configure a collection of import properties to be passed to
     * {@code aiImportFileExWithProperties()}.
     *
     * @return a new instance (the caller should invoke
     * {@code aiReleasePropertyStore} on it)
     */
    AIPropertyStore createPropertyStore() {
        AIPropertyStore result = Assimp.aiCreatePropertyStore();

        for (Map.Entry<String, Float> entry : floatProperties.entrySet()) {
            String key = entry.getKey();
            float value = entry.getValue();
            Assimp.aiSetImportPropertyFloat(result, key, value);
        }

        for (Map.Entry<String, Integer> entry : intProperties.entrySet()) {
            String key = entry.getKey();
            int value = entry.getValue();
            Assimp.aiSetImportPropertyInteger(result, key, value);
        }

        for (Map.Entry<String, Matrix4f> entry : matrixProperties.entrySet()) {
            String key = entry.getKey();
            Matrix4f jmeMatrix = entry.getValue();
            AIMatrix4x4 aiMatrix = ConversionUtils.convertMatrix(jmeMatrix);
            Assimp.aiSetImportPropertyMatrix(result, key, aiMatrix);
        }

        return result;
    }

    /**
     * Return the float value of the specified import property, if any.
     *
     * @param name the name of the property (not {@code null})
     * @return the value, or {@code null} if no float value is set
     */
    public Float findFloatProperty(String name) {
        Float result = floatProperties.get(name);
        return result;
    }

    /**
     * Return the integer value of the specified import property, if any.
     *
     * @param name the name of the property (not {@code null})
     * @return the value, or {@code null} if no integer value is set
     */
    public Integer findIntegerProperty(String name) {
        Integer result = intProperties.get(name);
        return result;
    }

    /**
     * Return the matrix value of the specified import property, if any.
     *
     * @param name the name of the property (not {@code null})
     * @return a copy of the value, or {@code null} if no matrix value is set
     */
    public Matrix4f findMatrixProperty(String name) {
        Matrix4f alias = matrixProperties.get(name);
        Matrix4f result = (alias == null) ? null : alias.clone();

        return result;
    }

    /**
     * Return the post-processing flags to be passed to
     * {@code aiImportFileExWithProperties()}.
     *
     * @return flag values, ORed together
     */
    public int flags() {
        return flags;
    }

    /**
     * Return the scale factor for use with the {@code GLOBAL_SCALE}
     * post-processing flag.
     *
     * @return scale factor
     */
    public float getGlobalScale() {
        Float result
                = findFloatProperty(Assimp.AI_CONFIG_GLOBAL_SCALE_FACTOR_KEY);
        if (result == null) {
            result = defaultGlobalScale;
        }

        return result;
    }

    /**
     * Access the texture-load options.
     *
     * @return the pre-existing instance (not null)
     */
    public TextureLoader getTextureLoader() {
        Validate.nonNull(textureLoader, "texture loader");
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
        LwjglAssetKey result = (LwjglAssetKey) super.clone();

        result.floatProperties = new TreeMap<String, Float>();
        result.floatProperties.putAll(floatProperties);

        result.intProperties = new TreeMap<String, Integer>();
        result.intProperties.putAll(intProperties);

        result.matrixProperties = new TreeMap<String, Matrix4f>();
        Set<Map.Entry<String, Matrix4f>> entrySet
                = matrixProperties.entrySet();
        for (Map.Entry<String, Matrix4f> entry : entrySet) {
            String key = entry.getKey();
            Matrix4f copy = entry.getValue().clone();
            result.matrixProperties.put(key, copy);
        }

        return result;
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
                    && floatProperties.equals(otherKey.floatProperties)
                    && intProperties.equals(otherKey.intProperties)
                    && matrixProperties.equals(otherKey.matrixProperties)
                    && textureLoader.equals(otherKey.textureLoader);
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
        result = 31 * result + floatProperties.hashCode();
        result = 31 * result + intProperties.hashCode();
        result = 31 * result + matrixProperties.hashCode();
        result = 31 * result + textureLoader.hashCode();

        return result;
    }
}
