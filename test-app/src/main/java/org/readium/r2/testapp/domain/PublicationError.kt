/*
 * Copyright 2023 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.r2.testapp.domain

import androidx.annotation.StringRes
import org.readium.r2.shared.UserException
import org.readium.r2.shared.asset.AssetRetriever
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Error
import org.readium.r2.testapp.R

sealed class PublicationError(@StringRes userMessageId: Int) : UserException(userMessageId) {

    class Unavailable(val error: Error) : PublicationError(R.string.publication_error_unavailable)

    class NotFound(val error: Error) : PublicationError(R.string.publication_error_not_found)

    class OutOfMemory(val error: Error) : PublicationError(R.string.publication_error_out_of_memory)

    class SchemeNotSupported(val error: Error) : PublicationError(
        R.string.publication_error_scheme_not_supported
    )

    class UnsupportedAsset(val error: Error? = null) : PublicationError(
        R.string.publication_error_unsupported_asset
    )

    class InvalidPublication(val error: Error) : PublicationError(
        R.string.publication_error_invalid_publication
    )

    class IncorrectCredentials(val error: Error) : PublicationError(
        R.string.publication_error_incorrect_credentials
    )

    class Forbidden(val error: Error? = null) : PublicationError(
        R.string.publication_error_forbidden
    )

    class Unexpected(val error: Error) : PublicationError(R.string.publication_error_unexpected)

    companion object {

        operator fun invoke(error: Publication.OpenError): PublicationError =
            when (error) {
                is Publication.OpenError.Forbidden ->
                    Forbidden(error)
                is Publication.OpenError.IncorrectCredentials ->
                    IncorrectCredentials(error)
                is Publication.OpenError.NotFound ->
                    NotFound(error)
                is Publication.OpenError.OutOfMemory ->
                    OutOfMemory(error)
                is Publication.OpenError.InvalidAsset ->
                    InvalidPublication(error)
                is Publication.OpenError.Unavailable ->
                    Unavailable(error)
                is Publication.OpenError.Unknown ->
                    Unexpected(error)
                is Publication.OpenError.UnsupportedAsset ->
                    UnsupportedAsset(error)
            }

        operator fun invoke(error: AssetRetriever.Error): PublicationError =
            when (error) {
                is AssetRetriever.Error.ArchiveFormatNotSupported ->
                    UnsupportedAsset(error)
                is AssetRetriever.Error.Forbidden ->
                    Forbidden(error)
                is AssetRetriever.Error.NotFound ->
                    NotFound(error)
                is AssetRetriever.Error.InvalidAsset ->
                    InvalidPublication(error)
                is AssetRetriever.Error.OutOfMemory ->
                    OutOfMemory(error)
                is AssetRetriever.Error.SchemeNotSupported ->
                    SchemeNotSupported(error)
                is AssetRetriever.Error.Unavailable ->
                    Unavailable(error)
                is AssetRetriever.Error.Unknown ->
                    Unexpected(error)
            }
    }
}