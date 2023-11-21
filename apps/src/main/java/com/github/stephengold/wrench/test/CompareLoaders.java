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
package com.github.stephengold.wrench.test;

import com.github.stephengold.wrench.LwjglAssetKey;
import com.github.stephengold.wrench.LwjglAssetLoader;
import com.github.stephengold.wrench.LwjglReader;
import com.github.stephengold.wrench.PathEdit;
import com.github.stephengold.wrench.TextureLoader;
import com.jme3.anim.AnimClip;
import com.jme3.anim.AnimComposer;
import com.jme3.anim.Armature;
import com.jme3.anim.SkinningControl;
import com.jme3.anim.util.AnimMigrationUtils;
import com.jme3.app.StatsAppState;
import com.jme3.app.state.AppState;
import com.jme3.asset.AssetLoader;
import com.jme3.asset.ModelKey;
import com.jme3.input.KeyInput;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.light.LightList;
import com.jme3.light.LightProbe;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.plugins.OBJLoader;
import com.jme3.scene.plugins.blender.BlenderLoader;
import com.jme3.scene.plugins.fbx.FbxLoader;
import com.jme3.scene.plugins.gltf.GlbLoader;
import com.jme3.scene.plugins.gltf.GltfLoader;
import com.jme3.scene.plugins.ogre.MeshLoader;
import com.jme3.shadow.DirectionalLightShadowRenderer;
import com.jme3.system.AppSettings;
import com.jme3.system.JmeVersion;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import jme3utilities.Heart;
import jme3utilities.MyCamera;
import jme3utilities.MyMesh;
import jme3utilities.MySkeleton;
import jme3utilities.MySpatial;
import jme3utilities.MyString;
import jme3utilities.debug.Dumper;
import jme3utilities.debug.SkeletonVisualizer;
import jme3utilities.math.MyMath;
import jme3utilities.ui.AcorusDemo;
import jme3utilities.ui.CameraOrbitAppState;
import jme3utilities.ui.HelpBuilder;
import jme3utilities.ui.InputMode;
import jme3utilities.ui.Locators;
import jme3utilities.ui.Signals;

/**
 * An Acorus application to compare various asset loaders on test assets located
 * in the filesystem.
 *
 * @author Stephen Gold sgold@sonic.net
 */
class CompareLoaders extends AcorusDemo {
    // *************************************************************************
    // constants and loggers

    /**
     * default height for the framebuffer (in pixels)
     * <p>
     * Make it tall enough to accommodate the Acorus help node.
     */
    final private static int defaultFramebufferHeight = 544;
    /**
     * default width for the framebuffer (in pixels)
     */
    final private static int defaultFramebufferWidth = 800;
    /**
     * message logger for this class
     */
    final private static Logger logger
            = Logger.getLogger(CompareLoaders.class.getName());
    /**
     * application name (for the title bar of the app's window)
     */
    final private static String applicationName
            = CompareLoaders.class.getSimpleName();
    /**
     * action string to print details of the loaded asset(s)
     */
    final private static String asDumpAssets = "dump assets";
    /**
     * action string to load the selected test asset using the selected
     * loader(s)
     */
    final private static String asLoadAsset = "load asset";
    /**
     * name of the signal that's active when a Ctrl key is pressed
     */
    final private static String snCtrl = "ctrl";
    /**
     * name of the signal to orbit the camera to the left
     */
    final private static String snOrbitLeft = "orbit left";
    /**
     * name of the signal to orbit the camera to the right
     */
    final private static String snOrbitRight = "orbit right";
    /**
     * name of the signal that's active when a Shift key is pressed
     */
    final private static String snShift = "shift";
    /**
     * for loading non-embedded textures
     */
    final private static TextureLoader textureLoader
            = new TextureLoader(PathEdit.LastComponent, "%s%s%s",
                    "%stextures/%s%s", "textures/%2$s%3$s",
                    "%sTextures/%s%s", "Textures/%2$s%3$s",
                    "textures/%2$s.jpeg", "textures/%2$s%3$s.png");
    // *************************************************************************
    // fields

    /**
     * ambient light source added to the scene
     */
    private static AmbientLight ambientLight;
    /**
     * true to enable all skeleton visualizers, false to disable them
     */
    private static boolean showArmatures = true;
    /**
     * all skeleton visualizers in the scene
     */
    final private static Collection<SkeletonVisualizer> visualizers
            = new ArrayList<>(2);
    /**
     * dump the {@code dumpSpatial} to {@code System.out}
     */
    final private static Dumper dumper = new Dumper();
    /**
     * map group names to asset groups
     */
    final private static Map<String, AssetGroup> groupMap = new TreeMap<>();
    /**
     * scene-graph subtree to dump
     */
    private static Spatial dumpSpatial = new Node("No assets are loaded.");
    /**
     * AppState to manage the status overlay
     */
    private static TestStatus status;
    // *************************************************************************
    // new methods exposed

