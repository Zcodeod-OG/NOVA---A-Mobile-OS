package com.nova.runtime.planner.serialization

import com.nova.runtime.models.ActionNode
import com.nova.runtime.models.Nag
import com.nova.runtime.models.Priority
import java.util.UUID

interface NagSerializer {
    fun serialize(graph: Nag): String
    fun deserialize(payload: String): Nag
}

/**
 * Canonical JSON serialization for NAG — IAS §3, TDD §12.
 * Keys are sorted for deterministic output.
 */
class CanonicalNagSerializer : NagSerializer {
    override fun serialize(graph: Nag): String = buildString {
        append("{\n")
        append("  \"version\": 1,\n")
        append("  \"graphId\": \"${graph.graphId}\",\n")
        append("  \"metadata\": ${mapToJson(graph.metadata)},\n")
        append("  \"taskHierarchy\": ${stringListToJson(graph.taskHierarchy)},\n")
        append("  \"actionNodes\": [\n")
        val sortedNodes = graph.actionNodes.sortedBy { it.id.toString() }
        sortedNodes.forEachIndexed { index, node ->
            append("    ${actionNodeToJson(node)}")
            if (index < sortedNodes.lastIndex) append(",")
            append("\n")
        }
        append("  ],\n")
        append("  \"dependencies\": ${dependenciesToJson(graph.dependencies)},\n")
        append("  \"executionPolicies\": ${mapToJson(graph.executionPolicies)}\n")
        append("}")
    }

    override fun deserialize(payload: String): Nag {
        val root = JsonParser(payload.trim()).parseObject()
        require(root.int("version") == 1) { "Unsupported NAG version" }

        return Nag(
            graphId = UUID.fromString(root.string("graphId")),
            metadata = root.stringMap("metadata"),
            taskHierarchy = root.stringList("taskHierarchy"),
            actionNodes = root.objectList("actionNodes").map(::parseActionNode),
            dependencies = root.uuidListMap("dependencies"),
            executionPolicies = root.stringMap("executionPolicies"),
        )
    }

    private fun parseActionNode(obj: ParsedObject): ActionNode =
        ActionNode(
            id = UUID.fromString(obj.string("id")),
            actionType = obj.string("actionType"),
            inputs = obj.stringMap("inputs"),
            outputs = obj.stringMap("outputs"),
            dependencies = obj.uuidList("dependencies"),
            timeoutMs = obj.long("timeoutMs"),
            retryPolicy = obj.string("retryPolicy"),
            rollbackPolicy = obj.string("rollbackPolicy"),
            executionPriority = Priority.valueOf(obj.string("executionPriority")),
        )

    private fun actionNodeToJson(node: ActionNode): String = buildString {
        append("{")
        append("\"id\":\"${node.id}\",")
        append("\"actionType\":${stringToJson(node.actionType)},")
        append("\"inputs\":${mapToJson(node.inputs)},")
        append("\"outputs\":${mapToJson(node.outputs)},")
        append("\"dependencies\":${uuidListToJson(node.dependencies.sortedBy { it.toString() })}, ")
        append("\"timeoutMs\":${node.timeoutMs},")
        append("\"retryPolicy\":${stringToJson(node.retryPolicy)},")
        append("\"rollbackPolicy\":${stringToJson(node.rollbackPolicy)},")
        append("\"executionPriority\":${stringToJson(node.executionPriority.name)}")
        append("}")
    }

    private fun mapToJson(map: Map<String, String>): String {
        if (map.isEmpty()) return "{}"
        return map.entries.sortedBy { it.key }.joinToString(prefix = "{", postfix = "}") { (key, value) ->
            "${stringToJson(key)}:${stringToJson(value)}"
        }
    }

    private fun stringListToJson(values: List<String>): String =
        values.joinToString(prefix = "[", postfix = "]") { stringToJson(it) }

    private fun uuidListToJson(values: List<UUID>): String =
        values.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }

    private fun dependenciesToJson(dependencies: Map<UUID, List<UUID>>): String {
        if (dependencies.isEmpty()) return "{}"
        return dependencies.entries.sortedBy { it.key.toString() }.joinToString(prefix = "{", postfix = "}") { (key, value) ->
            "${stringToJson(key.toString())}:${uuidListToJson(value.sortedBy { it.toString() })}"
        }
    }

    private fun stringToJson(value: String): String = buildString {
        append('"')
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
        append('"')
    }
}

