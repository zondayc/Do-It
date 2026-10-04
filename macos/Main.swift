import Cocoa
import WebKit
import UserNotifications
import UniformTypeIdentifiers

final class AppDelegate: NSObject, NSApplicationDelegate, NSWindowDelegate, WKScriptMessageHandler, WKNavigationDelegate, UNUserNotificationCenterDelegate {
    var window: NSWindow!
    var web: WKWebView!
    var statusItem: NSStatusItem!
    let center = UNUserNotificationCenter.current()
    let storageQueue = DispatchQueue(label: "com.doit.app.storage", qos: .userInitiated)
    var generation = 0
    var timer: Timer?
    var lastDay = ""
    var dayStart = 0
    let support: URL = {
        if let path = ProcessInfo.processInfo.environment["DOIT_DATA_DIR"], path.hasPrefix("/") { return URL(fileURLWithPath: path, isDirectory: true) }
        return FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("Do It", isDirectory: true)
    }()
    var dataFile: URL { support.appendingPathComponent("state.json") }

    func applicationDidFinishLaunching(_ notification: Notification) {
        try? FileManager.default.createDirectory(at: support, withIntermediateDirectories: true)
        center.delegate = self
        let complete = UNNotificationAction(identifier: "complete", title: "完成打卡", options: [])
        let snooze = UNNotificationAction(identifier: "snooze", title: "10 分钟后提醒", options: [])
        center.setNotificationCategories([UNNotificationCategory(identifier: "task", actions: [complete, snooze], intentIdentifiers: [], options: [])])
        let config = WKWebViewConfiguration()
        config.userContentController.add(self, name: "doit")
        web = WKWebView(frame: .zero, configuration: config)
        web.navigationDelegate = self
        web.setValue(false, forKey: "drawsBackground")
        let phonePreview = ProcessInfo.processInfo.arguments.contains("--preview-phone")
        window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: phonePreview ? 448 : 1110, height: phonePreview ? 900 : 790), styleMask: [.titled, .closable, .miniaturizable, .resizable], backing: .buffered, defer: false)
        window.title = "Do It"
        window.minSize = NSSize(width: phonePreview ? 360 : 780, height: 590)
        window.contentView = web
        window.backgroundColor = NSColor(calibratedWhite: 0.97, alpha: 1)
        window.delegate = self
        window.isReleasedWhenClosed = false
        window.center()
        window.makeKeyAndOrderFront(nil)
        NSApp.activate(ignoringOtherApps: true)
        let root = Bundle.main.resourceURL!.appendingPathComponent("web", isDirectory: true)
        web.loadFileURL(root.appendingPathComponent("index.html"), allowingReadAccessTo: root)
        buildMenus()
        timer = Timer.scheduledTimer(withTimeInterval: 30, repeats: true) { [weak self] _ in
            guard let self = self else { return }
            let day = self.logicalDay()
            if day != self.lastDay { self.lastDay = day; self.emit("focus") }
        }
        NSWorkspace.shared.notificationCenter.addObserver(self, selector: #selector(wake), name: NSWorkspace.didWakeNotification, object: nil)
    }
    func buildMenus() {
        let main = NSMenu()
        let app = NSMenuItem(); let menu = NSMenu()
        menu.addItem(withTitle: "关于 Do It", action: #selector(about), keyEquivalent: "")
        menu.addItem(.separator())
        menu.addItem(withTitle: "隐藏 Do It", action: #selector(NSApplication.hide(_:)), keyEquivalent: "h")
        menu.addItem(withTitle: "退出 Do It", action: #selector(NSApplication.terminate(_:)), keyEquivalent: "q")
        app.submenu = menu; main.addItem(app)
        let editItem = NSMenuItem(); let edit = NSMenu(title: "编辑")
        for (title, action, key) in [("撤销", "undo:", "z"), ("剪切", "cut:", "x"), ("复制", "copy:", "c"), ("粘贴", "paste:", "v"), ("全选", "selectAll:", "a")] {
            edit.addItem(withTitle: title, action: Selector(action), keyEquivalent: key)
        }
        editItem.submenu = edit; main.addItem(editItem); NSApp.mainMenu = main
        statusItem = NSStatusBar.system.statusItem(withLength: NSStatusItem.squareLength)
        statusItem.button?.image = NSImage(systemSymbolName: "checkmark.circle", accessibilityDescription: "Do It")
        let statusMenu = NSMenu()
        statusMenu.addItem(withTitle: "打开 Do It", action: #selector(showWindow), keyEquivalent: "")
        statusMenu.addItem(.separator())
        statusMenu.addItem(withTitle: "退出 Do It", action: #selector(NSApplication.terminate(_:)), keyEquivalent: "")
        statusMenu.items.forEach { if $0.action == #selector(showWindow) { $0.target = self } }
        statusItem.menu = statusMenu
    }
    @objc func about() { NSApp.orderFrontStandardAboutPanel(options: [.applicationName: "Do It", .applicationVersion: "0.1.0", .credits: NSAttributedString(string: "本地打卡与提醒\n完全离线 · 记录留在本机")]) }
    @objc func showWindow() { window.makeKeyAndOrderFront(nil); NSApp.activate(ignoringOtherApps: true); emit("focus") }
    @objc func wake() { emit("focus") }
    func windowShouldClose(_ sender: NSWindow) -> Bool { sender.orderOut(nil); return false }
    func applicationShouldHandleReopen(_ sender: NSApplication, hasVisibleWindows flag: Bool) -> Bool { showWindow(); return true }
    func applicationDidBecomeActive(_ notification: Notification) { if web != nil { emit("focus") } }
    func logicalDay() -> String {
        var date = Date()
        if Calendar.current.component(.hour, from: date) < dayStart { date = Calendar.current.date(byAdding: .day, value: -1, to: date)! }
        let f = DateFormatter(); f.dateFormat = "yyyy-MM-dd"; f.locale = Locale(identifier: "en_US_POSIX"); return f.string(from: date)
    }
    func reply(_ id: Int, data: Any? = nil, error: String? = nil) {
        let value: [String: Any] = error.map { ["error": $0] } ?? ["data": data ?? NSNull()]
        guard let bytes = try? JSONSerialization.data(withJSONObject: value, options: [.fragmentsAllowed]), let json = String(data: bytes, encoding: .utf8) else { return }
        DispatchQueue.main.async { self.web.evaluateJavaScript("window.DoItNativeReply(\(id),\(json))", completionHandler: nil) }
    }
    func emit(_ value: Any) { guard let data = try? JSONSerialization.data(withJSONObject: value, options: [.fragmentsAllowed]), let json = String(data: data, encoding: .utf8) else { return }; DispatchQueue.main.async { self.web.evaluateJavaScript("window.DoItNativeEvent(\(json))", completionHandler: nil) } }
    func status(_ completion: @escaping ([String: Any]) -> Void) { center.getNotificationSettings { settings in completion(["platform": "macos", "notifications": settings.authorizationStatus == .authorized || settings.authorizationStatus == .provisional, "exact": true]) } }
    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        guard message.frameInfo.isMainFrame, message.frameInfo.request.url?.isFileURL == true, let body = message.body as? [String: Any], let id = body["id"] as? Int, let action = body["action"] as? String else { return }
        let payload = body["payload"] as? [String: Any] ?? [:]
        switch action {
        case "load": storageQueue.async { do { if FileManager.default.fileExists(atPath: self.dataFile.path) { let data = try Data(contentsOf: self.dataFile); let obj = try JSONSerialization.jsonObject(with: data); if let state = obj as? [String: Any], let settings = state["settings"] as? [String: Any], let hour = settings["dayStart"] as? Int { DispatchQueue.main.async { self.dayStart = hour } }; self.reply(id, data: obj) } else { self.reply(id) } } catch { self.reply(id, error: error.localizedDescription) } }
        case "save": storageQueue.async { do { guard let state = payload["state"] as? [String: Any], (state["version"] as? Int) == 1 else { throw NSError(domain: "Do It", code: 1, userInfo: [NSLocalizedDescriptionKey: "无效的任务数据"]) }; let bytes = try JSONSerialization.data(withJSONObject: state, options: [.sortedKeys]); try bytes.write(to: self.dataFile, options: .atomic); if let settings = state["settings"] as? [String: Any], let hour = settings["dayStart"] as? Int { DispatchQueue.main.async { self.dayStart = hour } }; self.reply(id, data: true) } catch { self.reply(id, error: error.localizedDescription) } }
        case "status": status { self.reply(id, data: $0) }
        case "sync":
            generation += 1; let currentGeneration = generation
            let alarms = payload["alarms"] as? [[String: Any]] ?? []; let sound = payload["sound"] as? Bool ?? true
            center.getPendingNotificationRequests { requests in
                let invalidSnoozes = self.invalidSnoozes(requests)
                DispatchQueue.main.async {
                    guard currentGeneration == self.generation else { self.status { self.reply(id, data: $0) }; return }
                    self.center.removePendingNotificationRequests(withIdentifiers: requests.filter { $0.identifier.hasPrefix("task-") }.map { $0.identifier })
                    self.center.removePendingNotificationRequests(withIdentifiers: invalidSnoozes)
                    self.status { info in
                        DispatchQueue.main.async {
                            if currentGeneration == self.generation && info["notifications"] as? Bool == true {
                                for alarm in alarms.prefix(60) { self.schedule(alarm, sound: sound) }
                            }
                            self.reply(id, data: info)
                        }
                    }
                }
            }
        case "permission":
            center.getNotificationSettings { settings in
                if settings.authorizationStatus == .notDetermined { self.center.requestAuthorization(options: [.alert, .sound, .badge]) { _, error in if let error = error { self.reply(id, error: error.localizedDescription) } else { self.reply(id, data: true) } } }
                else { DispatchQueue.main.async { NSWorkspace.shared.open(URL(string: "x-apple.systempreferences:com.apple.Notifications-Settings.extension")!); self.reply(id, data: true) } }
            }
        case "test": center.getNotificationSettings { settings in
            guard settings.authorizationStatus == .authorized else { self.reply(id, error: "请先允许系统通知"); return }
            let content = UNMutableNotificationContent(); content.title = "Do It · 提醒测试"; content.body = "测试提醒已触发。"; if payload["sound"] as? Bool ?? true { content.sound = .default }
            self.center.add(UNNotificationRequest(identifier: "test-" + UUID().uuidString, content: content, trigger: UNTimeIntervalNotificationTrigger(timeInterval: 10, repeats: false))) { error in if let error = error { self.reply(id, error: error.localizedDescription) } else { self.reply(id, data: true) } }
        }
        case "export":
            let panel = NSSavePanel(); panel.allowedContentTypes = [.json]; panel.nameFieldStringValue = "Do-It-\(logicalDay()).json"
            panel.beginSheetModal(for: window) { result in guard result == .OK, let url = panel.url else { self.reply(id); return }; self.storageQueue.async { do { let bytes = try Data(contentsOf: self.dataFile); try bytes.write(to: url, options: .atomic); self.reply(id, data: true) } catch { self.reply(id, error: error.localizedDescription) } } }
        case "pickImport":
            let panel = NSOpenPanel(); panel.allowedContentTypes = [.json]; panel.allowsMultipleSelection = false
            panel.beginSheetModal(for: window) { result in guard result == .OK, let url = panel.url else { self.reply(id); return }; self.storageQueue.async { do { let data = try Data(contentsOf: url); guard data.count <= 20_000_000, let raw = String(data: data, encoding: .utf8) else { self.reply(id, error: "备份文件过大或编码无效"); return }; self.reply(id, data: raw) } catch { self.reply(id, error: error.localizedDescription) } } }
        case "beforeImport": storageQueue.async { do { if FileManager.default.fileExists(atPath: self.dataFile.path) { let bytes = try Data(contentsOf: self.dataFile); try bytes.write(to: self.support.appendingPathComponent("before-import.json"), options: .atomic) }; self.reply(id, data: true) } catch { self.reply(id, error: error.localizedDescription) } }
        default: reply(id, error: "不支持的操作")
        }
    }
    func invalidSnoozes(_ requests: [UNNotificationRequest]) -> [String] {
        let snoozes = requests.filter { $0.identifier.hasPrefix("snooze-") }
        guard !snoozes.isEmpty, let data = try? Data(contentsOf: dataFile), let state = try? JSONSerialization.jsonObject(with: data) as? [String: Any], let tasks = state["tasks"] as? [[String: Any]] else { return [] }
        let records = state["completions"] as? [String: [String: Any]] ?? [:]
        return snoozes.filter { request in
            guard let id = request.content.userInfo["taskId"] as? String, let day = request.content.userInfo["date"] as? String, let task = tasks.first(where: { $0["id"] as? String == id }), let revisions = task["revisions"] as? [[String: Any]], let latest = revisions.last else { return true }
            return task["archivedOn"] is String || latest["enabled"] as? Bool != true || latest["alarm"] as? Bool != true || records[id]?[day] != nil || (task["kind"] as? String == "note" && !(records[id] ?? [:]).isEmpty)
        }.map { $0.identifier }
    }
    func schedule(_ alarm: [String: Any], sound: Bool, identifier: String? = nil) {
        guard let at = alarm["at"] as? Double, let task = alarm["taskId"] as? String, let day = alarm["date"] as? String, let title = alarm["title"] as? String else { return }
        let interval = at / 1000 - Date().timeIntervalSince1970; guard interval > 0 else { return }
        let content = UNMutableNotificationContent(); content.title = "Do It · \(title)"; content.body = "到时间了，做完记得打卡。"; content.categoryIdentifier = "task"; content.userInfo = ["taskId": task, "date": day]; if sound { content.sound = .default }
        let request = UNNotificationRequest(identifier: identifier ?? "task-\(task)-\(day)", content: content, trigger: UNTimeIntervalNotificationTrigger(timeInterval: max(1, interval), repeats: false))
        center.add(request) { error in if let error = error { self.emit(["error": "提醒未能安排：" + error.localizedDescription]) } }
    }
    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification, withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) { completionHandler([.banner, .sound]) }
    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse, withCompletionHandler completionHandler: @escaping () -> Void) {
        let info = response.notification.request.content.userInfo
        if response.actionIdentifier == "snooze", let task = info["taskId"] as? String, let day = info["date"] as? String { schedule(["taskId": task, "date": day, "title": response.notification.request.content.title.replacingOccurrences(of: "Do It · ", with: ""), "at": (Date().timeIntervalSince1970 + 600) * 1000], sound: response.notification.request.content.sound != nil, identifier: "snooze-\(task)-\(day)"); completionHandler(); return }
        if response.actionIdentifier == "complete", let task = info["taskId"] as? String, let day = info["date"] as? String {
            storageQueue.async {
                do { let data = try Data(contentsOf: self.dataFile); var state = try JSONSerialization.jsonObject(with: data) as! [String: Any]; var all = state["completions"] as? [String: [String: Any]] ?? [:]; var records = all[task] ?? [:]; records[day] = ["at": ISO8601DateFormatter().string(from: Date()), "manual": day != self.logicalDay()]; all[task] = records; state["completions"] = all; try JSONSerialization.data(withJSONObject: state).write(to: self.dataFile, options: .atomic); self.emit("reload") } catch { self.emit(["error": error.localizedDescription]) }
                completionHandler()
            }
        } else { DispatchQueue.main.async { self.showWindow() }; completionHandler() }
    }
    func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction, decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) { decisionHandler(navigationAction.request.url?.isFileURL == true ? .allow : .cancel) }
}

let app = NSApplication.shared
let delegate = AppDelegate()
app.delegate = delegate
app.setActivationPolicy(.regular)
app.run()
