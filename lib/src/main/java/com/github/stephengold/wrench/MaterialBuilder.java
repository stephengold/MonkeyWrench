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
import com.jme3.material.MatParam;
import com.jme3.material.Material;
import com.jme3.material.MaterialDef;
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
import com.jme3.util.BufferUtils;
import com.jme3.util.PlaceholderAssets;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import jme3utilities.MyString;
import jme3utilities.math.MyColor;
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
     * search path for texture assets
     */
    final private static List<String> textureSearchPath = new ArrayList<>(6);

    static {
        textureSearchPath.add("%s%s%s"); // relative to the main asset
        textureSearchPath.add("textures/%2$s%3$s"); // fixed asset folder
        textureSearchPath.add("%sTextures/%s%s");
        textureSearchPath.add("Textures/%2$s%3$s"); // fixed asset folder
        textureSearchPath.add("textures/%2$s.jpeg");
        textureSearchPath.add("textures/%2$s%3$s.png");
    }

    /**
     * message logger for this class
     */
    final private static Logger logger
            = Logger.getLogger(MaterialBuilder.class.getName());
    // *************************************************************************
    // fields

    /**
     * for loading material definitions and non-embedded textures (not null)
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
     * base color to be applied after all properties, or null if unspecified
     */
    private ColorRGBA baseColor;
    /**
     * diffuse color to be applied after all properties, or null if unspecified
     */
    private ColorRGBA diffuseColor;
    /**
     * weight to be applied to the base color, or null if unspecified
     */
    private Float baseWeight;
    /**
     * map from Assimp material keys to material properties
     * <p>
     * To avoid duplicate keys, the keys of texture material properties are
     * suffixed with the texture index and usage semantic.
     */
    final private Map<String, AIMaterialProperty> propMap = new TreeMap<>();
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
     * asset path to the folder from which the main asset was loaded, for
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
     * @param index the index of the material in the imported asset (&ge;0)
     * @param assetManager for loading textures (not null, alias created)
     * @param assetFolder the asset path to the folder from which the main asset
     * was loaded (not null, alias created)
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
            if (suffix != null) { // texture material property:
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
            dumpPropMap();
        }

        this.matDefs = selectMaterialDefinitions();
        this.isPbr = matDefs.equals(Materials.PBR);
        this.isPhong = matDefs.equals(Materials.LIGHTING);
        this.isUnshaded = matDefs.equals(Materials.UNSHADED);
        assert isPbr || isPhong || isUnshaded : matDefs;

        property = propMap.remove(Assimp.AI_MATKEY_GLTF_ALPHAMODE);
        String alphaMode = (property == null)
                ? "OPAQUE" : PropertyUtils.toString(property);
        if (alphaMode == null || alphaMode.isEmpty()) {
            alphaMode = "OPAQUE";
        }
        this.gltfAlphaMode = alphaMode;

        initializeEffects();
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Return a JMonkeyEngine material that approximates the original
     * {@code AIMaterial}.
     *
     * @param jmeMesh the Mesh to which the material will be applied (not null)
     * @param meshName the (Assimp) name of the target mesh
     * @return a new instance (not null)
     */
    Material createJmeMaterial(Mesh jmeMesh, String meshName)
            throws IOException {
        if (verboseLogging) {
            System.out.println();
            System.out.printf("Building %s material for the %s mesh...%n",
                    MyString.quote(materialName), MyString.quote(meshName));
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
            String suffix = PropertyUtils.suffixString(property);
            if (suffix != null) { // texture material property
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

        applyBaseDiffuseColors();

        // Use vertex colors if they're available:
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
     * Apply the specified non-texture material property to the current
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
            case "$raw.Ambient":
                if (isPhong) {
                    color = PropertyUtils.toColor(property);
                    jmeMaterial.setColor("Ambient", color);
                }
                break;

            case Assimp.AI_MATKEY_BASE_COLOR: // "$clr.base"
            case "$raw.3dsMax|Parameters|base_color":
            case "$raw.Maya|baseColor":
                this.baseColor = PropertyUtils.toColor(property);
                break;

            case Assimp.AI_MATKEY_COLOR_DIFFUSE: // "$clr.diffuse"
            case "$mat.blend.diffuse.color":
            case "$raw.Diffuse":
                this.diffuseColor = PropertyUtils.toColor(property);
                break;

            case Assimp.AI_MATKEY_COLOR_EMISSIVE: // "$clr.emissive"
            case "$raw.3dsMax|Parameters|emit_color":
            case "$raw.Emissive":
            case "$raw.Maya|emissionColor":
                color = PropertyUtils.toColor(property);
                if (isPbr) {
                    jmeMaterial.setColor("Emissive", color);
                } else {
                    jmeMaterial.setColor("GlowColor", color);
                }
                break;

            case Assimp.AI_MATKEY_COLOR_REFLECTIVE: // "$clr.reflective"
            case "$raw.3dsMax|Parameters|refl_color":
                ignoreColor(materialKey, property, ColorRGBA.Black);
                break;

            case Assimp.AI_MATKEY_COLOR_SPECULAR: // "$clr.specular"
            case "$mat.blend.specular.color":
            case "$raw.Maya|specularColor":
            case "$raw.Specular":
                color = PropertyUtils.toColor(property);
                jmeMaterial.setColor("Specular", color);
                break;

            case Assimp.AI_MATKEY_COLOR_TRANSPARENT: // "$clr.transparent"
                // ignore
                break;

            case Assimp.AI_MATKEY_ANISOTROPY_FACTOR: // "$mat.anisotropyFactor"
            case "$raw.3dsMax|Parameters|anisotropy":
                ignoreFloat(materialKey, property, 0f);
                break;

            case "$mat.blend.diffuse.intensity":
                ignoreFloat(materialKey, property, 1f);
                break;

            case "$mat.blend.diffuse.ramp":
            case "$mat.blend.diffuse.shader":
            case "$mat.blend.specular.hardness":
                ignoreInteger(materialKey, property, 0);
                break;

            case "$mat.blend.specular.intensity":
            case Assimp.AI_MATKEY_SPECULAR_FACTOR: // "$mat.specularFactor"
            case "$raw.Maya|specular":
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

            case Assimp.AI_MATKEY_BUMPSCALING: // "$mat.bumpscaling"
            case Assimp.AI_MATKEY_CLEARCOAT_FACTOR: // "$mat.clearcoat.factor"
            case "$mat.displacementscaling":
                ignoreFloat(materialKey, property, 1f);
                break;

            case Assimp.AI_MATKEY_EMISSIVE_INTENSITY:
            // "$mat.emissiveIntensity"
            case "$raw.3dsMax|Parameters|emission":
            case "$raw.Maya|emission":
                if (isPbr) {
                    floatValue = PropertyUtils.toFloat(property);
                    jmeMaterial.setFloat("EmissiveIntensity", floatValue);
                }
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
            case "$raw.3dsMax|Parameters|metalness":
            case "$raw.Maya|metalness":
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
            case "$raw.3dsMax|Parameters|reflectivity":
            case "$raw.Reflectivity":
                ignoreFloat(materialKey, property, 1f);
                break;

            case Assimp.AI_MATKEY_ROUGHNESS_FACTOR: // "$mat.roughnessFactor"
            case "$raw.3dsMax|Parameters|roughness":
                if (isPbr) {
                    floatValue = PropertyUtils.toFloat(property);
                    jmeMaterial.setFloat("Roughness", floatValue);
                } else {
                    ignoreFloat(materialKey, property, 1f);
                }
                break;

            case Assimp.AI_MATKEY_SHININESS: // "$mat.shininess"
            case "$raw.Shininess":
                if (isUnshaded) {
                    ignoreFloat(materialKey, property, 0f);
                } else if (isPhong) {
                    floatValue = PropertyUtils.toFloat(property);
                    jmeMaterial.setFloat("Shininess", floatValue);
                }
                break;

            case Assimp.AI_MATKEY_SHININESS_STRENGTH: // "$mat.shinpercent"
                ignoreFloat(materialKey, property, 0.25f);
                break;

            case Assimp.AI_MATKEY_TRANSMISSION_FACTOR:
            // "$mat.transmission.factor"
            case Assimp.AI_MATKEY_TRANSPARENCYFACTOR:
                // "$mat.transparencyfactor"
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

            case "$raw.3dsMax|ClassIDa":
            case "$raw.3dsMax|ClassIDb":
            case "$raw.3dsMax|ORIGINAL_MTL":
            case "$raw.3dsMax|SuperClassID":
            case "$raw.Maya|TypeId":
            case "$raw.ShadingModel":
                // ignore
                break;

            case "$raw.3dsMax|Parameters|base_weight":
            case "$raw.Maya|base":
                this.baseWeight = PropertyUtils.toFloat(property);
                break;

            case "$raw.3dsMax|Parameters|sss_color":
                ignoreColor(materialKey, property, ColorRGBA.White);
                break;

            case "$raw.3dsMax|Parameters|dispersion":
            case "$raw.3dsMax|Parameters|scattering":
                ignoreFloat(materialKey, property, 0f);
                break;

            case "$raw.3dsMax|Parameters|emit_kelvin":
                ignoreFloat(materialKey, property, 6500f);
                break;

            case "$raw.3dsMax|Parameters|emit_luminance":
                ignoreFloat(materialKey, property, 1500f);
                break;

            case "$raw.3dsMax|Parameters|sss_scale":
            case "$raw.3dsMax|Parameters|brdf_high":
                ignoreFloat(materialKey, property, 1f);
                break;

            case "$raw.3dsMax|Parameters|aniso_channel":
            case "$raw.3dsMax|Parameters|aniso_mode":
            case "$raw.3dsMax|Parameters|material_mode":
            case "$raw.3dsMax|Parameters|roughness_inv":
            case "$raw.3dsMax|Parameters|thin_walled":
                ignoreInteger(materialKey, property, 0);
                break;

            case "$raw.3dsMax|Parameters|anisotropy_map_on":
            case "$raw.3dsMax|Parameters|aniso_angle_map_on":
            case "$raw.3dsMax|Parameters|base_color_map_on":
            case "$raw.3dsMax|Parameters|base_weight_map_on":
            case "$raw.3dsMax|Parameters|brdf_mode":
            case "$raw.3dsMax|Parameters|cutout_map_on":
            case "$raw.3dsMax|Parameters|diff_rough_map_on":
            case "$raw.3dsMax|Parameters|displacement_map_on":
            case "$raw.3dsMax|Parameters|emission_map_on":
            case "$raw.3dsMax|Parameters|emit_color_map_on":
            case "$raw.3dsMax|Parameters|metalness_map_on":
            case "$raw.3dsMax|Parameters|refl_color_map_on":
            case "$raw.3dsMax|Parameters|reflectivity_map_on":
            case "$raw.3dsMax|Parameters|roughness_map_on":
            case "$raw.3dsMax|Parameters|scattering_map_on":
            case "$raw.3dsMax|Parameters|sss_color_map_on":
            case "$raw.3dsMax|Parameters|sss_scale_map_on":
            case "$raw.Maya|thinWalled":
                ignoreInteger(materialKey, property, 1);
                break;

            default:
                // Ignore properties associated with disabled effects:
                boolean forDisabledEffect = isForDisabledEffect(materialKey);
                if (!forDisabledEffect) {
                    String quotedKey = MyString.quote(materialKey);
                    String describeValue = PropertyUtils.describe(property);
                    System.err.printf("Ignoring unexpected "
                            + "(non-texture) matprop with key %s and %s%n",
                            quotedKey, describeValue);
                }
        }

        return result;
    }

    /**
     * Apply the specified Assimp material property to the current JMonkeyEngine
     * material during the 2nd pass over the properties.
     *
     * @param property the Assimp material property (not null, unaffected)
     */
    private void apply2(AIMaterialProperty property) throws IOException {
        ColorRGBA color;
        float intensity;

        String materialKey = property.mKey().dataString();
        switch (materialKey) {
            case "$mat.blend.specular.intensity":
            //  case Assimp.AI_MATKEY_SPECULAR_FACTOR: // "$mat.specularFactor"
            case "$raw.Maya|specular":
                color = jmeMaterial.getParamValue("Specular"); // alias
                intensity = PropertyUtils.toFloat(property);
                color.multLocal(intensity);
                break;

            case Assimp._AI_MATKEY_TEXTURE_BASE: // "$tex.file"
            case "$raw.3dsMax|Parameters|base_color_map|file":
            case "$raw.DiffuseColor|file":
            case "$raw.EmissiveColor|file":
            case "$raw.Maya|baseColor|file":
            case "$raw.NormalMap|file":
            case "$raw.ReflectionFactor|file":
            case "$raw.ShininessExponent|file":
            case "$raw.SpecularColor|file":
            case "$raw.SpecularFactor|file":
            case "$raw.TransparentColor|file":
            case "$raw.TransparencyFactor|file":
                slotTexture(property);
                break;

            default:
                String quotedKey = MyString.quote(materialKey);
                String describeValue = PropertyUtils.describe(property);
                System.err.printf("Ignoring unexpected "
                        + "(2nd-pass) matprop with key %s and %s%n",
                        quotedKey, describeValue);
        }
    }

    /**
     * Apply the base and/or diffuse color(s) to the current JMonkeyEngine
     * material.
     */
    private void applyBaseDiffuseColors() {
        // Apply base and/or diffuse color(s):
        if (baseColor == null) {
            if (baseWeight == null) {
                this.baseWeight = 0f;
            }
            this.baseColor = new ColorRGBA(1f, 1f, 1f, 1f);
        } else if (baseWeight == null) {
            this.baseWeight = 1f;
        }
        if (diffuseColor == null) {
            this.diffuseColor = new ColorRGBA(1f, 1f, 1f, 1f);
        }
        ColorRGBA color
                = MyColor.lerp(baseWeight, diffuseColor, baseColor, null);

        if (isPbr) {
            jmeMaterial.setColor("BaseColor", color);
        } else if (isUnshaded) {
            jmeMaterial.setColor("Color", color);
        } else {
            jmeMaterial.setColor("Diffuse", color);
        }
    }

    /**
     * Apply the specified texture material property to the current
     * JMonkeyEngine material during the first pass over the properties.
     *
     * @param property the Assimp material property to apply (not null,
     * unaffected)
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
            case "$raw.3dsMax|Parameters|base_color_map|file":
            case "$raw.DiffuseColor|file":
            case "$raw.EmissiveColor|file":
            case "$raw.Maya|baseColor|file":
            case "$raw.NormalMap|file":
            case "$raw.ReflectionFactor|file":
            case "$raw.ShininessExponent|file":
            case "$raw.SpecularColor|file":
            case "$raw.SpecularFactor|file":
            case "$raw.TransparencyFactor|file":
            case "$raw.TransparentColor|file":
                result = true; // defer to the next pass
                break;

            case "$tex.file.strength":
            case "$tex.strength":
                if (isPbr) {
                    /*
                     * "AoStrength" was added to "PBRMaterial.j3md"
                     * *after* JME 3.6.1-stable, so make sure it's available.
                     */
                    MaterialDef def = jmeMaterial.getMaterialDef();
                    MatParam paramDef = def.getMaterialParam("AoStrength");
                    if (paramDef == null) {
                        ignoreFloat(materialKey, property, 1f);
                    } else {
                        float strength = PropertyUtils.toFloat(property);
                        jmeMaterial.setFloat("AoStrength", strength);
                    }
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
            case "$raw.3dsMax|Parameters|base_color_map|uvtrafo":
            case "$raw.DiffuseColor|uvtrafo":
            case "$raw.EmissiveColor|uvtrafo":
            case "$raw.Maya|baseColor|uvtrafo":
            case "$raw.NormalMap|uvtrafo":
            case "$raw.ReflectionFactor|uvtrafo":
            case "$raw.ShininessExponent|uvtrafo":
            case "$raw.SpecularColor|uvtrafo":
            case "$raw.SpecularFactor|uvtrafo":
            case "$raw.TransparencyFactor|uvtrafo":
            case "$raw.TransparentColor|uvtrafo":
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
            case "$raw.3dsMax|Parameters|base_color_map|uvwsrc":
            case "$raw.DiffuseColor|uvwsrc":
            case "$raw.EmissiveColor|uvwsrc":
            case "$raw.Maya|baseColor|uvwsrc":
            case "$raw.NormalMap|uvwsrc":
            case "$raw.ReflectionFactor|uvwsrc":
            case "$raw.ShininessExponent|uvwsrc":
            case "$raw.SpecularColor|uvwsrc":
            case "$raw.SpecularFactor|uvwsrc":
            case "$raw.TransparencyFactor|uvwsrc":
            case "$raw.TransparentColor|uvwsrc":
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
                System.err.printf("Ignoring unexpected "
                        + "(texture) matprop with key %s and %s%n",
                        quotedKey, describeValue);
        }

        return result;
    }

    /**
     * Create a placeholder texture for the specified texture path.
     *
     * @param texturePath the texture path to use (not null)
     * @return a new texture (not null)
     */
    private Texture createPlaceholderTexture(String texturePath) {
        String format = textureSearchPath.get(0);
        TextureKey textureKey = createTextureKey(format, texturePath);

        Image image = PlaceholderAssets.getPlaceholderImage(assetManager);
        Texture result = new Texture2D(image);
        result.setKey(textureKey);

        return result;
    }

    /**
     * Create an asset key using the specified asset-path format and texture
     * path.
     *
     * @param apFormat the asset-path format to use (not null)
     * @param texturePath the texture path to use (not null)
     * @return a new key (not null)
     */
    private TextureKey createTextureKey(String apFormat, String texturePath) {
        String name;
        int charPos = texturePath.lastIndexOf("\\");
        if (charPos >= 0) {
            /*
             * It looks like Sketchfab reorganized its FBX assets at some point,
             * putting models in "source/" and textures in "textures/".
             * Apparently, Windows-style texture paths (containing backslashes)
             * did not get updated, so here we use just the final
             * component of the path. XXX
             */
            name = texturePath.substring(charPos + 1);
        } else {
            name = texturePath;
        }

        String baseName;
        String extension;
        charPos = name.lastIndexOf(".");
        if (charPos >= 0) {
            baseName = name.substring(0, charPos);
            extension = name.substring(charPos);
        } else {
            baseName = name;
            extension = "";
        }
        String assetPath
                = String.format(apFormat, assetFolder, baseName, extension);

        TextureKey result = new TextureKey(assetPath);
        result.setFlipY(flipY);
        result.setGenerateMips(true);

        return result;
    }

    /**
     * Print all the defined Assimp material properties.
     *
     * @throws IOException if a material property cannot be converted
     */
    private void dumpPropMap() throws IOException {
        for (Map.Entry<String, AIMaterialProperty> entry : propMap.entrySet()) {
            String materialKey = entry.getKey();
            String quotedKey = MyString.quote(materialKey);

            AIMaterialProperty property = entry.getValue();
            String describeValue = PropertyUtils.describe(property);

            System.out.printf(" %s with %s%n", quotedKey, describeValue);
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
    private void ignoreColor(String materialKey,
            AIMaterialProperty property, ColorRGBA expected)
            throws IOException {
        ColorRGBA actual = PropertyUtils.toColor(property);
        if (!actual.equals(expected) && !isForDisabledEffect(materialKey)) {
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
    private void ignoreFloat(
            String materialKey, AIMaterialProperty property, float expected)
            throws IOException {
        float actual = PropertyUtils.toFloat(property);
        if (actual != expected && !isForDisabledEffect(materialKey)) {
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
    private void ignoreInteger(
            String materialKey, AIMaterialProperty property, int expected)
            throws IOException {
        int actual = PropertyUtils.toInteger(property);
        if (actual != expected && !isForDisabledEffect(materialKey)) {
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
    private void ignoreString(String materialKey, AIMaterialProperty property,
            String expected) throws IOException {
        String actual = PropertyUtils.toString(property);
        if (!actual.equals(expected) && !isForDisabledEffect(materialKey)) {
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
     * Determine which tool-specific effects (if any) are enabled.
     *
     * @throws IOException if a property cannot be interpreted
     */
    private void initializeEffects() throws IOException {
        // Blender effects:
        AIMaterialProperty property = propMap.remove("$mat.blend.mirror.use");
        boolean enabled
                = (property != null && PropertyUtils.toBoolean(property));
        isSfxEnabled.put("$mat.blend.mirror.", enabled);

        property = propMap.remove("$mat.blend.transparency.use");
        enabled = (property != null && PropertyUtils.toBoolean(property));
        isSfxEnabled.put("$mat.blend.transparency.", enabled);

        // Autodesk 3ds Max effects:
        property = propMap.remove("$raw.3dsMax|Parameters|coating");
        enabled = (property != null && PropertyUtils.toFloat(property) != 0f);
        isSfxEnabled.put("$raw.3dsMax|Parameters|coat_", enabled);

        property = propMap.remove("$raw.3dsMax|Parameters|sheen");
        enabled = (property != null && PropertyUtils.toFloat(property) != 0f);
        isSfxEnabled.put("$raw.3dsMax|Parameters|sheen_", enabled);

        property = propMap.remove("$raw.3dsMax|Parameters|thin_film");
        enabled = (property != null && PropertyUtils.toFloat(property) != 0f);
        isSfxEnabled.put("$raw.3dsMax|Parameters|thin_film_", enabled);

        property = propMap.remove("$raw.3dsMax|Parameters|transparency");
        enabled = (property != null && PropertyUtils.toFloat(property) != 0f);
        isSfxEnabled.put("$raw.3dsMax|Parameters|trans", enabled);

        // Autodesk Maya effects:
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

        // Ensure the destination exists:
        if (vbDestination == null) {
            int numFloats = 2 * jmeMesh.getVertexCount();
            FloatBuffer floatBuffer = BufferUtils.createFloatBuffer(numFloats);
            jmeMesh.setBuffer(VertexBuffer.Type.TexCoord, 2, floatBuffer);
            vbDestination
                    = jmeMesh.getBuffer(VertexBuffer.Type.TexCoord);
        }

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
     * Determine which material definitions to use.
     *
     * @return the path to a J3MD asset (not null)
     * @throws IOException if an unexpected shading mode is encountered
     */
    private String selectMaterialDefinitions() throws IOException {
        AIMaterialProperty property = propMap.remove(
                Assimp.AI_MATKEY_SHADING_MODEL); // "$mat.shadingm"
        int shadingMode = (property == null) ? Assimp.aiShadingMode_Phong
                : PropertyUtils.toInteger(property);

        property = propMap.remove("$mat.gltf.unlit"); // deprecated in Assimp
        if (property != null) {
            shadingMode = Assimp.aiShadingMode_Unlit;
        }

        String result;
        switch (shadingMode) {
            case Assimp.aiShadingMode_Blinn:
            case Assimp.aiShadingMode_Gouraud:
            case Assimp.aiShadingMode_Phong:
                result = Materials.LIGHTING;
                break;

            case Assimp.aiShadingMode_PBR_BRDF:
                result = Materials.PBR;
                break;

            case Assimp.aiShadingMode_Unlit:
                result = Materials.UNSHADED;
                break;

            default:
                throw new IOException(
                        "Unexpected shading mode:  " + shadingMode);
        }

        // Select PBR material definitions if there are PBR-type textures:
        if (samplerMap.containsKey("15 0") // METALNESS
                || samplerMap.containsKey("16 0") // DIFFUSE_ROUGHNESS
                || samplerMap.containsKey("17 0")) { // AMBIENT_OCCLUSION
            result = Materials.PBR;
        }

        if (verboseLogging) {
            System.out.println("Using " + result + " material definitions.");
        }

        return result;
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
                // Used in glTF2Importer.cpp for metallic-roughness texture.
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
        if (string.startsWith("*")) { // an embedded texture:
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

            try {
                string = URLDecoder.decode(
                        string, StandardCharsets.UTF_8.name());
            } catch (UnsupportedEncodingException exception) {
                // do nothing
            }

            int maxExceptions = textureSearchPath.size();
            List<AssetNotFoundException> exceptionList
                    = new ArrayList<>(maxExceptions);

            // Attempt to load the texture using the AssetManager:
            result = null;
            for (String format : textureSearchPath) {
                TextureKey textureKey = createTextureKey(format, string);
                try {
                    result = assetManager.loadTexture(textureKey);
                    break;
                } catch (AssetNotFoundException exception) {
                    exceptionList.add(exception);
                }
            }

            // If not found on the texture-search path, create a placeholder:
            if (result == null) {
                for (AssetNotFoundException exception : exceptionList) {
                    System.err.println(exception);
                }
                result = createPlaceholderTexture(string);
            }
        }
        sampler.applyTo(result);

        return result;
    }
}
