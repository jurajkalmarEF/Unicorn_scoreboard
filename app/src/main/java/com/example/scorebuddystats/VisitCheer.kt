package com.example.scorebuddystats

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

// One finished visit of a player: remaining score at start and end of the turn
data class Visit(val player: String, val start: Int, val end: Int) {
  val total: Int get() = start - end
}

// Reads the Scorebuddy game screen (left panel = player on the throw) and works out
// how many points a player scored in one visit. A visit ends when the active player
// changes or when the remaining score reaches 0.
object VisitTracker {
  private val NUMBER = Regex("^\\d{1,3}$")
  private val DART = Regex("^(?i)([SDT]\\d{1,2}|BULL|25|MISS)$")

  private var leg: String? = null
  private val remaining = mutableMapOf<String, Int>()
  private var legMax = 0
  private var active: String? = null
  private var startScore = 0
  private var curScore = 0
  private var finished = false

  private fun reset() {
    remaining.clear()
    legMax = 0
    active = null
    finished = false
  }

  fun update(nodes: List<NodeText>): Visit? {
    if (nodes.isEmpty()) return null
    if (nodes.any { it.text.trim().equals("GAME SUMMARY", ignoreCase = true) }) return null

    val legText = nodes.firstOrNull { it.text.trim().startsWith("LEG ", ignoreCase = true) }
    ?.text?.trim()
    if (legText != leg) {
      reset()
      leg = legText
    }

    val maxRight = nodes.maxOf { it.right }
    val panel = nodes.filter { it.right <= maxRight * 0.3 }

    val scoreNode = panel
    .filter { NUMBER.matches(it.text.trim()) && it.bottom - it.top >= 100 }
    .maxByOrNull { it.bottom - it.top } ?: return null
    val nameNode = panel
    .filter {
      val t = it.text.trim()
      !NUMBER.matches(t) && !DART.matches(t) &&
      !t.startsWith("LEG ", ignoreCase = true) && it.bottom - it.top >= 60
    }
    .maxByOrNull { it.bottom - it.top } ?: return null

    val name = nameNode.text.trim()
    val score = scoreNode.text.trim().toInt()
    if (score > legMax) legMax = score

    var result: Visit? = null
    if (active != name) {
      val prev = active
      if (prev != null && !finished) result = Visit(prev, startScore, curScore)
      active = name
      startScore = remaining[name] ?: legMax
      finished = false
    }
    curScore = score
    remaining[name] = score

    if (score == 0 && !finished && result == null) {
      finished = true
      result = Visit(name, startScore, 0)
    }
    return result
  }
}

// Plays a funny Slovak voice line (text-to-speech) for big visits
class VisitCheer(context: Context) {
  private val TAG = "VisitCheer"
  private var ready = false
  private var tts: TextToSpeech? = null

  companion object {
    const val THRESHOLD = 100

    // Lines by tens: key = 100, 110, ... 180
    private val LINES: Map<Int, List<String>> = mapOf(
      100 to listOf("Stovka! Také hody by sa hodili aj do Pelíškov.", "Stovka! Súper si môže ísť dať pivo."),
      110 to listOf("Sto desať! Terč sa potí ako vo Slnku, seno, jahody.", "Ty si to trafil schválne, však?"),
      120 to listOf("Stodvadsať! Aj Kolja by tlieskal.", "No pozrite, on vie hádzať!"),
      130 to listOf("Stotridsať! Ako Černí baroni: terč nemá šancu.", "Terč sa ospravedlňuje, už to nebude robiť."),
      140 to listOf("Sto štyridsať! Zdeněk Svěrák by o tom napísal scenár.", "Toto už nie je náhoda, to je podozrivé."),
      150 to listOf("Stopäťdesiat! Takto by hádzal aj Jára Cimrman.", "Terč práve požiadal o azyl."),
      160 to listOf("Stošesťdesiat! Takéto hody Vesnička má střediskova ešte nevidela.", "Šípky ti dnes veria. Zneužij to."),
      170 to listOf("Stosedemdesiat! Ako tri oriešky pre Popolušku, ešte kúsok a máš to!", "Toto sa už nedá nazvať šťastím!"),
      180 to listOf("Jedna osemdesiat! Maximum! Toto si zaslúži filmový potlesk!", "Sto osemdesiat! Pán sa dnes naozaj trafil!")
      )
  }

  init {
    tts = TextToSpeech(context.applicationContext) { status ->
      if (status == TextToSpeech.SUCCESS) {
        val engine = tts
        val r = engine?.setLanguage(Locale("sk", "SK")) ?: TextToSpeech.LANG_NOT_SUPPORTED
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
          engine?.setLanguage(Locale("cs", "CZ"))
        }
        ready = true
      } else {
        Log.w(TAG, "TextToSpeech init failed: $status")
      }
    }
  }

  fun onVisit(visit: Visit) {
    Log.i(TAG, "Visit ${visit.player}: ${visit.start} -> ${visit.end} = ${visit.total}")
    if (visit.total < THRESHOLD || visit.total > 180) return
    val bucket = (visit.total / 10) * 10
    val pool = LINES[bucket] ?: return
    speak(pool.random())
  }

  private fun speak(text: String) {
    if (!ready) return
    tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "cheer")
  }

  fun shutdown() {
    tts?.stop()
    tts?.shutdown()
    tts = null
  }
}