    /**
     * Count how many armatures are visualized.
     *
     * @return the count (&ge;0)
     */
    static int countVisibleArmatures() {
        int result;
        if (showArmatures) {
            result = visualizers.size();
        } else {
            result = 0;
        }

        return result;
    }

    /**
     * Find the named AssetGroup.
     *
     * @param groupName the name of the group to find (not null)
     * @return the pre-existing instance, or null if not recognized
     */
    static AssetGroup findGroup(String groupName) {
        AssetGroup result = groupMap.get(groupName);
        return result;
    }

    /**
     * Load the specified animation and run it.
     *
     * @param animationName the name of the animation clip to load, or a
     * fictitious animation name (not null)
     */
    void loadAnimation(String animationName) {
        if (isPaused()) {
            togglePause();
        }

        List<AnimComposer> composers
                = MySpatial.listControls(rootNode, AnimComposer.class, null);
        for (AnimComposer composer : composers) {
            switch (animationName) {
                case TestStatus.initialPoseName:
                case TestStatus.noClipsName:
                    composer.reset();
                    break;

                default:
                    if (composer.getAnimClip(animationName) == null) {
                        logger.log(Level.WARNING, "Clip not found: {0}",
                                MyString.quote(animationName));
                        for (String clipName : composer.getAnimClipsNames()) {
                            System.out.println(MyString.quote(clipName));
                        }
                    } else {
                        composer.setCurrentAction(animationName);
                    }
            }
        }

        switch (animationName) {
            case TestStatus.initialPoseName:
            case TestStatus.noClipsName:
                List<SkinningControl> skinners = MySpatial.listControls(
                        rootNode, SkinningControl.class, null);
                for (SkinningControl skinner : skinners) {
                    Armature armature = skinner.getArmature();
                    armature.applyInitialPose();
                }
                break;

            default:
        }
    }

    /**
     * Main entry point for the CompareLoaders application.
     *
     * @param arguments array of command-line arguments (not null)
     */
    public static void main(String[] arguments) {
        String title = applicationName + " " + MyString.join(arguments);
        CompareLoaders application = new CompareLoaders();
        Heart.parseAppArgs(application, arguments);

        boolean loadDefaults = true;
        AppSettings settings = new AppSettings(loadDefaults);
        settings.setAudioRenderer(null);
        settings.setGammaCorrection(true);
        settings.setHeight(defaultFramebufferHeight);
        settings.setResizable(true);
        settings.setSamples(4); // anti-aliasing
        settings.setTitle(title); // Customize the window's title bar.
        settings.setWidth(defaultFramebufferWidth);
        application.setSettings(settings);
        /*
         * The AWT settings dialog interferes with LWJGL v3
         * on macOS and Raspbian, so don't show it!
         */
        application.setShowSettings(false);
        application.start();
    }

    /**
     * Clear the scene, then reset the loaded asset including its animation
     * list.
     */
    void newScene() {
        clearScene();
        dumpSpatial = new Node("No assets loaded.");
        status.resetAnimationsAndMaterials();
    }

    /**
     * Adjust the ambient-light level.
     *
     * @param settingName the name of the desired setting (not empty, not null)
     */
    void setAmbient(String settingName) {
        assert settingName != null;
        assert !settingName.isEmpty();

        if (settingName.endsWith("%")) {
            int len = settingName.length();
            String numberString = settingName.substring(0, len - 1);
            float percentage = Float.parseFloat(numberString);
            float fraction = percentage / 100f;
            ColorRGBA gray = new ColorRGBA(fraction, fraction, fraction, 1f);
            ambientLight.setColor(gray);
            ambientLight.setEnabled(true);

        } else {
            assert settingName.equals("disabled") :
                    "settingName = " + settingName;
            ambientLight.setEnabled(false);
        }
    }

