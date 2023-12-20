# Dependency injection

Dependency injection provided by this framework is a more lightweight alternative to dedicated frameworks, 
quite similarly to [Spring](https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-collaborators.html) or [CDI using Weld](https://www.baeldung.com/java-ee-cdi).

Rather than you having to construct objects, you may only request them, 
the framework will then construct it by providing the dependencies required for your service, wherever they may come from.

This avoids having to pass objects everywhere, allowing a more effective decoupling, 
and allows switching implementations in a completely transparent manner.

!!! example

    `ConnectionSupplier` is an interfaced service (an interface that, when implemented, enables the service to be retrieved as such interface).

    You can create an implementation of this interface, per database, enabling you to switch your database, 
    for example, using a configuration file, without changing anything else in your code.

??? info "2.X Migration"

    All singletons / classes with static methods were moved as services, including:

    - `Components`
    - `EventWaiter`
    - `Localization`

    If you were using `ExtensionsBuilder#registerConstructorParameter(Class<T>, ConstructorParameterSupplier<T>)` to get objects in commands, 
    it is basically the same, except in a much more complete framework, and without having to declare everything with this method.

    === "2.X"
        === "Java"
            ```java title="TagDatabase.java"
            public class TagDatabase { /* */ }
            ```
    
            ```java title="TagCommand.java"
            public class TagCommand {
                private final TagDatabase tagDatabase;
    
                public TagCommand(TagDatabase tagDatabase) {
                    this.tagDatabase = tagDatabase;
                }
            }
            ```
    
            ```java title="Builder"
            final var tagDatabase = new TagDatabase(/* */);
            
            CommandsBuilder.newBuilder()
                .registerConstructorParameter(TagDatabase.class, clazz -> tagDatabase)
                // Further configuration
                .build();
            ```

        === "Kotlin"
            ```kotlin title="TagDatabase.kt"
            class TagDatabase { /* */ }
            ```
    
            ```kotlin title="TagCommand.kt"
            class TagCommand(private val tagDatabase: TagDatabase) {
                /* */
            }
            ```
    
            ```kotlin title="Builder"
            val tagDatabase = TagDatabase(/* */);
            
            CommandsBuilder.newBuilder()
                .registerConstructorParameter(TagDatabase::class.java) { tagDatabase }
                // Further configuration
                .build();
            ```

    === "3.X"
        === "Java"
            ```java title="TagDatabase.java"
            @BService //Makes this class injectable, can also pull other services in its constructor
            public class TagDatabase { /* */ }
            ```
    
            ```java title="TagCommand.java"
            @Command
            public class TagCommand {
                private final TagDatabase tagDatabase;
    
                public TagCommand(TagDatabase tagDatabase) {
                    this.tagDatabase = tagDatabase;
                }

                /* */
            }
            ```
    
            No specific builder code required!

        === "Kotlin"
            ```kotlin title="TagDatabase.kt"
            @BService //Makes this class injectable, can also pull other services in its constructor
            class TagDatabase { /* */ }
            ```
    
            ```kotlin title="TagCommand.kt"
            @Command
            class TagCommand(private val tagDatabase: TagDatabase) {
                /* */
            }
            ```
    
            No specific builder code required!

## Creating services
To register a class as a service, add `#!java @BService` to your class declaration.

`#!java @BService` is the base annotation to register a service, 
other annotations exist such as `#!java @Command` and `#!java @Resolver`, 
but the appropriate documentation will specify if such alternatives are required.

!!! info
    
    All classes available for dependency injection must be in the framework's classpath,
    by adding packages to `BBuilder#packages`, or by using `BBuilder#addSearchPath`, 
    all classes are searched recursively.

### Service factories
Service factories are methods that create initialized services themselves,
they accept other services as parameters and define a service with the method's return type.

In addition to the package requirement,
they must be annotated with `#!java @BService`, be in a service, or in an `#!kotlin object`, or be a static method.

!!! note "Terminology"

    Classes registered as services, and service factories, are service providers.

??? example

    === "Java"
        ```java
        public class Config {
            private static Config INSTANCE = null;

            /* */

            // Service factory, registers as "Config" (as it is the return type)
            @BService
            public static Config getInstance() {
                if (INSTANCE == null) {
                    // Of course here you would load the config from a file
                    INSTANCE = new Config();
                }
                
                return INSTANCE;
            }
        }
        ```

    === "Kotlin"
        ```kotlin
        class Config {
            /* */

            companion object {
                // Service factory, registers as "Config" (as it is the return type)
                // You can use any method name
                fun createConfig(): Config {
                    // Of course here you would load the config from a file
                    Config()
                }
            }
        }
        ```

    === "Kotlin property"
        ```kotlin
        class Config {
            /* */

            companion object {
                // Service factory, registers as "Config" (as it is the return type)
                @get:BService
                val instance: Config by lazy {
                    // Of course here you would load the config from a file
                    Config()
                }
            }
        }
        ```

### Conditional services
Some services may not always be instantiable, 
some may require soft dependencies (prevents instantiation if a service is unavailable, without failing),
while some run a set of conditions to determine if a service can be instantiated.

Services that are not instantiable will not be created at startup, 
will be unavailable for injection and do not figure in the list of interfaced services.

!!! info

    All the following annotations must be used alongside a service-declaring annotation, 
    such as `#!java @BService` or `#!java @Command`.

#### Dependencies
The `#!java @Dependencies` annotation lets you define soft dependencies,
that is, if any of these classes in the annotation are unavailable, your service will not be instantiated.

Without the annotation, any unavailable dependency would throw an exception.

#### Interfaced conditions
`#!java @ConditionalService` defines a list of classes implementing `ConditionalServiceChecker`,
the service is only created if none of these classes return an error message.

`ConditionalServiceChecker` can be implemented on any class that has a no-arg constructor, or is an `#!kotlin object`.

??? example

    === "Java"
        ```java
        --8<-- "wiki/java/commands/slash/TagCommand.java:tag_interfaced_condition-java"
        ```

    === "Kotlin"
        ```kotlin
        --8<-- "wiki/commands/slash/TagCommand.kt:tag_interfaced_condition-kotlin"
        ```

#### Annotation conditions
`#!java @Condition` is a *meta-annotation* (an annotation for annotations) which marks your own annotation as being a condition.

Similar to interfaced conditions, they must refer to an implementation of `CustomConditionChecker`, 
to determine if the annotated service can be created, 
you can also indicate if the service creation must throw an exception in case it fails.

The implementation must have a no-arg constructor, or be an `#!kotlin object`

!!! note
    
    The annotation must also be in the framework's classpath.

??? example

    === "Java"
        ```java title="DevCommand.java"
        --8<-- "wiki/java/switches/DevCommand.java:dev_command_annotated_condition-annotation-java"
        ```

        ```java title="DevCommandChecker.java"
        --8<-- "wiki/java/switches/DevCommandChecker.java:dev_command_annotated_condition-checker-java"
        ```

        ```java title="SlashShutdown.java"
        --8<-- "wiki/java/commands/slash/SlashShutdown.java:dev_command_annotated_condition-command-java"
        ```

    === "Kotlin"
        ```kotlin title="DevCommand.kt"
        --8<-- "wiki/switches/DevCommand.kt:dev_command_annotated_condition-annotation-kotlin"
        
        --8<-- "wiki/switches/DevCommand.kt:dev_command_annotated_condition-checker-kotlin"
        ```

        ```kotlin title="SlashShutdown.kt"
        --8<-- "wiki/commands/slash/SlashShutdown.kt:dev_command_annotated_condition-command-kotlin"
        ```

### Interfaced services
Interfaced services are interfaces, or abstract class, marked by `#!java @InterfacedService`,
they must be implemented by a service.

In addition to the service's type,
implementations of these annotated interfaces have the interface's type automatically added.

Some interfaced services may only be implemented once, some may allow multiple implementations,
if an interfaced service only accepts one implementation, multiple implementations can exist,
but only one must be instantiable.

!!! tip

    You can implement multiple interfaced services at once, 
    which may be useful for text, application and component filters.

??? info "2.X Migration"

    Most methods in `CommandsBuilder` accepting interfaces, implementations or lambdas, were moved to interfaced services:

    **Global:**

    - `CommandsBuilder#setComponentManager`: Removed, using components must be enabled in `BComponentsConfigBuilder#useComponents`, and a `ConnectionSupplier` service be present
    - `CommandsBuilder#setSettingsProvider`: Needs to implement `SettingsProvider`
    - `CommandsBuilder#setUncaughtExceptionHandler`: Needs to implement `GlobalExceptionHandler`
    - `CommandsBuilder#setDefaultEmbedFunction`: Needs to implement `DefaultEmbedSupplier` and `DefaultEmbedFooterIconSupplier`

    **Text commands:**

    - `TextCommandBuilder#addTextFilter`: Needs to implement `TextCommandFilter`, and `TextCommandRejectionHandler`
    - `TextCommandBuilder#setHelpBuilderConsumer`: Needs to implement `HelpBuilderConsumer`

    **Application commands:**

    - `ApplicationCommandBuilder#addApplicationFilter`: Needs to implement `ApplicationCommandFilter`, and `ApplicationCommandRejectionHandler`
    - `ApplicationCommandBuilder#addComponentFilter`: Needs to implement `ComponentCommandFilter`, and `ComponentCommandRejectionHandler`

    **Extensions:**

    - `ExtensionsBuilder#registerAutocompletionTransformer`: Needs to implement `AutocompleteTransformer`
    - `ExtensionsBuilder#registerCommandDependency`: Replaced with standard dependency injection
    - `ExtensionsBuilder#registerConstructorParameter`: Replaced with standard dependency injection
    - `ExtensionsBuilder#registerCustomResolver`: Needs to implement `ClassParameterResolver` and `ICustomResolver`
    - `ExtensionsBuilder#registerDynamicInstanceSupplier`: Needs to implement `DynamicSupplier`
    - `ExtensionsBuilder#registerInstanceSupplier`: Replaced by service factories
    - `ExtensionsBuilder#registerParameterResolver`: Needs to implement `ClassParameterResolver` and the resolver interface of your choices

### Service properties
Service providers can have names, additional registered types, and an instantiation priority.

#### Service names
Named services may be useful if you have multiple services of the same type, but need to get a specific one.

The name is either defined by using `#!java @ServiceName`, or with `BService#name` on the service provider.

!!! example

    You can have a caching `HttpClient` named `cachingHttpClient`, while the usual client uses the default name.

#### Service types
In addition to the type of the service provider, 
`#!java @ServiceType` enables you to register a service as a supertype.

#### Service priority
Service priorities control how service providers are sorted.

A higher priority means that the service will be loaded first,
or that an interfaced service will appear first when [requesting interfaced services](#interfaced-services-1).

The priority is either defined by using `#!java @ServicePriority`, or with `BService#priority` on the service provider, 
see their documentation to learn what how service providers are sorted.

## Retrieving services
Any class given by a service provider can be injected into other service providers, 
requesting a service is as simple as declaring a parameter in the class's constructor, 
or the service factory's parameters.

Named services can be retrieved by using `#!java @ServiceName` on the parameter.

!!! tip

    You can also get services manually with `BContext` or `ServiceContainer`, the latter has all methods available, 
    including Kotlin extensions.

!!! example

    === "Java"
        ```java
        @BService // Enables the service to request services and be requested
        public class TagDatabase { /* */ }
        ```

        ```java
        @Command // Enables the command to request services and be requested
        public class TagCommand {
            private final Component components;
            private final TagDatabase tagDatabase;
            
            public TagCommand(
                // You can even request framework services, as long as they are annotated with @BService or @InterfacedService
                Component components,
                // and your own services
                TagDatabase tagDatabase
            ) {
                this.components = components;
                this.tagDatabase = tagDatabase;
            }

            /* */
        }
        ```                

    === "Kotlin"
        ```kotlin
        @BService // Enables the service to request services and be requested
        class TagDatabase { /* */ }
        ```

        ```kotlin
        @Command // Enables the command to request services and be requested
        class TagCommand(
            // You can even request framework services, as long as they are annotated with @BService or @InterfacedService
            // Here I've named it "componentsService" because "components" might conflict with some JDA-KTX builders
            private val componentsService: Components,
            // and your own services
            private val tagDatabase: TagDatabase
        ) {
            /* */
        }
        ```

### Primary providers

When requesting a service of a specific type, there must be at most one service provider for such a type.

If multiple **usable** providers for the same type are present,
no service can be returned unless *one* primary provider is defined.

For example, if you have two [service factories](#service-factories) with the same return type:

- :x: If both are usable
- :white_check_mark: One has a failing condition, meaning you have one usable provider
- :white_check_mark: One is annotated with `#!java @Primary`, in which case this one is prioritized

!!! note

    You can still retrieve existing services with `ServiceContainer#getInterfacedServices/getInterfacedServiceTypes`

### Interfaced services
A list which the element type is an interfaced service can be requested,
the list will then contain all instantiable instances with the specified type.

!!! example

    `#!java List<ApplicationCommandFilter<?>>` will contain all instances implementing `ApplicationCommandFilter`, 
    which are usable.

### Lazy services
Lazy service retrieval enables you to get lazily-created service, delaying the initialization,
or to get services that are not yet available, such as manually injected services (like `JDA`).

!!! example "Retrieving a lazy service"

    === "Java"
        Request a `Lazy` with the element type being the requested service, 
        and then get the service when needed by using `Lazy#getValue`.

    === "Kotlin"
        Request a `ServiceContainer` and use a delegated property, such as:

        `#!kotlin private val helpCommand: IHelpCommand by serviceContainer.lazy()`

!!! note

    Lazy injections cannot contain a list of interfaced services, 
    nor can a list of lazy services be requested.

### Optional services
When a requested service is not available, and is not a [soft-dependency](#dependencies), 
service creation will fail.

[null-safety]: https://kotlinlang.org/docs/null-safety.html
[default-arguments]: https://kotlinlang.org/docs/functions.html#default-arguments

In case your service does not always require the service,
you can prevent failure by using Kotlin's [nullable][null-safety] / [optional][default-arguments] parameters,
but Java users will need a runtime-retained `#!java @Nullable` annotation 
(such as `#!java @javax.annotation.Nullable`, or, in checker-framework or JSpecify) or `#!java @Optional`.

!!! note "Lazy nullability"

    Lazy services can also have their element type be marked as nullable, 
    for example, `#!java Lazy<@Nullable IHelpCommand>`.