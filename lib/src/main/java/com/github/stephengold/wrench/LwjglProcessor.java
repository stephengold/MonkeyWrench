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
import com.jme3.anim.MorphControl;
import com.jme3.anim.SkinningControl;
import com.jme3.asset.AssetManager;
import com.jme3.light.Light;
import com.jme3.material.Material;
import com.jme3.math.Transform;
import com.jme3.scene.CameraNode;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.control.LightControl;
import com.jme3.texture.Texture;
import com.jme3.util.mikktspace.MikktspaceTangentGenerator;
import java.io.IOException;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import jme3utilities.Heart;
import jme3utilities.MySpatial;
import jme3utilities.MyString;
import org.lwjgl.PointerBuffer;
import org.lwjgl.assimp.AIAnimation;
import org.lwjgl.assimp.AICamera;
import org.lwjgl.assimp.AILight;
import org.lwjgl.assimp.AIMaterial;
import org.lwjgl.assimp.AIMatrix4x4;
import org.lwjgl.assimp.AIMesh;
import org.lwjgl.assimp.AIMetaData;
import org.lwjgl.assimp.AINode;
import org.lwjgl.assimp.AIScene;
import org.lwjgl.assimp.Assimp;

/**
 * Process data that has been imported by lwjgl-assimp.
 *
 * @author Stephen Gold sgold@sonic.net
 */
class LwjglProcessor {
    // *************************************************************************
    // constants and loggers

    /**
     * message logger for this class
     */
    final private static Logger logger
            = Logger.getLogger(LwjglProcessor.class.getName());
    // *************************************************************************
    // fields

    /**
     * data imported by lwjgl-assimp
     */
    final private AIScene aiScene;
    /**
     * true if the imported data structure is a complete scene, otherwise false
     */
    private boolean isComplete;
    /**
     * true if verbose logging is requested, otherwise false
     */
    final private boolean verboseLogging;
    /**
     * true if the scene has Z-up orientation, otherwise false
     */
    private boolean zUp;
    /**
     * post-processing flags that were passed to {@code aiImportFile()}
     */
    final private int loadFlags;
    /**
     * builder for each material in the AIScene
     */
    final private List<MaterialBuilder> builderList;
    // *************************************************************************
    // constructors