    /**
     * Alter the mode of every SkinningControl in the scene.
     *
     * @param settingName the name of the desired setting (either "CPU" or
     * "GPU")
     */
    void setSkinningMode(String settingName) {
        boolean useGpu;
        switch (settingName) {
            case "CPU":
                useGpu = false;
                break;

            case "GPU":
                useGpu = true;
                break;

            default:
                String qName = MyString.quote(settingName);
                throw new IllegalArgumentException("settingName = " + qName);
        }

        List<SkinningControl> skinners
                = MySpatial.listControls(rootNode, SkinningControl.class, null);
        for (SkinningControl skinner : skinners) {
            skinner.setHardwareSkinningPreferred(useGpu);
        }
    }

    /**
     * Show the specified material and hide all others.
     *
     * @param materialName the name of the material to show, or a fictitious
     * material name (not null)
     */
    void showMaterial(String materialName) {
        List<Geometry> geometryList = MySpatial.listGeometries(rootNode);
        for (Geometry geometry : geometryList) {
            boolean show = materialName.equals(TestStatus.allMaterialsName);
            if (!show) {
                Material material = geometry.getMaterial();
                String name = material.getName();
                show = Objects.equals(name, materialName);
            }
            if (show) {
                geometry.setCullHint(Spatial.CullHint.Inherit);
            } else {
                geometry.setCullHint(Spatial.CullHint.Always);
            }
        }
    }
    // *************************************************************************
    // AcorusDemo methods

    /**
     * Initialize the application.
     */
    @Override
    public void acorusInit() {
        System.out.printf("Using %s (Git hash %s)%n", JmeVersion.FULL_NAME,
                JmeVersion.GIT_SHORT_HASH);

        String mwVersion = LwjglReader.version();
        System.out.printf(
                "Using version %s of the MonkeyWrench library%n", mwVersion);

        String assimpGitHash = loadResourceAsString(
                "/META-INF/linux/x64/org/lwjgl/assimp/libassimp.so.git");
        System.out.println(
                "Using Assimp Git hash " + assimpGitHash.substring(0, 7));

        String disEn = Heart.areAssertionsEnabled() ? "en" : "dis";
        logger.log(Level.WARNING, "Assertions are {0}abled.", disEn);

        // Mute the warnings from checkTextureParamColorSpace():
        Logger.getLogger("com.jme3.material.Material").setLevel(Level.SEVERE);

        renderer.setDefaultAnisotropicFilter(8);

        AppState orbitState
                = new CameraOrbitAppState(cam, snOrbitLeft, snOrbitRight);
        boolean success = stateManager.attach(orbitState);
        assert success;

        addAssetGroups();
        status = new TestStatus(groupMap.keySet());
        success = stateManager.attach(status);
        assert success;

        super.acorusInit();

        // Hide the render-statistics overlay:
        StatsAppState sas = stateManager.getState(StatsAppState.class);
        sas.toggleStats();

        // Set the background to light blue:
        ColorRGBA backgroundColor = new ColorRGBA(0.2f, 0.2f, 1f, 1f);
        viewPort.setBackgroundColor(backgroundColor);

        // Set up the initial scene:
        addLighting();
        configureCamera();
        clearScene();

        HelpBuilder helpBuilder = getHelpBuilder();
        helpBuilder.setBackgroundColor(ColorRGBA.Blue);
    }

    /**
     * Add application-specific hotkey bindings and override existing ones.
     */
    @Override
    public void moreDefaultBindings() {
        InputMode dim = getDefaultInputMode();

        dim.bindSignal(snCtrl, KeyInput.KEY_LCONTROL, KeyInput.KEY_RCONTROL);
        dim.bindSignal(snShift, KeyInput.KEY_LSHIFT, KeyInput.KEY_RSHIFT);

        dim.bind(asDumpAssets, KeyInput.KEY_P);

        dim.bind(asLoadAsset, KeyInput.KEY_NUMPADENTER,
                KeyInput.KEY_NUMPAD5, KeyInput.KEY_RETURN, KeyInput.KEY_L);

        dim.bind("next animation", KeyInput.KEY_N);
        dim.bind("next field", KeyInput.KEY_NUMPAD2, KeyInput.KEY_DOWN);
        dim.bind("next value",
                KeyInput.KEY_NUMPAD6, KeyInput.KEY_EQUALS, KeyInput.KEY_TAB);

        dim.bindSignal(snOrbitLeft, KeyInput.KEY_LEFT);
        dim.bindSignal(snOrbitRight, KeyInput.KEY_RIGHT);

        dim.bind("previous field", KeyInput.KEY_NUMPAD8, KeyInput.KEY_UP);
        dim.bind("previous value",
                KeyInput.KEY_NUMPAD4, KeyInput.KEY_MINUS, KeyInput.KEY_BACK);

        dim.bind("reposition camera", KeyInput.KEY_R, KeyInput.KEY_F7);

        dim.bind("toggle armatures", KeyInput.KEY_V, KeyInput.KEY_F3);
        dim.bind(asToggleHelp, KeyInput.KEY_H, KeyInput.KEY_F1);
        dim.bind(asTogglePause, KeyInput.KEY_PAUSE, KeyInput.KEY_PERIOD);
        dim.bind("toggle projection", KeyInput.KEY_F8);
        dim.bind(asToggleWorldAxes, KeyInput.KEY_SPACE, KeyInput.KEY_F6);

        dim.bind("value+7", KeyInput.KEY_NUMPAD9);
        dim.bind("value-7", KeyInput.KEY_NUMPAD7);
    }

