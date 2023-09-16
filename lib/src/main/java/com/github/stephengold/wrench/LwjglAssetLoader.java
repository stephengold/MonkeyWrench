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

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetLoadException;
import com.jme3.asset.AssetLoader;
import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.mesh.IndexBuffer;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.util.BufferUtils;
import com.jme3.util.PlaceholderAssets;
import java.io.IOException;
import java.nio.Buffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jme3utilities.MyString;
import jme3utilities.math.MyVector3f;
import org.lwjgl.PointerBuffer;
import org.lwjgl.assimp.AIColor4D;
import org.lwjgl.assimp.AIFace;
import org.lwjgl.assimp.AIFileIO;
import org.lwjgl.assimp.AILogStream;
import org.lwjgl.assimp.AIMaterial;
import org.lwjgl.assimp.AIMesh;
import org.lwjgl.assimp.AINode;
import org.lwjgl.assimp.AIScene;
import org.lwjgl.assimp.AITexel;
import org.lwjgl.assimp.AITexture;
import org.lwjgl.assimp.AIVector3D;
import org.lwjgl.assimp.Assimp;

/**
 * A versatile loader for model/scene assets based on lwjgl-assimp.
 *
 * @author Stephen Gold sgold@sonic.net
 */
final public class LwjglAssetLoader implements AssetLoader {
    // *************************************************************************
    // constants and loggers

    /**
     * message logger for this class
     */
    final private static Logger logger
            = Logger.getLogger(LwjglAssetLoader.class.getName());
    // *************************************************************************
    // constructors

