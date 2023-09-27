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
import com.jme3.anim.AnimComposer;
import com.jme3.anim.Armature;
import com.jme3.anim.SkinningControl;
import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetManager;
import com.jme3.asset.DesktopAssetManager;
import com.jme3.asset.plugins.ClasspathLocator;
import com.jme3.asset.plugins.FileLocator;
import com.jme3.light.Light;
import com.jme3.material.Material;
import com.jme3.material.plugins.J3MLoader;
import com.jme3.math.FastMath;
import com.jme3.math.Transform;
import com.jme3.scene.CameraNode;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.mesh.IndexBuffer;
import com.jme3.texture.Texture;
import com.jme3.texture.plugins.AWTLoader;
import com.jme3.util.BufferUtils;
import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import jme3utilities.Heart;
import jme3utilities.MyControl;
import jme3utilities.MyString;
import jme3utilities.math.MyVector3f;
import org.lwjgl.PointerBuffer;
import org.lwjgl.assimp.AIAnimation;
import org.lwjgl.assimp.AIBone;
import org.lwjgl.assimp.AICamera;
import org.lwjgl.assimp.AIColor4D;
import org.lwjgl.assimp.AIFace;
import org.lwjgl.assimp.AILight;
import org.lwjgl.assimp.AILogStream;
import org.lwjgl.assimp.AIMaterial;
import org.lwjgl.assimp.AIMatrix4x4;
import org.lwjgl.assimp.AIMesh;
import org.lwjgl.assimp.AIMetaData;
import org.lwjgl.assimp.AINode;
import org.lwjgl.assimp.AIScene;
import org.lwjgl.assimp.AIVector3D;
import org.lwjgl.assimp.AIVertexWeight;
import org.lwjgl.assimp.Assimp;

/**
 * Read assets from the real filesystem using lwjgl-assimp.
 *
 * @author Stephen Gold sgold@sonic.net
 */
final public class LwjglReader {
    // *************************************************************************
    // constants and loggers

    /**
     * message logger for this class
     */
    final private static Logger logger
            = Logger.getLogger(LwjglReader.class.getName());
    // *************************************************************************
    // constructors

    /**
     * A private constructor to inhibit instantiation of this class.
     */
    private LwjglReader() {
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
     * @param embeddedTextures the array of embedded textures (not null)
     * @return a new list of new instances
     */
    static List<Material> convertMaterials(
            PointerBuffer pMaterials, AssetManager assetManager,
            String assetFolder, Texture[] embeddedTextures) throws IOException {
        int numMaterials = pMaterials.capacity();
        List<Material> result = new ArrayList<>(numMaterials);

        for (int i = 0; i < numMaterials; ++i) {
            long handle = pMaterials.get(i);
            AIMaterial aiMaterial = AIMaterial.createSafe(handle);
            MaterialBuilder builder = new MaterialBuilder(aiMaterial,
                    assetManager, assetFolder, embeddedTextures);
            Material jmeMaterial = builder.createJmeMaterial();
            result.add(jmeMaterial);
        }

        return result;
    }

    /**
     * Log importer progress to the standard output.
     * <p>
     * Remember to invoke {@code Assimp.aiDetachAllLogStreams()} when done
     * importing the mode/scene!
     */
    static void enableVerboseLogging() {
        String logFilename = null;
        AILogStream logStream = AILogStream.create();
        logStream = Assimp.aiGetPredefinedLogStream(
                Assimp.aiDefaultLogStream_STDOUT, logFilename, logStream);
        Assimp.aiAttachLogStream(logStream);

        Assimp.aiEnableVerboseLogging(true);
    }

    /**
     * Process the flags and metadata of the specified AIScene.
     *
     * @param aiScene the scene returned by {@code aiImportFile()} (not null)
     * @return true if the scene has Z-up orientation, otherwise false
     */
    static boolean processFlagsAndMetadata(AIScene aiScene) throws IOException {
        int sceneFlags = aiScene.mFlags();
        sceneFlags &= ~Assimp.AI_SCENE_FLAGS_NON_VERBOSE_FORMAT;
        if ((sceneFlags & Assimp.AI_SCENE_FLAGS_INCOMPLETE) != 0x0) {
            logger.warning("The imported scene data is incomplete!");
            sceneFlags &= ~Assimp.AI_SCENE_FLAGS_INCOMPLETE;
        }
        if (sceneFlags != 0x0) {
            String hexString = Integer.toHexString(sceneFlags);
            System.out.println("Unexpected scene flags: 0x" + hexString);
        }

        boolean result = false;
        AIMetaData metadata = aiScene.mMetaData();
        if (metadata != null) {
            Map<String, Object> map = ConversionUtils.convertMetadata(metadata);

            System.out.println("Scene metadata:");
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                String mdKey = entry.getKey();
                Object data = entry.getValue();

                if (data instanceof String) {
                    String stringData = (String) data;
                    if (mdKey.equals("SourceAsset_Format")
                            && stringData.startsWith("Blender 3D")) {
                        result = true;
                    }
                    data = MyString.quote(stringData);
                }

                System.out.printf(" %s: %s%n", MyString.quote(mdKey), data);
            }
        }

        return result;
    }