    /**
     * Process an action that wasn't handled by the active InputMode.
     *
     * @param actionString textual description of the action (not null)
     * @param ongoing true if the action is ongoing, otherwise false
     * @param tpf the time interval between frames (in seconds, &ge;0)
     */
    @Override
    public void onAction(String actionString, boolean ongoing, float tpf) {
        if (ongoing) {
            switch (actionString) {
                case asDumpAssets:
                    Signals signals = this.getSignals();
                    boolean verbose = signals.test(snShift);
                    boolean vertexData = signals.test(snCtrl);
                    dumpLoadedAssets(verbose, vertexData);
                    return;

                case asLoadAsset:
                    loadModel();
                    return;

                case "next animation":
                    status.advanceAnimation(+1);
                    return;

                case "next field":
                    status.advanceSelectedField(+1);
                    return;

                case "next value":
                    status.advanceValue(+1);
                    return;

                case "previous field":
                    status.advanceSelectedField(-1);
                    return;

                case "previous value":
                    status.advanceValue(-1);
                    return;

                case "reposition camera":
                    repositionCamera();
                    return;

                case "toggle armatures":
                    toggleArmatures();
                    return;

                case "toggle projection":
                    toggleProjection();
                    return;

                case "value+7":
                    status.advanceValue(+7);
                    return;

                case "value-7":
                    status.advanceValue(-7);
                    return;

                default:
            }
        }

        // The action has not been handled:  forward it to the superclass.
        super.onAction(actionString, ongoing, tpf);
    }

    /**
     * Update the GUI layout after the ViewPort gets resized.
     *
     * @param newWidth the new width of the ViewPort (in pixels, &gt;0)
     * @param newHeight the new height of the ViewPort (in pixels, &gt;0)
     */
    @Override
    public void onViewPortResize(int newWidth, int newHeight) {
        super.onViewPortResize(newWidth, newHeight);
        status.resize(newWidth, newHeight);
    }
    // *************************************************************************
    // private methods

    /**
     * Add the specified asset group to {@code groupMap} if the group is
     * accessible.
     *
     * @param groupName a name to identify the group (not null)
     * @param group the group to add (not null, alias created)
     */
    private static void addAssetGroup(String groupName, AssetGroup group) {
        if (group.isAccessible()) {
            String[] names = group.listAssets();
            int numNames = (names == null) ? 0 : names.length;
            if (numNames > 0) {
                System.out.printf("Found %d test asset%s in group %s.%n",
                        numNames, (numNames == 1) ? "" : "s", groupName);
                groupMap.put(groupName, group);
            }
        }
    }

