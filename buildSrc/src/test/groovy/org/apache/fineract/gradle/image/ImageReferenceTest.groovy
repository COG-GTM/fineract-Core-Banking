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

import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertThrows
import static org.junit.jupiter.api.Assertions.assertTrue

class ImageReferenceTest {

    private static final String DIGEST = 'sha256:' + ('a' * 64)
    private static final String ECR = '123456789012.dkr.ecr.eu-west-1.amazonaws.com/fineract'

    @Test
    void publishedEcrReferenceResolvesToADigest() {
        def reference = ImageReference.parse("${ECR}@${DIGEST}")

        assertTrue(reference.digestPinned)
        assertFalse(reference.floating)
        assertEquals(ECR, reference.repository)
        assertEquals(DIGEST, reference.digest)
        assertEquals("${ECR}@${DIGEST}".toString(), reference.toDeployableReference())
    }

    @Test
    void taggedAndDigestedReferenceDropsTheTagWhenDeployed() {
        def reference = ImageReference.parse("${ECR}:develop@${DIGEST}")

        assertTrue(reference.digestPinned)
        assertEquals('develop', reference.tag)
        assertEquals("${ECR}@${DIGEST}".toString(), reference.toDeployableReference())
    }

    @Test
    void latestIsFloatingAndNotDeployable() {
        def reference = ImageReference.parse('apache/fineract:latest')

        assertTrue(reference.floating)
        assertTrue(reference.resolvesLatest())
        assertThrows(IllegalStateException, { reference.toDeployableReference() })
    }

    @Test
    void anUntaggedReferenceResolvesLatest() {
        assertTrue(ImageReference.parse('apache/fineract').resolvesLatest())
    }

    @Test
    void aShaTagIsStillFloating() {
        def reference = ImageReference.parse("${ECR}:8c187f9d1")

        assertTrue(reference.floating)
        assertFalse(reference.resolvesLatest())
        assertThrows(IllegalStateException, { reference.toDeployableReference() })
    }

    @Test
    void registryPortIsNotMistakenForATag() {
        def reference = ImageReference.parse('registry.local:5000/fineract')

        assertEquals('registry.local:5000/fineract', reference.repository)
        assertEquals(null, reference.tag)
    }

    @Test
    void malformedDigestIsRejected() {
        assertThrows(IllegalArgumentException, { ImageReference.parse("${ECR}@sha256:nope") })
        assertThrows(IllegalArgumentException, { ImageReference.ofDigest(ECR, 'latest') })
        assertThrows(IllegalArgumentException, { ImageReference.parse('  ') })
    }
}
