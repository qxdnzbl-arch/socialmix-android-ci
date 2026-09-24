from pathlib import Path
import re

api_path = Path('app/src/main/java/com/suisuinian/app/KehuaApi.kt')
gradle_path = Path('app/build.gradle')

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

gradle = gradle_path.read_text(encoding='utf-8')
gradle = re.sub(r"\n\s*implementation platform\('com\.google\.firebase:firebase-bom:[^']+'\)\n\s*implementation 'com\.google\.firebase:firebase-auth'\n", "\n", gradle)
gradle_path.write_text(gradle, encoding='utf-8')

# Hard fail if release prep left Firebase code behind.
remaining = [token for token in ('com.google.firebase', 'FirebaseAuth', 'FirebaseApp', 'FirebaseOptions', 'awaitTask') if token in text]
if remaining:
    raise SystemExit('Firebase remnants: ' + ', '.join(remaining))
print('Kehua release auth prepared: direct Supabase Auth')