    /**
     * Add accessible asset groups to {@code groupMap}.
     */
    private static void addAssetGroups() {
        addAssetGroup("assimp-mdb-3mf", new AssimpMdb("3mf"));
        addAssetGroup("assimp-mdb-blender", new AssimpMdb("blender"));
        addAssetGroup("assimp-mdb-fbx", new AssimpMdb("fbx"));
        addAssetGroup("assimp-mdb-gltf2", new AssimpMdb("glTF2"));
        addAssetGroup("assimp-mdb-obj", new AssimpMdb("Obj"));

        addAssetGroup("gltf-sample-models-10",
                new GltfSampleModels("1.0", "glTF"));
        addAssetGroup("gltf-sample-models-10-binary",
                new GltfSampleModels("1.0", "glTF-Binary"));
        addAssetGroup("gltf-sample-models-10-common",
                new GltfSampleModels("1.0", "glTF-MaterialsCommon"));
        addAssetGroup("gltf-sample-models-10-embedded",
                new GltfSampleModels("1.0", "glTF-Embedded"));

        addAssetGroup("gltf-sample-models-20",
                new GltfSampleModels("2.0", "glTF"));
        addAssetGroup("gltf-sample-models-20-binary",
                new GltfSampleModels("2.0", "glTF-Binary"));
        addAssetGroup("gltf-sample-models-20-draco",
                new GltfSampleModels("2.0", "glTF-Draco"));
        addAssetGroup("gltf-sample-models-20-embedded",
                new GltfSampleModels("2.0", "glTF-Embedded"));

        addAssetGroup("jme3-testdata-31", new Jme3TestData("3.1.0-stable"));
        addAssetGroup("jme3-testdata-36", new Jme3TestData("3.6.1-stable"));

        addAssetGroup("lumberyard-bistro", new BistroGroup());
        addAssetGroup("mixamo-dae", new MixamoData("dae"));
        addAssetGroup("open3dmodel", new Open3dModelGroup());

        addAssetGroup("sketchfab-3ds", new SketchfabData("3ds"));
        addAssetGroup("sketchfab-blend", new SketchfabData("blend"));
        addAssetGroup("sketchfab-conv-glb", new SketchfabData("glb", true));
        addAssetGroup("sketchfab-conv-gltf", new SketchfabData("glTF", true));
        addAssetGroup("sketchfab-dae", new SketchfabData("dae"));
        addAssetGroup("sketchfab-fbx", new SketchfabData("fbx"));
        addAssetGroup("sketchfab-obj", new SketchfabData("obj"));
        addAssetGroup("sketchfab-orig-glb", new SketchfabData("glb"));
        addAssetGroup("sketchfab-orig-gltf", new SketchfabData("glTF"));

        addAssetGroup("threejs-3ds", new ThreejsExamples("3ds"));
        addAssetGroup("threejs-3mf", new ThreejsExamples("3mf"));
        addAssetGroup("threejs-bvh", new ThreejsExamples("bvh"));
        addAssetGroup("threejs-dae", new ThreejsExamples("collada"));
        addAssetGroup("threejs-fbx", new ThreejsExamples("fbx"));
        addAssetGroup("threejs-glb", new ThreejsExamples("gltf"));
        addAssetGroup("threejs-lwo", new ThreejsExamples("lwo"));
        addAssetGroup("threejs-obj", new ThreejsExamples("obj"));
        addAssetGroup("threejs-ply-ascii", new ThreejsExamples("ply/ascii"));
        addAssetGroup("threejs-ply-binary", new ThreejsExamples("ply/binary"));
        addAssetGroup("threejs-stl-ascii", new ThreejsExamples("stl/ascii"));
        addAssetGroup("threejs-stl-binary", new ThreejsExamples("stl/binary"));

        if (groupMap.isEmpty()) {
            throw new RuntimeException("No test assets were found.");
        }
    }

    /**
     * Add lighting and shadows to the scene.
     * <p>
     * TODO: disable these if the loaded model contains lights
     */
    private void addLighting() {
        Spatial probeSpatial = assetManager.loadModel("defaultProbe.j3o");
        LightList lightList = probeSpatial.getLocalLightList();
        LightProbe defaultProbe = (LightProbe) lightList.get(0);
        defaultProbe.setName("defaultProbe");
        rootNode.addLight(defaultProbe);

        ambientLight = new AmbientLight();
        ambientLight.setName("ambient");
        rootNode.addLight(ambientLight);

        Vector3f direction = new Vector3f(1f, -2f, -2f).normalizeLocal();
        DirectionalLight sun = new DirectionalLight(direction);
        sun.setName("sun");
        rootNode.addLight(sun);

        DirectionalLightShadowRenderer dlsr
                = new DirectionalLightShadowRenderer(assetManager, 4_096, 3);
        dlsr.setLight(sun);
        dlsr.setShadowIntensity(0.5f);
        viewPort.addProcessor(dlsr);
    }

    /**
     * Clear the main scene.
     * <p>
     * Doesn't affect the background color, camera, or lights.
     */
    private void clearScene() {
        rootNode.detachAllChildren();
        visualizers.clear();

        // Attach world axes to the root node:
        float axesLength = 1f;
        attachWorldAxes(axesLength);
    }

    /**
     * Configure the camera during startup.
     */
    private void configureCamera() {
        flyCam.setDragToRotate(true);
        flyCam.setMoveSpeed(2f);

        cam.setName("FlyByCamera");
        MyCamera.setNearFar(cam, 0.01f, 40f);
        repositionCamera();
    }

