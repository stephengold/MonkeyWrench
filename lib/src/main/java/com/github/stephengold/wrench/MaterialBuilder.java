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
     */
    MaterialBuilder(AIMaterial aiMaterial, AssetManager assetManager,
            String assetFolder, Texture[] embeddedTextures) {
        assert assetManager != null;
        assert assetFolder != null;
        assert embeddedTextures != null;

        this.assetManager = assetManager;
        this.assetFolder = assetFolder;
        this.embeddedTextures = embeddedTextures;

        // Convert the Assimp material properties into a Map:
        PointerBuffer ppProperties = aiMaterial.mProperties();
        int numProperties = ppProperties.capacity();
        for (int i = 0; i < numProperties; ++i) {
            long handle = ppProperties.get(i);
            AIMaterialProperty property = AIMaterialProperty.createSafe(handle);
            String materialKey = property.mKey().dataString();
            propMap.put(materialKey, property);
        }

        // Determine the Assimp shading model:
        int shadingModel;
        AIMaterialProperty property
                = propMap.remove(Assimp.AI_MATKEY_SHADING_MODEL);
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
                throw new IllegalArgumentException(
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
    Material createJmeMaterial() {
        Material result = new Material(assetManager, matDefs);
        if (matDefs.equals(Materials.LIGHTING)) {
            // Supply some default parameters:
            result.setBoolean("UseMaterialColors", true);
            result.setColor("Ambient", new ColorRGBA(0.2f, 0.2f, 0.2f, 1f));
            result.setColor("Diffuse", new ColorRGBA(1f, 1f, 1f, 1f));
            result.setColor("Specular", new ColorRGBA(0f, 0f, 0f, 1f));
            //result.setFloat("Shininess", 16f);
        }
        this.jmeMaterial = result;

        // Use the remaining properties to tune the material:
        Map<String, AIMaterialProperty> map2 = new TreeMap<>();
        for (Map.Entry<String, AIMaterialProperty> entry : propMap.entrySet()) {
            String materialKey = entry.getKey();
            //System.out.println("materialKey: " + materialKey);
            AIMaterialProperty property = entry.getValue();
            boolean defer = apply(materialKey, property);
            if (defer) { // property deferred to the next pass
                map2.put(materialKey, property);
            }
        }
        for (Map.Entry<String, AIMaterialProperty> entry : map2.entrySet()) {
            String materialKey = entry.getKey();
            AIMaterialProperty property = entry.getValue();
            apply2(materialKey, property);
        }

        return result;
    }
    // *************************************************************************
    // private methods

    /**
     * Apply the specified Assimp material key and property to the specified
     * JMonkeyEngine material during the first pass over the properties.
     *
     * @param materialKey the name of the Assimp material key (not null, not
     * empty)
     * @param property the the Assimp material property (not null, unaffected)
     * @return true to defer the property to the next pass, otherwise false
     */
    private boolean apply(String materialKey, AIMaterialProperty property) {
        boolean result = false;
        ColorRGBA color;
        float floatValue;
        RenderState ars = jmeMaterial.getAdditionalRenderState();
        String defName = jmeMaterial.getMaterialDef().getAssetName();
        String string;
        Texture texture;

        switch (materialKey) {
            case Assimp.AI_MATKEY_ANISOTROPY_FACTOR:
                ignoreFloat(materialKey, property, 0f);
                break;

            case Assimp.AI_MATKEY_BASE_COLOR:
                color = toColor(property);
                jmeMaterial.setColor("BaseColor", color);
                break;

            case Assimp.AI_MATKEY_COLOR_AMBIENT:
                color = toColor(property);
                jmeMaterial.setColor("Ambient", color);
                break;

            case Assimp.AI_MATKEY_COLOR_DIFFUSE:
            case "$mat.blend.diffuse.color":
                color = toColor(property);
                if (defName.equals(Materials.PBR)) {
                    jmeMaterial.setColor("BaseColor", color);
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

            case Assimp.AI_MATKEY_COLOR_EMISSIVE:
                color = toColor(property);
                if (defName.equals(Materials.PBR)) {
                    jmeMaterial.setColor("Emissive", color);
                } else {
                    jmeMaterial.setColor("GlowColor", color);
                }
                break;

            case Assimp.AI_MATKEY_COLOR_REFLECTIVE:
                ignoreColor(materialKey, property, ColorRGBA.White);
                break;

            case Assimp.AI_MATKEY_COLOR_SPECULAR:
            case "$mat.blend.specular.color":
                color = toColor(property);
                jmeMaterial.setColor("Specular", color);
                break;

            case "$mat.blend.specular.intensity":
                result = true; // defer to the next pass
                break;

            case "$mat.blend.specular.ramp":
            case "$mat.blend.specular.shader":
                ignoreInteger(materialKey, property, 0);
                break;

            case Assimp.AI_MATKEY_COLOR_TRANSPARENT:
                ignoreColor(materialKey, property, ColorRGBA.White);
                break;

            case Assimp.AI_MATKEY_GLTF_ALPHACUTOFF:
                floatValue = toFloat(property);
                jmeMaterial.setFloat("AlphaDiscardThreshold", floatValue);
                break;

            case Assimp.AI_MATKEY_GLTF_ALPHAMODE:
                ignoreString(materialKey, property, "OPAQUE");
                break;

            case Assimp._AI_MATKEY_GLTF_MAPPINGFILTER_MAG_BASE:
                ignoreInteger(materialKey, property, 0x2601);
                break;

            case Assimp._AI_MATKEY_GLTF_MAPPINGFILTER_MIN_BASE:
                ignoreInteger(materialKey, property, 0x2702);
                break;

            case Assimp._AI_MATKEY_GLTF_MAPPINGID_BASE:
                ignoreString(materialKey, property, "samplers[0]");
                break;

            case Assimp._AI_MATKEY_GLTF_MAPPINGNAME_BASE:
                ignoreString(materialKey, property, "");
                break;

            case Assimp._AI_MATKEY_MAPPINGMODE_U_BASE:
                ignoreInteger(materialKey, property, 0);
                break;

            case Assimp._AI_MATKEY_MAPPINGMODE_V_BASE:
                ignoreInteger(materialKey, property, 0);
                break;

            case Assimp.AI_MATKEY_METALLIC_FACTOR:
                floatValue = toFloat(property);
                jmeMaterial.setFloat("Metallic", floatValue);
                break;

            case Assimp.AI_MATKEY_NAME:
                string = toString(property);
                jmeMaterial.setName(string);
                break;

            case Assimp.AI_MATKEY_OBJ_ILLUM:
                // always ignore
                break;

            case Assimp.AI_MATKEY_OPACITY:
            case Assimp.AI_MATKEY_REFRACTI:
                ignoreFloat(materialKey, property, 1f);
                break;

            case Assimp.AI_MATKEY_ROUGHNESS_FACTOR:
                floatValue = toFloat(property);
                jmeMaterial.setFloat("Roughness", floatValue);
                break;

            case Assimp.AI_MATKEY_SHININESS:
                floatValue = toFloat(property);
                if (defName.equals(Materials.PBR)) {
                    jmeMaterial.setFloat("Glossiness", floatValue);
                } else {
                    jmeMaterial.setFloat("Shininess", floatValue);
                }
                break;

            case Assimp._AI_MATKEY_TEXTURE_BASE:
                texture = toTexture(property);
                if (defName.equals(Materials.PBR)) {
                    jmeMaterial.setTexture("BaseColorMap", texture);
                } else {
                    jmeMaterial.setTexture("DiffuseMap", texture);
                }
                break;

            case Assimp.AI_MATKEY_TWOSIDED:
                if (toBoolean(property)) {
                    ars.setFaceCullMode(RenderState.FaceCullMode.Off);
                } else {
                    ars.setFaceCullMode(RenderState.FaceCullMode.Back);
                }
                break;

            case Assimp._AI_MATKEY_UVWSRC_BASE:
                ignoreInteger(materialKey, property, 0);
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
     * Apply the specified Assimp material key and property to the specified
     * JMonkeyEngine material during the 2nd pass over the properties.
     *
     * @param materialKey the name of the Assimp material key (not null, not
     * empty)
     * @param property the the Assimp material property (not null, unaffected)
     */
    private void apply2(String materialKey, AIMaterialProperty property) {
        ColorRGBA color;
        float intensity;
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
                color = jmeMaterial.getParamValue("Specular"); // alias
                intensity = toFloat(property);
                color.multLocal(intensity);
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
            AIMaterialProperty property, ColorRGBA expected) {
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
            String materialKey, AIMaterialProperty property, float expected) {
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
            String materialKey, AIMaterialProperty property, int expected) {
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
            String materialKey, AIMaterialProperty property, String expected) {
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
     * Convert an AIMaterialProperty to a boolean.
     *
     * @param property the property to convert (not null, unaffected)
     * @return the converted value
     */
    private static boolean toBoolean(AIMaterialProperty property) {
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
     */
    private static ColorRGBA toColor(AIMaterialProperty property) {
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
                throw new IllegalArgumentException(
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
     */
    private static float toFloat(AIMaterialProperty property) {
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
                throw new IllegalArgumentException(
                        "Unexpected property type:  " + typeString);
        }

        return result;
    }

    /**
     * Convert an AIMaterialProperty to a single integer.
     *
     * @param property the property to convert (not null, unaffected)
     * @return the converted value
     */
    private static int toInteger(AIMaterialProperty property) {
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
                throw new IllegalArgumentException(
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
    private static String toString(AIMaterialProperty property) {
        byte[] byteArray;
        ByteBuffer byteBuffer = property.mData();
        int propertyType = property.mType();
        switch (propertyType) {
            case Assimp.aiPTI_Buffer:
            case Assimp.aiPTI_String:
                int numBytes = byteBuffer.capacity();
                byteArray = new byte[numBytes];
                byteBuffer.get(byteArray);
                break;

            default:
                String typeString = typeString(property);
                throw new IllegalArgumentException(
                        "Unexpected property type:  " + typeString);
        }

        String result = new String(byteArray);
        // Delete extraneous whitespace:
        result = result.trim();

        return result;
    }

    /**
     * Convert an AIMaterialProperty to a JMonkeyEngine texture.
     *
     * @param property the property to convert (not null, unaffected)
     * @return a Texture instance with a key (not null)
     */
    private Texture toTexture(AIMaterialProperty property) {
        //int index = property.mIndex();
        //int semantic = property.mSemantic();
        //System.out.println("semantic=" + semantic + " index=" + index);

        String string = toString(property);
        Texture result;
        if (string.startsWith("*")) {
            String indexString = string.substring(1);
            int textureIndex = Integer.parseInt(indexString);
            result = embeddedTextures[textureIndex];

        } else {
            if (string.startsWith("1 1 ")) { // TODO what does this mean?
                string = string.substring(4);
            }

            // Attempt to load the texture using the AssetManager:
            String assetPath = assetFolder + string;
            assetPath = assetPath.replace("///", "/");
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
            result.setWrap(Texture.WrapMode.Repeat);
        }

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
