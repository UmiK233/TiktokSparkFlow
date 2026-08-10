package com.umik.tiktoksparkflow.browser;

import com.umik.tiktoksparkflow.config.TiktokSenderConfiguration;
import com.umik.tiktoksparkflow.enums.LoginStatus;
import com.umik.tiktoksparkflow.vo.SendReceiptVO;
import com.umik.tiktoksparkflow.vo.LoginQrVO;
import com.umik.tiktoksparkflow.exception.LoginRequiredException;
import com.umik.tiktoksparkflow.exception.RiskVerificationRequiredException;
import com.umik.tiktoksparkflow.utils.SendReceiptParser;
import com.umik.tiktoksparkflow.utils.TextNormalizer;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.BoundingBox;
import com.microsoft.playwright.options.WaitUntilState;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 封装单用户流程中对抖音创作者中心页面的全部操作。
 * Playwright 页面对象只作为浏览器运行时对象保存在这里，不会进入接口数据对象。
 */
public final class TiktokCreatorClient {
    private static final String CHAT_URL =
            "https://creator.douyin.com/creator-micro/data/following/chat";
    private static final String MESSAGE_SEND_PATH = "/v1/message/send";
    private static final String FRIEND_ENTRY_SELECTOR =
            "#sub-app div[class*='semi-list-item-body'][class*='semi-list-item-body-flex-start']:visible";
    private static final String FRIEND_NAME_SELECTOR =
            "span[class*='item-header-name-']";
    private static final String FRIEND_LIST_LOADING_SELECTOR =
            "#sub-app div[class*='semi-spin']";
    private static final String FRIEND_LIST_END_SELECTOR =
            "#sub-app div[class*='no-more-tip-']";
    private static final List<String> LOGIN_QR_SELECTORS = List.of(
            "img[aria-label='二维码']",
            "img[alt*='二维码']",
            ".login-img-code-wrapper img",
            "[class*='login-img-code'] img",
            "[class*='qrcode'] img",
            "[class*='qr-code'] img",
            ".login-img-code-wrapper canvas",
            "[class*='login-img-code'] canvas",
            "[class*='qrcode'] canvas",
            "[class*='qr-code'] canvas",
            "[class*='qrcode'] svg",
            "[class*='qr-code'] svg");
    private static final int FRIEND_LIST_IDLE_ROUNDS = 5;
    private static final int FRIEND_LIST_STUCK_ROUNDS = 2;

    private final Page page;
    private final TiktokSenderConfiguration properties;
    private final SendReceiptParser receiptParser;

    public TiktokCreatorClient(
            Page page,
            TiktokSenderConfiguration properties,
            SendReceiptParser receiptParser
    ) {
        this.page = page;
        this.properties = properties;
        this.receiptParser = receiptParser;
    }

    public boolean checkAuthentication() {
        requireNoRiskVerification();
        if (workspaceVisible()) {
            return true;
        }
        if (!page.url().startsWith(CHAT_URL)) {
            navigateToChat();
        }
        return waitForWorkspace(properties.getAuthenticationCheckTimeout(), false);
    }

    public void requireAuthentication() {
        if (!checkAuthentication()) {
            throw new LoginRequiredException(
                    "抖音账号未登录，请调用 POST /api/session/login 扫码登录");
        }
    }

    /** 仅检测并上报人工身份验证，不操作验证控件。 */
    public void requireNoRiskVerification() {
        if (riskVerificationVisible()) {
            throw new RiskVerificationRequiredException(
                    "检测到抖音身份验证。请打开浏览器实时画面，手动完成验证后任务将自动继续。");
        }
    }

    public boolean waitForManualRiskVerification() {
        long deadline = System.nanoTime() + properties.getLoginTimeout().toNanos();
        while (System.nanoTime() < deadline) {
            if (!riskVerificationVisible() && checkAuthentication()) {
                return true;
            }
            page.waitForTimeout(1000);
        }
        return false;
    }

    public boolean waitForInteractiveLogin() {
        if (!workspaceVisible() && !page.url().startsWith(CHAT_URL)) {
            navigateToChat();
        }
        System.out.println("正在等待扫码登录，成功后会保存当前浏览器资料");
        return waitForWorkspace(properties.getLoginTimeout(), true);
    }

