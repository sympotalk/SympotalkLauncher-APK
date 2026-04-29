# Claude Design 비교 생성용 프롬프트 (v3)

아래 프롬프트를 **claude.ai** 또는 **Claude Artifacts**에 붙여넣어 비교용 디자인을 생성하세요.
이미 만든 HTML 미리보기(`docs/mockups/launcher-redesign.html`)와 나란히 비교 가능.

> **변경 이력**:
> - v2: 참석자 버튼이 룸 액션 버튼들과 **동일 행** (콘솔 좌측). 참석자 아이콘 = 태블릿
> - v3: **WiFi · 설정** 두 탭도 새 디자인으로 추가, 하단 탭 동작

---

## 프롬프트 (영문 — Claude Design은 영문이 더 정확)

```
Design a launcher app UI for an event venue Android tablet (LG G Pad 5, 1920×1200 landscape, Chrome 138 WebView). Output as a single self-contained HTML file with inline CSS, no external assets.

═══════════════════════════════════════════════
CONTEXT
═══════════════════════════════════════════════
This is "Sympotalk Launcher" — a kiosk-mode tablet PWA used at medical conferences.
Each event listed has FIVE actions in one horizontal row:
  [ Attendee ] [ Console ] [ Chair ] [ Moderator ] [ Question ]

The first button (Attendee / 참석자) opens the attendee PADS link.
The remaining four are operator links scoped to a specific room.
- Single-room events: all 5 buttons visible inline.
- Multi-room events: only Attendee and a "Room Picker" button (2-button row); the picker opens a modal with 4-button grids per room.
- No-rooms events: only Attendee button (1-button row), with a warning panel below.

The tablet is mounted at the registration desk; staff tap a button to open the right link in WebView.

═══════════════════════════════════════════════
DESIGN SYSTEM (mandatory)
═══════════════════════════════════════════════
Theme: Pure black with neon green (#00FF00) as the single accent color.
Use neon green sparingly — only for:
- Live status indicator (pulsing dot)
- Active state on buttons (border + glow on press)
- Active bottom-nav tab
- Focus ring
- A subtle 2px hairline on the LEFT edge of the Attendee button (to mark it as the row's anchor point)

Color tokens (use as CSS custom properties):
- bg-deep:      #000000
- bg-surface:   #0A0A0A
- bg-elevated:  #141414
- bg-press:     #1A1A1A
- border-soft:  #1A1A1A
- border-base:  #262626
- border-hover: #3A3A3A
- text-1:       #FAFAFA  (primary)
- text-2:       #A3A3A3  (secondary)
- text-3:       #6B6B6B  (tertiary)
- accent:       #00FF00
- accent-fill:  rgba(0,255,0,0.10)
- accent-press: rgba(0,255,0,0.20)
- accent-border:rgba(0,255,0,0.40)
- accent-glow:  0 0 16px rgba(0,255,0,0.45)
- warning:      #FFB800
- danger:       #FF3838

Typography:
- Font: -apple-system, "Pretendard", "Noto Sans KR", "Malgun Gothic", sans-serif
- Event name (display): 22px / 700 / -0.5px
- Page title (h1): 18px / 700
- Section label: 11px / 600 / 0.6px / uppercase
- Button label: 13px / 500
- Caption / meta: 12–13px / 500

Spacing: 8 / 12 / 16 / 20 / 24 px scale.
Radius: 8 / 12 / 16 px.

Iconography: Outline-style SVG, 1.5 stroke weight, 26×26px in action buttons, currentColor (Lucide / Feather style).

═══════════════════════════════════════════════
ICONS (mandatory)
═══════════════════════════════════════════════
- ATTENDEE → "tablet" icon (rounded rectangle 16×20, with horizontal home indicator line near bottom). NOT a "users" icon.
  ```
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"
       stroke-linecap="round" stroke-linejoin="round">
    <rect x="4" y="2" width="16" height="20" rx="2.5" ry="2.5"/>
    <line x1="10" y1="18" x2="14" y2="18"/>
  </svg>
  ```
- CONSOLE → "monitor" (rectangle with stand)
- CHAIR → "mic" (microphone)
- MODERATOR → "clipboard"
- QUESTION → "presentation" (projector screen with descender arrow)

═══════════════════════════════════════════════
SCREEN: Event List (home view)
═══════════════════════════════════════════════
Top to bottom:

1. TOP BAR (52px, full width, bg-surface, bottom border 1px border-soft)
   - Pulsing neon-green dot (8×8, with accent-glow) — 1.6s pulse animation
   - "Sympotalk" wordmark, 17px / 700
   - Middle dot separator "·"
   - "행사 런처" (subtitle), 14px / 500 / text-2
   - flex-spacer
   - "v1.1.0", 11px / 500 / text-3

2. PAGE HEAD (16px padding, flex space-between)
   - LEFT: segmented control with 2 tabs:
     "진행 예정" (active — accent-fill background, accent text, inset 1px accent-border)
     "지난 행사" (inactive — transparent, text-2)
     Wrapper: bg-surface, 1px border-base, 10px radius, 3px padding, 2px gap
   - RIGHT: refresh button — bg-surface, 1px border-base, 8px radius, 7×12 padding, 14×14 refresh icon + "새로고침" label

3. EVENT CARDS (stacked, 16px gap)
   Each card: bg-surface, 1px border-soft, 16px radius, 20px padding.

   Card structure:
   a. HEADER ROW (flex, 12px gap, 12px margin-bottom)
      - Status dot (8×8 circle):
        * LIVE → accent fill + accent-glow + pulse animation (1.6s ease-out infinite)
        * UPCOMING → 1.5px text-3 outline (no fill)
        * ENDED → bg text-disabled (#404040)
      - Event name (22px/700, ellipsis truncate, flex:1)
      - Time stamp (12px/500/text-3, e.g. "3시간 전" / "방금 전" / "어제")

   b. META ROW (flex, 16px gap, wrap, 13px/500/text-2, 16px margin-bottom)
      - 📅 calendar (13×13 stroke-2 text-3) + date "04.15 ~ 04.18"
      - 🏢 building + agency "메디컬코어"
      - 👤 user + manager "김매니저"

   c. ROOM LABEL (only when single-room or archived single-room)
      - 11/600/uppercase/text-3 with 4×4 dot prefix and room name in text-2 normal-case
      - 12px margin-bottom
      - Multi-room and no-room cards SKIP this line.

   d. ACTION ROW — depends on case:

      CASE A — single room (5-button grid in one row):
        ```
        display: grid;
        grid-template-columns: repeat(5, 1fr);
        gap: 8px;
        ```
        Buttons in order, ALL using the same .action-btn style:
          1. ATTENDEE (tablet icon) — has a 2px accent-border vertical hairline at left edge
          2. CONSOLE (monitor)
          3. CHAIR (mic)
          4. MODERATOR (clipboard)
          5. QUESTION (presentation)
        Each button:
          - Vertical stack (icon top, label below, 8px gap)
          - 92px min-height, 16px / 12px padding, bg-elevated, 1px border-base, 12px radius
          - Color text-2, font 13px/500
          - 26×26 icon (currentColor, stroke 1.5)
          - Active state: bg accent-press, border accent-border, color accent, scale(0.97), inset 1px accent-border ring + accent-glow

      CASE B — multiple rooms (2-button grid in one row):
        ```
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 8px;
        ```
        Buttons:
          1. ATTENDEE (tablet icon, same .action-btn + left hairline)
          2. ROOM PICKER:
             - Same dimensions but with DASHED border (1px dashed border-base)
             - Vertical stack: 26×26 building icon + "룸 선택" label (13/600) + sublabel "3개 룸 — 콘솔 / 좌장 / 사회자 / 질문" (11/500/text-3)
             - On tap, opens MODAL (see below). Hover changes border to solid border-hover.
             - Active: solid accent-border, accent fill, scale(0.97)

      CASE C — no rooms (1-button grid):
        ```
        display: grid;
        grid-template-columns: 1fr;
        gap: 8px;
        ```
        Single ATTENDEE button only.
        BELOW the row: warning panel (12px margin-top):
          - bg-elevated, 1px border-base, 12px radius, 12×16 padding
          - 16×16 alert-triangle icon (warning color, stroke 2) at top-left
          - 2-line text:
            "룸이 등록되지 않았습니다" (warning, 13/600)
            "sympotalk 관리자에서 룸을 추가하면 콘솔 · 좌장 · 사회자 · 질문 링크가 자동 생성됩니다." (text-2, 12/500)

   e. ENDED CARDS get opacity: 0.55 on the whole card. They appear ONLY in the "지난 행사" pane.
      In archive, the row label may show "{룸명} (아카이브)".

4. BOTTOM NAV (64px, full width, bg-surface, top border 1px border-soft)
   3 tabs: 행사 / WiFi / 설정 — vertical stack: icon (22×22) + label (11/500), 4px gap.
   Inactive: text-3.
   Active: text accent, with a 28×2px accent underline at the very top edge (centered, 0 0 8px accent border-radius glow), bottom-radius 2px.

═══════════════════════════════════════════════
SCREEN: WiFi · QR Scanner View (bottom nav 2nd tab)
═══════════════════════════════════════════════
PAGE HEAD:
- Title "WiFi · QR 스캐너" (18/700)
- Outline button "스캔 재시작" (14×14 refresh icon, bg-surface, border-base, 8px radius)

SECTION 1 — QR Scanner card:
- Card (bg-surface, border-soft, 16px radius, 20px padding, 16px margin-bottom)
- Section label "QR 스캐너"
- Scanner frame: max-width 520px, aspect-ratio 4/3, bg #050505, 1px border-base, 16px radius
  Inside the frame:
  - Camera placeholder (subtle 45deg striped background) with camera icon + "카메라 준비 중..." text
  - 4 corner brackets in accent color (#00FF00), 36×36, 2px stroke, 8px corner radius, with drop-shadow glow
  - Animated scan line: 2px tall, full-width gradient (transparent→accent→transparent), box-shadow glow,
    keyframe animation moving top↔bottom over 2.4s
- Below the frame: hint text "WiFi QR 코드를 카메라에 비춰주세요. 인식되면 자동으로 연결을 시도합니다." (the words "QR 코드" highlighted in accent)

SECTION 2 — Quick actions card:
- Section label "빠른 동작"
- 3-button grid (same .action-btn vertical style as room buttons):
  1. "WiFi 새로고침" — refresh icon
  2. "시스템 설정" — gear icon
  3. "기본 비밀번호" — lock icon

SECTION 3 — Networks card:
- Section label "주변 네트워크"
- Vertical list of WiFi items, each:
  - Container: bg-elevated, 1px border-base, 12px radius, 16px padding
  - Layout: flex with 12px gap, [22×22 wifi-signal icon] [info column flex-1] [action button or badge]
  - Info column:
    - SSID (14/600/text-1, ellipsis)
    - Meta line (11/500/text-3, 12px gap):
      - Lock icon + "WPA2" (or "개방")
      - IP address (only if connected)
      - Signal "·85 dBm"
  - Right side:
    - If connected: green pill "연결됨" (accent text, accent-fill bg, accent-border, 11/700/uppercase, 4×10 padding, 6px radius)
    - Otherwise: outline "연결" button (1px border-base, 8/14 padding, 12/600)
- The connected item gets accent-border on the container + accent-fill background + accent-stroke wifi icon
- Show 4 sample networks:
  1. "Sympotalk-Event-5G" (CONNECTED, WPA2, 192.168.1.42, ·85 dBm)
  2. "Convention-Hall-Public" (WPA2, ·67 dBm)
  3. "Hotel-Guest" (개방, ·54 dBm)
  4. "iptime-2.4G" (WPA2, ·92 dBm 약함)

SECTION 4 — Network diagnostic card:
- Section label "네트워크 진단"
- 3-button grid: "Ping 테스트" / "Speed Test" / "전체 진단" (same .action-btn style)
- Result panel below: 2-column grid
  - Each cell: bg-elevated, border-base, 12px radius, 16px padding
  - Label (11/600/uppercase/text-3) + value (24/700/accent + small unit)
  - Show: Latency 12ms / Download 87.4 Mbps

═══════════════════════════════════════════════
SCREEN: Settings View (bottom nav 3rd tab)
═══════════════════════════════════════════════
PAGE HEAD:
- Title "설정" (18/700)

SECTION 1 — Version Hero (full-width row):
- Card-like container (bg-surface, border-soft, 16px radius, 20px padding, 16px margin-bottom)
- Layout: flex with 20px gap
  - LEFT: 56×56 icon container with bg accent-fill, border accent-border, 14px radius
    - Inside: 28×28 box/cube icon (Lucide "package"), accent stroke, drop-shadow glow
  - MIDDLE (flex-1):
    - Version number "1.1.0" (28/700/-1px) + small "installed" suffix (13/500/text-3)
    - Build line: "build 39 · 2026-04-29 · com.sympotalk.launcher" (12/500/text-3, margin-top 6px)
  - RIGHT: primary CTA button "업데이트 확인"
    - 10×18 padding, accent-fill bg, accent-border, accent text, 10px radius, 13/600
    - 14×14 refresh icon prefix
    - Hover: accent-press bg
    - Active: scale(0.97) + accent-glow

SECTION 2 — App Info card:
- Section label "앱 정보"
- 2-column grid (8px gap), each row:
  - bg-elevated, border-base, 12px radius, 12×16 padding
  - Label (11/500/text-3/uppercase) + value (13/600/text-1)
- Show: Build Type "Release · Signed", Target SDK "Android 14 (API 34)", Min SDK "Android 9 (API 28)", Last Update Check "방금 전"

SECTION 3 — Recent Logs card:
- Section label row with title left "최근 로그" + small outline buttons right "복사" / "지우기"
- Log list (max-height 320px, overflow-y auto):
  Each log: 3-column grid [8px dot] [60px time] [1fr msg]
  - Dot color: error=#FF3838 / warn=#FFB800 / info=text-3
  - Time: 11px/text-3
  - Msg: 12/500/text-1, with optional explanation below in 11/500/text-2
- Show 5 sample logs (mix of info/warn/error):
  - 15:42 INFO "앱 시작" / "런처가 정상적으로 실행됐습니다."
  - 15:38 WARN "Page Freeze — 시스템이 앱을 일시 정지" / "시스템이 메모리 확보를 위해 앱을 정지시켰습니다."
  - 15:30 ERROR "이전 세션 비정상 종료 감지 — 마지막 이벤트: visibility:hidden" / "이전에 앱이 비정상적으로 종료됐습니다. 메모리 부족이나 시스템 강제 종료가 원인일 수 있습니다."
  - 15:30 INFO "WiFi 연결됨 — Sympotalk-Event-5G" / "192.168.1.42 / 85 dBm"
  - 14:55 INFO "행사 목록 새로고침 — 3건 로드"

SECTION 4 — WiFi Default Password card:
- Section label "WiFi 기본 비밀번호"
- Layout: 2-column [info-row flex-1] [vertical button stack]
- Info row: label "현재 값", value "••••••••••••" (monospace, 2px letter-spacing)
- Right side: 2 stacked outline buttons "변경" / "기본값 복원"

SECTION 5 — Danger Zone:
- Container: bg-surface, 1px border with rgba(255,56,56,0.25), 16px radius, 20px padding
- Section label "위험 영역" — using danger color #FF3838 (text + dot)
- Body: flex space-between
  - Left text: "캐시 및 로그 초기화" (bold) + description "저장된 행사 정보, 로그, WiFi 자동 연결 기록을 모두 삭제합니다."
  - Right: outline button "초기화" — danger color text, transparent bg, 1px border rgba(255,56,56,0.3)

═══════════════════════════════════════════════
TAB SWITCHING (working JS)
═══════════════════════════════════════════════
Bottom nav must actually switch which view is visible:
- Three buttons "행사" / "WiFi" / "설정", each with data-view + data-title attributes
- Clicking changes which .view has the .active class (display block)
- Top bar's subtitle text updates to match (data-title)
- Scroll position resets to top on switch

═══════════════════════════════════════════════
SCREEN: Room Picker Modal (CASE B trigger on HOME view)
═══════════════════════════════════════════════
Backdrop: rgba(0,0,0,0.85) + 4px backdrop-blur.
Modal: max-width 640px, bg-elevated, 1px border-base, 16px radius, 20px padding, max-height calc(100% - 40px) with scroll.
Drop shadow: 0 20px 80px rgba(0,0,0,0.6).

Header:
- Title "룸 선택" (18px/700)
- Subtitle "<event name> — 3개의 룸" (12px/500/text-3)
- Close button (×, 32×32, hover bg-press)

Body: a list of room sections, separated by 1px border-soft.
Each room section:
- ROOM label "ROOM N · <name>" (11/600/uppercase/text-3 + name in text-2 normal)
- 4-button grid (Console / Chair / Moderator / Question) — same .action-btn style as main view
  ```
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
  ```
- If a room is missing certain tokens, show that button with opacity 0.35 + pointer-events none.

═══════════════════════════════════════════════
INTERACTION DETAILS
═══════════════════════════════════════════════
- All transitions: 0.12s ease for color/border/background, 0.08s for transform.
- Press effect on every button: scale(0.97) + accent-glow + border accent-border + text accent.
- LIVE dot pulse: 1.6s keyframes, ring expanding from 0 to 8px (alpha 0.6 → 0).
- Active bottom-nav has a small green underline glow.
- Modal animates in: opacity 0→1 (200ms), modal scale 0.96→1 (200ms ease-out).
- Attendee button's left-edge hairline: 2px wide accent-border, 12px top/bottom inset, brightens to full accent on press.

═══════════════════════════════════════════════
DATA TO RENDER
═══════════════════════════════════════════════

Pane "진행 예정":
1. LIVE — "2026 대한피부과학회 춘계학술대회"
   Date "04.15 ~ 04.18", agency "메디컬코어", manager "김매니저", time "3시간 전"
   Single room "그랜드볼룸" — render CASE A (5 buttons inline)
2. LIVE — "2026 추계 대규모 학술대회"
   Date "11.20 ~ 11.22", agency "시포스", manager "박매니저", time "방금 전"
   3 rooms — render CASE B (참석자 + 룸 선택)
3. UPCOMING — "2026 동계 워크샵"
   Date "12.05 ~ 12.06", agency "메디컬코어", time "어제"
   No rooms — render CASE C (참석자 only + warning panel)

Pane "지난 행사":
1. ENDED — "2025 동계학술대회" (12.05 ~ 12.07, 메디컬코어), single room → CASE A with "(아카이브)" suffix
2. ENDED — "2025 추계 대한신경과학회" (10.15 ~ 10.18), 2 rooms → CASE B
ENDED cards have 0.55 opacity.

═══════════════════════════════════════════════
CONSTRAINTS
═══════════════════════════════════════════════
- Single self-contained HTML file. No external CSS/JS/images.
- All icons inline SVG.
- Korean copy as written above.
- Optimize for 1920×1200 landscape but should also degrade gracefully at narrow widths (5-col grid → 3-col @ 720px → 2-col @ 420px).
- Show BOTH home-tab panes (working segmented control with small JS toggle).
- Show ALL THREE BOTTOM-NAV TABS as actual switchable views (행사 / WiFi / 설정), not just stub placeholders. The default active view is "행사".
- Show the room-picker modal in an OPEN state alongside or below the main view, so the design is visible without interaction.
- Use CSS custom properties (--bg-deep, --accent, etc.) so design tokens are inspectable.
- Plain ES5-style JS (var, function) — no arrow functions, no const/let, no optional chaining. Tablet runtime is Chrome 138 (modern) but the production codebase is ES5; preserve consistency.

Output the HTML directly in an artifact. Include detailed comments labeling each section.
```

