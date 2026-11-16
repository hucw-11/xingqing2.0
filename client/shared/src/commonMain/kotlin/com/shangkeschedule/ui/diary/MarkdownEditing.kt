package com.shangkeschedule.ui.diary

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Markdown 源码编辑器的选区格式操作。
 *
 * 所有函数只改 [TextFieldValue] 的文本与选区，不依赖平台文本框实现；
 * 格式工具作用于当前选区/光标，不在正文末尾随意追加符号。
 */

fun toggleInlineWrap(value: TextFieldValue, marker: String): TextFieldValue {
    val text = value.text
    val start = value.selection.min
    val end = value.selection.max
    if (start == end) {
        val insert = marker + marker
        val newText = text.substring(0, start) + insert + text.substring(end)
        return value.copy(
            text = newText,
            selection = TextRange(start + marker.length)
        )
    }
    val selected = text.substring(start, end)
    val beforeStart = start - marker.length
    val afterEnd = end + marker.length
    val alreadyWrapped = beforeStart >= 0 &&
        afterEnd <= text.length &&
        text.substring(beforeStart, start) == marker &&
        text.substring(end, afterEnd) == marker
    return if (alreadyWrapped) {
        val newText = text.substring(0, beforeStart) + selected + text.substring(afterEnd)
        value.copy(
            text = newText,
            selection = TextRange(beforeStart, beforeStart + selected.length)
        )
    } else {
        val newText = text.substring(0, start) + marker + selected + marker + text.substring(end)
        value.copy(
            text = newText,
            selection = TextRange(start + marker.length, end + marker.length)
        )
    }
}

/**
 * 给选区覆盖的每一行加/去前缀（标题、列表、引用）。
 * 所有非空行都已有该前缀时执行去掉，否则统一补上。
 */
fun toggleLinePrefix(value: TextFieldValue, prefix: String): TextFieldValue {
    val text = value.text
    val start = value.selection.min
    val end = value.selection.max
    val lineStart = text.lastIndexOf('\n', (start - 1).coerceAtLeast(0))
        .let { if (it < 0) 0 else it + 1 }
    val lineEnd = text.indexOf('\n', end).let { if (it < 0) text.length else it }
    val block = text.substring(lineStart, lineEnd)
    val lines = block.split('\n')
    val nonBlank = lines.filter { it.isNotBlank() }
    val allPrefixed = nonBlank.isNotEmpty() && nonBlank.all { it.startsWith(prefix) }
    val updated = if (allPrefixed) {
        lines.joinToString("\n") { line ->
            if (line.isNotBlank() && line.startsWith(prefix)) line.removePrefix(prefix) else line
        }
    } else {
        lines.joinToString("\n") { line ->
            if (line.isBlank() || line.startsWith(prefix)) line else prefix + line
        }
    }
    val delta = updated.length - block.length
    val newText = text.substring(0, lineStart) + updated + text.substring(lineEnd)
    val newStart = (start + delta).coerceIn(lineStart, lineStart + updated.length)
    val newEnd = (end + delta).coerceIn(newStart, lineStart + updated.length)
    return value.copy(
        text = newText,
        selection = TextRange(newStart, newEnd)
    )
}

fun insertLink(value: TextFieldValue): TextFieldValue {
    val text = value.text
    val start = value.selection.min
    val end = value.selection.max
    val selected = text.substring(start, end)
    val label = selected.ifBlank { "链接文字" }
    val snippet = "[$label](https://)"
    val newText = text.substring(0, start) + snippet + text.substring(end)
    val urlStart = start + label.length + 3
    return value.copy(
        text = newText,
        selection = TextRange(urlStart, urlStart + "https://".length)
    )
}

fun insertCodeBlock(value: TextFieldValue): TextFieldValue {
    val text = value.text
    val start = value.selection.min
    val end = value.selection.max
    val selected = text.substring(start, end)
    val snippet = "```\n$selected\n```"
    val newText = text.substring(0, start) + snippet + text.substring(end)
    return value.copy(
        text = newText,
        selection = TextRange(start + 4, start + 4 + selected.length)
    )
}

fun insertImageReference(
    value: TextFieldValue,
    attachmentId: String,
    alt: String = "图片"
): TextFieldValue {
    val text = value.text
    val start = value.selection.min
    val token = "![$alt]($DIARY_ATTACHMENT_SCHEME$attachmentId)"
    val needsLeadingBreak = start > 0 && text[start - 1] != '\n'
    val prefix = if (needsLeadingBreak) "\n$token\n" else "$token\n"
    val newText = text.substring(0, start) + prefix + text.substring(start)
    val cursor = start + prefix.length
    return value.copy(text = newText, selection = TextRange(cursor))
}

/** 删除正文中指向某附件的全部图片引用；用于从编辑器移除图片。 */
fun removeImageReference(text: String, attachmentId: String): String {
    val pattern = Regex(
        "!\\[[^\\]]*\\]\\($DIARY_ATTACHMENT_SCHEME${Regex.escape(attachmentId)}\\)\\n?"
    )
    return pattern.replace(text, "")
}

/** 提取正文中引用的附件 ID（用于判断哪些附件仍在正文中出现）。 */
fun referencedAttachmentIds(text: String): Set<String> {
    val pattern = Regex("!\\[[^\\]]*\\]\\($DIARY_ATTACHMENT_SCHEME([^)\\s]+)\\)")
    return pattern.findAll(text).map { it.groupValues[1] }.toSet()
}
