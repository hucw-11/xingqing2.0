package com.shangkeschedule.ui.diary

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import com.shangkeschedule.ui.theme.appColors
import com.shangkeschedule.ui.theme.appSpacing
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser
import org.jetbrains.compose.resources.stringResource
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.diary_image_missing

/** 日记附件在 Markdown 中的引用前缀：`![说明](attachment://<id>)`。 */
const val DIARY_ATTACHMENT_SCHEME = "attachment://"

private val markdownFlavour = CommonMarkFlavourDescriptor()

/**
 * 用 JetBrains 官方 CommonMark 解析器产出 AST，再映射为 Compose 组件。
 *
 * 原文是唯一数据；这里不把渲染结果写回任何地方。未知/暂不支持的节点按纯文本显示，
 * 不丢内容。
 */
@Composable
fun DiaryMarkdown(
    markdown: String,
    attachmentPaths: Map<String, String>,
    modifier: Modifier = Modifier
) {
    val parsed = remember(markdown) {
        runCatching {
            MarkdownParser(markdownFlavour).buildMarkdownTreeFromString(markdown)
        }.getOrNull()
    }
    var viewingImage by remember { mutableStateOf<String?>(null) }

    if (parsed == null) {
        Text(
            text = markdown,
            modifier = modifier,
            style = MaterialTheme.typography.bodyLarge,
            color = appColors().textPrimary
        )
        return
    }

    MarkdownBlockList(
        nodes = parsed.children,
        content = markdown,
        attachmentPaths = attachmentPaths,
        modifier = modifier,
        onImageClick = { viewingImage = it }
    )

    viewingImage?.let { path ->
        ImageViewerDialog(path = path, onDismiss = { viewingImage = null })
    }
}

@Composable
private fun MarkdownBlockList(
    nodes: List<ASTNode>,
    content: CharSequence,
    attachmentPaths: Map<String, String>,
    modifier: Modifier = Modifier,
    onImageClick: (String) -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        nodes.forEach { node ->
            MarkdownBlock(node, content, attachmentPaths, onImageClick)
        }
    }
}

