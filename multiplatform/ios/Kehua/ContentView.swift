import SwiftUI
import PhotosUI
import Security
import UIKit

private let bg = Color(red: 244/255, green: 244/255, blue: 247/255)
private let ink = Color(red: 36/255, green: 36/255, blue: 40/255)
private let sub = Color(red: 141/255, green: 141/255, blue: 147/255)
private let third = Color(red: 177/255, green: 177/255, blue: 183/255)
private let line = Color(red: 231/255, green: 231/255, blue: 235/255)
private let pink = Color(red: 255/255, green: 62/255, blue: 104/255)

private let supabaseURL = "https://nvwdtfnhsyfdopaxdylx.supabase.co"
private let supabaseKey = "sb_publishable_S4IE-ziO7WQ_JAK9tuQGgQ_cszwKBWB"

struct KProfile: Identifiable { let id:String; var nickname:String; var bio:String; var avatarPath:String? }
struct KPost: Identifiable { let id:String; let body:String; let imagePath:String?; let createdAt:String; let resonanceCount:Int }
struct KResonance: Identifiable { var id:String { matchId }; let matchId:String; let targetPostId:String; let targetUserId:String?; let body:String; let imagePath:String?; let createdAt:String; let isLit:Bool; let nickname:String?; let bio:String?; let avatarPath:String?; let conversationId:String? }
struct KConversation: Identifiable { let id:String; let otherUserId:String; let nickname:String; let bio:String; let avatarPath:String?; let lastMessage:String?; let lastAt:String? }
struct KMessage: Identifiable { let id:Int64; let senderId:String; let body:String; let createdAt:String }
struct KFriendRequest: Identifiable { let id:String; let senderId:String; let nickname:String; let createdAt:String }

private enum KError: LocalizedError {
    case message(String)
    var errorDescription: String? { if case .message(let s) = self { return s }; return "请求失败" }
}

private final class SecureSession {
    private let service = "com.qxdnzbl.kehua.session"
    func set(_ value:String, key:String) {
        let data = Data(value.utf8)
        let base:[String:Any] = [kSecClass as String:kSecClassGenericPassword, kSecAttrService as String:service, kSecAttrAccount as String:key]
        SecItemDelete(base as CFDictionary)
        var add = base
        add[kSecValueData as String] = data
        add[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        SecItemAdd(add as CFDictionary, nil)
    }
    func get(_ key:String) -> String {
        let query:[String:Any] = [kSecClass as String:kSecClassGenericPassword, kSecAttrService as String:service, kSecAttrAccount as String:key, kSecReturnData as String:true, kSecMatchLimit as String:kSecMatchLimitOne]
        var item:CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess, let data = item as? Data else { return "" }
        return String(data:data, encoding:.utf8) ?? ""
    }
    func clear() {
        let q:[String:Any] = [kSecClass as String:kSecClassGenericPassword, kSecAttrService as String:service]
        SecItemDelete(q as CFDictionary)
    }
}

@MainActor
final class KehuaModel: ObservableObject {
    @Published var loggedIn = false
    @Published var loading = true
    @Published var notice = ""
    @Published var profile:KProfile?
    @Published var posts:[KPost] = []
    @Published var conversations:[KConversation] = []
    @Published var requests:[KFriendRequest] = []
    @Published var selectedResonances:[KResonance] = []
    @Published var openConversation:KConversation?

    private let secure = SecureSession()
    private var token:String { secure.get("access") }
    private var refresh:String { secure.get("refresh") }
    var userId:String { secure.get("uid") }

    init() { Task { await restore() } }

    func restore() async {
        defer { loading = false }
        guard !token.isEmpty, !userId.isEmpty else { loggedIn = false; return }
        do {
            _ = try await rpc("kehua_ensure_profile", [:])
            loggedIn = true
            await refreshAll()
        } catch { loggedIn = false; secure.clear() }
    }

    func signUp(email:String, password:String) async -> String? {
        do {
            let json = try await request(path:"/auth/v1/signup", method:"POST", body:["email":email.trimmingCharacters(in:.whitespacesAndNewlines).lowercased(), "password":password], authenticated:false)
            if let access = json["access_token"] as? String, !access.isEmpty { saveSession(json); loggedIn = true; await refreshAll(); return nil }
            return "确认邮件已经发送。验证邮箱后回来登录。"
        } catch { return readable(error) }
    }