private class JsonParser(private val source: String) {
    private var index = 0

    fun parseObject(): ParsedObject {
        expect('{')
        val values = linkedMapOf<String, ParsedValue>()
        if (peek() != '}') {
            while (true) {
                val key = parseString()
                expect(':')
                values[key] = parseValue()
                if (peek() == ',') index++ else break
            }
        }
        expect('}')
        return ParsedObject(values)
    }

    private fun parseValue(): ParsedValue = when (peek()) {
        '{' -> ParsedObjectValue(parseObject())
        '[' -> ParsedArrayValue(parseArray())
        '"' -> ParsedStringValue(parseString())
        in '0'..'9', '-' -> ParsedNumberValue(parseNumber())
        else -> error("Unexpected character '${peek()}' at $index")
    }

    private fun parseArray(): List<ParsedValue> {
        expect('[')
        val values = mutableListOf<ParsedValue>()
        if (peek() != ']') {
            while (true) {
                values += parseValue()
                if (peek() == ',') index++ else break
            }
        }
        expect(']')
        return values
    }

    private fun parseString(): String {
        expect('"')
        val builder = StringBuilder()
        while (index < source.length) {
            when (val char = source[index++]) {
                '"' -> return builder.toString()
                '\\' -> {
                    val escaped = source[index++]
                    builder.append(
                        when (escaped) {
                            'n' -> '\n'
                            'r' -> '\r'
                            't' -> '\t'
                            '"' -> '"'
                            '\\' -> '\\'
                            else -> escaped
                        },
                    )
                }
                else -> builder.append(char)
            }
        }
        error("Unterminated string at $index")
    }

    private fun parseNumber(): String {
        val start = index
        if (peek() == '-') index++
        while (index < source.length && source[index].isDigit()) index++
        return source.substring(start, index)
    }

    private fun expect(char: Char) {
        skipWhitespace()
        require(index < source.length && source[index] == char) {
            "Expected '$char' at $index but found '${source.getOrNull(index)}'"
        }
        index++
        skipWhitespace()
    }

    private fun peek(): Char {
        skipWhitespace()
        return source[index]
    }

    private fun skipWhitespace() {
        while (index < source.length && source[index].isWhitespace()) index++
    }
}

private sealed interface ParsedValue
private data class ParsedObjectValue(val value: ParsedObject) : ParsedValue
private data class ParsedArrayValue(val value: List<ParsedValue>) : ParsedValue
private data class ParsedStringValue(val value: String) : ParsedValue
private data class ParsedNumberValue(val value: String) : ParsedValue

private class ParsedObject(private val fields: Map<String, ParsedValue>) {
    fun string(key: String): String = (fields.getValue(key) as ParsedStringValue).value
    fun int(key: String): Int = (fields.getValue(key) as ParsedNumberValue).value.toInt()
    fun long(key: String): Long = (fields.getValue(key) as ParsedNumberValue).value.toLong()

    fun stringMap(key: String): Map<String, String> {
        val objectValue = fields[key] as? ParsedObjectValue ?: return emptyMap()
        return objectValue.value.fields.mapValues { (_, value) ->
            (value as ParsedStringValue).value
        }
    }

    fun stringList(key: String): List<String> {
        val arrayValue = fields[key] as? ParsedArrayValue ?: return emptyList()
        return arrayValue.value.map { (it as ParsedStringValue).value }
    }

    fun uuidList(key: String): List<UUID> = stringList(key).map(UUID::fromString)

    fun objectList(key: String): List<ParsedObject> {
        val arrayValue = fields[key] as? ParsedArrayValue ?: return emptyList()
        return arrayValue.value.map { (it as ParsedObjectValue).value }
    }

    fun uuidListMap(key: String): Map<UUID, List<UUID>> {
        val objectValue = fields[key] as? ParsedObjectValue ?: return emptyMap()
        return objectValue.value.fields.map { (rawKey, rawValue) ->
            UUID.fromString(rawKey) to
                (rawValue as ParsedArrayValue).value.map { UUID.fromString((it as ParsedStringValue).value) }
        }.toMap()
    }
}
