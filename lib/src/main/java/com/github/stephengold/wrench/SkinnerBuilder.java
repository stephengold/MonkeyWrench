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

import com.jme3.anim.Armature;
import com.jme3.anim.Joint;
import com.jme3.anim.SkinningControl;
import com.jme3.math.Matrix4f;
import com.jme3.math.Transform;
import com.jme3.scene.Spatial;
import java.nio.IntBuffer;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;
import jme3utilities.MyString;
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
     * true if creation of a SkinningControl has begun, otherwise false
     */
    private boolean doneAddingJoints = false;
    /**
     * maps joint IDs (indices into the Armature) to joints
     */
    final private Map<Integer, Joint> idToJoint = new TreeMap<>();
    /**
     * maps joint IDs (indices into the Armature) to bind matrices
     */
    final private Map<Integer, Matrix4f> idToBind = new TreeMap<>();
    /**
     * maps joint IDs (indices into the Armature) to offset matrices
     */
    final private Map<Integer, Matrix4f> idToOffset = new TreeMap<>();
    /**
     * maps bone names to joint IDs (indices into the Armature)
     */
    final private Map<String, Integer> nameToId = new TreeMap<>();
    // *************************************************************************
    // new methods exposed

    /**
     * If one or more bones have been seen, create a SkinningControl and add it
     * to the specified Spatial.
     * <p>
     * Once this method is invoked, no more IDs can be assigned and no more
     * joints can be created.
     *
     * @param addControl where to add the control (not null, modified)
     */
    void buildAndAddTo(Spatial addControl) {
        assert addControl != null;
        this.doneAddingJoints = true;

        int numJoints = nameToId.size();
        assert idToJoint.size() == numJoints : numJoints;
        assert idToOffset.size() == numJoints : numJoints;

        if (numJoints > 0) {
            // Populate an array of joints:
            Joint[] jointArray = new Joint[numJoints];
            for (int id = 0; id < numJoints; ++id) {
                Joint joint = idToJoint.get(id);
                assert joint != null;
                assert joint.getId() == id : id;
                jointArray[id] = joint;
            }

            // Set the inverse bind matrix and local transform of each Joint:
            for (int id = 0; id < numJoints; ++id) {
                configureJoint(id);
            }
            /*
             * Create an Armature and set the initial transform of each Joint
             * to its local transform:
             */
            Armature armature = new Armature(jointArray);
            armature.update();
            armature.saveInitialPose();

            SkinningControl skinner = new SkinningControl(armature);
            addControl.addControl(skinner);
        }
    }

    /**
     * Create joints for the specified bone node and all its descendants. Note:
     * recursive!
     *
     * @param aiNode the bone node to process (not null, unaffected)
     * @return a new instance (not null)
     */
    Joint createJoints(AINode aiNode) {
        assert !doneAddingJoints;
        String boneName = aiNode.mName().dataString();

        // A bone node shouldn't contain any meshes:
        IntBuffer pMeshIndices = aiNode.mMeshes();
        if (pMeshIndices != null) {
            int numMeshes = pMeshIndices.capacity();
            if (numMeshes > 0) {
                String qName = MyString.quote(boneName);
                String message = String.format(
                        "%s looks like a bone node, yet it contains %d mesh%s.",
                        qName, numMeshes, (numMeshes == 1) ? "" : "s");
            }
        }

        // Get the joint ID, assigning one if the name hasn't been seen before:
        int jointId = jointId(boneName);

        // Convert the offset matrix and save it for configureJoint():
        AIMatrix4x4 transformation = aiNode.mTransformation();
        Matrix4f offsetMatrix = ConversionUtils.convertMatrix(transformation);
        idToOffset.put(jointId, offsetMatrix);
        /*
         * Create a Joint, setting its name, ID, parent, and children,
         * but without configuring its bind matrix or initial local transform:
         */
        Joint result = new Joint(boneName);
        result.setId(jointId);
        PointerBuffer pChildren = aiNode.mChildren();
        if (pChildren != null) {
            int numChildren = aiNode.mNumChildren();
            for (int childIndex = 0; childIndex < numChildren; ++childIndex) {
                long handle = pChildren.get(childIndex);
                AINode aiChild = AINode.createSafe(handle);
                Joint childJoint = createJoints(aiChild);
                result.addChild(childJoint);
            }
        }
        idToJoint.put(jointId, result);

        return result;
    }

    /**
     * Test whether the argument is the name of a bone the builder has already
     * seen.
     *
     * @param name the name to test (not null)
     * @return true if a bone name, otherwise false
     */
    boolean isKnownBone(String name) {
        assert name != null;
        boolean result = nameToId.containsKey(name);
        return result;
    }

    /**
     * Return the joint ID of the named bone. If the builder hasn't seen the
     * name before, a new ID is assigned.
     *
     * @param boneName the name of the bone (not null, unaffected)
     * @return a joint ID (an index into the Armature, &ge;0)
     */
    int jointId(String boneName) {
        assert !doneAddingJoints;
        assert boneName != null;

        int result;
        if (isKnownBone(boneName)) {
            result = nameToId.get(boneName);

        } else { // Assign the next ID:
            result = nameToId.size();
            nameToId.put(boneName, result);
        }

        return result;
    }
    // *************************************************************************
    // private methods

    /**
     * Configure the specified Joint and all of its ancestors.
     * <p>
     * Configuration consists of:<ul>
     * <li>Initializing the joint's local transform.</li>
     * <li>Saving the joint's bind matrix into {@code idToJoint}.</li>
     * <li>Setting the joint's inverse bind matrix.</li>
     * </ul>
     * <p>
     * Note: the joint IDs must all be initialized!
     * <p>
     * Note: recursive!
     *
     * @param id the ID of the joint to configure (&ge;0, &lt;numJoints)
     */
    private void configureJoint(int id) {
        assert doneAddingJoints;
        if (idToBind.containsKey(id)) { // The joint is already configured.
            return;
        }
        Joint joint = idToJoint.get(id);

        // Initialize the local transform using the offset matrix:
        Matrix4f offset = idToOffset.get(id);
        Transform initialTransform = new Transform();
        initialTransform.fromTransformMatrix(offset);
        joint.setLocalTransform(initialTransform);

        // Calculate the joint's bind matrix and save it into the map:
        Matrix4f bindMatrix;
        Joint parent = joint.getParent();
        if (parent == null) { // root joint:
            bindMatrix = offset.clone();

        } else { // non-root joint:
            int parentId = parent.getId(); // joint IDs must all be initialized
            configureJoint(parentId);

            Matrix4f parentOffset = idToBind.get(parentId);
            bindMatrix = parentOffset.mult(offset);
        }
        idToBind.put(id, bindMatrix);

        // Set the joint's inverse bind matrix:
        Matrix4f imbm = bindMatrix.invert();
        joint.setInverseModelBindMatrix(imbm);
    }
}