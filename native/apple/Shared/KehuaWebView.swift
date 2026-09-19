import SwiftUI
import WebKit

private let productionBaseURL = URL(string: "https://nvwdtfnhsyfdopaxdylx.supabase.co/functions/v1/kehua-original-web/")!

private func loadKehua(into webView: WKWebView) {
    guard let url = Bundle.main.url(forResource: "kehua", withExtension: "html"),
          let html = try? String(contentsOf: url, encoding: .utf8) else {
        webView.loadHTMLString("<html><body><h2>可话资源加载失败</h2></body></html>", baseURL: productionBaseURL)
        return
    }
    webView.loadHTMLString(html, baseURL: productionBaseURL)
}

final class KehuaNavigationDelegate: NSObject, WKNavigationDelegate {
    func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction, decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
        guard let url = navigationAction.request.url else {
            decisionHandler(.cancel)
            return
        }
        if url.scheme == "about" || url.host == nil || url.host == "nvwdtfnhsyfdopaxdylx.supabase.co" {
            decisionHandler(.allow)
            return
        }
        #if os(iOS)
        UIApplication.shared.open(url)
        #elseif os(macOS)
        NSWorkspace.shared.open(url)
        #endif
        decisionHandler(.cancel)
    }
}

#if os(iOS)
struct KehuaWebView: UIViewRepresentable {
    final class Coordinator {
        let delegate = KehuaNavigationDelegate()
    }

    func makeCoordinator() -> Coordinator { Coordinator() }

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.websiteDataStore = .default()
        config.defaultWebpagePreferences.allowsContentJavaScript = true
        let view = WKWebView(frame: .zero, configuration: config)
        view.navigationDelegate = context.coordinator.delegate
        view.isOpaque = false
        view.backgroundColor = .systemBackground
        loadKehua(into: view)
        return view
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {}
}
#elseif os(macOS)
struct KehuaWebView: NSViewRepresentable {
    final class Coordinator {
        let delegate = KehuaNavigationDelegate()
    }

    func makeCoordinator() -> Coordinator { Coordinator() }

    func makeNSView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.websiteDataStore = .default()
        config.defaultWebpagePreferences.allowsContentJavaScript = true
        let view = WKWebView(frame: .zero, configuration: config)
        view.navigationDelegate = context.coordinator.delegate
        loadKehua(into: view)
        return view
    }

    func updateNSView(_ nsView: WKWebView, context: Context) {}
}
#endif

struct KehuaRootView: View {
    var body: some View {
        KehuaWebView()
            .ignoresSafeArea(.container, edges: .bottom)
    }
}
