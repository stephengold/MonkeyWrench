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
import com.jme3.anim.AnimClip;
import com.jme3.anim.AnimComposer;
import com.jme3.anim.AnimTrack;
import com.jme3.anim.MorphTrack;
import com.jme3.anim.SkinningControl;
import com.jme3.anim.util.AnimMigrationUtils;
import com.jme3.app.StatsAppState;
import com.jme3.asset.AssetLoadException;
import com.jme3.asset.AssetLoader;
import com.jme3.asset.AssetNotFoundException;
import com.jme3.asset.ModelKey;
import com.jme3.input.KeyInput;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.light.LightList;
import com.jme3.light.LightProbe;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.mesh.MorphTarget;
import com.jme3.scene.plugins.OBJLoader;
import com.jme3.scene.plugins.blender.BlenderLoader;
import com.jme3.scene.plugins.fbx.FbxLoader;
import com.jme3.scene.plugins.gltf.GlbLoader;
import com.jme3.scene.plugins.gltf.GltfLoader;
import com.jme3.scene.plugins.ogre.MeshLoader;
import com.jme3.shadow.DirectionalLightShadowRenderer;
import com.jme3.system.AppSettings;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import jme3utilities.Heart;
import jme3utilities.InfluenceUtil;
import jme3utilities.MyCamera;
import jme3utilities.MyMesh;
import jme3utilities.MySpatial;
import jme3utilities.MyString;
import jme3utilities.debug.Dumper;
import jme3utilities.debug.SkeletonVisualizer;
import jme3utilities.math.MyMath;
import jme3utilities.ui.AcorusDemo;
import jme3utilities.ui.CameraOrbitAppState;
import jme3utilities.ui.InputMode;
import jme3utilities.ui.Locators;

/**
 * An Acorus application to compare various asset loaders on test assets.
 *
 * @author Stephen Gold sgold@sonic.net
 */
class CompareLoaders extends AcorusDemo {
    // *************************************************************************
    // constants and loggers

