/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.gradle.image

import groovy.transform.Immutable

/**
 * A parsed OCI image reference, used by the build to guarantee that anything a deployment resolves is pinned to an
 * immutable digest rather than to a floating tag.
 */
@Immutable
class ImageReference {

    private static final java.util.regex.Pattern DIGEST = ~/^sha256:[0-9a-f]{64}$/

    String repository
    String tag
    String digest

    static ImageReference parse(String reference) {
        if (reference == null || reference.trim().isEmpty()) {
            throw new IllegalArgumentException("Image reference must not be empty")
        }

        def value = reference.trim()
        def digestSeparator = value.indexOf('@')
        if (digestSeparator >= 0) {
            def repository = value.substring(0, digestSeparator)
            def digest = value.substring(digestSeparator + 1)
            def tag = null
            def taggedSeparator = repository.lastIndexOf(':')
            if (taggedSeparator > repository.lastIndexOf('/')) {
                tag = repository.substring(taggedSeparator + 1)
                repository = repository.substring(0, taggedSeparator)
            }
            if (!DIGEST.matcher(digest).matches()) {
                throw new IllegalArgumentException("Not a valid sha256 digest: '${digest}'")
            }
            return new ImageReference(repository, tag, digest)
        }

        def tagSeparator = value.lastIndexOf(':')
        if (tagSeparator > value.lastIndexOf('/')) {
            return new ImageReference(value.substring(0, tagSeparator), value.substring(tagSeparator + 1), null)
        }
        return new ImageReference(value, null, null)
    }

    static ImageReference ofDigest(String repository, String digest) {
        parse("${repository}@${digest}")
    }

    boolean isDigestPinned() {
        digest != null
    }

    /**
     * A tag is floating when it can be moved to a different image: 'latest' and an absent tag (which docker resolves
     * to 'latest') are always floating, and so is every tag when no digest pins the reference.
     */
    boolean isFloating() {
        !digestPinned
    }

    boolean resolvesLatest() {
        !digestPinned && (tag == null || tag == 'latest')
    }

    /**
     * The reference a deployment should use: repository plus digest, dropping the human-readable tag.
     */
    String toDeployableReference() {
        if (!digestPinned) {
            throw new IllegalStateException("Image reference '${this}' is not pinned to a digest and must not be deployed")
        }
        "${repository}@${digest}"
    }

    @Override
    String toString() {
        def result = new StringBuilder(repository)
        if (tag != null) {
            result.append(':').append(tag)
        }
        if (digest != null) {
            result.append('@').append(digest)
        }
        result.toString()
    }
}
