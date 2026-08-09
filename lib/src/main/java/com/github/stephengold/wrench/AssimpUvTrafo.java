/*
 Copyright (c) 2026 Stephen Gold

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

import jme3utilities.Validate;
import org.lwjgl.assimp.Assimp;

/**
 * Represent texture-coordinate transformations that can be applied using
 * {@code AssimpProcessFlag.TRANSFORM_UV_COORDS}. Each enum value corresponds to
 * a specific Assimp.AI_UVTRAFO_* bitmask.
 *
 * @author Stephen Gold sgold@sonic.net
 */
public enum AssimpUvTrafo {
    // *************************************************************************
    // values

    /**
     * texture-coordinate rotation
     */
    ROTATION(Assimp.AI_UVTRAFO_ROTATION),
    /**
     * texture-coordinate scaling
     */
    SCALING(Assimp.AI_UVTRAFO_SCALING),
    /**
     * texture-coordinate translation
     */
    TRANSLATION(Assimp.AI_UVTRAFO_TRANSLATION);
    // *************************************************************************
    // fields

    /**
     * bitmask value (with exactly one bit set)
     */
    final private int value;
    // *************************************************************************
    // constructors

    /**
     * Private constructor that initializes the bitmask value of the current
     * enum value.
     *
     * @param value the desired bitmask value (with exactly one bit set)
     */
    AssimpUvTrafo(int value) {
        assert Integer.bitCount(value) == 1 : value;
        this.value = value;
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Combine an array of enum values into a single bitmask value.
     *
     * @param transformations array of enum values to combine (not {@code null},
     * unaffected)
     * @return the bitwise OR of the bitmask values
     */
    public static int combine(AssimpUvTrafo... transformations) {
        Validate.nonNullArray(transformations, "transformations");

        int result = 0x0;
        for (AssimpUvTrafo type : transformations) {
            result |= type.getValue();
        }

        return result;
    }

    /**
     * Return the bitmask value of the current enum value.
     *
     * @return bitmask value (with exactly one bit set)
     */
    public int getValue() {
        assert Integer.bitCount(value) == 1 : value;
        return value;
    }
}
