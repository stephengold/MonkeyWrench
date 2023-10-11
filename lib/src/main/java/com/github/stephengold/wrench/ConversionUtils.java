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
import com.jme3.anim.MorphControl;
import com.jme3.anim.MorphTrack;
import com.jme3.anim.TransformTrack;
import com.jme3.anim.util.HasLocalTransform;
import com.jme3.light.DirectionalLight;
import com.jme3.light.PointLight;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Matrix4f;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.CameraNode;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.control.LightControl;
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import jme3utilities.MySpatial;
import jme3utilities.MyString;
import jme3utilities.Validate;
import jme3utilities.wes.TransformTrackBuilder;
import org.lwjgl.PointerBuffer;
import org.lwjgl.assimp.AIAnimation;
import org.lwjgl.assimp.AICamera;
import org.lwjgl.assimp.AIColor3D;
import org.lwjgl.assimp.AILight;
import org.lwjgl.assimp.AIMatrix4x4;
import org.lwjgl.assimp.AIMeshMorphAnim;
import org.lwjgl.assimp.AIMeshMorphKey;
import org.lwjgl.assimp.AIMetaData;
import org.lwjgl.assimp.AIMetaDataEntry;
import org.lwjgl.assimp.AINodeAnim;
import org.lwjgl.assimp.AIQuatKey;
import org.lwjgl.assimp.AIQuaternion;
import org.lwjgl.assimp.AIString;
import org.lwjgl.assimp.AITexel;
import org.lwjgl.assimp.AITexture;
import org.lwjgl.assimp.AIVector2D;
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
     * @param clipName name for the new clip (not null, not empty)
     * @param armature the armature for bone animations (may be null)
     * @param jmeRoot the root node of the converted scene graph (not null)
     * @return a new instance (not null)
     */
    static AnimClip convertAnimation(AIAnimation aiAnimation, String clipName,
            Armature armature, Node jmeRoot) throws IOException {
        assert Validate.nonEmpty(clipName, "clipName");

        double clipDurationInTicks = aiAnimation.mDuration();
        double ticksPerSecond = aiAnimation.mTicksPerSecond();
        if (ticksPerSecond == 0.) {
            // If the rate is unspecified, assume one tick per second:
            ticksPerSecond = 1.;
        }

        // Create the track list with a null element for each Joint:
        int numJoints = (armature == null) ? 0 : armature.getJointCount();
        List<AnimTrack> trackList = new ArrayList<>(numJoints);
        for (int jointId = 0; jointId < numJoints; ++jointId) {
            trackList.add(null);
        }

        // Convert each aiNodeAnim channel to a Transform Track:
        int numChannels = aiAnimation.mNumChannels();
        PointerBuffer pChannels = aiAnimation.mChannels();
        for (int trackIndex = 0; trackIndex < numChannels; ++trackIndex) {
            long handle = pChannels.get(trackIndex);
            AINodeAnim aiNodeAnim = AINodeAnim.createSafe(handle);
            TransformTrack track = convertNodeAnim(aiNodeAnim, armature,
                    jmeRoot, clipDurationInTicks, ticksPerSecond);

            HasLocalTransform target = track.getTarget();
            if (target instanceof Joint) {
                Joint joint = (Joint) track.getTarget();
                int jointId = joint.getId();
                trackList.set(jointId, track);
            } else { // The target is probably a Spatial.
                trackList.add(track);
            }
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
            pChannels = aiAnimation.mMorphMeshChannels();
            for (int trackI = 0; trackI < numMorphMeshTracks; ++trackI) {
                long handle = pChannels.get(trackI);
                AIMeshMorphAnim anim = AIMeshMorphAnim.createSafe(handle);
                List<MorphTrack> morphTrack
                        = convertMeshMorphAnim(anim, jmeRoot, ticksPerSecond);
                trackList.addAll(morphTrack);
            }
        }

        AnimClip result = new AnimClip(clipName);

        int numTracks = trackList.size();
        AnimTrack[] trackArray = new AnimTrack[numTracks];
        trackList.toArray(trackArray);
        result.setTracks(trackArray);

        return result;
    }

    /**
     * Convert the specified {@code AICamera} to a JMonkeyEngine camera node.
     *
     * @param aiCamera the camera to convert (not null, unaffected)
     * @return a new node (not null)
     */
    static CameraNode convertCamera(AICamera aiCamera) {
        String nodeName = aiCamera.mName().dataString();
        CameraNode result = new CameraNode(nodeName, (Camera) null);

        // Determine the camera node's offset relative to its parent:
        Vector3f offset
                = ConversionUtils.convertVector(aiCamera.mPosition());
        result.setLocalTranslation(offset);

        // Determine the camera's orientation relative to its parent:
        Vector3f lookDirection = convertVector(aiCamera.mLookAt());
        Vector3f upDirection = convertVector(aiCamera.mUp());
        Quaternion rotation = new Quaternion();
        rotation.lookAt(lookDirection, upDirection);
        result.setLocalRotation(rotation);

        return result;
    }

    /**
     * Convert the specified {@code AIColor3D} to a JMonkeyEngine color.
     *
     * @param aiColor the color to convert (not null, unaffected)
     * @return a new instance (not null)
     */
    static ColorRGBA convertColor(AIColor3D aiColor) {
        float r = aiColor.r();
        float g = aiColor.g();
        float b = aiColor.b();
        ColorRGBA result = new ColorRGBA(r, g, b, 1f);

        return result;
    }

    /**
     * Convert the specified {@code AILight} to a JMonkeyEngine directional
     * light controlled by a LightControl.
     *
     * @param aiLight the light to convert (not null, unaffected)
     * @return a new node with a new LightControl (not null)
     */
    static Node convertDirectionalLight(AILight aiLight) {
        DirectionalLight directionalLight = new DirectionalLight();

        ColorRGBA color = convertColor(aiLight.mColorDiffuse());
        directionalLight.setColor(color);

        LightControl lightControl = new LightControl(directionalLight);

        Vector3f direction = convertVector(aiLight.mDirection());
        Vector3f up = convertVector(aiLight.mUp());
        Quaternion rotation = new Quaternion();
        rotation.lookAt(direction, up);

        Node result = new Node();
        result.addControl(lightControl);
        result.setLocalRotation(rotation);

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
     * Convert the specified {@code AILight} to a JMonkeyEngine point light
     * controlled by a LightControl.
     *
     * @param aiLight the light to convert (not null, unaffected)
     * @return a new Node with a new LightControl (not null)
     */
    static Node convertPointLight(AILight aiLight) {
        /*
         * JMonkeyEngine's PointLight has a hard cutoff that impedes
         * implementing attenuation as specified by Assimp.
         */
        float att1 = aiLight.mAttenuationLinear();
        float att2 = aiLight.mAttenuationQuadratic();
        if (att1 != 0f || att2 != 0f) {
            float att0 = aiLight.mAttenuationConstant();
            logger.log(Level.WARNING, "Ignoring attenuation of point light:"
                    + "  att0={0}, att1={1}, att2={2}",
                    new Object[]{att0, att1, att2});
        }

        PointLight pointLight = new PointLight(); // with radius = 0

        ColorRGBA color = convertColor(aiLight.mColorDiffuse());
        pointLight.setColor(color);

        LightControl lightControl = new LightControl(pointLight);
        Vector3f offset = convertVector(aiLight.mPosition());

        Node result = new Node();
        result.addControl(lightControl);
        result.setLocalTranslation(offset);

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

        float determinant = jmeMatrix.determinant();
        if (determinant <= 0f) {
            System.out.flush();
            logger.log(Level.WARNING, "determinant = {0}", determinant);
            System.err.flush();
        }

        if (determinant < 0f) { // Work around JME issue #2089:
            jmeMatrix.m00 *= -1f;
            jmeMatrix.m10 *= -1f;
            jmeMatrix.m20 *= -1f;
        }

        Transform result = new Transform();
        result.fromTransformMatrix(jmeMatrix);

        if (determinant < 0f) { // Work around JME issue #2089:
            result.getScale().x *= -1f;
        }

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

    /**
     * Convert the specified {@code AIVector3D} to a JMonkeyEngine vector.
     *
     * @param aiVector the vector to convert (not null, unaffected)
     * @return a new instance (not null)
     */
    static Vector2f convertVector2D(AIVector2D aiVector) {
        float x = aiVector.x();
        float y = aiVector.y();
        Vector2f result = new Vector2f(x, y);

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
     * Convert the specified AIMeshMorphAnim to a collection of JMonkeyEngine
     * animation tracks.
     *
     * @param aiMeshMorphAnim (not null, unaffected)
     * @param jmeRoot the root node of the converted scene graph (not null,
     * modified)
     * @param ticksPerSecond the number of ticks per second for the current
     * model (&gt;0)
     * @return a new list of new tracks (not null)
     */
    private static List<MorphTrack> convertMeshMorphAnim(
            AIMeshMorphAnim aiMeshMorphAnim, Node jmeRoot,
            double ticksPerSecond) throws IOException {
        assert jmeRoot != null;

        String targetName = aiMeshMorphAnim.mName().dataString();
        if (targetName == null || targetName.isEmpty()) {
            throw new IOException("Invalid name for morph-animation target.");
        }
        /*
         * According to Assimp inline documentation, it's fine for
         * multiple meshes to have the same name.
         */
        List<MorphTrack> result = new ArrayList<>(1); // empty list
        List<Geometry> targetList = listMorphTargets(targetName, jmeRoot);
        if (targetList.isEmpty()) {
            logger.log(Level.WARNING, "No targets found for morph animation.");
            return result;
        }

        int numKeyframes = aiMeshMorphAnim.mNumKeys();
        //System.out.println("numKeyframes = " + numKeyframes);
        float[] timeArray = new float[numKeyframes];

        AIMeshMorphKey.Buffer pKeys = aiMeshMorphAnim.mKeys();
        AIMeshMorphKey key = pKeys.get(0);
        int numWeightsPerFrame = key.mNumValuesAndWeights();
        //System.out.println("numWeightsPerFrame = " + numWeightsPerFrame);

        IntBuffer mValues = key.mValues();
        int numTargets = mValues.capacity();
        //System.out.println("numTargets=" + numTargets);

        int numFloats = numKeyframes * numWeightsPerFrame;
        float[] weightArray = new float[numFloats];

        for (int frameI = 0; frameI < numKeyframes; ++frameI) {
            key = pKeys.get(frameI);
            assert numWeightsPerFrame == key.mNumValuesAndWeights();

            double time = key.mTime() / ticksPerSecond;
            timeArray[frameI] = (float) time;

            // We don't support anything fancy here:
            mValues = key.mValues();
            assert numTargets == mValues.capacity();
            for (int targetI = 0; targetI < numTargets; ++targetI) {
                assert mValues.get(targetI) == targetI;
            }

            DoubleBuffer mWeights = key.mWeights();
            for (int j = 0; j < numWeightsPerFrame; ++j) {
                // Copy the weights in keyframe-major order:
                int floatIndex = frameI * numWeightsPerFrame + j;
                weightArray[floatIndex] = (float) mWeights.get();
            }
        }

        for (Geometry target : targetList) {
            // Clone arrays to prevent unexpected aliasing:
            float[] times = Arrays.copyOf(timeArray, numKeyframes);
            float[] weights = Arrays.copyOf(weightArray, numFloats);

            MorphTrack morphTrack = new MorphTrack(
                    target, times, weights, numWeightsPerFrame);
            result.add(morphTrack);
        }

        MorphControl morphControl = new MorphControl();
        jmeRoot.addControl(morphControl);

        return result;
    }

    /**
     * Convert the specified AINodeAnim to a JMonkeyEngine animation track.
     *
     * @param aiNodeAnim (not null, unaffected)
     * @param armature the Armature of the converted model/scene (may be null)
     * @param jmeRoot the root node of the converted model/scene (not null)
     * @param clipDurationInTicks the duration of the track (in ticks, &ge;0)
     * @param ticksPerSecond the number of ticks per second (&gt;0)
     * @return a new instance (not null)
     */
    private static TransformTrack convertNodeAnim(AINodeAnim aiNodeAnim,
            Armature armature, Node jmeRoot, double clipDurationInTicks,
            double ticksPerSecond) throws IOException {
        assert jmeRoot != null;
        assert ticksPerSecond > 0 : ticksPerSecond;

        String nodeName = aiNodeAnim.mNodeName().dataString();
        HasLocalTransform target = getNode(nodeName, armature, jmeRoot);
        double trackSeconds = clipDurationInTicks / ticksPerSecond;
        TransformTrackBuilder builder
                = new TransformTrackBuilder(target, (float) trackSeconds);

        int numPositionKeys = aiNodeAnim.mNumPositionKeys();
        AIVectorKey.Buffer pPositionKeys = aiNodeAnim.mPositionKeys();
        for (int keyIndex = 0; keyIndex < numPositionKeys; ++keyIndex) {
            AIVectorKey key = pPositionKeys.get(keyIndex);
            double time = key.mTime() / ticksPerSecond;
            Vector3f offset = convertVector(key.mValue());
            builder.addTranslation((float) time, offset);
        }

        int numRotationKeys = aiNodeAnim.mNumRotationKeys();
        AIQuatKey.Buffer pRotationKeys = aiNodeAnim.mRotationKeys();
        for (int keyIndex = 0; keyIndex < numRotationKeys; ++keyIndex) {
            AIQuatKey key = pRotationKeys.get(keyIndex);
            double time = key.mTime() / ticksPerSecond;
            Quaternion rotation = convertQuaternion(key.mValue());
            builder.addRotation((float) time, rotation);
        }

        int numScalingKeys = aiNodeAnim.mNumScalingKeys();
        AIVectorKey.Buffer pScalingKeys = aiNodeAnim.mScalingKeys();
        for (int keyIndex = 0; keyIndex < numScalingKeys; ++keyIndex) {
            AIVectorKey key = pScalingKeys.get(keyIndex);
            double time = key.mTime() / ticksPerSecond;
            Vector3f scale = convertVector(key.mValue());
            builder.addScale((float) time, scale);
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
     * Return the Node or Joint corresponding to the named AINode.
     *
     * @param nodeName the name to search for (not null)
     * @param armature the Armature of the converted model/scene (may be null)
     * @param jmeRoot the root node of the converted model/scene (not null)
     * @return a pre-existing Node or Joint (not null)
     * @throws IOException if the name is not found
     */
    private static HasLocalTransform getNode(String nodeName, Armature armature,
            Node jmeRoot) throws IOException {
        assert nodeName != null;
        assert jmeRoot != null;

        HasLocalTransform result = null;
        if (armature != null) {
            // Search for an armature joint with the specified name:
            result = armature.getJoint(nodeName);
        }

        if (result == null) {
            // Search for a scene-graph node with the specified name:
            List<Node> nodeList
                    = MySpatial.listSpatials(jmeRoot, Node.class, null);
            for (Node node : nodeList) {
                String name = node.getName();
                if (nodeName.equals(name)) {
                    result = node;
                    break;
                }
            }
        }

        if (result == null) {
            String qName = MyString.quote(nodeName);
            throw new IOException("Missing joint or node:  " + qName);
        }

        return result;
    }

    /**
     * Enumerate morph targets in the specified scene-graph subtree.
     *
     * @param targetName the name of the mesh to search for (not null, not
     * empty)
     * @param subtree (not null, aliases created)
     * @return a new list of pre-existing geometries (not null)
     */
    private static List<Geometry> listMorphTargets(
            String targetName, Spatial subtree) {
        assert targetName != null;
        assert !targetName.isEmpty();
        assert subtree != null;

        List<Geometry> result = new ArrayList<>(2); // empty list

        // Search for geometries with the target name:
        List<Geometry> geometryList = MySpatial.listGeometries(subtree);
        for (Geometry geometry : geometryList) {
            String geometryName = geometry.getName();
            if (targetName.equals(geometryName)) {
                result.add(geometry);
            }
        }

        if (result.isEmpty()) {
            // TODO seen in AnimatedMorphCube.gltf
            String qName = MyString.quote(targetName);
            logger.log(Level.WARNING, "No mesh named {0} was found.", qName);

            // Search for nodes with the target name:
            List<Node> nodeList
                    = MySpatial.listSpatials(subtree, Node.class, null);
            for (Node node : nodeList) {
                String name = node.getName();
                if (targetName.equals(name)) {
                    geometryList = MySpatial.listGeometries(node);
                    int numTargets = geometryList.size();
                    /*
                     * In JMonkeyEngine, morph tracks can only target
                     * geometries, not nodes.
                     *
                     * Target all geometries in the node's subtree,
                     * regardless of their names:
                     */
                    result.addAll(geometryList);
                    logger.log(Level.WARNING,
                            "A node named {0} provided {1} morph target{2}.",
                            new Object[]{
                                qName, numTargets, (numTargets == 1) ? "" : "s"
                            });
                    // TODO open an Assimp issue for this
                }
            }
        }

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
