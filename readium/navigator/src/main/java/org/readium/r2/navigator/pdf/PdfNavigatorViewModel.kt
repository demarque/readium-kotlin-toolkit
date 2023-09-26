/*
 * Copyright 2021 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.r2.navigator.pdf

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.readium.r2.navigator.preferences.Configurable
import org.readium.r2.navigator.util.createViewModelFactory
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.positions

@OptIn(ExperimentalReadiumApi::class)
internal class PdfNavigatorViewModel<S : Configurable.Settings, P : Configurable.Preferences<P>>(
    application: Application,
    private val publication: Publication,
    initialLocations: Locator.Locations?,
    initialPreferences: P,
    private val pdfEngineProvider: PdfEngineProvider<*, *, S, P, *>
) : AndroidViewModel(application) {

    private val _currentLocator: MutableStateFlow<Locator> =
        MutableStateFlow(
            requireNotNull(publication.locatorFromLink(publication.readingOrder.first()))
                .copy(locations = initialLocations ?: Locator.Locations())
        )

    val currentLocator: StateFlow<Locator> = _currentLocator.asStateFlow()

    private val _settings: MutableStateFlow<S> =
        MutableStateFlow(computeSettings(initialPreferences))

    val settings: StateFlow<S> = _settings.asStateFlow()

    fun submitPreferences(preferences: P) = viewModelScope.launch {
        _settings.value = computeSettings(preferences)
    }

    private fun computeSettings(preferences: P): S =
        pdfEngineProvider.computeSettings(publication.metadata, preferences)

    fun onPageChanged(pageIndex: Int) = viewModelScope.launch {
        publication.positions().getOrNull(pageIndex)?.let { locator ->
            _currentLocator.value = locator
        }
    }

    fun findLink(locator: Locator): Link? =
        if (isPDFFile) {
            publication.readingOrder.first()
        } else {
            publication.linkWithHref(locator.href)
        }

    /**
     * Historically, the reading order of a standalone PDF file contained a single link with the
     * HREF "/$assetName". This was fragile if the asset named changed, or was different on other
     * devices.
     *
     * To avoid this, we now use a single link with the HREF "publication.pdf". And to avoid
     * breaking legacy locators, we match any HREF if the reading order contains a single link with
     * the HREF "publication.pdf".
     */
    private val isPDFFile: Boolean =
        publication.readingOrder.count() == 1 &&
            publication.readingOrder[0].href.toString() == "publication.pdf"

    companion object {
        fun <S : Configurable.Settings, P : Configurable.Preferences<P>> createFactory(
            application: Application,
            publication: Publication,
            initialLocations: Locator.Locations?,
            initialPreferences: P,
            pdfEngineProvider: PdfEngineProvider<*, *, S, P, *>
        ) = createViewModelFactory {
            PdfNavigatorViewModel(
                application = application,
                publication = publication,
                initialLocations = initialLocations,
                initialPreferences = initialPreferences,
                pdfEngineProvider = pdfEngineProvider
            )
        }
    }
}
