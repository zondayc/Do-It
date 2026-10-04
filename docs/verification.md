# Do It 0.1.1 验证记录

日期：2026-10-03。开发设备为本机 Apple Silicon Mac，macOS 26.6.2。

## 本次更新验证

- 24 项模型和手势测试在 Asia/Shanghai、America/Los_Angeles 时区全部通过。
- 便签未完成、提前完成、过期、撤销及独立统计；便签不计入任务进度和全勤。
- 模板套用产生独立任务，更新模板不修改任务，单次日期与间隔起点在使用时重新计算。
- 30 种图标与旧备份兼容；无效或重复图标顺序被拒绝。
- 长按只触发一次，短按、滑动、取消清除计时器，长按后的点击被抑制；排序保留所有图标。
- 在本机 WKWebView 的 448px 手机预览中检查任务编辑器、30 种图标、展开与收起、排序面板、时间滚轮和日期日历。
- 界面创建「阅读测试」，选择阅读图标和时间后保存、完成打卡；创建明日便签「取快递测试」，完成后独立计数从 1 待完成变为 1 已完成。
- 界面新建「周末运动」模板，选择周六、周日，再套用创建「运动 20 分钟」任务，模板仍保留。
- 界面将每日开始时间从 00:00 调至 03:00，确认后写入隔离数据。
- 图标前移后顺序写入本地数据，重新打开选择器显示保存后的前 8 个图标。
- 测试目录为 build/qa-0.1.1，没有混入正常用户数据。
- 新版 Mac 编译、签名通过；Android 构建、资源打包及 APK 签名验证通过。

## 0.1.0 基础验证

- Swift 原生 Mac 应用编译与启动，AppKit/WKWebView 页面加载成功。
- Mac 实际界面创建示例任务，数据写入隔离的本机测试目录。
- 逐个打卡后显示 1/3、2/3、3/3，最后全勤连续数、最长数、累计数均为 1。
- 创建每周指定日任务，选择周六、周日；保存后显示「周末」，当前周六任务计入当日安排。
- 检查隔离数据文件：4 个任务、3 条打卡，周循环保存为 `[6,7]`。正常用户数据没有混入测试任务。
- 功能文案移除励志语句，页面直接命名为「今天」「我的任务」「打卡记录」「设置」。
- Logo 的 SVG 与 PNG 已生成并目视检查，Mac `.icns` 与 Android vector 图标由同一设计生成。
- Node.js 逻辑测试通过，覆盖循环、日期边界、历史安排、暂停、归档、补记、撤销、全勤和备份校验。
- Android Java 编译、D8、资源打包、zipalign 和 APK v2/v3 签名验证通过。
- APK 元数据：`com.doit.app`，版本 `0.1.0`，min SDK 26、target SDK 35；未声明 INTERNET 权限。

## 性能检查

0.1.1 相同压力样例五次完整统计耗时为 14.53、16.43、17.47、18.60、22.17ms，中位数 17.47ms。长按采用单次计时器和事件委托，选择器采用原生滚动吸附，没有持续动画循环。

压力数据：200 个任务、730 天、137,400 条完成记录。本机 Node.js 一次计算全部任务的当前、最长、累计数据及全勤数据。

初始完整统计中位数约 78ms。通过复用日期序列与单次遍历，五次样例结果为 10.46、10.83、11.98、12.71、19.78ms，中位数约 12ms。后续重复运行的实际结果以测试输出为准。

打卡采用局部复制；界面更新复用节点，而非替换整个应用。Mac 日期检查使用内存中的每日截止时间，避免后台每 30 秒读取和解析整个历史文件。Android 提醒安排从界面主线程移出。

这些数值仅验证统计模型，不代表 Android 实机点击延迟、帧率、内存占用，也不包含大型 JSON 的原生存储往返。

## 未验证项

Mac 通知授权弹窗、真实通知触发、通知中的打卡与稍后提醒、备份文件选择器往返尚未实际验证。

Android 没有连接设备，未运行 APK。Redmi K90 Pro Max 的安装、WebView、通知权限、准时闹钟权限、前台响铃、熄屏待机、重启恢复、后台限制、备份往返、帧率和内存占用仍需实机验证。没有通过模拟器验证。

手机布局在 Mac WKWebView 的 448px 窗口完成屏幕检查；Android 的实际触屏手势仍需实机验证。Mac 14 兼容目标通过编译，实跑环境仅为本机 macOS 26.6.2。

Mac 包为本机 ad hoc 签名，未做发布公证。Android 包为本地开发签名；密钥保存在构建缓存，未打包进交付源码。

## API 参考

- [Apple UserNotifications](https://developer.apple.com/documentation/usernotifications/unusernotificationcenter)
- [Android Schedule alarms](https://developer.android.com/develop/background-work/services/alarms)
- [Android Load in-app content](https://developer.android.com/develop/ui/views/layout/webapps/load-local-content)
- [Android Service](https://developer.android.com/reference/android/app/Service)

后续优先实机验证提醒可靠性与手机布局，再根据实际记录量评估 SQLite 存储和端到端性能。
