from pathlib import Path
import re

api_path = Path('app/src/main/java/com/suisuinian/app/KehuaApi.kt')
ui_path = Path('app/src/main/java/com/suisuinian/app/KehuaActivity.kt')
gradle_path = Path('app/build.gradle')

# --- Production auth: direct Supabase Auth, no Firebase bridge. ---
text = api_path.read_text(encoding='utf-8')
for line in [
    'import com.google.android.gms.tasks.Task\n',
    'import com.google.firebase.FirebaseApp\n',
    'import com.google.firebase.FirebaseOptions\n',
    'import com.google.firebase.auth.FirebaseAuth\n',
    'import com.google.firebase.auth.FirebaseAuthException\n',
    'import kotlinx.coroutines.suspendCancellableCoroutine\n',
    'import kotlin.coroutines.resume\n',
    'import kotlin.coroutines.resumeWithException\n',
]:
    text = text.replace(line, '')
text = text.replace('    @Volatile private var firebase: FirebaseAuth? = null\n\n', '')

auth_block = '''    fun isFirebaseConfigured(): Boolean = true

    fun userId(): String = prefs.getString("user_id", "") ?: ""
    private fun accessToken(): String = prefs.getString("access_token", "") ?: ""
    private fun refreshToken(): String = prefs.getString("refresh_token", "") ?: ""

    suspend fun restoreSession(): Boolean = ensureSession()

    suspend fun signUp(email: String, password: String): KehuaAuthResult = withContext(Dispatchers.IO) {
        if (!validEmail(email) || password.length !in 6..72) {
            return@withContext KehuaAuthResult(false, message = "请填写有效邮箱，密码至少 6 位")
        }
        try {
            val normalized = email.trim().lowercase()
            val result = callSupabaseAuth(
                "/auth/v1/signup",
                JSONObject().put("email", normalized).put("password", password)
            )
            if (result.optString("access_token").isNotBlank()) {
                saveSession(result)
                KehuaAuthResult(true)
            } else {
                KehuaAuthResult(true, needsVerification = true, message = "确认邮件已经发送。验证邮箱后回来登录。")
            }
        } catch (e: Exception) {
            KehuaAuthResult(false, message = authError(e))
        }
    }

    suspend fun signIn(email: String, password: String): KehuaAuthResult = withContext(Dispatchers.IO) {
        if (!validEmail(email) || password.length !in 6..72) {
            return@withContext KehuaAuthResult(false, message = "请填写有效邮箱，密码至少 6 位")
        }
        try {
            val result = callSupabaseAuth(
                "/auth/v1/token?grant_type=password",
                JSONObject().put("email", email.trim().lowercase()).put("password", password)
            )
            if (result.optString("access_token").isBlank()) throw IllegalStateException("邮箱或密码不对")
            saveSession(result)
            KehuaAuthResult(true)
        } catch (e: Exception) {
            KehuaAuthResult(false, message = authError(e))
        }
    }

    suspend fun sendPasswordReset(email: String): String? = withContext(Dispatchers.IO) {
        if (!validEmail(email)) return@withContext "先填写你注册时使用的邮箱"
        try {
            callSupabaseAuth("/auth/v1/recover", JSONObject().put("email", email.trim().lowercase()))
            null
        } catch (e: Exception) {
            authError(e)
        }
    }

    fun logout() {
        prefs.edit().clear().apply()
    }

    suspend fun ensureProfile(): KehuaProfile'''

text, count = re.subn(
    r'    fun isFirebaseConfigured\(\): Boolean =.*?    suspend fun ensureProfile\(\): KehuaProfile',
    auth_block,
    text,
    flags=re.S,
)
if count != 1:
    raise SystemExit(f'auth block replacement count={count}')

text, count = re.subn(
    r'    private fun firebaseAuth\(\): FirebaseAuth \{.*?    private fun callSupabaseAuth',
    '    private fun callSupabaseAuth',
    text,
    flags=re.S,
)
if count != 1:
    raise SystemExit(f'firebase helper removal count={count}')

tail_marker = '    private fun authError(e: Exception): String {'
if tail_marker not in text:
    raise SystemExit('authError marker missing')
text = text.split(tail_marker, 1)[0] + '''    private fun authError(e: Exception): String {
        val raw = e.message.orEmpty().lowercase()
        return when {
            raw.contains("invalid login credentials") || raw.contains("invalid_credentials") || raw.contains("email or password") -> "邮箱或密码不对"
            raw.contains("already registered") || raw.contains("user already registered") -> "这个邮箱已经有账号了，直接登录即可"
            raw.contains("email not confirmed") -> "这个邮箱还没完成验证，请先打开确认邮件"
            raw.contains("rate") || raw.contains("too many") -> "操作太频繁了，请稍后再试"
            raw.contains("network") || raw.contains("timeout") || raw.contains("failed to connect") -> "网络连接失败，请稍后再试"
            else -> e.message?.takeIf { it.isNotBlank() } ?: "暂时无法完成，请稍后再试"
        }
    }
}
'''
api_path.write_text(text, encoding='utf-8')

