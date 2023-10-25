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

import com.jme3.anim.AnimComposer;
import com.jme3.app.Application;
import com.jme3.app.state.AppStateManager;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.renderer.Camera;
import com.jme3.scene.Spatial;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;
import jme3utilities.MySpatial;
import jme3utilities.MyString;
import jme3utilities.SimpleAppState;
import jme3utilities.math.MyArray;
import jme3utilities.math.MyMath;
import jme3utilities.ui.AcorusDemo;

/**
 * Display the status of the CompareLoaders application in an overlay.
 * <p>
 * The overlay consists of 6 status lines, one of which is selected for editing.
 * The overlay is located in the upper-left corner of the display.
 *
 * @author Stephen Gold sgold@sonic.net
 */
class TestStatus extends SimpleAppState {
    // *************************************************************************
    // constants and loggers

    /**
     * index of the status line for the animation name
     */
    final private static int animationStatusLine = 4;
    /**
     * index of the status line for the asset name
     */
    final private static int assetStatusLine = 3;
    /**
     * index of the status line for the asset group
     */
    final private static int groupStatusLine = 2;
    /**
     * index of the status line for the asset loader(s)
     */
    final private static int loaderStatusLine = 1;
    /**
     * index of the status line for the material name
     */
    final private static int materialStatusLine = 5;
    /**
     * number of lines of text in the overlay
     */
    final private static int numStatusLines = 6;
    /**
     * message logger for this class
     */
    final static Logger logger = Logger.getLogger(TestStatus.class.getName());
    /**
     * fictitious material name to match all materials
     */
    final static String allMaterialsName = " all materials ";
    /**
     * fictitious animation name to refer to a model's initial pose
     */
    final static String initialPoseName = " initial pose ";
    /**
     * fictitious animation name for a model with no animation clips
     */
    final static String noClipsName = " no animation clips ";
    /**
     * list of all model loaders, in ascending lexicographic order
     */
    final private static String[] loaderNames = {
        "Default", "Lwjgl", "LwjglVerbose", "SideBySide"
    };
    // *************************************************************************
    // fields

    /**
     * lines of text displayed in the upper-left corner of the GUI node ([0] is
     * the top line)
     */
    final private BitmapText[] statusLines = new BitmapText[numStatusLines];
    /**
     * application instance
     */
    private CompareLoaders appInstance;
    /**
     * index of the status line being edited (&ge;0)
     */
    private int selectedLine = assetStatusLine;
    /**
     * name of the selected animation
     */
    private String animationName;
    /**
     * name of the selected asset
     */
    private String assetName;
    /**
     * name of the selected asset group
     */
    private String groupName;
    /**
     * name of the selected asset loader(s)
     */
    private String loaderName = "SideBySide";
    /**
     * name of the selected material
     */
    private String materialName;
    /**
     * names of all runnable animations plus a fictitious animation name, in
     * ascending lexicographic order
     */
    private String[] animationNames;
    /**
     * names of all test assets, in ascending lexicographic order
     */
    private String[] assetNames;
    /**
     * names of all accessible asset groups, in ascending lexicographic order
     */
    final private String[] groupNames;
    /**
     * names of all selectable materials plus {@code allMaterialsName}, in
     * ascending lexicographic order
     */
    private String[] materialNames;
    /**
     * compose the status text
     */
    final private static StringBuilder builder = new StringBuilder(80);
    // *************************************************************************
    // constructors

