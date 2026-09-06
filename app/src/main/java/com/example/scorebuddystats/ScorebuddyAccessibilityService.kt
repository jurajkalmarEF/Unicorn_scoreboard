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

    companion object {
        const val PKG_SCOREBUDDY = "com.joofunn.idart"
        const val PKG_SMARTNESS = "com.evisionhk.smartness"
        val TRACKED_PACKAGES = setOf(PKG_SCOREBUDDY, PKG_SMARTNESS)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.packageName !in TRACKED_PACKAGES) return

        pendingCheck?.let { debounceHandler.removeCallbacks(it) }
        val runnable = Runnable { handleStableContent() }
        pendingCheck = runnable
        debounceHandler.postDelayed(runnable, 200)
    }

    private fun handleStableContent() {
        val root = rootInActiveWindow ?: return
        val activePackage = root.packageName?.toString()
        if (activePackage !in TRACKED_PACKAGES) {
            root.recycle()
            return
        }
        val nodes = mutableListOf<NodeText>()
        val dumpLines = mutableListOf<String>()
        collect(root, 0, nodes, dumpLines)
        root.recycle()

        val signature = dumpLines.joinToString("\n").hashCode().toString()
        if (signature != lastDumpedSignature) {
            lastDumpedSignature = signature
            ResultsStore.saveDump(applicationContext, dumpLines.joinToString("\n"))
        }

        val result: Pair<String, List<String>>? = when (activePackage) {
            PKG_SCOREBUDDY -> LegDetector.detect(nodes)?.let { it.legKey to it.orderedPlayers }
            PKG_SMARTNESS -> SmartnessDetector.detect(nodes)?.let { it.legKey to it.orderedPlayers }
            else -> null
        }
        if (result == null) return
        val (legKey, orderedPlayers) = result

        if (legKey == lastProcessedLegKey) return

        Log.i(TAG, "Leg finished ($legKey): placements = $orderedPlayers")
        val scored = PlacementScorer.score(orderedPlayers)
        ResultsStore.saveLegResult(applicationContext, scored, legKey)
        lastProcessedLegKey = legKey
    }

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
