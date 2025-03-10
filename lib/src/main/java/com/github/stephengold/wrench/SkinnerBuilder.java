/*
 Copyright (c) 2023-2024, Stephen Gold

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

import com.jme3.anim.Armature;
import com.jme3.anim.Joint;
import com.jme3.anim.SkinningControl;
import com.jme3.math.Matrix4f;
import com.jme3.math.Transform;
import com.jme3.scene.Spatial;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import jme3utilities.MyString;
import jme3utilities.Validate;
import org.lwjgl.PointerBuffer;
import org.lwjgl.assimp.AIMatrix4x4;
import org.lwjgl.assimp.AINode;

/**
 * Gather the data needed to construct a JMonkeyEngine SkinningControl.
 *
 * @author Stephen Gold sgold@sonic.net
 */
class SkinnerBuilder {
    // *************************************************************************
    // constants and loggers

    /**
     * message logger for this class
     */
    final private static Logger logger
            = Logger.getLogger(SkinnerBuilder.class.getName());
    // *************************************************************************
    // fields

    /**
     * true if creation of a {@code SkinningControl} has begun, otherwise false
     */
    private boolean doneAddingJoints = false;
    /**
     * map joint IDs (indices into the {@code Armature}) to joints
     */
    final private Map<Integer, Joint> idToJoint = new TreeMap<>();
    /**
     * map joint IDs (indices into the {@code Armature}) to bind matrices
     */
    final private Map<Integer, Matrix4f> idToBind = new TreeMap<>();
    /**
     * map joint IDs (indices into the {@code Armature}) to offset matrices
     */
    final private Map<Integer, Matrix4f> idToOffset = new TreeMap<>();
    /**
     * map known bone names to joint IDs (indices into the {@code Armature})
     */
    final private Map<String, Integer> nameToId = new TreeMap<>();
    // *************************************************************************
    // new methods exposed

    /**
     * If one or more bones are known, create a SkinningControl and add it to
     * the specified Spatial.
     * <p>
     * Once this method is invoked, no more IDs can be assigned and no more
     * joints can be created.
     *
     * @param addControl where to add the control (not null, modified)
     * @return the new SkinningControl, or null if none was created
     * @throws IOException if some joints haven't been instantiated
     */
    SkinningControl buildAndAddTo(Spatial addControl) throws IOException {
        Validate.nonNull(addControl, "add control");

        int numJoints = countBones();
        if (idToJoint.size() != numJoints) {
            throw new IOException("Some joints haven't been instantiated, "
                    + "numJoints=" + numJoints);
        }
        assert idToOffset.size() == numJoints : idToOffset.size();

        SkinningControl result = null;
        if (numJoints > 0) {
            Armature armature = buildArmature();
            result = new SkinningControl(armature);
            addControl.addControl(result);
        }

        return result;
    }

    /**
     * Create joints for the specified bone node and its descendants.
     * <p>
     * Note: recursive!
     *
     * @param aiNode the root of the subtree to process (not null, unaffected)
     * @return a new instance corresponding to {@code aiNode} (not null)
     * @throws IOException if multiple bones have the same name
     */
    Joint createJoints(AINode aiNode) throws IOException {
        assert !doneAddingJoints;
        String boneName = aiNode.mName().dataString();

        // Get the joint ID, assigning a new one if not already assigned:
        int jointId = jointId(boneName);
        /*
         * Create a Joint, setting its name, ID, and children,
         * but without configuring its bind matrix or initial local transform:
         */
        Joint result = new Joint(boneName);
        result.setId(jointId);
        //System.out.println(
        //"new joint " + MyString.quote(boneName) + " id=" + jointId);

        int numChildren = aiNode.mNumChildren();
        if (numChildren > 0) {
            PointerBuffer pChildren = aiNode.mChildren();
            for (int childIndex = 0; childIndex < numChildren; ++childIndex) {
                long handle = pChildren.get(childIndex);
                AINode aiChild = AINode.createSafe(handle);

                Joint childJoint = createJoints(aiChild);
                result.addChild(childJoint); // The child's parent is set here.
            }
        }

        Joint oldJoint = idToJoint.put(jointId, result);
        if (oldJoint != null) {
            String quotedName = MyString.quote(boneName);
            throw new IOException("Found multiple bones named " + quotedName);
        }

        // Convert the offset matrix and save it for configureJoint():
        AIMatrix4x4 transformation = aiNode.mTransformation();
        Matrix4f offsetMatrix = ConversionUtils.convertMatrix(transformation);
        idToOffset.put(jointId, offsetMatrix);

        return result;
    }

