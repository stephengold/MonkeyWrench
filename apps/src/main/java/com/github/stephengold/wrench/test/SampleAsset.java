/*
 Copyright (c) 2024 Stephen Gold

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

import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import jme3utilities.Validate;

/**
 * Metadata for a single sample asset, loaded from the JSON file at
 * https://github.com/KhronosGroup/glTF-Sample-Assets/blob/main/Models/model-index.json
 *
 * @author Stephen Gold sgold@sonic.net
 */
class SampleAsset {
    // *************************************************************************
    // constants and loggers

    /**
     * message logger for this class
     */
    final private static Logger logger
            = Logger.getLogger(SampleAsset.class.getName());
    // *************************************************************************
    // fields

    /**
     * descriptive label (unused)
     */
    private String label;
    /**
     * name of the asset directory
     */
    private String name;
    /**
     * filename of the screenshot image (unused)
     */
    private String screenshot;
    /**
     * descriptive tags that apply to the asset
     */
    private Set<String> tags;
    /**
     * map variants to loadable filenames
     */
    private Map<String, String> variants;
    // *************************************************************************
    // new methods exposed

    /**
     * Return the filename for the specified variant.
     *
     * @param variant the variant to look up (not null, not empty)
     * @return the name of the loadable file
     */
    String fileName(String variant) {
        Validate.nonEmpty(variant, "variant");
        String result = variants.get(variant);
        return result;
    }

    /**
     * Test whether all the asset possesses all the specified tags.
     *
     * @param requiredTags the tags to test for (not null)
     * @return true if all required tags are present, otherwise false
     */
    boolean hasTags(Set<String> requiredTags) {
        boolean result = tags.containsAll(requiredTags);
        return result;
    }

    /**
     * Return the asset's name.
     *
     * @return the directory name (not null, not empty)
     */
    String name() {
        assert name != null;
        assert !name.isEmpty();
        return name;
    }

    /**
     * Test whether the asset provides the specified variant.
     *
     * @param variant the variant to test for (not null, not empty)
     * @return true if provided, otherwise false
     */
    boolean providesVariant(String variant) {
        Validate.nonEmpty(variant, "variant");

        boolean result = variants.containsKey(variant);
        return result;
    }
}
