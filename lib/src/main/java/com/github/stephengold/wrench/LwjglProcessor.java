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
import com.jme3.anim.AnimTrack;
import com.jme3.anim.Armature;
import com.jme3.anim.Joint;
import com.jme3.anim.MorphControl;
import com.jme3.anim.MorphTrack;
import com.jme3.anim.SkinningControl;
import com.jme3.anim.TransformTrack;
import com.jme3.anim.util.HasLocalTransform;
import com.jme3.asset.AssetManager;
import com.jme3.light.Light;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.CameraNode;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.control.LightControl;
import com.jme3.texture.Texture;
import com.jme3.util.mikktspace.MikktspaceTangentGenerator;
import java.io.IOException;
import java.nio.DoubleBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import jme3utilities.MyMesh;
import jme3utilities.MySpatial;
import jme3utilities.MyString;
import jme3utilities.Validate;
import jme3utilities.wes.TransformTrackBuilder;
import org.lwjgl.PointerBuffer;
import org.lwjgl.assimp.AIAnimation;
import org.lwjgl.assimp.AICamera;
import org.lwjgl.assimp.AILight;
import org.lwjgl.assimp.AIMaterial;
import org.lwjgl.assimp.AIMatrix4x4;
import org.lwjgl.assimp.AIMesh;
import org.lwjgl.assimp.AIMeshMorphAnim;
import org.lwjgl.assimp.AIMeshMorphKey;
import org.lwjgl.assimp.AIMetaData;
import org.lwjgl.assimp.AINode;
import org.lwjgl.assimp.AINodeAnim;
import org.lwjgl.assimp.AIQuatKey;
import org.lwjgl.assimp.AIScene;
import org.lwjgl.assimp.AIVectorKey;
import org.lwjgl.assimp.Assimp;