    /**
     * Read a model/scene from the real filesystem.
     *
     * @param filename the filesystem path to the model/scene file (not null)
     * @param verboseLogging true to enable verbose logging, otherwise false
     * @param loadFlags post-processing flags to be passed to
     * {@code aiImportFile()}
     * @return a new scene-graph subtree (not null)
     * @throws IOException if lwjgl-assimp fails to import a model/scene
     */
    public static Spatial readCgm(
            String filename, boolean verboseLogging, int loadFlags)
            throws IOException {
        if (verboseLogging) {
            enableVerboseLogging();
        }

        AIScene aiScene = Assimp.aiImportFile(filename, loadFlags);
        Assimp.aiDetachAllLogStreams();

        if (aiScene == null || aiScene.mRootNode() == null) {
            // Report the error:
            String quotedName = MyString.quote(filename);
            String errorString = Assimp.aiGetErrorString();
            String message = String.format(
                    "Assimp failed to import a model/scene from %s:%n %s",
                    quotedName, errorString);
            throw new IOException(message);
        }

        boolean zUp = processFlagsAndMetadata(aiScene);

        // Convert the embedded textures, if any:
        Texture[] textureArray = new Texture[0];
        int numTextures = aiScene.mNumTextures();
        if (numTextures > 0) {
            PointerBuffer pTextures = aiScene.mTextures();
            textureArray = ConversionUtils.convertTextures(pTextures);
        }

        // Convert the materials:
        List<Material> materialList = new ArrayList<>(1); // empty list
        int numMaterials = aiScene.mNumMaterials();
        if (numMaterials > 0) {
            PointerBuffer pMaterials = aiScene.mMaterials();
            /*
             * Create a temporary AssetManager for loading
             * material definitions and non-embedded textures:
             */
            DesktopAssetManager assetManager = new DesktopAssetManager();
            assetManager.registerLocator("/", FileLocator.class);
            assetManager.registerLocator("/", ClasspathLocator.class);
            assetManager.registerLoader(
                    AWTLoader.class, "bmp", "gif", "jpg", "jpeg", "png");
            assetManager.registerLoader(J3MLoader.class, "j3md");

            String assetPath = Heart.fixPath(filename);
            AssetKey key = new AssetKey(assetPath);
            String assetFolder = key.getFolder();
            materialList = convertMaterials(
                    pMaterials, assetManager, assetFolder, textureArray);
        }

        Node result = toSceneGraph(aiScene, materialList);
        if (zUp) {
            // Rotate to JMonkeyEngine's Y-up orientation.
            result.rotate(-FastMath.HALF_PI, 0f, 0f);
        }

        return result;
    }

