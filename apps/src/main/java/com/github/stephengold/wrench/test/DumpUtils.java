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

import com.jme3.anim.AnimClip;
import com.jme3.anim.AnimTrack;
import com.jme3.anim.Armature;
import com.jme3.anim.Joint;
import com.jme3.anim.MorphTrack;
import com.jme3.anim.TransformTrack;
import com.jme3.anim.util.HasLocalTransform;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.mesh.MorphTarget;
import java.nio.FloatBuffer;
import java.util.EnumMap;
import java.util.List;
import java.util.logging.Logger;
import jme3utilities.MyString;

/**
 * Utility methods to print details of JMonkeyEngine data structures. TODO move
 * to the Heart library
 *
 * @author Stephen Gold sgold@sonic.net
 */
final class DumpUtils {
    // *************************************************************************
    // constants and loggers

    /**
     * message logger for this class
     */
    final private static Logger logger
            = Logger.getLogger(DumpUtils.class.getName());
    // *************************************************************************
    // constructors

    /**
     * A private constructor to inhibit instantiation of this class.
     */
    private DumpUtils() {
        // do nothing
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Print details about the specified Armature.
     *
     * @param armature the armature to dump (not null, unaffected)
     */
    static void dumpArmature(Armature armature) {
        Joint[] rootJoints = armature.getRoots();
        int numRoots = rootJoints.length;
        int numJoints = armature.getJointCount();
        System.out.printf("%nArmature with %d root%s and %d joint%s:%n",
                numRoots, numRoots == 1 ? "" : "s",
                numJoints, numJoints == 1 ? "" : "s");

        for (Joint rootJoint : rootJoints) {
            dumpArmatureSubtree(rootJoint, "  ");
        }
    }

    /**
     * Print details about the specified AnimClip.
     *
     * @param clip the clip to dump (not null, unaffected)
     */
    static void dumpClip(AnimClip clip) {
        AnimTrack<?>[] tracks = clip.getTracks();
        System.out.println();
        System.out.println("AnimClip " + MyString.quote(clip.getName())
                + " with " + tracks.length + " tracks:");

        for (AnimTrack<?> track : tracks) {
            System.out.print("  ");
            System.out.print(track.getClass().getSimpleName());

            if (track instanceof MorphTrack) {
                MorphTrack morphTrack = (MorphTrack) track;
                Geometry target = morphTrack.getTarget();
                String desc = describeTrackTarget(target);
                System.out.println(desc);

                float[] times = morphTrack.getTimes();
                dumpFloats("    times", times);

                float[] weights = morphTrack.getWeights();
                dumpFloats("    weights", weights);

            } else if (track instanceof TransformTrack) {
                TransformTrack transformTrack = (TransformTrack) track;
                HasLocalTransform target = transformTrack.getTarget();
                String desc = describeTrackTarget(target);
                System.out.println(desc);

                float[] times = transformTrack.getTimes();
                dumpFloats("    times", times);

            } else {
                System.out.println();
            }
        }
    }

    /**
     * Print details about the morph targets in the specified mesh.
     *
     * @param mesh the mesh to analyze (not null, unaffected)
     */
    static void dumpMorphTargets(Mesh mesh) {
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
    // *************************************************************************
    // private methods

    /**
     * Describe the specified track target.
     *
     * @param target the target to describe (may be null, unaffected)
     * @return a string of descriptive text (not null, not empty)
     */
    private static String describeTrackTarget(HasLocalTransform target) {
        if (target == null) {
            return "null";
        }

        String targetName;
        if (target instanceof Spatial) {
            targetName = ((Spatial) target).getName();
        } else {
            targetName = ((Joint) target).getName();
        }
        String qTargetName = MyString.quote(targetName);

        String className = target.getClass().getSimpleName();
        String result = String.format(
                " targets a %s named %s:", className, qTargetName);

        return result;
    }

    /**
     * Print details about the specified armature subtree.
     *
     * @param subtree the root of the subtree to dump (not null, unaffected)
     * @param prefix printed at the start of each line of output
     */
    private static void dumpArmatureSubtree(Joint subtree, String prefix) {
        System.out.print(prefix);

        String name = subtree.getName();
        String qName = MyString.quote(name);
        System.out.print(qName);

        List<Joint> children = subtree.getChildren();
        if (!children.isEmpty()) {
            int numChildren = children.size();
            System.out.printf(" with %d child%s:",
                    numChildren, numChildren == 1 ? "" : "ren");
        }
        System.out.println();

        for (Joint child : children) {
            dumpArmatureSubtree(child, prefix + "  ");
        }
    }

    /**
     * Print the specified array of single-precision data.
     *
     * @param label a descriptive label for the data
     * @param floatArray the data to print (not null, unaffected)
     */
    private static void dumpFloats(String label, float[] floatArray) {
        System.out.printf("%s (%d) ", label, floatArray.length);
        for (float fValue : floatArray) {
            System.out.print(' ');
            System.out.print(fValue);
        }
        System.out.println();
    }
}
