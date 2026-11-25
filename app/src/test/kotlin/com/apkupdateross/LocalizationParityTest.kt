package com.apkupdateross

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element

class LocalizationParityTest {

    @Test
    fun `russian locale contains every translatable english resource`() {
        val english = parseResources("values")
        val russian = parseResources("values-ru")

        assertEquals(english.strings.keys, russian.strings.keys)
        assertEquals(english.plurals.keys, russian.plurals.keys)
    }

    @Test
    fun `localized strings preserve format arguments`() {
        val english = parseResources("values")
        val russian = parseResources("values-ru")

        english.strings.forEach { (name, value) ->
            assertEquals("Format arguments differ for $name", placeholders(value), placeholders(russian.strings.getValue(name)))
        }
        english.plurals.forEach { (name, values) ->
            val expected = values.values.flatMap(::placeholders).toSet()
            val actual = russian.plurals.getValue(name).values.flatMap(::placeholders).toSet()
            assertEquals("Format arguments differ for plural $name", expected, actual)
        }
    }

    private fun parseResources(directory: String): ResourceSet {
        val file = File("src/main/res/$directory/strings.xml")
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            isExpandEntityReferences = false
        }
        val root = factory.newDocumentBuilder().parse(file).documentElement
        val strings = linkedMapOf<String, String>()
        val plurals = linkedMapOf<String, Map<String, String>>()

        for (index in 0 until root.childNodes.length) {
            val element = root.childNodes.item(index) as? Element ?: continue
            when (element.tagName) {
                "string" -> if (element.getAttribute("translatable") != "false") {
                    strings[element.getAttribute("name")] = element.textContent
                }
                "plurals" -> {
                    val items = linkedMapOf<String, String>()
                    for (itemIndex in 0 until element.childNodes.length) {
                        val item = element.childNodes.item(itemIndex) as? Element ?: continue
                        if (item.tagName == "item") items[item.getAttribute("quantity")] = item.textContent
                    }
                    plurals[element.getAttribute("name")] = items
                }
            }
        }
        return ResourceSet(strings, plurals)
    }

    private fun placeholders(value: String): List<String> =
        FORMAT_ARGUMENT.findAll(value).map { it.value }.sorted().toList()

    private data class ResourceSet(
        val strings: Map<String, String>,
        val plurals: Map<String, Map<String, String>>
    )

    private companion object {
        val FORMAT_ARGUMENT = Regex("%\\d+\\\$[a-zA-Z]")
    }
}
