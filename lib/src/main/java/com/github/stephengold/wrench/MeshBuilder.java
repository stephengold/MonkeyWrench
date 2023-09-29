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

import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.mesh.IndexBuffer;
import com.jme3.util.BufferUtils;
import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.logging.Logger;
import jme3utilities.math.MyVector3f;
import org.lwjgl.PointerBuffer;
import org.lwjgl.assimp.AIBone;
import org.lwjgl.assimp.AIColor4D;
import org.lwjgl.assimp.AIFace;
import org.lwjgl.assimp.AIMesh;
import org.lwjgl.assimp.AIVector3D;
import org.lwjgl.assimp.AIVertexWeight;
import org.lwjgl.assimp.Assimp;

/**
 * Utility methods to construct a JMonkeyEngine mesh.
 *
 * @author Stephen Gold sgold@sonic.net
 */
final public class MeshBuilder {
    // *************************************************************************
    // constants and loggers

    /**
     * message logger for this class
     */
    final private static Logger logger
            = Logger.getLogger(MeshBuilder.class.getName());
    // *************************************************************************
    // constructors

    /**
     * A private constructor to inhibit instantiation of this class.
     */
    private MeshBuilder() {
        // do nothing
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Convert the specified {@code AIMesh} into a JMonkeyEngine mesh.
     *
     * @param aiMesh the Assimp mesh to convert (not null, unaffected)
     * @param skinnerBuilder information about the model's bones (not null)
     * @return a new instance (not null)
     */
    static Mesh convertMesh(AIMesh aiMesh, SkinnerBuilder skinnerBuilder)
            throws IOException {
        Mesh result = new Mesh();

        // Determine the topology:
        int vpp;
        int meshType = aiMesh.mPrimitiveTypes()
                & ~Assimp.aiPrimitiveType_NGONEncodingFlag;
        switch (meshType) {
            case Assimp.aiPrimitiveType_POINT:
                result.setMode(Mesh.Mode.Points);
                vpp = 1;
                break;

            case Assimp.aiPrimitiveType_LINE:
                result.setMode(Mesh.Mode.Lines);
                vpp = 2;
                break;

            case Assimp.aiPrimitiveType_TRIANGLE:
                result.setMode(Mesh.Mode.Triangles);
                vpp = 3;
                break;

            default:
                throw new IOException(
                        "Unsupported primitive in mesh, meshType=" + meshType);
        }

        int numAnimMeshes = aiMesh.mNumAnimMeshes();
        if (numAnimMeshes > 0) {
            throw new IOException("Morph animation not handled yet.");
            //PointerBuffer pAnimMeshes = aiMesh.mAnimMeshes();
            //int morphMethod = aiMesh.mMethod();
        }

        // Convert the vertex buffers:
        AIColor4D.Buffer pAiColors = aiMesh.mColors(1);
        if (pAiColors != null) {
            logger.warning("JMonkeyEngine doesn't support "
                    + "multiple vertex colors - ignored.");
        }

        AIVector3D.Buffer pAiPositions = aiMesh.mVertices();
        VertexBuffer vertexBuffer = toPositionBuffer(pAiPositions);
        result.setBuffer(vertexBuffer);
        int vertexCount = vertexBuffer.getNumElements();

        AIVector3D.Buffer pAiBitangents = aiMesh.mBitangents();
        if (pAiBitangents != null) {
            assert pAiBitangents.capacity() == vertexCount :
                    pAiBitangents.capacity();
            vertexBuffer = toBinormalBuffer(pAiBitangents);
            result.setBuffer(vertexBuffer);
        }

        pAiColors = aiMesh.mColors(0);
        if (pAiColors != null) {
            assert pAiColors.capacity() == vertexCount : pAiColors.capacity();
            vertexBuffer = toColorBuffer(pAiColors);
            result.setBuffer(vertexBuffer);
        }

        AIVector3D.Buffer pAiNormals = aiMesh.mNormals();
        if (pAiNormals != null) {
            assert pAiNormals.capacity() == vertexCount : pAiNormals.capacity();
            vertexBuffer = toNormalBuffer(pAiNormals);
            result.setBuffer(vertexBuffer);
        }

        AIVector3D.Buffer pAiTangents = aiMesh.mTangents();
        if (pAiTangents != null) {
            assert pAiTangents.capacity() == vertexCount :
                    pAiTangents.capacity();
            vertexBuffer = toTangentBuffer(pAiTangents);
            result.setBuffer(vertexBuffer);
        }

        int numBones = aiMesh.mNumBones();
        if (numBones > 0) {
            PointerBuffer pBones = aiMesh.mBones();
            addBoneBuffers(
                    numBones, vertexCount, pBones, result, skinnerBuilder);
        }

        IntBuffer pNumComponents = aiMesh.mNumUVComponents();
        PointerBuffer ppAiTexCoords = aiMesh.mTextureCoords();
        if (pNumComponents != null && ppAiTexCoords != null) {
            int maxUvChannels = Math.min(
                    pNumComponents.capacity(), ppAiTexCoords.capacity());
            for (int channelI = 0; channelI < maxUvChannels; ++channelI) {
                AIVector3D.Buffer pAiTexCoords
                        = aiMesh.mTextureCoords(channelI);
                if (pAiTexCoords != null) {
                    int numComponents = pNumComponents.get(channelI);
                    VertexBuffer.Type vbType = uvType(channelI);
                    vertexBuffer = toTexCoordsBuffer(
                            pAiTexCoords, numComponents, vbType);
                    result.setBuffer(vertexBuffer);
                }
            }
        }

        AIFace.Buffer pFaces = aiMesh.mFaces();
        addIndexBuffer(pFaces, vertexCount, vpp, result);

        result.updateCounts();
        result.updateBound();

        return result;
    }
    // *************************************************************************
    // private methods

    /**
     * Add a bone-index buffer and a bone-weight buffer to the specified
     * JMonkeyEngine mesh.
     *
     * @param numBones the number of bones in the rig (&gt;0)
     * @param vertexCount the number of vertices in the mesh (&gt;0)
     * @param pBones a buffer of pointers to {@code AIBone} data
     * @param mesh the mesh to modify (not null)
     * @param skinnerBuilder information about the model's bones (not null)
     */
    private static void addBoneBuffers(int numBones, int vertexCount,
            PointerBuffer pBones, Mesh mesh, SkinnerBuilder skinnerBuilder) {
        // Create vertex buffers for hardware skinning:
        VertexBuffer hwBoneIndexVbuf
                = new VertexBuffer(VertexBuffer.Type.HWBoneIndex);
        VertexBuffer hwBoneWeightVbuf
                = new VertexBuffer(VertexBuffer.Type.HWBoneWeight);

        // Initialize usage to CpuOnly so buffers are not sent empty to the GPU:
        hwBoneIndexVbuf.setUsage(VertexBuffer.Usage.CpuOnly);
        hwBoneWeightVbuf.setUsage(VertexBuffer.Usage.CpuOnly);

        mesh.setBuffer(hwBoneIndexVbuf);
        mesh.setBuffer(hwBoneWeightVbuf);

        // Create a BoneIndex vertex buffer:
        int capacity = WeightList.maxSize * vertexCount;
        Buffer boneIndexData;
        if (numBones > 32767) {
            boneIndexData = BufferUtils.createIntBuffer(capacity);
            mesh.setBuffer(VertexBuffer.Type.BoneIndex,
                    WeightList.maxSize, (IntBuffer) boneIndexData);
        } else if (numBones > 255) {
            boneIndexData = BufferUtils.createShortBuffer(capacity);
            mesh.setBuffer(VertexBuffer.Type.BoneIndex,
                    WeightList.maxSize, (ShortBuffer) boneIndexData);
        } else {
            boneIndexData = BufferUtils.createByteBuffer(capacity);
            mesh.setBuffer(VertexBuffer.Type.BoneIndex,
                    WeightList.maxSize, (ByteBuffer) boneIndexData);
        }
        VertexBuffer boneIndexVbuf
                = mesh.getBuffer(VertexBuffer.Type.BoneIndex);
        boneIndexVbuf.setUsage(VertexBuffer.Usage.CpuOnly);

        // Create a BoneWeight vertex buffer:
        FloatBuffer boneWeightData
                = BufferUtils.createFloatBuffer(capacity);
        mesh.setBuffer(VertexBuffer.Type.BoneWeight,
                WeightList.maxSize, boneWeightData);
        VertexBuffer boneWeightVbuf
                = mesh.getBuffer(VertexBuffer.Type.BoneWeight);
        boneWeightVbuf.setUsage(VertexBuffer.Usage.CpuOnly);

        // Collect the joint IDs and weights for each mesh vertex:
        WeightList[] weightListArray = new WeightList[vertexCount];
        for (int vertexId = 0; vertexId < vertexCount; ++vertexId) {
            weightListArray[vertexId] = new WeightList();
        }
        for (int boneIndex = 0; boneIndex < numBones; ++boneIndex) {
            long address = pBones.get(boneIndex);
            AIBone aiBone = AIBone.createSafe(address);
            String boneName = aiBone.mName().dataString();
            int jointId = skinnerBuilder.jointId(boneName);

            int numWeights = aiBone.mNumWeights();
            AIVertexWeight.Buffer pWeights = aiBone.mWeights();
            for (int j = 0; j < numWeights; ++j) {
                AIVertexWeight aiVertexWeight = pWeights.get(j);
                int vertexId = aiVertexWeight.mVertexId();
                weightListArray[vertexId].add(aiVertexWeight, jointId);
            }
        }

        // Write joint IDs and weights to the vertex buffers:
        int maxNumWeights = 0;
        for (int vertexId = 0; vertexId < vertexCount; ++vertexId) {
            WeightList weightList = weightListArray[vertexId];
            int numWeights = weightList.count();
            if (numWeights > maxNumWeights) {
                maxNumWeights = numWeights;
            }
            weightList.putIndices(boneIndexData);
            weightList.putWeights(boneWeightData);
        }
        mesh.setMaxNumWeights(maxNumWeights);

        boneIndexData.flip();
        assert boneIndexData.limit() == boneIndexData.capacity();

        boneWeightData.flip();
        assert boneWeightData.limit() == boneWeightData.capacity();

        mesh.generateBindPose();
    }

    /**
     * Add an index buffer to the specified JMonkeyEngine mesh.
     *
     * @param pFaces the buffer to copy faces from (not null, unaffected)
     * @param vertexCount the number of vertices in the mesh (&ge;0)
     * @param vpf the number of vertices per face (&ge;1, &le;3)
     * @param jmeMesh the JMonkeyEngine mesh to modify (not null)
     */
    private static void addIndexBuffer(
            AIFace.Buffer pFaces, int vertexCount, int vpf, Mesh jmeMesh)
            throws IOException {
        assert vpf >= 1 : vpf;
        assert vpf <= 3 : vpf;

        int numFaces = pFaces.capacity();
        int indexCount = numFaces * vpf;
        IndexBuffer indexBuffer
                = IndexBuffer.createIndexBuffer(vertexCount, indexCount);

        for (int faceIndex = 0; faceIndex < numFaces; ++faceIndex) {
            AIFace face = pFaces.get(faceIndex);
            IntBuffer pIndices = face.mIndices();
            int numIndices = face.mNumIndices();
            if (numIndices != vpf) {
                String message = String.format(
                        "Expected %d indices in face but found %d indices.",
                        vpf, numIndices);
                throw new IOException(message);
            }
            for (int j = 0; j < numIndices; ++j) {
                int vertexIndex = pIndices.get(j);
                indexBuffer.put(vertexIndex);
            }
        }
        Buffer ibData = indexBuffer.getBuffer();
        ibData.flip();

        VertexBuffer.Format ibFormat = indexBuffer.getFormat();
        jmeMesh.setBuffer(VertexBuffer.Type.Index, 1, ibFormat, ibData);
    }

    /**
     * Convert the specified vectors to a JMonkeyEngine vertex buffer with
     * type=Binormal.
     * <p>
     * Note: apparently "binormal" and "bitangent" refer to the same vector.
     *
     * @param pAiBitangents the buffer to copy vectors from (not null,
     * unaffected)
     * @return a new vertex buffer (not null)
     */
    private static VertexBuffer toBinormalBuffer(
            AIVector3D.Buffer pAiBitangents) {
        int numVertices = pAiBitangents.capacity();
        FloatBuffer floats = BufferUtils.createVector3Buffer(numVertices);

        for (int vertexIndex = 0; vertexIndex < numVertices; ++vertexIndex) {
            AIVector3D binormal = pAiBitangents.get(vertexIndex);
            float x = binormal.x();
            float y = binormal.y();
            float z = binormal.z();
            floats.put(x).put(y).put(z);
        }
        floats.flip();

        VertexBuffer result = new VertexBuffer(VertexBuffer.Type.Binormal);
        result.setupData(VertexBuffer.Usage.Static, MyVector3f.numAxes,
                VertexBuffer.Format.Float, floats);

        return result;
    }

    /**
     * Convert the specified colors to a JMonkeyEngine vertex buffer with
     * type=Color.
     *
     * @param pAiColors the buffer to copy colors from (not null, unaffected)
     * @return a new vertex buffer (not null)
     */
    private static VertexBuffer toColorBuffer(AIColor4D.Buffer pAiColors) {
        int numVertices = pAiColors.capacity();
        int numFloats = 4 * numVertices;
        FloatBuffer floats = BufferUtils.createFloatBuffer(numFloats);

        for (int vertexIndex = 0; vertexIndex < numVertices; ++vertexIndex) {
            AIColor4D color = pAiColors.get(vertexIndex);
            float r = color.r();
            float g = color.g();
            float b = color.b();
            float a = color.a();
            floats.put(r).put(g).put(b).put(a);
        }
        floats.flip();

        VertexBuffer result = new VertexBuffer(VertexBuffer.Type.Color);
        result.setupData(VertexBuffer.Usage.Static, 4,
                VertexBuffer.Format.Float, floats);

        return result;
    }

    /**
     * Convert the specified vectors to a JMonkeyEngine vertex buffer with
     * type=Normal.
     *
     * @param pAiNormals the buffer to copy vectors from (not null, unaffected)
     * @return a new vertex buffer (not null)
     */
    private static VertexBuffer toNormalBuffer(AIVector3D.Buffer pAiNormals) {
        int numVertices = pAiNormals.capacity();
        FloatBuffer floats = BufferUtils.createVector3Buffer(numVertices);

        for (int vertexIndex = 0; vertexIndex < numVertices; ++vertexIndex) {
            AIVector3D normal = pAiNormals.get(vertexIndex);
            float x = normal.x();
            float y = normal.y();
            float z = normal.z();
            floats.put(x).put(y).put(z);
        }
        floats.flip();

        VertexBuffer result = new VertexBuffer(VertexBuffer.Type.Normal);
        result.setupData(VertexBuffer.Usage.Static, MyVector3f.numAxes,
                VertexBuffer.Format.Float, floats);

        return result;
    }

    /**
     * Convert the specified vectors to a JMonkeyEngine vertex buffer with
     * type=Position.
     *
     * @param pAiPositions the buffer to copy vectors from (not null,
     * unaffected)
     * @return a new vertex buffer (not null)
     */
    private static VertexBuffer toPositionBuffer(
            AIVector3D.Buffer pAiPositions) {
        int numVertices = pAiPositions.capacity();
        FloatBuffer floats = BufferUtils.createVector3Buffer(numVertices);

        for (int vertexIndex = 0; vertexIndex < numVertices; ++vertexIndex) {
            AIVector3D position = pAiPositions.get(vertexIndex);
            float x = position.x();
            float y = position.y();
            float z = position.z();
            floats.put(x).put(y).put(z);
        }
        floats.flip();

        VertexBuffer result = new VertexBuffer(VertexBuffer.Type.Position);
        result.setupData(VertexBuffer.Usage.Static, MyVector3f.numAxes,
                VertexBuffer.Format.Float, floats);

        return result;
    }

    /**
     * Convert the specified vectors to a JMonkeyEngine vertex buffer with
     * type=Tangent.
     *
     * @param pAiTangents the buffer to copy vectors from (not null, unaffected)
     * @return a new vertex buffer (not null)
     */
    private static VertexBuffer toTangentBuffer(AIVector3D.Buffer pAiTangents) {
        int numVertices = pAiTangents.capacity();
        int numFloats = 4 * numVertices;
        FloatBuffer floats = BufferUtils.createFloatBuffer(numFloats);

        for (int vertexIndex = 0; vertexIndex < numVertices; ++vertexIndex) {
            AIVector3D tangent = pAiTangents.get(vertexIndex);
            float x = tangent.x();
            float y = tangent.y();
            float z = tangent.z();
            floats.put(x).put(y).put(z).put(-1f);
        }
        floats.flip();

        VertexBuffer result = new VertexBuffer(VertexBuffer.Type.Tangent);
        result.setupData(VertexBuffer.Usage.Static, 4,
                VertexBuffer.Format.Float, floats);

        return result;
    }

    /**
     * Convert the specified vectors to a JMonkeyEngine vertex buffer containing
     * texture (U-V) coordinates.
     *
     * @param pAiTexCoords the buffer to copy texture coordinates from (not
     * null, unaffected)
     * @param numComponents the number of (float) components in each set of
     * texture coordinates (&ge;1, &le;3)
     * @param vbType the type of vertex buffer to create (not null)
     * @return a new vertex buffer (not null)
     */
    private static VertexBuffer toTexCoordsBuffer(
            AIVector3D.Buffer pAiTexCoords, int numComponents,
            VertexBuffer.Type vbType) {
        assert numComponents >= 1 : numComponents;
        assert numComponents <= 3 : numComponents;
        assert vbType != null;

        int numVertices = pAiTexCoords.capacity();
        int numFloats = numVertices * numComponents;
        FloatBuffer floats = BufferUtils.createFloatBuffer(numFloats);

        for (int vertexIndex = 0; vertexIndex < numVertices; ++vertexIndex) {
            AIVector3D texCoords = pAiTexCoords.get(vertexIndex);
            float u = texCoords.x();
            floats.put(u);
            if (numComponents > 1) {
                float v = texCoords.y();
                floats.put(v);
                if (numComponents > 2) {
                    float w = texCoords.z();
                    floats.put(w);
                }
            }
        }
        floats.flip();

        VertexBuffer result = new VertexBuffer(vbType);
        result.setupData(VertexBuffer.Usage.Static, numComponents,
                VertexBuffer.Format.Float, floats);

        return result;
    }

    /**
     * Convert a texture-coordinate channel into a JMonkeyEngine vertex-buffer
     * type.
     *
     * @param channelIndex which channel (&ge;0, &lt;8)
     * @return an enum value (not null)
     */
    private static VertexBuffer.Type uvType(int channelIndex)
            throws IOException {
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
}
