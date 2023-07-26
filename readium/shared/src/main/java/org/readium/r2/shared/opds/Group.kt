/*
 * Module: r2-shared-kotlin
 * Developers: Aferdita Muriqi, Clément Baumann
 *
 * Copyright (c) 2018. Readium Foundation. All rights reserved.
 * Use of this source code is governed by a BSD-style license which is detailed in the
 * LICENSE file present in the project repository where this source code is maintained.
 */

package org.readium.r2.shared.opds

import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Publication

public data class Group(val title: String) {
    var metadata: OpdsMetadata = OpdsMetadata(title = title)
    var links: MutableList<Link> = mutableListOf<Link>()
    var publications: MutableList<Publication> = mutableListOf<Publication>()
    var navigation: MutableList<Link> = mutableListOf<Link>()
}