    func signIn(email:String, password:String) async -> String? {
        do {
            let json = try await request(path:"/auth/v1/token?grant_type=password", method:"POST", body:["email":email.trimmingCharacters(in:.whitespacesAndNewlines).lowercased(), "password":password], authenticated:false)
            guard (json["access_token"] as? String)?.isEmpty == false else { return "邮箱或密码不对" }
            saveSession(json)
            loggedIn = true
            await refreshAll()
            return nil
        } catch { return readable(error) }
    }

    func resetPassword(email:String) async -> String? {
        do { _ = try await request(path:"/auth/v1/recover", method:"POST", body:["email":email.trimmingCharacters(in:.whitespacesAndNewlines).lowercased()], authenticated:false); return nil }
        catch { return readable(error) }
    }

    func logout() { secure.clear(); loggedIn = false; profile = nil; posts = []; conversations = []; requests = [] }

    func refreshAll() async {
        do {
            async let p = loadProfile()
            async let ps = loadPosts()
            async let cs = loadConversations()
            async let rs = loadRequests()
            let (profileValue, postValue, convValue, reqValue) = try await (p, ps, cs, rs)
            profile = profileValue; posts = postValue; conversations = convValue; requests = reqValue
        } catch { notice = readable(error) }
    }

    func publish(_ body:String, image:Data?) async -> Bool {
        let clean = body.trimmingCharacters(in:.whitespacesAndNewlines)
        guard !clean.isEmpty else { notice = "写点想说的话再发布"; return false }
        do {
            var imagePath:String? = nil
            if let image { imagePath = try await uploadJPEG(image, kind:"posts") }
            var payload:[String:Any] = ["p_body":clean]
            payload["p_image_path"] = imagePath ?? NSNull()
            let rows = try await rpc("kehua_publish", payload)
            let matchCount = (rows.first?["match_count"] as? NSNumber)?.intValue ?? 0
            notice = matchCount > 0 ? "共鸣已到达，请签收～" : "已经替你放出去了"
            posts = try await loadPosts()
            return true
        } catch { notice = readable(error); return false }
    }

    func openResonances(postId:String) async {
        do { selectedResonances = try await loadResonances(postId:postId) }
        catch { notice = readable(error) }
    }

    func light(_ item:KResonance) async -> KResonance? {
        do {
            let rows = try await rpc("kehua_light", ["p_match_id":item.matchId])
            guard let o = rows.first else { throw KError.message("点亮失败") }
            let lit = KResonance(matchId:item.matchId, targetPostId:item.targetPostId, targetUserId:str(o,"target_user_id"), body:item.body, imagePath:item.imagePath, createdAt:item.createdAt, isLit:true, nickname:str(o,"nickname"), bio:str(o,"bio"), avatarPath:str(o,"avatar_path"), conversationId:str(o,"conversation_id"))
            conversations = try await loadConversations()
            return lit
        } catch { notice = readable(error); return nil }
    }

    func messages(_ conversationId:String) async -> [KMessage] {
        do {
            return try await rpc("kehua_message_list", ["p_conversation_id":conversationId]).map { o in
                KMessage(id:(o["id"] as? NSNumber)?.int64Value ?? 0, senderId:str(o,"sender_id") ?? "", body:str(o,"body") ?? "", createdAt:str(o,"created_at") ?? "")
            }
        } catch { notice = readable(error); return [] }
    }

    func sendMessage(_ conversationId:String, body:String) async -> Bool {
        let clean = body.trimmingCharacters(in:.whitespacesAndNewlines)
        guard !clean.isEmpty else { return false }
        do { _ = try await rpcRaw("kehua_send_message", ["p_conversation_id":conversationId,"p_body":clean]); conversations = try await loadConversations(); return true }
        catch { notice = readable(error); return false }
    }

    func accept(_ req:KFriendRequest) async {
        do { _ = try await rpcRaw("kehua_accept_friend_request", ["p_request":req.id]); requests = try await loadRequests(); notice = "你们现在是好友啦" }
        catch { notice = readable(error) }
    }

    func friendRequest(_ user:String) async {
        do { _ = try await rpcRaw("kehua_friend_request", ["p_target":user]); notice = "好友请求已发送" }
        catch { notice = readable(error) }
    }

    func block(_ user:String) async {
        do { _ = try await rpcRaw("kehua_block", ["p_target_user_id":user]); conversations = try await loadConversations(); notice = "已屏蔽" }
        catch { notice = readable(error) }
    }

    func report(user:String?, post:String?, reason:String = "不适内容") async {
        do { _ = try await rpcRaw("kehua_report", ["p_target_user_id":user ?? NSNull(), "p_target_post_id":post ?? NSNull(), "p_reason":reason]); notice = "已举报" }
        catch { notice = readable(error) }
    }

