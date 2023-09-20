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

import java.util.Comparator;
import org.lwjgl.assimp.AIVertexWeight;

/**
 * Compare vertex weights using the absolute value of their weights, for sorting
 * into descending order.
 * <p>
 * Note: This comparator imposes orderings that are inconsistent with equals.
 *
 * @author Stephen Gold sgold@sonic.net
 */
final class ByDescAbsWeight implements Comparator<AIVertexWeight> {
    // *************************************************************************
    // Comparator methods

    /**
     * Compare 2 vertex weights for a descending sort by absolute value.
     *
     * @param w1 the first vertex weight (not null, unaffected)
     * @param w2 the 2nd vertex weight (not null, unaffected)
     * @return negative if {@code w1} comes before {@code w2}, positive if
     * {@code w1} comes after {@code w2}
     */
    @Override
    public int compare(AIVertexWeight w1, AIVertexWeight w2) {
        float weight1 = Math.abs(w1.mWeight());
        float weight2 = Math.abs(w2.mWeight());
        int result = Float.compare(weight2, weight1);

        return result;
    }
}