    /**
     * Generate a ModelKey for the specified loaders and selected test asset.
     *
     * @param loaders the name of the asset loader(s) that will be used (not
     * null, not empty)
     * @return a new instance (not null)
     */
    private static ModelKey createModelKey(String loaders) {
        String groupName = status.selectedGroup();
        AssetGroup group = findGroup(groupName);

        // Determine the asset path:
        String assetName = status.selectedAsset();
        String assetPath = group.assetPath(assetName);
        if (assetPath == null) {
            logger.log(Level.SEVERE, "No known path for asset {0}.",
                    MyString.quote(assetName));
            assetPath = "No known path for " + assetName;
        }

        // Generate the key:
        ModelKey result;
        switch (loaders) {
            case "Default":
                result = new ModelKey(assetPath);
                break;

            case "Lwjgl":
                LwjglAssetKey key = new LwjglAssetKey(assetPath, textureLoader);
                result = key;
                break;

            case "LwjglVerbose":
                key = new LwjglAssetKey(assetPath, textureLoader);
                key.setVerboseLogging(true);
                //int flags = key.flags();
                //System.out.println("flags = 0x" + Integer.toHexString(flags));
                result = key;
                break;

            default:
                throw new IllegalArgumentException("loaders = " + loaders);
        }

        return result;
    }

    /**
     * Dump the loaded assets.
     *
     * @param verbose true for a more detailed dump (with render-queue buckets,
     * cull hints, material parameters/overrides, and transforms), false for
     * less detail
     * @param vertexData true to dump vertex data, false to omit vertex data
     */
    private void dumpLoadedAssets(boolean verbose, boolean vertexData) {
        boolean worldAxesWereEnabled = areWorldAxesEnabled();
        if (worldAxesWereEnabled) {
            toggleWorldAxes(); // Temporarily hide the world axes.
        }

        dumper.setDumpBucket(verbose);
        dumper.setDumpMatParam(verbose);
        dumper.setDumpOverride(verbose);
        dumper.setDumpTransform(verbose);

        dumper.setDumpVertex(vertexData);

        System.out.println();
        dumper.dump(dumpSpatial);

        List<Armature> armatures = MySkeleton.listArmatures(dumpSpatial, null);
        for (Armature armature : armatures) {
            DumpUtils.dumpArmature(armature);
        }

        for (Mesh mesh : MyMesh.listMeshes(dumpSpatial, null)) {
            if (mesh.hasMorphTargets()) {
                //DumpUtils.dumpMorphTargets(mesh);
            }
        }

        List<AnimComposer> composers
                = MySpatial.listControls(dumpSpatial, AnimComposer.class, null);
        for (AnimComposer composer : composers) {
            for (AnimClip clip : composer.getAnimClips()) {
                //DumpUtils.dumpClip(clip);
            }
        }

        if (worldAxesWereEnabled) {
            toggleWorldAxes(); // Show the axes that were temporarily hidden.
        }
    }

    /**
     * Load the selected test asset using the selected asset loader(s).
     */
    private void loadModel() {
        clearScene();
        status.resetAnimationsAndMaterials();

        String groupName = status.selectedGroup();
        registerLocators(groupName);

        Spatial loadedSpatial;
        String selectedLoaders = status.selectedLoaders();
        if (selectedLoaders.equals("SideBySide")) {
            Node leftNode = new Node("LWJGL loader");
            Node rightNode = new Node("DEFAULT loaders");

            registerLoader("Lwjgl");
            loadedSpatial = loadModel("Lwjgl");
            leftNode.attachChild(loadedSpatial);

            registerLoader("Default");
            loadedSpatial = loadModel("Default");
            rightNode.attachChild(loadedSpatial);

            leftNode.move(-3f, 0f, 0f);
            rootNode.attachChild(leftNode);

            rightNode.move(3f, 0f, 0f);
            rootNode.attachChild(rightNode);

        } else {
            registerLoader(selectedLoaders);
            loadedSpatial = loadModel(selectedLoaders);
            rootNode.attachChild(loadedSpatial);
        }

        dumpSpatial = rootNode;

        String skinningName = status.selectedSkinning();
        setSkinningMode(skinningName);
    }