    func saveProfile(nickname:String, bio:String, avatar:Data?) async -> Bool {
        do {
            var avatarPath = profile?.avatarPath
            if let avatar { avatarPath = try await uploadJPEG(avatar, kind:"avatars") }
            let body:[String:Any] = ["nickname":nickname.trimmingCharacters(in:.whitespacesAndNewlines).isEmpty ? "可话用户" : nickname.trimmingCharacters(in:.whitespacesAndNewlines), "bio":bio.trimmingCharacters(in:.whitespacesAndNewlines), "avatar_path":avatarPath ?? NSNull()]
            _ = try await request(path:"/rest/v1/kehua_profiles?id=eq.\(userId)", method:"PATCH", body:body, authenticated:true, extra:["Prefer":"return=minimal"])
            profile = try await loadProfile(); notice = "保存好了"; return true
        } catch { notice = readable(error); return false }
    }

    func signedURL(_ path:String?) async -> URL? {
        guard let path, !path.isEmpty else { return nil }
        do {
            let encoded = path.split(separator:"/").map { String($0).addingPercentEncoding(withAllowedCharacters:.urlPathAllowed) ?? String($0) }.joined(separator:"/")
            let json = try await request(path:"/storage/v1/object/sign/kehua-media/\(encoded)", method:"POST", body:["expiresIn":3600], authenticated:true)
            guard let s = json["signedURL"] as? String else { return nil }
            return URL(string:s.hasPrefix("http") ? s : supabaseURL + s)
        } catch { return nil }
    }

    private func loadProfile() async throws -> KProfile {
        guard let o = try await rpc("kehua_ensure_profile", [:]).first else { throw KError.message("账号资料暂不可用") }
        return KProfile(id:str(o,"id") ?? userId, nickname:str(o,"nickname") ?? "可话用户", bio:str(o,"bio") ?? "", avatarPath:str(o,"avatar_path"))
    }
    private func loadPosts() async throws -> [KPost] {
        try await rpc("kehua_my_posts", [:]).map { o in KPost(id:str(o,"id") ?? UUID().uuidString, body:str(o,"body") ?? "", imagePath:str(o,"image_path"), createdAt:str(o,"created_at") ?? "", resonanceCount:(o["resonance_count"] as? NSNumber)?.intValue ?? 0) }
    }
    private func loadResonances(postId:String) async throws -> [KResonance] {
        try await rpc("kehua_get_resonances", ["p_post_id":postId]).map { o in KResonance(matchId:str(o,"match_id") ?? UUID().uuidString, targetPostId:str(o,"target_post_id") ?? "", targetUserId:str(o,"target_user_id"), body:str(o,"body") ?? "", imagePath:str(o,"image_path"), createdAt:str(o,"created_at") ?? "", isLit:(o["is_lit"] as? Bool) ?? false, nickname:str(o,"nickname"), bio:str(o,"bio"), avatarPath:str(o,"avatar_path"), conversationId:str(o,"conversation_id")) }
    }
    private func loadConversations() async throws -> [KConversation] {
        try await rpc("kehua_conversation_list", [:]).map { o in KConversation(id:str(o,"id") ?? UUID().uuidString, otherUserId:str(o,"other_user_id") ?? "", nickname:str(o,"nickname") ?? "TA", bio:str(o,"bio") ?? "", avatarPath:str(o,"avatar_path"), lastMessage:str(o,"last_message"), lastAt:str(o,"last_at")) }
    }
    private func loadRequests() async throws -> [KFriendRequest] {
        try await rpc("kehua_incoming_friend_requests", [:]).map { o in KFriendRequest(id:str(o,"id") ?? UUID().uuidString, senderId:str(o,"sender_id") ?? "", nickname:str(o,"nickname") ?? "TA", createdAt:str(o,"created_at") ?? "") }
    }

    private func uploadJPEG(_ data:Data, kind:String) async throws -> String {
        guard let image = UIImage(data:data), var jpg = image.jpegData(compressionQuality:0.86) else { throw KError.message("图片读取失败") }
        if jpg.count > 3*1024*1024, let reduced = image.jpegData(compressionQuality:0.58) { jpg = reduced }
        guard jpg.count <= 3*1024*1024 else { throw KError.message("图片不能超过 3MB") }
        let path = "\(userId)/\(kind)/\(Int(Date().timeIntervalSince1970*1000))-\(UUID().uuidString).jpg"
        let encoded = path.split(separator:"/").map { String($0).addingPercentEncoding(withAllowedCharacters:.urlPathAllowed) ?? String($0) }.joined(separator:"/")
        _ = try await rawRequest(path:"/storage/v1/object/kehua-media/\(encoded)", method:"POST", data:jpg, authenticated:true, headers:["Content-Type":"image/jpeg","x-upsert":"false"])
        return path
    }

