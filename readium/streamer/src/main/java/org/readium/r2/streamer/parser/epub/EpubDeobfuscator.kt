/*
 * Copyright 2020 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.r2.streamer.parser.epub

import com.mcxiaoke.koi.HASH
import com.mcxiaoke.koi.ext.toHexBytes
import kotlin.experimental.xor
import org.readium.r2.shared.publication.encryption.Encryption
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.data.ReadError
import org.readium.r2.shared.util.data.ReadTry
import org.readium.r2.shared.util.resource.Resource
import org.readium.r2.shared.util.resource.TransformingResource
import org.readium.r2.shared.util.resource.flatMap

/**
 * Deobfuscates fonts according to https://www.w3.org/TR/epub-33/#sec-font-obfuscation
 */
internal class EpubDeobfuscator(
    private val pubId: String,
    private val encryptionData: Map<Url, Encryption>
) {

    @Suppress("Unused_parameter")
    fun transform(url: Url, resource: Resource): Resource =
        resource.flatMap {
            val algorithm = encryptionData[url]?.algorithm
            if (algorithm != null && algorithm2length.containsKey(algorithm)) {
                DeobfuscatingResource(resource, algorithm)
            } else {
                resource
            }
        }

    inner class DeobfuscatingResource(
        private val resource: Resource,
        private val algorithm: String
    ) : TransformingResource(resource) {

        // The obfuscation doesn't change the length of the resource.
        override suspend fun length(): ReadTry<Long> =
            resource.length()

        override suspend fun transform(data: ReadTry<ByteArray>): ReadTry<ByteArray> =
            data.map { bytes ->
                val obfuscationLength: Int = algorithm2length[algorithm]
                    ?: return@map bytes

                val obfuscationKey: ByteArray = when (algorithm) {
                    "http://ns.adobe.com/pdf/enc#RC" -> getHashKeyAdobe(pubId)
                    else -> toHexBytes(HASH.sha1(pubId))
                } ?: ByteArray(0)

                if (obfuscationKey.isEmpty()) {
                    return Try.failure(ReadError.Decoding("The obfuscation key is not valid."))
                }

                deobfuscate(
                    bytes = bytes,
                    obfuscationKey = obfuscationKey,
                    obfuscationLength = obfuscationLength
                )
                bytes
            }
    }

    private val algorithm2length: Map<String, Int> = mapOf(
        "http://www.idpf.org/2008/embedding" to 1040,
        "http://ns.adobe.com/pdf/enc#RC" to 1024
    )

    private fun deobfuscate(bytes: ByteArray, obfuscationKey: ByteArray, obfuscationLength: Int) {
        val toDeobfuscate = 0 until obfuscationLength.coerceAtMost(bytes.size)
        for (i in toDeobfuscate) {
            bytes[i] = bytes[i].xor(obfuscationKey[i % obfuscationKey.size])
        }
    }

    private fun getHashKeyAdobe(pubId: String) =
        toHexBytes(
            pubId.replace("urn:uuid:", "")
                .replace("-", "")
        )

    private fun toHexBytes(string: String): ByteArray? {
        // Only even-length strings can be converted to an Hex byte array, otherwise it crashes
        // with StringIndexOutOfBoundsException.
        if (string.isEmpty() || string.length % 2 != 0) {
            return null
        }
        return string.toHexBytes()
    }
}