---

## 사용법

### 1. 내가 만든 미리보기 (이 레포)
```
docs/mockups/launcher-redesign.html
```
탐색기에서 더블클릭 → 브라우저로 열림.

또는 로컬 서버:
```bash
cd "C:/Users/이경민/source/repos/sympopad_web"
python -m http.server 8080
# http://localhost:8080/docs/mockups/launcher-redesign.html
```

### 2. Claude Design 버전 생성
- https://claude.ai 새 채팅
- 위 영문 프롬프트 전체 붙여넣기
- Claude가 HTML artifact를 생성해서 미리보기 패널에 렌더링됨
- 다운로드 → 같은 폴더에 `claude-design-version.html`로 저장

### 3. 비교 체크포인트
- [ ] 5버튼이 한 줄에 균등 분할되는 시각 무게감
- [ ] 참석자 좌측 형광 hairline의 적절한 강도 (너무 강한가? 너무 약한가?)
- [ ] 태블릿 아이콘이 다른 4개와 시각적 일관성 있는지 (모니터/마이크/클립보드/스크린과의 톤)
- [ ] 1920×1200 풀스크린에서 행사 카드의 시각적 중량 (덩어리감 vs 가벼움)
- [ ] 다중 룸 케이스에서 2버튼 행의 무게 균형 (참석자 vs 룸 선택)
- [ ] 빈 룸 경고 패널의 톤
- [ ] 다크 환경에서의 대비 (WCAG AA: 4.5:1)
- [ ] LIVE 도트 펄스 애니메이션의 자연스러움