@Composable
private fun MarkdownBlock(
    node: ASTNode,
    content: CharSequence,
    attachmentPaths: Map<String, String>,
    onImageClick: (String) -> Unit
) {
    when (node.type) {
        MarkdownElementTypes.ATX_1, MarkdownElementTypes.SETEXT_1 ->
            HeadingBlock(node, content, 1)
        MarkdownElementTypes.ATX_2, MarkdownElementTypes.SETEXT_2 ->
            HeadingBlock(node, content, 2)
        MarkdownElementTypes.ATX_3 -> HeadingBlock(node, content, 3)
        MarkdownElementTypes.ATX_4 -> HeadingBlock(node, content, 4)
        MarkdownElementTypes.ATX_5 -> HeadingBlock(node, content, 5)
        MarkdownElementTypes.ATX_6 -> HeadingBlock(node, content, 6)

        MarkdownElementTypes.PARAGRAPH ->
            ParagraphBlock(node, content, attachmentPaths, onImageClick)

        MarkdownElementTypes.UNORDERED_LIST,
        MarkdownElementTypes.ORDERED_LIST ->
            ListBlock(node, content, attachmentPaths, onImageClick)

        MarkdownElementTypes.BLOCK_QUOTE ->
            QuoteBlock(node, content, attachmentPaths, onImageClick)

        MarkdownElementTypes.CODE_FENCE,
        MarkdownElementTypes.CODE_BLOCK ->
            CodeBlock(node, content)

        MarkdownElementTypes.HTML_BLOCK ->
            CodeBlock(node, content, plain = true)

        MarkdownElementTypes.LINK_DEFINITION -> Unit

        MarkdownTokenTypes.HORIZONTAL_RULE -> HorizontalRuleBlock()
        MarkdownTokenTypes.EOL,
        MarkdownTokenTypes.WHITE_SPACE,
        MarkdownTokenTypes.BLOCK_QUOTE,
        MarkdownTokenTypes.LIST_BULLET,
        MarkdownTokenTypes.LIST_NUMBER -> Unit

        else -> {
            // 未知块：有子节点就按块继续映射，没有就按纯文本显示，避免内容消失。
            if (node.children.isNotEmpty()) {
                MarkdownBlockList(node.children, content, attachmentPaths, onImageClick = onImageClick)
            } else {
                val raw = node.getTextInNode(content).toString()
                if (raw.isNotBlank()) {
                    Text(
                        text = raw,
                        style = MaterialTheme.typography.bodyLarge,
                        color = appColors().textPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun HeadingBlock(node: ASTNode, content: CharSequence, level: Int) {
    val text = node.children
        .filter {
            it.type == MarkdownTokenTypes.ATX_CONTENT ||
                it.type == MarkdownTokenTypes.SETEXT_CONTENT
        }
        .joinToString("") { it.getTextInNode(content).toString() }
        .ifBlank {
            node.getTextInNode(content).toString()
                .trim()
                .trimStart('#')
                .trim()
        }
    val style = when (level) {
        1 -> MaterialTheme.typography.headlineMedium
        2 -> MaterialTheme.typography.headlineSmall
        3 -> MaterialTheme.typography.titleLarge
        4 -> MaterialTheme.typography.titleMedium
        else -> MaterialTheme.typography.titleSmall
    }
    Text(
        text = text.trim(),
        style = style.copy(fontWeight = FontWeight.SemiBold),
        color = appColors().textPrimary
    )
}

private sealed interface InlineItem {
    data class TextItem(val text: AnnotatedString) : InlineItem
    data class ImageItem(val attachmentId: String, val alt: String) : InlineItem
}

@Composable
private fun ParagraphBlock(
    node: ASTNode,
    content: CharSequence,
    attachmentPaths: Map<String, String>,
    onImageClick: (String) -> Unit
) {
    val colors = appColors()
    val items = remember(node, content) {
        buildInlineItems(node.children, content)
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { item ->
            when (item) {
                is InlineItem.TextItem -> Text(
                    text = item.text,
                    style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp),
                    color = colors.textPrimary
                )

                is InlineItem.ImageItem -> AttachmentImage(
                    path = attachmentPaths[item.attachmentId],
                    alt = item.alt,
                    onImageClick = onImageClick
                )
            }
        }
    }
}

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun buildInlineItems(nodes: List<ASTNode>, content: CharSequence): List<InlineItem> {
    val items = mutableListOf<InlineItem>()
    var current = AnnotatedString.Builder()
    var hasText = false

    fun flush() {
        if (hasText) {
            items += InlineItem.TextItem(current.toAnnotatedString())
            hasText = false
        }
    }

    fun append(node: ASTNode) {
        when (node.type) {
            MarkdownElementTypes.STRONG -> node.children.forEach { child ->
                current.pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                append(child)
                current.popStyle()
            }

            MarkdownElementTypes.EMPH -> node.children.forEach { child ->
                current.pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                append(child)
                current.popStyle()
            }

            MarkdownElementTypes.CODE_SPAN -> {
                val raw = node.children
                    .filter { it.type != MarkdownTokenTypes.BACKTICK }
                    .joinToString("") { it.getTextInNode(content).toString() }
                current.withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = Color(0x1A7A7A7A)
                    )
                ) {
                    this.append(if (raw.isEmpty()) node.getTextInNode(content).toString() else raw)
                }
                hasText = true
            }

            MarkdownElementTypes.INLINE_LINK,
            MarkdownElementTypes.FULL_REFERENCE_LINK,
            MarkdownElementTypes.SHORT_REFERENCE_LINK -> {
                val destination = node.children
                    .firstOrNull { it.type == MarkdownElementTypes.LINK_DESTINATION }
                    ?.getTextInNode(content)
                    ?.toString()
                    ?.trim()
                    ?.removeSurrounding("<", ">")
                val linkText = node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_TEXT }
                val start = current.length
                if (linkText != null) {
                    linkText.children.forEach { append(it) }
                } else {
                    node.children
                        .filter {
                            it.type != MarkdownElementTypes.LINK_DESTINATION &&
                                it.type != MarkdownElementTypes.LINK_LABEL &&
                                it.type != MarkdownTokenTypes.LBRACKET &&
                                it.type != MarkdownTokenTypes.RBRACKET &&
                                it.type != MarkdownTokenTypes.LPAREN &&
                                it.type != MarkdownTokenTypes.RPAREN
                        }
                        .forEach { append(it) }
                }
                val plain = current.toAnnotatedString().text.substring(start)
                if (destination != null && plain.isNotBlank()) {
                    current.addLink(
                        LinkAnnotation.Url(
                            destination,
                            TextLinkStyles(SpanStyle(textDecoration = TextDecoration.Underline))
                        ),
                        start,
                        current.length
                    )
                }
                hasText = true
            }

            MarkdownElementTypes.AUTOLINK -> {
                val url = node.getTextInNode(content).toString().trim().removeSurrounding("<", ">")
                val start = current.length
                current.append(url)
                current.addLink(
                    LinkAnnotation.Url(
                        url,
                        TextLinkStyles(SpanStyle(textDecoration = TextDecoration.Underline))
                    ),
                    start,
                    current.length
                )
                hasText = true
            }

            MarkdownElementTypes.IMAGE -> {
                flush()
                val link = node.children.firstOrNull {
                    it.type == MarkdownElementTypes.INLINE_LINK ||
                        it.type == MarkdownElementTypes.FULL_REFERENCE_LINK ||
                        it.type == MarkdownElementTypes.SHORT_REFERENCE_LINK
                }
                val destination = link?.children
                    ?.firstOrNull { it.type == MarkdownElementTypes.LINK_DESTINATION }
                    ?.getTextInNode(content)
                    ?.toString()
                    ?.trim()
                    ?.removeSurrounding("<", ">")
                    .orEmpty()
                val alt = link?.children
                    ?.firstOrNull { it.type == MarkdownElementTypes.LINK_TEXT }
                    ?.getTextInNode(content)
                    ?.toString()
                    ?.trim('[', ']')
                    .orEmpty()
                val attachmentId = destination
                    .takeIf { it.startsWith(DIARY_ATTACHMENT_SCHEME) }
                    ?.removePrefix(DIARY_ATTACHMENT_SCHEME)
                    .orEmpty()
                items += InlineItem.ImageItem(attachmentId, alt)
                current = AnnotatedString.Builder()
                hasText = false
            }

            MarkdownTokenTypes.HARD_LINE_BREAK -> {
                current.append("\n")
                hasText = true
            }

            MarkdownTokenTypes.EOL -> {
                current.append("\n")
                hasText = true
            }

            MarkdownTokenTypes.TEXT,
            MarkdownTokenTypes.WHITE_SPACE,
            MarkdownTokenTypes.HTML_TAG,
            MarkdownTokenTypes.BAD_CHARACTER,
            MarkdownTokenTypes.EMPH -> {
                current.append(node.getTextInNode(content).toString())
                hasText = true
            }

            else -> {
                if (node.children.isEmpty()) {
                    val raw = node.getTextInNode(content).toString()
                    if (raw.isNotEmpty()) {
                        current.append(raw)
                        hasText = true
                    }
                } else {
                    node.children.forEach { append(it) }
                }
            }
        }
    }

    nodes.forEach { append(it) }
    if (hasText) {
        items += InlineItem.TextItem(current.toAnnotatedString())
    }
    return items
}

@Composable
private fun ListBlock(
    node: ASTNode,
    content: CharSequence,
    attachmentPaths: Map<String, String>,
    onImageClick: (String) -> Unit
) {
    val ordered = node.type == MarkdownElementTypes.ORDERED_LIST
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        node.children
            .filter { it.type == MarkdownElementTypes.LIST_ITEM }
            .forEachIndexed { index, item ->
                val markerNode = item.children.firstOrNull {
                    it.type == MarkdownTokenTypes.LIST_NUMBER || it.type == MarkdownTokenTypes.LIST_BULLET
                }
                val marker = markerNode
                    ?.getTextInNode(content)
                    ?.toString()
                    ?.trim()
                    ?.ifBlank { null }
                    ?: if (ordered) "${index + 1}." else "•"
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = marker,
                        modifier = Modifier.width(28.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = appColors().textSecondary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        MarkdownBlockList(
                            nodes = item.children.filter {
                                it.type != MarkdownTokenTypes.LIST_NUMBER &&
                                    it.type != MarkdownTokenTypes.LIST_BULLET &&
                                    it.type != MarkdownTokenTypes.EOL &&
                                    it.type != MarkdownTokenTypes.WHITE_SPACE
                            },
                            content = content,
                            attachmentPaths = attachmentPaths,
                            onImageClick = onImageClick
                        )
                    }
                }
            }
    }
}

