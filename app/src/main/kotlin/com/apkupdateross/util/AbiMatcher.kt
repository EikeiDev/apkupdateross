package com.apkupdateross.util

object AbiMatcher {
    private const val ARM32 = "armeabi-v7a"
    private const val ARM64 = "arm64-v8a"
    private const val X86 = "x86"
    private const val X86_64 = "x86_64"

    private val universalArchNames = setOf("universal", "noarch", "all")
    private val arm32ArchNames = setOf(ARM32, "armeabi_v7a", "armeabi", "arm-v7a", "arm_v7a", "armv7", "armv7a", "arm32", "arm")
    private val arm64ArchNames = setOf(ARM64, "arm64_v8a", "arm64", "aarch64")
    private val x86ArchNames = setOf(X86, "i686")
    private val x86_64ArchNames = setOf(X86_64, "x86-64", "x64", "amd64")

    private val universalPatterns = listOf("universal", "noarch")
    private val arm64Patterns = listOf("arm64[-_]?v8a", "arm64", "aarch64")
    private val arm32Patterns = listOf("armeabi[-_]?v7a", "armeabi", "arm[-_]?v7a", "armv7a", "armv7", "arm32", "arm")
    private val x86_64Patterns = listOf("x86[-_]?64", "x64", "amd64")
    private val x86Patterns = listOf("i686", "x86(?![-_]?64)")

    fun isCompatible(apkArches: List<String>, supportedAbis: List<String>): Boolean {
        if (apkArches.isEmpty()) return true

        val supported = supportedAbis
            .mapNotNull { normalizeAbi(it) }
            .toSet()

        return apkArches.any { arch ->
            val normalized = normalizeAbi(arch)
            normalized in universalArchNames || normalized in supported
        }
    }

    fun <T> selectCompatible(
        items: List<T>,
        supportedAbis: List<String>,
        nameSelector: (T) -> String,
        sizeSelector: (T) -> Long
    ): T? {
        if (items.isEmpty()) return null

        val supported = supportedAbis
            .mapNotNull { normalizeAbi(it) }
            .distinct()
        val annotated = items.map { item -> AnnotatedItem(item, detectAbiTags(nameSelector(item))) }

        supported.forEach { abi ->
            annotated
                .filter { abi in it.abis }
                .maxByOrNull { sizeSelector(it.item) }
                ?.let { return it.item }
        }

        annotated
            .filter { it.abis.any { abi -> abi in universalArchNames } }
            .maxByOrNull { sizeSelector(it.item) }
            ?.let { return it.item }

        val unknownAbiItems = annotated.filter { it.abis.isEmpty() }
        if (unknownAbiItems.isNotEmpty()) {
            return unknownAbiItems.maxByOrNull { sizeSelector(it.item) }?.item
        }

        return null
    }

    private fun detectAbiTags(value: String): Set<String> {
        val text = value.lowercase()
        return buildSet {
            if (universalPatterns.any { text.containsTokenPattern(it) }) addAll(universalArchNames)
            if (arm64Patterns.any { text.containsTokenPattern(it) }) add(ARM64)
            if (arm32Patterns.any { text.containsTokenPattern(it) }) add(ARM32)
            if (x86_64Patterns.any { text.containsTokenPattern(it) }) add(X86_64)
            if (x86Patterns.any { text.containsTokenPattern(it) }) add(X86)
        }
    }

    private fun normalizeAbi(value: String): String? {
        val normalized = value.trim().lowercase()
        return when {
            normalized in universalArchNames -> normalized
            normalized in arm32ArchNames -> ARM32
            normalized in arm64ArchNames -> ARM64
            normalized in x86ArchNames -> X86
            normalized in x86_64ArchNames -> X86_64
            else -> null
        }
    }

    private fun String.containsTokenPattern(pattern: String): Boolean =
        Regex("(^|[^a-z0-9])$pattern($|[^a-z0-9])", RegexOption.IGNORE_CASE).containsMatchIn(this)

    private data class AnnotatedItem<T>(
        val item: T,
        val abis: Set<String>
    )
}