    /**
     * Finish converting the specified AIScene to a JMonkeyEngine scene-graph
     * subtree.
     *
     * @param aiScene the AIScene being converted (not null)
     * @param materialList the list of converted materials (not null)
     * @return a new scene-graph subtree (not null)
     */
    static Node toSceneGraph(AIScene aiScene, List<Material> materialList)
            throws IOException {
        assert aiScene != null;
        assert materialList != null;

        // Convert each AIMesh to a Geometry:
        int numMeshes = aiScene.mNumMeshes();
        PointerBuffer pMeshes = aiScene.mMeshes();
        SkinnerBuilder skinnerBuilder = new SkinnerBuilder();
        Geometry[] geometryArray = convertMeshes(
                numMeshes, pMeshes, materialList, skinnerBuilder);

        // Traverse the node tree to generate the scene-graph hierarchy:
        AINode rootNode = aiScene.mRootNode();
        Node result = convertNode(
                rootNode, materialList, geometryArray, skinnerBuilder);

        // If necessary, create a SkinningControl and add it to the result:
        skinnerBuilder.buildAndAddTo(result);

        // Convert animations (if any) to a composer and add it to the scene:
        int numAnimations = aiScene.mNumAnimations();
        if (numAnimations > 0) {
            PointerBuffer pAnimations = aiScene.mAnimations();
            addAnimComposer(numAnimations, pAnimations, geometryArray, result);
        }

        // Convert cameras (if any) and add them to the scene:
        int numCameras = aiScene.mNumCameras();
        if (numCameras > 0) {
            PointerBuffer pCameras = aiScene.mCameras();
            addCameras(numCameras, pCameras, result);
        }

        // Convert lights (if any) and add them to the scene:
        int numLights = aiScene.mNumLights();
        if (numLights > 0) {
            PointerBuffer pLights = aiScene.mLights();
            addLights(numLights, pLights, result);
        }

        return result;
    }
    // *************************************************************************
    // private methods