    /**
     * The publicly accessible no-arg constructor required by
     * {@code DesktopAssetManager}, made explicit to avoid javadoc warnings from
     * JDK 18.
     */
    public LwjglAssetLoader() {
        // do nothing
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Convert the specified Assimp materials into JMonkeyEngine materials.
     *
     * @param pMaterials the Assimp materials to convert (not null, unaffected)
     * @param assetManager for loading textures (not null)
     * @param assetFolder the asset path of the folder from which the
     * model/scene was loaded (not null)
     * @param embeddedTextures the list of embedded textures (not null)
     * @return a new list of new instances
     */
    static List<Material> convertMaterials(
            PointerBuffer pMaterials, AssetManager assetManager,
            String assetFolder, List<Texture> embeddedTextures) {
        int numMaterials = pMaterials.capacity();
        List<Material> result = new ArrayList<>(numMaterials);

        for (int i = 0; i < numMaterials; ++i) {
            long handle = pMaterials.get(i);
            AIMaterial aiMaterial = AIMaterial.createSafe(handle);
            Material jmeMaterial = MaterialUtils.createJmeMaterial(
                    aiMaterial, assetManager, assetFolder, embeddedTextures);
            result.add(jmeMaterial);
        }

        return result;
    }

    /**
     * Create a JMonkeyEngine node that approximates the specified Assimp node.
     * Note: recursive!
     *
     * @param aiNode the Assimp node to convert (not null, unaffected)
     * @param materialList the list of materials in the model/scene (not null,
     * unaffected)
     * @param pMeshes the handles of all the meshes in the model/scene (not
     * null, unaffected)
     * @return a new instance (not null)
     */
    static Node convertNode(AINode aiNode, List<Material> materialList,
            PointerBuffer pMeshes) {
        String nodeName = aiNode.mName().dataString();
        Node result = new Node(nodeName);

        IntBuffer pMeshIndices = aiNode.mMeshes();
        if (pMeshIndices != null) {
            int numMeshesInNode = pMeshIndices.capacity();
            for (int i = 0; i < numMeshesInNode; ++i) {
                int meshIndex = pMeshIndices.get(i);
                long handle = pMeshes.get(meshIndex);
                AIMesh aiMesh = AIMesh.createSafe(handle);
                Mesh jmeMesh = convertMesh(aiMesh);

                String meshName = aiMesh.mName().dataString();
                Geometry geometry = new Geometry(meshName, jmeMesh);

                int materialIndex = aiMesh.mMaterialIndex();
                Material material = materialList.get(materialIndex);
                geometry.setMaterial(material);

                result.attachChild(geometry);
            }
        }

        PointerBuffer pChildren = aiNode.mChildren();
        if (pChildren != null) {
            int numChildren = aiNode.mNumChildren();
            for (int childIndex = 0; childIndex < numChildren; ++childIndex) {
                long handle = pChildren.get(childIndex);
                AINode aiChild = AINode.createSafe(handle);
                Node jmeChild = convertNode(aiChild, materialList, pMeshes);
                result.attachChild(jmeChild);
            }
        }

        return result;
    }

    /**
     * Convert the specified Assimp textures into JMonkeyEngine textures.
     *
     * @param pTextures the Assimp textures to convert (not null, unaffected)
     * @return a new list of new instances
     */
    static List<Texture> convertTextures(PointerBuffer pTextures) {
        int numTextures = pTextures.capacity();
        if (numTextures > 0) {
            System.out.println("numTextures = " + numTextures);
        }
        List<Texture> result = new ArrayList<>(numTextures);
        for (int i = 0; i < numTextures; ++i) {
            long handle = pTextures.get(i);
            AITexture aiTexture = AITexture.createSafe(handle);
            Texture jmeTexture = convertTexture(aiTexture);
            result.add(jmeTexture);
        }

        return result;
    }
    // *************************************************************************
    // AssetLoader methods

    /**
     * Load a model/scene asset using a virtual filesystem.
     *
     * @param info the located asset (not null)
     * @return a new instance (not null)
     * @throws IOException if lwjgl-assimp fails to import a model/scene
     */
    @Override
    public Object load(AssetInfo info) throws IOException {
        AssetKey assetKey = info.getKey();
        if (assetKey == null) {
            throw new IllegalArgumentException("AssetInfo lacks a key!");
        }
        LwjglAssetKey key;
        if (assetKey instanceof LwjglAssetKey) {
            key = (LwjglAssetKey) assetKey;
        } else {
            key = new LwjglAssetKey(assetKey);
        }

        if (key.isVerboseLogging()) {
            String logFilename = null;
            AILogStream logStream = AILogStream.create();
            logStream = Assimp.aiGetPredefinedLogStream(
                    Assimp.aiDefaultLogStream_STDOUT, logFilename, logStream);
            Assimp.aiAttachLogStream(logStream);
            Assimp.aiEnableVerboseLogging(true);
        }

        // Create a temporary virtual filesystem:
        AssetManager assetManager = info.getManager();
        AssetFileSystem tempFileSystem = new AssetFileSystem(assetManager);
        AIFileIO aiFileIo = tempFileSystem.getAccess();

        int flags = key.flags();
        String name = key.getName();
        AIScene aiScene = Assimp.aiImportFileEx(name, flags, aiFileIo);
        Assimp.aiDetachAllLogStreams();

        if (aiScene == null || aiScene.mRootNode() == null) {
            // Report the error:
            String quotedName = MyString.quote(name);
            String errorString = Assimp.aiGetErrorString();
            String message = String.format(
                    "Assimp failed to import a C-G model from %s:%n %s",
                    quotedName, errorString);
            throw new IOException(message);
        }

        // Convert the embedded textures, if any:
        List<Texture> textureList = new ArrayList<>(1); // empty list
        PointerBuffer pTextures = aiScene.mTextures();
        if (pTextures != null) {
            textureList = convertTextures(pTextures);

            // Use placeholders for now - TODO remove this temporary hack
            Image im = PlaceholderAssets.getPlaceholderImage(assetManager);
            Texture t = new Texture2D(im);
            for (int i = 0; i < textureList.size(); ++i) {
                textureList.set(i, t);
            }
        }

        // Convert the materials:
        List<Material> materialList = new ArrayList<>(1); // empty list
        PointerBuffer pMaterials = aiScene.mMaterials();
        if (pMaterials != null) {
            String assetFolder = key.getFolder();
            materialList = convertMaterials(
                    pMaterials, assetManager, assetFolder, textureList);
        }

        // Convert the nodes and meshes:
        AINode rootNode = aiScene.mRootNode();
        PointerBuffer pMeshes = aiScene.mMeshes();
        Node result = convertNode(rootNode, materialList, pMeshes);

        tempFileSystem.destroy();

        return result;
    }
    // *************************************************************************
    // private methods

    /**
     * Add a color buffer to the specified JMonkeyEngine mesh.
     *
     * @param pAiColors the buffer to copy vertex colors from (not null,
     * unaffected)
     * @param jmeMesh the JMonkeyEngine mesh to modify (not null)
     */
    private static void addColorBuffer(
            AIColor4D.Buffer pAiColors, Mesh jmeMesh) {
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

        VertexBuffer colors = new VertexBuffer(VertexBuffer.Type.Color);
        colors.setupData(VertexBuffer.Usage.Static, 4,
                VertexBuffer.Format.Float, floats);
        jmeMesh.setBuffer(colors);
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
            AIFace.Buffer pFaces, int vertexCount, int vpf, Mesh jmeMesh) {
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
                throw new AssetLoadException(message);
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
     * Add a normal buffer to the specified JMonkeyEngine mesh.
     *
     * @param pAiNormals the buffer to copy vertex normals from (not null,
     * unaffected)
     * @param jmeMesh the JMonkeyEngine mesh to modify (not null)
     */
    private static void addNormalBuffer(
            AIVector3D.Buffer pAiNormals, Mesh jmeMesh) {
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

        VertexBuffer vertexBuffer
                = new VertexBuffer(VertexBuffer.Type.Normal);
        vertexBuffer.setupData(VertexBuffer.Usage.Static, MyVector3f.numAxes,
                VertexBuffer.Format.Float, floats);
        jmeMesh.setBuffer(vertexBuffer);
    }

    /**
     * Add a position buffer to the specified JMonkeyEngine mesh.
     *
     * @param pAiPositions the buffer to copy vertex positions from (not null,
     * unaffected)
     * @param jmeMesh the JMonkeyEngine mesh to modify (not null)
     * @return the number of vertices in the mesh (&gt;0)
     */
    private static int addPositionBuffer(
            AIVector3D.Buffer pAiPositions, Mesh jmeMesh) {
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

        VertexBuffer vertexBuffer
                = new VertexBuffer(VertexBuffer.Type.Position);
        vertexBuffer.setupData(VertexBuffer.Usage.Static, MyVector3f.numAxes,
                VertexBuffer.Format.Float, floats);
        jmeMesh.setBuffer(vertexBuffer);

        return numVertices;
    }

    /**
     * Add a tangent buffer to the specified JMonkeyEngine mesh.
     *
     * @param pAiTangents the buffer to copy vertex tangents from (not null,
     * unaffected)
     * @param jmeMesh the JMonkeyEngine mesh to modify (not null)
     */
    private static void addTangentBuffer(
            AIVector3D.Buffer pAiTangents, Mesh jmeMesh) {
        int numVertices = pAiTangents.capacity();
        FloatBuffer floats = BufferUtils.createVector3Buffer(numVertices);

        for (int vertexIndex = 0; vertexIndex < numVertices; ++vertexIndex) {
            AIVector3D tangent = pAiTangents.get(vertexIndex);
            float x = tangent.x();
            float y = tangent.y();
            float z = tangent.z();
            floats.put(x).put(y).put(z);
        }
        floats.flip();

        VertexBuffer vertexBuffer
                = new VertexBuffer(VertexBuffer.Type.Tangent);
        vertexBuffer.setupData(VertexBuffer.Usage.Static, MyVector3f.numAxes,
                VertexBuffer.Format.Float, floats);
        jmeMesh.setBuffer(vertexBuffer);
    }

    /**
     * Add a texture-coordinates (UV) buffer to the specified JMonkeyEngine
     * mesh.
     *
     * @param pAiTexCoords the buffer to copy texture coordinates from (not
     * null, unaffected)
     * @param numComponents the number of (float) components in each set of
     * texture coordinates (&ge;1, &le;3)
     * @param vbType the type of vertex buffer to create (not null)
     * @param jmeMesh the JMonkeyEngine mesh to modify (not null)
     */
    private static void addTexCoordsBuffer(AIVector3D.Buffer pAiTexCoords,
            int numComponents, VertexBuffer.Type vbType, Mesh jmeMesh) {
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

        VertexBuffer vertexBuffer = new VertexBuffer(vbType);
        vertexBuffer.setupData(VertexBuffer.Usage.Static, numComponents,
                VertexBuffer.Format.Float, floats);
        jmeMesh.setBuffer(vertexBuffer);
    }

    /**
     * Convert the specified {@code AIMesh} into a JMonkeyEngine material.
     *
     * @param aiMesh the Assimp mesh to convert (not null, unaffected)
     * @return a new instance (not null)
     */
    private static Mesh convertMesh(AIMesh aiMesh) {
        Mesh result = new Mesh();
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
                throw new AssetLoadException(
                        "Unsupported primitive in mesh, meshType=" + meshType);
        }

        AIVector3D.Buffer pAiBitangents = aiMesh.mBitangents();
        if (pAiBitangents != null) {
            logger.warning("JMonkeyEngine doesn't support "
                    + "vertex bitangents - ignored.");
        }
        AIColor4D.Buffer pAiColors = aiMesh.mColors(1);
        if (pAiColors != null) {
            logger.warning("JMonkeyEngine doesn't support "
                    + "multiple vertex colors - ignored.");
        }

        //int numBones = aiMesh.mNumBones();
        AIVector3D.Buffer pAiPositions = aiMesh.mVertices();
        int vertexCount = addPositionBuffer(pAiPositions, result);

        pAiColors = aiMesh.mColors(0);
        if (pAiColors != null) {
            assert pAiColors.capacity() == vertexCount : pAiColors.capacity();
            addColorBuffer(pAiColors, result);
        }

        AIVector3D.Buffer pAiNormals = aiMesh.mNormals();
        if (pAiNormals != null) {
            assert pAiNormals.capacity() == vertexCount : pAiNormals.capacity();
            addNormalBuffer(pAiNormals, result);
        }

        AIVector3D.Buffer pAiTangents = aiMesh.mTangents();
        if (pAiTangents != null) {
            assert pAiTangents.capacity() == vertexCount :
                    pAiTangents.capacity();
            addTangentBuffer(pAiTangents, result);
        }

        IntBuffer pNumComponents = aiMesh.mNumUVComponents();
        PointerBuffer ppAiTexCoords = aiMesh.mTextureCoords();
        if (pNumComponents != null && ppAiTexCoords != null) {
            int maxUvChannels = Math.min(pNumComponents.capacity(),
                    ppAiTexCoords.capacity());
            for (int channelI = 0; channelI < maxUvChannels; ++channelI) {
                AIVector3D.Buffer pAiTexCoords
                        = aiMesh.mTextureCoords(channelI);
                if (pAiTexCoords != null) {
                    int numComponents = pNumComponents.get(channelI);
                    VertexBuffer.Type vbType = uvType(channelI);
                    addTexCoordsBuffer(
                            pAiTexCoords, numComponents, vbType, result);
                }
            }
        }

        AIFace.Buffer pFaces = aiMesh.mFaces();
        addIndexBuffer(pFaces, vertexCount, vpp, result);

        result.updateCounts();
        result.updateBound();

        return result;
    }

    /**
     * Convert the specified {@code AITexture} into a JMonkeyEngine texture.
     *
     * @param aiTexture the Assimp texture to convert (not null, unaffected)
     * @return a new instance (not null)
     */
    private static Texture convertTexture(AITexture aiTexture) {
        Texture result = new Texture2D();

        //AIString filename = aiTexture.mFilename();
        //ByteBuffer formatHint = aiTexture.achFormatHint();
        AITexel.Buffer pcData = aiTexture.pcData();
        int height = aiTexture.mHeight();
        if (height == 0) { // compressed image
            //System.out.println("Compressed image");
        } else {
            int width = aiTexture.mWidth();
            for (int y = 0; y < height; ++y) {
                for (int x = 0; x < width; ++x) {
                    int index = x + width * y;
                    AITexel texel = pcData.get(index);
                    // TODO not implemented yet
                }
            }
        }
        logger.log(Level.WARNING, "Texture not converted.");

        return result;
    }

    /**
     * Convert a texture-coordinate channel into a JMonkeyEngine vertex-buffer
     * type.
     *
     * @param channelIndex which channel (&ge;0, &lt;8)
     * @return an enum value (not null)
     */
    private static VertexBuffer.Type uvType(int channelIndex) {
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
                throw new AssetLoadException("Too many texture-coordinate "
                        + "channels in mesh, channelIndex=" + channelIndex);
        }

        return result;
    }
}
