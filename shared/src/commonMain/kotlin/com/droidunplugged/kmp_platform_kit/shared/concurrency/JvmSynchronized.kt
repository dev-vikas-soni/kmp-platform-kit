package com.droidunplugged.kmp_platform_kit.shared.concurrency

/**
 * Marks a function as JVM-synchronized.
 *
 * On JVM/Android this is actualized to [kotlin.jvm.Synchronized].
 * On iOS/Native it is a no-op - Kotlin/Native's strict memory model
 * and single-threaded coroutine dispatchers provide safety by default.
 *
 * Usage:
 * ```kotlin
 * @JvmSynchronized
 * fun criticalSection() { ... }
 * ```
 */
@OptIn(ExperimentalMultiplatform::class)
@OptionalExpectation
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
expect annotation class JvmSynchronized()