    /**
     * 返回当前登录二维码截图。调用方应短轮询该方法，以跟随网页自动刷新的二维码。
     */
    public LoginQrVO captureLoginQr() {
        if (workspaceVisible()) {
            return new LoginQrVO(LoginStatus.LOGGED_IN, "", "浏览器页面已确认登录有效");
        }
        if (!page.url().startsWith(CHAT_URL)) {
            navigateToChat();
        }

        long deadline = System.nanoTime() + Duration.ofSeconds(8).toNanos();
        while (System.nanoTime() < deadline) {
            if (workspaceVisible()) {
                return new LoginQrVO(LoginStatus.LOGGED_IN, "", "二维码登录成功，浏览器资料已保存");
            }
            // 只接受具有“二维码”或二维码容器标记的元素。
            // 登录页中也有作品发布宣传卡片，不能把任意方形图片当作二维码。
            for (String selector : LOGIN_QR_SELECTORS) {
                try {
                    Locator qrImage = page.locator(selector).first();
                    if (isQrCandidate(qrImage, true)) {
                        return loginQrResult(qrImage);
                    }
                } catch (RuntimeException ignored) {
                    // 页面刷新二维码期间节点可能短暂失效，继续尝试其他定位方式。
                }
            }
            LoginQrVO nearbyQr = captureQrNearLoginTab();
            if (nearbyQr != null) {
                return nearbyQr;
            }
            page.waitForTimeout(250);
        }
        return new LoginQrVO(LoginStatus.LOGIN_REQUIRED, "", "正在加载登录二维码，请稍后重试");
    }