@Composable
private fun QuoteBlock(
    node: ASTNode,
    content: CharSequence,
    attachmentPaths: Map<String, String>,
    onImageClick: (String) -> Unit
) {
    val colors = appColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.primarySoft.copy(alpha = 0.55f))
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .heightIn(min = 36.dp)
                .background(colors.primary)
        )
        Column(modifier = Modifier.padding(12.dp)) {
            MarkdownBlockList(
                nodes = node.children.filter {
                    it.type != MarkdownTokenTypes.BLOCK_QUOTE &&
                        it.type != MarkdownTokenTypes.EOL &&
                        it.type != MarkdownTokenTypes.WHITE_SPACE
                },
                content = content,
                attachmentPaths = attachmentPaths,
                onImageClick = onImageClick
            )
        }
    }
}

@Composable
private fun CodeBlock(node: ASTNode, content: CharSequence, plain: Boolean = false) {
    val code = node.children
        .filter {
            it.type == MarkdownTokenTypes.CODE_FENCE_CONTENT ||
                it.type == MarkdownTokenTypes.CODE_LINE ||
                it.type == MarkdownTokenTypes.HTML_BLOCK_CONTENT
        }
        .joinToString("") { it.getTextInNode(content).toString() }
        .ifBlank {
            node.getTextInNode(content).toString()
                .lines()
                .drop(1)
                .dropLastWhile { line -> line.isBlank() || line.startsWith("```") || line.startsWith("~~~") }
                .joinToString("\n")
        }
        .trimEnd()
    val colors = appColors()
    Text(
        text = code,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.inputBg)
            .horizontalScroll(rememberScrollState())
            .padding(12.dp),
        style = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = if (plain) FontFamily.Default else FontFamily.Monospace,
            fontSize = 13.sp
        ),
        color = colors.textPrimary
    )
}

@Composable
private fun HorizontalRuleBlock() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .height(1.dp)
            .background(appColors().divider)
    )
}

@Composable
private fun AttachmentImage(
    path: String?,
    alt: String,
    onImageClick: (String) -> Unit
) {
    val colors = appColors()
    if (path == null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.inputBg),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(Res.string.diary_image_missing),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary
            )
        }
        return
    }
    AsyncImage(
        model = path,
        contentDescription = alt.ifBlank { null },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 360.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable { onImageClick(path) },
        contentScale = ContentScale.Fit
    )
}

@Composable
private fun ImageViewerDialog(path: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = path,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(appSpacing().cardInner),
                contentScale = ContentScale.Fit
            )
        }
    }
}