    private func saveSession(_ json:[String:Any]) {
        if let access = json["access_token"] as? String { secure.set(access,key:"access") }
        if let refresh = json["refresh_token"] as? String { secure.set(refresh,key:"refresh") }
        if let user = json["user"] as? [String:Any], let id = user["id"] as? String { secure.set(id,key:"uid") }
    }

    private func refreshIfNeeded() async -> Bool {
        guard !refresh.isEmpty else { return false }
        do { let j = try await request(path:"/auth/v1/token?grant_type=refresh_token", method:"POST", body:["refresh_token":refresh], authenticated:false); saveSession(j); return !token.isEmpty }
        catch { secure.clear(); return false }
    }

    private func rpc(_ name:String, _ body:[String:Any]) async throws -> [[String:Any]] {
        let any = try await requestAny(path:"/rest/v1/rpc/\(name)", method:"POST", body:body, authenticated:true)
        if let rows = any as? [[String:Any]] { return rows }
        return []
    }
    private func rpcRaw(_ name:String, _ body:[String:Any]) async throws -> Any { try await requestAny(path:"/rest/v1/rpc/\(name)", method:"POST", body:body, authenticated:true) }

    private func request(path:String, method:String, body:[String:Any], authenticated:Bool, extra:[String:String] = [:]) async throws -> [String:Any] {
        let any = try await requestAny(path:path, method:method, body:body, authenticated:authenticated, extra:extra)
        return any as? [String:Any] ?? [:]
    }
    private func requestAny(path:String, method:String, body:[String:Any], authenticated:Bool, extra:[String:String] = [:], retry:Bool = true) async throws -> Any {
        let data = try JSONSerialization.data(withJSONObject:body)
        let (out,status) = try await rawRequest(path:path, method:method, data:data, authenticated:authenticated, headers:["Content-Type":"application/json"].merging(extra){$1})
        if status == 401 && authenticated && retry && await refreshIfNeeded() { return try await requestAny(path:path, method:method, body:body, authenticated:true, extra:extra, retry:false) }
        guard (200..<300).contains(status) else {
            let json = (try? JSONSerialization.jsonObject(with:out)) as? [String:Any]
            let message = (json?["message"] as? String) ?? (json?["msg"] as? String) ?? (json?["error_description"] as? String) ?? (json?["error"] as? String) ?? "请求失败 \(status)"
            throw KError.message(message)
        }
        if out.isEmpty { return [:] }
        return (try? JSONSerialization.jsonObject(with:out)) ?? [:]
    }
    private func rawRequest(path:String, method:String, data:Data, authenticated:Bool, headers:[String:String]) async throws -> (Data,Int) {
        guard let url = URL(string:supabaseURL+path) else { throw KError.message("地址错误") }
        var r = URLRequest(url:url, timeoutInterval:15); r.httpMethod = method; r.httpBody = data
        r.setValue(supabaseKey, forHTTPHeaderField:"apikey")
        if authenticated { r.setValue("Bearer \(token)", forHTTPHeaderField:"Authorization") }
        headers.forEach { r.setValue($0.value, forHTTPHeaderField:$0.key) }
        let (out,res) = try await URLSession.shared.data(for:r)
        return (out,(res as? HTTPURLResponse)?.statusCode ?? 0)
    }
    private func str(_ o:[String:Any], _ key:String) -> String? { let v=o[key]; if v is NSNull { return nil }; return v as? String }
    private func readable(_ e:Error) -> String { let s=e.localizedDescription.lowercased(); if s.contains("invalid login credentials") { return "邮箱或密码不对" }; if s.contains("email not confirmed") { return "这个邮箱还没完成验证" }; if s.contains("rate") || s.contains("too many") { return "操作太频繁了，请稍后再试" }; return e.localizedDescription }
}

struct ContentView: View {
    @StateObject private var model = KehuaModel()
    var body: some View {
        Group {
            if model.loading { ZStack { bg.ignoresSafeArea(); ProgressView().tint(pink) } }
            else if model.loggedIn { MainView(model:model) }
            else { AuthView(model:model) }
        }
        .preferredColorScheme(.light)
    }
}

struct AuthView: View {
    @ObservedObject var model:KehuaModel
    @State private var mode = 0
    @State private var email = ""
    @State private var password = ""
    @State private var busy = false
    @State private var message = ""
    var body: some View {
        ZStack {
            bg.ignoresSafeArea()
            VStack(spacing:24) {
                Spacer()
                VStack(spacing:8) {
                    HStack(spacing:2) { Text("说想说的话").font(.system(size:31,weight:.semibold)).foregroundStyle(ink); Text("|").font(.system(size:32,weight:.medium)).foregroundStyle(pink) }
                    Text("记录你真实的想法和感受").font(.system(size:14)).foregroundStyle(sub)
                }
                VStack(spacing:16) {
                    Picker("", selection:$mode) { Text("第一次来").tag(0); Text("回来看看").tag(1) }.pickerStyle(.segmented)
                    TextField("邮箱",text:$email).textInputAutocapitalization(.never).keyboardType(.emailAddress).textContentType(.username).padding(14).background(bg).clipShape(RoundedRectangle(cornerRadius:14))
                    SecureField("密码（至少 6 位）",text:$password).textContentType(mode == 0 ? .newPassword : .password).padding(14).background(bg).clipShape(RoundedRectangle(cornerRadius:14))
                    if !message.isEmpty { Text(message).font(.footnote).foregroundStyle(message.contains("发送") ? sub : pink).multilineTextAlignment(.center) }
                    Button {
                        guard !busy else { return }; busy=true
                        Task { message = (mode == 0 ? await model.signUp(email:email,password:password) : await model.signIn(email:email,password:password)) ?? ""; busy=false }
                    } label: { Text(busy ? "请稍候…" : "进去").frame(maxWidth:.infinity).frame(height:48).foregroundStyle(.white).background(ink).clipShape(RoundedRectangle(cornerRadius:24)) }
                    if mode == 1 { Button("忘记密码") { Task { message = (await model.resetPassword(email:email)) == nil ? "重置邮件已经发送" : "暂时无法发送" } }.font(.footnote).foregroundStyle(sub) }
                    Text("没有手机号、关注数和公开广场。你说的话只会去寻找共鸣。").font(.caption).foregroundStyle(third).multilineTextAlignment(.center)
                }
                .padding(20).background(.white).clipShape(RoundedRectangle(cornerRadius:24)).padding(.horizontal,24)
                Spacer()
            }
        }
    }
}

struct MainView: View {
    @ObservedObject var model:KehuaModel
    @State private var tab = 0
    var body: some View {
        TabView(selection:$tab) {
            HomeView(model:model).tabItem { Label("首页",systemImage:"house") }.tag(0)
            MessagesView(model:model).tabItem { Label("消息",systemImage:"bubble.left") }.tag(1)
            MeView(model:model).tabItem { Label("我",systemImage:"person") }.tag(2)
        }
        .tint(pink)
        .onAppear { UITabBar.appearance().backgroundColor = UIColor(red:250/255,green:250/255,blue:252/255,alpha:1) }
        .overlay(alignment:.top) { if !model.notice.isEmpty { Text(model.notice).font(.footnote).foregroundStyle(.white).padding(.horizontal,16).padding(.vertical,10).background(ink.opacity(0.92)).clipShape(Capsule()).padding(.top,8).onTapGesture { model.notice="" } } }
    }
}

struct HomeView: View {
    @ObservedObject var model:KehuaModel
    @State private var bodyText = ""
    @State private var busy = false
    @State private var picker:PhotosPickerItem?
    @State private var imageData:Data?
    @State private var resonanceOpen = false
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment:.leading,spacing:0) {
                    Text(Date.now.formatted(.dateTime.month().day().weekday())).font(.system(size:13)).foregroundStyle(third)
                    Text("此刻，\n有什么想说～").font(.system(size:24,weight:.semibold)).foregroundStyle(ink).lineSpacing(5).padding(.top,20)
                    VStack(alignment:.leading,spacing:14) {
                        ZStack(alignment:.topLeading) {
                            if bodyText.isEmpty { Text("写下你真实的想法和感受").font(.system(size:16)).foregroundStyle(sub).padding(.top,8).padding(.leading,5) }
                            TextEditor(text:$bodyText).font(.system(size:16)).foregroundStyle(ink).scrollContentBackground(.hidden).frame(minHeight:112)
                        }
                        if let imageData, let img=UIImage(data:imageData) { Image(uiImage:img).resizable().scaledToFill().frame(maxWidth:.infinity,maxHeight:180).clipped().clipShape(RoundedRectangle(cornerRadius:14)) }
                        HStack {
                            PhotosPicker(selection:$picker, matching:.images) { Label("图片",systemImage:"plus").font(.system(size:14)).foregroundStyle(sub) }
                            Spacer()
                            Text("\(bodyText.count)/1200").font(.caption).foregroundStyle(third)
                            Button {
                                guard !busy else{return}; busy=true
                                Task { if await model.publish(bodyText,image:imageData) { bodyText=""; imageData=nil }; busy=false }
                            } label: { Image(systemName:"arrow.up").font(.system(size:18,weight:.bold)).foregroundStyle(.white).frame(width:44,height:44).background((bodyText.trimmingCharacters(in:.whitespacesAndNewlines).isEmpty || busy) ? Color(red:1,green:0.85,blue:0.88) : pink).clipShape(Circle()) }.disabled(bodyText.trimmingCharacters(in:.whitespacesAndNewlines).isEmpty || busy)
                        }
                    }
                    .padding(18).background(.white).clipShape(RoundedRectangle(cornerRadius:24)).padding(.top,26)
                    if !model.posts.isEmpty { VStack(spacing:12) { ForEach(model.posts) { post in PostCard(model:model,post:post) { Task { await model.openResonances(postId:post.id); resonanceOpen=true } } } }.padding(.top,18) }
                }.padding(.horizontal,24).padding(.top,20).padding(.bottom,30)
            }.background(bg).navigationBarHidden(true)
        }
        .task(id:picker) { if let picker, let data=try? await picker.loadTransferable(type:Data.self) { imageData=data } }
        .fullScreenCover(isPresented:$resonanceOpen) { ResonanceView(model:model) }
        .refreshable { await model.refreshAll() }
    }
}

