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
import com.jme3.math.ColorRGBA;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.logging.Logger;
import jme3utilities.MyString;
import jme3utilities.SimpleAppState;
import jme3utilities.math.MyArray;
import jme3utilities.math.MyMath;
import jme3utilities.ui.AcorusDemo;

/**
 * Display the CompareLoaders status in an overlay. The overlay consists of
 * status lines, one of which is selected for editing. The overlay is located in
 * the upper-left portion of the display.
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
     * index of the status line for the model group
     */
    final private static int groupStatusLine = 1;
    /**
     * index of the status line for the asset loaders
     */
    final private static int loaderStatusLine = 2;
    /**
     * index of the status line for the model/scene
     */
    final private static int modelStatusLine = 3;
    /**
     * number of lines of text in the overlay
     */
    final private static int numStatusLines = 5;
    /**
     * message logger for this class
     */
    final static Logger logger = Logger.getLogger(TestStatus.class.getName());
    /**
     * fictitious animation name to refer to a model's initial pose
     */
    final static String initialPoseName = " initial pose ";
    /**
     * fictitious animation name for a model with no AnimComposer
     */
    final static String noComposerName = " no AnimComposer ";
    /**
     * list of all model loaders, in ascending lexicographic order
     */
    final private static String[] loaderNames = {
        "Default", "Lwjgl", "LwjglVerbose", "SideBySide"
    };
    /**
     * names of all test models/scenes, in ascending lexicographic order
     */
    private String[] modelNames;
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
    private int selectedLine = modelStatusLine;
    /**
     * name of the selected animation
     */
    private String animationName = noComposerName;
    /**
     * name of the selected model group
     */
    private String groupName = "gltf-sample-models-20";
    /**
     * name of the selected asset loaders
     */
    private String loaderName = "SideBySide";
    /**
     * name of the selected model/scene
     */
    private String modelName;
    /**
     * names of all available animations plus a fictitious animation name, in
     * ascending lexicographic order
     */
    private String[] animationNames = {animationName};
    /**
     * names of all available model groups, in ascending lexicographic order
     */
    final private String[] groupNames;
    /**
     * compose the status text
     */
    final private static StringBuilder builder = new StringBuilder(80);
    // *************************************************************************
    // constructors

    /**
     * Instantiate an uninitialized enabled state.
     *
     * @param modelGroups the names of the model groups to be tested (not null,
     * not empty)
     */
    TestStatus(Set<String> modelGroups) {
        super(true);

        int numGroups = modelGroups.size();
        this.groupNames = new String[numGroups];
        modelGroups.toArray(groupNames);
        Arrays.sort(groupNames);
    }
    // *************************************************************************
    // new methods exposed

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
     * Advance the selected field by the specified amount.
     *
     * @param amount the number of fields to move downward (may be negative)
     */
    void advanceSelectedField(int amount) {
        int firstField = 1;
        int numFields = 4;

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

            case modelStatusLine:
                advanceModel(amount);
                break;

            default:
                throw new IllegalStateException(
                        "selectedLine = " + selectedLine);
        }
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
        return animationName;
    }

    /**
     * Return the selected model group.
     *
     * @return the name of the selected model group (not null, not empty)
     */
    String selectedGroup() {
        assert groupName != null;
        assert !groupName.isEmpty();
        return groupName;
    }

    /**
     * Return the selected loaders.
     *
     * @return the name of the selected asset loader(s) (not null, not empty)
     */
    String selectedLoaders() {
        assert loaderName != null;
        assert !loaderName.isEmpty();
        return loaderName;
    }

    /**
     * Return the selected model/scene.
     *
     * @return the name of the selected model/scene (not null, not empty)
     */
    String selectedModel() {
        assert modelName != null;
        assert !modelName.isEmpty();
        return modelName;
    }

    /**
     * Update the list of available animations using the specified AnimComposer.
     *
     * @param composer the AnimComposer to use (may be null, unaffected)
     */
    void setAnimations(AnimComposer composer) {
        Collection<String> nameSet;
        if (composer == null) {
            this.animationName = noComposerName;
            nameSet = new ArrayList<>(1); // an empty list

        } else {
            this.animationName = initialPoseName;
            /*
             * An AnimComposer may have animations that are not clips, but
             * clips alone are sufficient for testing asset loaders.
             */
            nameSet = composer.getAnimClipsNames();
        }
        int numClips = nameSet.size();
        String[] tempArray = new String[numClips];
        nameSet.toArray(tempArray);

        // Append the selected name (always fictitious) as the final element:
        this.animationNames = new String[numClips + 1];
        System.arraycopy(tempArray, 0, animationNames, 0, numClips);
        this.animationNames[numClips] = animationName;

        Arrays.sort(animationNames);
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
        setModels();
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
        int count = animationNames.length;
        String quotedName = MyString.quote(animationName);
        String message = String.format(
                "Animation #%d of %d:  %s", index, count, quotedName);
        updateStatusLine(animationStatusLine, message);

        index = 1 + Arrays.binarySearch(loaderNames, loaderName);
        count = loaderNames.length;
        message = String.format(
                "Loader #%d of %d:  %s", index, count, loaderName);
        updateStatusLine(loaderStatusLine, message);

        index = 1 + Arrays.binarySearch(groupNames, groupName);
        count = groupNames.length;
        message = String.format(
                "Group #%d of %d:  %s", index, count, groupName);
        updateStatusLine(groupStatusLine, message);

        index = 1 + Arrays.binarySearch(modelNames, modelName);
        count = modelNames.length;
        message = String.format(
                "Model #%d of %d:  %s", index, count, modelName);
        updateStatusLine(modelStatusLine, message);
    }
    // *************************************************************************
    // private methods

    /**
     * Advance the asset-group selection by the specified amount.
     *
     * @param amount the number of values to advance (may be negative)
     */
    private void advanceGroup(int amount) {
        this.groupName
                = AcorusDemo.advanceString(groupNames, groupName, amount);
        setModels();
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
     * Advance the C-G model selection by the specified amount.
     *
     * @param amount the number of values to advance (may be negative)
     */
    private void advanceModel(int amount) {
        this.modelName
                = AcorusDemo.advanceString(modelNames, modelName, amount);
        appInstance.newScene();
    }

    /**
     * Update the list of models available from the selected group.
     */
    private void setModels() {
        ModelGroup group = CompareLoaders.findGroup(groupName);
        if (group == null) {
            throw new IllegalStateException("groupName = " + groupName);
        }
        this.modelNames = group.listModels();
        assert MyArray.isSorted(modelNames);
        this.modelName = modelNames[0];

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

        boolean isOrthographic = appInstance.getCamera().isParallelProjection();
        if (isOrthographic) {
            builder.append("Orthographic view");
        } else {
            builder.append("Perpsective view");
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
