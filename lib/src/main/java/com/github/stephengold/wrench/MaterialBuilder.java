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
import com.jme3.material.Material;
import com.jme3.material.Materials;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Matrix3f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.util.PlaceholderAssets;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import jme3utilities.MyString;
import org.lwjgl.PointerBuffer;
import org.lwjgl.assimp.AIMaterial;
import org.lwjgl.assimp.AIMaterialProperty;
import org.lwjgl.assimp.Assimp;

/**
 * Gather the data needed to construct a JMonkeyEngine material.
 *
 * @author Stephen Gold sgold@sonic.net
 */
class MaterialBuilder {
    // *************************************************************************
    // constants and loggers

    /**
     * message logger for this class
     */
    final private static Logger logger
            = Logger.getLogger(MaterialBuilder.class.getName());
    // *************************************************************************
    // fields

    /**
     * for loading textures
     */
    final private AssetManager assetManager;
    /**
     * true to reverse the Y coordinate when loading images, false to load them
     * unflipped
     */
    final private boolean flipY;
    /**
     * true if using PBRLighting.j3md material definitions
     */
    final private boolean isPbr;
    /**
     * true if using Lighting.j3md material definitions
     */
    final private boolean isPhong;
    /**
     * true if using Unshaded.j3md material definitions
     */
    final private boolean isUnshaded;
    /**
     * true if verbose logging is enabled, otherwise false
     */
    final private boolean verboseLogging;
    /**
     * maps Assimp material keys to material properties
     */
    private Map<String, AIMaterialProperty> propMap = new TreeMap<>();
    /**
     * map names of tool-specific effects to {@code true} if enabled
     */
    final private Map<String, Boolean> isSfxEnabled = new TreeMap<>();
    /**
     * properties for texture sampling
     */
    final private Map<String, Sampler> samplerMap = new TreeMap<>();
    /**
     * JMonkeyEngine material under construction
     */
    private Material jmeMaterial;
    /**
     * linear transformation to apply to texture coordinates, or null if
     * unspecified
     */
    private Matrix3f uvTransform;
    /**
     * asset path to the folder from which the model/scene was loaded, for
     * loading textures
     */
    final private String assetFolder;
    /**
     * alpha mode when importing glTF materials
     */
    final private String gltfAlphaMode;
    /**
     * name of the JMonkeyEngine material definitions being used
     */
    final private String matDefs;
    /**
     * name of the material
     */
    final private String materialName;
    /**
     * array of embedded textures
     */
    final private Texture[] embeddedTextures;
    /**
     * source of texture coordinates, or null if unspecified
     */
    private VertexBuffer.Type uvSourceType;
    // *************************************************************************
    // constructors