struct PostCard: View {
    @ObservedObject var model:KehuaModel; let post:KPost; let open:()->Void
    var body: some View {
        VStack(alignment:.leading,spacing:12) {
            Text(post.body).font(.system(size:15)).foregroundStyle(ink).lineSpacing(6)
            SignedImage(model:model,path:post.imagePath,maxHeight:220)
            HStack { Text(shortTime(post.createdAt)).font(.caption).foregroundStyle(third); Spacer(); Button(post.resonanceCount > 0 ? "共鸣已到达，请签收～" : "正在寻找共鸣") { if post.resonanceCount > 0 { open() } }.font(.system(size:13,weight:.medium)).foregroundStyle(post.resonanceCount > 0 ? pink : sub) }
        }.padding(18).background(.white).clipShape(RoundedRectangle(cornerRadius:20))
    }
}

struct ResonanceView: View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject var model:KehuaModel
    @State private var index=0
    @State private var revealed:KResonance?
    @State private var showChat=false
    var current:KResonance? { model.selectedResonances.indices.contains(index) ? model.selectedResonances[index] : nil }
    var body: some View {
        ZStack { bg.ignoresSafeArea(); VStack(spacing:0) {
            ZStack { Button { dismiss() } label:{ Image(systemName:"chevron.left").foregroundStyle(ink).frame(width:44,height:44) }.frame(maxWidth:.infinity,alignment:.leading); Text("共鸣").font(.system(size:17,weight:.semibold)).foregroundStyle(ink); Text(current == nil ? "" : "\(index+1) / \(model.selectedResonances.count)").font(.caption).foregroundStyle(sub).frame(maxWidth:.infinity,alignment:.trailing).padding(.trailing,12) }.frame(height:56).padding(.horizontal,8)
            Spacer()
            if let current {
                VStack(alignment:.leading,spacing:20) {
                    Text(current.body).font(.system(size:18)).foregroundStyle(ink).lineSpacing(9).frame(maxWidth:.infinity,alignment:.leading)
                    SignedImage(model:model,path:current.imagePath,maxHeight:300)
                    Text(shortTime(current.createdAt)).font(.caption).foregroundStyle(third)
                    if let r=revealed ?? (current.isLit ? current:nil), r.isLit {
                        HStack(spacing:12) { Circle().fill(Color.white).frame(width:44,height:44).overlay(Text(String((r.nickname ?? "TA").prefix(1))).foregroundStyle(sub)); VStack(alignment:.leading){Text(r.nickname ?? "TA").font(.system(size:15,weight:.semibold)); if let b=r.bio,!b.isEmpty { Text(b).font(.caption).foregroundStyle(sub) } }; Spacer(); if r.conversationId != nil { Button("聊聊") { if let c=model.conversations.first(where:{$0.id==r.conversationId}) { model.openConversation=c; showChat=true } }.foregroundStyle(pink) } }
                    }
                }.padding(24)
            } else { Text("还没有找到合适的共鸣").foregroundStyle(sub) }
            Spacer()
            if current != nil { HStack(spacing:12) { Button("略过") { if index+1 < model.selectedResonances.count { index+=1; revealed=nil } else { dismiss() } }.frame(maxWidth:.infinity).frame(height:44).foregroundStyle(sub).background(.white).clipShape(RoundedRectangle(cornerRadius:22)); Button("点亮") { guard let current else{return}; Task { revealed=await model.light(current) } }.frame(maxWidth:.infinity).frame(height:44).foregroundStyle(.white).background(pink).clipShape(RoundedRectangle(cornerRadius:22)) }.padding(24) }
        }}.fullScreenCover(isPresented:$showChat) { if let c=model.openConversation { ChatView(model:model,conversation:c) } }
    }
}

