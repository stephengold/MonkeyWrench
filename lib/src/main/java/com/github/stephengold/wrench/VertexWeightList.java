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
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;
import org.lwjgl.assimp.AIVertexWeight;

/**
 * Manage the bone indices and weights of a single mesh vertex.
 *
 * @author Stephen Gold sgold@sonic.net
 */
class VertexWeightList {
    // *************************************************************************
    // constants and loggers

    /**
     * to compare vertex weights
     */
    final private static Comparator<AIVertexWeight> comparator
            = new ByDescAbsWeight();
    /**
     * maximum number of weights per vertex that JMonkeyEngine can handle
     */
    final static int maxElements = 4;
    /**
     * message logger for this class
     */
    final private static Logger logger
            = Logger.getLogger(VertexWeightList.class.getName());
    // *************************************************************************
    // fields

    /**
     * list of bone indices and weights
     */
    final private List<AIVertexWeight> list = new ArrayList<>(4);
    // *************************************************************************
    // new methods exposed

    /**
     * If the argument has non-zero weight, add it to the end of the list.
     *
     * @param aiVertexWeight the instance to append (not null, alias created)
     */
    void add(AIVertexWeight aiVertexWeight) {
        float weight = aiVertexWeight.mWeight();
        if (weight != 0f) {
            list.add(aiVertexWeight);
        }
    }

    /**
     * Write {@code maxElements} bone indices to the specified buffer at its
     * current position and advance its position.
     *
     * @param boneIndexData the buffer to write to (not null, must be a
     * ByteBuffer or a ShortBuffer, modified)
     */
    void putIndices(Buffer boneIndexData) {
        int size = list.size();
        for (int i = 0; i < maxElements; ++i) {
            int vertexId;
            if (i < size) {
                AIVertexWeight aiVertexWeight = list.get(i);
                vertexId = aiVertexWeight.mVertexId();
            } else {
                vertexId = -1;
            }

            if (boneIndexData instanceof ByteBuffer) {
                ((ByteBuffer) boneIndexData).put((byte) vertexId);
            } else {
                ((ShortBuffer) boneIndexData).put((short) vertexId);
            }
        }
    }

    /**
     * Write {@code maxElements} bone weights to the specified buffer at its
     * current position and advance its position.
     *
     * @param boneWeightData the buffer to write to (not null, modified)
     */
    void putWeights(FloatBuffer boneWeightData) {
        int size = list.size();
        float totalWeight = 0f;
        for (int i = 0; i < maxElements; ++i) {
            if (i < size) {
                AIVertexWeight aiVertexWeight = list.get(i);
                float weight = aiVertexWeight.mWeight();
                totalWeight += weight;
            }
        }

        for (int i = 0; i < maxElements; ++i) {
            float weight;
            if (i < size) {
                AIVertexWeight aiVertexWeight = list.get(i);
                weight = aiVertexWeight.mWeight();
                if (totalWeight != 0f) {
                    weight /= totalWeight;
                }
            } else {
                weight = 0f;
            }
            boneWeightData.put(weight);
        }
    }

    /**
     * If the list has more than {@code maxElements} elements, sort the elements
     * and discard those with the least weight.
     *
     * @return the (new) number of bone weights for the vertex (&ge;0,
     * &le;maxElements)
     */
    int truncate() {
        int size = list.size();
        if (size > maxElements) {
            Collections.sort(list, comparator);
            List<AIVertexWeight> extras = list.subList(maxElements, size);
            extras.clear();
            size = maxElements;
        }

        return size;
    }
}
