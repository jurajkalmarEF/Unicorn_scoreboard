package com.example.scorebuddystats

import android.content.Context
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
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

// Darts caller: announces every visit in English, then a funny Slovak line for 100+
class VisitCheer(context: Context) {
private val appContext = context.applicationContext
private val TAG = "VisitCheer"
private var skReady = false
private var enReady = false
private var ttsSk: TextToSpeech? = null
private var ttsEn: TextToSpeech? = null
@Volatile private var pendingSk: String? = null

companion object {
const val THRESHOLD = 100

private val ONES = arrayOf(
"", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
"eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen",
"eighteen", "nineteen"
)
private val TENS = arrayOf("", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty")

private fun words(n: Int): String =
if (n < 20) ONES[n] else TENS[n / 10] + (if (n % 10 != 0) " " + ONES[n % 10] else "")

// Known male Google voice codes (en-GB first, then en-US)
private val MALE_CODES = listOf("-gbb-", "-gbd-", "-iom-", "-iol-", "-tpd-")

private fun pickMaleVoice(engine: TextToSpeech): Voice? {
val voices = try { engine.voices } catch (e: Exception) { null } ?: return null
val usable = voices.filter {
it.locale.language == "en" && !it.isNetworkConnectionRequired &&
!it.features.contains("notInstalled")
}
val named = usable.firstOrNull {
it.name.contains("male", ignoreCase = true) && !it.name.contains("female", ignoreCase = true)
}
if (named != null) return named
for (code in MALE_CODES) {
val v = usable.firstOrNull { it.name.contains(code) }
if (v != null) return v
}
return null
}

fun callerText(visit: Visit): String {
val total = visit.total
val score = when {
total <= 0 -> "No score"
total < 100 -> words(total)
total == 100 -> "One hundred"
else -> "One hundred, and " + words(total - 100)
}
return if (visit.end == 0 && total > 0) "$score! Game shot!" else "$score!"
}

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
ttsSk = TextToSpeech(context.applicationContext) { status ->
if (status == TextToSpeech.SUCCESS) {
val engine = ttsSk
val r = engine?.setLanguage(Locale("sk", "SK")) ?: TextToSpeech.LANG_NOT_SUPPORTED
if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
engine?.setLanguage(Locale("cs", "CZ"))
}
skReady = true
} else {
Log.w(TAG, "Slovak TextToSpeech init failed: $status")
}
}
ttsEn = TextToSpeech(context.applicationContext) { status ->
if (status == TextToSpeech.SUCCESS) {
val engine = ttsEn
if (engine != null) {
engine.setLanguage(Locale.UK)
val male = pickMaleVoice(engine)
if (male != null) engine.setVoice(male)
engine.setSpeechRate(0.85f)
engine.setPitch(0.7f)
}
engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
override fun onStart(utteranceId: String?) {}
override fun onError(utteranceId: String?) {}
override fun onDone(utteranceId: String?) {
val joke = pendingSk
pendingSk = null
if (utteranceId == "caller" && joke != null) speakSk(joke)
}
})
enReady = true
} else {
Log.w(TAG, "English TextToSpeech init failed: $status")
}
}
}

fun onVisit(visit: Visit) {
Log.i(TAG, "Visit ${visit.player}: ${visit.start} -> ${visit.end} = ${visit.total}")
if (visit.total < 0 || visit.total > 180) return
val bucket = (visit.total / 10) * 10
val joke = if (visit.total >= THRESHOLD) LINES[bucket]?.random() else null
boostVolume()
if (enReady) {
pendingSk = joke
ttsEn?.speak(callerText(visit), TextToSpeech.QUEUE_FLUSH, null, "caller")
} else if (joke != null) {
speakSk(joke)
}
}

// Raise the media volume to at least 90 percent so the caller is loud enough
private fun boostVolume() {
try {
val am = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
val target = (max * 0.9).toInt()
if (am.getStreamVolume(AudioManager.STREAM_MUSIC) < target) {
am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
}
} catch (e: Exception) {
Log.w(TAG, "Could not raise volume", e)
}
}

private fun speakSk(text: String) {
if (!skReady) return
ttsSk?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "joke")
}

fun shutdown() {
ttsSk?.stop()
ttsSk?.shutdown()
ttsSk = null
ttsEn?.stop()
ttsEn?.shutdown()
ttsEn = null
}
}
