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
import com.jme3.anim.AnimComposer;
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
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jme3utilities.Heart;
import jme3utilities.MyCamera;
import jme3utilities.MySpatial;
import jme3utilities.MyString;
import jme3utilities.debug.Dumper;
import jme3utilities.math.MyMath;
import jme3utilities.ui.AcorusDemo;
import jme3utilities.ui.CameraOrbitAppState;
import jme3utilities.ui.Combo;
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
     * dump the {@code dumpSpatial} to {@code System.out}
     */
    final private static Dumper dumper = new Dumper();
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
     * Load the selected model/scene using the selected asset loaders.
     */
    void loadModel() {
        clearScene();

        Spatial loadedSpatial;
        String selectedLoaders = status.selectedLoaders();
        if (selectedLoaders.equals("SideBySide")) {
            Node leftNode = new Node("Lwjgl parent");
            Node rightNode = new Node("Default parent");

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
        settings.setHeight(500);
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
     * Register the named asset locators.
     *
     * @param locatorName the name of the desired asset locators (not null)
     */
    void registerLocator(String locatorName) {
        Locators.unregisterAll();

        String rootPath;
        switch (locatorName) {
            case "gltf-sample-models-20":
                rootPath = "../../ext/glTF-Sample-Models/2.0/";
                break;

            case "jme3-testdata-31":
                rootPath = "../downloads/jme3-testdata-3.1.0-stable.jar";
                break;

            case "jme3-testdata-36":
                rootPath = "../downloads/jme3-testdata-3.6.1-stable.jar";
                break;

            default:
                throw new IllegalArgumentException(
                        "locatorName = " + locatorName);
        }
        rootPath = Heart.fixPath(rootPath);
        Locators.registerFilesystem(rootPath);

        // The classpath locator is needed for J3MDs and such:
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

        CameraOrbitAppState orbitState
                = new CameraOrbitAppState(cam, "orbit left", "orbit right");
        boolean success = stateManager.attach(orbitState);
        assert success;

        status = new TestStatus();
        success = stateManager.attach(status);
        assert success;

        super.acorusInit();

        dumper.setDumpMatParam(true);

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

        dim.bindSignal("shift", KeyInput.KEY_LSHIFT, KeyInput.KEY_RSHIFT);
        Combo shiftP = new Combo(KeyInput.KEY_P, "shift", true);
        Combo noShiftP = new Combo(KeyInput.KEY_P, "shift", false);

        dim.bind(asDumpModel, noShiftP);
        dim.bind("dump model verbose", shiftP);

        dim.bind(asLoadModel, KeyInput.KEY_NUMPADENTER,
                KeyInput.KEY_NUMPAD5, KeyInput.KEY_RETURN, KeyInput.KEY_L);

        dim.bind("next animation", KeyInput.KEY_N);
        dim.bind("next field", KeyInput.KEY_NUMPAD2, KeyInput.KEY_DOWN);
        dim.bind("next value", KeyInput.KEY_NUMPAD6, KeyInput.KEY_EQUALS);

        dim.bindSignal("orbit left", KeyInput.KEY_LEFT);
        dim.bindSignal("orbit right", KeyInput.KEY_RIGHT);

        dim.bind("previous field", KeyInput.KEY_NUMPAD8, KeyInput.KEY_UP);
        dim.bind("previous value", KeyInput.KEY_NUMPAD4, KeyInput.KEY_MINUS);

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
                    dumpModel(false);
                    return;

                case "dump model verbose":
                    dumpModel(true);
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
        LightProbe lightProbe = (LightProbe) lightList.get(0);
        rootNode.addLight(lightProbe);

        ColorRGBA ambientColor = new ColorRGBA(0.2f, 0.2f, 0.2f, 1f);
        AmbientLight ambient = new AmbientLight(ambientColor);
        rootNode.addLight(ambient);

        Vector3f direction = new Vector3f(1f, -2f, -2f).normalizeLocal();
        DirectionalLight sun = new DirectionalLight(direction);
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
        cam.setLocation(new Vector3f(-0.5f, 3.46f, 10.73f));
        cam.setRotation(new Quaternion(0.002f, 0.997137f, -0.0702f, 0.0281f));
    }

    /**
     * Generate a ModelKey for the specified loaders and selected model/scene.
     *
     * @param loaders the name of the asset loader(s) that will be used (not
     * null, not empty)
     * @return a new instance (not null)
     */
    private ModelKey createModelKey(String loaders) {
        String modelName = status.selectedModel();
        String location = status.selectedLocation();
        System.out.printf("%n%n%n%n======%n"
                + "Using the %s loader(s) to load the %s model from %s...%n%n",
                loaders, modelName, location);

        // Determine the asset path:
        String assetPath;
        switch (location) {
            case "gltf-sample-models-20":
                assetPath = GltfSampleModels.assetPath(modelName);
                break;

            case "jme3-testdata-31":
            case "jme3-testdata-36":
                assetPath = Jme3TestData.assetPath(modelName);
                break;

            default:
                throw new IllegalStateException("location = " + location);

        }
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
     * Dump the loaded model/scene.
     *
     * @param verbose true for a more detailed dump, false for less detailed
     */
    private void dumpModel(boolean verbose) {
        dumper.setDumpMatParam(verbose);
        dumper.dump(dumpSpatial);
        // TODO dump vertex buffers, joints, and animation clips
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
        ModelKey modelKey = createModelKey(loaders);

        Spatial result;
        long startTime = System.nanoTime();
        try {
            result = assetManager.loadModel(modelKey);
            long completionTime = System.nanoTime();
            System.err.flush();
            if (Heart.areAssertionsEnabled()) {
                System.out.printf("%nLoad succeeded.%n======%n");
            } else {
                double elapsedSeconds = 1e-9 * (completionTime - startTime);
                System.out.printf(
                        "%nLoad succeeded; elapsed time = %.3f sec.%n======%n",
                        elapsedSeconds);
            }

        } catch (AssetLoadException | AssetNotFoundException exception) {
            System.err.flush();
            System.out.println(exception);
            System.out.printf("%nLoad failed.%n======%n");

            result = new Node("Load failed");
        }
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
     * Toggle the default camera between perspective and orthographic (parallel)
     * projections.
     */
    private void toggleProjection() {
        float far = cam.getFrustumFar();
        float near = cam.getFrustumNear();
        float range = cam.getLocation().length();
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
