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
import com.jme3.anim.Joint;
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
import com.jme3.scene.control.LightControl;
import com.jme3.texture.Texture;
import com.jme3.texture.plugins.AWTLoader;
import java.io.IOException;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import jme3utilities.Heart;
import jme3utilities.MyControl;
import jme3utilities.MySpatial;
import jme3utilities.MyString;
import org.lwjgl.PointerBuffer;
import org.lwjgl.assimp.AIAnimation;
import org.lwjgl.assimp.AICamera;
import org.lwjgl.assimp.AILight;
import org.lwjgl.assimp.AILogStream;
import org.lwjgl.assimp.AIMaterial;
import org.lwjgl.assimp.AIMatrix4x4;
import org.lwjgl.assimp.AIMesh;
import org.lwjgl.assimp.AIMetaData;
import org.lwjgl.assimp.AINode;
import org.lwjgl.assimp.AIScene;
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
     * @throws IOException if lwjgl-assimp fails to import a model/scene or if
     * the imported model/scene cannot be converted to a scene graph
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
     * @throws IOException if the AIScene cannot be converted to a scene graph
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
        SkinningControl skinner = skinnerBuilder.buildAndAddTo(result);

        // Convert animations (if any) to a composer and add it to the scene:
        int numAnimations = aiScene.mNumAnimations();
        if (numAnimations > 0) {
            PointerBuffer pAnimations = aiScene.mAnimations();
            addAnimComposer(numAnimations, pAnimations, result);
        }

        // Convert cameras (if any) to camera nodes and add them to the scene:
        int numCameras = aiScene.mNumCameras();
        if (numCameras > 0) {
            PointerBuffer pCameras = aiScene.mCameras();
            addCameras(numCameras, pCameras, result);
        }

        // Convert lights (if any) and add them to the scene:
        int numLights = aiScene.mNumLights();
        if (numLights > 0) {
            PointerBuffer pLights = aiScene.mLights();
            addLights(numLights, pLights, skinner, result);
        }

        return result;
    }
    // *************************************************************************
    // private methods

    /**
     * Create an AnimComposer and add it to the specified Node.
     *
     * @param numAnimations the number of animations to convert (&ge;0)
     * @param pAnimations pointers to the animations (not null, unaffected)
     * @param jmeRoot the root node of the converted scene graph (not null)
     */
    private static void addAnimComposer(int numAnimations,
            PointerBuffer pAnimations, Node jmeRoot) throws IOException {
        assert jmeRoot != null;

        List<SkinningControl> list
                = MySpatial.listControls(jmeRoot, SkinningControl.class, null);
        SkinningControl skinner = (list.size() == 1) ? Heart.first(list) : null;
        Armature armature = (skinner == null) ? null : skinner.getArmature();

        AnimComposer composer = new AnimComposer();
        for (int animIndex = 0; animIndex < numAnimations; ++animIndex) {
            long handle = pAnimations.get(animIndex);
            AIAnimation aiAnimation = AIAnimation.createSafe(handle);
            AnimClip animClip = ConversionUtils.convertAnimation(
                    aiAnimation, armature, jmeRoot);
            composer.addAnimClip(animClip);
        }
        /*
         * The order of scene-graph controls matters, especially during updates.
         * For best results, the AnimComposer should come *before*
         * the SkinningControl, if any:
         */
        if (skinner == null) {
            jmeRoot.addControl(composer);

        } else {
            int skinnerIndex = MyControl.findIndex(skinner, jmeRoot);
            if (skinnerIndex >= 0) {
                jmeRoot.addControlAt(skinnerIndex, composer);
            } else {
                jmeRoot.addControl(composer);
            }
        }
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
     * Create lights and attach them to the root of the scene graph.
     *
     * @param numLights the number of lights to convert (&ge;0)
     * @param pLights pointers to the lights (not null, unaffected)
     * @param skinner (may be null)
     * @param jmeRoot the root node of the converted scene graph (not null)
     */
    private static void addLights(int numLights, PointerBuffer pLights,
            SkinningControl skinner, Node jmeRoot) throws IOException {
        assert jmeRoot != null;

        for (int lightIndex = 0; lightIndex < numLights; ++lightIndex) {
            long handle = pLights.get(lightIndex);
            AILight aiLight = AILight.createSafe(handle);

            String nodeName = aiLight.mName().dataString();
            assert nodeName != null;

            Light light;
            Node lightNode;
            Node parentNode;
            LightControl lightControl;
            int lightType = aiLight.mType();
            switch (lightType) {
                case Assimp.aiLightSource_POINT:
                    lightNode = ConversionUtils.convertPointLight(aiLight);
                    parentNode = getNode(nodeName, skinner, jmeRoot);
                    parentNode.attachChild(lightNode);
                    lightControl = lightNode.getControl(LightControl.class);
                    light = lightControl.getLight();
                    break;

                case Assimp.aiLightSource_DIRECTIONAL:
                    lightNode
                            = ConversionUtils.convertDirectionalLight(aiLight);
                    parentNode = getNode(nodeName, skinner, jmeRoot);
                    parentNode.attachChild(lightNode);
                    lightControl = lightNode.getControl(LightControl.class);
                    light = lightControl.getLight();
                    break;

                case Assimp.aiLightSource_AMBIENT:
                case Assimp.aiLightSource_AREA:
                case Assimp.aiLightSource_SPOT:
                    throw new IOException(
                            "Light type not handled yet: " + lightType);

                case Assimp.aiLightSource_UNDEFINED:
                    logger.warning(
                            "Skipped a light source with UNDEFINED type.");
                    continue;

                default:
                    throw new IOException(
                            "Unrecognized light type: " + lightType);
            }
            light.setName(nodeName);
            /*
             * In JMonkeyEngine, lights illuminate only a subtree of the scene
             * graph.  We add each light to the model's root node, so it will
             * illuminate the entire model:
             */
            jmeRoot.addLight(light);
        }
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
            Mesh jmeMesh = MeshBuilder.convertMesh(aiMesh, skinnerBuilder);
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
                Geometry geometry = geometryArray[meshId].clone();
                result.attachChild(geometry);
            }
        }

        PointerBuffer pChildren = aiNode.mChildren();
        if (pChildren != null) {
            int numChildren = aiNode.mNumChildren();
            for (int childIndex = 0; childIndex < numChildren; ++childIndex) {
                long handle = pChildren.get(childIndex);
                AINode aiChild = AINode.createSafe(handle);
                int numMeshesInSubtree = countMeshesInSubtree(aiChild);
                if (numMeshesInSubtree > 0) {
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
     * Count the meshes in the specified AINode and all its descendants. Note:
     * recursive!
     *
     * @param aiNode the node to process (not null, unaffected)
     * @return the count (&ge;0)
     */
    private static int countMeshesInSubtree(AINode aiNode) {
        int result = aiNode.mNumMeshes();

        int numChildren = aiNode.mNumChildren();
        if (numChildren > 0) {
            PointerBuffer pChildren = aiNode.mChildren();
            for (int childIndex = 0; childIndex < numChildren; ++childIndex) {
                long handle = pChildren.get(childIndex);
                AINode aiChild = AINode.createSafe(handle);
                result += countMeshesInSubtree(aiChild);
            }
        }

        return result;
    }

    /**
     * Return a model node for the named AINode, either a pre-existing node in
     * the converted model/scene or else an attachment node.
     *
     * @param nodeName the name to search for (not null)
     * @param skinner (may be null)
     * @param jmeRoot the root node of the converted model/scene (not null)
     * @return a Node in the converted model/scene (might be new)
     * @throws IOException if the name is not found
     */
    private static Node getNode(
            String nodeName, SkinningControl skinner, Node jmeRoot)
            throws IOException {
        assert nodeName != null;
        assert jmeRoot != null;

        if (skinner != null) { // Search for a Joint with the specified name:
            Joint joint = skinner.getArmature().getJoint(nodeName);
            if (joint != null) { // Find or create the joint's attachment node:
                Node result = skinner.getAttachmentsNode(nodeName);
                return result;
            }
        }

        List<Node> nodeList
                = MySpatial.listSpatials(jmeRoot, Node.class, null);
        for (Node node : nodeList) {
            String name = node.getName();
            if (nodeName.equals(name)) {
                return node;
            }
        }

        String qName = MyString.quote(nodeName);
        throw new IOException("Missing joint or node:  " + qName);
    }
}
