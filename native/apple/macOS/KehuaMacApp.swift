import SwiftUI

@main
struct KehuaMacApp: App {
    var body: some Scene {
        WindowGroup {
            KehuaRootView()
                .frame(minWidth: 390, minHeight: 700)
        }
        .defaultSize(width: 430, height: 820)
        .windowResizability(.contentMinSize)
    }
}