# --- Original Kehua visual/interaction baseline. ---
ui = ui_path.read_text(encoding='utf-8')

literal_replacements = [
    ('private val KehuaBg = Color(0xFFF7F7F7)', 'private val KehuaBg = Color(0xFFF4F4F7)'),
    ('private val KehuaInk = Color(0xFF29292D)', 'private val KehuaInk = Color(0xFF242428)'),
    ('private val KehuaSub = Color(0xFF8A8A91)', 'private val KehuaSub = Color(0xFF8D8D93)'),
    ('private val KehuaLine = Color(0xFFECECF0)', 'private val KehuaLine = Color(0xFFE7E7EB)'),
    ('private val KehuaPink = Color(0xFFFF4169)', 'private val KehuaPink = Color(0xFFFF3E68)'),
    ('Text("可话·重生", color = KehuaInk, fontSize = 31.sp, fontWeight = FontWeight.SemiBold)', 'Text("说想说的话", color = KehuaInk, fontSize = 31.sp, fontWeight = FontWeight.SemiBold)'),
    ('Text("说想说的话，找到真正的共鸣。", color = KehuaSub, fontSize = 14.sp)', 'Text("记录你真实的想法和感受", color = KehuaSub, fontSize = 14.sp)'),
    ('NavigationBar(containerColor = KehuaSurface, tonalElevation = 0.dp, modifier = Modifier.navigationBarsPadding())', 'NavigationBar(containerColor = Color(0xFFFAFAFC), tonalElevation = 0.dp, modifier = Modifier.height(74.dp).navigationBarsPadding())'),
    ('Column(Modifier.fillMaxWidth().background(KehuaSurface).padding(horizontal = 20.dp, vertical = 22.dp)) {\n                Text(todayLabel(), color = KehuaSub, fontSize = 12.sp)\n                Spacer(Modifier.height(8.dp))\n                Text("此刻，说你想说的话～", color = KehuaInk, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)\n                Spacer(Modifier.height(24.dp))\n                Column(Modifier.fillMaxWidth().background(KehuaSurface)) {', 'Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp)) {\n                Text(todayLabel(), color = Color(0xFF9A9AA0), fontSize = 13.sp)\n                Spacer(Modifier.height(20.dp))\n                Text("此刻，\\n有什么想说～", color = KehuaInk, fontSize = 24.sp, lineHeight = 31.sp, fontWeight = FontWeight.SemiBold)\n                Spacer(Modifier.height(26.dp))\n                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(KehuaSurface).padding(18.dp)) {'),
    ('textStyle = TextStyle(color = KehuaInk, fontSize = 17.sp, lineHeight = 26.sp)', 'textStyle = TextStyle(color = KehuaInk, fontSize = 16.sp, lineHeight = 27.sp)'),
    ('modifier = Modifier.fillMaxWidth().height(126.dp)', 'modifier = Modifier.fillMaxWidth().height(112.dp)'),
    ('Text("写下你真实的想法和感受", color = KehuaSub, fontSize = 17.sp)', 'Text("写下你真实的想法和感受", color = KehuaSub, fontSize = 16.sp)'),
    ('Column(Modifier.fillMaxWidth().background(KehuaSurface).padding(horizontal = 20.dp, vertical = 18.dp)) {\n        Text(post.body, color = KehuaInk, fontSize = 16.sp, lineHeight = 25.sp)', 'Column(Modifier.padding(horizontal = 24.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(KehuaSurface).padding(18.dp)) {\n        Text(post.body, color = KehuaInk, fontSize = 15.sp, lineHeight = 26.sp)'),
    ('Text("共鸣已到达。请签收～", color = KehuaPink', 'Text("共鸣已到达，请签收～", color = KehuaPink'),
    ('Text("消息", color = KehuaInk, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)', 'Text("消息", color = KehuaInk, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)'),
    ('Text(if (status.isBlank()) "还没有消息。\\n从一次共鸣开始。" else status', 'Text(if (status.isBlank()) "还没有消息" else status'),
    ('Text(c.nickname, color = KehuaInk, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)', 'Text(c.nickname, color = KehuaInk, fontSize = 15.sp, fontWeight = FontWeight.Medium)'),
    ('KehuaAvatar(api, c.nickname, c.avatarPath, 48.dp)', 'KehuaAvatar(api, c.nickname, c.avatarPath, 42.dp)'),
    ('Text(shortTime(c.lastAt.orEmpty()), color = KehuaSub, fontSize = 10.sp)', 'Text(shortTime(c.lastAt.orEmpty()), color = Color(0xFFB1B1B7), fontSize = 11.sp)'),
    ('Text("我", color = KehuaInk, fontSize = 25.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.Start))', 'Text("我", color = KehuaInk, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.CenterHorizontally))'),
    ('Modifier.size(76.dp).clip(CircleShape)', 'Modifier.size(68.dp).clip(CircleShape)'),
    ('profile?.avatarPath, 76.dp)', 'profile?.avatarPath, 68.dp)'),
    ('textStyle = TextStyle(color = KehuaSub, fontSize = 13.sp, textAlign = TextAlign.Center)', 'textStyle = TextStyle(color = Color(0xFF77777D), fontSize = 14.sp, textAlign = TextAlign.Center)'),
]
for old, new in literal_replacements:
    ui = ui.replace(old, new)