    /**
     * Instantiate a processor to process the specified AIScene.
     *
     * @param aiScene the imported data (not null, alias created)
     * @param loadFlags the post-processing flags that were passed to
     * {@code aiImportFile()}
     * @param verboseLogging true if verbose logging was requested, otherwise
     * false
     * @throws IOException if the AIScene metadata cannot be processed
     */
    LwjglProcessor(AIScene aiScene, int loadFlags, boolean verboseLogging)
            throws IOException {
        this.aiScene = aiScene;
        this.loadFlags = loadFlags;
        this.verboseLogging = verboseLogging;

        int numMaterials = aiScene.mNumMaterials();
        this.builderList = new ArrayList<>(numMaterials);
        processFlagsAndMetadata();
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Convert the materials in the AIScene to builders.
     *
     * @param assetManager for loading textures (not null)
     * @param assetFolder the asset path to the folder from which the
     * model/scene was loaded (not null)
     * @param embeddedTextures the array of embedded textures (not null)
     * @throws IOException if the materials cannot be converted
     */
    void convertMaterials(AssetManager assetManager, String assetFolder,
            Texture[] embeddedTextures) throws IOException {
        PointerBuffer pMaterials = aiScene.mMaterials();

        int numMaterials = aiScene.mNumMaterials();
        for (int i = 0; i < numMaterials; ++i) {
            long handle = pMaterials.get(i);
            AIMaterial aiMaterial = AIMaterial.createSafe(handle);
            MaterialBuilder builder = new MaterialBuilder(
                    aiMaterial, i, assetManager, assetFolder, embeddedTextures,
                    loadFlags, verboseLogging);
            builderList.add(builder);
        }
    }

    /**
     * Test whether the imported data structure is a complete scene.
     *
     * @return true if complete, otherwise false
     */
    boolean isComplete() {
        return isComplete;
    }

    /**
     * Complete the conversion of an incomplete AIScene into a JMonkeyEngine
     * node with an AnimComposer and a SkinningControl.
     *
     * @return a new orphan node (not null)
     * @throws IOException if the AIScene cannot be converted to a Node with an
     * AnimComposer and a SkinningControl
     */
    Node toAnimationNode() throws IOException {
        int numAnimations = aiScene.mNumAnimations();
        if (numAnimations < 1) {
            throw new IOException("No animations found.");
        }

        int numCameras = aiScene.mNumCameras();
        if (numCameras != 0) {
            logger.log(Level.WARNING, "Ignoring {0} camera{1}.",
                    new Object[]{numCameras, (numCameras == 1) ? "" : "s"});
        }

        int numLights = aiScene.mNumLights();
        if (numLights != 0) {
            logger.log(Level.WARNING, "Ignoring {0} lights{1}",
                    new Object[]{numLights, (numLights == 1) ? "" : "s"});
        }

        // Create the result Node:
        AINode rootNode = aiScene.mRootNode();
        //LwjglReader.dumpNodes(rootNode, "");
        String nodeName = rootNode.mName().dataString();
        Node result = new Node(nodeName);

        // Traverse the node tree to build and add the SkinningControl:
        SkinnerBuilder skinnerBuilder = new SkinnerBuilder();
        skinnerBuilder.createJoints(rootNode);
        skinnerBuilder.buildAndAddTo(result);

        // Build and add the AnimComposer:
        PointerBuffer pAnimations = aiScene.mAnimations();
        addAnimComposer(numAnimations, pAnimations, result);

        return result;
    }

    /**
     * Complete the conversion of a complete AIScene into a JMonkeyEngine
     * scene-graph subtree.
     * <p>
     * Before invoking this method, any materials in the AIScene should've
     * already been converted to builders.
     *
     * @return a new scene-graph subtree (not null)
     * @throws IOException if the AIScene cannot be converted
     */
    Node toSceneGraph() throws IOException {
        if (!isComplete) {
            throw new IOException("Not a complete scene.");
        }

        // Convert each AIMesh to a Geometry:
        int numMeshes = aiScene.mNumMeshes();
        PointerBuffer pMeshes = aiScene.mMeshes();
        SkinnerBuilder skinnerBuilder = new SkinnerBuilder();
        Geometry[] geometryArray
                = convertMeshes(numMeshes, pMeshes, skinnerBuilder);

        // Traverse the node tree to generate the scene-graph hierarchy:
        AINode rootNode = aiScene.mRootNode();
        //LwjglReader.dumpNodes(rootNode, "");
        Node result = convertSubtree(rootNode, geometryArray, skinnerBuilder);

        // If necessary, create a SkinningControl and add it to the result:
        SkinningControl skinner = skinnerBuilder.buildAndAddTo(result);

        // Convert animations (if any) to a composer and add it to the scene:
        int numAnimations = aiScene.mNumAnimations();
        if (numAnimations > 0) {
            PointerBuffer pAnimations = aiScene.mAnimations();
            addAnimComposer(numAnimations, pAnimations, result);

        } else { // No animations, add MorphControl if there are morph targets:
            for (Geometry geometry : MySpatial.listGeometries(result)) {
                Mesh mesh = geometry.getMesh();
                if (mesh.hasMorphTargets()) {
                    MorphControl morphControl = new MorphControl();
                    result.addControl(morphControl);
                    break;
                }
            }
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

        // Add a parent Node where external transforms can be safely applied:
        String sceneName = aiScene.mName().dataString();
        Node sceneNode = new Node(sceneName);
        sceneNode.attachChild(result);
        result = sceneNode;

        return result;
    }

    /**
     * Test whether the model/scene has Z-up orientation.
     *
     * @return true if the orientation is Z-up, otherwise false
     */
    boolean zUp() {
        return zUp;
    }
    // *************************************************************************
    // private methods

    /**
     * Create an AnimComposer and add it to the specified Node.
     *
     * @param numAnimations the number of animations to convert (&ge;0)
     * @param pAnimations pointers to the animations (not null, unaffected)
     * @param jmeRoot the root node of the converted scene graph (not null,
     * modified)
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
            String clipName = aiAnimation.mName().dataString();
            if (clipName == null || clipName.isEmpty()) {
                clipName = "anim_" + animIndex;
            }
            AnimClip animClip = ConversionUtils.convertAnimation(
                    aiAnimation, clipName, armature, jmeRoot);
            composer.addAnimClip(animClip);
        }
        /*
         * The order of scene-graph controls matters, especially during updates.
         * For best results, the AnimComposer should come *before*
         * the MorphControl or SkinningControl, if any:
         */
        jmeRoot.addControlAt(0, composer);
    }

    /**
     * Create camera nodes and add them to the specified Spatial.
     *
     * @param numCameras the number of cameras to convert (&ge;0)
     * @param pCameras pointers to the cameras (not null, unaffected)
     * @param attachNodes where to attach the camera nodes (not null, modified)
     */
    private static void addCameras(int numCameras, PointerBuffer pCameras,
            Node attachNodes) throws IOException {
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
                    light = ConversionUtils.convertAmbientLight(aiLight);
                    break;
                    
                case Assimp.aiLightSource_AREA:
                case Assimp.aiLightSource_SPOT:
                    throw new IOException("MonkeyWrench doesn't handle "
                            + "this type of light source yet: " + lightType);

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
     * @param skinnerBuilder information about the model's bones (not null)
     * @return a new list of new instances
     */
    private Geometry[] convertMeshes(int numMeshes, PointerBuffer pMeshes,
            SkinnerBuilder skinnerBuilder) throws IOException {
        assert skinnerBuilder != null;

        Geometry[] result = new Geometry[numMeshes];
        for (int meshIndex = 0; meshIndex < numMeshes; ++meshIndex) {
            long handle = pMeshes.get(meshIndex);
            AIMesh aiMesh = AIMesh.createSafe(handle);
            MeshBuilder meshBuilder = new MeshBuilder(aiMesh, meshIndex);

            String name = meshBuilder.getName();
            Mesh jmeMesh = meshBuilder.createJmeMesh(skinnerBuilder);
            Geometry geometry = new Geometry(name, jmeMesh);

            float[] state = meshBuilder.getInitialMorphState();
            geometry.setMorphState(state);

            int materialIndex = aiMesh.mMaterialIndex();
            MaterialBuilder builder = builderList.get(materialIndex);
            Material material = builder.createJmeMaterial(jmeMesh);
            geometry.setMaterial(material);

            Texture normalMap = material.getParamValue("NormalMap");
            VertexBuffer tangentBuffer
                    = jmeMesh.getBuffer(VertexBuffer.Type.Tangent);
            if (normalMap != null && tangentBuffer == null) {
                System.out.println("Using Mikktspace to generate tangents.");
                MikktspaceTangentGenerator.generate(geometry);
            }

            result[meshIndex] = geometry;
        }

        return result;
    }

    /**
     * Create a JMonkeyEngine node that approximates the specified Assimp node.
     * Note: recursive!
     *
     * @param aiNode the root of the Assimp node tree to convert (not null,
     * unaffected)
     * @param geometryArray all geometries in the model/scene, indexed by Assimp
     * mesh index (not null)
     * @param skinnerBuilder information about the model's bones (not null)
     * @return a new instance (not null)
     */
    private Node convertSubtree(AINode aiNode, Geometry[] geometryArray,
            SkinnerBuilder skinnerBuilder) throws IOException {
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
                int numMeshesInSubtree
                        = LwjglReader.countMeshesInSubtree(aiChild);
                if (numMeshesInSubtree > 0) {
                    // Attach a child to the JMonkeyEngine scene-graph node:
                    Node jmeChild = convertSubtree(
                            aiChild, geometryArray, skinnerBuilder);
                    result.attachChild(jmeChild);

                } else { // Add a root joint to the armature:
                    skinnerBuilder.createJoints(aiChild);
                }
            }
        }

        AIMatrix4x4 transformation = aiNode.mTransformation();
        Transform transform = ConversionUtils.convertTransform(transformation);
        result.setLocalTransform(transform);
        //System.out.println("set " + nodeName + " local to " + transform);

        AIMetaData metadata = aiNode.mMetadata();
        if (metadata != null) {
            Map<String, Object> map = ConversionUtils.convertMetadata(metadata);

            if (verboseLogging) {
                System.out.println("Node metadata:");
                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    String mdKey = entry.getKey();
                    Object data = entry.getValue();
                    if (data instanceof String) {
                        String stringData = (String) data;
                        data = MyString.quote(stringData);
                    }
                    System.out.printf(" %s: %s%n", MyString.quote(mdKey), data);
                }
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
    private static Node getNode(String nodeName, SkinningControl skinner,
            Node jmeRoot) throws IOException {
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

    /**
     * Process the flags and metadata of the AIScene.
     */
    private void processFlagsAndMetadata() throws IOException {
        int sceneFlags = aiScene.mFlags();
        sceneFlags &= ~Assimp.AI_SCENE_FLAGS_NON_VERBOSE_FORMAT;

        this.isComplete = true;
        if ((sceneFlags & Assimp.AI_SCENE_FLAGS_INCOMPLETE) != 0x0) {
            this.isComplete = false;
            sceneFlags &= ~Assimp.AI_SCENE_FLAGS_INCOMPLETE;
        }
        if (sceneFlags != 0x0) {
            String hexString = Integer.toHexString(sceneFlags);
            System.out.println("Unexpected scene flags: 0x" + hexString);
        }

        this.zUp = false;
        AIMetaData metadata = aiScene.mMetaData();
        if (metadata != null) {
            Map<String, Object> map = ConversionUtils.convertMetadata(metadata);

            if (verboseLogging) {
                System.out.println("Scene metadata:");
            }
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                String mdKey = entry.getKey();
                Object data = entry.getValue();

                if (data instanceof String) {
                    String stringData = (String) data;
                    if (mdKey.equals("SourceAsset_Format")
                            && stringData.startsWith("Blender 3D")) {
                        this.zUp = true;
                    }
                    data = MyString.quote(stringData);
                }
                if (verboseLogging) {
                    System.out.printf(" %s: %s%n", MyString.quote(mdKey), data);
                }
            }
        }
    }
}
