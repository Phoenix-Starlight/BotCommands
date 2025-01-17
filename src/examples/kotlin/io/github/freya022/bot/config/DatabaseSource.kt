package io.github.freya022.bot.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.freya022.botcommands.api.core.db.HikariSourceSupplier
import io.github.freya022.botcommands.api.core.service.annotations.BService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.flywaydb.core.Flyway
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger { }

// Interfaced service used to retrieve an SQL Connection
@BService
class DatabaseSource(config: Config) : HikariSourceSupplier {
    override val source = HikariDataSource(HikariConfig().apply {
        jdbcUrl = config.databaseConfig.url
        username = config.databaseConfig.user
        password = config.databaseConfig.password

        // At most 2 JDBC connections, the database will suspend/block if all connections are used
        maximumPoolSize = 2
        // Emits a warning and does a thread/coroutine dump after the duration
        leakDetectionThreshold = 10.seconds.inWholeMilliseconds
    })

    init {
        //Migrate BC tables
        createFlyway("bc", "bc_database_scripts").migrate()

        //You can use the same function for your database, you have to change the schema and scripts location
        createFlyway("public", "wiki_database_scripts").migrate()

        logger.info { "Created database source" }
    }

    private fun createFlyway(schema: String, scriptsLocation: String): Flyway = Flyway.configure()
        .dataSource(source)
        .schemas(schema)
        .locations(scriptsLocation)
        .validateMigrationNaming(true)
        .loggers("slf4j")
        .load()
}