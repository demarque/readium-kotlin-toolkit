/*
 * Module: r2-shared-kotlin
 * Developers: Mickaël Menu
 *
 * Copyright (c) 2020. Readium Foundation. All rights reserved.
 * Use of this source code is governed by a BSD-style license which is detailed in the
 * LICENSE file present in the project repository where this source code is maintained.
 */

package org.readium.r2.shared.publication

import android.os.Parcelable
import java.util.*
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.readium.r2.shared.util.MapCompanion

@Serializable
@Parcelize
enum class ReadingProgression(val value: String) : Parcelable {
    /** Right to left */
    @SerialName("rtl") RTL("rtl"),
    /** Left to right */
    @SerialName("ltr") LTR("ltr");

    companion object : MapCompanion<String, ReadingProgression>(values(), ReadingProgression::value) {

        override fun get(key: String?): ReadingProgression? =
            // For backward compatibility, we allow uppercase keys.
            keys.firstOrNull { it == key?.lowercase(Locale.ROOT) }
                ?.let { map[it] }
    }
}
