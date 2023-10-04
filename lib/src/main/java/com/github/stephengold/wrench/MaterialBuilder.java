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
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.util.PlaceholderAssets;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import jme3utilities.MyString;
import org.lwjgl.PointerBuffer;
import org.lwjgl.assimp.AIMaterial;
import org.lwjgl.assimp.AIMaterialProperty;
import org.lwjgl.assimp.AIString;
import org.lwjgl.assimp.Assimp;
import org.lwjgl.system.MemoryUtil;

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
     * true if the material uses Blender "mirror", otherwise false
     */
    private boolean usesMirror;
    /**
     * true if the material uses Blender transparency, otherwise false
     */
    private boolean usesTransparency;
    /**
     * maps Assimp material keys to material properties
     */
    private Map<String, AIMaterialProperty> propMap = new TreeMap<>();
    /**
     * properties for texture sampling
     */
    private Map<String, Sampler> samplerMap = new TreeMap<>();
    /**
     * JMonkeyEngine material under construction
     */
    private Material jmeMaterial;
    /**
     * asset path of the folder from which the model/scene was loaded, for
     * loading textures
     */
    final private String assetFolder;
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
    // *************************************************************************
    // constructors

    /**
     * Instantiate a builder for the specified {@code AIMaterial}.
     *
     * @param aiMaterial the Assimp material to convert (not null, unaffected)
     * @param assetManager for loading textures (not null, alias created)
     * @param assetFolder the asset path of the folder from which the
     * model/scene was loaded (not null, alias created)
     * @param embeddedTextures the array of embedded textures (not null, alias
     * created)
     * @throws IOException if the Assimp material cannot be converted
     */
    MaterialBuilder(AIMaterial aiMaterial, AssetManager assetManager,
            String assetFolder, Texture[] embeddedTextures) throws IOException {
        assert assetManager != null;
        assert assetFolder != null;
        assert embeddedTextures != null;

        this.assetManager = assetManager;
        this.assetFolder = assetFolder;
        this.embeddedTextures = embeddedTextures;

        // Use the material properties to populate propMap and samplerMap:
        PointerBuffer ppProperties = aiMaterial.mProperties();
        int numProperties = aiMaterial.mNumProperties();
        for (int i = 0; i < numProperties; ++i) {
            long handle = ppProperties.get(i);
            AIMaterialProperty property = AIMaterialProperty.createSafe(handle);
            String materialKey = property.mKey().dataString();
            String suffix = toSuffix(property);
            if (suffix != null) {
                materialKey += suffix;
                Sampler sampler = samplerMap.get(suffix);
                if (sampler == null) {
                    sampler = new Sampler();
                    samplerMap.put(suffix, sampler);
                }
            }

            AIMaterialProperty oldProperty = propMap.put(materialKey, property);
            assert oldProperty == null : materialKey;
        }

        // Name the material:
        AIMaterialProperty property = propMap.remove(Assimp.AI_MATKEY_NAME);
        String name = (property == null) ? null : toString(property);
        if (name == null) {
            name = "nameless material";
        }
        this.materialName = name;

        // Determine the Assimp shading model:
        int shadingModel;
        property = propMap.remove(Assimp.AI_MATKEY_SHADING_MODEL);
        if (property == null) {
            shadingModel = Assimp.aiShadingMode_Phong;
        } else {
            shadingModel = toInteger(property);
        }

        // Determine whether mirror and/or transparency are used:
        property = propMap.remove("$mat.blend.mirror.use");
        if (property == null) {
            this.usesMirror = false;
        } else {
            this.usesMirror = toBoolean(property);
        }
        property = propMap.remove("$mat.blend.transparency.use");
        if (property == null) {
            this.usesTransparency = false;
        } else {
            this.usesTransparency = toBoolean(property);
        }

        // Determine which material definitions to use:
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
        //System.out.println("material defs = " + matDefs);
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Return a JMonkeyEngine material that approximates the original
     * {@code AIMaterial}.
     *
     * @return a new instance (not null)
     */
    Material createJmeMaterial() throws IOException {
        Material result = new Material(assetManager, matDefs);
        result.setName(materialName);

        if (matDefs.equals(Materials.LIGHTING)) {
            // Supply some default parameters:
            result.setBoolean("UseMaterialColors", true);
            result.setColor("Ambient", new ColorRGBA(0.2f, 0.2f, 0.2f, 1f));
            result.setColor("Diffuse", new ColorRGBA(1f, 1f, 1f, 1f));
            result.setColor("Specular", new ColorRGBA(0f, 0f, 0f, 1f));
            //result.setFloat("Shininess", 16f);
        }
        this.jmeMaterial = result;

        // Use the remaining properties to tune the result:
        Map<String, AIMaterialProperty> map2 = new TreeMap<>();
        for (Map.Entry<String, AIMaterialProperty> entry : propMap.entrySet()) {
            String materialKey = entry.getKey();
            //System.out.println("materialKey: " + materialKey);
            AIMaterialProperty property = entry.getValue();
            boolean defer = apply(property);
            if (defer) { // property deferred to the next pass
                map2.put(materialKey, property);
            }
        }
        for (Map.Entry<String, AIMaterialProperty> entry : map2.entrySet()) {
            AIMaterialProperty property = entry.getValue();
            apply2(property);
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
     * unaffected)
     * @return true to defer the property to the next pass, otherwise false
     */
    private boolean apply(AIMaterialProperty property) throws IOException {
        boolean result = false; // don't defer to the next pass
        ColorRGBA color;
        float floatValue;
        int integerValue;
        RenderState ars = jmeMaterial.getAdditionalRenderState();
        String defName = jmeMaterial.getMaterialDef().getAssetName();
        String string;
        String suffix = toSuffix(property);
        Sampler sampler = (suffix == null) ? null : samplerMap.get(suffix);

        String materialKey = property.mKey().dataString();
        switch (materialKey) {
            case Assimp.AI_MATKEY_ANISOTROPY_FACTOR: // "$mat.anisotropyFactor"
                ignoreFloat(materialKey, property, 0f);
                break;

            case Assimp.AI_MATKEY_COLOR_AMBIENT: // "$clr.ambient"
                color = toColor(property);
                jmeMaterial.setColor("Ambient", color);
                break;

            case Assimp.AI_MATKEY_BASE_COLOR: // "$clr.base"
            case Assimp.AI_MATKEY_COLOR_DIFFUSE: // "$clr.diffuse"
            case "$mat.blend.diffuse.color":
                color = toColor(property);
                if (defName.equals(Materials.PBR)) {
                    jmeMaterial.setColor("BaseColor", color);
                } else if (defName.equals(Materials.UNSHADED)) {
                    jmeMaterial.setColor("Color", color);
                } else {
                    jmeMaterial.setColor("Diffuse", color);
                }
                break;

            case "$mat.blend.diffuse.intensity":
                result = true; // defer to the next pass
                break;

            case "$mat.blend.diffuse.ramp":
            case "$mat.blend.diffuse.shader":
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

            case Assimp.AI_MATKEY_COLOR_EMISSIVE: // "$clr.emissive"
                color = toColor(property);
                if (defName.equals(Materials.PBR)) {
                    jmeMaterial.setColor("Emissive", color);
                } else {
                    jmeMaterial.setColor("GlowColor", color);
                }
                break;

            case Assimp.AI_MATKEY_COLOR_REFLECTIVE: // "$clr.reflective"
                ignoreColor(materialKey, property, ColorRGBA.White);
                break;

            case Assimp.AI_MATKEY_COLOR_SPECULAR: // "$clr.specular"
            case "$mat.blend.specular.color":
                color = toColor(property);
                jmeMaterial.setColor("Specular", color);
                break;

            case "$mat.blend.specular.intensity":
            case Assimp.AI_MATKEY_SPECULAR_FACTOR: // "$mat.specularFactor"
                result = true; // defer to the next pass
                break;

            case "$mat.blend.specular.ramp":
            case "$mat.blend.specular.shader":
                ignoreInteger(materialKey, property, 0);
                break;

            case Assimp.AI_MATKEY_CLEARCOAT_FACTOR: // "$mat.clearcoat.factor"
                ignoreFloat(materialKey, property, 1f);
                break;

            case Assimp.AI_MATKEY_COLOR_TRANSPARENT: // "$clr.transparent"
                ignoreColor(materialKey, property, ColorRGBA.White);
                break;

            case Assimp.AI_MATKEY_EMISSIVE_INTENSITY:
                // "$mat.emissiveIntensity"
                floatValue = toFloat(property);
                jmeMaterial.setFloat("EmissiveIntensity", floatValue);
                break;

            case Assimp.AI_MATKEY_GLTF_ALPHACUTOFF: // "$mat.gltf.alphaCutoff"
                floatValue = toFloat(property);
                jmeMaterial.setFloat("AlphaDiscardThreshold", floatValue);
                break;

            case Assimp.AI_MATKEY_GLTF_ALPHAMODE: // "$mat.gltf.alphaMode"
                ignoreString(materialKey, property, "OPAQUE");
                break;

            case "$tex.file.strength":
                ignoreFloat(materialKey, property, 1f);
                break;

            case Assimp._AI_MATKEY_GLTF_MAPPINGFILTER_MAG_BASE:
                // "$tex.mappingfiltermag"
                integerValue = toInteger(property);
                sampler.setMagFilter(integerValue);
                break;

            case Assimp._AI_MATKEY_GLTF_MAPPINGFILTER_MIN_BASE:
                // "$tex.mappingfiltermin"
                integerValue = toInteger(property);
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

            case Assimp._AI_MATKEY_MAPPINGMODE_U_BASE: // "$tex.mapmodeu"
                integerValue = toInteger(property);
                sampler.setWrapS(integerValue);
                break;

            case Assimp._AI_MATKEY_MAPPINGMODE_V_BASE: // "$tex.mapmodev"
                integerValue = toInteger(property);
                sampler.setWrapT(integerValue);
                break;

            case Assimp.AI_MATKEY_METALLIC_FACTOR: // "$mat.metallicFactor"
                if (defName.equals(Materials.PBR)) {
                    floatValue = toFloat(property);
                    jmeMaterial.setFloat("Metallic", floatValue);
                } else {
                    ignoreFloat(materialKey, property, 0f);
                }
                break;

            case Assimp.AI_MATKEY_OBJ_ILLUM: // "$mat.illum"
                // always ignore
                break;

            case Assimp.AI_MATKEY_OPACITY: // "$mat.opacity"
            case Assimp.AI_MATKEY_REFRACTI: // "$mat.refracti"
                ignoreFloat(materialKey, property, 1f);
                break;

            case Assimp.AI_MATKEY_ROUGHNESS_FACTOR: // "$mat.roughnessFactor"
                if (defName.equals(Materials.PBR)) {
                    floatValue = toFloat(property);
                    jmeMaterial.setFloat("Roughness", floatValue);
                } else {
                    ignoreFloat(materialKey, property, 1f);
                }
                break;

            case Assimp.AI_MATKEY_SHININESS: // "$mat.shininess"
                if (defName.equals(Materials.PBR)) {
                    floatValue = toFloat(property);
                    jmeMaterial.setFloat("Glossiness", floatValue);
                } else if (defName.equals(Materials.UNSHADED)) {
                    ignoreFloat(materialKey, property, 0f);
                } else {
                    floatValue = toFloat(property);
                    jmeMaterial.setFloat("Shininess", floatValue);
                }
                break;

            case Assimp._AI_MATKEY_TEXTURE_BASE: // "$tex.file"
                result = true; // defer to the next pass
                break;

            case Assimp.AI_MATKEY_TRANSMISSION_FACTOR:
                // "$mat.transmission.factor"
                ignoreFloat(materialKey, property, 0f);
                break;

            case Assimp.AI_MATKEY_TWOSIDED: // "$mat.twosided"
                if (toBoolean(property)) {
                    ars.setFaceCullMode(RenderState.FaceCullMode.Off);
                } else {
                    ars.setFaceCullMode(RenderState.FaceCullMode.Back);
                }
                break;

            case Assimp._AI_MATKEY_UVWSRC_BASE: // "$tex.uvwsrc"
                ignoreInteger(materialKey, property, 0);
                break;

            case Assimp.AI_MATKEY_VOLUME_ATTENUATION_DISTANCE:
                // "$mat.volume.attenuationDistance"
                ignoreFloat(materialKey, property, Float.POSITIVE_INFINITY);
                break;

            default:
                // Ignore Blender properties that won't be used:
                if (!usesMirror
                        && materialKey.startsWith("$mat.blend.mirror.")) {
                    break;
                } else if (!usesTransparency
                        && materialKey.startsWith("$mat.blend.transparency.")) {
                    break;
                }

                String quotedKey = MyString.quote(materialKey);
                int numBytes = property.mDataLength();
                String pluralBytes = (numBytes == 1) ? "" : "s";
                String typeString = typeString(property);
                System.err.printf("Ignoring unexpected material key %s. "
                        + "The property contains %d byte%s of %s data.%n",
                        quotedKey, numBytes, pluralBytes, typeString);
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
                if (matDefs.equals(Materials.PBR)) {
                    color = jmeMaterial.getParamValue("BaseColor"); // alias
                } else {
                    color = jmeMaterial.getParamValue("Diffuse"); // alias
                }
                intensity = toFloat(property);
                color.multLocal(intensity);
                break;

            case "$mat.blend.specular.intensity":
            case Assimp.AI_MATKEY_SPECULAR_FACTOR: // "$mat.specularFactor"
                color = jmeMaterial.getParamValue("Specular"); // alias
                intensity = toFloat(property);
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
        ColorRGBA actual = toColor(property);
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
        float actual = toFloat(property);
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
        int actual = toInteger(property);
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
        String actual = toString(property);
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
     * Generate a texture from the specified Assimp material property and slot
     * it into the current JMonkeyEngine material.
     *
     * @param property identifies the texture image, which might be embedded
     * @throws IOException if a texture cannot be generated or no parameter of
     * the current material is suitable
     */
    private void slotTexture(AIMaterialProperty property) throws IOException {
        String defName = jmeMaterial.getMaterialDef().getAssetName();
        int semanticType = property.mSemantic();

        int textureIndex = property.mIndex();
        if (textureIndex != 0) {
            String string = toString(property);
            String qString = MyString.quote(string);
            // TODO name the type
            logger.log(Level.WARNING,
                    "Skipped a texture {0} with semantic type {1} for defs={2}",
                    new Object[]{qString, semanticType, defName});
            return;
        }

        boolean lighting = defName.equals(Materials.LIGHTING);
        boolean pbr = defName.equals(Materials.PBR);
        boolean unshaded = defName.equals(Materials.UNSHADED);
        assert lighting || pbr || unshaded : defName;

        String matParamName = null; // name of the material parameter to set
        switch (semanticType) {
            case Assimp.aiTextureType_BASE_COLOR:
                if (pbr) {
                    matParamName = "BaseColorMap";
                } else if (unshaded) {
                    matParamName = "ColorMap";
                }
                break;

            case Assimp.aiTextureType_DIFFUSE:
                if (lighting) {
                    matParamName = "DiffuseMap";
                } else if (pbr) {
                    matParamName = "BaseColorMap";
                }
                break;

            case Assimp.aiTextureType_DIFFUSE_ROUGHNESS:
                if (pbr) {
                    matParamName = "RoughnessMap";
                }
                break;

            case Assimp.aiTextureType_EMISSIVE:
                if (pbr) {
                    matParamName = "EmissiveMap";
                }
                break;

            case Assimp.aiTextureType_HEIGHT:
                if (lighting || pbr) {
                    matParamName = "ParallaxMap";
                }
                break;

            case Assimp.aiTextureType_LIGHTMAP:
                if (pbr) {
                    matParamName = "LightMap";
                    jmeMaterial.setBoolean("LightMapAsAOMap", true);
                }
                break;

            case Assimp.aiTextureType_METALNESS:
                if (pbr) {
                    matParamName = "MetallicMap";
                }
                break;

            case Assimp.aiTextureType_NORMALS:
                if (lighting || pbr) {
                    matParamName = "NormalMap";
                }
                break;

            case Assimp.aiTextureType_SHININESS:
                if (lighting || pbr) {
                    matParamName = "GlossinessMap";
                }
                break;

            case Assimp.aiTextureType_SPECULAR:
                if (lighting || pbr) {
                    matParamName = "SpecularMap";
                }
                break;

            case Assimp.aiTextureType_UNKNOWN:
                if (pbr) {
                    matParamName = "MetallicRoughnessMap"; // TODO srsly?
                    jmeMaterial.setBoolean("AoPackedInMRMap", true);
                }
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
            String string = toString(property);
            String qString = MyString.quote(string);
            // TODO name the type
            logger.log(Level.WARNING,
                    "Skipped a texture {0} with semantic type {1} for defs={2}",
                    new Object[]{qString, semanticType, defName});
        } else {
            Texture texture = toTexture(property);
            jmeMaterial.setTexture(matParamName, texture);
        }
    }

    /**
     * Convert an AIMaterialProperty to a {@code boolean}.
     *
     * @param property the property to convert (not null, unaffected)
     * @return the converted value
     * @throws IOException if the property cannot be converted
     */
    private static boolean toBoolean(AIMaterialProperty property)
            throws IOException {
        int integer = toInteger(property);
        if (integer == 0) {
            return false;
        } else {
            return true;
        }
    }

    /**
     * Convert an AIMaterialProperty to a JMonkeyEngine color.
     *
     * @param property the property to convert (not null, unaffected)
     * @return a new instance
     * @throws IOException if the property cannot be converted
     */
    private static ColorRGBA toColor(AIMaterialProperty property)
            throws IOException {
        float alpha = 1f;
        float blue;
        float green;
        float red;
        ByteBuffer byteBuffer = property.mData();

        int propertyType = property.mType();
        switch (propertyType) {
            case Assimp.aiPTI_Double:
                DoubleBuffer doubleBuffer = byteBuffer.asDoubleBuffer();
                int numDoubles = doubleBuffer.capacity();
                if (numDoubles > 4) {
                    logger.log(Level.WARNING,
                            "Ignored extra doubles in color. numDoubles={0}",
                            numDoubles);
                }
                red = (float) doubleBuffer.get(0);
                green = (float) doubleBuffer.get(1);
                blue = (float) doubleBuffer.get(2);
                if (numDoubles == 4) {
                    alpha = (float) doubleBuffer.get(3);
                }
                break;

            case Assimp.aiPTI_Float:
                FloatBuffer floatBuffer = byteBuffer.asFloatBuffer();
                int numFloats = floatBuffer.capacity();
                if (numFloats > 4) {
                    logger.log(Level.WARNING,
                            "Ignored extra floats in color. numFloats={0}",
                            numFloats);
                }
                red = floatBuffer.get(0);
                green = floatBuffer.get(1);
                blue = floatBuffer.get(2);
                if (numFloats == 4) {
                    alpha = floatBuffer.get(3);
                }
                break;

            default:
                String typeString = typeString(property);
                throw new IOException(
                        "Unexpected property type:  " + typeString);
        }

        ColorRGBA result = new ColorRGBA(red, green, blue, alpha);
        return result;
    }

    /**
     * Convert an AIMaterialProperty to a single float.
     *
     * @param property the property to convert (not null, unaffected)
     * @return the converted value
     * @throws IOException if the property cannot be converted
     */
    private static float toFloat(AIMaterialProperty property)
            throws IOException {
        float result;
        ByteBuffer byteBuffer = property.mData();
        int propertyType = property.mType();
        switch (propertyType) {
            case Assimp.aiPTI_Double:
                DoubleBuffer doubleBuffer = byteBuffer.asDoubleBuffer();
                int numDoubles = doubleBuffer.capacity();
                if (numDoubles > 1) {
                    logger.log(Level.WARNING,
                            "Ignored extra doubles in property. numDoubles={0}",
                            numDoubles);
                }
                result = (float) doubleBuffer.get(0);
                break;

            case Assimp.aiPTI_Float:
                FloatBuffer floatBuffer = byteBuffer.asFloatBuffer();
                int numFloats = floatBuffer.capacity();
                if (numFloats > 1) {
                    logger.log(Level.WARNING,
                            "Ignored extra floats in property. numFloats={0}",
                            numFloats);
                }
                result = floatBuffer.get(0);
                break;

            default:
                String typeString = typeString(property);
                throw new IOException(
                        "Unexpected property type:  " + typeString);
        }

        return result;
    }

    /**
     * Convert an AIMaterialProperty to a single integer.
     *
     * @param property the property to convert (not null, unaffected)
     * @return the converted value
     * @throws IOException if the property cannot be converted
     */
    private static int toInteger(AIMaterialProperty property)
            throws IOException {
        int result;
        ByteBuffer byteBuffer = property.mData();
        int propertyType = property.mType();
        switch (propertyType) {
            case Assimp.aiPTI_Buffer:
            case Assimp.aiPTI_Integer:
                if (byteBuffer.capacity() == 1) {
                    result = byteBuffer.get(0);
                } else {
                    IntBuffer intBuffer = byteBuffer.asIntBuffer();
                    int numInts = intBuffer.capacity();
                    if (numInts > 1) {
                        logger.log(Level.WARNING,
                                "Skipped extra ints in property. numInts={0}",
                                new Object[]{numInts});
                    }
                    result = intBuffer.get(0);
                }
                break;

            default:
                String typeString = typeString(property);
                throw new IOException(
                        "Unexpected property type:  " + typeString);
        }

        return result;
    }

    /**
     * Convert an AIMaterialProperty to a Java string.
     *
     * @param property the property to convert (not null, unaffected)
     * @return the converted value (not null)
     */
    private static String toString(AIMaterialProperty property)
            throws IOException {
        String result;

        ByteBuffer byteBuffer = property.mData();
        long address = MemoryUtil.memAddressSafe(byteBuffer);

        int propertyType = property.mType();
        switch (propertyType) {
            case Assimp.aiPTI_Buffer:
                result = MemoryUtil.memASCII(address);
                break;

            case Assimp.aiPTI_String:
                AIString s = AIString.createSafe(address);
                result = s.dataString();
                break;

            default:
                String typeString = typeString(property);
                throw new IOException(
                        "Unexpected property type:  " + typeString);
        }

        return result;
    }

    /**
     * Encode the texture index and usage semantic of an AIMaterialProperty into
     * a Java string.
     *
     * @param property the property to encode (not null, unaffected)
     * @return a string, or null if {@code property} is not a texture property
     * @throws IOException if the argument has unexpected parameters
     */
    private static String toSuffix(AIMaterialProperty property)
            throws IOException {
        int textureIndex = property.mIndex();
        int semantic = property.mSemantic();

        String suffix;
        String materialKey = property.mKey().dataString();
        if (materialKey.startsWith("$tex.")) { // texture property
            suffix = semantic + " " + textureIndex;

        } else { // non-texture property - no suffix
            assert textureIndex == 0 : textureIndex;
            assert semantic == Assimp.aiTextureType_NONE : semantic;
            suffix = null;
        }

        return suffix;
    }

    /**
     * Convert an AIMaterialProperty to a JMonkeyEngine texture.
     *
     * @param property the property to convert (not null, unaffected)
     * @return a new Texture instance (not null)
     */
    private Texture toTexture(AIMaterialProperty property) throws IOException {
        String suffix = toSuffix(property);
        Sampler sampler = (suffix == null) ? null : samplerMap.get(suffix);

        String string = toString(property);
        Texture result;
        if (string.startsWith("*")) {
            String indexString = string.substring(1);
            int textureIndex = Integer.parseInt(indexString);
            result = embeddedTextures[textureIndex].clone();

        } else {
            //System.out.println("tex string=" + string);
            if (string.startsWith("1 1 ")) { // TODO what does this mean?
                string = string.substring(4);
            } else if (string.startsWith("//")) { // TODO what does this mean?
                string = string.substring(2);
            } else if (string.startsWith("$//")) { // TODO what does this mean?
                string = string.substring(3);
            }

            // Attempt to load the texture using the AssetManager:
            String assetPath = assetFolder + string;
            TextureKey textureKey = new TextureKey(assetPath);
            textureKey.setFlipY(true);
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

    /**
     * Convert the type information of a material property to a string of text.
     *
     * @param property the material property to analyze (not null, unaffected)
     * @return descriptive text (not null)
     */
    private static String typeString(AIMaterialProperty property) {
        String result;
        int info = property.mType();
        switch (info) {
            case Assimp.aiPTI_Buffer:
                result = "Buffer";
                break;
            case Assimp.aiPTI_Double:
                result = "Double";
                break;
            case Assimp.aiPTI_Float:
                result = "Float";
                break;
            case Assimp.aiPTI_Integer:
                result = "Integer";
                break;
            case Assimp.aiPTI_String:
                result = "String";
                break;
            default:
                result = "PTI_" + info;
                break;
        }
        return result;
    }
}
