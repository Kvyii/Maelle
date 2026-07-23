package com.kvyii.maelle.ui

import android.util.TypedValue
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material3.MaterialTheme
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.html.HtmlPlugin

/**
 * Renders [markdown] as formatted text (bold, lists, tables, code, etc.) using
 * Markwon in a native TextView. The AI assistant returns Markdown, so this keeps
 * its responses readable instead of showing raw `**` / `|` syntax.
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    fontSizeSp: Float = 15f,
) {
    val context = LocalContext.current
    val markwon = remember(context) {
        Markwon.builder(context)
            .usePlugin(TablePlugin.create(context))
            .usePlugin(HtmlPlugin.create())
            .build()
    }
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()

    AndroidView(
        modifier = modifier,
        factory = { ctx -> TextView(ctx) },
        update = { tv ->
            tv.setTextColor(textColor)
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp)
            markwon.setMarkdown(tv, markdown)
        },
    )
}