/**
 * Process data imported into lwjgl-assimp to construct a JMonkeyEngine asset.
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
     * true if the loaded asset has Z-up orientation, otherwise false
     */
    private boolean zUp;
    /**
     * constructed Geometry for each AIMesh
     */
    final private Geometry[] geometryArray;
    /**
     * builder for each material in the AIScene
     */
    final private List<MaterialBuilder> builderList;
    /**
     * key used to load the main asset
     */
    final private LwjglAssetKey mainKey;
    /**
     * where animation controls will be added
     */
    private Node controlledNode;
    /**
     * root node of the asset under construction
     */
    private Node jmeRoot;
    /**
     * data used to construct the SkinningControl, if any
     */
    final private SkinnerBuilder skinnerBuilder = new SkinnerBuilder();
    /**
     * SkinningControl of the asset under construction, or null if none
     */
    private SkinningControl skinner;
    /**
     * name of the node to which animation controls will be added
     */
    private String controlledNodeName;
    // *************************************************************************
    // constructors

    /**
     * Instantiate a processor for the specified AIScene.
     *
     * @param aiScene the imported data (not null, alias created)
     * @param mainKey the key used to load the main asset (not null, unaffected)
     * @throws IOException if the AIScene metadata cannot be processed
     */
    LwjglProcessor(AIScene aiScene, LwjglAssetKey mainKey) throws IOException {
        this.aiScene = aiScene;
        this.mainKey = mainKey;

        int numMaterials = aiScene.mNumMaterials();
        this.builderList = new ArrayList<>(numMaterials);

        int numMeshes = aiScene.mNumMeshes();
        this.geometryArray = new Geometry[numMeshes];

        processFlagsAndMetadata();
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Convert all materials in the AIScene to builders.
     *
     * @param assetManager for loading textures (not null)
     * @param embeddedTextures the array of embedded textures (not null)
     * @throws IOException if the materials cannot be converted
     */
    void convertMaterials(AssetManager assetManager, Texture[] embeddedTextures)
            throws IOException {
        PointerBuffer pMaterials = aiScene.mMaterials();

        int numMaterials = aiScene.mNumMaterials();
        for (int i = 0; i < numMaterials; ++i) {
            long handle = pMaterials.get(i);
            AIMaterial aiMaterial = AIMaterial.createSafe(handle);
            MaterialBuilder builder = new MaterialBuilder(
                    aiMaterial, i, assetManager, mainKey, embeddedTextures);
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
     * Test whether the loaded asset has Z-up orientation.
     *
     * @return true if the orientation is Z-up, otherwise false
     */
    boolean isZUp() {
        return zUp;
    }

    /**
     * Complete the conversion of an incomplete AIScene into a JMonkeyEngine
     * node with an AnimComposer and a SkinningControl.
     *
     * @return a new scene-graph subtree (not null, no parent)
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
        AINode aiRoot = aiScene.mRootNode();
        if (mainKey.isVerboseLogging()) {
            //System.out.println("Assimp node tree:");
            //LwjglReader.dumpNodes(aiRoot, "");
            //System.out.println();
        }

        String nodeName = aiRoot.mName().dataString();
        this.jmeRoot = new Node(nodeName);

        skinnerBuilder.mapSubtree(aiRoot, null);
        AINode rootBoneNode
                = skinnerBuilder.findRootBone(aiRoot, new boolean[1]);

        // Traverse the AINode tree to build and add the SkinningControl:
        skinnerBuilder.createJoints(rootBoneNode);
        this.skinner = skinnerBuilder.buildAndAddTo(controlledNode);

        // Build and add the AnimComposer:
        PointerBuffer pAnimations = aiScene.mAnimations();
        addAnimComposer(numAnimations, pAnimations);

        return jmeRoot;
    }

    /**
     * Finish converting a complete AIScene into a JMonkeyEngine scene-graph
     * subtree.
     * <p>
     * Before invoking this method, the {@code convertMaterials()} method should
     * be invoked to populate the {@code builderList}.
     *
     * @return a new scene-graph subtree (not null, no parent)
     * @throws IOException if the AIScene cannot be converted
     */
    Node toSceneGraph() throws IOException {
        if (!isComplete) {
            throw new IOException("Not a complete scene.");
        }

        // Convert each AIMesh to a Geometry:
        convertMeshes();

        AINode aiRoot = aiScene.mRootNode();
        if (mainKey.isVerboseLogging()) {
            //System.out.println("Assimp node tree:");
            //LwjglReader.dumpNodes(aiRoot, "");
            //System.out.println();
        }

        PointerBuffer pMeshes = aiScene.mMeshes();
        skinnerBuilder.mapSubtree(aiRoot, pMeshes);
        AINode rootBoneNode
                = skinnerBuilder.findRootBone(aiRoot, new boolean[1]);

        // Traverse the AINode tree to generate the JME scene-graph hierarchy:
        this.controlledNodeName = aiRoot.mName().dataString(); // TODO
        this.jmeRoot = convertSubtree(aiRoot);
        assert controlledNode == jmeRoot : controlledNode;

        // If necessary, create a SkinningControl and add it to the result:
        this.skinner = skinnerBuilder.buildAndAddTo(controlledNode);
        /*
         * Convert the animations (if any) to a composer and add it to the
         * controlled node:
         */
        int numAnimations = aiScene.mNumAnimations();
        if (numAnimations > 0) {
            PointerBuffer pAnimations = aiScene.mAnimations();
            addAnimComposer(numAnimations, pAnimations);

        } else { // No animations, add MorphControl if there are morph targets:
            for (Geometry geometry : MySpatial.listGeometries(jmeRoot)) {
                Mesh mesh = geometry.getMesh();
                if (mesh.hasMorphTargets()) {
                    MorphControl morphControl = new MorphControl();
                    controlledNode.addControl(morphControl);
                    break;
                }
            }
        }

        // Convert cameras (if any) to camera nodes and add them to the scene:
        int numCameras = aiScene.mNumCameras();
        if (numCameras > 0) {
            PointerBuffer pCameras = aiScene.mCameras();
            addCameras(numCameras, pCameras);
        }

        // Convert lights (if any) and add them to the scene:
        int numLights = aiScene.mNumLights();
        if (numLights > 0) {
            PointerBuffer pLights = aiScene.mLights();
            addLights(numLights, pLights);
        }

        // Add a parent node where external transforms can be safely applied:
        String sceneName = aiScene.mName().dataString();
        Node result = new Node(sceneName);
        result.attachChild(jmeRoot);

        return result;
    }
    // *************************************************************************
    // private methods

    /**
     * Create an AnimComposer and add it to the specified Node.
     *
     * @param numAnimations the number of animations to convert (&ge;0)
     * @param pAnimations pointers to the animations (not null, unaffected)
     */
    private void addAnimComposer(int numAnimations, PointerBuffer pAnimations)
            throws IOException {
        assert controlledNode != null;
        assert jmeRoot != null;

        AnimComposer composer = new AnimComposer();
        for (int animIndex = 0; animIndex < numAnimations; ++animIndex) {
            long handle = pAnimations.get(animIndex);
            AIAnimation aiAnimation = AIAnimation.createSafe(handle);
            String clipName = aiAnimation.mName().dataString();
            if (clipName.isEmpty()) {
                clipName = "anim_" + animIndex;
            }
            AnimClip animClip = convertAnimation(aiAnimation, clipName);
            composer.addAnimClip(animClip);
        }
        /*
         * The order of scene-graph controls matters, especially during updates.
         * For best results, the AnimComposer should come *before*
         * the MorphControl or SkinningControl, if any:
         */
        controlledNode.addControlAt(0, composer);
    }

    /**
     * Create camera nodes and add them to the asset's root node.
     *
     * @param numCameras the number of cameras to convert (&ge;0)
     * @param pCameras pointers to the cameras (not null, unaffected)
     */
    private void addCameras(int numCameras, PointerBuffer pCameras) {
        assert jmeRoot != null;

        for (int cameraIndex = 0; cameraIndex < numCameras; ++cameraIndex) {
            long handle = pCameras.get(cameraIndex);
            AICamera aiCamera = AICamera.createSafe(handle);
            CameraNode cameraNode = ConversionUtils.convertCamera(aiCamera);
            jmeRoot.attachChild(cameraNode);
        }
    }

    /**
     * Create lights and attach them to the root of the scene graph.
     *
     * @param numLights the number of lights to convert (&ge;0)
     * @param pLights pointers to the lights (not null, unaffected)
     */
    private void addLights(int numLights, PointerBuffer pLights)
            throws IOException {
        assert jmeRoot != null;

        for (int lightIndex = 0; lightIndex < numLights; ++lightIndex) {
            long handle = pLights.get(lightIndex);
            AILight aiLight = AILight.createSafe(handle);

            String nodeName = aiLight.mName().dataString();

            Light light;
            Node lightNode;
            Node parentNode;
            LightControl lightControl;
            int lightType = aiLight.mType();
            switch (lightType) {
                case Assimp.aiLightSource_POINT:
                    lightNode = ConversionUtils.convertPointLight(aiLight);
                    parentNode = getNode(nodeName);
                    parentNode.attachChild(lightNode);
                    lightControl = lightNode.getControl(LightControl.class);
                    light = lightControl.getLight();
                    break;

                case Assimp.aiLightSource_DIRECTIONAL:
                    lightNode
                            = ConversionUtils.convertDirectionalLight(aiLight);
                    parentNode = getNode(nodeName);
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
             * graph.  We add each light to the asset's root node, so it will
             * illuminate the entire model:
             */
            jmeRoot.addLight(light);
        }
    }

    /**
     * Convert the specified AIAnimation to a JMonkeyEngine animation clip.
     *
     * @param aiAnimation the animation to convert (not null, unaffected)
     * @param clipName name for the new clip (not null, not empty)
     * @return a new instance (not null)
     */
    private AnimClip convertAnimation(AIAnimation aiAnimation, String clipName)
            throws IOException {
        assert Validate.nonEmpty(clipName, "clipName");

        double clipDurationInTicks = aiAnimation.mDuration();
        double ticksPerSecond = aiAnimation.mTicksPerSecond();
        if (ticksPerSecond == 0.) {
            // If the rate is unspecified, assume one tick per second:
            ticksPerSecond = 1.;
        }

        // Create the track list with a null element for each Joint:
        int numJoints = (skinner == null) ? 0
                : skinner.getArmature().getJointCount();
        List<AnimTrack<?>> trackList = new ArrayList<>(numJoints);
        for (int jointId = 0; jointId < numJoints; ++jointId) {
            trackList.add(null);
        }

        // Convert each aiNodeAnim channel to a TransformTrack:
        int numChannels = aiAnimation.mNumChannels();
        PointerBuffer pChannels = aiAnimation.mChannels();
        for (int trackIndex = 0; trackIndex < numChannels; ++trackIndex) {
            long handle = pChannels.get(trackIndex);
            AINodeAnim aiNodeAnim = AINodeAnim.createSafe(handle);
            TransformTrack track = convertNodeAnim(
                    aiNodeAnim, clipDurationInTicks, ticksPerSecond);

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
        Armature armature = (skinner == null) ? null : skinner.getArmature();
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

        int numMeshChannels = aiAnimation.mNumMeshChannels();
        if (numMeshChannels > 0) {
            throw new IOException(
                    "MonkeyWrench doesn't handle mesh channels yet.");
        }

        int numMorphMeshChannels = aiAnimation.mNumMorphMeshChannels();
        if (numMorphMeshChannels > 0) {
            pChannels = aiAnimation.mMorphMeshChannels();
            for (int trackI = 0; trackI < numMorphMeshChannels; ++trackI) {
                long handle = pChannels.get(trackI);
                AIMeshMorphAnim anim = AIMeshMorphAnim.createSafe(handle);
                List<MorphTrack> morphTrack
                        = convertMeshMorphAnim(anim, ticksPerSecond);
                trackList.addAll(morphTrack);
            }
        }

        AnimClip result = new AnimClip(clipName);

        int numTracks = trackList.size();
        AnimTrack<?>[] trackArray = new AnimTrack[numTracks];
        trackList.toArray(trackArray);
        result.setTracks(trackArray);

        return result;
    }

    /**
     * Convert the specified Assimp meshes into JMonkeyEngine geometries.
     */
    private void convertMeshes() throws IOException {
        assert skinnerBuilder != null;

        int numMeshes = aiScene.mNumMeshes();
        PointerBuffer pMeshes = aiScene.mMeshes();
        for (int meshIndex = 0; meshIndex < numMeshes; ++meshIndex) {
            long handle = pMeshes.get(meshIndex);
            AIMesh aiMesh = AIMesh.createSafe(handle);
            MeshBuilder meshBuilder = new MeshBuilder(aiMesh, meshIndex);

            String name = meshBuilder.getName();
            Mesh jmeMesh = meshBuilder.createJmeMesh(skinnerBuilder);
            Geometry geometry = new Geometry(name, jmeMesh);
            this.geometryArray[meshIndex] = geometry;

            float[] state = meshBuilder.getInitialMorphState();
            geometry.setMorphState(state);

            // Build and apply the material:
            int materialIndex = aiMesh.mMaterialIndex();
            MaterialBuilder builder = builderList.get(materialIndex);
            Material material = builder.createJmeMaterial(jmeMesh, name);
            geometry.setMaterial(material);

            if (builder.wantsFacetNormals()) {
                Mesh expandedMesh = MyMesh.expand(jmeMesh);
                MyMesh.generateFacetNormals(expandedMesh);
                geometry.setMesh(expandedMesh);
            }

            // Ensure that each geometry with a normal map also has tangents:
            Texture normalMap = material.getParamValue("NormalMap");
            VertexBuffer tangentBuffer
                    = jmeMesh.getBuffer(VertexBuffer.Type.Tangent);
            if (normalMap != null && tangentBuffer == null) {
                System.out.println("Using Mikktspace to generate tangents.");
                MikktspaceTangentGenerator.generate(geometry);
            }
            /*
             * Ensure that transparent geometries
             * will be enqueued to the Transparent bucket:
             */
            RenderState ars = material.getAdditionalRenderState();
            RenderState.BlendMode blendMode = ars.getBlendMode();
            if (blendMode == RenderState.BlendMode.Alpha) {
                geometry.setQueueBucket(RenderQueue.Bucket.Transparent);
            }
        }
    }

    /**
     * Convert the specified {@code AIMeshMorphAnim} to a collection of
     * JMonkeyEngine animation tracks.
     *
     * @param aiMeshMorphAnim the morph animation to convert (not null,
     * unaffected)
     * @param ticksPerSecond the number of ticks per second for the current
     * model (&gt;0)
     * @return a new list of new tracks (not null)
     */
    private List<MorphTrack> convertMeshMorphAnim(
            AIMeshMorphAnim aiMeshMorphAnim, double ticksPerSecond)
            throws IOException {
        assert jmeRoot != null;

        String targetName = aiMeshMorphAnim.mName().dataString();
        if (targetName.isEmpty()) {
            throw new IOException("Invalid name for morph-animation target.");
        }
        /*
         * According to Assimp inline documentation, it's fine for
         * multiple meshes to have the same name.
         */
        List<MorphTrack> result = new ArrayList<>(1); // empty list
        List<Geometry> targetList
                = ConversionUtils.listMorphTargets(targetName, jmeRoot);
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
        controlledNode.addControl(morphControl);

        return result;
    }

    /**
     * Convert the specified {@code AINodeAnim} to a JMonkeyEngine animation
     * track.
     *
     * @param aiNodeAnim the animation to convert (not null, unaffected)
     * @param clipDurationInTicks the duration of the track (in ticks, &ge;0)
     * @param ticksPerSecond the number of ticks per second (&gt;0)
     * @return a new instance (not null)
     */
    private TransformTrack convertNodeAnim(AINodeAnim aiNodeAnim,
            double clipDurationInTicks, double ticksPerSecond)
            throws IOException {
        assert jmeRoot != null;
        assert ticksPerSecond > 0. : ticksPerSecond;

        String nodeName = aiNodeAnim.mNodeName().dataString();
        HasLocalTransform target = getTarget(nodeName);
        double trackSeconds = clipDurationInTicks / ticksPerSecond;
        TransformTrackBuilder builder
                = new TransformTrackBuilder(target, (float) trackSeconds);

        int numPositionKeys = aiNodeAnim.mNumPositionKeys();
        AIVectorKey.Buffer pPositionKeys = aiNodeAnim.mPositionKeys();
        for (int keyIndex = 0; keyIndex < numPositionKeys; ++keyIndex) {
            AIVectorKey key = pPositionKeys.get(keyIndex);
            double time = key.mTime() / ticksPerSecond;
            if (time >= 0.) {
                Vector3f offset = ConversionUtils.convertVector(key.mValue());
                builder.addTranslation((float) time, offset);
            }
        }

        int numRotationKeys = aiNodeAnim.mNumRotationKeys();
        AIQuatKey.Buffer pRotationKeys = aiNodeAnim.mRotationKeys();
        for (int keyIndex = 0; keyIndex < numRotationKeys; ++keyIndex) {
            AIQuatKey key = pRotationKeys.get(keyIndex);
            double time = key.mTime() / ticksPerSecond;
            if (time >= 0.) {
                Quaternion rotation
                        = ConversionUtils.convertQuaternion(key.mValue());
                builder.addRotation((float) time, rotation);
            }
        }

        int numScalingKeys = aiNodeAnim.mNumScalingKeys();
        AIVectorKey.Buffer pScalingKeys = aiNodeAnim.mScalingKeys();
        for (int keyIndex = 0; keyIndex < numScalingKeys; ++keyIndex) {
            AIVectorKey key = pScalingKeys.get(keyIndex);
            double time = key.mTime() / ticksPerSecond;
            if (time >= 0.) {
                Vector3f scale = ConversionUtils.convertVector(key.mValue());
                builder.addScale((float) time, scale);
            }
        }

        TransformTrack result = builder.build();
        return result;
    }

    /**
     * Create a JMonkeyEngine node that approximates the specified Assimp node.
     * Note: recursive!
     *
     * @param aiNode the root of the Assimp node tree to convert (not null,
     * unaffected)
     * @return a new scene-graph subtree (not null, no parent)
     */
    private Node convertSubtree(AINode aiNode) throws IOException {
        String nodeName = aiNode.mName().dataString();
        Node result = new Node(nodeName);
        if (nodeName.equals(controlledNodeName)) {
            assert !skinnerBuilder.isKnownBone(nodeName);
            this.controlledNode = result;
        }

        int numMeshesInNode = aiNode.mNumMeshes();
        if (numMeshesInNode > 0) {
            IntBuffer pMeshIndices = aiNode.mMeshes();
            for (int i = 0; i < numMeshesInNode; ++i) {
                int meshId = pMeshIndices.get(i);
                Geometry geometry = geometryArray[meshId].clone();
                result.attachChild(geometry);
            }
        }

        int numChildren = aiNode.mNumChildren();
        if (numChildren > 0) {
            PointerBuffer pChildren = aiNode.mChildren();
            for (int childIndex = 0; childIndex < numChildren; ++childIndex) {
                long handle = pChildren.get(childIndex);
                AINode aiChild = AINode.createSafe(handle);

                int numMeshesInSubtree
                        = LwjglReader.countMeshesInSubtree(aiChild);
                if (numMeshesInSubtree > 0) {
                    // Attach a child to the JMonkeyEngine scene-graph node:
                    Node jmeChild = convertSubtree(aiChild);
                    result.attachChild(jmeChild);

                } else { // Add a root joint to the armature:
                    skinnerBuilder.createJoints(aiChild);
                }
            }
        }

        AIMatrix4x4 transformation = aiNode.mTransformation();
        Transform transform = ConversionUtils.convertTransform(transformation);
        result.setLocalTransform(transform);

        AIMetaData metadata = aiNode.mMetadata();
        if (metadata != null) {
            Map<String, Object> map = ConversionUtils.convertMetadata(metadata);
            if (mainKey.isVerboseLogging()) {
                //System.out.println("Node metadata:");
                //LwjglReader.dumpMetaData(map, " ");
            }
        }

        return result;
    }

    /**
     * Return a model node for the named AINode, either a pre-existing node in
     * the converted asset or else an attachment node.
     *
     * @param nodeName the name to search for (not null)
     * @return a Node in the converted asset (might be new)
     * @throws IOException if the name is not found
     */
    private Node getNode(String nodeName) throws IOException {
        assert nodeName != null;
        assert jmeRoot != null;

        if (skinner != null) { // Search for a Joint with the specified name:
            Armature armature = skinner.getArmature();
            Joint joint = armature.getJoint(nodeName);
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
     * Return the JMonkeyEngine Node or Joint corresponding to the named AINode.
     *
     * @param nodeName the name to search for (not null)
     * @return a pre-existing Node or Joint (not null)
     * @throws IOException if the name is not found
     */
    private HasLocalTransform getTarget(String nodeName) throws IOException {
        assert nodeName != null;
        assert jmeRoot != null;

        HasLocalTransform result = null;
        if (skinner != null) {
            // Search for an armature joint with the specified name:
            Armature armature = skinner.getArmature();
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
     * Process the flags and metadata of the AIScene.
     *
     * @throws IOException if the metadata can't be processed
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
            if (mainKey.isVerboseLogging()) {
                System.out.println("Scene metadata:");
                LwjglReader.dumpMetaData(map, " ");
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
                }
            }
        }
    }
}
