package io.github.freya022.botcommands.api.pagination.menu

import io.github.freya022.botcommands.api.core.BContext
import io.github.freya022.botcommands.api.pagination.Paginators

/**
 * A paginator where each page is filled with a list of entries.
 *
 * @param E Type of the entries
 *
 * @see Paginators.menu
 */
class Menu<E> internal constructor(
    context: BContext,
    builder: MenuBuilder<E>
) : AbstractMenu<E, Menu<E>>(
    context,
    builder,
    makePages(builder.entries, builder.transformer, builder.rowPrefixSupplier, builder.maxEntriesPerPage)
) {
    object Defaults {
        /** @see MenuBuilder.setMaxEntriesPerPage */
        @JvmStatic
        var maxEntriesPerPage: Int = 5

        /** @see MenuBuilder.setRowPrefixSupplier */
        @JvmStatic
        var rowPrefixSupplier: RowPrefixSupplier = RowPrefixSupplier.paddedNumberPrefix
    }
}
