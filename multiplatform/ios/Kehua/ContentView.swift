import SwiftUI
import WebKit

private let kehuaURL = URL(string: "https://kehua-revival-live.onrender.com/")!

struct ContentView: View {
    @State private var offline = false

    var body: some View {
        ZStack {
            Color(red: 244/255, green: 243/255, blue: 248/255)
                .ignoresSafeArea()
            KehuaWebView(offline: $offline)
                .ignoresSafeArea(.container, edges: .bottom)
            if offline {
                VStack(spacing: 14) {
                    Text("暂时连不上可话")
                        .font(.title3.weight(.semibold))
                    Text("账号和数据都还在。网络恢复后重新连接即可。")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                    Button("重新连接") {
                        NotificationCenter.default.post(name: .kehuaRetry, object: nil)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(Color(red: 35/255, green: 35/255, blue: 40/255))
                }
                .padding(28)
                .frame(maxWidth: 380)
                .background(.white)
                .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
                .shadow(color: .black.opacity(0.08), radius: 30, y: 12)
                .padding(24)
            }
        }
    }
}

extension Notification.Name {
    static let kehuaRetry = Notification.Name("KehuaRetry")
}

struct KehuaWebView: UIViewRepresentable {
    @Binding var offline: Bool

    func makeCoordinator() -> Coordinator { Coordinator(offline: $offline) }

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.websiteDataStore = .default()
        config.defaultWebpagePreferences.allowsContentJavaScript = true
        config.preferences.javaScriptCanOpenWindowsAutomatically = false

        let webView = WKWebView(frame: .zero, configuration: config)
        webView.navigationDelegate = context.coordinator
        webView.uiDelegate = context.coordinator
        webView.allowsBackForwardNavigationGestures = true
        webView.scrollView.contentInsetAdjustmentBehavior = .automatic
        webView.isOpaque = false
        webView.backgroundColor = UIColor(red: 244/255, green: 243/255, blue: 248/255, alpha: 1)
        context.coordinator.webView = webView
        context.coordinator.retryObserver = NotificationCenter.default.addObserver(forName: .kehuaRetry, object: nil, queue: .main) { [weak webView] _ in
            webView?.load(URLRequest(url: kehuaURL, cachePolicy: .reloadRevalidatingCacheData, timeoutInterval: 30))
        }
        webView.load(URLRequest(url: kehuaURL, cachePolicy: .useProtocolCachePolicy, timeoutInterval: 30))
        return webView
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {}

    static func dismantleUIView(_ uiView: WKWebView, coordinator: Coordinator) {
        if let token = coordinator.retryObserver { NotificationCenter.default.removeObserver(token) }
        uiView.stopLoading()
    }

    final class Coordinator: NSObject, WKNavigationDelegate, WKUIDelegate {
        @Binding var offline: Bool
        weak var webView: WKWebView?
        var retryObserver: NSObjectProtocol?

        init(offline: Binding<Bool>) { _offline = offline }

        private func isInternal(_ url: URL) -> Bool {
            guard url.scheme == "https" else { return false }
            return url.host == "kehua-revival-live.onrender.com" || url.host == "nvwdtfnhsyfdopaxdylx.supabase.co"
        }

        func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction, decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
            guard let url = navigationAction.request.url else { decisionHandler(.cancel); return }
            if isInternal(url) || navigationAction.navigationType == .other {
                decisionHandler(.allow)
            } else {
                UIApplication.shared.open(url)
                decisionHandler(.cancel)
            }
        }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            offline = false
        }

        func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
            offline = true
        }

        func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
            offline = true
        }

        func webView(_ webView: WKWebView, createWebViewWith configuration: WKWebViewConfiguration, for navigationAction: WKNavigationAction, windowFeatures: WKWindowFeatures) -> WKWebView? {
            if let url = navigationAction.request.url {
                if isInternal(url) { webView.load(URLRequest(url: url)) }
                else { UIApplication.shared.open(url) }
            }
            return nil
        }
    }
}
