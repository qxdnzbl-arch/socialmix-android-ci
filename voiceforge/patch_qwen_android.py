#!/usr/bin/env python3
from pathlib import Path
import re
import sys

root = Path(sys.argv[1]).resolve()
main = root / "app/src/main/java/com/qwen/tts/android/MainActivity.kt"
gradle = root / "app/build.gradle.kts"
strings = root / "app/src/main/res/values/strings.xml"

src = main.read_text(encoding="utf-8")

# 1) Replace model catalogue with the high-quality 1.7B VoiceDesign Q8 talker
#    and F16 tokenizer/codec. Runtime remains fully on-device after first download.
model_block = r'''private object QwenModel {
    private const val talkerUrl =
        "https://huggingface.co/cstr/qwen3-tts-1.7b-voicedesign-GGUF/resolve/main/qwen3-tts-12hz-1.7b-voicedesign-q8_0.gguf?download=true"
    private const val tokenizerUrl =
        "https://huggingface.co/cstr/qwen3-tts-tokenizer-12hz-GGUF/resolve/main/qwen3-tts-tokenizer-12hz.gguf?download=true"

    val defaultVariant = ModelVariant(
        id = "voice_design_q8",
        label = "VoiceDesign Q8",
        displayName = "VoiceForge · 1.7B VoiceDesign",
        talkerName = "qwen3-tts-12hz-1.7b-voicedesign-q8_0.gguf",
        files = listOf(
            ModelFile(
                name = "qwen3-tts-tokenizer-12hz.gguf",
                url = tokenizerUrl,
                sizeBytes = 350_000_000L,
            ),
            ModelFile(
                name = "qwen3-tts-12hz-1.7b-voicedesign-q8_0.gguf",
                url = talkerUrl,
                sizeBytes = 1_900_000_000L,
            ),
        ),
    )

    val variants = listOf(defaultVariant)
    val obsoleteFileNames = emptySet<String>()

    fun variantById(id: String): ModelVariant =
        variants.firstOrNull { it.id == id } ?: defaultVariant
}'''
src, n = re.subn(
    r'private object QwenModel \{.*?\n\}\n\ndata class QwenTtsUiState',
    model_block + "\n\ndata class QwenTtsUiState",
    src,
    flags=re.S,
)
assert n == 1, f"QwenModel replacement count={n}"

# 2) Make the default experience a Chinese voice-design workbench.
src = src.replace(
    'val text: String = "Hello World from Qwen3 TTS running on Android on-device!",',
    'val text: String = "窗外的风吹过树梢，阳光落在桌角。冰块轻轻碰了一下杯壁，声音很清楚。",\n'
    '    val instruction: String = "20多岁的年轻女性。声音清澈、温柔、自然，不甜腻、不夹、不像播音或配音演员。带轻微真实呼吸感和自然停顿，年轻但有知性和层次，情绪细腻，声音柔和而不虚弱，清晰、有生命力。",',
)
src = src.replace('val selectedLanguageId: Int = 2050,', 'val selectedLanguageId: Int = 2055,')

needle = '''    fun updateText(value: String) {
        _uiState.update { it.copy(text = value) }
    }
'''
insert = needle + '''
    fun updateInstruction(value: String) {
        _uiState.update { it.copy(instruction = value) }
    }
'''
assert needle in src, "updateText block not found"
src = src.replace(needle, insert, 1)

# 3) Pass the actual natural-language voice description into native VoiceDesign.
old = 'params = QwenEngine.NativeParams(languageId = _uiState.value.selectedLanguageId),'
new = '''params = QwenEngine.NativeParams(
                                languageId = _uiState.value.selectedLanguageId,
                                instruction = _uiState.value.instruction.trim(),
                                maxAudioTokens = 768,
                            ),'''
assert old in src, "synthesize NativeParams call not found"
src = src.replace(old, new, 1)

# 4) Simplify navigation for this model: design, results, model. Hide the incompatible
#    microphone-cloning screen rather than pretending VoiceDesign can extract a speaker.
src = src.replace(
    'object Studio : AppDestination("studio", "Studio",',
    'object Studio : AppDestination("studio", "捏声音",',
)
src = src.replace(
    'object History : AppDestination("history", "History",',
    'object History : AppDestination("history", "作品",',
)
src = src.replace(
    'object Settings : AppDestination("settings", "Settings",',
    'object Settings : AppDestination("settings", "模型",',
)
src = src.replace(
    '''private val appDestinations = listOf(
    AppDestination.Studio,
    AppDestination.Voices,
    AppDestination.History,
    AppDestination.Settings,
)''',
    '''private val appDestinations = listOf(
    AppDestination.Studio,
    AppDestination.History,
    AppDestination.Settings,
)''',
)
src = src.replace(
    '''            composable(AppDestination.Voices.route) {
                VoicesScreen(viewModel)
            }
''',
    '',
)

