package com.example.scorebuddystats

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.util.Locale

/** One finished 3-dart visit of a player: remaining score at the start and at the end of the turn. */
data class Visit(val player: String, val start: Int, val end: Int) {
val total: Int get() = start - end
}

/**
 * Reads the Scorebuddy game screen (left panel = player on the throw) and works out
 * how many points a player scored in one visit. A visit ends when the active player
 * changes or when the remaining score reaches 0 (checkout).
 */
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

// Darts caller: plays recorded clips from assets (falls back to text-to-speech),
// then a funny Slovak line for 100+
class VisitCheer(context: Context) {
private val appContext = context.applicationContext
private val TAG = "VisitCheer"
private val handler = Handler(Looper.getMainLooper())
private var skReady = false
private var enReady = false
private var ttsSk: TextToSpeech? = null
private var ttsEn: TextToSpeech? = null
private var player: MediaPlayer? = null
private val queue = ArrayDeque<Step>()
@Volatile private var gen = 0
private val assetNames: Set<String> by lazy {
try { appContext.assets.list("")?.toSet() ?: emptySet() } catch (e: Exception) { emptySet() }
}

// kind: 0 = recorded clip from assets, 1 = English TTS, 2 = Slovak TTS
private class Step(val kind: Int, val value: String)

companion object {
const val THRESHOLD = 100

// Recorded clips (files in app/src/main/assets), keyed by exact visit score
private val CLIPS = mapOf(
0 to "no_score.mp3",
60 to "score_60.mp3",
80 to "score_80.mp3",
90 to "score_90.mp3",
100 to "score_100.mp3",
120 to "score_120.mp3",
140 to "score_140.mp3",
160 to "score_160.mp3",
177 to "score_177.mp3",
180 to "score_180.mp3"
)
private const val GAME_SHOT = "game_shot.mp3"
private const val FINISH = "what_a_finish.mp3"
private const val ON_FIRE = "on_fire.mp3"

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

fun scoreText(total: Int): String = when {
total <= 0 -> "No score"
total < 100 -> words(total)
total == 100 -> "One hundred"
else -> "One hundred, and " + words(total - 100)
}

// Hlasky podla desiatok: kluc = 100, 110, ... 180 (filmove odkazy, slovenske a ceske)
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
ttsSk = TextToSpeech(appContext) { status ->
if (status == TextToSpeech.SUCCESS) {
val engine = ttsSk
val r = engine?.setLanguage(Locale("sk", "SK")) ?: TextToSpeech.LANG_NOT_SUPPORTED
if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
engine?.setLanguage(Locale("cs", "CZ"))
}
engine?.setOnUtteranceProgressListener(stepListener())
skReady = true
} else {
Log.w(TAG, "Slovak TextToSpeech init failed: $status")
}
}
ttsEn = TextToSpeech(appContext) { status ->
if (status == TextToSpeech.SUCCESS) {
val engine = ttsEn
if (engine != null) {
engine.setLanguage(Locale.UK)
val male = pickMaleVoice(engine)
if (male != null) engine.setVoice(male)
engine.setSpeechRate(0.85f)
engine.setPitch(0.7f)
engine.setOnUtteranceProgressListener(stepListener())
}
enReady = true
} else {
Log.w(TAG, "English TextToSpeech init failed: $status")
}
}
}

private fun stepListener() = object : UtteranceProgressListener() {
override fun onStart(utteranceId: String?) {}
@Deprecated("Deprecated in Java")
override fun onError(utteranceId: String?) { next(utteranceId) }
override fun onDone(utteranceId: String?) { next(utteranceId) }
private fun next(id: String?) {
if (id == "step$gen") handler.post { advance() }
}
}

private fun clipOrSpeech(name: String?, fallbackText: String): Step =
if (name != null && assetNames.contains(name)) Step(0, name) else Step(1, fallbackText)

fun onVisit(visit: Visit) {
Log.i(TAG, "Visit ${visit.player}: ${visit.start} -> ${visit.end} = ${visit.total}")
val total = visit.total
if (total < 0 || total > 180) return

val steps = mutableListOf<Step>()
steps.add(clipOrSpeech(CLIPS[total], scoreText(total)))
val checkout = visit.end == 0 && total > 0
if (checkout) {
steps.add(clipOrSpeech(GAME_SHOT, "Game shot!"))
if (total >= THRESHOLD && assetNames.contains(FINISH)) steps.add(Step(0, FINISH))
} else if (total >= THRESHOLD) {
if (total >= 160 && assetNames.contains(ON_FIRE)) steps.add(Step(0, ON_FIRE))
val joke = LINES[(total / 10) * 10]?.random()
if (joke != null) steps.add(Step(2, joke))
}

handler.post {
boostVolume()
stopAll()
gen++
queue.addAll(steps)
advance()
}
}

private fun stopAll() {
queue.clear()
try {
player?.stop()
player?.release()
} catch (e: Exception) {
Log.w(TAG, "player stop failed", e)
}
player = null
ttsEn?.stop()
ttsSk?.stop()
}

private fun advance() {
val step = queue.removeFirstOrNull() ?: return
when (step.kind) {
0 -> playClip(step.value)
1 -> if (enReady) {
ttsEn?.speak(step.value, TextToSpeech.QUEUE_FLUSH, null, "step$gen")
} else advance()
else -> if (skReady) {
ttsSk?.speak(step.value, TextToSpeech.QUEUE_FLUSH, null, "step$gen")
} else advance()
}
}

private fun assetFile(name: String): File? {
return try {
val f = File(appContext.cacheDir, "clip_$name")
if (!f.exists()) {
appContext.assets.open(name).use { input ->
f.outputStream().use { out -> input.copyTo(out) }
}
}
f
} catch (e: Exception) {
Log.w(TAG, "Missing clip $name", e)
null
}
}

private fun playClip(name: String) {
val file = assetFile(name)
if (file == null) {
advance()
return
}
try {
val mp = MediaPlayer()
mp.setDataSource(file.path)
mp.setOnCompletionListener { m ->
m.release()
if (player === m) player = null
advance()
}
mp.setOnErrorListener { m, _, _ ->
m.release()
if (player === m) player = null
advance()
true
}
mp.prepare()
player = mp
mp.start()
} catch (e: Exception) {
Log.w(TAG, "Clip playback failed", e)
advance()
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

fun shutdown() {
stopAll()
ttsSk?.shutdown()
ttsSk = null
ttsEn?.shutdown()
ttsEn = null
}
}