    /**
     * 登录页也会出现作品发布等宣传卡片，其中的图标同样可能是方形。
     * 因此先以“扫码登录”标题为锚点，只从同一登录卡片中查找带明确标记的二维码。
     */
    private LoginQrVO captureQrNearLoginTab() {
        try {
            Locator loginTab = page.getByText("扫码登录").first();
            if (loginTab.count() == 0 || !loginTab.isVisible()) {
                return null;
            }
            Locator scope = loginTab;
            for (int level = 0; level < 3; level++) {
                scope = scope.locator("xpath=..");
                for (String selector : LOGIN_QR_SELECTORS) {
                    Locator qrImage = scope.locator(selector).first();
                    if (isQrCandidate(qrImage, true)) {
                        return loginQrResult(qrImage);
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // 页面跳转或二维码刷新期间由下一轮轮询继续处理。
        }
        return null;
    }

    private boolean isQrCandidate(Locator candidate, boolean requireQrLikeShape) {
        if (candidate.count() == 0 || !candidate.isVisible()) {
            return false;
        }
        BoundingBox box = candidate.boundingBox();
        if (box == null) {
            return false;
        }
        double ratio = box.width / box.height;
        return box.width >= 70 && box.height >= 70 && ratio >= 0.88 && ratio <= 1.12;
    }

    private LoginQrVO loginQrResult(Locator qrElement) {
        String originalImageData = readQrSource(qrElement);
        if (!originalImageData.isBlank()) {
            return new LoginQrVO(LoginStatus.LOGIN_REQUIRED, originalImageData,
                    "请使用抖音 App 扫码登录（来源：二维码原始数据）");
        }
        byte[] screenshot = qrElement.screenshot();
        String imageData = "data:image/png;base64,"
                + Base64.getEncoder().encodeToString(screenshot);
        return new LoginQrVO(LoginStatus.LOGIN_REQUIRED, imageData,
                "请使用抖音 App 扫码登录（来源：二维码元素截图兜底）");
    }

    /**
     * 优先导出二维码的原始数据，避免截图受页面缩放影响而变模糊。
     * 远程 URL 与 blob URL 在浏览器上下文中读取后也会转为 data URL，
     * 前端无需携带抖音 Cookie 或处理跨域问题。
     */
    private String readQrSource(Locator qrElement) {
        try {
            String src = qrElement.getAttribute("src");
            if (src != null && src.startsWith("data:image/")) {
                return src;
            }
            if (src != null && !src.isBlank()) {
                Object result = page.evaluate("""
                        async (source) => {
                          try {
                            const response = await fetch(source);
                            if (!response.ok) return '';
                            const blob = await response.blob();
                            return await new Promise((resolve) => {
                              const reader = new FileReader();
                              reader.onloadend = () => resolve(typeof reader.result === 'string' ? reader.result : '');
                              reader.onerror = () => resolve('');
                              reader.readAsDataURL(blob);
                            });
                          } catch (_) {
                            return '';
                          }
                        }
                        """, src);
                if (result instanceof String imageData && imageData.startsWith("data:image/")) {
                    return imageData;
                }
            }

            Object tagNameResult = qrElement.evaluate("node => node.tagName.toLowerCase()");
            String tagName = tagNameResult instanceof String value ? value : "";
            if ("canvas".equals(tagName)) {
                Object canvasDataResult = qrElement.evaluate("node => node.toDataURL('image/png')");
                String canvasData = canvasDataResult instanceof String value ? value : "";
                return canvasData == null ? "" : canvasData;
            }
            if ("svg".equals(tagName)) {
                Object svgResult = qrElement.evaluate("node => node.outerHTML");
                String svg = svgResult instanceof String value ? value : "";
                return svg == null ? "" : "data:image/svg+xml;base64,"
                        + Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));
            }
        } catch (RuntimeException ignored) {
            // 资源可能因二维码刷新而失效，改由元素截图作为兼容兜底。
        }
        return "";
    }

    private boolean waitForWorkspace(Duration timeout, boolean revisitChat) {
        long deadline = System.nanoTime() + timeout.toNanos();
        long nextReloadAt = 0;
        while (System.nanoTime() < deadline) {
            if (workspaceVisible()) {
                return true;
            }
            long now = System.nanoTime();
            if (revisitChat && now >= nextReloadAt) {
                try {
                    navigateToChat();
                } catch (RuntimeException ignored) {
                    // 登录完成时页面可能正在跳转，下一轮继续尝试。
                }
                //登录页面刷新时间
                nextReloadAt = now + Duration.ofSeconds(15).toNanos();
            }
            page.waitForTimeout(1000);
        }
        return false;
    }

    private boolean workspaceVisible() {
        try {
            Locator workspace = page.locator("#sub-app");
            return workspace.count() > 0 && workspace.isVisible();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean riskVerificationVisible() {
        try {
            Locator title = page.getByText("身份验证", new Page.GetByTextOptions().setExact(true));
            Locator sms = page.getByText("接收短信验证码", new Page.GetByTextOptions().setExact(true));
            return title.count() > 0 && title.first().isVisible()
                    && sms.count() > 0 && sms.first().isVisible();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public void selectFriend(String targetNickname) {
        requireNoRiskVerification();
        if (selectTargetInTab(targetNickname, "朋友私信")
                || selectTargetInTab(targetNickname, "群消息")) {
            return;
        }
        throw new IllegalStateException("未在“朋友私信”或“群消息”中找到目标：" + targetNickname);
    }

    private boolean selectTargetInTab(String targetNickname, String tabText) {
        try {
            // 好友列表已展示时无需再次点击当前标签；部分页面版本不会将活动标签
            // 暴露为可由 getByText 定位的节点。
            if (!("朋友私信".equals(tabText) && firstFriendEntryVisible())) {
                openConversationTab(tabText);
            }
            if (!waitForConversationListReady(Duration.ofSeconds(60))) {
                return false;
            }
        } catch (RuntimeException ignored) {
            return false;
        }
        resetFriendListToTop();
        long deadline = System.nanoTime() + properties.getFriendScanTimeout().toNanos();
        long idleTimeoutNanos = Duration.ofSeconds(15).toNanos();
        long lastListActivity = System.nanoTime();
        String previousSignature = "";
        while (System.nanoTime() < deadline) {
            Locator entries = friendEntries();
            StringBuilder signature = new StringBuilder();
            for (int index = 0; index < entries.count(); index++) {
                Locator entry = entries.nth(index);
                String name = friendName(entry);
                signature.append(name).append('|');
                if (targetNickname.equals(name)) {
                    entry.click();
                    return true;
                }
            }
            String currentSignature = signature.toString();
            long now = System.nanoTime();
            if (!currentSignature.equals(previousSignature)) {
                lastListActivity = now;
            }
            previousSignature = currentSignature;
            boolean scrolled = scrollFriendList();
            if (scrolled) {
                lastListActivity = now;
            } else if (now - lastListActivity >= idleTimeoutNanos) {
                break;
            }
            page.waitForTimeout(800);
        }
        return false;
    }

    public List<String> listFriends() {
        return listFriendsWithAvatars().friends();
    }

    public FriendListSnapshot listFriendsWithAvatars() {
        LinkedHashSet<String> friends = new LinkedHashSet<>();
        java.util.LinkedHashMap<String, String> avatars = new java.util.LinkedHashMap<>();
        collectFriends(friends, avatars);
        return new FriendListSnapshot(List.copyOf(friends), avatars);
    }

    private void collectFriends(
            LinkedHashSet<String> friends,
            java.util.Map<String, String> avatars
    ) {
        // 聊天页默认已展示朋友私信列表时，活动标签可能无法通过文本 locator 再次定位。
        // 此时直接读取当前列表即可。
        if (!firstFriendEntryVisible()) {
            openConversationTab("朋友私信");
        }
        if (!waitForConversationListReady(Duration.ofSeconds(60))) {
            throw new IllegalStateException("“朋友私信”在 60 秒内没有加载完成");
        }
        resetFriendListToTop();
        long deadline = System.nanoTime() + properties.getFriendScanTimeout().toNanos();
        int idleRounds = 0;
        int stuckRounds = 0;

        while (System.nanoTime() < deadline) {
            Locator entries = friendEntries();
            int sizeBefore = friends.size();
            for (int index = 0; index < entries.count(); index++) {
                Locator entry = entries.nth(index);
                String name = friendName(entry);
                if (!name.isBlank()) {
                    friends.add(name);
                    String avatarUrl = friendAvatar(entry);
                    if (!avatarUrl.isBlank()) {
                        avatars.put(name, avatarUrl);
                    }
                }
            }

            if (friendListEndVisible()) {
                break;
            }

            if (friendListLoadingVisible()) {
                page.waitForTimeout(1200);
            }

            FriendListScrollState scrollState = scrollFriendListWithState();
            if (!scrollState.containerFound()) {
                if (friends.isEmpty()) {
                    throw new IllegalStateException("未找到“朋友私信”列表滚动容器，可能是抖音网页结构已更新");
                }
                break;
            }

            idleRounds = friends.size() > sizeBefore ? 0 : idleRounds + 1;
            stuckRounds = scrollState.moved() ? 0 : stuckRounds + 1;
            if (idleRounds >= FRIEND_LIST_IDLE_ROUNDS
                    || stuckRounds >= FRIEND_LIST_STUCK_ROUNDS) {
                break;
            }
            page.waitForTimeout(1200);
        }

        if (friends.isEmpty()) {
            throw new IllegalStateException("朋友私信列表为空或网页结构已经更新");
        }
    }

    private boolean waitForConversationListReady(Duration readyTimeout) {
        long deadline = System.nanoTime() + readyTimeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (firstFriendEntryVisible()) {
                return true;
            }
            page.waitForTimeout(300);
        }
        return false;
    }

    public SendReceiptVO sendAndConfirm(String message) {
        requireNoRiskVerification();
        Locator input = locateInput();
        int countBefore = countOwnMessages(message);
        typeMessage(input, message);
        Response response = page.waitForResponse(
                item -> item.url().contains(MESSAGE_SEND_PATH)
                        && "POST".equalsIgnoreCase(item.request().method()),
                () -> input.press("Enter"));
        SendReceiptVO receipt = receiptParser.parse(response);
        requireNoRiskVerification();
        if (!receipt.accepted()) {
            throw new IllegalStateException("抖音服务端拒绝了本次发送：" + receipt);
        }
        if (!waitForVisibleConfirmation(input, message, countBefore)) {
            throw new IllegalStateException(
                    "服务端已接受请求，但页面中没有确认到新的己方消息气泡");
        }
        return receipt;
    }

    private void navigateToChat() {
        page.navigate(CHAT_URL, new Page.NavigateOptions()
                .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                .setTimeout(properties.getNavigationTimeout().toMillis()));
    }

    private void openConversationTab(String tabText) {
        for (boolean exact : List.of(true, false)) {
            try {
                Locator candidate = page.getByText(
                        tabText, new Page.GetByTextOptions().setExact(exact));
                if (candidate.count() > 0 && candidate.first().isVisible()) {
                    candidate.first().click();
                    page.waitForTimeout(600);
                    return;
                }
            } catch (RuntimeException ignored) {
                // 当前定位方式不可用，继续尝试备用定位方式。
            }
        }
        if ("朋友私信".equals(tabText)) {
            try {
                Locator fallback = page.locator("xpath=//*[@id='sub-app']/div/div/div[1]/div[2]");
                if (fallback.count() > 0 && fallback.first().isVisible()) {
                    fallback.first().click();
                    page.waitForTimeout(600);
                    return;
                }
            } catch (RuntimeException ignored) {
                // 旧版网页结构兜底失败后使用统一异常提示。
            }
        }
        throw new IllegalStateException("找不到“" + tabText
                + "”标签页，可能是抖音网页结构已更新");
    }

    private boolean firstFriendEntryVisible() {
        try {
            Locator entries = friendEntries();
            return entries.count() > 0 && entries.first().isVisible();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean textVisible(String text) {
        try {
            Locator matches = page.getByText(text, new Page.GetByTextOptions().setExact(true));
            for (int index = 0; index < matches.count(); index++) {
                if (matches.nth(index).isVisible()) {
                    return true;
                }
            }
        } catch (RuntimeException ignored) {
            // 页面切换期间节点可能瞬时失效，按不可见处理。
        }
        return false;
    }

    private boolean clickVisibleFriendName(String targetNickname) {
        try {
            Locator matches = page.getByText(
                    targetNickname,
                    new Page.GetByTextOptions().setExact(true));
            Locator leftmost = null;
            double leftmostX = Double.MAX_VALUE;
            for (int index = 0; index < matches.count(); index++) {
                Locator match = matches.nth(index);
                if (!match.isVisible()) {
                    continue;
                }
                BoundingBox box = match.boundingBox();
                if (box != null && box.x < leftmostX) {
                    leftmost = match;
                    leftmostX = box.x;
                }
            }
            if (leftmost != null) {
                leftmost.click();
                return true;
            }
        } catch (RuntimeException ignored) {
            // 昵称节点不可用时，继续使用列表项扫描方式定位。
        }
        return false;
    }

    private Locator friendEntries() {
        return page.locator(FRIEND_ENTRY_SELECTOR);
    }

    private String friendName(Locator entry) {
        try {
            Locator named = entry.locator(FRIEND_NAME_SELECTOR).first();
            if (named.count() > 0) {
                String value = TextNormalizer.normalize(named.innerText());
                if (!value.isEmpty()) return value;
            }
        } catch (RuntimeException ignored) {
            // 虚拟列表重新渲染时名称节点可能短暂失效，按无效条目处理。
        }
        return "";
    }

    private String friendAvatar(Locator entry) {
        try {
            for (Locator image : entry.locator("img").all()) {
                if (!image.isVisible()) {
                    continue;
                }
                String src = image.getAttribute("src");
                if (src != null && !src.isBlank()) {
                    return src.startsWith("//") ? "https:" + src : src;
                }
            }
        } catch (RuntimeException ignored) {
            // 虚拟列表刷新时头像节点可能短暂失效，昵称仍会正常保存。
        }
        return "";
    }

    private boolean scrollFriendList() {
        return scrollFriendListWithState().moved();
    }

    @SuppressWarnings("unchecked")
    private FriendListScrollState scrollFriendListWithState() {
        Object result = page.evaluate("""
                () => {
                  const first = document.querySelector(
                    "#sub-app div[class*='semi-list-item-body'][class*='semi-list-item-body-flex-start']"
                  );
                  if (!first) return { containerFound: false, moved: false };
                  let node = first.parentElement;
                  while (node) {
                    const style = getComputedStyle(node);
                    const scrollable = /(auto|scroll)/.test(style.overflowY)
                      && node.scrollHeight > node.clientHeight;
                    if (scrollable) {
                      const before = node.scrollTop;
                      node.scrollTop += 800;
                      node.dispatchEvent(new Event('scroll', { bubbles: true }));
                      return { containerFound: true, moved: node.scrollTop > before };
                    }
                    node = node.parentElement;
                  }
                  return { containerFound: false, moved: false };
                }
                """);
        if (!(result instanceof java.util.Map<?, ?> values)) {
            return new FriendListScrollState(false, false);
        }
        return new FriendListScrollState(
                Boolean.TRUE.equals(values.get("containerFound")),
                Boolean.TRUE.equals(values.get("moved")));
    }

    private boolean friendListLoadingVisible() {
        return firstVisible(FRIEND_LIST_LOADING_SELECTOR);
    }

    private boolean friendListEndVisible() {
        return firstVisible(FRIEND_LIST_END_SELECTOR);
    }

    private boolean firstVisible(String selector) {
        try {
            Locator locator = page.locator(selector).first();
            return locator.count() > 0 && locator.isVisible();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private record FriendListScrollState(boolean containerFound, boolean moved) { }

    private void resetFriendListToTop() {
        Object result = page.evaluate("""
                () => {
                  const first = document.querySelector(
                    "#sub-app div[class*='semi-list-item-body'][class*='semi-list-item-body-flex-start']"
                  );
                  if (!first) return false;
                  let node = first.parentElement;
                  while (node) {
                    const style = getComputedStyle(node);
                    const scrollable = /(auto|scroll)/.test(style.overflowY)
                      && node.scrollHeight > node.clientHeight;
                    if (scrollable) {
                      node.scrollTop = 0;
                      node.dispatchEvent(new Event('scroll', { bubbles: true }));
                      return true;
                    }
                    node = node.parentElement;
                  }
                  return false;
                }
                """);
        if (Boolean.TRUE.equals(result)) {
            // 虚拟列表回到顶部后需要一点时间重新生成好友节点。
            page.waitForTimeout(800);
        }
    }

    private Locator locateInput() {
        List<String> selectors = List.of(
                "div[contenteditable='true'][data-placeholder*='消息']",
                "div[contenteditable='true'][aria-label*='消息']",
                "xpath=(//div[@contenteditable='true'])[last()]");
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (System.nanoTime() < deadline) {
            for (String selector : selectors) {
                Locator input = page.locator(selector).first();
                try {
                    if (input.count() > 0 && input.isVisible()) {
                        input.click();
                        return input;
                    }
                } catch (RuntimeException ignored) {
                    // 会话区域可能仍在渲染，继续等待。
                }
            }
            page.waitForTimeout(200);
        }
        throw new IllegalStateException("找不到私信输入框");
    }

    private void typeMessage(Locator input, String message) {
        String[] lines = message.split("\\R", -1);
        for (int index = 0; index < lines.length; index++) {
            input.pressSequentially(lines[index],
                    new Locator.PressSequentiallyOptions().setDelay(35));
            if (index < lines.length - 1) input.press("Shift+Enter");
        }
    }

    private boolean waitForVisibleConfirmation(Locator input, String message, int countBefore) {
        long deadline = System.nanoTime() + properties.getVisibleConfirmationTimeout().toNanos();
        while (System.nanoTime() < deadline) {
            page.waitForTimeout(500);
            if (TextNormalizer.normalize(input.innerText()).isEmpty()
                    && countOwnMessages(message) > countBefore) {
                return true;
            }
        }
        return false;
    }

    private int countOwnMessages(String expectedMessage) {
        Locator rows = page.locator("[class*='box-item-'][class*='is-me']");
        int matched = 0;
        for (int index = 0; index < rows.count(); index++) {
            Locator row = rows.nth(index);
            try {
                Locator textNode = row.locator("pre, [class*='text-']").first();
                String actual = TextNormalizer.normalize(
                        textNode.count() > 0 ? textNode.innerText() : row.innerText());
                if (TextNormalizer.normalize(expectedMessage).equals(actual)) matched++;
            } catch (RuntimeException ignored) {
                // 虚拟列表更新时可能出现瞬态节点，忽略后继续检查。
            }
        }
        return matched;
    }

}
