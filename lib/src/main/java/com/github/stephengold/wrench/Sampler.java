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

import com.jme3.texture.Texture;
import java.io.IOException;
import java.util.logging.Logger;
import org.lwjgl.assimp.Assimp;

/**
 * Parameters that JMonkeyEngine uses for texture sampling.
 *
 * @author Stephen Gold sgold@sonic.net
 */
class Sampler {
    // *************************************************************************
    // constants and loggers

    /**
     * message logger for this class
     */
    final private static Logger logger
            = Logger.getLogger(Sampler.class.getName());
    // *************************************************************************
    // fields

    /**
     * magnification filter
     */
    private Texture.MagFilter magnificationFilter
            = Texture.MagFilter.Bilinear;
    /**
     * minification filter
     */
    private Texture.MinFilter minificationFilter
            = Texture.MinFilter.BilinearNoMipMaps;
    /**
     * wrap mode for the first axis of a 2-D texture
     */
    private Texture.WrapMode wrapS = Texture.WrapMode.Repeat;
    /**
     * wrap mode for the 2nd axis of a 2-D texture
     */
    private Texture.WrapMode wrapT = Texture.WrapMode.Repeat;
    // *************************************************************************
    // new methods exposed

    void applyTo(Texture result) {
        result.setMagFilter(magnificationFilter);
        result.setMinFilter(minificationFilter);
        result.setWrap(Texture.WrapAxis.S, wrapS);
        result.setWrap(Texture.WrapAxis.T, wrapT);
    }

    /**
     * Set the magnification filter.
     *
     * @param code encoded value from an AIMaterialProperty
     * @throws IOException if the argument doesn't encode a known magnification
     * filter
     */
    void setMagFilter(int code) throws IOException {
        Texture.MagFilter filter;
        switch (code) {
            case 9728:
                filter = Texture.MagFilter.Nearest;
                break;
            case 9729:
                filter = Texture.MagFilter.Bilinear;
                break;
            default:
                throw new IOException("code = " + code);
        }
        this.magnificationFilter = filter;
    }

    /**
     * Set the minification filter.
     *
     * @param code encoded value from an AIMaterialProperty
     * @throws IOException if the argument doesn't encode a known minification
     * filter
     */
    void setMinFilter(int code) throws IOException {
        Texture.MinFilter filter;
        switch (code) {
            case 9728:
                filter = Texture.MinFilter.NearestNoMipMaps;
                break;
            case 9729:
                filter = Texture.MinFilter.BilinearNoMipMaps;
                break;
            case 9984:
                filter = Texture.MinFilter.NearestNearestMipMap;
                break;
            case 9985:
                filter = Texture.MinFilter.BilinearNearestMipMap;
                break;
            case 9986:
                filter = Texture.MinFilter.NearestLinearMipMap;
                break;
            case 9987:
                filter = Texture.MinFilter.Trilinear;
                break;
            default:
                throw new IOException("code = " + code);
        }
        this.minificationFilter = filter;
    }

    /**
     * Set the wrap mode for the first axis of a 2-D texture.
     *
     * @param code encoded value from an AIMaterialProperty
     * @throws IOException if the argument doesn't encode a known wrap mode
     */
    void setWrapS(int code) throws IOException {
        this.wrapS = toWrapMode(code);
    }

    /**
     * Set the wrap mode for the 2nd axis of a 2-D texture.
     *
     * @param code encoded value from an AIMaterialProperty
     * @throws IOException if the argument doesn't encode a known wrap mode
     */
    void setWrapT(int code) throws IOException {
        this.wrapT = toWrapMode(code);
    }
    // *************************************************************************
    // private methods

    /**
     * Convert an encoded value to a JMonkeyEngine texture-axis wrap mode.
     *
     * @param code encoded value from an AIMaterialProperty
     * @return an enum value (not null)
     * @throws IOException if the argument doesn't encode a known wrap mode
     */
    private static Texture.WrapMode toWrapMode(int code) throws IOException {
        switch (code) {
            case Assimp.aiTextureMapMode_Wrap:
            // fallthru
            case 10497: // TODO Assimp should map glTF codes to Assimp codes
                return Texture.WrapMode.Repeat;

            case Assimp.aiTextureMapMode_Clamp:
            // fallthru
            case 33071:
                return Texture.WrapMode.EdgeClamp;

            case Assimp.aiTextureMapMode_Mirror:
            // fallthru
            case 33648:
                return Texture.WrapMode.MirroredRepeat;

            default:
                throw new IOException("code = " + code);
        }
    }
}
