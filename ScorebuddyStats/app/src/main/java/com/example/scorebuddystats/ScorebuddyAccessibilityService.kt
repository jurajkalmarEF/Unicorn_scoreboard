package com.example.scorebuddystats

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ScorebuddyAccessibilityService : AccessibilityService() {

    private val TAG = "ScorebuddyA11y"
    private val debounceHandler = Handler(Looper.getMainLooper())
    private var pendingCheck: Runnable? = null
    private var lastDumpedSignature: String? = null
    private var lastProcessedLegKey: String? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.packageName != "com.joofunn.idart") return

        // Debounce: wait for the screen to stop changing for 400ms before
        // reading it, otherwise we read half-rendered frames mid-animation.
        pendingCheck?.let { debounceHandler.removeCallbacks(it) }
        val runnable = Runnable { handleStableContent() }
        pendingCheck = runnable
        debounceHandler.postDelayed(runnable, 400)
    }

    private fun handleStableContent() {
        val root = rootInActiveWindow ?: return
        val nodes = mutableListOf<NodeText>()
        val dumpLines = mutableListOf<String>()
        collect(root, 0, nodes, dumpLines)
        root.recycle()

        // Keep saving diagnostic dumps too - cheap, and useful if detection
        // ever misses a leg so we can see exactly what was on screen.
        val signature = dumpLines.joinToString("\n").hashCode().toString()
        if (signature != lastDumpedSignature) {
            lastDumpedSignature = signature
            ResultsStore.saveDump(applicationContext, dumpLines.joinToString("\n"))
        }

        val result = LegDetector.detect(nodes) ?: return
        if (result.legKey == lastProcessedLegKey) return // already recorded this leg

        Log.i(TAG, "Leg finished (${result.legKey}): placements = ${result.orderedPlayers}")
        val scored = PlacementScorer.score(result.orderedPlayers)
        ResultsStore.saveLegResult(applicationContext, scored)
        lastProcessedLegKey = result.legKey
    }

    /** Walks the accessibility tree, collecting both a structured NodeText list
     *  (used for leg-end detection) and a human-readable dump (diagnostics). */
    private fun collect(
        node: AccessibilityNodeInfo,
        depth: Int,
        nodesOut: MutableList<NodeText>,
        dumpOut: MutableList<String>
    ) {
        val text = node.text?.toString()
        val desc = node.contentDescription?.toString()
        val displayText = when {
            !text.isNullOrBlank() -> text
            !desc.isNullOrBlank() -> desc
            else -> null
        }
        if (displayText != null) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            nodesOut.add(NodeText(displayText, bounds.left, bounds.top, bounds.right, bounds.bottom))
            dumpOut.add("${"  ".repeat(depth)}[${node.className}] \"$displayText\" bounds=$bounds")
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collect(child, depth + 1, nodesOut, dumpOut)
            child.recycle()
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted")
    }
}