    /**
     * Instantiate an uninitialized enabled state.
     *
     * @param assetGroups the names of the asset groups to be tested (not null,
     * not empty)
     */
    TestStatus(Collection<String> assetGroups) {
        super(true);

        int numGroups = assetGroups.size();
        this.groupNames = new String[numGroups];
        assetGroups.toArray(groupNames);
        Arrays.sort(groupNames);

        this.groupName = groupNames[0];
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Expand the list of selectable animations and the list of selectable
     * materials using the specified scene-graph subtree. May alter the selected
     * animation.
     *
     * @param subtree the subtree to analyze (may be null, unaffected)
     */
    void addAnimationsAndMaterials(Spatial subtree) {
        Set<String> nameSet = new TreeSet<>(); // an empty set

        // Enumerate the animations:
        AnimComposer composer = findComposer(subtree);
        if (composer != null) {
            /*
             * An AnimComposer may contain animations that are not clips, but
             * clips alone are sufficient for testing asset loaders.
             */
            Set<String> addSet = composer.getAnimClipsNames();
            for (String name : addSet) {
                if (name != null && !name.isEmpty()) {
                    nameSet.add(name);
                }
            }
        }
        nameSet.addAll(Arrays.asList(animationNames));
        nameSet.remove(initialPoseName);
        nameSet.remove(noClipsName);
        this.animationName
                = nameSet.isEmpty() ? noClipsName : initialPoseName;
        nameSet.add(animationName);

        int numNames = nameSet.size();
        this.animationNames = new String[numNames];
        nameSet.toArray(animationNames);
        Arrays.sort(animationNames);

        List<Material> materialList = new ArrayList<>(20); // an empty list
        if (subtree != null) {
            MySpatial.listMaterials(subtree, materialList);
        }

        // Enumerate the materials:
        nameSet.clear(); // an empty set
        for (Material material : materialList) {
            String name = material.getName();
            if (name != null && !name.isEmpty()) {
                nameSet.add(name);
            }
        }
        nameSet.addAll(Arrays.asList(materialNames));

        // If there's only one real material, don't list it as an option:
        assert nameSet.contains(allMaterialsName);
        if (nameSet.size() == 2) {
            nameSet.clear();
            nameSet.add(allMaterialsName);
        }

        numNames = nameSet.size();
        this.materialNames = new String[numNames];
        nameSet.toArray(materialNames);
        Arrays.sort(materialNames);
    }

    /**
     * Advance the animation selection by the specified amount.
     *
     * @param amount the number of values to advance (may be negative)
     */
    void advanceAnimation(int amount) {
        this.animationName = AcorusDemo.advanceString(
                animationNames, animationName, amount);
        appInstance.loadAnimation(animationName);
    }

    /**
     * Advance the material selection by the specified amount.
     *
     * @param amount the number of values to advance (may be negative)
     */
    void advanceMaterial(int amount) {
        this.materialName
                = AcorusDemo.advanceString(materialNames, materialName, amount);
        appInstance.showMaterial(materialName);
    }

    /**
     * Advance the selected field by the specified amount.
     *
     * @param amount the number of fields to move downward (may be negative)
     */
    void advanceSelectedField(int amount) {
        int firstField = 1;
        int numFields = 5;

        int selectedField = selectedLine - firstField;
        int sum = selectedField + amount;
        selectedField = MyMath.modulo(sum, numFields);
        this.selectedLine = selectedField + firstField;
    }

    /**
     * Advance the value of the selected field by the specified amount.
     *
     * @param amount the number of values to advance (may be negative)
     */
    void advanceValue(int amount) {
        switch (selectedLine) {
            case animationStatusLine:
                advanceAnimation(amount);
                break;

            case groupStatusLine:
                advanceGroup(amount);
                break;

            case loaderStatusLine:
                advanceLoader(amount);
                break;

            case assetStatusLine:
                advanceAsset(amount);
                break;

            case materialStatusLine:
                advanceMaterial(amount);
                break;

            default:
                throw new IllegalStateException(
                        "selectedLine = " + selectedLine);
        }
    }

    /**
     * Reset the lists of selectable animations and materials.
     */
    void resetAnimationsAndMaterials() {
        this.animationName = noClipsName;
        this.animationNames = new String[]{animationName};

        this.materialName = allMaterialsName;
        this.materialNames = new String[]{materialName};
    }

    /**
     * Update the GUI layout and proposed settings after a resize.
     *
     * @param newWidth the new width of the framebuffer (in pixels, &gt;0)
     * @param newHeight the new height of the framebuffer (in pixels, &gt;0)
     */
    void resize(int newWidth, int newHeight) {
        if (isInitialized()) {
            for (int lineIndex = 0; lineIndex < numStatusLines; ++lineIndex) {
                float y = newHeight - 20f * lineIndex;
                statusLines[lineIndex].setLocalTranslation(0f, y, 0f);
            }
        }
    }

    /**
     * Return the selected animation.
     *
     * @return the name of an animation clip, or a fictitious animation name
     * (not null)
     */
    String selectedAnimation() {
        assert animationName != null;
        return animationName;
    }

    /**
     * Return the selected test asset.
     *
     * @return the name of the selected asset (not null, not empty)
     */
    String selectedAsset() {
        assert assetName != null;
        assert !assetName.isEmpty();
        return assetName;
    }

    /**
     * Return the selected asset group.
     *
     * @return the name of the selected group (not null, not empty)
     */
    String selectedGroup() {
        assert groupName != null;
        assert !groupName.isEmpty();
        return groupName;
    }

    /**
     * Return the selected asset loader(s).
     *
     * @return the name of the selected asset loader(s) (not null, not empty)
     */
    String selectedLoaders() {
        assert loaderName != null;
        assert !loaderName.isEmpty();
        return loaderName;
    }
    // *************************************************************************
    // ActionAppState methods

    /**
     * Clean up this AppState during the first update after it gets detached.
     * Should be invoked only by a subclass or by the AppStateManager.
     */
    @Override
    public void cleanup() {
        super.cleanup();

        // Remove the status lines from the guiNode.
        for (int i = 0; i < numStatusLines; ++i) {
            statusLines[i].removeFromParent();
        }
    }

    /**
     * Initialize this AppState on the first update after it gets attached.
     *
     * @param sm application's state manager (not null)
     * @param app application which owns this state (not null)
     */
    @Override
    public void initialize(AppStateManager sm, Application app) {
        super.initialize(sm, app);

        this.appInstance = (CompareLoaders) app;
        BitmapFont guiFont
                = assetManager.loadFont("Interface/Fonts/Default.fnt");

        // Add status lines to the guiNode.
        for (int lineIndex = 0; lineIndex < numStatusLines; ++lineIndex) {
            statusLines[lineIndex] = new BitmapText(guiFont);
            float y = cam.getHeight() - 20f * lineIndex;
            statusLines[lineIndex].setLocalTranslation(0f, y, 0f);
            guiNode.attachChild(statusLines[lineIndex]);
        }

        assert MyArray.isSorted(loaderNames);
        setAssets();
        resetAnimationsAndMaterials();
    }

    /**
     * Callback to update this AppState prior to rendering. (Invoked once per
     * frame while the state is attached and enabled.)
     *
     * @param tpf time interval between frames (in seconds, &ge;0)
     */
    @Override
    public void update(float tpf) {
        super.update(tpf);

        updateStatusText();

        int index = 1 + Arrays.binarySearch(animationNames, animationName);
        assert index > 0;
        int count = animationNames.length;
        String quotedName = MyString.quote(animationName);
        String text = String.format(
                "Animation #%d of %d:  %s", index, count, quotedName);
        updateStatusLine(animationStatusLine, text);

        index = 1 + Arrays.binarySearch(loaderNames, loaderName);
        assert index > 0;
        count = loaderNames.length;
        text = String.format("Loader #%d of %d:  %s", index, count, loaderName);
        updateStatusLine(loaderStatusLine, text);

        index = 1 + Arrays.binarySearch(groupNames, groupName);
        assert index > 0;
        count = groupNames.length;
        text = String.format("Group #%d of %d:  %s", index, count, groupName);
        updateStatusLine(groupStatusLine, text);

        index = 1 + Arrays.binarySearch(materialNames, materialName);
        assert index > 0;
        count = materialNames.length;
        quotedName = MyString.quote(materialName);
        text = String.format(
                "Material #%d of %d:  %s", index, count, quotedName);
        updateStatusLine(materialStatusLine, text);

        index = 1 + Arrays.binarySearch(assetNames, assetName);
        assert index > 0;
        count = assetNames.length;
        quotedName = MyString.quote(assetName);
        text = String.format("Asset #%d of %d:  %s", index, count, quotedName);
        updateStatusLine(assetStatusLine, text);
    }
    // *************************************************************************
    // private methods

    /**
     * Advance the asset selection by the specified amount.
     *
     * @param amount the number of values to advance (may be negative)
     */
    private void advanceAsset(int amount) {
        this.assetName
                = AcorusDemo.advanceString(assetNames, assetName, amount);
        appInstance.newScene();
    }

    /**
     * Advance the asset-group selection by the specified amount.
     *
     * @param amount the number of values to advance (may be negative)
     */
    private void advanceGroup(int amount) {
        this.groupName
                = AcorusDemo.advanceString(groupNames, groupName, amount);
        setAssets();
    }

    /**
     * Advance the asset-loader selection by the specified amount.
     *
     * @param amount the number of values to advance (may be negative)
     */
    private void advanceLoader(int amount) {
        this.loaderName
                = AcorusDemo.advanceString(loaderNames, loaderName, amount);
        appInstance.newScene();
    }

    /**
     * Access the first AnimComposer (if any) in the specified scene-graph
     * subtree.
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
     * Update the list of assets available from the selected group.
     */
    private void setAssets() {
        AssetGroup group = CompareLoaders.findGroup(groupName);
        if (group == null) {
            throw new IllegalStateException("groupName = " + groupName);
        }
        this.assetNames = group.listAssets();
        assert assetNames.length > 0 : groupName;
        assert MyArray.isSorted(assetNames) : groupName;
        this.assetName = assetNames[0];

        appInstance.newScene();
    }

    /**
     * Update the indexed status line.
     *
     * @param lineIndex which status line (&ge;0)
     * @param text the text to display, not including the arrow, if any
     */
    private void updateStatusLine(int lineIndex, String text) {
        BitmapText spatial = statusLines[lineIndex];

        if (lineIndex == selectedLine) {
            spatial.setColor(ColorRGBA.Yellow);
            spatial.setText("-> " + text);
        } else {
            spatial.setColor(ColorRGBA.White);
            spatial.setText(" " + text);
        }
    }

    /**
     * Update the status text (the top status line).
     */
    private void updateStatusText() {
        builder.setLength(0);

        Camera camera = appInstance.getCamera();
        boolean isOrthographic = camera.isParallelProjection();
        if (isOrthographic) {
            builder.append("Orthographic view");
        } else {
            builder.append("Perspective view");
        }

        boolean worldAxes = appInstance.areWorldAxesEnabled();
        int numVisible = CompareLoaders.countVisibleArmatures();
        if (worldAxes || numVisible > 0) {
            builder.append(" with ");
        }
        if (worldAxes) {
            builder.append("world axes ");
        }
        if (worldAxes && numVisible > 0) {
            builder.append("and ");
        }
        if (numVisible == 1) {
            builder.append("a visible armature");
        } else if (numVisible > 1) {
            builder.append(numVisible);
            builder.append(" visible armatures");
        }

        if (appInstance.isPaused()) {
            builder.append(", PAUSED");
        }

        String text = builder.toString();
        statusLines[0].setText(text);
    }
}
