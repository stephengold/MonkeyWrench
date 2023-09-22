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

/**
 * The animation weight of a single animation joint. Immutable.
 *
 * @author Stephen Gold sgold@sonic.net
 */
class JointWeight {
    // *************************************************************************
    // fields

    /**
     * relative animation weight or influence of the Joint on the current
     * vertex, used to animate vertex positions, normals, and tangents (finite,
     * not zero)
     */
    final private float weight;
    /**
     * ID of the Joint in its Armature (&ge;0)
     */
    final private int jointId;
    // *************************************************************************
    // constructors

    /**
     * Instantiate a new joint weight.
     *
     * @param weight the desired animation weight (finite, not zero)
     * @param jointId the ID of the Joint in its Armature (&ge;0)
     */
    JointWeight(float weight, int jointId) {
        assert Float.isFinite(weight) : weight;
        assert weight != 0f;
        assert jointId >= 0 : jointId;

        this.weight = weight;
        this.jointId = jointId;
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Return the ID of the Joint in its Armature.
     *
     * @return the ID (&ge;0)
     */
    int jointId() {
        assert jointId >= 0 : jointId;
        return jointId;
    }

    /**
     * Return the relative weight or influence of the Joint on the current
     * vertex.
     *
     * @return the animation weight (finite, not zero)
     */
    float weight() {
        assert Float.isFinite(weight) : weight;
        assert weight != 0f;
        return weight;
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
        String result = String.format("%f(%d)", weight, jointId);
        return result;
    }
}
