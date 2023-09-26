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

import com.jme3.anim.AnimClip;
import com.jme3.anim.AnimTrack;
import com.jme3.anim.Armature;
import com.jme3.anim.Joint;
import com.jme3.anim.TransformTrack;
import com.jme3.math.Matrix4f;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.texture.image.ColorSpace;
import com.jme3.texture.plugins.AWTLoader;
import com.jme3.util.BufferUtils;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;
import jme3utilities.MyString;
import org.lwjgl.PointerBuffer;
import org.lwjgl.assimp.AIAnimation;
import org.lwjgl.assimp.AIMatrix4x4;
import org.lwjgl.assimp.AIMetaData;
import org.lwjgl.assimp.AIMetaDataEntry;
import org.lwjgl.assimp.AINodeAnim;
import org.lwjgl.assimp.AIQuatKey;
import org.lwjgl.assimp.AIQuaternion;
import org.lwjgl.assimp.AIString;
import org.lwjgl.assimp.AITexel;
import org.lwjgl.assimp.AITexture;
import org.lwjgl.assimp.AIVector3D;
import org.lwjgl.assimp.AIVectorKey;
import org.lwjgl.assimp.Assimp;
import org.lwjgl.system.MemoryUtil;

/**
 * Utility methods to convert various lwjgl-assimp data structures into forms
 * suitable for use in JMonkeyEngine.
 *
 * @author Stephen Gold sgold@sonic.net
 */
final class ConversionUtils {
    // *************************************************************************
    // constants and loggers

    /**
     * message logger for this class
     */
    final private static Logger logger
            = Logger.getLogger(ConversionUtils.class.getName());
    // *************************************************************************
    // constructors

