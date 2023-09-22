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

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.lwjgl.assimp.AIVertexWeight;

/**
 * Manage the joint indices and weights used to animate a single mesh vertex.
 *
 * @author Stephen Gold sgold@sonic.net
 */
class WeightList {
    // *************************************************************************
    // constants and loggers

    /**
     * maximum number of weights per vertex that JMonkeyEngine can handle
     */
    final static int maxSize = 4;
    /**
     * message logger for this class
     */
    final private static Logger logger
            = Logger.getLogger(WeightList.class.getName());
    // *************************************************************************
    // fields

    /**
     * list of weights
     */
    final private List<JointWeight> list = new ArrayList<>(maxSize + 1);
    // *************************************************************************
    // new methods exposed

    /**
     * If the specified {@code AIVertexWeight} has non-zero weight, add its
     * weight to the list. If the list contains more than {@code maxSize}
     * elements, drop the element with the least absolute weight.
     *
     * @param aiVertexWeight (not null, unaffected)
     * @param jointId the ID of the Joint in the Armature (&ge;0)
     */
    void add(AIVertexWeight aiVertexWeight, int jointId) {
        assert jointId >= 0 : jointId;

        float weight = aiVertexWeight.mWeight();
        if (weight != 0f) {
            JointWeight element = new JointWeight(weight, jointId);
            list.add(element);

            int size = list.size();
            if (size > maxSize) {
                dropLeastAbsWeight();
                assert list.size() == maxSize : list.size();
            }
        }
        assert list.size() <= maxSize : list.size();
    }

    /**
     * Return the number of elements in the list.
     *
     * @return the count (&ge;0, &le;maxSize)
     */
    int count() {
        int result = list.size();

        assert result >= 0 : result;
        assert result <= maxSize : result;
        return result;
    }

    /**
     * Write {@code maxElements} joint indices to the specified buffer at its
     * current position and advance the position.
     *
     * @param indexBuffer the buffer to write to (not null, modified, must be a
     * {@code ByteBuffer} {@code ShortBuffer} or {@code IntBuffer}, modified)
     */
    void putIndices(Buffer indexBuffer) {
        assert indexBuffer != null;

        int size = list.size();
        for (int elementIndex = 0; elementIndex < maxSize; ++elementIndex) {
            int jointId;
            if (elementIndex < size) {
                JointWeight element = list.get(elementIndex);
                jointId = element.jointId();
            } else {
                jointId = -1;
            }

            if (indexBuffer instanceof ByteBuffer) {
                ((ByteBuffer) indexBuffer).put((byte) jointId);
            } else if (indexBuffer instanceof ShortBuffer) {
                ((ShortBuffer) indexBuffer).put((short) jointId);
            } else {
                ((IntBuffer) indexBuffer).put(jointId);
            }
        }
    }

    /**
     * Write {@code maxElements} animation weights to the specified buffer at
     * its current position and advance the position.
     * <p>
     * If possible, the written weights will be normalized so that they sum to
     * one.
     *
     * @param weightBuffer the buffer to write to (not null, modified)
     */
    void putWeights(FloatBuffer weightBuffer) {
        assert weightBuffer != null;

        float totalWeight = totalWeight();
        int size = list.size();
        for (int elementIndex = 0; elementIndex < maxSize; ++elementIndex) {
            float weight;
            if (elementIndex < size) {
                JointWeight element = list.get(elementIndex);
                weight = element.weight();
                if (totalWeight != 0f) {
                    weight /= totalWeight;
                }
            } else {
                weight = 0f;
            }
            weightBuffer.put(weight);
        }
    }
    // *************************************************************************
    // Object methods

    /**
     * Represent this instance as a text string.
     *
     * @return a descriptive string of text (not null, not empty)
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(80);
        int size = list.size();

        for (int elementIndex = 0; elementIndex < size; ++elementIndex) {
            JointWeight bw = list.get(elementIndex);
            builder.append(bw);
            if (elementIndex != size - 1) {
                builder.append(" ");
            }
        }

        return builder.toString();
    }
    // *************************************************************************
    // private methods

    /**
     * Drop the element with the least absolute weight.
     */
    private void dropLeastAbsWeight() {
        //System.out.println("before drop: " + this);

        JointWeight dropElement = null;
        float min = Float.POSITIVE_INFINITY;
        for (JointWeight element : list) {
            float absWeight = Math.abs(element.weight());
            if (absWeight <= min) {
                min = absWeight;
                dropElement = element;
            }
        }
        assert dropElement != null;
        boolean success = list.remove(dropElement);
        assert success;

        //System.out.println("after drop: " + this);
        //System.out.println();
    }

    /**
     * Return the total weight across all elements.
     *
     * @return the sum
     */
    private float totalWeight() {
        float result = 0f;
        int size = list.size();
        for (int numElements = 0; numElements < size; ++numElements) {
            JointWeight element = list.get(numElements);
            float weight = element.weight();
            result += weight;
        }

        return result;
    }
}
