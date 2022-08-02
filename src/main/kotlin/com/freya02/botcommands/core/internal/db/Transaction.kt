package com.freya02.botcommands.core.internal.db

import org.intellij.lang.annotations.Language
import java.sql.Connection

internal class Transaction(val database: Database, val connection: Connection) {
    inline fun <R> preparedStatement(@Language("PostgreSQL") sql: String, block: KPreparedStatement.() -> R): R {
        return block(KPreparedStatement(database, connection.prepareStatement(sql)))
    }
}