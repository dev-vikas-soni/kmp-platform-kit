package com.droidunplugged.kmp_platform_kit.shared.concurrency

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
@Retention(AnnotationRetention.SOURCE)
expect annotation class JvmSynchronized()
