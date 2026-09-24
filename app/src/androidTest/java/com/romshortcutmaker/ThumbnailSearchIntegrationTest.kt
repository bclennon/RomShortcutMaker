package com.romshortcutmaker

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThumbnailSearchIntegrationTest {

    private fun invokeMainKt(functionName: String, parameterTypes: Array<Class<*>>, vararg args: Any?): Any? {
        val clazz = Class.forName("com.romshortcutmaker.MainActivityKt")
        val method = clazz.getDeclaredMethod(functionName, *parameterTypes)
        method.isAccessible = true
        return method.invoke(null, *args)
    }

    private fun loadThumbnailListingEntries(database: String): List<Any> {
        val result = invokeMainKt(
            functionName = "loadThumbnailListingEntries",
            parameterTypes = arrayOf(String::class.java),
            database
        )

        @Suppress("UNCHECKED_CAST")
        return (result as? List<Any>).orEmpty()
    }

    private fun buildComparisonKey(value: String): String {
        return invokeMainKt(
            functionName = "buildComparisonKey",
            parameterTypes = arrayOf(String::class.java),
            value
        ) as String
    }

    private fun imageNameFromEntry(entry: Any): String {
        val imageNameField = entry.javaClass.getDeclaredField("imageName")
        imageNameField.isAccessible = true
        return imageNameField.get(entry) as String
    }

    @Test
    fun loadThumbnailListingEntries_returnsDataForNesDatabase() {
        val entries = loadThumbnailListingEntries("Nintendo - Nintendo Entertainment System")

        assertTrue(
            "Expected online listing entries for NES database, but got ${entries.size}.",
            entries.isNotEmpty()
        )
    }

    @Test
    fun searchLogic_findsKnownNesGameFromListing() {
        val entries = loadThumbnailListingEntries("Nintendo - Nintendo Entertainment System")
        val superMarioKey = buildComparisonKey("Super Mario Bros")

        val foundMatch = entries.any { entry ->
            val imageName = imageNameFromEntry(entry).substringBeforeLast('.')
            val imageKey = buildComparisonKey(imageName)
            imageKey == superMarioKey || imageKey.contains(superMarioKey)
        }

        assertTrue(
            "Expected to find a thumbnail listing match for Super Mario Bros in NES database.",
            foundMatch
        )
    }
}
