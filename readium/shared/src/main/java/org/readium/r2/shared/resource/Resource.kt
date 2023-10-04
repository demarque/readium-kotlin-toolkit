/*
 * Copyright 2023 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.r2.shared.resource

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.StringRes
import java.io.ByteArrayInputStream
import java.nio.charset.Charset
import org.json.JSONObject
import org.readium.r2.shared.R
import org.readium.r2.shared.UserException
import org.readium.r2.shared.parser.xml.ElementNode
import org.readium.r2.shared.parser.xml.XmlParser
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.SuspendingCloseable
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.flatMap
import org.readium.r2.shared.util.mediatype.MediaType

public typealias ResourceTry<SuccessT> = Try<SuccessT, Resource.Exception>

/**
 * Acts as a proxy to an actual resource by handling read access.
 */
public interface Resource : SuspendingCloseable {

    /**
     * URL locating this resource, if any.
     */
    public val source: AbsoluteUrl?

    /**
     * Returns the resource media type if known.
     */
    public suspend fun mediaType(): ResourceTry<MediaType>

    /**
     * Properties associated to the resource.
     *
     * This is opened for extensions.
     */
    public suspend fun properties(): ResourceTry<Properties>

    public class Properties(
        properties: Map<String, Any> = emptyMap()
    ) : Map<String, Any> by properties {

        public companion object {
            public inline operator fun invoke(build: Builder.() -> Unit): Properties =
                Properties(Builder().apply(build))
        }

        public inline fun copy(build: Builder.() -> Unit): Properties =
            Properties(Builder(this).apply(build))

        public class Builder(properties: Map<String, Any> = emptyMap()) :
            MutableMap<String, Any> by properties.toMutableMap()
    }

    /**
     * Returns data length from metadata if available, or calculated from reading the bytes otherwise.
     *
     * This value must be treated as a hint, as it might not reflect the actual bytes length. To get
     * the real length, you need to read the whole resource.
     */
    public suspend fun length(): ResourceTry<Long>

    /**
     * Reads the bytes at the given range.
     *
     * When [range] is null, the whole content is returned. Out-of-range indexes are clamped to the
     * available length automatically.
     */
    public suspend fun read(range: LongRange? = null): ResourceTry<ByteArray>

    /**
     * Errors occurring while accessing a resource.
     *
     * @param url URL locating the resource, if any.
     */
    public sealed class Exception(
        public val url: Url?,
        @StringRes userMessageId: Int,
        cause: Throwable? = null
    ) : UserException(
        userMessageId,
        cause = cause
    ) {

        /** Equivalent to a 400 HTTP error. */
        public class BadRequest(url: Url?, cause: Throwable? = null) :
            Exception(url, R.string.readium_shared_resource_exception_bad_request, cause)

        /** Equivalent to a 404 HTTP error. */
        public class NotFound(url: Url?, cause: Throwable? = null) :
            Exception(url, R.string.readium_shared_resource_exception_not_found, cause)

        /**
         * Equivalent to a 403 HTTP error.
         *
         * This can be returned when trying to read a resource protected with a DRM that is not
         * unlocked.
         */
        public class Forbidden(url: Url?, cause: Throwable? = null) :
            Exception(url, R.string.readium_shared_resource_exception_forbidden, cause) {
            public constructor(resource: Resource, cause: Throwable? = null) :
                this(resource.url, cause)
        }

        /**
         * Equivalent to a 503 HTTP error.
         *
         * Used when the source can't be reached, e.g. no Internet connection, or an issue with the
         * file system. Usually this is a temporary error.
         */
        public class Unavailable(url: Url?, cause: Throwable? = null) :
            Exception(url, R.string.readium_shared_resource_exception_unavailable, cause)

        /**
         * The Internet connection appears to be offline.
         */
        public data object Offline :
            Exception(null, R.string.readium_shared_resource_exception_offline)

        /**
         * Equivalent to a 507 HTTP error.
         *
         * Used when the requested range is too large to be read in memory.
         */
        public class OutOfMemory(url: Url?, override val cause: OutOfMemoryError) :
            Exception(url, R.string.readium_shared_resource_exception_out_of_memory)

        /** For any other error, such as HTTP 500. */
        public class Other(url: Url?, cause: Throwable) :
            Exception(url, R.string.readium_shared_resource_exception_other, cause)

        public companion object {

            public fun wrap(resource: Resource?, e: Throwable): Exception =
                wrap(resource?.url, e)

            public fun wrap(url: Url?, e: Throwable): Exception =
                when (e) {
                    is Exception -> e
                    is OutOfMemoryError -> OutOfMemory(url, e)
                    else -> Other(url, e)
                }
        }
    }
}

private val Resource.url: Url?
    get() = source ?: (this as? Container.Entry)?.url

/** Creates a Resource that will always return the given [error]. */
public class FailureResource(
    private val error: Resource.Exception
) : Resource {

    internal constructor(url: Url?, cause: Throwable) : this(Resource.Exception.wrap(url, cause))

    override val source: AbsoluteUrl? = null
    override suspend fun mediaType(): ResourceTry<MediaType> = Try.failure(error)
    override suspend fun properties(): ResourceTry<Resource.Properties> = Try.failure(error)
    override suspend fun length(): ResourceTry<Long> = Try.failure(error)
    override suspend fun read(range: LongRange?): ResourceTry<ByteArray> = Try.failure(error)
    override suspend fun close() {}

    override fun toString(): String =
        "${javaClass.simpleName}($error)"
}

/**
 * Maps the result with the given [transform]
 *
 * If the [transform] throws an [Exception], it is wrapped in a failure with Resource.Exception.Other.
 */
public inline fun <R, S> ResourceTry<S>.mapCatching(resource: Resource, transform: (value: S) -> R): ResourceTry<R> =
    try {
        map(transform)
    } catch (e: Exception) {
        Try.failure(Resource.Exception.wrap(resource, e))
    } catch (e: OutOfMemoryError) { // We don't want to catch any Error, only OOM.
        Try.failure(Resource.Exception.wrap(resource, e))
    }

public inline fun <R, S> ResourceTry<S>.flatMapCatching(
    resource: Resource,
    transform: (value: S) -> ResourceTry<R>
): ResourceTry<R> =
    mapCatching(resource, transform).flatMap { it }

/**
 * Reads the full content as a [String].
 *
 * If [charset] is null, then it is parsed from the `charset` parameter of link().type,
 * or falls back on UTF-8.
 */
public suspend fun Resource.readAsString(charset: Charset? = null): ResourceTry<String> =
    read().mapCatching(this) {
        String(it, charset = charset ?: Charsets.UTF_8)
    }

/**
 * Reads the full content as a JSON object.
 */
public suspend fun Resource.readAsJson(): ResourceTry<JSONObject> =
    readAsString(charset = Charsets.UTF_8).mapCatching(this) { JSONObject(it) }

/**
 * Reads the full content as an XML document.
 */
public suspend fun Resource.readAsXml(): ResourceTry<ElementNode> =
    read().mapCatching(this) { XmlParser().parse(ByteArrayInputStream(it)) }

/**
 * Reads the full content as a [Bitmap].
 */
public suspend fun Resource.readAsBitmap(): ResourceTry<Bitmap> =
    read().mapCatching(this) {
        BitmapFactory.decodeByteArray(it, 0, it.size)
            ?: throw kotlin.Exception("Could not decode resource as a bitmap")
    }
