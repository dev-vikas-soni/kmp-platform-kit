package com.droidunplugged.kmp_platform_kit.shared.concurrency

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
@Retention(AnnotationRetention.SOURCE)
actual annotation class JvmSynchronized()
