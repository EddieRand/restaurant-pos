package com.restaurantpos.server.ai

import com.restaurantpos.server.model.AiWorkspaceHowToResultDto
import com.restaurantpos.server.model.AiWorkspaceHowToSourceDto

class AiWorkspaceHowToService(
    private val modelClient: AiWorkspaceModelClient,
    private val loader: ClassLoader = Thread.currentThread().contextClassLoader,
) {
    private val documents: List<HelpDocument> by lazy { loadDocuments() }

    suspend fun answer(question: String): AiWorkspaceHowToResultDto {
        val normalized = question.trim().lowercase()
        val matches = documents.map { document ->
            val score = document.keywords.count { normalized.contains(it.lowercase()) } * 4 +
                document.title.split(" ", "、").count { it.isNotBlank() && normalized.contains(it.lowercase()) } * 2 +
                if (normalized.contains(document.route.lowercase())) 2 else 0
            document to score
        }.sortedByDescending { it.second }.filter { it.second > 0 }.take(3).map { it.first }
        if (matches.isEmpty()) {
            throw AiWorkspaceException("AI_UNSUPPORTED_INTENT", "帮助资料暂未覆盖这个问题")
        }
        val excerpts = matches.map { it.toExcerpt() }
        val generated = modelClient.answerHowTo(question, excerpts)
        if (generated.answer.isBlank()) throw AiWorkspaceException("AI_INVALID_RESPONSE", "产品帮助回答为空", true)
        return AiWorkspaceHowToResultDto(
            answer = generated.answer.trim(),
            steps = generated.steps.filter(String::isNotBlank).take(10),
            sources = excerpts.map { it.source },
        )
    }

    private fun loadDocuments(): List<HelpDocument> {
        val names = loader.getResourceAsStream("ai-help/zh-CN/index.txt")
            ?.bufferedReader()?.use { it.readLines() }
            ?.map(String::trim)?.filter { it.isNotEmpty() && !it.startsWith("#") }
            ?: error("AI help index is missing")
        return names.map { name ->
            val raw = loader.getResourceAsStream("ai-help/zh-CN/$name")
                ?.bufferedReader()?.use { it.readText() }
                ?: error("AI help document is missing: $name")
            parseDocument(raw)
        }
    }

    private fun parseDocument(raw: String): HelpDocument {
        val parts = raw.split("---", limit = 3)
        require(parts.size == 3) { "Invalid AI help document" }
        val metadata = parts[1].lineSequence().mapNotNull { line ->
            line.substringBefore(':', "").trim().takeIf(String::isNotEmpty)?.let { key ->
                key to line.substringAfter(':').trim()
            }
        }.toMap()
        val body = parts[2].trim()
        val section = body.lineSequence().firstOrNull { it.startsWith("## ") }?.removePrefix("## ")
            ?: metadata.getValue("title")
        val steps = body.lineSequence().mapNotNull { line ->
            STEP.matchEntire(line.trim())?.groupValues?.get(1)?.trim()
        }.toList()
        return HelpDocument(
            id = metadata.getValue("id"),
            title = metadata.getValue("title"),
            route = metadata["route"].orEmpty(),
            keywords = metadata["keywords"].orEmpty().split(',').map(String::trim).filter(String::isNotEmpty),
            lastVerifiedAt = metadata.getValue("lastVerifiedAt").toLong(),
            section = section,
            body = body,
            steps = steps,
        )
    }

    private data class HelpDocument(
        val id: String,
        val title: String,
        val route: String,
        val keywords: List<String>,
        val lastVerifiedAt: Long,
        val section: String,
        val body: String,
        val steps: List<String>,
    ) {
        fun toExcerpt() = AiHowToExcerpt(
            source = AiWorkspaceHowToSourceDto(id, title, section, route.ifBlank { null }, lastVerifiedAt),
            text = body,
            steps = steps,
        )
    }

    companion object {
        private val STEP = Regex("^\\d+[.)、]\\s*(.+)$")
    }
}