struct MessagesView: View {
    @ObservedObject var model:KehuaModel
    @State private var chat:KConversation?
    var body: some View {
        NavigationStack { List {
            if !model.requests.isEmpty { Section("好友请求") { ForEach(model.requests) { r in HStack { Text(r.nickname); Spacer(); Button("接受") { Task { await model.accept(r) } }.foregroundStyle(pink) } } } }
            Section { if model.conversations.isEmpty { Text("还没有消息").foregroundStyle(sub).listRowBackground(bg) } else { ForEach(model.conversations) { c in Button { chat=c } label:{ HStack(spacing:12) { Avatar(model:model,path:c.avatarPath,name:c.nickname,size:42); VStack(alignment:.leading,spacing:5){HStack{Text(c.nickname).font(.system(size:15,weight:.medium)).foregroundStyle(ink);Spacer();Text(shortTime(c.lastAt ?? "")).font(.caption).foregroundStyle(third)};Text(c.lastMessage ?? "从一次共鸣开始").font(.system(size:13)).foregroundStyle(sub).lineLimit(1)} }.padding(.vertical,5) } } } }
        }.scrollContentBackground(.hidden).background(bg).navigationTitle("消息").navigationBarTitleDisplayMode(.inline) }
        .fullScreenCover(item:$chat) { ChatView(model:model,conversation:$0) }
        .task { await model.refreshAll() }.refreshable { await model.refreshAll() }
    }
}

