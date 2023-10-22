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

import com.jme3.math.ColorRGBA;
import com.jme3.math.Matrix3f;
import com.jme3.math.Vector3f;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;
import jme3utilities.MyString;
import org.lwjgl.assimp.AIMaterialProperty;
import org.lwjgl.assimp.AIString;
import org.lwjgl.assimp.AIUVTransform;
import org.lwjgl.assimp.AIVector2D;
import org.lwjgl.assimp.Assimp;
import org.lwjgl.system.MemoryUtil;

/**
 * Utility methods to decode an {@code AIMaterialProperty}.
 *
 * @author Stephen Gold sgold@sonic.net
 */
final class PropertyUtils {
    // *************************************************************************
    // constants and loggers

    /**
     * message logger for this class
     */
    final private static Logger logger
            = Logger.getLogger(PropertyUtils.class.getName());
    // *************************************************************************
    // constructors

    /**
     * A private constructor to inhibit instantiation of this class.
     */
    private PropertyUtils() {
        // do nothing
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Describe the value of the specified Assimp material property.
     *
     * @param property the property to describe (not null, unaffected)
     * @return a descriptive string of text (not null, not empty)
     * @throws IOException if the property cannot be converted
     */
    static String describe(AIMaterialProperty property) throws IOException {
        int numBytes = property.mDataLength();
        int mType = property.mType();

        String result;
        if (mType == Assimp.aiPTI_Float && numBytes == AIUVTransform.SIZEOF) {
            Matrix3f uvTransform = toUvTransform(property);
            result = "transform " + uvTransform;

        } else if (mType == Assimp.aiPTI_Float
                && (numBytes == 12 || numBytes == 16)) {
            ColorRGBA color = toColor(property);
            result = "color value " + color.r + " " + color.g
                    + " " + color.b + " " + color.a;

        } else if (mType == Assimp.aiPTI_Float && numBytes == 4) {
            result = "float value " + toFloat(property);

        } else if (mType == Assimp.aiPTI_Integer && numBytes == 4) {
            result = "int value " + toInteger(property);

        } else if (mType == Assimp.aiPTI_Buffer
                && (numBytes == 1 || numBytes == 4)) {
            result = "int value " + toInteger(property);

        } else if (mType == Assimp.aiPTI_String) {
            String value = toString(property);
            result = "string value " + MyString.quote(value);

        } else {
            String pluralBytes = (numBytes == 1) ? "" : "s";
            String typeString = typeString(property);
            result = String.format(
                    "%d byte%s of %s data", numBytes, pluralBytes, typeString);
        }

        return result;
    }

    /**
     * Convert the semantic information (texture type) of the argument to a
     * string of text.
     *
     * @param property the material property to analyze (not null, unaffected)
     * @return descriptive text (not null, not empty)
     */
    static String semanticString(AIMaterialProperty property) {
        int semanticType = property.mSemantic();
        String result = Assimp.aiTextureTypeToString(semanticType);

        return result;
    }

    /**
     * Encode the texture index and usage semantic of the argument to a string
     * of text.
     *
     * @param property the property to encode (not null, unaffected)
     * @return a string, or null if {@code property} is not a texture property
     * @throws IOException if the argument has unexpected parameters
     */
    static String suffixString(AIMaterialProperty property) throws IOException {
        int textureIndex = property.mIndex();
        int semantic = property.mSemantic();

        String result;
        String materialKey = property.mKey().dataString();
        if (materialKey.startsWith("$tex.")) { // texture property
            assert textureIndex >= 0 : textureIndex;
            result = semantic + " " + textureIndex;

        } else if (materialKey.startsWith("$raw.")
                && semantic != Assimp.aiTextureType_NONE) {
            assert textureIndex >= 0 : textureIndex;
            result = semantic + " " + textureIndex;

        } else { // non-texture property - no suffix
            assert textureIndex == 0 : textureIndex;
            assert semantic == Assimp.aiTextureType_NONE :
                    "semantic = " + semantic
                    + ", materialKey = " + MyString.quote(materialKey);
            result = null;
        }

        return result;
    }

    /**
     * Convert the data of the argument to a {@code boolean}.
     *
     * @param property the property to convert (not null, unaffected)
     * @return the converted value
     * @throws IOException if the property cannot be converted
     */
    static boolean toBoolean(AIMaterialProperty property) throws IOException {
        int integer = toInteger(property);
        if (integer == 0) {
            return false;
        } else {
            return true;
        }
    }

    /**
     * Convert the data of the argument to a JMonkeyEngine color.
     *
     * @param property the property to convert (not null, unaffected)
     * @return a new instance
     * @throws IOException if the property cannot be converted
     */
    static ColorRGBA toColor(AIMaterialProperty property) throws IOException {
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
     * Convert the data of the argument to a single float.
     *
     * @param property the property to convert (not null, unaffected)
     * @return the converted value
     * @throws IOException if the property cannot be converted
     */
    static float toFloat(AIMaterialProperty property) throws IOException {
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
     * Convert the data of the argument to a single integer.
     *
     * @param property the property to convert (not null, unaffected)
     * @return the converted value
     * @throws IOException if the property cannot be converted
     */
    static int toInteger(AIMaterialProperty property) throws IOException {
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
     * Convert the data of the argument to a Java string.
     *
     * @param property the property to convert (not null, unaffected)
     * @return the converted value (not null)
     */
    static String toString(AIMaterialProperty property) throws IOException {
        String result;

        ByteBuffer byteBuffer = property.mData();
        long address = MemoryUtil.memAddressSafe(byteBuffer);

        int propertyType = property.mType();
        switch (propertyType) {
            case Assimp.aiPTI_String:
                AIString aiString = AIString.createSafe(address);
                result = aiString.dataString();
                break;

            default:
                String typeString = typeString(property);
                throw new IOException(
                        "Unexpected property type:  " + typeString);
        }

        return result;
    }

    /**
     * Convert the data of the argument to a U-V transform matrix.
     *
     * @param property the property to convert (not null, unaffected)
     * @return a new matrix
     * @throws IOException if the property cannot be converted
     */
    static Matrix3f toUvTransform(AIMaterialProperty property)
            throws IOException {
        int propertyType = property.mType();
        if (propertyType != Assimp.aiPTI_Float) {
            String typeString = typeString(property);
            throw new IOException(
                    "Unexpected property type:  " + typeString);
        }

        ByteBuffer byteBuffer = property.mData();
        int numBytes = byteBuffer.capacity();
        if (numBytes != AIUVTransform.SIZEOF) {
            throw new IOException("Unexpected size:  " + numBytes);
        }

        long address = MemoryUtil.memAddressSafe(byteBuffer);
        AIUVTransform uvTrafo = AIUVTransform.createSafe(address);

        Matrix3f result = new Matrix3f(); // identity

        // Apply scaling, rotation, and translation, in that order:
        AIVector2D scaling = uvTrafo.mScaling();
        result.set(0, 0, scaling.x());
        result.set(1, 1, scaling.y());

        Matrix3f traMatrix = new Matrix3f(); // identity

        float rotationAngle = uvTrafo.mRotation(); // rads, CCW around (.5,.5)
        if (rotationAngle != 0f) {
            traMatrix.set(0, 2, -0.5f);
            traMatrix.set(1, 2, -0.5f);
            traMatrix.mult(result, result);

            Matrix3f rotMatrix = new Matrix3f();
            rotMatrix.fromAngleNormalAxis(rotationAngle, Vector3f.UNIT_Z);
            rotMatrix.mult(result, result);

            traMatrix.set(0, 2, 0.5f);
            traMatrix.set(1, 2, 0.5f);
            traMatrix.mult(result, result);
        }

        AIVector2D translation = uvTrafo.mTranslation();
        traMatrix.set(0, 2, translation.x());
        traMatrix.set(1, 2, translation.y());
        traMatrix.mult(result, result);

        // Override some round-off errors:
        result.set(2, 0, 0f);
        result.set(2, 1, 0f);
        result.set(2, 2, 1f);

        return result;
    }

    /**
     * Convert the type information of the argument to a string of text.
     *
     * @param property the material property to analyze (not null, unaffected)
     * @return descriptive text (not null)
     */
    static String typeString(AIMaterialProperty property) {
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