    /**
     * Load the selected test asset using the specified asset loader(s).
     *
     * @param loaders the name of the asset loader(s) that will be used (not
     * null, not empty)
     * @return the root node of the loaded asset, or a dummy node if the load
     * failed (not null)
     */
    private Spatial loadModel(String loaders) {
        assetManager.clearCache();

        String assetName = status.selectedAsset();
        String groupName = status.selectedGroup();
        String testDescription
                = String.format("%s from the %s group using the %s loader(s)",
                        MyString.quote(assetName), groupName, loaders);
        System.out.printf(
                "%n%n%n%n======%nLoading %s ...%n%n", testDescription);

        ModelKey modelKey = createModelKey(loaders);

        Spatial result;
        long startTime = System.nanoTime();
        try {
            result = assetManager.loadModel(modelKey);
            long completionTime = System.nanoTime();

            System.err.flush();
            System.out.printf("%nSuccessfully loaded %s", testDescription);
            if (!Heart.areAssertionsEnabled()) {
                double elapsedSeconds = 1e-9 * (completionTime - startTime);
                System.out.printf("; elapsed time = %.3f sec", elapsedSeconds);
            }

        } catch (Exception exception) {
            result = new Node("Load failed");

            System.out.flush();
            exception.printStackTrace();
            System.err.flush();
            System.out.printf("%nFailed to load %s", testDescription);
        }
        System.out.printf(".%n======%n");
        /*
         * If the loaded asset uses the old animation system,
         * convert it to the new one.
         */
        AnimMigrationUtils.migrate(result);

        status.addAnimationsAndMaterials(result);
        /*
         * Add a SkeletonVisualizer for each SkinningControl. Enable and update
         * each visualizer so its vertices will be included in the scaling and
         * centering calculations below.
         */
        List<SkinningControl> skinners
                = MySpatial.listControls(result, SkinningControl.class, null);
        for (SkinningControl skinner : skinners) {
            SkeletonVisualizer visualizer
                    = new SkeletonVisualizer(assetManager, skinner);
            result.addControl(visualizer);
            visualizers.add(visualizer);

            //InfluenceUtil.hideNonInfluencers(visualizer, skinner);
            visualizer.setEnabled(true);
            visualizer.setLineColor(ColorRGBA.Red);
        }
        result.updateLogicalState(0f);

        int numVertices = MySpatial.countVertices(result);
        if (numVertices > 1) {
            // Scale the C-G model and center it directly above the origin:
            scaleCgm(result);
            centerCgm(result);
        }

        if (!showArmatures) {
            // Disable each visualizer:
            for (SkeletonVisualizer visualizer : visualizers) {
                visualizer.setEnabled(false);
            }
        }

        String animationName = status.selectedAnimation();
        loadAnimation(animationName);

        return result;
    }

    /**
     * Load UTF-8 text from the named resource. TODO use the Heart library
     *
     * @param resourceName the name of the classpath resource to load (not null)
     * @return the text (possibly multiple lines)
     */
    private static String loadResourceAsString(String resourceName) {
        InputStream inputStream
                = CompareLoaders.class.getResourceAsStream(resourceName);
        if (inputStream == null) {
            String q = MyString.quote(resourceName);
            throw new RuntimeException("resource not found:  " + q);
        }

        // Parse the stream's data into one long text string.
        String charsetName = StandardCharsets.UTF_8.name();
        String result;
        try (Scanner scanner = new Scanner(inputStream, charsetName)) {
            scanner.useDelimiter("\\Z");
            result = scanner.next();
        }

        return result;
    }

    /**
     * Register JMonkeyEngine's customary loaders to handle known file formats.
     * <p>
     * Compare with the "General.cfg" file in the jme3-core resources.
     */
    private void registerDefaultLoaders() {
        /*
         * JMonkeyEngine does not provide a loader for
         * 3D Studio Max (.3ds) assets,
         * 3-D Manufacturing Format (.3mf) assets,
         * Biovision (.bvh) assets,
         * COLLADA (.dae) assets,
         * LightWave Model (.lwo) assets,
         * Polygon (.ply) assets, nor
         * Sterolithography (.stl) assets:
         */
        assetManager.registerLoader(DummyLoader.class,
                "3ds", "3mf", "bvh", "dae", "lwo", "ply", "stl");

        assetManager.registerLoader(BlenderLoader.class, "blend");

        assetManager.registerLoader(FbxLoader.class, "fbx");
        assetManager.registerLoader(GlbLoader.class, "glb");
        assetManager.registerLoader(GltfLoader.class, "gltf");
        assetManager.registerLoader(OBJLoader.class, "obj");
        assetManager.registerLoader(MeshLoader.class, "meshxml", "mesh.xml");
    }