struct ChatView: View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject var model:KehuaModel
    let conversation:KConversation
    @State private var messages:[KMessage]=[]
    @State private var input=""
    @State private var menu=false
    var body: some View {
        VStack(spacing:0) {
            HStack { Button { dismiss() } label:{Image(systemName:"chevron.left").foregroundStyle(ink).frame(width:44,height:44)}; Spacer(); VStack(spacing:2){Text(conversation.nickname).font(.system(size:16,weight:.semibold)); if !conversation.bio.isEmpty {Text(conversation.bio).font(.caption2).foregroundStyle(sub).lineLimit(1)}}; Spacer(); Button {menu=true} label:{Image(systemName:"ellipsis").foregroundStyle(ink).frame(width:44,height:44)} }.padding(.horizontal,8).frame(height:56).background(.white)
            ScrollViewReader { proxy in ScrollView { LazyVStack(spacing:10) { ForEach(messages) { m in HStack { if m.senderId == model.userId { Spacer() }; Text(m.body).font(.system(size:15)).foregroundStyle(ink).padding(.horizontal,14).padding(.vertical,10).background(m.senderId == model.userId ? Color(red:1,green:0.94,blue:0.96) : .white).clipShape(RoundedRectangle(cornerRadius:18)).frame(maxWidth:280,alignment:m.senderId == model.userId ? .trailing:.leading); if m.senderId != model.userId {Spacer()} }.id(m.id) } }.padding(16) }.onChange(of:messages.count) { _ in if let last=messages.last {withAnimation{proxy.scrollTo(last.id,anchor:.bottom)}} } }
            HStack(alignment:.bottom,spacing:10){TextField("说点什么",text:$input,axis:.vertical).lineLimit(1...4).padding(12).background(bg).clipShape(RoundedRectangle(cornerRadius:20));Button("发送"){let text=input;input="";Task{if await model.sendMessage(conversation.id,body:text){messages=await model.messages(conversation.id)}}}.foregroundStyle(pink).fontWeight(.semibold)}.padding(12).background(.white)
        }.background(bg).confirmationDialog(conversation.nickname,isPresented:$menu){Button("加为好友"){Task{await model.friendRequest(conversation.otherUserId)}};Button("屏蔽这个人",role:.destructive){Task{await model.block(conversation.otherUserId);dismiss()}};Button("举报",role:.destructive){Task{await model.report(user:conversation.otherUserId,post:nil)}}}
        .task { while !Task.isCancelled { messages=await model.messages(conversation.id); try? await Task.sleep(nanoseconds:4_000_000_000) } }
    }
}

