from pathlib import Path

root = Path(__file__).resolve().parents[1]
main = root / "app/src/main/java/com/qwen/tts/android/MainActivity.kt"
gradle = root / "app/build.gradle.kts"

def must_replace(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise RuntimeError(f"Patch anchor not found: {label}")
    return text.replace(old, new, 1)

text = main.read_text(encoding="utf-8")

text = must_replace(
    text,
    '''    val defaultVariant: ModelVariant = variant(
        id = "q4_k_m",
        label = "Q4_K_M",
        displayName = "Qwen3-TTS 0.6B Q4_K_M",
        talkerName = "qwen-talker-0.6b-base-Q4_K_M.gguf",
        tokenizerName = "qwen-tokenizer-12hz-Q4_K_M.gguf",
        talkerSizeBytes = 628_905_056L,
        tokenizerSizeBytes = 254_974_752L,
    )''',
    '''    val defaultVariant: ModelVariant = variant(
        id = "voice_design_q4_k_m",
        label = "VoiceDesign Q4_K_M",
        displayName = "Qwen3-TTS 1.7B VoiceDesign",
        talkerName = "qwen-talker-1.7b-voicedesign-Q4_K_M.gguf",
        tokenizerName = "qwen-tokenizer-12hz-Q4_K_M.gguf",
        talkerSizeBytes = 1_182_630_816L,
        tokenizerSizeBytes = 254_974_752L,
    )''',
    "voice-design model",
)

text = must_replace(
    text,
    '    val text: String = "Hello World from Qwen3 TTS running on Android on-device!",\n',
    '''    val text: String = "今天天气很好，我想出去走一走。风吹过来的时候，忽然觉得整个世界都安静了一点。",
    val instruction: String = "年轻女性声音，清澈、温柔、自然、知性。声音轻盈细腻，有真实的人类呼吸感和细微颗粒感，情绪克制但有层次，不平淡。说话像自然聊天和安静讲故事，带一点脆弱感和电影感。不要甜腻，不要幼态，不要播音腔，不要成熟厚重，不要戏剧腔，不要夸张表演，不要机械感。",
''',
    "default text and voice instruction",
)

text = must_replace(
    text,
    "    val selectedLanguageId: Int = 2050,\n",
    "    val selectedLanguageId: Int = 2055,\n",
    "default Chinese language",
)

text = must_replace(
    text,
    '''    fun updateText(value: String) {
        _uiState.update { it.copy(text = value) }
    }

''',
    '''    fun updateText(value: String) {
        _uiState.update { it.copy(text = value) }
    }

    fun updateInstruction(value: String) {
        _uiState.update { it.copy(instruction = value) }
    }

''',
    "instruction updater",
)

text = must_replace(
    text,
    '''                    native.synthesize(
                            text = text,
                            speakerEmbeddingPath = selectedVoice?.speakerEmbeddingPath,
                            params = QwenEngine.NativeParams(languageId = _uiState.value.selectedLanguageId),
                        )''',
    '''                    native.synthesize(
                            text = text,
                            speakerEmbeddingPath = null,
                            params = QwenEngine.NativeParams(
                                languageId = _uiState.value.selectedLanguageId,
                                instruction = _uiState.value.instruction.trim().ifBlank { null },
                            ),
                        )''',
    "voice-design synthesis parameters",
)

text = must_replace(
    text,
    '''                    val voiceName = selectedVoice?.name ?: "Default Voice"
                    persistGeneratedAudio(text, nativeResult.audio, sampleRate, voiceName, selectedVoice?.voiceId, nativeResult.timeMs)''',
    '''                    val voiceName = "Voice Design"
                    persistGeneratedAudio(text, nativeResult.audio, sampleRate, voiceName, null, nativeResult.timeMs)''',
    "voice-design history label",
)

text = must_replace(
    text,
    '''                    refreshModelState("Model ready")
''',
    '''                    refreshModelState("Model ready")
                    loadModel()
''',
    "autoload after download",
)

text = must_replace(
    text,
    '''            Text("Text to speech", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            state.error?.let { error ->
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            ComposerPanel(
''',
    '''            Text("捏声音", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "在手机本地自由描述你想要的声音。模型只需下载一次，之后可离线生成。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.error?.let { error ->
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            if (!state.modelReady || !state.loaded) {
                ModelPanel(
                    state = state,
                    onDownload = viewModel::downloadModel,
                    onLoad = viewModel::loadModel,
                )
            }
            ComposerPanel(
''',
    "studio intro and inline model setup",
)

text = must_replace(
    text,
    '''                onTextChange = viewModel::updateText,
                onVoicePickerClick = { if (!state.busy) showVoicePicker = true },
''',
    '''                onTextChange = viewModel::updateText,
                onInstructionChange = viewModel::updateInstruction,
                onVoicePickerClick = { if (!state.busy) showVoicePicker = true },
''',
    "composer instruction callback",
)

text = must_replace(
    text,
    '''                enabled = state.modelReady && state.text.isNotBlank() && !state.busy,
''',
    '''                enabled = state.modelReady && state.loaded && state.text.isNotBlank() && state.instruction.isNotBlank() && !state.busy,
''',
    "generation gate",
)

text = must_replace(
    text,
    '''                Text(if (state.busy && !state.downloading) "Generating..." else "Generate")
''',
    '''                Text(if (state.busy && !state.downloading) "正在生成..." else "生成声音")
''',
    "generate button copy",
)

text = must_replace(
    text,
    '''private fun ComposerPanel(
    state: QwenTtsUiState,
    selectedVoiceName: String,
    onTextChange: (String) -> Unit,
    onVoicePickerClick: () -> Unit,
    onLanguageChange: (Int) -> Unit,
) {''',
    '''private fun ComposerPanel(
    state: QwenTtsUiState,
    selectedVoiceName: String,
    onTextChange: (String) -> Unit,
    onInstructionChange: (String) -> Unit,
    onVoicePickerClick: () -> Unit,
    onLanguageChange: (Int) -> Unit,
) {''',
    "composer signature",
)

text = must_replace(
    text,
    '''                label = { Text("Text") },
            )
            OutlinedButton(
                onClick = onVoicePickerClick,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                    Text("Voice", style = MaterialTheme.typography.labelMedium)
                    Text(selectedVoiceName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
''',
    '''                label = { Text("要说的话") },
            )
            OutlinedTextField(
                value = state.instruction,
                onValueChange = onInstructionChange,
                modifier = Modifier.fillMaxWidth(),
                minLines = 5,
                maxLines = 10,
                enabled = !state.busy,
                label = { Text("声音描述（你想怎么捏就怎么写）") },
                supportingText = { Text("例如：年轻、温柔、自然、知性、有呼吸感、有情绪层次。") },
            )
''',
    "voice instruction editor",
)

text = must_replace(
    text,
    '''                    Text(selectedVariant.displayName, style = MaterialTheme.typography.titleMedium)
''',
    '''                    Text(selectedVariant.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "约 1.44 GB，首次下载后可离线、无次数限制使用。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
''',
    "model explanation",
)

text = text.replace('"Download model"', '"下载模型"')
text = text.replace('"Model ready"', '"模型已下载"')
text = text.replace('"Loaded"', '"已加载"')
text = text.replace('"Load"', '"加载"')

main.write_text(text, encoding="utf-8")

g = gradle.read_text(encoding="utf-8")
g = must_replace(g, '        versionCode = 1\n        versionName = "0.1.0"\n',
                 '        versionCode = 2\n        versionName = "0.2.0-voicedesign"\n',
                 "app version")
gradle.write_text(g, encoding="utf-8")

print("VoiceDesign Android patch applied successfully")
