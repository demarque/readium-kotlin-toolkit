/*
 * Copyright 2022 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.adapters.pspdfkit.navigator

import android.graphics.PointF
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commitNow
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.pspdfkit.annotations.Annotation
import com.pspdfkit.annotations.LinkAnnotation
import com.pspdfkit.annotations.SoundAnnotation
import com.pspdfkit.configuration.PdfConfiguration
import com.pspdfkit.configuration.annotations.AnnotationReplyFeatures
import com.pspdfkit.configuration.page.PageFitMode
import com.pspdfkit.configuration.page.PageLayoutMode
import com.pspdfkit.configuration.page.PageScrollDirection
import com.pspdfkit.configuration.page.PageScrollMode
import com.pspdfkit.configuration.theming.ThemeMode
import com.pspdfkit.document.PageBinding
import com.pspdfkit.document.PdfDocument
import com.pspdfkit.listeners.DocumentListener
import com.pspdfkit.listeners.OnPreparePopupToolbarListener
import com.pspdfkit.ui.PdfFragment
import com.pspdfkit.ui.toolbar.popup.PdfTextSelectionPopupToolbar
import kotlin.math.roundToInt
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.readium.adapters.pspdfkit.document.PsPdfKitDocument
import org.readium.adapters.pspdfkit.document.PsPdfKitDocumentFactory
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.pdf.PdfDocumentFragment
import org.readium.r2.navigator.preferences.Axis
import org.readium.r2.navigator.preferences.Fit
import org.readium.r2.navigator.preferences.ReadingProgression
import org.readium.r2.navigator.preferences.Spread
import org.readium.r2.navigator.util.createViewModelFactory
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.isProtected
import org.readium.r2.shared.resource.Resource
import org.readium.r2.shared.resource.ResourceTry
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.pdf.cachedIn

@ExperimentalReadiumApi
public class PsPdfKitDocumentFragment(
    private val publication: Publication,
    private val href: Url,
    initialPageIndex: Int,
    initialSettings: PsPdfKitSettings,
    private val listener: Listener?,
    private val inputListener: InputListener
) : PdfDocumentFragment<PsPdfKitDocumentFragment.Listener, PsPdfKitSettings>() {

    public interface Listener : PdfDocumentFragment.Listener {
        /**
         * Called when a PDF resource failed to be loaded, for example because of an
         * [OutOfMemoryError].
         */
        public fun onResourceLoadFailed(href: Url, error: Resource.Exception) {}

        /** Called when configuring a new PDF fragment. */
        public fun onConfigurePdfView(builder: PdfConfiguration.Builder): PdfConfiguration.Builder = builder
    }

    private companion object {
        private const val pdfFragmentTag = "com.pspdfkit.ui.PdfFragment"
    }

    private var pdfFragment: PdfFragment? = null
    private val psPdfKitListener = PsPdfKitListener()

    private class DocumentViewModel(
        document: suspend () -> ResourceTry<PsPdfKitDocument>
    ) : ViewModel() {

        val document: Deferred<ResourceTry<PsPdfKitDocument>> =
            viewModelScope.async { document() }
    }

    private val viewModel: DocumentViewModel by viewModels {
        createViewModelFactory {
            DocumentViewModel(
                document = {
                    PsPdfKitDocumentFactory(requireContext())
                        .cachedIn(publication)
                        .open(publication.get(href), null)
                }
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // We don't support fragment restoration for the PdfFragment, as we want to recreate a fresh
        // instance in [reset]. To prevent restoring (and crashing) the PdfFragment without a
        // document source, we remove it from the fragment manager.
        (childFragmentManager.findFragmentByTag(pdfFragmentTag) as? PdfFragment)
            ?.let { fragment ->
                childFragmentManager.commitNow {
                    remove(fragment)
                }
            }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View =
        FragmentContainerView(inflater.context)
            .apply {
                id = R.id.readium_pspdfkit_fragment
            }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.document.await()
                .onFailure { error ->
                    listener?.onResourceLoadFailed(href, error)
                }
                .onSuccess { reset() }
        }
    }

    private fun reset() {
        val doc = viewModel.document.tryGetCompleted()?.getOrNull() ?: return
        if (view == null) return

        doc.document.pageBinding = settings.readingProgression.pageBinding

        val fragment = PdfFragment.newInstance(doc.document, configForSettings(settings))
            .apply {
                setOnPreparePopupToolbarListener(psPdfKitListener)
                addDocumentListener(psPdfKitListener)
            }
            .also { pdfFragment = it }

        childFragmentManager.commitNow {
            replace(R.id.readium_pspdfkit_fragment, fragment, pdfFragmentTag)
        }
    }

    private fun configForSettings(settings: PsPdfKitSettings): PdfConfiguration {
        var config = PdfConfiguration.Builder()
            .animateScrollOnEdgeTaps(false)
            .annotationReplyFeatures(AnnotationReplyFeatures.READ_ONLY)
            .automaticallyGenerateLinks(true)
            .autosaveEnabled(false)
            .disableAnnotationEditing()
            .disableAnnotationRotation()
            .disableAutoSelectNextFormElement()
            .disableFormEditing()
            .enableMagnifier(true)
            .excludedAnnotationTypes(emptyList())
            .scrollOnEdgeTapEnabled(false)
            .scrollOnEdgeTapMargin(50)
            .scrollbarsEnabled(true)
            .setAnnotationInspectorEnabled(false)
            .setJavaScriptEnabled(false)
            .textSelectionEnabled(true)
            .textSelectionPopupToolbarEnabled(true)
            .themeMode(ThemeMode.DEFAULT)
            .videoPlaybackEnabled(true)
            .zoomOutBounce(true)

        // Customization point for integrators.
        listener?.let {
            config = it.onConfigurePdfView(config)
        }

        // Settings-specific configuration
        config = config
            .fitMode(settings.fit.fitMode)
            .layoutMode(settings.spread.pageLayout)
            .firstPageAlwaysSingle(settings.offsetFirstPage)
            .pagePadding(settings.pageSpacing.roundToInt())
            .restoreLastViewedPage(false)
            .scrollDirection(
                if (!settings.scroll) {
                    PageScrollDirection.HORIZONTAL
                } else {
                    settings.scrollAxis.scrollDirection
                }
            )
            .scrollMode(settings.scroll.scrollMode)

        if (publication.isProtected) {
            config = config.disableCopyPaste()
        }

        return config.build()
    }

    private val _pageIndex = MutableStateFlow(initialPageIndex)
    override val pageIndex: StateFlow<Int> = _pageIndex.asStateFlow()

    override fun goToPageIndex(index: Int, animated: Boolean): Boolean {
        val fragment = pdfFragment ?: return false
        if (!isValidPageIndex(index)) {
            return false
        }
        fragment.setPageIndex(index, animated)
        return true
    }

    private fun isValidPageIndex(pageIndex: Int): Boolean {
        val validRange = 0 until (pdfFragment?.pageCount ?: 0)
        return validRange.contains(pageIndex)
    }

    private var settings: PsPdfKitSettings = initialSettings

    override fun applySettings(settings: PsPdfKitSettings) {
        if (this.settings == settings) {
            return
        }

        this.settings = settings
        reset()
    }

    private inner class PsPdfKitListener : DocumentListener, OnPreparePopupToolbarListener {
        override fun onPageChanged(document: PdfDocument, pageIndex: Int) {
            _pageIndex.value = pageIndex
        }

        override fun onDocumentClick(): Boolean {
            val center = view?.run { PointF(width.toFloat() / 2, height.toFloat() / 2) }
            return center?.let { inputListener.onTap(TapEvent(it)) } ?: false
        }

        override fun onPageClick(
            document: PdfDocument,
            pageIndex: Int,
            event: MotionEvent?,
            pagePosition: PointF?,
            clickedAnnotation: Annotation?
        ): Boolean {
            if (
                pagePosition == null || clickedAnnotation is LinkAnnotation ||
                clickedAnnotation is SoundAnnotation
            ) {
                return false
            }

            checkNotNull(pdfFragment).viewProjection.toViewPoint(pagePosition, pageIndex)
            return inputListener.onTap(TapEvent(pagePosition))
        }

        private val allowedTextSelectionItems = listOf(
            com.pspdfkit.R.id.pspdf__text_selection_toolbar_item_share,
            com.pspdfkit.R.id.pspdf__text_selection_toolbar_item_copy,
            com.pspdfkit.R.id.pspdf__text_selection_toolbar_item_speak
        )

        override fun onPrepareTextSelectionPopupToolbar(toolbar: PdfTextSelectionPopupToolbar) {
            // Makes sure only the menu items in `allowedTextSelectionItems` will be visible.
            toolbar.menuItems = toolbar.menuItems
                .filter { allowedTextSelectionItems.contains(it.id) }
        }

        override fun onDocumentLoaded(document: PdfDocument) {
            super.onDocumentLoaded(document)

            checkNotNull(pdfFragment).setPageIndex(pageIndex.value, false)
        }
    }

    private val Boolean.scrollMode: PageScrollMode
        get() = when (this) {
            false -> PageScrollMode.PER_PAGE
            true -> PageScrollMode.CONTINUOUS
        }

    private val Fit.fitMode: PageFitMode
        get() = when (this) {
            Fit.WIDTH -> PageFitMode.FIT_TO_WIDTH
            else -> PageFitMode.FIT_TO_SCREEN
        }

    private val Axis.scrollDirection: PageScrollDirection
        get() = when (this) {
            Axis.VERTICAL -> PageScrollDirection.VERTICAL
            Axis.HORIZONTAL -> PageScrollDirection.HORIZONTAL
        }

    private val ReadingProgression.pageBinding: PageBinding
        get() = when (this) {
            ReadingProgression.LTR -> PageBinding.LEFT_EDGE
            ReadingProgression.RTL -> PageBinding.RIGHT_EDGE
        }

    private val Spread.pageLayout: PageLayoutMode
        get() = when (this) {
            Spread.AUTO -> PageLayoutMode.AUTO
            Spread.ALWAYS -> PageLayoutMode.DOUBLE
            Spread.NEVER -> PageLayoutMode.SINGLE
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> Deferred<T>.tryGetCompleted(): T? =
        if (isCompleted) {
            getCompleted()
        } else {
            null
        }
}
