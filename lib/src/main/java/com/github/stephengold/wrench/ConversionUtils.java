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

import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.light.PointLight;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Matrix4f;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.CameraNode;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
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
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import jme3utilities.MySpatial;
import jme3utilities.MyString;
import org.lwjgl.PointerBuffer;
import org.lwjgl.assimp.AICamera;
import org.lwjgl.assimp.AIColor3D;
import org.lwjgl.assimp.AILight;
import org.lwjgl.assimp.AIMatrix4x4;
import org.lwjgl.assimp.AIMetaData;
import org.lwjgl.assimp.AIMetaDataEntry;
import org.lwjgl.assimp.AIQuaternion;
import org.lwjgl.assimp.AIString;
import org.lwjgl.assimp.AITexel;
import org.lwjgl.assimp.AITexture;
import org.lwjgl.assimp.AIVector3D;
import org.lwjgl.assimp.Assimp;
import org.lwjgl.system.MemoryUtil;

/**
 * Utility methods to convert various lwjgl-assimp data structures into
 * JMonkeyEngine objects.
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
     * Convert the specified {@code AILight} to a JMonkeyEngine ambient light.
     *
     * @param aiLight the light source to convert (not null, unaffected)
     * @return a new instance (not null)
     */
    static AmbientLight convertAmbientLight(AILight aiLight) {
        AmbientLight result = new AmbientLight();

        ColorRGBA color = convertColor(aiLight.mColorDiffuse());
        result.setColor(color);

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
        Vector3f offset = convertVector(aiCamera.mPosition());
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
     * Convert the specified {@code AIMetaData} to a Java map. Note: recursive!
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
        // TODO Assimp doesn't provide the "range"
        pointLight.setColor(color);

        LightControl lightControl = new LightControl(pointLight);
        Vector3f offset = convertVector(aiLight.mPosition());

        Node result = new Node();
        result.addControl(lightControl);
        result.setLocalTranslation(offset);

        return result;
    }

    /**
     * Convert the specified embedded textures to JMonkeyEngine textures.
     *
     * @param pTextures the Assimp textures to convert (not null, unaffected)
     * @param loadFlags post-processing flags that were passed to
     * {@code aiImportFile()}
     * @return a new array of new instances
     * @throws IOException if AWTLoader fails to convert a compressed image
     */
    static Texture[] convertTextures(PointerBuffer pTextures, int loadFlags)
            throws IOException {
        int numTextures = pTextures.capacity();
        Texture[] result = new Texture[numTextures];

        boolean flipY = (loadFlags & Assimp.aiProcess_FlipUVs) == 0x0;
        for (int textureIndex = 0; textureIndex < numTextures; ++textureIndex) {
            long handle = pTextures.get(textureIndex);
            AITexture aiTexture = AITexture.createSafe(handle);
            Texture jmeTexture = convertTexture(aiTexture, flipY);
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
            logger.log(Level.INFO, "determinant = {0}", determinant);
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
     * Convert a texture-coordinate channel into a JMonkeyEngine vertex-buffer
     * type.
     *
     * @param channelIndex which channel (&ge;0, &lt;8)
     * @return an enum value (not null)
     */
    static VertexBuffer.Type uvType(int channelIndex) throws IOException {
        VertexBuffer.Type result;
        switch (channelIndex) {
            case 0:
                result = VertexBuffer.Type.TexCoord;
                break;

            case 1:
                result = VertexBuffer.Type.TexCoord2;
                break;

            case 2:
                result = VertexBuffer.Type.TexCoord3;
                break;

            case 3:
                result = VertexBuffer.Type.TexCoord4;
                break;

            case 4:
                result = VertexBuffer.Type.TexCoord5;
                break;

            case 5:
                result = VertexBuffer.Type.TexCoord6;
                break;

            case 6:
                result = VertexBuffer.Type.TexCoord7;
                break;

            case 7:
                result = VertexBuffer.Type.TexCoord8;
                break;

            default:
                throw new IOException("Too many texture-coordinate "
                        + "channels in mesh, channelIndex=" + channelIndex);
        }

        return result;
    }
    // *************************************************************************
    // private methods

    /**
     * Convert the specified {@code AIColor3D} to a JMonkeyEngine color.
     *
     * @param aiColor the color to convert (not null, unaffected)
     * @return a new instance (not null)
     */
    private static ColorRGBA convertColor(AIColor3D aiColor) {
        float red = aiColor.r();
        float green = aiColor.g();
        float blue = aiColor.b();
        ColorRGBA result = new ColorRGBA(red, green, blue, 1f);

        return result;
    }

    /**
     * Convert the specified {@code AIMetaDataEntry} to a Java object. Note:
     * recursive!
     *
     * @param metaDataEntry the Assimp metadata to convert (not null,
     * unaffected)
     * @return a new object (not null)
     */
    private static Object convertEntry(AIMetaDataEntry metaDataEntry)
            throws IOException {
        ByteBuffer pVoid = metaDataEntry.mData(1);
        long address = MemoryUtil.memAddress(pVoid);

        Object result;
        int entryType = metaDataEntry.mType();
        switch (entryType) {
            case Assimp.AI_AIMETADATA:
                AIMetaData aiMetaData = AIMetaData.createSafe(address);
                result = convertMetadata(aiMetaData);
                break;

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
            case Assimp.AI_UINT64:
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
     * Convert the specified uncompressed Assimp texture to a JMonkeyEngine
     * image.
     *
     * @param pcData the uncompressed texture data to convert (not null,
     * unaffected)
     * @param width the texture width (in texels, &ge;0)
     * @param height the texture height (in texels, &ge;0)
     * @param flipY true to reverse the Y coordinates of image data, false to
     * leave them unflipped
     * @return a new instance (not null)
     */
    private static Image convertImage(
            AITexel.Buffer pcData, int width, int height, boolean flipY) {
        if (height >= 99999) {
            logger.log(Level.WARNING,
                    "Embedded texture has a height of {0} texels!", height);
        }
        if (width >= 99999) {
            logger.log(Level.WARNING,
                    "Embedded texture has a width of {0} texels!", width);
        }

        int numTexels = height * width;
        int numBytes = 4 * Float.BYTES * numTexels;
        ByteBuffer jmeData = BufferUtils.createByteBuffer(numBytes);
        for (int yy = 0; yy < height; ++yy) {
            int y = flipY ? height - yy - 1 : yy;
            for (int x = 0; x < width; ++x) {
                int index = x + width * y;
                AITexel texel = pcData.get(index);
                float r = texel.r();
                float g = texel.g();
                float b = texel.b();
                float a = texel.a();
                jmeData.putFloat(r).putFloat(g).putFloat(b).putFloat(a);
            }
        }
        jmeData.flip();
        assert jmeData.limit() == jmeData.capacity();
        Image result = new Image(
                Image.Format.RGBA32F, width, height, jmeData, ColorSpace.sRGB);

        return result;
    }

    /**
     * Convert the specified {@code AIQuaternion} to a JMonkeyEngine quaternion.
     *
     * @param aiQuat the quaternion to convert (not null, unaffected)
     * @return a new instance (not null)
     */
    static Quaternion convertQuaternion(AIQuaternion aiQuat) { // TODO re-order
        float w = aiQuat.w();
        float x = aiQuat.x();
        float y = aiQuat.y();
        float z = aiQuat.z();
        Quaternion result = new Quaternion(x, y, z, w);

        return result;
    }

    /**
     * Convert the specified embedded texture to a JMonkeyEngine texture.
     *
     * @param aiTexture the Assimp texture to convert (not null, unaffected)
     * @param flipY true to reverse the Y coordinates of image data, false to
     * leave them unflipped
     * @return a new instance (not null)
     * @throws IOException if AWTLoader fails to decompress an image
     */
    private static Texture convertTexture(AITexture aiTexture, boolean flipY)
            throws IOException {
        int width = aiTexture.mWidth();
        int height = aiTexture.mHeight();

        AITexel.Buffer pcData = aiTexture.pcData();
        Image image;
        if (height == 0) { // a compressed image, try AWTLoader:
            ByteBuffer wrappedBuffer
                    = MemoryUtil.memByteBufferSafe(pcData.address(), width);
            image = decompressImage(wrappedBuffer, flipY);

        } else if (height < 0 || width < 0) {
            throw new IOException("Embedded texture has a negative dimension!");

        } else { // an array of texels in R8G8B8A8 format:
            image = convertImage(pcData, width, height, flipY);
        }
        Texture result = new Texture2D(image);

        return result;
    }

    /**
     * Convert the specified {@code AIVector3D} to a JMonkeyEngine vector.
     *
     * @param aiVector the vector to convert (not null, unaffected)
     * @return a new instance (not null)
     */
    static Vector3f convertVector(AIVector3D aiVector) { // TODO re-order
        float x = aiVector.x();
        float y = aiVector.y();
        float z = aiVector.z();
        Vector3f result = new Vector3f(x, y, z);

        return result;
    }

    /**
     * Decompress the specified compressed image using AWTLoader.
     *
     * @param byteBuffer the compressed image data to convert (not null)
     * @param flipY true to reverse the Y coordinates of image data, false to
     * leave them unflipped
     * @return a new instance (not null)
     * @throws IOException if AWTLoader fails to decompress the image
     */
    private static Image decompressImage(
            ByteBuffer byteBuffer, boolean flipY) throws IOException {
        int numBytes = byteBuffer.capacity();
        byte[] byteArray = new byte[numBytes];
        for (int i = 0; i < numBytes; ++i) {
            byteArray[i] = byteBuffer.get(i);
        }
        InputStream awtStream = new ByteArrayInputStream(byteArray);

        AWTLoader awtLoader = new AWTLoader();
        Image result = awtLoader.load(awtStream, flipY);
        if (result == null) {
            StringBuilder message = new StringBuilder(80);
            message.append("AWTLoader failed to decompress an embedded image.");
            if (numBytes > 0) {
                message.append("  content =");
            }
            for (int byteI = 0; byteI < 8 && byteI < numBytes; ++byteI) {
                int intValue = 0xFF & byteArray[byteI];
                String hexCodes = String.format(" %02x", intValue);
                message.append(hexCodes);
            }
            if (numBytes > 8) {
                message.append(" ...");
            }
            throw new IOException(message.toString());
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
    static List<Geometry> listMorphTargets(String targetName, Spatial subtree) {
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
