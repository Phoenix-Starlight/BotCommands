package io.github.freya022.botcommands.internal.components.repositories

import io.github.freya022.botcommands.api.components.ComponentInteractionFilter
import io.github.freya022.botcommands.api.components.annotations.RequiresComponents
import io.github.freya022.botcommands.api.components.builder.BaseComponentBuilder
import io.github.freya022.botcommands.api.components.builder.ITimeoutableComponent
import io.github.freya022.botcommands.api.components.builder.group.ComponentGroupBuilder
import io.github.freya022.botcommands.api.components.data.ComponentTimeout
import io.github.freya022.botcommands.api.components.data.InteractionConstraints
import io.github.freya022.botcommands.api.core.db.Transaction
import io.github.freya022.botcommands.api.core.db.transactional
import io.github.freya022.botcommands.api.core.service.annotations.BService
import io.github.freya022.botcommands.internal.components.ComponentType
import io.github.freya022.botcommands.internal.components.LifetimeType
import io.github.freya022.botcommands.internal.components.controller.ComponentFilters
import io.github.freya022.botcommands.internal.components.controller.ComponentTimeoutManager
import io.github.freya022.botcommands.internal.components.data.*
import io.github.freya022.botcommands.internal.components.handler.EphemeralComponentHandlers
import io.github.freya022.botcommands.internal.components.handler.EphemeralHandler
import io.github.freya022.botcommands.internal.components.handler.PersistentHandler
import io.github.freya022.botcommands.internal.components.timeout.EphemeralTimeoutHandlers
import io.github.freya022.botcommands.internal.core.db.InternalDatabase
import io.github.freya022.botcommands.internal.utils.throwInternal
import io.github.freya022.botcommands.internal.utils.throwUser
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toKotlinInstant
import net.dv8tion.jda.api.Permission
import java.sql.Timestamp

