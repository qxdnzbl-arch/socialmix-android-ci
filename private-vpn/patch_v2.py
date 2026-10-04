#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else "v2rayng-src")

app_gradle = root / "V2rayNG/app/build.gradle.kts"
s = app_gradle.read_text()
s = s.replace('applicationId = "com.v2ray.ang"', 'applicationId = "com.nzbl.privatevpn"')
s = s.replace('"v2rayNG_\${variant.versionName}-fdroid_\${abi}.apk"', '"PrivateVPN_\${variant.versionName}-fdroid_\${abi}.apk"')
s = s.replace('"v2rayNG_\${variant.versionName}_\${abi}.apk"', '"PrivateVPN_\${variant.versionName}_\${abi}.apk"')
app_gradle.write_text(s)

for rel in [
    "V2rayNG/app/src/main/res/values/strings.xml",
    "V2rayNG/app/src/fdroid/res/values/strings.xml",
    "V2rayNG/app/src/dev/res/values/strings.xml",
    "V2rayNG/app/src/pre_release/res/values/strings.xml",
]:
    p = root / rel
    t = p.read_text()
    t = t.replace("v2rayNG (F-Droid)", "私人VPN")
    t = t.replace("v2rayNG (DEV)", "私人VPN")
    t = t.replace("v2rayNG (PR)", "私人VPN")
    t = t.replace(">v2rayNG<", ">私人VPN<")
    p.write_text(t)

default_strings = root / "V2rayNG/app/src/main/res/values/strings.xml"
t = default_strings.read_text()
anchor = "</resources>"
extra = """
    <string name="connection_auto_selecting">Testing nodes and selecting the fastest…</string>
    <string name="connection_auto_selected">Fastest node selected. Connecting…</string>
    <string name="connection_no_available_node">No working node was found.</string>
"""
if "connection_auto_selecting" not in t:
    t = t.replace(anchor, extra + anchor)
default_strings.write_text(t)

zh_strings = root / "V2rayNG/app/src/main/res/values-zh-rCN/strings.xml"
z = zh_strings.read_text()
zextra = """
    <string name="connection_auto_selecting">正在测速并自动选择最快节点…</string>
    <string name="connection_auto_selected">已选择当前最快节点，正在连接…</string>
    <string name="connection_no_available_node">没有检测到可用节点</string>
"""
if "connection_auto_selecting" not in z:
    z = z.replace(anchor, zextra + anchor)
zh_strings.write_text(z)

icon = root / "V2rayNG/app/src/main/res/drawable/ic_private_vpn.xml"
icon.parent.mkdir(parents=True, exist_ok=True)
icon.write_text("""<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:fillColor="#FFFFFF"
        android:pathData="M54,8 L91,22 L86,60 C82,79 69,92 54,100 C39,92 26,79 22,60 L17,22 Z"/>
    <path android:fillColor="#111111"
        android:pathData="M54,27 C44,27 36,35 36,45 C36,52 40,58 46,61 L46,77 L62,77 L62,61 C68,58 72,52 72,45 C72,35 64,27 54,27 Z M54,37 C59,37 62,40 62,45 C62,50 59,53 54,53 C49,53 46,50 46,45 C46,40 49,37 54,37 Z"/>
</vector>
""")

manifest = root / "V2rayNG/app/src/main/AndroidManifest.xml"
m = manifest.read_text()
m = m.replace('android:icon="@mipmap/ic_launcher"', 'android:icon="@drawable/ic_private_vpn"')
manifest.write_text(m)

asset = root / "V2rayNG/app/src/main/assets/nzbl_nodes.txt"
asset.write_text(
    "vless://00000000-0000-4000-8000-000000000001@render-placeholder.invalid:443?encryption=none&security=tls&sni=render-placeholder.invalid&type=ws&host=render-placeholder.invalid&path=%2Frender-placeholder#NZBL-AUTO-Render-SG\n"
    "vless://00000000-0000-4000-8000-000000000002@railway-placeholder.invalid:443?encryption=none&security=tls&sni=railway-placeholder.invalid&type=ws&host=railway-placeholder.invalid&path=%2Frailway-placeholder#NZBL-AUTO-Railway-SG\n"
)

app = root / "V2rayNG/app/src/main/java/com/v2ray/ang/AngApplication.kt"
a = app.read_text()
a = a.replace(
    "import com.v2ray.ang.AppConfig.ANG_PACKAGE\nimport com.v2ray.ang.handler.SettingsManager",
    "import com.v2ray.ang.AppConfig.ANG_PACKAGE\nimport com.v2ray.ang.AppConfig.DEFAULT_SUBSCRIPTION_ID\nimport com.v2ray.ang.handler.AngConfigManager\nimport com.v2ray.ang.handler.MmkvManager\nimport com.v2ray.ang.handler.SettingsManager"
)
anchor_app = """        SettingsManager.initApp(this)
        SettingsManager.setNightMode()
"""
insert_app = """        SettingsManager.initApp(this)
        SettingsManager.setNightMode()

        val nzblBundleVersion = "2"
        if (MmkvManager.decodeSettingsString("nzbl_bundle_version", "") != nzblBundleVersion) {
            MmkvManager.decodeAllServerList().toList().forEach { guid ->
                val remarks = MmkvManager.decodeServerConfig(guid)?.remarks.orEmpty()
                if (remarks.startsWith("NZBL-") || remarks.startsWith("AUTO-")) {
                    MmkvManager.removeServer(guid)
                }
            }

            runCatching {
                assets.open("nzbl_nodes.txt").bufferedReader().useLines { lines ->
                    lines.map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .forEach { node ->
                            AngConfigManager.importBatchConfig(
                                node,
                                DEFAULT_SUBSCRIPTION_ID,
                                true
                            )
                        }
                }
                MmkvManager.encodeSettings("nzbl_bundle_version", nzblBundleVersion)
            }
        }
"""
if anchor_app not in a:
    raise SystemExit("AngApplication anchor not found")