    /**
     * Create an AnimComposer and add it to the specified Spatial.
     *
     * @param numAnimations the number of animations to convert (&ge;0)
     * @param pAnimations pointers to the animations (not null, unaffected)
     * @param geometryArray all geometries in the model/scene (not null)
     * @param addControl where to add the control (not null, modified)
     */
    private static void addAnimComposer(
            int numAnimations, PointerBuffer pAnimations,
            Geometry[] geometryArray, Spatial addControl) throws IOException {
        assert geometryArray != null;
        assert addControl != null;

        SkinningControl skinner = addControl.getControl(SkinningControl.class);
        Armature armature = (skinner == null) ? null : skinner.getArmature();

        AnimComposer composer = new AnimComposer();
        for (int animIndex = 0; animIndex < numAnimations; ++animIndex) {
            long handle = pAnimations.get(animIndex);
            AIAnimation aiAnimation = AIAnimation.createSafe(handle);
            AnimClip animClip = ConversionUtils.convertAnimation(
                    aiAnimation, armature, geometryArray);
            composer.addAnimClip(animClip);
        }
        /*
         * The order of scene-graph controls matters, especially during updates.
         * For best results, the AnimComposer should come *before*
         * the SkinningControl, if any:
         */
        if (skinner == null) {
            addControl.addControl(composer);
        } else {
            int skinnerIndex = MyControl.findIndex(skinner, addControl);
            assert skinnerIndex >= 0 : skinnerIndex;
            addControl.addControlAt(skinnerIndex, composer);
        }
    }

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
     * Create camera nodes and add them to the specified Spatial.
     *
     * @param numCameras the number of cameras to convert (&ge;0)
     * @param pCameras pointers to the cameras (not null, unaffected)
     * @param attachNodes where to attach the camera nodes (not null, modified)
     */
    private static void addCameras(
            int numCameras, PointerBuffer pCameras, Node attachNodes)
            throws IOException {
        assert attachNodes != null;

        for (int cameraIndex = 0; cameraIndex < numCameras; ++cameraIndex) {
            long handle = pCameras.get(cameraIndex);
            AICamera aiCamera = AICamera.createSafe(handle);
            CameraNode cameraNode = ConversionUtils.convertCamera(aiCamera);
            attachNodes.attachChild(cameraNode);
        }
    }

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
     * Create lights and add them to the specified Spatial.
     *
     * @param numLights the number of lights to convert (&ge;0)
     * @param pLights pointers to the cameras (not null, unaffected)
     * @param addLights where to add the cameras (not null, modified)
     */
    private static void addLights(
            int numLights, PointerBuffer pLights, Spatial addLights)
            throws IOException {
        assert addLights != null;

        for (int lightIndex = 0; lightIndex < numLights; ++lightIndex) {
            long handle = pLights.get(lightIndex);
            AILight aiLight = AILight.createSafe(handle);

            Light light;
            int lightType = aiLight.mType();
            switch (lightType) {
                case Assimp.aiLightSource_POINT:
                    light = ConversionUtils.convertPointLight(aiLight);
                    break;

                case Assimp.aiLightSource_AMBIENT:
                case Assimp.aiLightSource_AREA:
                case Assimp.aiLightSource_DIRECTIONAL:
                case Assimp.aiLightSource_SPOT:
                    throw new IOException(
                            "Light type not handled yet: " + lightType);

                default:
                    throw new IOException(
                            "Unrecognized light type: " + lightType);
            }
            addLights.addLight(light);
        }
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
     * Convert the specified {@code AIMesh} into a JMonkeyEngine mesh.
     *
     * @param aiMesh the Assimp mesh to convert (not null, unaffected)
     * @param skinnerBuilder information about the model's bones (not null)
     * @return a new instance (not null)
     */
    private static Mesh convertMesh(
            AIMesh aiMesh, SkinnerBuilder skinnerBuilder) throws IOException {
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
     * Convert the specified Assimp meshes into JMonkeyEngine geometries.
     *
     * @param numMeshes the number of meshes to convert (&ge;0)
     * @param pMeshes pointers to the meshes to convert (not null, unaffected)
     * @param materialList the list of converted materials (not null, aliases
     * created)
     * @param skinnerBuilder information about the model's bones (not null)
     * @return a new list of new instances
     */
    private static Geometry[] convertMeshes(
            int numMeshes, PointerBuffer pMeshes, List<Material> materialList,
            SkinnerBuilder skinnerBuilder) throws IOException {
        assert materialList != null;
        assert skinnerBuilder != null;

        Geometry[] result = new Geometry[numMeshes];
        for (int meshIndex = 0; meshIndex < numMeshes; ++meshIndex) {
            long handle = pMeshes.get(meshIndex);
            AIMesh aiMesh = AIMesh.createSafe(handle);

            String meshName = aiMesh.mName().dataString();
            Mesh jmeMesh = convertMesh(aiMesh, skinnerBuilder);
            Geometry geometry = new Geometry(meshName, jmeMesh);

            int materialIndex = aiMesh.mMaterialIndex();
            Material material = materialList.get(materialIndex);
            geometry.setMaterial(material);

            result[meshIndex] = geometry;
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
     * @param geometryArray all geometries in the model/scene, indexed by Assimp
     * mesh index (not null)
     * @param skinnerBuilder information about the model's bones (not null)
     * @return a new instance (not null)
     */
    private static Node convertNode(AINode aiNode, List<Material> materialList,
            Geometry[] geometryArray, SkinnerBuilder skinnerBuilder) {
        String nodeName = aiNode.mName().dataString();
        Node result = new Node(nodeName);

        int numMeshesInNode = aiNode.mNumMeshes();
        if (numMeshesInNode > 0) {
            IntBuffer pMeshIndices = aiNode.mMeshes();
            for (int i = 0; i < numMeshesInNode; ++i) {
                int meshId = pMeshIndices.get(i);
                Geometry geometry = geometryArray[meshId];
                result.attachChild(geometry);
            }
        }

        PointerBuffer pChildren = aiNode.mChildren();
        if (pChildren != null) {
            int numChildren = aiNode.mNumChildren();
            for (int childIndex = 0; childIndex < numChildren; ++childIndex) {
                long handle = pChildren.get(childIndex);
                AINode aiChild = AINode.createSafe(handle);
                String childName = aiChild.mName().dataString();
                int numMeshesInChild = aiChild.mNumMeshes();
                if (childName.isEmpty() || numMeshesInChild > 0) {
                    // Attach a child to the JMonkeyEngine scene-graph node:
                    Node jmeChild = convertNode(aiChild, materialList,
                            geometryArray, skinnerBuilder);
                    result.attachChild(jmeChild);

                } else { // Add a root joint to the armature:
                    skinnerBuilder.createJoints(aiChild);
                }
            }
        }

        AIMatrix4x4 transformation = aiNode.mTransformation();
        Transform transform = ConversionUtils.convertTransform(transformation);
        result.setLocalTransform(transform);

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
