/*
 Copyright (c) 2026 Stephen Gold

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

import com.github.stephengold.wrench.enumerate.AssimpProcessFlag;
import com.github.stephengold.wrench.enumerate.Component;
import com.github.stephengold.wrench.enumerate.Primitive;
import com.github.stephengold.wrench.enumerate.UvTransform;
import com.jme3.math.Matrix4f;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;
import jme3utilities.Validate;
import org.lwjgl.assimp.Assimp;

/**
 * Construct an LwjglAssetKey.
 *
 * @author Stephen Gold sgold@sonic.net
 */
final public class AssetKeyBuilder {
    // *************************************************************************
    // constants and loggers

    /**
     * message logger for this class
     */
    final private static Logger logger
            = Logger.getLogger(AssetKeyBuilder.class.getName());
    // *************************************************************************
    // fields

    /**
     * {@code true} to enable verbose logging, otherwise {@code false}
     */
    private boolean isVerboseLogging;
    /**
     * post-processing flags, to be passed to
     * {@code aiImportFileExWithProperties()}
     */
    private int processFlags;
    /**
     * search path for texture assets (a sequence of format strings to try on
     * transformed asset paths), used to generate texture loaders
     */
    final private List<String> formatStrings = new ArrayList<>(16);
    /**
     * map from configuration property names to float values, used to generate
     * property stores
     */
    final private Map<String, Float> floatProperties = new TreeMap<>();
    /**
     * map from configuration property names to integer values, used to generate
     * property stores
     */
    final private Map<String, Integer> intProperties = new TreeMap<>();
    /**
     * map from configuration property names to 4x4 matrix values, used to
     * generate property stores
     */
    final private Map<String, Matrix4f> matrixProperties = new TreeMap<>();
    /**
     * enum value, used to generate texture loaders
     */
    private PathEdit pathEdit;
    /**
     * name of (path to) the asset
     */
    private String assetPath;
    // *************************************************************************
    // constructors