    /**
     * message logger for this class
     */
    final static Logger logger
            = Logger.getLogger(CompareLoaders.class.getName());
    /**
     * application name (for the title bar of the app's window)
     */
    final private static String applicationName
            = CompareLoaders.class.getSimpleName();
    /**
     * action string to dump the loaded model
     */
    final private static String asDumpModel = "dump model";
    /**
     * action string to load the selected model using the selected loader
     */
    final private static String asLoadModel = "load model";
    // *************************************************************************
    // fields

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
     * map group names to model groups
     */
    final private static Map<String, ModelGroup> groupMap = new TreeMap<>();
    /**
     * scene-graph subtree to dump
     */
    private static Spatial dumpSpatial = new Node("No model(s) loaded.");
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
     * Find the named ModelGroup.
     *
     * @param groupName the name of the group to find (not null)
     * @return the pre-existing instance, or null if not recognized
     */
    static ModelGroup findGroup(String groupName) {
        ModelGroup result = groupMap.get(groupName);
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
                case TestStatus.noComposerName:
                    composer.removeCurrentAction();
                    break;

                default:
                    if (composer.getAnimClip(animationName) == null) {
                        logger.log(Level.WARNING, "Clip not found: {0}",
                                MyString.quote(animationName));
                        for (String n : composer.getAnimClipsNames()) {
                            System.out.println(MyString.quote(n));
                        }
                    } else {
                        composer.setCurrentAction(animationName);
                    }
            }
        }

        switch (animationName) {
            case TestStatus.initialPoseName:
            case TestStatus.noComposerName:
                List<SkinningControl> skinners = MySpatial.listControls(
                        rootNode, SkinningControl.class, null);
                for (SkinningControl skinner : skinners) {
                    skinner.getArmature().applyInitialPose();
                }
                break;

            default:
        }
    }

    /**
     * Load the selected model/scene using the selected asset loader(s).
     */
    void loadModel() {
        clearScene();

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
        settings.setHeight(544);
        settings.setResizable(true);
        settings.setSamples(4); // anti-aliasing
        settings.setTitle(title); // Customize the window's title bar.
        settings.setWidth(800);
        application.setSettings(settings);
        /*
         * The AWT settings dialog interferes with LWJGL v3
         * on macOS and Raspbian, so don't show it!
         */
        application.setShowSettings(false);
        application.start();
    }

    /**
     * Clear the scene, then reset the loaded model including its animation
     * list.
     */
    void newScene() {
        clearScene();
        dumpSpatial = new Node("No model(s) loaded.");
        status.setAnimations(null);
    }

    /**
     * Register the named asset loaders.
     *
     * @param loaderName the name of the desired asset loaders (not null)
     */
    void registerLoader(String loaderName) {
        switch (loaderName) {
            case "Default":
                registerDefaultLoaders();
                break;

            case "Lwjgl":
            case "LwjglVerbose":
                registerLoaders(LwjglAssetLoader.class);
                break;

            default:
                throw new IllegalArgumentException(
                        "loaderName = " + loaderName);
        }
    }

    /**
     * Register asset locators for accessing the selected model/scene in the
     * specified group.
     *
     * @param groupName the name of the model group to access (not null)
     */
    void registerLocators(String groupName) {
        Locators.unregisterAll();

        ModelGroup group = findGroup(groupName);
        String modelName = status.selectedModel();
        String rootPath = group.rootPath(modelName);
        if (rootPath == null) {
            System.out.println("Model name " + MyString.quote(modelName)
                    + " not recognized for group " + groupName);
        } else {
            rootPath = Heart.fixPath(rootPath);
            Locators.registerFilesystem(rootPath);
        }

        // A classpath locator is needed for J3MDs and such:
        Locators.registerDefault();
    }
    // *************************************************************************
    // AcorusDemo methods

    /**
     * Initialize the application.
     */
    @Override
    public void acorusInit() {
        String disEn = Heart.areAssertionsEnabled() ? "en" : "dis";
        logger.log(Level.WARNING, "Assertions are {0}abled.", disEn);

        renderer.setDefaultAnisotropicFilter(8);

        CameraOrbitAppState orbitState
                = new CameraOrbitAppState(cam, "orbit left", "orbit right");
        boolean success = stateManager.attach(orbitState);
        assert success;

        addModelGroups();
        status = new TestStatus(groupMap.keySet());
        success = stateManager.attach(status);
        assert success;

        super.acorusInit();

        dumper.setDumpTransform(true);

        // Hide the render-statistics overlay:
        stateManager.getState(StatsAppState.class).toggleStats();

        // Set the background to light blue:
        ColorRGBA backgroundColor = new ColorRGBA(0.2f, 0.2f, 1f, 1f);
        viewPort.setBackgroundColor(backgroundColor);

        // Set up the initial scene:
        addLighting();
        configureCamera();
        clearScene();

        getHelpBuilder().setBackgroundColor(ColorRGBA.Blue);
    }

    /**
     * Add application-specific hotkey bindings and override existing ones.
     */
    @Override
    public void moreDefaultBindings() {
        InputMode dim = getDefaultInputMode();

        dim.bindSignal("control", KeyInput.KEY_LCONTROL, KeyInput.KEY_RCONTROL);
        dim.bindSignal("shift", KeyInput.KEY_LSHIFT, KeyInput.KEY_RSHIFT);

        dim.bind(asDumpModel, KeyInput.KEY_P);

        dim.bind(asLoadModel, KeyInput.KEY_NUMPADENTER,
                KeyInput.KEY_NUMPAD5, KeyInput.KEY_RETURN, KeyInput.KEY_L);

        dim.bind("next animation", KeyInput.KEY_N);
        dim.bind("next field", KeyInput.KEY_NUMPAD2, KeyInput.KEY_DOWN);
        dim.bind("next value", KeyInput.KEY_NUMPAD6, KeyInput.KEY_EQUALS);

        dim.bindSignal("orbit left", KeyInput.KEY_LEFT);
        dim.bindSignal("orbit right", KeyInput.KEY_RIGHT);

        dim.bind("previous field", KeyInput.KEY_NUMPAD8, KeyInput.KEY_UP);
        dim.bind("previous value", KeyInput.KEY_NUMPAD4, KeyInput.KEY_MINUS);

        dim.bind("reposition camera", KeyInput.KEY_F7);

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
                case asDumpModel:
                    boolean verbose = this.getSignals().test("shift");
                    boolean vertexData = this.getSignals().test("control");
                    dumpModel(verbose, vertexData);
                    return;

                case asLoadModel:
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

        ColorRGBA ambientColor = new ColorRGBA(0.2f, 0.2f, 0.2f, 1f);
        AmbientLight ambient = new AmbientLight(ambientColor);
        ambient.setName("ambient");
        rootNode.addLight(ambient);

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
     * Add the specified model group to {@code groupMap} if the group is
     * accessible.
     *
     * @param groupName a name to identify the group (not null)
     * @param group the group to add (not null, alias created)
     */
    static private void addModelGroup(String groupName, ModelGroup group) {
        if (group.isAccessible()) {
            groupMap.put(groupName, group);
        }
    }

    /**
     * Add accessible model groups to {@code groupMap}.
     */
    static private void addModelGroups() {
        addModelGroup(
                "gltf-sample-models-20", new GltfSampleModels("2.0", "glTF"));
        addModelGroup("gltf-sample-models-20-binary",
                new GltfSampleModels("2.0", "glTF-Binary"));
        addModelGroup("gltf-sample-models-20-draco",
                new GltfSampleModels("2.0", "glTF-Draco"));
        addModelGroup("gltf-sample-models-20-embedded",
                new GltfSampleModels("2.0", "glTF-Embedded"));

        addModelGroup("jme3-testdata-31", new Jme3TestData("3.1.0-stable"));
        addModelGroup("jme3-testdata-36", new Jme3TestData("3.6.1-stable"));

        addModelGroup("mixamo-dae", new MixamoData("dae"));

        if (groupMap.isEmpty()) {
            throw new RuntimeException("No test assets were found.");
        }
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
     * Generate a ModelKey for the specified loaders and selected model/scene.
     *
     * @param loaders the name of the asset loader(s) that will be used (not
     * null, not empty)
     * @return a new instance (not null)
     */
    private ModelKey createModelKey(String loaders) {
        String groupName = status.selectedGroup();
        ModelGroup group = findGroup(groupName);

        // Determine the asset path:
        String modelName = status.selectedModel();
        String assetPath = group.assetPath(modelName);
        if (assetPath == null) {
            throw new RuntimeException(
                    "No known path for model " + MyString.quote(modelName));
        }

        // Generate the key:
        ModelKey result;
        switch (loaders) {
            case "Default":
                result = new ModelKey(assetPath);
                break;

            case "Lwjgl":
                LwjglAssetKey key = new LwjglAssetKey(assetPath);
                key.setVerboseLogging(false);
                result = key;
                break;

            case "LwjglVerbose":
                key = new LwjglAssetKey(assetPath);
                key.setVerboseLogging(true);
                result = key;
                break;

            default:
                throw new IllegalStateException("loaders = " + loaders);
        }

        return result;
    }

    /**
     * Print details about the specified AnimClip.
     *
     * @param clip the clip to dump (not null, unaffected)
     */
    private static void dumpClip(AnimClip clip) {
        AnimTrack[] tracks = clip.getTracks();
        System.out.println();
        System.out.println("clip: " + MyString.quote(clip.getName())
                + " with " + tracks.length + " tracks.");

        for (AnimTrack track : tracks) {
            System.out.println(track.getClass().getSimpleName());
            if (track instanceof MorphTrack) {
                MorphTrack morphTrack = (MorphTrack) track;

                float[] times = morphTrack.getTimes();
                System.out.print("times (" + times.length + ") ");
                for (float time : times) {
                    System.out.print(time + " ");
                }

                float[] weights = morphTrack.getWeights();
                System.out.println();
                System.out.print("weights (" + weights.length + ") ");
                for (float weight : weights) {
                    System.out.print(weight + " ");
                }
                System.out.println();
            }
        }
    }

    /**
     * Dump the loaded model/scene.
     *
     * @param verbose true for a more detailed dump (with render-queue buckets,
     * material parameters and overrides), false for less detail
     * @param vertexData true to dump vertex data, false to omit vertex data
     */
    private void dumpModel(boolean verbose, boolean vertexData) {
        boolean worldAxesWereEnabled = areWorldAxesEnabled();
        if (worldAxesWereEnabled) {
            toggleWorldAxes();
        }

        dumper.setDumpBucket(verbose);
        dumper.setDumpMatParam(verbose);
        dumper.setDumpOverride(verbose);

        dumper.setDumpVertex(vertexData);
        dumper.dump(dumpSpatial);

        for (Mesh mesh : MyMesh.listMeshes(dumpSpatial, null)) {
            if (mesh.hasMorphTargets()) {
                //dumpMorphTargets(mesh);
            }
        }

        List<AnimComposer> list = MySpatial.listControls(
                dumpSpatial, AnimComposer.class, null);
        for (AnimComposer composer : list) {
            for (AnimClip clip : composer.getAnimClips()) {
                //dumpClip(clip);
            }
        }

        if (worldAxesWereEnabled) {
            toggleWorldAxes();
        }
    }

    /**
     * Print details about the morph targets in the specified mesh.
     *
     * @param mesh the clip to analyze (not null, unaffected)
     */
    private static void dumpMorphTargets(Mesh mesh) {
        int patchVertexCount = mesh.getPatchVertexCount();
        System.out.println("patchVertexCount = " + patchVertexCount);
        MorphTarget[] targets = mesh.getMorphTargets();
        System.out.println("numTargets = " + targets.length);
        for (MorphTarget target : targets) {
            //String targetName = target.getName();
            EnumMap<VertexBuffer.Type, FloatBuffer> bufferMap
                    = target.getBuffers();
            System.out.println("targetBuffers: ");
            for (VertexBuffer.Type bufferType : bufferMap.keySet()) {
                FloatBuffer floatBuffer = bufferMap.get(bufferType);
                int capacity = floatBuffer.capacity();
                System.out.printf(" %s (%d) ", bufferType, capacity);
                for (int floatIndex = 0; floatIndex < capacity; ++floatIndex) {
                    float fValue = floatBuffer.get(floatIndex);
                    System.out.print(" " + fValue);
                }
                System.out.println();
            }
            System.out.println();
        }
    }

    /**
     * Access the first AnimComposer (if any) in the specified subtree.
     *
     * @param subtree the subtree to analyze (not null)
     * @return the pre-existing control, or null if none
     */
    private static AnimComposer findComposer(Spatial subtree) {
        List<AnimComposer> composers
                = MySpatial.listControls(subtree, AnimComposer.class, null);
        int numComposers = composers.size();

        AnimComposer result;
        switch (numComposers) {
            case 0:
                result = null;
                break;

            case 1:
                result = composers.get(0);
                break;

            default:
                result = composers.get(0);
                logger.warning("Multiple anim composers in subtree.");
                break;
        }

        return result;
    }

    /**
     * Load the selected model/scene using the specified asset loaders.
     *
     * @param loaders the name of the asset loader(s) that will be used (not
     * null, not empty)
     * @return the root node of the loaded model, or a dummy node if the load
     * failed (not null)
     */
    private Spatial loadModel(String loaders) {
        assetManager.clearCache();

        String modelName = status.selectedModel();
        String groupName = status.selectedGroup();
        System.out.printf("%n%n%n%n======%n"
                + "Using the %s loader(s) to load the %s model from %s ...%n%n",
                loaders, modelName, groupName);

        ModelKey modelKey = createModelKey(loaders);

        Spatial result;
        long startTime = System.nanoTime();
        try {
            result = assetManager.loadModel(modelKey);
            long completionTime = System.nanoTime();

            System.err.flush();
            System.out.printf("%nLoad of %s succeeded", modelName);
            if (!Heart.areAssertionsEnabled()) {
                double elapsedSeconds = 1e-9 * (completionTime - startTime);
                System.out.printf("; elapsed time = %.3f sec", elapsedSeconds);
            }

        } catch (AssetLoadException | AssetNotFoundException
                | NullPointerException
                | UnsupportedOperationException exception) {
            result = new Node("Load failed");

            System.err.flush();
            System.out.println(exception);
            System.out.printf("%nLoad of %s from %s using %s failed",
                    modelName, groupName, loaders);
        }
        System.out.printf(".%n======%n");
        /*
         * If the loaded model uses the old animation system,
         * convert it to the new one.
         */
        AnimMigrationUtils.migrate(result);

        AnimComposer composer = findComposer(result);
        status.setAnimations(composer);

        int numVertices = MySpatial.countVertices(result);
        if (numVertices > 1) {
            // Scale the model and center it directly above the origin:
            scaleCgm(result);
            centerCgm(result);
        }

        // Add a SkeletonVisualizer for each SkinningControl:
        List<SkinningControl> skinners
                = MySpatial.listControls(result, SkinningControl.class, null);
        for (SkinningControl skinner : skinners) {
            SkeletonVisualizer visualizer
                    = new SkeletonVisualizer(assetManager, skinner);
            result.addControl(visualizer);
            visualizers.add(visualizer);

            InfluenceUtil.hideNonInfluencers(visualizer, skinner);
            visualizer.setEnabled(showArmatures);
            visualizer.setLineColor(ColorRGBA.Red);
        }

        String animationName = status.selectedAnimation();
        loadAnimation(animationName);

        return result;
    }

    /**
     * Register JMonkeyEngine's customary loaders to handle known model/scene
     * file formats.
     * <p>
     * Compare with the "General.cfg" file in the jme3-core resources.
     */
    private void registerDefaultLoaders() {
        assetManager.registerLoader(BlenderLoader.class, "blend");
        assetManager.registerLoader(null, "dae"); // no loader provided
        assetManager.registerLoader(FbxLoader.class, "fbx");
        assetManager.registerLoader(GlbLoader.class, "glb");
        assetManager.registerLoader(GltfLoader.class, "gltf");
        assetManager.registerLoader(OBJLoader.class, "obj");
        assetManager.registerLoader(MeshLoader.class, "meshxml", "mesh.xml");
    }

    /**
     * Register the specified loader to handle known model/scene file formats.
     *
     * @param loaderClass the loader to use (not null)
     */
    private void registerLoaders(Class<? extends AssetLoader> loaderClass) {
        assert loaderClass != null;

        assetManager.registerLoader(loaderClass, "blend");
        assetManager.registerLoader(loaderClass, "dae");
        assetManager.registerLoader(loaderClass, "fbx");
        assetManager.registerLoader(loaderClass, "glb", "gltf");
        assetManager.registerLoader(loaderClass, "obj");
        assetManager.registerLoader(loaderClass, "meshxml", "mesh.xml");
        /*
         * Assimp provides no interface to import
         * materials, meshes, or skeletons except as part of a model/scene.
         *
         * Also, Assimp doesn't recognize Ogre's .scene file extension
         * and has no suitable importer for that format.
         */
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
        float range = cam.getLocation().length();

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