a = a.replace(anchor_app, insert_app)
app.write_text(a)

vm = root / "V2rayNG/app/src/main/java/com/v2ray/ang/viewmodel/MainViewModel.kt"
v = vm.read_text()
v = v.replace(
    "val updateTestResultAction by lazy { MutableLiveData<String>() }",
    "val updateTestResultAction by lazy { MutableLiveData<String>() }\n    val autoSelectFinishedAction by lazy { MutableLiveData<String>() }\n    private var autoSelectAfterTest = false"
)
v = v.replace(
    "fun testAllRealPing() {",
    "fun testAllRealPing(autoSelect: Boolean = false) {\n        if (autoSelect) autoSelectAfterTest = true"
)
anchor_on = """    fun onTestsFinished() {
        viewModelScope.launch(Dispatchers.Default) {
            if (MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_REMOVE_INVALID_AFTER_TEST)) {
                removeInvalidServer()
            }

            if (MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_SORT_AFTER_TEST)) {
                sortByTestResults()
            }

            withContext(Dispatchers.Main) {
                reloadServerList()
            }
        }
    }
"""
replace_on = """    fun onTestsFinished() {
        viewModelScope.launch(Dispatchers.Default) {
            if (MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_REMOVE_INVALID_AFTER_TEST)) {
                removeInvalidServer()
            }

            if (MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_SORT_AFTER_TEST)) {
                sortByTestResults()
            }

            val wasAutoSelect = autoSelectAfterTest
            val selectedGuid = if (wasAutoSelect) {
                serversCache.mapNotNull { item ->
                    val delay = MmkvManager.decodeServerAffiliationInfo(item.guid)?.testDelayMillis ?: -1L
                    if (delay > 0L) item.guid to delay else null
                }.minByOrNull { it.second }?.first?.also { MmkvManager.setSelectServer(it) }
            } else {
                null
            }
            autoSelectAfterTest = false

            withContext(Dispatchers.Main) {
                reloadServerList()
                if (wasAutoSelect) {
                    autoSelectFinishedAction.value = selectedGuid.orEmpty()
                }
            }
        }
    }
"""
if anchor_on not in v:
    raise SystemExit("MainViewModel onTestsFinished anchor not found")
v = v.replace(anchor_on, replace_on)
vm.write_text(v)

main = root / "V2rayNG/app/src/main/java/com/v2ray/ang/ui/MainActivity.kt"
q = main.read_text()
q = q.replace(
    "private var tabMediator: TabLayoutMediator? = null",
    "private var tabMediator: TabLayoutMediator? = null\n    private var pendingConnectAfterAutoSelect = false"
)
q = q.replace(
"""        mainViewModel.isRunning.observe(this) { isRunning ->
            applyRunningState(false, isRunning)
        }
        mainViewModel.startListenBroadcast()""",
"""        mainViewModel.isRunning.observe(this) { isRunning ->
            applyRunningState(false, isRunning)
        }
        mainViewModel.autoSelectFinishedAction.observe(this) { selectedGuid ->
            if (!pendingConnectAfterAutoSelect) return@observe
            pendingConnectAfterAutoSelect = false
            binding.fab.isEnabled = true
            if (selectedGuid.isNullOrBlank()) {
                applyRunningState(false, false)
                toastError(R.string.connection_no_available_node)
            } else {
                setTestState(getString(R.string.connection_auto_selected))
                prepareAndStartV2Ray()
            }
        }
        mainViewModel.startListenBroadcast()"""
)

old_handle = """    private fun handleFabAction() {
        applyRunningState(isLoading = true, isRunning = false)

        if (mainViewModel.isRunning.value == true) {
            CoreServiceManager.stopVService(this)
        } else if (SettingsManager.isVpnMode()) {
            val intent = VpnService.prepare(this)
            if (intent == null) {
                startV2Ray()
            } else {
                requestVpnPermission.launch(intent)
            }
        } else {
            startV2Ray()
        }
    }
"""
new_handle = """    private fun handleFabAction() {
        applyRunningState(isLoading = true, isRunning = false)

        if (mainViewModel.isRunning.value == true) {
            CoreServiceManager.stopVService(this)
            return
        }

        if (mainViewModel.serversCache.size > 1) {
            pendingConnectAfterAutoSelect = true
            binding.fab.isEnabled = false
            setTestState(getString(R.string.connection_auto_selecting))
            mainViewModel.testAllRealPing(autoSelect = true)
        } else {
            prepareAndStartV2Ray()
        }
    }

    private fun prepareAndStartV2Ray() {
        if (SettingsManager.isVpnMode()) {
            val intent = VpnService.prepare(this)
            if (intent == null) {
                startV2Ray()
            } else {
                requestVpnPermission.launch(intent)
            }
        } else {
            startV2Ray()
        }
    }
"""
if old_handle not in q:
    raise SystemExit("MainActivity handleFabAction anchor not found")
q = q.replace(old_handle, new_handle)
main.write_text(q)

print("NZBL multi-node auto-select patch applied")