    /**
     * Instantiate a default builder.
     */
    public AssetKeyBuilder() {
        this.processFlags = LwjglAssetKey.defaultFlags;
        this.pathEdit = PathEdit.NoOp;
        this.assetPath = "";
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Add the specified post-processing flags.
     *
     * @param flags the post-processing flags to add (not {@code null},
     * unaffected)
     * @return the (modified) builder, for chaining
     */
    public AssetKeyBuilder add(AssimpProcessFlag... flags) {
        Validate.nonNullArray(flags, "flags");
        this.processFlags |= AssimpProcessFlag.combine(flags);

        return this;
    }

    /**
     * Add a format string to the end of the search path for texture assets.
     *
     * @param formatString the string to append (not {@code null})
     * @return the (modified) builder, for chaining
     */
    public AssetKeyBuilder appendFormatString(String formatString) {
        Validate.nonNull(formatString, "formatString");
        formatStrings.add(formatString);

        return this;
    }

    /**
     * Build a new asset key for the stored asset path.
     *
     * @return a new key
     */
    public LwjglAssetKey build() {
        LwjglAssetKey result = new LwjglAssetKey(assetPath, this);
        return result;
    }

    /**
     * Build a new asset key for the specified path. The stored asset path is
     * unaffected.
     *
     * @param assetPath the name of (path to) the asset (not {@code null}, not
     * empty)
     * @return a new key
     */
    public LwjglAssetKey build(String assetPath) {
        Validate.nonEmpty(assetPath, "asset path");
        LwjglAssetKey result = new LwjglAssetKey(assetPath, this);

        return result;
    }

    /**
     * Clear away all of the config properties.
     *
     * @return the (modified) pre-existing builder
     */
    public AssetKeyBuilder clearConfigProperties() {
        floatProperties.clear();
        intProperties.clear();
        matrixProperties.clear();

        return this;
    }

    /**
     * Remove all the format strings from the search path for texture assets.
     *
     * @return the (modified) builder, for chaining
     */
    public AssetKeyBuilder clearFormatStrings() {
        formatStrings.clear();
        return this;
    }

    /**
     * Clear all of the post-processing flags.
     *
     * @return the (modified) pre-existing builder
     */
    public AssetKeyBuilder clearProcessFlags() {
        this.processFlags = 0x0;
        return this;
    }

    /**
     * Create and configure a texture loader using the path-edit value and
     * format strings.
     *
     * @return the new texture loader
     */
    public TextureLoader createTextureLoader() {
        int numFormats = formatStrings.size();
        String[] formatArray = new String[numFormats];
        formatStrings.toArray(formatArray);
        TextureLoader result = new TextureLoader(pathEdit, formatArray);

        return result;
    }

    /**
     * Delete the specified config property.
     *
     * @param name the name of config property to delete
     * @return the (modified) pre-existing builder
     */
    public AssetKeyBuilder deleteConfigProperty(String name) {
        floatProperties.remove(name);
        intProperties.remove(name);
        matrixProperties.remove(name);

        return this;
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
     * Return the asset path
     *
     * @return the name of (path to) the asset (not {@code null})
     */
    public String getAssetPath() {
        assert assetPath != null;
        return assetPath;
    }

    /**
     * Access the map from config-property names to float values.
     *
     * @return the pre-existing collection (not {@code null})
     */
    Map<String, Float> getFloatProperties() {
        assert floatProperties != null;
        return floatProperties;
    }

    /**
     * Access the map from config-property names to integer values.
     *
     * @return the pre-existing collection (not {@code null})
     */
    Map<String, Integer> getIntegerProperties() {
        assert intProperties != null;
        return intProperties;
    }

    /**
     * Access the map from config-property names to matrix values.
     *
     * @return the pre-existing collection (not {@code null})
     */
    Map<String, Matrix4f> getMatrixProperties() {
        return matrixProperties;
    }

    /**
     * Return the current post-processing flags.
     *
     * @return the flags
     */
    public int getProcessFlags() {
        return processFlags;
    }

    /**
     * Test whether verbose logging will be enabled.
     *
     * @return {@code true} if verbose logging will be enabled, otherwise
     * {@code false}
     */
    public boolean isVerboseLogging() {
        return isVerboseLogging;
    }

    /**
     * Remove the specified post-processing flags.
     *
     * @param flags the post-processing flags to remove (not {@code null})
     * @return the (modified) builder, for chaining
     */
    public AssetKeyBuilder remove(AssimpProcessFlag... flags) {
        Validate.nonNullArray(flags, "flags");
        this.processFlags &= ~AssimpProcessFlag.combine(flags);

        return this;
    }

    /**
     * Alter the stored asset path.
     *
     * @param path the name of (path to) the asset (not {@code null})
     * @return the (modified) builder, for chaining
     */
    public AssetKeyBuilder setAssetPath(String path) {
        Validate.nonNull(path, "path");
        this.assetPath = path;

        return this;
    }

    /**
     * Enable or disable the specified boolean import property.
     *
     * @param name the name of the import property
     * @param enable the desired setting ({@code true} to enable, {@code false}
     * to disable)
     * @return the (modified) builder, for chaining
     */
    public AssetKeyBuilder setBooleanProperty(String name, boolean enable) {
        int intValue = enable ? 1 : 0;
        intProperties.put(name, intValue);

        return this;
    }

    /**
     * Specify the smoothing threshold for
     * {@code AssimpProcessFlag.CALC_TANGENT_SPACE}.
     *
     * @param angle the desired angle (in degrees, &ge;0, &lt;45, default=45)
     * @return the (modified) builder, for chaining
     */
    public AssetKeyBuilder setCtMaxSmoothingAngle(float angle) {
        setFloatProperty(Assimp.AI_CONFIG_PP_CT_MAX_SMOOTHING_ANGLE, angle);
        return this;
    }

    /**
     * Specify the channel index for
     * {@code AssimpProcessFlag.CALC_TANGENT_SPACE}.
     *
     * @param index the desired index (default=0)
     * @return the (modified) builder, for chaining
     */
    public AssetKeyBuilder setCtTextureChannelIndex(int index) {
        setIntegerProperty(Assimp.AI_CONFIG_PP_CT_TEXTURE_CHANNEL_INDEX, index);
        return this;
    }

    /**
     * Enable or disable all-or-none behavior when using
     * {@code AssimpProcessFlag.DEBONE}.
     *
     * @param enable {@code true} to enable the behavior, or {@code false} to
     * disable it (default=false)
     * @return the (modified) builder, for chaining
     */
    public AssetKeyBuilder setDbAllOrNone(boolean enable) {
        setBooleanProperty(Assimp.AI_CONFIG_PP_DB_ALL_OR_NONE, enable);
        return this;
    }

    /**
     * Specify the threshold for {@code AssimpProcessFlag.DEBONE}.
     *
     * @param threshold the desired threshold (default=1)
     * @return the (modified) builder, for chaining
     */
    public AssetKeyBuilder setDbThreshold(float threshold) {
        setFloatProperty(Assimp.AI_CONFIG_PP_DB_THRESHOLD, threshold);
        return this;
    }

    /**
     * Enable or disable removal of very small triangles when using
     * {@code AssimpProcessFlag.FIND_DEGENERATES}.
     *
     * @param remove {@code true} to enable removal, or {@code false} to disable
     * it (default=false)
     * @return the (modified) builder, for chaining
     */
    public AssetKeyBuilder setFdCheckArea(boolean remove) {
        setBooleanProperty(Assimp.AI_CONFIG_PP_FD_CHECKAREA, remove);
        return this;
    }

    /**
     * Enable or disable removal of degenerate primitives when using
     * {@code AssimpProcessFlag.FIND_DEGENERATES}.
     *
     * @param remove {@code true} to enable removal, or {@code false} to disable
     * it (default=false)
     * @return the (modified) builder, for chaining
     */
    public AssetKeyBuilder setFdRemove(boolean remove) {
        setBooleanProperty(Assimp.AI_CONFIG_PP_FD_REMOVE, remove);
        return this;
    }

    /**
     * Specify the tolerance for detecting duplicate animation tracks with
     * {@code AssimpProcessFlag.FIND_INVALID_DATA}.
     *
     * @param epsilon the desired tolerance (default=0)
     * @return the (modified) builder, for chaining
     */
    public AssetKeyBuilder setFidAnimAccuracy(float epsilon) {
        setFloatProperty(Assimp.AI_CONFIG_PP_FID_ANIM_ACCURACY, epsilon);
        return this;
    }

    /**
     * Enable or disable ignoring texture coordinates with
     * {@code AssimpProcessFlag.FIND_INVALID_DATA}.
     *
     * @param ignore {@code true} to enable ignoring them or {@code false} to
     * disable ignoring them (default=false)
     * @return the (modified) builder, for chaining
     */
    public AssetKeyBuilder setFidIgnoreTextureCoords(boolean ignore) {
        setBooleanProperty(
                Assimp.AI_CONFIG_PP_FID_IGNORE_TEXTURECOORDS, ignore);
        return this;
    }

    /**
     * Specify a float value for the specified import property.
     *
     * @param name the name of the import property
     * @param value the desired value
     * @return the (modified) builder, for chaining
     */
    public AssetKeyBuilder setFloatProperty(String name, float value) {
        floatProperties.put(name, value);
        return this;
    }

    /**
     * Replace all the format strings in the search path for texture assets.
     *
     * @param strings the desired sequence of strings (not {@code null})
     * @return the (modified) builder, for chaining
     */
    public AssetKeyBuilder setFormatStrings(String... strings) {
        Validate.nonNullArray(strings, "strings");

        formatStrings.clear();
        formatStrings.addAll(Arrays.asList(strings));

        return this;
    }

    /**
     * Specify the scale factor for {@code AssimpProcessFlag.GLOBAL_SCALE}.
     *
     * @param scaleFactor the desired scale factor
     * @return the (modified) builder, for chaining
     */
    public AssetKeyBuilder setGlobalScale(float scaleFactor) {
        setFloatProperty(Assimp.AI_CONFIG_GLOBAL_SCALE_FACTOR_KEY, scaleFactor);
        return this;
    }

    /**
     * Specify the smoothing threshold for
     * {@code AssimpProcessFlag.GEN_SMOOTH_NORMALS}.
     *
     * @param angle the desired angle (in degrees, &ge;0, &lt;175, default=175)
     * @return the (modified) builder, for chaining
     */
    public AssetKeyBuilder setGsnMaxSmoothingAngle(float angle) {
        setFloatProperty(Assimp.AI_CONFIG_PP_GSN_MAX_SMOOTHING_ANGLE, angle);
        return this;
    }

    /**
     * Specify the target cache size for
     * {@code AssimpProcessFlag.IMPROVE_CACHE_LOCALITY}.
     *
     * @param size the desired cache size (default=12)
     * @return the (modified) builder, for chaining
     */
    public AssetKeyBuilder setIclPtCacheSize(int size) {
        setIntegerProperty(Assimp.AI_CONFIG_PP_ICL_PTCACHE_SIZE, size);
        return this;
    }

    /**
     * Specify an integer value for the specified import property.
     *
     * @param name the name of the import property
     * @param value the desired value
     * @return the (modified) builder, for chaining
     */
    public AssetKeyBuilder setIntegerProperty(String name, int value) {
        intProperties.put(name, value);
        return this;
    }

    /**
     * Specify the maximum number of bone weights per vertex for
     * {@code AssimpProcessFlag.LIMIT_BONE_WEIGHTS}.
     *
     * @param numWeights the desired limit (default=4)
     * @return the (modified) builder, for chaining
     */
    public AssetKeyBuilder setLbwMaxWeights(int numWeights) {
        setIntegerProperty(Assimp.AI_CONFIG_PP_LBW_MAX_WEIGHTS, numWeights);
        return this;
    }

    /**
     * Specify a matrix value for the specified import property.
     *
     * @param name the name of the import property
     * @param value the desired value (not {@code null}, unaffected)
     * @return the (modified) builder, for chaining
     */
    public AssetKeyBuilder setMatrixProperty(String name, Matrix4f value) {
        Matrix4f clone = value.clone();
        matrixProperties.put(name, clone);

        return this;
    }

    /**
     * Alter the path-edit option for texture loaders.
     *
     * @param pathEdit the desired enum value (not {@code null}, default=NoOp)
     * @return the (modified) builder, for chaining
     */
    public AssetKeyBuilder setPathEdit(PathEdit pathEdit) {
        Validate.nonNull(pathEdit, "path edit");
        this.pathEdit = pathEdit;

        return this;
    }

    /**
     * Alter the post-processing flags.
     *
     * @param flags the desired post-processing flag values, ORed together
     * (default=0x942b)
     * @return the (modified) builder, for chaining
     */
    public AssetKeyBuilder setProcessFlags(int flags) {
        this.processFlags = flags;
        return this;
    }

    /**
     * Enable or disable applying a root transformation with the
     * {@code PRE_TRANSFORM_VERTICES} post-processing flag.
     *
     * @param apply {@code true} to enable application of the transform or
     * {@code false} to disable it (default=false)
     * @return the (modified) builder, for chaining
     */
    public AssetKeyBuilder setPtvAddRootTransformation(boolean apply) {
        setBooleanProperty(
                Assimp.AI_CONFIG_PP_PTV_ADD_ROOT_TRANSFORMATION, apply);
        return this;
    }

    /**
     * Enable or disable preserving the hierarchy when using
     * {@code AssimpProcessFlag.PRE_TRANSFORM_VERTICES}.
     *
     * @param preserve {@code true} to enable preservation of the hierarchy or
     * {@code false} to disable it (default=false)
     * @return the (modified) builder, for chaining
     */
    public AssetKeyBuilder setPtvKeepHierarchy(boolean preserve) {
        setBooleanProperty(Assimp.AI_CONFIG_PP_PTV_KEEP_HIERARCHY, preserve);
        return this;
    }

    /**
     * Enable or disable normalizing coordinates into the range -1 ... 1 when
     * using {@code AssimpProcessFlag.PRE_TRANSFORM_VERTICES}.
     *
     * @param normalize {@code true} to enable normalization of coordinates or
     * {@code false} to disable it (default=false)
     * @return the (modified) builder, for chaining
     */
    public AssetKeyBuilder setPtvNormalize(boolean normalize) {
        setBooleanProperty(Assimp.AI_CONFIG_PP_PTV_NORMALIZE, normalize);
        return this;
    }

    /**
     * Specify the root transformation to apply when using
     * {@code AssimpProcessFlag.PRE_TRANSFORM_VERTICES}.
     *
     * @param matrix the desired transformation matrix (not {@code null},
     * unaffected, default=Identity)
     * @return the (modified) builder, for chaining
     */
    public AssetKeyBuilder setPtvRootTransformation(Matrix4f matrix) {
        setMatrixProperty(Assimp.AI_CONFIG_PP_PTV_ROOT_TRANSFORMATION, matrix);
        return this;
    }

    /**
     * Specify all the AIMesh/AIScene components to be removed when using
     * {@code AssimpProcessFlag.REMOVE_COMPONENT}.
     *
     * @param components the components to remove (not {@code null}, unaffected)
     * @return the (modified) builder, for chaining
     */
    public AssetKeyBuilder setRvcFlags(Component... components) {
        Validate.nonNullArray(components, "components");

        int intValue = Component.combine(components);
        setIntegerProperty(Assimp.AI_CONFIG_PP_RVC_FLAGS, intValue);

        return this;
    }

    /**
     * Specify the maximum number of bones for
     * {@code AssimpProcessFlag.SPLIT_BY_BONE_COUNT}.
     *
     * @param numBones the desired limit (default=60)
     * @return the (modified) builder, for chaining
     */
    public AssetKeyBuilder setSbbcMaxBones(int numBones) {
        setIntegerProperty(Assimp.AI_CONFIG_PP_SBBC_MAX_BONES, numBones);
        return this;
    }

    /**
     * Specify which types of primitives to discard when using
     * {@code AssimpProcessFlag.SORT_BY_PTYPE}.
     *
     * @param primitiveTypes the types to discard (not {@code null}, unaffected)
     * @return the (modified) builder, for chaining
     */
    public AssetKeyBuilder setSbpRemove(Primitive... primitiveTypes) {
        Validate.nonNullArray(primitiveTypes, "primitive types");

        int intValue = Primitive.combine(primitiveTypes);
        setIntegerProperty(Assimp.AI_CONFIG_PP_SBP_REMOVE, intValue);

        return this;
    }

    /**
     * Specify the triangle limit for
     * {@code AssimpProcessFlag.SPLIT_LARGE_MESHES}.
     *
     * @param numFaces the desired limit (default=1_000_000)
     * @return the (modified) builder, for chaining
     */
    public AssetKeyBuilder setSlmTriangleLimit(int numFaces) {
        setIntegerProperty(Assimp.AI_CONFIG_PP_SLM_TRIANGLE_LIMIT, numFaces);
        return this;
    }

    /**
     * Specify the vertex limit for
     * {@code AssimpProcessFlag.SPLIT_LARGE_MESHES}.
     *
     * @param numVertices the desired limit (default=1_000_000)
     * @return the (modified) builder, for chaining
     */
    public AssetKeyBuilder setSlmVertexLimit(int numVertices) {
        setIntegerProperty(Assimp.AI_CONFIG_PP_SLM_VERTEX_LIMIT, numVertices);
        return this;
    }

    /**
     * Specify all the UV transformations to be applied when using
     * {@code AssimpProcessFlag.TRANSFORM_UV_COORDS}.
     *
     * @param transforms the transforms to apply (not {@code null}, unaffected)
     * @return the (modified) builder, for chaining
     */
    public AssetKeyBuilder setTuvEvaluate(UvTransform... transforms) {
        Validate.nonNullArray(transforms, "transforms");

        int intValue = UvTransform.combine(transforms);
        setIntegerProperty(Assimp.AI_CONFIG_PP_TUV_EVALUATE, intValue);

        return this;
    }

    /**
     * Enable or disable verbose logging.
     *
     * @param setting {@code true} to enable verbose logging, {@code false} to
     * disable it (default=false)
     * @return the (modified) builder, for chaining
     */
    public AssetKeyBuilder setVerboseLogging(boolean setting) {
        this.isVerboseLogging = setting;
        return this;
    }
}