    /**
     * Instantiate a builder for the specified {@code AIMaterial}.
     *
     * @param aiMaterial the Assimp material to convert (not null, unaffected)
     * @param index the index of the material in the model/scene (&ge;0)
     * @param assetManager for loading textures (not null, alias created)
     * @param assetFolder the asset path to the folder from which the
     * model/scene was loaded (not null, alias created)
     * @param embeddedTextures the array of embedded textures (not null, alias
     * created)
     * @param loadFlags post-processing flags that were passed to
     * {@code aiImportFile()}
     * @param verboseLogging true to enable verbose logging, otherwise false
     * @throws IOException if the Assimp material cannot be converted
     */
    MaterialBuilder(AIMaterial aiMaterial, int index, AssetManager assetManager,
            String assetFolder, Texture[] embeddedTextures, int loadFlags,
            boolean verboseLogging) throws IOException {
        assert assetManager != null;
        assert assetFolder != null;
        assert embeddedTextures != null;

        this.assetManager = assetManager;
        this.assetFolder = assetFolder;
        this.embeddedTextures = embeddedTextures;
        this.flipY = (loadFlags & Assimp.aiProcess_FlipUVs) == 0x0;
        this.verboseLogging = verboseLogging;

        // Use the material properties to populate propMap and samplerMap:
        PointerBuffer ppProperties = aiMaterial.mProperties();
        int numProperties = aiMaterial.mNumProperties();
        for (int i = 0; i < numProperties; ++i) {
            long handle = ppProperties.get(i);
            AIMaterialProperty property = AIMaterialProperty.createSafe(handle);
            String materialKey = property.mKey().dataString();
            String suffix = PropertyUtils.suffixString(property);
            if (suffix != null) {
                materialKey += " " + suffix;
                Sampler sampler = samplerMap.get(suffix);
                if (sampler == null) {
                    sampler = new Sampler();
                    samplerMap.put(suffix, sampler);
                }
            }

            AIMaterialProperty oldProperty = propMap.put(materialKey, property);
            assert oldProperty == null : materialKey;
        }

        // Determine the name of the material:
        AIMaterialProperty property = propMap.remove(Assimp.AI_MATKEY_NAME);
        String name
                = (property == null) ? null : PropertyUtils.toString(property);
        if (name == null || name.isEmpty()) {
            name = "nameless #" + (index + 1);
        }
        this.materialName = name;

        if (verboseLogging) {
            System.out.println();
            System.out.println("Creating a builder for " + MyString.quote(name)
                    + " material with the following properties:");
            for (Map.Entry<String, AIMaterialProperty> entry
                    : propMap.entrySet()) {
                String materialKey = entry.getKey();
                property = entry.getValue();
                String quotedKey = MyString.quote(materialKey);
                String describeValue = PropertyUtils.describe(property);
                System.out.printf(" %s with %s%n", quotedKey, describeValue);
            }
        }
        /*
         * Use the Assimp shading model to determine which
         * material definitions to use:
         */
        property = propMap.remove(Assimp.AI_MATKEY_SHADING_MODEL);
        int shadingModel = (property == null) ? Assimp.aiShadingMode_Phong
                : PropertyUtils.toInteger(property);
        property = propMap.remove("$mat.gltf.unlit"); // deprecated in Assimp
        if (property != null) {
            shadingModel = Assimp.aiShadingMode_Unlit;
        }
        switch (shadingModel) {
            case Assimp.aiShadingMode_Blinn:
            case Assimp.aiShadingMode_Gouraud:
            case Assimp.aiShadingMode_Phong:
                this.matDefs = Materials.LIGHTING;
                break;

            case Assimp.aiShadingMode_PBR_BRDF:
                this.matDefs = Materials.PBR;
                break;

            case Assimp.aiShadingMode_Unlit:
                this.matDefs = Materials.UNSHADED;
                break;

            default:
                throw new IOException(
                        "Unexpected shading model:  " + shadingModel);
        }
        if (verboseLogging) {
            System.out.println("Using " + matDefs + " material definitions.");
        }
        this.isPbr = matDefs.equals(Materials.PBR);
        this.isPhong = matDefs.equals(Materials.LIGHTING);
        this.isUnshaded = matDefs.equals(Materials.UNSHADED);
        assert isPbr || isPhong || isUnshaded : shadingModel;

        property = propMap.remove(Assimp.AI_MATKEY_GLTF_ALPHAMODE);
        String alphaMode = (property == null)
                ? "OPAQUE" : PropertyUtils.toString(property);
        if (alphaMode == null || alphaMode.isEmpty()) {
            alphaMode = "OPAQUE";
        }
        this.gltfAlphaMode = alphaMode;

        // Determine which tool-specific effects (if any) are enabled:
        boolean enabled;
        property = propMap.remove("$mat.blend.mirror.use");
        enabled = (property != null && PropertyUtils.toBoolean(property));
        isSfxEnabled.put("$mat.blend.mirror.", enabled);

        property = propMap.remove("$mat.blend.transparency.use");
        enabled = (property != null && PropertyUtils.toBoolean(property));
        isSfxEnabled.put("$mat.blend.transparency.", enabled);

        property = propMap.remove("$raw.Maya|coat");
        enabled = (property != null && PropertyUtils.toFloat(property) != 0f);
        isSfxEnabled.put("$raw.Maya|coat", enabled);

        property = propMap.remove("$raw.Maya|sheen");
        enabled = (property != null && PropertyUtils.toFloat(property) != 0f);
        isSfxEnabled.put("$raw.Maya|sheen", enabled);

        property = propMap.remove("$raw.Maya|subsurface");
        enabled = (property != null && PropertyUtils.toFloat(property) != 0f);
        isSfxEnabled.put("$raw.Maya|subsurface", enabled);

        property = propMap.remove("$raw.Maya|transmission");
        enabled = (property != null && PropertyUtils.toFloat(property) != 0f);
        isSfxEnabled.put("$raw.Maya|transmission", enabled);
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Return a JMonkeyEngine material that approximates the original
     * {@code AIMaterial}.
     *
     * @param jmeMesh the Mesh to which the material will be applied (not null)
     * @return a new instance (not null)
     */
    Material createJmeMaterial(Mesh jmeMesh) throws IOException {
        if (verboseLogging) {
            System.out.println();
            System.out.println(
                    "Building " + MyString.quote(materialName) + " material.");
        }

        Material result = new Material(assetManager, matDefs);
        this.jmeMaterial = result;
        result.setName(materialName);

        // Override some default parameters:
        if (isPhong) {
            result.setBoolean("UseMaterialColors", true);
            result.setColor("Ambient", new ColorRGBA(0.2f, 0.2f, 0.2f, 1f));
            result.setColor("Diffuse", new ColorRGBA(1f, 1f, 1f, 1f));
            result.setColor("Specular", new ColorRGBA(0f, 0f, 0f, 1f));
        } else if (isPbr) {
            result.clearParam("AlphaDiscardThreshold");
            result.setFloat("EmissiveIntensity", 1f);
        }

        // Use the remaining properties to tune the result:
        Map<String, AIMaterialProperty> map2 = new TreeMap<>();
        for (Map.Entry<String, AIMaterialProperty> entry : propMap.entrySet()) {
            String materialKey = entry.getKey();
            //System.out.println("materialKey: " + materialKey);
            AIMaterialProperty property = entry.getValue();
            boolean defer;
            if (materialKey.startsWith("$tex.")) {
                defer = applyTex(property);
            } else {
                defer = apply(property);
            }
            if (defer) { // property deferred to the next pass
                map2.put(materialKey, property);
            }
        }
        for (Map.Entry<String, AIMaterialProperty> entry : map2.entrySet()) {
            AIMaterialProperty property = entry.getValue();
            apply2(property);
        }

        if (jmeMesh.getBuffer(VertexBuffer.Type.Color) != null) {
            result.setBoolean("UseVertexColor", true);
            if (isPbr) {
                result.setFloat("Metallic", 0f);
            }
        }

        if (uvSourceType != null || uvTransform != null) {
            modifyTextureCoordinates(jmeMesh);
        }

        // Delete any unused U-V channels:
        for (int channelI = 1; channelI < 8; ++channelI) {
            VertexBuffer.Type vbType = ConversionUtils.uvType(channelI);
            jmeMesh.clearBuffer(vbType);
        }

        return result;
    }
    // *************************************************************************
    // private methods

    /**
     * Apply the specified Assimp material key and property to the specified
     * JMonkeyEngine material during the first pass over the properties.
     *
     * @param property the Assimp material property to apply (not null,
     * unaffected, key does not begin with "$tex.")
     * @return true to defer the property to the next pass, otherwise false
     */
    private boolean apply(AIMaterialProperty property) throws IOException {
        boolean result = false; // don't defer to the next pass
        ColorRGBA color;
        float floatValue;
        RenderState ars = jmeMaterial.getAdditionalRenderState();

        String materialKey = property.mKey().dataString();
        switch (materialKey) {
            case Assimp.AI_MATKEY_COLOR_AMBIENT: // "$clr.ambient"
                color = PropertyUtils.toColor(property);
                jmeMaterial.setColor("Ambient", color);
                break;

            case Assimp.AI_MATKEY_BASE_COLOR: // "$clr.base"
            case Assimp.AI_MATKEY_COLOR_DIFFUSE: // "$clr.diffuse"
            case "$mat.blend.diffuse.color":
                color = PropertyUtils.toColor(property);
                if (isPbr) {
                    jmeMaterial.setColor("BaseColor", color);
                } else if (isUnshaded) {
                    jmeMaterial.setColor("Color", color);
                } else {
                    jmeMaterial.setColor("Diffuse", color);
                }
                break;

            case Assimp.AI_MATKEY_COLOR_EMISSIVE: // "$clr.emissive"
                color = PropertyUtils.toColor(property);
                if (isPbr) {
                    jmeMaterial.setColor("Emissive", color);
                } else {
                    jmeMaterial.setColor("GlowColor", color);
                }
                break;

            case Assimp.AI_MATKEY_COLOR_REFLECTIVE: // "$clr.reflective"
                ignoreColor(materialKey, property, ColorRGBA.Black);
                break;

            case Assimp.AI_MATKEY_COLOR_SPECULAR: // "$clr.specular"
            case "$mat.blend.specular.color":
                color = PropertyUtils.toColor(property);
                jmeMaterial.setColor("Specular", color);
                break;

            case Assimp.AI_MATKEY_COLOR_TRANSPARENT: // "$clr.transparent"
                ignoreColor(materialKey, property, ColorRGBA.Black);
                break;

            case Assimp.AI_MATKEY_ANISOTROPY_FACTOR: // "$mat.anisotropyFactor"
                ignoreFloat(materialKey, property, 0f);
                break;

            case "$mat.blend.diffuse.intensity":
                result = true; // defer to the next pass
                break;

            case "$mat.blend.diffuse.ramp":
            case "$mat.blend.diffuse.shader":
                ignoreInteger(materialKey, property, 0);
                break;

            case "$mat.blend.specular.intensity":
            case Assimp.AI_MATKEY_SPECULAR_FACTOR: // "$mat.specularFactor"
                result = true; // defer to the next pass
                break;

            case "$mat.blend.specular.ramp":
            case "$mat.blend.specular.shader":
                ignoreInteger(materialKey, property, 0);
                break;

            case "$mat.blend.transparency.alpha":
            case "$mat.blend.transparency.falloff":
                ignoreFloat(materialKey, property, 1f);
                break;
            case "$mat.blend.transparency.fresnel":
                ignoreFloat(materialKey, property, 0f);
                break;
            case "$mat.blend.transparency.ior":
                ignoreFloat(materialKey, property, 1f);
                break;
            case "$mat.blend.transparency.limit":
                ignoreFloat(materialKey, property, 0f);
                break;
            case "$mat.blend.transparency.specular":
                ignoreFloat(materialKey, property, 1f);
                break;

            case Assimp.AI_MATKEY_CLEARCOAT_FACTOR: // "$mat.clearcoat.factor"
                ignoreFloat(materialKey, property, 1f);
                break;

            case Assimp.AI_MATKEY_EMISSIVE_INTENSITY:
                // "$mat.emissiveIntensity"
                floatValue = PropertyUtils.toFloat(property);
                jmeMaterial.setFloat("EmissiveIntensity", floatValue);
                break;

            case Assimp.AI_MATKEY_GLOSSINESS_FACTOR: // "$mat.glossinessFactor"
                if (isPbr) {
                    floatValue = PropertyUtils.toFloat(property);
                    jmeMaterial.setFloat("Glossiness", floatValue);
                    jmeMaterial.setBoolean("UseSpecGloss", true);
                }
                break;

            case Assimp.AI_MATKEY_GLTF_ALPHACUTOFF: // "$mat.gltf.alphaCutoff"
                if (gltfAlphaMode.equals("MASK")) {
                    floatValue = PropertyUtils.toFloat(property);
                    jmeMaterial.setFloat("AlphaDiscardThreshold", floatValue);
                } else if (gltfAlphaMode.equals("BLEND")) {
                    ars.setBlendMode(RenderState.BlendMode.Alpha);
                }
                break;

            case Assimp.AI_MATKEY_OBJ_ILLUM: // "$mat.illum"
                // always ignore
                break;

            case Assimp.AI_MATKEY_METALLIC_FACTOR: // "$mat.metallicFactor"
                if (isPbr) {
                    floatValue = PropertyUtils.toFloat(property);
                    jmeMaterial.setFloat("Metallic", floatValue);
                } else {
                    ignoreFloat(materialKey, property, 0f);
                }
                break;

            case Assimp.AI_MATKEY_OPACITY: // "$mat.opacity"
            case Assimp.AI_MATKEY_REFLECTIVITY: // "$mat.reflectivity"
            case Assimp.AI_MATKEY_REFRACTI: // "$mat.refracti"
                ignoreFloat(materialKey, property, 1f);
                break;

            case Assimp.AI_MATKEY_ROUGHNESS_FACTOR: // "$mat.roughnessFactor"
                if (isPbr) {
                    floatValue = PropertyUtils.toFloat(property);
                    jmeMaterial.setFloat("Roughness", floatValue);
                } else {
                    ignoreFloat(materialKey, property, 1f);
                }
                break;

            case Assimp.AI_MATKEY_SHININESS: // "$mat.shininess"
                if (isUnshaded) {
                    ignoreFloat(materialKey, property, 0f);
                } else if (isPhong) {
                    floatValue = PropertyUtils.toFloat(property);
                    jmeMaterial.setFloat("Shininess", floatValue);
                }
                break;

            case Assimp.AI_MATKEY_TRANSMISSION_FACTOR:
                // "$mat.transmission.factor"
                ignoreFloat(materialKey, property, 0f);
                break;

            case Assimp.AI_MATKEY_TWOSIDED: // "$mat.twosided"
                if (PropertyUtils.toBoolean(property)) {
                    ars.setFaceCullMode(RenderState.FaceCullMode.Off);
                } else {
                    ars.setFaceCullMode(RenderState.FaceCullMode.Back);
                }
                break;

            case Assimp.AI_MATKEY_VOLUME_ATTENUATION_DISTANCE:
                // "$mat.volume.attenuationDistance"
                ignoreFloat(materialKey, property, Float.POSITIVE_INFINITY);
                break;

            case Assimp.AI_MATKEY_ENABLE_WIREFRAME: // "$mat.wireframe"
                boolean booleanValue = PropertyUtils.toBoolean(property);
                ars.setWireframe(booleanValue);
                break;

            default:
                // Ignore properties associated with disabled effects:
                boolean forDisabledEffect = isForDisabledEffect(materialKey);
                if (!forDisabledEffect) {
                    String quotedKey = MyString.quote(materialKey);
                    String describeValue = PropertyUtils.describe(property);
                    System.err.printf("Ignoring unexpected "
                            + "(non-texture) material key %s with %s%n",
                            quotedKey, describeValue);
                }
        }

        return result;
    }

    /**
     * Apply the specified Assimp material key and property to the specified
     * JMonkeyEngine material during the first pass over the properties.
     *
     * @param property the Assimp material property to apply (not null,
     * unaffected, key begins with "$tex.")
     * @return true to defer the property to the next pass, otherwise false
     */
    private boolean applyTex(AIMaterialProperty property) throws IOException {
        boolean result = false; // don't defer to the next pass
        int integerValue;
        String suffix = PropertyUtils.suffixString(property);
        Sampler sampler = (suffix == null) ? null : samplerMap.get(suffix);

        String materialKey = property.mKey().dataString();
        switch (materialKey) {
            case Assimp._AI_MATKEY_TEXBLEND_BASE: // "$tex.blend"
                ignoreFloat(materialKey, property, 1f);
                break;

            case Assimp._AI_MATKEY_TEXTURE_BASE: // "$tex.file"
                result = true; // defer to the next pass
                break;

            case "$tex.file.strength":
                if (isPbr) {
                    float strength = PropertyUtils.toFloat(property);
                    jmeMaterial.setFloat("AoStrength", strength);
                } else {
                    ignoreFloat(materialKey, property, 1f);
                }
                break;

            case Assimp._AI_MATKEY_MAPPINGMODE_U_BASE: // "$tex.mapmodeu"
                integerValue = PropertyUtils.toInteger(property);
                sampler.setWrapS(integerValue);
                break;

            case Assimp._AI_MATKEY_MAPPINGMODE_V_BASE: // "$tex.mapmodev"
                integerValue = PropertyUtils.toInteger(property);
                sampler.setWrapT(integerValue);
                break;

            case Assimp._AI_MATKEY_GLTF_MAPPINGFILTER_MAG_BASE:
                // "$tex.mappingfiltermag"
                integerValue = PropertyUtils.toInteger(property);
                sampler.setMagFilter(integerValue);
                break;

            case Assimp._AI_MATKEY_GLTF_MAPPINGFILTER_MIN_BASE:
                // "$tex.mappingfiltermin"
                integerValue = PropertyUtils.toInteger(property);
                sampler.setMinFilter(integerValue);
                break;

            case Assimp._AI_MATKEY_GLTF_MAPPINGID_BASE: // "$tex.mappingid"
                ignoreString(materialKey, property, "samplers[0]");
                break;

            case Assimp._AI_MATKEY_GLTF_MAPPINGNAME_BASE: // "$tex.mappingname"
                ignoreString(materialKey, property, "");
                break;

            case Assimp._AI_MATKEY_GLTF_SCALE_BASE: // "$tex.scale"
                ignoreFloat(materialKey, property, 1f);
                break;

            case Assimp._AI_MATKEY_UVTRANSFORM_BASE: // "$tex.uvtrafo"
                Matrix3f trafo = PropertyUtils.toUvTransform(property);
                if (uvTransform == null) {
                    this.uvTransform = trafo;
                } else if (!trafo.equals(uvTransform)) {
                    String qName = MyString.quote(materialName);
                    logger.log(Level.WARNING,
                            "{0} material uses multiple UV transforms.", qName);
                }
                break;

            case Assimp._AI_MATKEY_UVWSRC_BASE: // "$tex.uvwsrc"
                integerValue = PropertyUtils.toInteger(property);
                VertexBuffer.Type vbType = ConversionUtils.uvType(integerValue);
                if (uvSourceType == null) {
                    this.uvSourceType = vbType;
                } else if (vbType != uvSourceType) {
                    String qName = MyString.quote(materialName);
                    logger.log(Level.WARNING,
                            "{0} material uses multiple UV channels.", qName);
                }
                break;

            default:
                String quotedKey = MyString.quote(materialKey);
                String describeValue = PropertyUtils.describe(property);
                System.err.printf(
                        "Ignoring unexpected material key %s with %s%n",
                        quotedKey, describeValue);
        }

        return result;
    }

    /**
     * Apply the specified Assimp material property to the specified
     * JMonkeyEngine material during the 2nd pass over the properties.
     *
     * @param property the the Assimp material property (not null, unaffected)
     */
    private void apply2(AIMaterialProperty property) throws IOException {
        ColorRGBA color;
        float intensity;

        String materialKey = property.mKey().dataString();
        switch (materialKey) {
            case "$mat.blend.diffuse.intensity":
                if (isPbr) {
                    color = jmeMaterial.getParamValue("BaseColor"); // alias
                } else {
                    color = jmeMaterial.getParamValue("Diffuse"); // alias
                }
                intensity = PropertyUtils.toFloat(property);
                color.multLocal(intensity);
                break;

            case "$mat.blend.specular.intensity":
            case Assimp.AI_MATKEY_SPECULAR_FACTOR: // "$mat.specularFactor"
                color = jmeMaterial.getParamValue("Specular"); // alias
                intensity = PropertyUtils.toFloat(property);
                color.multLocal(intensity);
                break;

            case Assimp._AI_MATKEY_TEXTURE_BASE: // "$tex.file"
                slotTexture(property);
                break;

            default:
                String quotedKey = MyString.quote(materialKey);
                System.err.printf(
                        "Ignoring unexpected material key %s in 2nd pass.%n",
                        quotedKey);
        }
    }

    /**
     * Convert an AIMaterialProperty to a JMonkeyEngine color and log a warning
     * if it doesn't match the specified value.
     *
     * @param materialKey the material key that has the property
     * @param property the property to convert (not null, unaffected)
     * @param expected the expected value (not null, unaffected)
     */
    private static void ignoreColor(String materialKey,
            AIMaterialProperty property, ColorRGBA expected)
            throws IOException {
        ColorRGBA actual = PropertyUtils.toColor(property);
        if (!actual.equals(expected)) {
            String quotedKey = MyString.quote(materialKey);
            logger.log(Level.WARNING,
                    "Unexpected color {0} for key {1} (expected {2})",
                    new Object[]{actual, quotedKey, expected});
        }
    }

    /**
     * Convert an AIMaterialProperty to a {@code float} and log a warning if it
     * doesn't match the specified value.
     *
     * @param materialKey the material key that has the property
     * @param property the property to convert (not null, unaffected)
     * @param expected the expected value (not null, unaffected)
     */
    private static void ignoreFloat(
            String materialKey, AIMaterialProperty property, float expected)
            throws IOException {
        float actual = PropertyUtils.toFloat(property);
        if (actual != expected) {
            String quotedKey = MyString.quote(materialKey);
            logger.log(Level.WARNING,
                    "Unexpected value {0} for key {1} (expected {2})",
                    new Object[]{actual, quotedKey, expected});
        }
    }

    /**
     * Convert an AIMaterialProperty to an {@code int} and log a warning if it
     * doesn't match the specified value.
     *
     * @param materialKey the material key that has the property
     * @param property the property to convert (not null, unaffected)
     * @param expected the expected value (not null, unaffected)
     */
    private static void ignoreInteger(
            String materialKey, AIMaterialProperty property, int expected)
            throws IOException {
        int actual = PropertyUtils.toInteger(property);
        if (actual != expected) {
            String quotedKey = MyString.quote(materialKey);
            logger.log(Level.WARNING,
                    "Unexpected value {0} for key {1} (expected {2})",
                    new Object[]{actual, quotedKey, expected});
        }
    }

    /**
     * Convert an AIMaterialProperty to a Java {@code String} and log a warning
     * if it doesn't match the specified value.
     *
     * @param materialKey the material key that has the property
     * @param property the property to convert (not null, unaffected)
     * @param expected the expected value (not null, unaffected)
     */
    private static void ignoreString(
            String materialKey, AIMaterialProperty property, String expected)
            throws IOException {
        String actual = PropertyUtils.toString(property);
        if (!actual.equals(expected)) {
            logger.log(Level.WARNING,
                    "Unexpected value {0} for material key {1} (expected {2})",
                    new Object[]{
                        MyString.quote(actual),
                        MyString.quote(materialKey),
                        MyString.quote(expected)
                    });
        }
    }

    /**
     * Test whether the specified material key is associated with a disabled
     * tool-specific effect.
     *
     * @param materialKey (not null)
     * @return true if associated with an unused effect, otherwise false
     */
    private boolean isForDisabledEffect(String materialKey) {
        for (Map.Entry<String, Boolean> entry : isSfxEnabled.entrySet()) {
            String effectName = entry.getKey();
            if (materialKey.startsWith(effectName)) {
                boolean isEnabled = entry.getValue();
                return !isEnabled;
            }
        }

        return false;
    }

    /**
     * Copy and/or transform the texture coordinates in the specified Mesh.
     *
     * @param jmeMesh the Mesh to modify (not null)
     */
    private void modifyTextureCoordinates(Mesh jmeMesh) {
        VertexBuffer vbDestination
                = jmeMesh.getBuffer(VertexBuffer.Type.TexCoord);
        assert vbDestination.getNumComponents() == 2;
        FloatBuffer destination = (FloatBuffer) vbDestination.getData();

        FloatBuffer source;
        if (uvSourceType != null) {
            VertexBuffer vbSource = jmeMesh.getBuffer(uvSourceType);
            assert vbSource.getNumComponents() == 2;
            source = (FloatBuffer) vbSource.getData();
        } else {
            source = destination;
        }

        Vector3f tmpVector = new Vector3f();

        int vertexCount = jmeMesh.getVertexCount();
        assert vertexCount * 2 == source.capacity();
        assert vertexCount * 2 == destination.capacity();
        for (int i = 0; i < vertexCount; ++i) {
            float u = source.get(2 * i);
            float v = source.get(2 * i + 1);
            tmpVector.set(u, v, 1f);

            if (uvTransform != null) {
                // TODO why doesn't TextureTransformMultiTest work?
                uvTransform.mult(tmpVector, tmpVector);
            }
            destination.put(2 * i, tmpVector.x);
            destination.put(2 * i + 1, tmpVector.y);
        }
    }

    /**
     * Generate a texture from the specified Assimp material property and slot
     * it into the current JMonkeyEngine material.
     *
     * @param property identifies the texture image, which might be embedded
     * @throws IOException if a texture cannot be generated or no parameter of
     * the current material is suitable
     */
    private void slotTexture(AIMaterialProperty property) throws IOException {
        int textureIndex = property.mIndex();
        if (textureIndex != 0) {
            String string = PropertyUtils.toString(property);
            String qString = MyString.quote(string);
            String qName = MyString.quote(materialName);
            logger.log(Level.WARNING,
                    "Skipped texture {0} in {1} with index={2}.",
                    new Object[]{qString, qName, textureIndex});
            return;
        }

        String matParamName = null; // name of the material parameter to set
        int semanticType = property.mSemantic();
        switch (semanticType) {
            case Assimp.aiTextureType_BASE_COLOR:
                if (isPbr) {
                    matParamName = "BaseColorMap";
                } else if (isUnshaded) {
                    matParamName = "ColorMap";
                }
                break;

            case Assimp.aiTextureType_DIFFUSE:
                if (isPhong) {
                    matParamName = "DiffuseMap";
                } else if (isPbr) {
                    matParamName = "BaseColorMap";
                } else if (isUnshaded) {
                    matParamName = "ColorMap";
                }
                break;

            case Assimp.aiTextureType_DIFFUSE_ROUGHNESS:
                if (isPbr) {
                    matParamName = "RoughnessMap";
                }
                break;

            case Assimp.aiTextureType_EMISSIVE:
                if (isPbr) {
                    matParamName = "EmissiveMap";
                } else {
                    matParamName = "GlowMap";
                }
                break;

            case Assimp.aiTextureType_HEIGHT:
                if (isPhong || isPbr) {
                    matParamName = "ParallaxMap";
                }
                break;

            case Assimp.aiTextureType_LIGHTMAP:
                matParamName = "LightMap";
                if (isPbr) {
                    jmeMaterial.setBoolean("LightMapAsAOMap", true);
                }
                break;

            case Assimp.aiTextureType_METALNESS:
                if (isPbr) {
                    matParamName = "MetallicRoughnessMap";
                }
                break;

            case Assimp.aiTextureType_NORMALS:
                if (isPhong || isPbr) {
                    matParamName = "NormalMap";
                    if (isPbr) { // assume an OpenGL-style normal map
                        jmeMaterial.setFloat("NormalType", 1f);
                    }
                }
                break;

            case Assimp.aiTextureType_SHININESS:
                if (isPbr) {
                    matParamName = "GlossinessMap";
                } else if (isPhong) {
                    matParamName = "SpecularMap";
                }
                break;

            case Assimp.aiTextureType_SPECULAR:
                if (isPbr) {
                    matParamName = "SpecularGlossinessMap";
                    jmeMaterial.setBoolean("UseSpecGloss", true);
                } else if (isPhong) {
                    matParamName = "SpecularMap";
                }
                break;

            case Assimp.aiTextureType_UNKNOWN: // TODO
                // Used in glTF2Importer.cpp for for metallic-roughness texture.
                break;

            case Assimp.aiTextureType_AMBIENT:
            case Assimp.aiTextureType_OPACITY:
            case Assimp.aiTextureType_DISPLACEMENT:
            case Assimp.aiTextureType_REFLECTION:
            case Assimp.aiTextureType_NORMAL_CAMERA:
            case Assimp.aiTextureType_EMISSION_COLOR:
            case Assimp.aiTextureType_AMBIENT_OCCLUSION:
            case Assimp.aiTextureType_SHEEN:
            case Assimp.aiTextureType_CLEARCOAT:
            case Assimp.aiTextureType_TRANSMISSION:
                break;

            default:
                throw new IOException("Unknown semantic type " + semanticType
                        + " for texture property.");
        }

        if (matParamName == null) {
            Level logLevel;
            if (semanticType == Assimp.aiTextureType_UNKNOWN) {
                logLevel = Level.INFO;
            } else {
                logLevel = Level.WARNING;
            }
            String string = PropertyUtils.toString(property);
            String qString = MyString.quote(string);
            String qName = MyString.quote(materialName);
            String semanticString = PropertyUtils.semanticString(property);
            logger.log(logLevel,
                    "Skipped texture {0} in {1} with {2} semantics.",
                    new Object[]{qString, qName, semanticString});
        } else {
            Texture texture = toTexture(property);
            jmeMaterial.setTexture(matParamName, texture);
        }
    }

    /**
     * Convert an AIMaterialProperty to a JMonkeyEngine texture.
     *
     * @param property the property to convert (not null, unaffected)
     * @return a new Texture instance (not null)
     */
    private Texture toTexture(AIMaterialProperty property) throws IOException {
        String suffix = PropertyUtils.suffixString(property);
        Sampler sampler = (suffix == null) ? null : samplerMap.get(suffix);

        String string = PropertyUtils.toString(property);
        Texture result;
        if (string.startsWith("*")) {
            String indexString = string.substring(1);
            int textureIndex = Integer.parseInt(indexString);
            result = embeddedTextures[textureIndex].clone();

        } else {
            //System.out.println("tex string=" + string);
            if (string.startsWith("1 1 ")) { // TODO what does this mean?
                logger.warning("texture asset path starts with 1 1");
                string = string.substring(4);
            } else if (string.startsWith("//")) { // TODO what does this mean?
                logger.warning("texture asset path starts with //");
                string = string.substring(2);
            } else if (string.startsWith("$//")) { // TODO what does this mean?
                string = string.substring(3);
            }

            // Attempt to load the texture using the AssetManager:
            String assetPath = assetFolder + string;
            TextureKey textureKey = new TextureKey(assetPath);
            textureKey.setFlipY(flipY);
            textureKey.setGenerateMips(true);
            try {
                result = assetManager.loadTexture(textureKey);
            } catch (AssetNotFoundException exception) {
                System.err.println(exception);
                Image image
                        = PlaceholderAssets.getPlaceholderImage(assetManager);
                result = new Texture2D(image);
                result.setKey(textureKey);
            }
        }
        sampler.applyTo(result);

        return result;
    }
}