    /**
     * A private constructor to inhibit instantiation of this class.
     */
    private ConversionUtils() {
        // do nothing
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Convert the specified AIAnimation to a JMonkeyEngine animation clip.
     *
     * @param aiAnimation the animation to convert (not null, unaffected)
     * @param armature the armature for bone animations (may be null)
     * @param geometryArray all geometries in the model/scene (not null)
     * @return a new instance (not null)
     */
    static AnimClip convertAnimation(AIAnimation aiAnimation,
            Armature armature, Geometry[] geometryArray) throws IOException {
        // Calculate the clip duration in seconds:
        double clipDuration = aiAnimation.mDuration(); // in ticks
        double ticksPerSecond = aiAnimation.mTicksPerSecond();
        if (ticksPerSecond > 0.) { // convert ticks to seconds
            clipDuration /= ticksPerSecond;
        }

        // Create the track list with a null element for each Joint:
        int numJoints = armature.getJointCount();
        List<AnimTrack> trackList = new ArrayList<>(numJoints);
        for (int jointId = 0; jointId < numJoints; ++jointId) {
            trackList.add(null);
        }

        // Convert each aiNodeAnim channel to a bone track:
        int numBoneTracks = aiAnimation.mNumChannels();
        PointerBuffer pChannels = aiAnimation.mChannels();
        for (int trackIndex = 0; trackIndex < numBoneTracks; ++trackIndex) {
            long handle = pChannels.get(trackIndex);
            AINodeAnim aiNodeAnim = AINodeAnim.createSafe(handle);
            TransformTrack track = ConversionUtils.convertNodeAnim(
                    aiNodeAnim, armature, (float) clipDuration);
            Joint joint = (Joint) track.getTarget();
            int jointId = joint.getId();
            trackList.set(jointId, track);
        }
        /*
         * For each Joint without a bone track, create a single-frame track
         * that applies the joint's initial transform:
         */
        for (int jointId = 0; jointId < numJoints; ++jointId) {
            if (trackList.get(jointId) == null) {
                Joint joint = armature.getJoint(jointId);
                Transform initial = joint.getInitialTransform().clone();
                Vector3f[] translations = {initial.getTranslation()};
                Quaternion[] rotations = {initial.getRotation()};
                Vector3f[] scales = {initial.getScale()};

                float[] times = {0f};
                TransformTrack track = new TransformTrack(
                        joint, times, translations, rotations, scales);
                trackList.set(jointId, track);
            }
        }

        int numMeshTracks = aiAnimation.mNumMeshChannels();
        if (numMeshTracks > 0) {
            throw new IOException("Mesh tracks not handled yet.");
            //pChannels = aiAnimation.mMeshChannels();
            //AIMeshAnim aiMeshAnim = AIMeshAnim.createSafe(handle);
        }

        int numMorphMeshTracks = aiAnimation.mNumMorphMeshChannels();
        if (numMorphMeshTracks > 0) {
            throw new IOException("Morph tracks not handled yet.");
            //pChannels = aiAnimation.mMeshChannels();
            //AIMeshMorphAnim aiMeshMorphAnim =
        }

        String clipName = aiAnimation.mName().dataString();
        AnimClip result = new AnimClip(clipName);

        int numTracks = trackList.size();
        AnimTrack[] trackArray = new AnimTrack[numTracks];
        trackList.toArray(trackArray);
        result.setTracks(trackArray);

        return result;
    }

    /**
     * Convert the specified {@code AIMatrix4x4} to a JMonkeyEngine matrix.
     *
     * @param matrix the matrix to convert (not null, unaffected)
     * @return a new instance (not null)
     */
    static Matrix4f convertMatrix(AIMatrix4x4 matrix) {
        float a1 = matrix.a1();
        float a2 = matrix.a2();
        float a3 = matrix.a3();
        float a4 = matrix.a4();

        float b1 = matrix.b1();
        float b2 = matrix.b2();
        float b3 = matrix.b3();
        float b4 = matrix.b4();

        float c1 = matrix.c1();
        float c2 = matrix.c2();
        float c3 = matrix.c3();
        float c4 = matrix.c4();

        float d1 = matrix.d1();
        float d2 = matrix.d2();
        float d3 = matrix.d3();
        float d4 = matrix.d4();

        // According to documentation, an AIMatrix4x4 is row-major.
        Matrix4f result = new Matrix4f(
                a1, a2, a3, a4,
                b1, b2, b3, b4,
                c1, c2, c3, c4,
                d1, d2, d3, d4);

        return result;
    }

    /**
     * Convert the specified {@code AIMetaData} to a Java map.
     *
     * @param metaData (not null, unaffected)
     * @return a new Map containing all-new entries
     */
    static Map<String, Object> convertMetadata(AIMetaData metaData)
            throws IOException {
        Map<String, Object> result = new TreeMap<>();

        AIString.Buffer keys = metaData.mKeys();
        AIMetaDataEntry.Buffer pEntries = metaData.mValues();
        int numProperties = metaData.mNumProperties();
        for (int propertyI = 0; propertyI < numProperties; ++propertyI) {
            AIMetaDataEntry aiMetaDataEntry = pEntries.get(propertyI);
            Object value = convertEntry(aiMetaDataEntry);

            String key = keys.get(propertyI).dataString();
            result.put(key, value);
        }

        return result;
    }

    /**
     * Convert the specified {@code AIQuaternion} to a JMonkeyEngine quaternion.
     *
     * @param aiQuat the quaternion to convert (not null, unaffected)
     * @return a new instance (not null)
     */
    static Quaternion convertQuaternion(AIQuaternion aiQuat) {
        float w = aiQuat.w();
        float x = aiQuat.x();
        float y = aiQuat.y();
        float z = aiQuat.z();
        Quaternion result = new Quaternion(x, y, z, w);

        return result;
    }

    /**
     * Convert the specified Assimp textures into JMonkeyEngine textures.
     *
     * @param pTextures the Assimp textures to convert (not null, unaffected)
     * @return a new array of new instances
     */
    static Texture[] convertTextures(PointerBuffer pTextures) {
        int numTextures = pTextures.capacity();
        if (numTextures > 0) {
            System.out.println("numTextures = " + numTextures);
        }
        Texture[] result = new Texture[numTextures];

        for (int textureIndex = 0; textureIndex < numTextures; ++textureIndex) {
            long handle = pTextures.get(textureIndex);
            AITexture aiTexture = AITexture.createSafe(handle);
            Texture jmeTexture = convertTexture(aiTexture);
            result[textureIndex] = jmeTexture;
        }

        return result;
    }

    /**
     * Convert the specified {@code AIMatrix4x4} to a JMonkeyEngine 3-D
     * transform. Any shear in the matrix is lost.
     *
     * @param matrix the matrix to convert (not null, unaffected)
     * @return a new instance (not null)
     */
    static Transform convertTransform(AIMatrix4x4 matrix) {
        Matrix4f jmeMatrix = convertMatrix(matrix);

        Transform result = new Transform();
        result.fromTransformMatrix(jmeMatrix);

        return result;
    }

    /**
     * Convert the specified {@code AIVector3D} to a JMonkeyEngine vector.
     *
     * @param aiVector the vector to convert (not null, unaffected)
     * @return a new instance (not null)
     */
    static Vector3f convertVector(AIVector3D aiVector) {
        float x = aiVector.x();
        float y = aiVector.y();
        float z = aiVector.z();
        Vector3f result = new Vector3f(x, y, z);
        return result;
    }
    // *************************************************************************
    // private methods

    /**
     * Convert the specified {@code AIMetaDataEntry} to a Java object.
     *
     * @param metaDataEntry (not null, unaffected)
     * @return a new object (not null)
     */
    private static Object convertEntry(AIMetaDataEntry metaDataEntry)
            throws IOException {
        ByteBuffer pVoid = metaDataEntry.mData(1);
        long address = MemoryUtil.memAddress(pVoid);

        Object result;
        int entryType = metaDataEntry.mType();
        switch (entryType) {
            case Assimp.AI_AISTRING:
                AIString aiString = AIString.createSafe(address);
                result = aiString.dataString();
                break;

            case Assimp.AI_AIVECTOR3D:
                AIVector3D aiVector3d = AIVector3D.createSafe(address);
                float x = aiVector3d.x();
                float y = aiVector3d.y();
                float z = aiVector3d.z();
                result = new Vector3f(x, y, z);
                break;

            case Assimp.AI_BOOL:
                ByteBuffer byteBuffer
                        = MemoryUtil.memByteBufferSafe(address, 1);
                result = (byteBuffer.get(0) != 0);
                break;

            case Assimp.AI_DOUBLE:
                DoubleBuffer doubleBuffer
                        = MemoryUtil.memDoubleBufferSafe(address, 1);
                result = doubleBuffer.get(0);
                break;

            case Assimp.AI_FLOAT:
                FloatBuffer floatBuffer
                        = MemoryUtil.memFloatBufferSafe(address, 1);
                result = floatBuffer.get(0);
                break;

            case Assimp.AI_INT32:
                IntBuffer intBuffer = MemoryUtil.memIntBufferSafe(address, 1);
                result = intBuffer.get(0);
                break;

            case Assimp.AI_INT64:
                LongBuffer longBuffer
                        = MemoryUtil.memLongBufferSafe(address, 1);
                result = longBuffer.get(0);
                break;

            default:
                String typeString = typeString(metaDataEntry);
                throw new IOException(
                        "Unexpected metadata entry type:  " + typeString);
        }

        return result;
    }

    /**
     * Convert an AINodeAnim to a JMonkeyEngine bone-animation track.
     *
     * @param aiNodeAnim (not null, unaffected)
     * @param armature (not null)
     * @param duration the expected duration of the track (&ge;0)
     * @return a new instance (not null)
     */
    private static TransformTrack convertNodeAnim(
            AINodeAnim aiNodeAnim, Armature armature, float duration)
            throws IOException {
        assert duration >= 0f : duration;

        String nodeName = aiNodeAnim.mNodeName().dataString();
        Joint target = armature.getJoint(nodeName);
        if (target == null) {
            String qName = MyString.quote(nodeName);
            throw new IOException("Missing joint:  " + qName);
        }
        TransformTrackBuilder builder
                = new TransformTrackBuilder(target, duration);

        int numPositionKeys = aiNodeAnim.mNumPositionKeys();
        AIVectorKey.Buffer pPositionKeys = aiNodeAnim.mPositionKeys();
        for (int keyIndex = 0; keyIndex < numPositionKeys; ++keyIndex) {
            AIVectorKey key = pPositionKeys.get(keyIndex);
            float time = (float) key.mTime();
            Vector3f offset = ConversionUtils.convertVector(key.mValue());
            builder.addTranslation(time, offset);
        }

        int numRotationKeys = aiNodeAnim.mNumRotationKeys();
        AIQuatKey.Buffer pRotationKeys = aiNodeAnim.mRotationKeys();
        for (int keyIndex = 0; keyIndex < numRotationKeys; ++keyIndex) {
            AIQuatKey key = pRotationKeys.get(keyIndex);
            float time = (float) key.mTime();
            Quaternion rotation
                    = ConversionUtils.convertQuaternion(key.mValue());
            builder.addRotation(time, rotation);
        }

        int numScalingKeys = aiNodeAnim.mNumScalingKeys();
        AIVectorKey.Buffer pScalingKeys = aiNodeAnim.mScalingKeys();
        for (int keyIndex = 0; keyIndex < numScalingKeys; ++keyIndex) {
            AIVectorKey key = pScalingKeys.get(keyIndex);
            float time = (float) key.mTime();
            Vector3f scale = ConversionUtils.convertVector(key.mValue());
            builder.addScale(time, scale);
        }

        TransformTrack result = builder.build();
        return result;
    }

    /**
     * Convert the specified {@code AITexture} into a JMonkeyEngine texture.
     *
     * @param aiTexture the Assimp texture to convert (not null, unaffected)
     * @return a new instance (not null)
     */
    private static Texture convertTexture(AITexture aiTexture) {
        int width = aiTexture.mWidth();
        int height = aiTexture.mHeight();
        byte[] byteArray;

        //String nodeName = aiTexture.mFilename().dataString();
        //String qName = MyString.quote(nodeName);
        //ByteBuffer formatHint = aiTexture.achFormatHint();
        //byte[] byteArray = new byte[formatHint.capacity()];
        //formatHint.get(byteArray);
        //String hint = new String(byteArray);
        //String qHint = MyString.quote(hint);
        //System.out.printf(
        // "Converting texture %s hint=%s width=%d height=%d.%n",
        // qName, qHint, width, height);
        //
        AITexel.Buffer pcData = aiTexture.pcData();
        Image image = null;
        if (height == 0) { // compressed image, use AWTLoader
            ByteBuffer wrappedBuffer
                    = MemoryUtil.memByteBufferSafe(pcData.address(), width);
            byteArray = new byte[width];
            for (int i = 0; i < width; ++i) {
                byteArray[i] = wrappedBuffer.get(i);
            }
            InputStream awtStream = new ByteArrayInputStream(byteArray);
            boolean flipY = false;
            try {
                image = new AWTLoader().load(awtStream, flipY);
            } catch (IOException exception) {
                System.out.println(exception);
            }

        } else { // array of texels
            int numTexels = height * width;
            int numBytes = 4 * Float.BYTES * numTexels;
            ByteBuffer jmeData = BufferUtils.createByteBuffer(numBytes);
            for (int y = 0; y < height; ++y) {
                for (int x = 0; x < width; ++x) {
                    int index = x + width * y;
                    AITexel texel = pcData.get(index);
                    float r = texel.r();
                    float g = texel.g();
                    float b = texel.b();
                    float a = texel.a();
                    jmeData.putFloat(r)
                            .putFloat(g)
                            .putFloat(b)
                            .putFloat(a);
                }
            }
            jmeData.flip();
            assert jmeData.limit() == jmeData.capacity();
            image = new Image(Image.Format.RGBA32F, width, height, jmeData,
                    ColorSpace.sRGB);
        }
        Texture result = new Texture2D(image);

        return result;
    }

    /**
     * Convert the type of the specified {@code AIMetaDataEntry} to a string of
     * text.
     *
     * @param metaDataEntry the metadata entry to analyze (not null, unaffected)
     * @return descriptive text (not null)
     */
    private static String typeString(AIMetaDataEntry metaDataEntry) {
        String result;
        int info = metaDataEntry.mType();
        switch (info) {
            case Assimp.AI_AIMETADATA:
                result = "AIMETADATA";
                break;
            case Assimp.AI_AISTRING:
                result = "AISTRING";
                break;
            case Assimp.AI_AIVECTOR3D:
                result = "AIVECTOR3D";
                break;
            case Assimp.AI_BOOL:
                result = "BOOL";
                break;
            case Assimp.AI_DOUBLE:
                result = "DOUBLE";
                break;
            case Assimp.AI_FLOAT:
                result = "FLOAT";
                break;
            case Assimp.AI_INT32:
                result = "INT32";
                break;
            case Assimp.AI_INT64:
                result = "INT64";
                break;
            case Assimp.AI_META_MAX:
                result = "META_MAX";
                break;
            case Assimp.AI_UINT32:
                result = "UINT32";
                break;
            case Assimp.AI_UINT64:
                result = "UINT64";
                break;
            default:
                result = "MDE_" + info;
                break;
        }
        return result;
    }
}