    /**
     * Register the named asset loaders.
     *
     * @param loadersName the name of the desired asset loaders (not null)
     */
    private void registerLoader(String loadersName) {
        switch (loadersName) {
            case "Default":
                registerDefaultLoaders();
                break;

            case "Lwjgl":
            case "LwjglVerbose":
                registerLoaders(LwjglAssetLoader.class);
                break;

            default:
                throw new IllegalArgumentException(
                        "loadersName = " + loadersName);
        }
    }

    /**
     * Register the specified loader to handle known file formats.
     *
     * @param loaderClass the loader to use (not null)
     */
    private void registerLoaders(Class<? extends AssetLoader> loaderClass) {
        assert loaderClass != null;

        assetManager.registerLoader(loaderClass, "3ds");
        assetManager.registerLoader(loaderClass, "3mf");
        assetManager.registerLoader(loaderClass, "blend");
        assetManager.registerLoader(loaderClass, "bvh");
        assetManager.registerLoader(loaderClass, "dae");
        assetManager.registerLoader(loaderClass, "fbx");
        assetManager.registerLoader(loaderClass, "glb", "gltf");
        assetManager.registerLoader(loaderClass, "lwo");
        assetManager.registerLoader(loaderClass, "obj");
        assetManager.registerLoader(loaderClass, "ply");
        assetManager.registerLoader(loaderClass, "meshxml", "mesh.xml");
        assetManager.registerLoader(loaderClass, "stl");
        /*
         * Assimp provides no interface to import
         * materials, meshes, or skeletons except as part of an AIScene.
         *
         * Also, Assimp doesn't recognize Ogre's .scene file extension
         * and has no suitable importer for that format.
         */
    }

    /**
     * Register asset locators for accessing the selected test asset in the
     * specified group.
     *
     * @param groupName the name of the asset group to access (not null)
     */
    private static void registerLocators(String groupName) {
        Locators.unregisterAll();

        AssetGroup group = findGroup(groupName);
        String assetName = status.selectedAsset();
        String rootPath = group.rootPath(assetName);
        if (rootPath == null) {
            System.out.println("Asset " + MyString.quote(assetName)
                    + " not found in group " + groupName);
        } else {
            rootPath = Heart.fixPath(rootPath);
            Locators.registerFilesystem(rootPath);
        }

        // A classpath locator is needed for J3MDs and such:
        Locators.registerDefault();
    }

    /**
     * Return the camera to its initial position.
     */
    private void repositionCamera() {
        cam.setLocation(new Vector3f(-0.5f, 3.46f, 10.73f));
        cam.setRotation(new Quaternion(0.002f, 0.997137f, -0.0702f, 0.0281f));
    }

    /**
     * Uniformly scale the specified C-G model so that the extent of its longest
     * AABB axis is 4 world units.
     *
     * @param cgModel the model to scale (not null, modified)
     */
    private static void scaleCgm(Spatial cgModel) {
        Vector3f[] minMax = MySpatial.findMinMaxCoords(cgModel);
        Vector3f min = minMax[0];
        Vector3f max = minMax[1];
        Vector3f extent = max.subtract(min);
        float maxExtent = MyMath.max(extent.x, extent.y, extent.z);
        if (maxExtent > 0f) {
            cgModel.scale(4f / maxExtent);
        }
    }

    /**
     * Toggle the armatures between visible and hidden.
     */
    private static void toggleArmatures() {
        showArmatures = !showArmatures;
        for (SkeletonVisualizer visualizer : visualizers) {
            visualizer.setEnabled(showArmatures);
        }
    }

    /**
     * Toggle the default camera between perspective and orthographic (parallel)
     * projections.
     */
    private void toggleProjection() {
        Vector3f location = cam.getLocation(); // alias
        float range = location.length();

        float near = cam.getFrustumNear();
        float far = cam.getFrustumFar();
        range = FastMath.clamp(range, near, far);
        assert range > 0f : range;

        if (cam.isParallelProjection()) { // orthographic -> perspective:
            float yTangent = cam.getFrustumTop() / range;
            float yRadians = 2f * FastMath.atan(yTangent);
            float yDegrees = MyMath.toDegrees(yRadians);
            float aspectRatio = MyCamera.frustumAspectRatio(cam);
            cam.setFrustumPerspective(yDegrees, aspectRatio, near, far);

        } else { // perspective -> orthographic:
            float halfHeight = range * MyCamera.yTangent(cam);
            float halfWidth = range * MyCamera.xTangent(cam);
            cam.setFrustum(
                    near, far, -halfWidth, halfWidth, halfHeight, -halfHeight);
            cam.setParallelProjection(true);
        }
    }
}