# Home publish action: original-like 44dp pink circular send control.
publish_pattern = re.compile(r'''\s*Text\(\n\s*if \(busy\) "发布中…" else "发布",\n\s*color = if \(busy \|\| body\.isBlank\(\)\) KehuaSub else KehuaPink,\n\s*fontSize = 15\.sp,\n\s*fontWeight = FontWeight\.SemiBold,\n\s*modifier = Modifier\.clickable\(enabled = !busy && body\.isNotBlank\(\)\) \{\n(?P<body>.*?)\n\s*\}\.padding\(horizontal = 8\.dp, vertical = 10\.dp\)\n\s*\)''', re.S)
m = publish_pattern.search(ui)
if not m:
    raise SystemExit('publish action block missing')
action_body = m.group('body')
publish_replacement = '''\n                        Box(\n                            Modifier.size(44.dp).clip(CircleShape)\n                                .background(if (busy || body.isBlank()) Color(0xFFFFD8E1) else KehuaPink)\n                                .clickable(enabled = !busy && body.isNotBlank()) {\n''' + action_body + '''\n                                },\n                            contentAlignment = Alignment.Center\n                        ) {\n                            Icon(Icons.Outlined.Send, "发布", tint = Color.White, modifier = Modifier.size(20.dp))\n                        }'''
ui = ui[:m.start()] + publish_replacement + ui[m.end():]

# Resonance header: back + centered title + progress.
old_header = '''        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {\n            IconButton(onClick = onClose) { Text("×", fontSize = 28.sp, color = KehuaInk) }\n            Spacer(Modifier.weight(1f))\n            Text(if (rows.isEmpty()) "" else "${index + 1} / ${rows.size}", color = KehuaSub, fontSize = 12.sp)\n            Spacer(Modifier.width(14.dp))\n        }'''
new_header = '''        Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp)) {\n            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterStart)) { Icon(Icons.Outlined.ArrowBackIosNew, "返回", tint = KehuaInk) }\n            Text("共鸣", color = KehuaInk, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.Center))\n            Text(if (rows.isEmpty()) "" else "${index + 1} / ${rows.size}", color = KehuaSub, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp))\n        }'''
if old_header not in ui:
    raise SystemExit('resonance header block missing')
ui = ui.replace(old_header, new_header, 1)
ui = ui.replace('Text(current.body, color = KehuaInk, fontSize = 22.sp, lineHeight = 34.sp, textAlign = TextAlign.Center)', 'Text(current.body, color = KehuaInk, fontSize = 18.sp, lineHeight = 32.sp, textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth())')
ui = ui.replace('modifier = Modifier.weight(1f).height(50.dp)', 'modifier = Modifier.weight(1f).height(44.dp)')
ui = ui.replace('shape = RoundedCornerShape(25.dp)', 'shape = RoundedCornerShape(22.dp)')

# Do not expose rebuild wording in the main visual surface.
ui = ui.replace('Text("这是独立重建版本，不代表原开发团队。", color = KehuaSub, fontSize = 10.sp, modifier = Modifier.fillMaxWidth().padding(20.dp), textAlign = TextAlign.Center)', 'Spacer(Modifier.height(1.dp))')

# Hard visual regression gates for the release candidate.
for required in [
    'private val KehuaBg = Color(0xFFF4F4F7)',
    'private val KehuaPink = Color(0xFFFF3E68)',
    'Text("此刻,',
    'RoundedCornerShape(24.dp)',
    'Text("共鸣", color = KehuaInk, fontSize = 17.sp',
    'Text("消息", color = KehuaInk, fontSize = 17.sp',
]:
    if required not in ui:
        raise SystemExit('visual baseline missing: ' + required)
ui_path.write_text(ui, encoding='utf-8')

# Remove Firebase runtime deps from release build.
gradle = gradle_path.read_text(encoding='utf-8')
gradle = re.sub(r"\n\s*implementation platform\('com\.google\.firebase:firebase-bom:[^']+'\)\n\s*implementation 'com\.google\.firebase:firebase-auth'\n", "\n", gradle)
gradle_path.write_text(gradle, encoding='utf-8')

remaining = [token for token in ('com.google.firebase', 'FirebaseAuth', 'FirebaseApp', 'FirebaseOptions', 'awaitTask') if token in text]
if remaining:
    raise SystemExit('Firebase remnants: ' + ', '.join(remaining))
print('Kehua release prepared: direct Supabase Auth + original visual baseline')