# 5) Add voice-description editing to the composer, keep text + language controls.
src = src.replace(
    '''                onTextChange = viewModel::updateText,
                onVoicePickerClick = { if (!state.busy) showVoicePicker = true },
                onLanguageChange = viewModel::updateLanguage,''',
    '''                onTextChange = viewModel::updateText,
                onInstructionChange = viewModel::updateInstruction,
                onVoicePickerClick = { if (!state.busy) showVoicePicker = true },
                onLanguageChange = viewModel::updateLanguage,''',
)
src = src.replace(
    'Text("Text to speech", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)',
    'Text("捏一个你喜欢的声音", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)',
)
src = src.replace(
    '''    onTextChange: (String) -> Unit,
    onVoicePickerClick: () -> Unit,
    onLanguageChange: (Int) -> Unit,''',
    '''    onTextChange: (String) -> Unit,
    onInstructionChange: (String) -> Unit,
    onVoicePickerClick: () -> Unit,
    onLanguageChange: (Int) -> Unit,''',
    1,
)
composer_anchor = '''        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = state.text,
'''
composer_replacement = '''        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = state.instruction,
                onValueChange = onInstructionChange,
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                maxLines = 8,
                label = { Text("声音描述") },
                supportingText = { Text("直接写你想要的年龄感、清澈度、温柔度、情绪、语速和气质") },
            )
            OutlinedTextField(
                value = state.text,
'''
assert composer_anchor in src, "ComposerPanel anchor not found"
src = src.replace(composer_anchor, composer_replacement, 1)
src = src.replace('label = { Text("Text") },', 'label = { Text("试听内容") },', 1)

# VoiceDesign has no speaker encoder; remove the misleading picker from the composer.
voice_button = re.compile(r'''\n            OutlinedButton\(
                onClick = onVoicePickerClick,
                enabled = !state\.busy,
                modifier = Modifier\.fillMaxWidth\(\),
            \) \{
                Icon\(Icons\.Default\.RecordVoiceOver, contentDescription = null, modifier = Modifier\.size\(18\.dp\)\)
                Spacer\(Modifier\.width\(8\.dp\)\)
                Column\(Modifier\.weight\(1f\), horizontalAlignment = Alignment\.Start\) \{
                    Text\("Voice", style = MaterialTheme\.typography\.labelMedium\)
                    Text\(selectedVoiceName, maxLines = 1, overflow = TextOverflow\.Ellipsis\)
                \}
            \}''')
src, removed = voice_button.subn('', src, count=1)
assert removed == 1, f"voice picker removal count={removed}"

src = src.replace(
    'Text(if (state.busy && !state.downloading) "Generating..." else "Generate")',
    'Text(if (state.busy && !state.downloading) "正在生成…" else "生成并试听")',
)
src = src.replace('label = { Text("Language") },', 'label = { Text("语言") },', 1)
src = src.replace('LanguageOption("Chinese", 2055)', 'LanguageOption("中文", 2055)')
src = src.replace('LanguageOption("English", 2050)', 'LanguageOption("English", 2050)')

# 6) Model screen wording and first-launch guidance.
src = src.replace('"Model ready"', '"模型已就绪"')
src = src.replace('"Model not downloaded"', '"还没下载本地模型"')
src = src.replace('"Download model"', '"下载本地模型"')
src = src.replace('"Loading native model"', '"正在加载本地模型"')
src = src.replace('"Model loaded"', '"模型已加载"')
src = src.replace('"Performance"', '"运行设置"')
src = src.replace('"CPU backend"', '"手机本地 CPU · 断网可运行"')
src = src.replace('"Ready"', '"已下载"')
src = src.replace('"Missing"', '"未下载"')

main.write_text(src, encoding="utf-8")

# Android NDK 28 uses the C++ AttachCurrentThread(JNIEnv**, ...) overload.
# Upstream's desktop-compatible void** form does not compile for Android.
jni = root / "external/qwen3-tts.cpp/src/qwen3_tts_jni.cpp"
jni_src = jni.read_text(encoding="utf-8")
jni_old = """                void* attached_env = nullptr;
                if (state->vm->AttachCurrentThread(&attached_env, nullptr) != JNI_OK) {
                    return;
                }
                env = static_cast<JNIEnv*>(attached_env);"""
jni_new = """                JNIEnv* attached_env = nullptr;
                if (state->vm->AttachCurrentThread(&attached_env, nullptr) != JNI_OK) {
                    return;
                }
                env = attached_env;"""
assert jni_old in jni_src, "Android JNI AttachCurrentThread source pattern not found"
jni_src = jni_src.replace(jni_old, jni_new, 1)
jni.write_text(jni_src, encoding="utf-8")

# Rename the installed app/package without touching upstream source outside the generated worktree.
g = gradle.read_text(encoding="utf-8")
g = g.replace('namespace = "com.qwen.tts.android"', 'namespace = "com.qwen.tts.android"')
g = g.replace('applicationId = "com.qwen.tts.android"', 'applicationId = "com.voiceforge.offline"')
g = g.replace('versionName = "0.1.0"', 'versionName = "0.1.0-voiceforge"')
gradle.write_text(g, encoding="utf-8")

s = strings.read_text(encoding="utf-8")
s = s.replace('Qwen3 TTS', '声匠 · VoiceForge')
strings.write_text(s, encoding="utf-8")

# Structural gate: fail the CI before Gradle if a required patch silently stopped matching upstream.
final = main.read_text(encoding="utf-8")
required = [
    'val instruction: String',
    'instruction = _uiState.value.instruction.trim()',
    'qwen3-tts-12hz-1.7b-voicedesign-q8_0.gguf',
    'qwen3-tts-tokenizer-12hz.gguf',
    'Text("声音描述")',
    '"生成并试听"',
]
missing = [x for x in required if x not in final]
if missing:
    raise SystemExit(f"VoiceForge patch incomplete: {missing}")
if 'AppDestination.Voices,' in final:
    raise SystemExit("Incompatible Voice cloning destination still visible")

print("VoiceForge patch applied: VoiceDesign model + instruction UI + local-only runtime UI")