struct MeView: View {
    @ObservedObject var model:KehuaModel
    @State private var nickname=""
    @State private var bio=""
    @State private var picker:PhotosPickerItem?
    @State private var avatarData:Data?
    @State private var showSettings=false
    var body: some View {
        NavigationStack { ScrollView { VStack(spacing:14) {
            ZStack { Text("我").font(.system(size:17,weight:.semibold)).foregroundStyle(ink); Button {showSettings=true} label:{Image(systemName:"ellipsis").foregroundStyle(ink).frame(width:44,height:44)}.frame(maxWidth:.infinity,alignment:.trailing) }.frame(height:50)
            PhotosPicker(selection:$picker,matching:.images){ if let avatarData,let img=UIImage(data:avatarData){Image(uiImage:img).resizable().scaledToFill().frame(width:68,height:68).clipShape(Circle())}else{Avatar(model:model,path:model.profile?.avatarPath,name:nickname.isEmpty ? (model.profile?.nickname ?? "我") : nickname,size:68)} }
            TextField("昵称",text:$nickname).multilineTextAlignment(.center).font(.system(size:20,weight:.semibold)).foregroundStyle(ink)
            TextField("写一句关于自己",text:$bio,axis:.vertical).multilineTextAlignment(.center).font(.system(size:14)).foregroundStyle(Color(red:119/255,green:119/255,blue:125/255)).lineLimit(1...3)
            Button("保存") {Task{_ = await model.saveProfile(nickname:nickname,bio:bio,avatar:avatarData)}}.font(.system(size:14,weight:.medium)).foregroundStyle(pink)
            Divider().padding(.top,8)
            HStack { Text("发布").font(.system(size:14,weight:.semibold)).foregroundStyle(ink); Spacer() }.padding(.horizontal,4)
            ForEach(model.posts){post in PostCard(model:model,post:post,open:{Task{await model.openResonances(postId:post.id)}})}
        }.padding(.horizontal,24).padding(.bottom,30) }.background(bg).navigationBarHidden(true) }
        .task { if nickname.isEmpty {nickname=model.profile?.nickname ?? "";bio=model.profile?.bio ?? ""} }
        .task(id:picker){if let picker,let d=try? await picker.loadTransferable(type:Data.self){avatarData=d}}
        .confirmationDialog("设置",isPresented:$showSettings){Button("退出当前账号",role:.destructive){model.logout()};Button("取消",role:.cancel){}}
    }
}

struct Avatar: View {
    @ObservedObject var model:KehuaModel; let path:String?; let name:String; let size:CGFloat
    @State private var url:URL?
    var body: some View { Group { if let url { AsyncImage(url:url){phase in if let image=phase.image{image.resizable().scaledToFill()}else{Circle().fill(.white).overlay(Text(String(name.prefix(1))).foregroundStyle(sub))}} } else {Circle().fill(.white).overlay(Text(String(name.prefix(1))).foregroundStyle(sub))} }.frame(width:size,height:size).clipShape(Circle()).task{id in url=await model.signedURL(path)} }
    private var id:String { path ?? "" }
}

struct SignedImage: View {
    @ObservedObject var model:KehuaModel; let path:String?; let maxHeight:CGFloat
    @State private var url:URL?
    var body: some View { Group { if let url { AsyncImage(url:url){phase in if let image=phase.image{image.resizable().scaledToFit().clipShape(RoundedRectangle(cornerRadius:14))}else if phase.error != nil{EmptyView()}else{ProgressView()}} } }.frame(maxWidth:.infinity).frame(maxHeight:maxHeight).task(id:path){url=await model.signedURL(path)} }
}

private func shortTime(_ raw:String)->String {
    guard !raw.isEmpty else{return ""}
    let f=ISO8601DateFormatter(); f.formatOptions=[.withInternetDateTime,.withFractionalSeconds]
    let d=f.date(from:raw) ?? ISO8601DateFormatter().date(from:raw)
    guard let d else{return ""}
    if Calendar.current.isDateInToday(d){return d.formatted(date:.omitted,time:.shortened)}
    return d.formatted(.dateTime.month().day())
}