@BService
@RequiresComponents
internal class ComponentRepository(
    private val database: InternalDatabase,
    private val ephemeralComponentHandlers: EphemeralComponentHandlers,
    private val ephemeralTimeoutHandlers: EphemeralTimeoutHandlers,
    private val componentFilters: ComponentFilters
) {
    internal class DeletedComponent(val componentId: Int, val ephemeralComponentHandlerId: Int?, val ephemeralTimeoutHandlerId: Int?) {
        operator fun component1() = componentId
        operator fun component2() = ephemeralComponentHandlerId
        operator fun component3() = ephemeralTimeoutHandlerId
    }

    private val logger = KotlinLogging.logger { }

    init {
        cleanupEphemeral()
    }

    suspend fun createComponent(builder: BaseComponentBuilder<*>): Int {
        return database.transactional {
            // Create base component
            val componentId: Int = insertBaseComponent(builder.componentType, builder.lifetimeType, builder.oneUse, builder.rateLimitGroup, getFilterNames(builder.filters))

            // Add constraints
            preparedStatement("insert into bc_component_constraints (component_id, users, roles, permissions) VALUES (?, ?, ?, ?)") {
                executeUpdate(
                    componentId,
                    builder.constraints.allowedUsers.toArray(),
                    builder.constraints.allowedRoles.toArray(),
                    Permission.getRaw(builder.constraints.allowingPermissions)
                )
            }

            // Add handler
            val handler = builder.handler
            if (handler is EphemeralHandler<*>) {
                preparedStatement("insert into bc_ephemeral_handler (component_id, handler_id) VALUES (?, ?)") {
                    executeUpdate(componentId, ephemeralComponentHandlers.put(handler))
                }
            } else if (handler is PersistentHandler) {
                preparedStatement("insert into bc_persistent_handler (component_id, handler_name, user_data) VALUES (?, ?, ?)") {
                    executeUpdate(componentId, handler.handlerName, handler.userData.toTypedArray())
                }
            }

            // Add timeout
            insertTimeoutData(builder, componentId)

            componentId
        }
    }

    private fun getFilterNames(list: List<ComponentInteractionFilter<*>>): Array<out String> {
        return Array(list.size) { list[it].javaClass.name }
    }

    suspend fun getComponent(id: Int): ComponentData? = database.transactional(readOnly = true) {
        preparedStatement(
            """
            select lifetime_type, component_type, one_use, users, roles, permissions, group_id, rate_limit_group, filters
            from bc_component component
                     left join bc_component_constraints constraints using (component_id)
                     left join bc_component_component_group componentGroup on componentGroup.component_id = component.component_id
            where component.component_id = ?""".trimIndent()
        ) {
            val dbResult = executeQuery(id).readOrNull() ?: return@preparedStatement null

            val lifetimeType = LifetimeType.fromId(dbResult["lifetime_type"])
            val componentType = ComponentType.fromId(dbResult["component_type"])
            val oneUse: Boolean = dbResult["one_use"]

            if (componentType == ComponentType.GROUP) {
                return@preparedStatement getGroup(id, oneUse)
            }

            val filters = componentFilters.getFilters(dbResult["filters"])
            val rateLimitGroup: String? = dbResult.getOrNull("rate_limit_group")

            val constraints = InteractionConstraints.of(
                dbResult["users"],
                dbResult["roles"],
                dbResult["permissions"]
            )

            when (lifetimeType) {
                LifetimeType.PERSISTENT -> getPersistentComponent(
                    id,
                    componentType,
                    lifetimeType,
                    filters,
                    oneUse,
                    rateLimitGroup,
                    constraints,
                    dbResult["group_id"]
                )
                LifetimeType.EPHEMERAL -> getEphemeralComponent(
                    id,
                    componentType,
                    lifetimeType,
                    filters,
                    oneUse,
                    rateLimitGroup,
                    constraints,
                    dbResult["group_id"]
                )
            }
        }
    }

    suspend fun insertGroup(builder: ComponentGroupBuilder<*>): Int = database.transactional {
        val groupId: Int = insertBaseComponent(ComponentType.GROUP, builder.lifetimeType, false, null, emptyArray())

        // Add timeout
        insertTimeoutData(builder, groupId)

        // Associate group id to its components, and group id to itself
        suspend fun insertComponentGroupAssociation(componentId: Int) {
            preparedStatement("insert into bc_component_component_group (group_id, component_id) VALUES (?, ?)") {
                executeUpdate(groupId, componentId)
            }
        }
        builder.componentIds.forEach { componentId -> insertComponentGroupAssociation(componentId) }
        insertComponentGroupAssociation(groupId)

        // Check if components inside the group have timeouts
        val hasTimeouts: Boolean = preparedStatement(
            """
                select count(*) > 0
                from (select component_id
                      from bc_persistent_timeout
                      union all
                      select component_id
                      from bc_ephemeral_timeout) as timeouted_components
                where component_id = any (?);
            """.trimIndent()
        ) {
            executeQuery(builder.componentIds.toTypedArray()).read().getBoolean(1)
        }

        if (hasTimeouts) {
            throwUser("Cannot put components inside groups if they have a timeout set")
        }

        return@transactional groupId
    }

    context(Transaction)
    private suspend fun insertBaseComponent(
        componentType: ComponentType,
        lifetimeType: LifetimeType,
        oneUse: Boolean,
        rateLimitGroup: String?,
        filterNames: Array<out String>
    ): Int = preparedStatement(
        "insert into bc_component (component_type, lifetime_type, one_use, rate_limit_group, filters) VALUES (?, ?, ?, ?, ?)",
        columnNames = arrayOf("component_id")
    ) {
        executeReturningUpdate(componentType.key, lifetimeType.key, oneUse, rateLimitGroup, filterNames)
            .read()
            .getInt("component_id")
    }

    context(Transaction)
    private suspend fun insertTimeoutData(timeoutableComponentBuilder: ITimeoutableComponent<*>, groupId: Int) {
        val timeout = timeoutableComponentBuilder.timeout
        if (timeout is EphemeralTimeout) {
            preparedStatement("insert into bc_ephemeral_timeout (component_id, expiration_timestamp, handler_id) VALUES (?, ?, ?)") {
                executeUpdate(
                    groupId,
                    Timestamp.from(timeout.expirationTimestamp.toJavaInstant()),
                    timeout.handler?.let(ephemeralTimeoutHandlers::put)
                )
            }
        } else if (timeout is PersistentTimeout) {
            preparedStatement("insert into bc_persistent_timeout (component_id, expiration_timestamp, handler_name, user_data) VALUES (?, ?, ?, ?)") {
                executeUpdate(
                    groupId,
                    timeout.expirationTimestamp.toSqlTimestamp(),
                    timeout.handlerName,
                    timeout.userData.toTypedArray()
                )
            }
        }
    }

    suspend fun deleteComponentsById(ids: List<Int>): List<DeletedComponent> = database.transactional {
        // If the component is a group, then delete the component, and it's contained components
        // If the component is not a group, then delete the component as well as it's group

        val deletedComponents: List<DeletedComponent> = preparedStatement(
            """
                select c.component_id, eh.handler_id as component_handler_id, et.handler_id as timeout_handler_id
                from bc_component c
                         left join bc_ephemeral_handler eh using (component_id)
                         left join bc_ephemeral_timeout et using (component_id)
                where c.component_id = any (?) -- Delete this component
                   or c.component_id = any
                      (select component_id -- (This component is a group) Delete all components from the same group
                       from bc_component_component_group
                       where group_id = any (?))
                   or c.component_id = any
                      (select g.component_id -- (This component is not a group) Find all components from the same group and delete them
                       from bc_component_component_group c
                                join bc_component_component_group g on c.group_id = g.group_id
                       where c.component_id = any (?))
            """.trimIndent()
        ) {
            val idArray = ids.toTypedArray()
            executeQuery(idArray, idArray, idArray).map { DeletedComponent(it["component_id"], it.getOrNull("component_handler_id"), it.getOrNull("timeout_handler_id")) }
        }
        val deletedComponentIds = deletedComponents.map { it.componentId }

        preparedStatement("delete from bc_component where component_id = any (?)") {
            executeUpdate(deletedComponentIds.toTypedArray())
        }

        logger.trace { "Deleted components: ${deletedComponentIds.joinToString()}" }

        return@transactional deletedComponents
    }

    suspend fun scheduleExistingTimeouts(timeoutManager: ComponentTimeoutManager) = database.transactional(readOnly = true) {
        preparedStatement("select component_id, expiration_timestamp from bc_persistent_timeout") {
            executeQuery().forEach { dbResult ->
                timeoutManager.scheduleTimeout(dbResult["component_id"], dbResult.get<Timestamp>("expiration_timestamp").toInstant().toKotlinInstant())
            }
        }
    }

    context(Transaction)
    private suspend fun getPersistentComponent(
        id: Int,
        componentType: ComponentType,
        lifetimeType: LifetimeType,
        filters: List<ComponentInteractionFilter<*>>,
        oneUse: Boolean,
        rateLimitGroup: String?,
        constraints: InteractionConstraints,
        groupId: Int?
    ): PersistentComponentData = preparedStatement(
        """
           select ph.handler_name         as handler_handler_name,
                  ph.user_data            as handler_user_data,
                  pt.expiration_timestamp as timeout_expiration_timestamp,
                  pt.handler_name         as timeout_handler_name,
                  pt.user_data            as timeout_user_data
           from bc_component component
                    left join bc_persistent_handler ph on component.component_id = ph.component_id
                    left join bc_persistent_timeout pt on component.component_id = pt.component_id
           where component.component_id = ?;
        """.trimIndent()
    ) {
        // There is no rows if neither a handler nor a timeout has been set
        val dbResult = executeQuery(id).readOrNull()
            ?: return PersistentComponentData(id, componentType, lifetimeType, filters, oneUse, rateLimitGroup, handler = null, timeout = null, constraints, groupId)

        val handler = dbResult.getOrNull<String>("handler_handler_name")?.let { handlerName ->
            PersistentHandler.fromData(
                handlerName,
                dbResult["handler_user_data"]
            )
        }

        val timeout = dbResult.getOrNull<Timestamp>("timeout_expiration_timestamp")?.let { timestamp ->
            PersistentTimeout.fromData(
                timestamp,
                dbResult["timeout_handler_name"],
                dbResult["timeout_user_data"]
            )
        }

        PersistentComponentData(id, componentType, lifetimeType, filters, oneUse, rateLimitGroup, handler, timeout, constraints, groupId)
    }

    context(Transaction)
    private suspend fun getEphemeralComponent(
        id: Int,
        componentType: ComponentType,
        lifetimeType: LifetimeType,
        filters: List<ComponentInteractionFilter<*>>,
        oneUse: Boolean,
        rateLimitGroup: String?,
        constraints: InteractionConstraints,
        groupId: Int?
    ): EphemeralComponentData = preparedStatement(
        """
            select eh.handler_id           as handler_handler_id,
                   et.expiration_timestamp as timeout_expiration_timestamp,
                   et.handler_id           as timeout_handler_id
            from bc_component component
                     left join bc_ephemeral_handler eh on component.component_id = eh.component_id
                     left join bc_ephemeral_timeout et on component.component_id = et.component_id
            where component.component_id = ?;
        """.trimIndent()
    ) {
        // There is no rows if neither a handler nor a timeout has been set
        val dbResult = executeQuery(id).readOrNull()
            ?: return EphemeralComponentData(id, componentType, lifetimeType, filters, oneUse, rateLimitGroup, handler = null, timeout = null, constraints, groupId)

        val handler = dbResult.getOrNull<Int>("handler_handler_id")?.let { handlerId ->
            ephemeralComponentHandlers[handlerId]
                ?: throwInternal("Unable to find ephemeral handler with id $handlerId")
        }

        val timeout = dbResult.getOrNull<Timestamp>("timeout_expiration_timestamp")?.let { timestamp ->
            EphemeralTimeout(
                timestamp.toInstant().toKotlinInstant(),
                dbResult.getOrNull<Int>("timeout_handler_id")?.let { handlerId ->
                    ephemeralTimeoutHandlers[handlerId]
                        ?: throwInternal("Unable to find ephemeral handler with id $handlerId")
                }
            )
        }

        EphemeralComponentData(id, componentType, lifetimeType, filters, oneUse, rateLimitGroup, handler, timeout, constraints, groupId)
    }

    context(Transaction)
    private suspend fun getGroup(id: Int, oneUse: Boolean): ComponentGroupData {
        val timeout = getGroupTimeout(id)

        val componentIds: List<Int> = preparedStatement(
            """
                select component_id
                from bc_component_component_group
                where group_id = ?
            """.trimIndent()
        ) {
            executeQuery(id).map { it["component_id"] }
        }

        return ComponentGroupData(id, oneUse, timeout, componentIds)
    }

    context(Transaction)
    private suspend fun getGroupTimeout(id: Int): ComponentTimeout? {
        preparedStatement(
            """
               select pt.expiration_timestamp as timeout_expiration_timestamp,
                      pt.handler_name         as timeout_handler_name,
                      pt.user_data            as timeout_user_data
               from bc_persistent_timeout pt
               where component_id = ?;
            """.trimIndent()
        ) {
            val dbResult = executeQuery(id).readOrNull() ?: return@preparedStatement null

            dbResult.getOrNull<Timestamp>("timeout_expiration_timestamp")?.let { timestamp ->
                return PersistentTimeout.fromData(
                    timestamp,
                    dbResult["timeout_handler_name"],
                    dbResult["timeout_user_data"]
                )
            }
        }

        //In case there's no persistent timeout handler
        preparedStatement(
            """
               select pt.expiration_timestamp as timeout_expiration_timestamp,
                      pt.handler_id           as timeout_handler_id
               from bc_ephemeral_timeout pt
               where component_id = ?;
            """.trimIndent()
        ) {
            val dbResult = executeQuery(id).readOrNull() ?: return@preparedStatement null

            val timestamp: Timestamp = dbResult.getOrNull("timeout_expiration_timestamp") ?: return null
            val handlerId: Int = dbResult.getOrNull("timeout_handler_id") ?: return null
            return EphemeralTimeout(
                timestamp.toInstant().toKotlinInstant(),
                ephemeralTimeoutHandlers[handlerId]
                    ?: throwInternal("Unable to find ephemeral handler with id $handlerId")
            )
        }

        return null
    }

    @Suppress("SqlWithoutWhere")
    private fun cleanupEphemeral() = runBlocking {
        database.transactional {
            preparedStatement("truncate table bc_ephemeral_timeout") {
                val deletedRows = executeUpdate()
                logger.trace { "Deleted $deletedRows ephemeral timeout handlers" }
            }

            preparedStatement("truncate table bc_ephemeral_handler") {
                val deletedRows = executeUpdate()
                logger.trace { "Deleted $deletedRows ephemeral handlers" }
            }

            preparedStatement("delete from bc_component where lifetime_type = ?") {
                val deletedRows = executeUpdate(LifetimeType.EPHEMERAL.key)
                logger.trace { "Deleted $deletedRows ephemeral components" }
            }
        }
    }

    private fun Instant.toSqlTimestamp(): Timestamp = Timestamp.from(this.toJavaInstant())
}