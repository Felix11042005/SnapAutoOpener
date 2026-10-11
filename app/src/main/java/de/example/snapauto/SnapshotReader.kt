package de.example.snapauto

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

object SnapshotReader {
    private const val LIMIT = 900

    @Suppress("DEPRECATION")
    fun read(root: AccessibilityNodeInfo, windowBounds: Rect): UiSnapshot {
        val nodes = mutableListOf<UiNode>()
        val queue = java.util.ArrayDeque<Pair<AccessibilityNodeInfo, Int?>>()
        val pkg = root.packageName?.toString().orEmpty()
        val windowId = root.windowId
        queue.add(root to null)
        var truncated = false
        try {
            while (queue.isNotEmpty() && nodes.size < LIMIT) {
                val (node, parent) = queue.removeFirst()
                try {
                    val rect = Rect()
                    node.getBoundsInScreen(rect)
                    val index = nodes.size
                    nodes += UiNode(index, parent,
                        listOfNotNull(node.text?.toString()?.take(256), node.contentDescription?.toString()?.take(256)),
                        Box(rect.left, rect.top, rect.right, rect.bottom),
                        node.isVisibleToUser, node.isEnabled, node.isSelected || node.isChecked,
                        node.isEditable || node.className?.toString()?.endsWith("EditText") == true,
                        node.isClickable, node.collectionInfo != null ||
                            (node.isScrollable && (node.className?.contains("RecyclerView") == true ||
                                node.className?.contains("ListView") == true)),
                        selectionKnown = node.isCheckable)
                    val remaining = (LIMIT - nodes.size - queue.size).coerceAtLeast(0)
                    if (node.childCount > remaining) truncated = true
                    for (i in 0 until minOf(node.childCount, remaining)) {
                        val child = node.getChild(i)
                        if (child != null) queue.add(child to index) else truncated = true
                    }
                } finally { node.recycle() }
            }
        } finally {
            if (queue.isNotEmpty()) truncated = true
            queue.forEach { it.first.recycle() }
        }
        return UiSnapshot(pkg, windowId,
            Box(windowBounds.left, windowBounds.top, windowBounds.right, windowBounds.bottom), nodes, truncated)
    }
}