    /**
     * Test whether the argument is the name of a bone that the builder has
     * already seen.
     *
     * @param name the String to test (not null)
     * @return true if it's a bone name, otherwise false
     */
    boolean isKnownBone(String name) {
        Validate.nonNull(name, "name");
        boolean result = nameToId.containsKey(name);
        return result;
    }

    /**
     * Return the joint ID of the named bone. If the builder hasn't seen the
     * name before, a new ID is assigned sequentially.
     *
     * @param boneName the name of the bone (not null, unaffected)
     * @return a joint ID (an index into the Armature, &ge;0)
     */
    int jointId(String boneName) {
        assert !doneAddingJoints;
        Validate.nonNull(boneName, "bone name");

        int result;
        if (isKnownBone(boneName)) {
            result = nameToId.get(boneName);

        } else { // Assign the next available ID:
            result = countBones();
            nameToId.put(boneName, result);
        }

        return result;
    }
    // *************************************************************************
    // private methods

    /**
     * Create an Armature for a new SkinningControl.
     * <p>
     * Once this method is invoked, no more IDs can be assigned and no more
     * joints can be created.
     *
     * @return the new Armature (not null)
     */
    private Armature buildArmature() {
        this.doneAddingJoints = true;

        int numJoints = countBones();
        Joint[] jointArray = new Joint[numJoints];

        // Populate an array of joints:
        for (int jointId = 0; jointId < numJoints; ++jointId) {
            Joint joint = idToJoint.get(jointId);
            assert joint != null : "jointId = " + jointId;
            assert joint.getId() == jointId : "jointId = " + jointId;
            jointArray[jointId] = joint;
        }

        // Set the inverse bind matrix and local transform of each Joint:
        for (int jointId = 0; jointId < numJoints; ++jointId) {
            configureJoint(jointId);
        }
        /*
         * Create an Armature and set the bind and initial transforms
         * of each Joint to its current local transform:
         */
        Armature result = new Armature(jointArray);
        result.update();
        result.saveBindPose();
        result.saveInitialPose();

        return result;
    }

    /**
     * Configure the specified Joint and all of its ancestors.
     * <p>
     * Configuration consists of:<ul>
     * <li>Initializing the joint's local transform.</li>
     * <li>Saving the joint's bind matrix to the {@code idToJoint} map.</li>
     * <li>Setting the joint's inverse bind matrix.</li>
     * </ul>
     * <p>
     * Note: the joint IDs must all be initialized!
     * <p>
     * Note: recursive!
     *
     * @param jointId the ID of the joint to configure (&ge;0, &lt;numJoints)
     */
    private void configureJoint(int jointId) {
        assert doneAddingJoints;
        if (idToBind.containsKey(jointId)) { // The joint is already configured.
            return;
        }
        Joint joint = idToJoint.get(jointId);

        // Initialize the local transform from the offset matrix:
        Matrix4f offset = idToOffset.get(jointId); // alias
        float determinant = offset.determinant();
        if (determinant <= 0f) {
            System.out.flush();
            logger.log(Level.INFO, "determinant = {0}", determinant);
            System.err.flush();
        }
        if (determinant < 0f) { // Work around JME issue #2089:
            offset.m00 *= -1f;
            offset.m10 *= -1f;
            offset.m20 *= -1f;
        }
        Transform initialTransform = new Transform();
        initialTransform.fromTransformMatrix(offset);
        joint.setLocalTransform(initialTransform);

        // Calculate the joint's bind matrix and save it to the map:
        Matrix4f bindMatrix;
        Joint parent = joint.getParent();
        if (parent == null) { // root joint:
            bindMatrix = offset.clone();

        } else { // non-root joint:
            int parentId = parent.getId(); // joint IDs must all be initialized
            configureJoint(parentId);

            Matrix4f parentBind = idToBind.get(parentId); // alias
            bindMatrix = parentBind.mult(offset);
        }
        Matrix4f old = idToBind.put(jointId, bindMatrix);
        assert old == null;

        // Set the joint's inverse bind matrix:
        Matrix4f imbm = bindMatrix.invert();
        joint.setInverseModelBindMatrix(imbm);
    }

    /**
     * Count how many known bones are in the Assimp scene.
     *
     * @return the count (&ge;0)
     */
    private int countBones() {
        int result = nameToId.size();
        return result;
    }
}
