package com.kvyii.maelle.ui

import android.content.Context
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView

/**
 * A selectable TextView that reports selection changes and suppresses the
 * system's default Copy/Share action bar — mirroring how the QuickNovel/Cassie
 * reader captured selections. Compose's own text selection doesn't expose the
 * selected range to the app, so the reader renders through this via AndroidView
 * and drives the "Explain" bar from [onSelectionChangedListener].
 */
class SelectableTextView(context: Context) : TextView(context) {

    /** Invoked on every selection change with the current (start, end). */
    var onSelectionChangedListener: ((start: Int, end: Int) -> Unit)? = null

    init {
        setTextIsSelectable(true)
        // Suppress the floating Copy/Share/etc. bar; we provide our own action.
        customSelectionActionModeCallback = object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu) = false
            override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false
            override fun onActionItemClicked(mode: ActionMode, item: MenuItem) = false
            override fun onDestroyActionMode(mode: ActionMode) {}
        }
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        onSelectionChangedListener?.invoke(selStart, selEnd)
    }

    /** Currently selected substring, or empty if nothing is selected. */
    fun currentSelection(): String {
        val start = selectionStart
        val end = selectionEnd
        if (start < 0 || end < 0 || end <= start) return ""
        val content = text?.toString() ?: return ""
        val safeStart = start.coerceIn(0, content.length)
        val safeEnd = end.coerceIn(0, content.length)
        return content.substring(safeStart, safeEnd)
    }
}
