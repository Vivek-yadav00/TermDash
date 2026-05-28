package com.termdash.ui;

import java.io.IOException;
import java.text.DecimalFormat;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.googlecode.lanterna.Symbols;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;
import com.termdash.service.CryptoService;
import com.termdash.service.EnvironmentService;
import com.termdash.service.SystemMonitor;

public class Dashboard {

    private final Screen screen;
    private final TextGraphics tg;
    private final SystemMonitor sysMon;
    private final EnvironmentService envService;
    private final CryptoService cryptoService;

    private static final TextColor BG = TextColor.ANSI.BLACK;
    private static final TextColor TXT = TextColor.ANSI.GREEN;
    private static final TextColor HI = TextColor.ANSI.GREEN_BRIGHT;
    private static final TextColor ALERT = TextColor.ANSI.RED_BRIGHT;
    private static final TextColor DIM = TextColor.ANSI.BLACK_BRIGHT;

    private final DecimalFormat df = new DecimalFormat("0.0");

    private List<SystemMonitor.ProcessMetric> cachedProcesses = Collections.emptyList();
    private long lastProcessUpdate = 0;

    // ── View state ──
    private enum View { MENU, SYSTEM_VITALS, NETWORK_ENV, PARASITE_RADAR, CRYPTO_TICKER, ALL_TOGETHER }
    private View currentView = View.MENU;
    private int selectedButton = 0; // 0-4 for the menu items

    public Dashboard() throws IOException {
        DefaultTerminalFactory factory = new DefaultTerminalFactory()
                .setInitialTerminalSize(new TerminalSize(120, 38));
        Terminal terminal;
        try {
            terminal = factory.createTerminal();
        } catch (IOException e) {
            // Fallback to Swing terminal emulator on Windows console issues or headless mode fallback
            terminal = factory.setPreferTerminalEmulator(true).createTerminal();
        }

        screen = new TerminalScreen(terminal);
        screen.startScreen();
        screen.setCursorPosition(null);
        tg = screen.newTextGraphics();

        sysMon = new SystemMonitor();
        envService = new EnvironmentService();
        cryptoService = new CryptoService();
    }

    public void run() throws IOException, InterruptedException {
        while (true) {
            sysMon.updateNetworkSpeeds();

            switch (currentView) {
                case MENU            -> drawMenu();
                case SYSTEM_VITALS   -> drawSystemVitalsView();
                case NETWORK_ENV     -> drawNetworkEnvView();
                case PARASITE_RADAR  -> drawParasiteRadarView();
                case CRYPTO_TICKER   -> drawCryptoTickerView();
                case ALL_TOGETHER    -> drawAllTogetherView();
            }
            screen.refresh();

            KeyStroke key = screen.pollInput();
            if (key != null && handleInput(key)) break;

            Thread.sleep(120);
        }
        screen.stopScreen();
    }

    // ═══════════════════════════════════════════════════
    //  INPUT HANDLING
    // ═══════════════════════════════════════════════════

    /** @return true if the app should quit */
    private boolean handleInput(KeyStroke key) {
        // ── q always quits ──
        Character ch = key.getCharacter();
        if (ch != null && ch == 'q') return true;

        // ── Escape: back to menu, or quit from menu ──
        if (key.getKeyType() == KeyType.Escape) {
            if (currentView == View.MENU) return true;
            currentView = View.MENU;
            return false;
        }

        // ── Number keys 1-5 work from any screen ──
        if (ch != null) {
            switch (ch) {
                case '1' -> { currentView = View.SYSTEM_VITALS;  return false; }
                case '2' -> { currentView = View.NETWORK_ENV;    return false; }
                case '3' -> { currentView = View.PARASITE_RADAR; return false; }
                case '4' -> { currentView = View.CRYPTO_TICKER;  return false; }
                case '5' -> { currentView = View.ALL_TOGETHER;   return false; }
            }
        }

        // ── Menu-only: arrow keys + Enter ──
        if (currentView == View.MENU) {
            switch (key.getKeyType()) {
                case ArrowUp -> {
                    if (selectedButton == 4) {
                        selectedButton = 2; // Jump up from All Together to Parasite Radar
                    } else if (selectedButton >= 2) {
                        selectedButton -= 2;
                    }
                }
                case ArrowDown -> {
                    if (selectedButton == 2 || selectedButton == 3) {
                        selectedButton = 4; // Jump down to All Together
                    } else if (selectedButton < 2) {
                        selectedButton += 2;
                    }
                }
                case ArrowLeft -> {
                    if (selectedButton == 1 || selectedButton == 3) {
                        selectedButton -= 1;
                    }
                }
                case ArrowRight -> {
                    if (selectedButton == 0 || selectedButton == 2) {
                        selectedButton += 1;
                    }
                }
                case Enter -> currentView = View.values()[selectedButton + 1]; // +1 because MENU is index 0
                default -> {}
            }
        } else {
            // Backspace also goes back from detail views
            if (key.getKeyType() == KeyType.Backspace) {
                currentView = View.MENU;
            }
        }
        return false;
    }

    // ═══════════════════════════════════════════════════
    //  MENU SCREEN
    // ═══════════════════════════════════════════════════

    private void drawMenu() {
        TerminalSize size = screen.getTerminalSize();
        int width  = size.getColumns();
        int height = size.getRows();

        tg.setBackgroundColor(BG);
        tg.setForegroundColor(TXT);
        tg.fill(' ');

        drawBox(0, 0, width, height - 1, " TERMDASH ", HI);

        // ── Title ──
        String title    = ">>> TERMDASH CONTROL CENTER <<<";
        String subtitle = "Use arrow keys or press 1-5 to select a module";
        tg.setForegroundColor(HI);
        centerText(0, width, 2, title);
        tg.setForegroundColor(DIM);
        centerText(0, width, 4, subtitle);

        // ── Grid setup ──
        int btnWidth   = Math.min(50, (width - 14) / 2);
        int btnHeight  = Math.min(9, (height - 18) / 3);
        int totalWidth = btnWidth * 2 + 4;
        int startX     = (width - totalWidth) / 2;
        int startY     = 6;

        String[] labels = { " SYSTEM VITALS ", " NETWORK & ENV ", " PARASITE RADAR ", " CRYPTO TICKER " };
        String[] desc   = {
            "CPU | RAM | DISK | TEMP | BATTERY",
            "OS | UPTIME | FAN | NET SPEED",
            "TOP CPU PROCESSES",
            "BTC | ETH | SOL | DOGE"
        };
        String[] keys  = { "[ 1 ]", "[ 2 ]", "[ 3 ]", "[ 4 ]" };
        String[] icons = { ":: CPU ::", ":: NET ::", ":: PRC ::", ":: BTC ::" };

        // Draw top 4 buttons (2x2 grid)
        for (int i = 0; i < 4; i++) {
            int col = i % 2;
            int row = i / 2;
            int bx  = startX + col * (btnWidth + 4);
            int by  = startY + row * (btnHeight + 1);

            boolean sel = (i == selectedButton);
            drawBox(bx, by, btnWidth, btnHeight, labels[i], sel ? HI : DIM);

            tg.setForegroundColor(sel ? ALERT : TXT);
            centerText(bx, bx + btnWidth, by + 1, keys[i]);

            tg.setForegroundColor(sel ? HI : TXT);
            centerText(bx, bx + btnWidth, by + btnHeight / 2, icons[i]);

            tg.setForegroundColor(sel ? HI : DIM);
            centerText(bx, bx + btnWidth, by + btnHeight - 2, desc[i]);

            if (sel) {
                tg.setForegroundColor(HI);
                tg.putString(bx + 2, by + btnHeight / 2, ">");
                tg.putString(bx + btnWidth - 3, by + btnHeight / 2, "<");
            }
        }

        // Draw 5th button: ALL TOGETHER (bottom center, spanning full width)
        int b5x = startX;
        int b5y = startY + 2 * (btnHeight + 1);
        int b5w = totalWidth;
        boolean sel5 = (selectedButton == 4);

        drawBox(b5x, b5y, b5w, btnHeight, " ALL TOGETHER ", sel5 ? HI : DIM);

        tg.setForegroundColor(sel5 ? ALERT : TXT);
        centerText(b5x, b5x + b5w, b5y + 1, "[ 5 ]");

        tg.setForegroundColor(sel5 ? HI : TXT);
        centerText(b5x, b5x + b5w, b5y + btnHeight / 2, ":: SHOW ALL MODULES TOGETHER ::");

        tg.setForegroundColor(sel5 ? HI : DIM);
        centerText(b5x, b5x + b5w, b5y + btnHeight - 2, "View all dashboard panels simultaneously in a grid layout");

        if (sel5) {
            tg.setForegroundColor(HI);
            tg.putString(b5x + 4, b5y + btnHeight / 2, ">>>");
            tg.putString(b5x + b5w - 7, b5y + btnHeight / 2, "<<<");
        }

        // ── Footer ──
        tg.setForegroundColor(DIM);
        centerText(0, width, height - 3, "ARROWS = NAVIGATE | ENTER = OPEN | 1-5 = QUICK OPEN | q = QUIT");

        String time = "ONLINE | " + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        tg.setForegroundColor(TXT);
        centerText(0, width, height - 1, time);
    }

    // ═══════════════════════════════════════════════════
    //  SYSTEM VITALS  (full screen)
    // ═══════════════════════════════════════════════════

    private void drawSystemVitalsView() {
        TerminalSize size = screen.getTerminalSize();
        int width  = size.getColumns();
        int height = size.getRows();

        tg.setBackgroundColor(BG);
        tg.setForegroundColor(TXT);
        tg.fill(' ');

        drawBox(0, 0, width, height - 1, " SYSTEM VITALS ", HI);

        int barWidth = width - 12;
        int x = 5;

        // ── Progress bars ──
        drawProgressBar(x, 3, barWidth, "CPU USAGE", sysMon.getCpuLoad());
        drawProgressBar(x, 6, barWidth, "RAM USAGE", sysMon.getMemoryUsage());
        drawProgressBar(x, 9, barWidth, "STORAGE  ", sysMon.getStorageUsage());

        // ── Details section ──
        int detailY = 13;
        drawBox(2, detailY - 1, width - 4, height - detailY - 2, " DETAILS ", TXT);

        // Left column
        double temp = sysMon.getCpuTemperature();
        putLabel(x, detailY + 1, "CPU TEMP   : ");
        tg.setForegroundColor(temp > 75 ? ALERT : HI);
        tg.putString(x + 13, detailY + 1, temp <= 0 ? "N/A" : df.format(temp) + " C");

        putLabel(x, detailY + 3, "BATTERY    : ");
        tg.setForegroundColor(HI);
        tg.putString(x + 13, detailY + 3, sysMon.getBatteryInfo());

        putLabel(x, detailY + 5, "PROCESSES  : ");
        tg.setForegroundColor(HI);
        tg.putString(x + 13, detailY + 5, String.valueOf(sysMon.getProcessCount()));

        putLabel(x, detailY + 7, "THREADS    : ");
        tg.setForegroundColor(HI);
        tg.putString(x + 13, detailY + 7, String.valueOf(sysMon.getThreadCount()));

        // Right column
        int rx = width / 2 + 2;
        long totalMem = sysMon.getTotalMemory();
        long usedMem  = sysMon.getUsedMemory();

        putLabel(rx, detailY + 1, "TOTAL RAM  : ");
        tg.setForegroundColor(HI);
        tg.putString(rx + 13, detailY + 1, formatBytesLong(totalMem));

        putLabel(rx, detailY + 3, "USED RAM   : ");
        tg.setForegroundColor(HI);
        tg.putString(rx + 13, detailY + 3, formatBytesLong(usedMem));

        putLabel(rx, detailY + 5, "FREE RAM   : ");
        tg.setForegroundColor(HI);
        tg.putString(rx + 13, detailY + 5, formatBytesLong(totalMem - usedMem));

        putLabel(rx, detailY + 7, "FAN SPEED  : ");
        tg.setForegroundColor(HI);
        tg.putString(rx + 13, detailY + 7, sysMon.getFanSpeed());

        drawFooter(width, height, "SYSTEM VITALS", "1");
    }

    // ═══════════════════════════════════════════════════
    //  NETWORK & ENV  (full screen)
    // ═══════════════════════════════════════════════════

    private void drawNetworkEnvView() {
        TerminalSize size = screen.getTerminalSize();
        int width  = size.getColumns();
        int height = size.getRows();

        tg.setBackgroundColor(BG);
        tg.setForegroundColor(TXT);
        tg.fill(' ');

        drawBox(0, 0, width, height - 1, " NETWORK & ENV ", HI);

        int x = 5;

        // ── System info section ──
        drawBox(2, 2, width - 4, 10, " SYSTEM INFO ", TXT);

        putLabel(x, 4, "OS         : ");
        tg.setForegroundColor(HI);
        tg.putString(x + 13, 4, sysMon.getOsName());

        putLabel(x, 6, "UPTIME     : ");
        tg.setForegroundColor(HI);
        tg.putString(x + 13, 6, sysMon.getUptime());

        putLabel(x, 8, "GIT BRANCH : ");
        tg.setForegroundColor(HI);
        tg.putString(x + 13, 8, envService.getGitBranch());

        putLabel(x, 10, "WEATHER    : ");
        tg.setForegroundColor(HI);
        tg.putString(x + 13, 10, envService.getWeather());

        // ── Network section ──
        int netY = 14;
        drawBox(2, netY - 1, width - 4, height - netY - 2, " NETWORK SPEED ", TXT);

        long downSpeed = sysMon.getNetworkDownloadSpeed();
        long upSpeed   = sysMon.getNetworkUploadSpeed();

        putLabel(x, netY + 1, "DOWNLOAD   : ");
        tg.setForegroundColor(HI);
        tg.putString(x + 13, netY + 1, formatBytes(downSpeed) + "/s");

        putLabel(x, netY + 3, "UPLOAD     : ");
        tg.setForegroundColor(HI);
        tg.putString(x + 13, netY + 3, formatBytes(upSpeed) + "/s");

        putLabel(x, netY + 5, "FAN SPEED  : ");
        tg.setForegroundColor(HI);
        tg.putString(x + 13, netY + 5, sysMon.getFanSpeed());

        // Visual speed bars
        int barWidth = width - 12;
        double maxSpeed = 10_000_000.0; // 10 MB/s reference
        drawProgressBar(x, netY + 8,  barWidth, "DOWN RATE", Math.min(1.0, downSpeed / maxSpeed));
        drawProgressBar(x, netY + 11, barWidth, "UP RATE  ", Math.min(1.0, upSpeed / maxSpeed));

        drawFooter(width, height, "NETWORK & ENV", "2");
    }

    // ═══════════════════════════════════════════════════
    //  PARASITE RADAR  (full screen)
    // ═══════════════════════════════════════════════════

    private void drawParasiteRadarView() {
        TerminalSize size = screen.getTerminalSize();
        int width  = size.getColumns();
        int height = size.getRows();

        tg.setBackgroundColor(BG);
        tg.setForegroundColor(TXT);
        tg.fill(' ');

        drawBox(0, 0, width, height - 1, " PARASITE RADAR ", HI);

        // Refresh process list
        if (System.currentTimeMillis() - lastProcessUpdate > 2000) {
            cachedProcesses = sysMon.getTopProcesses(15);
            lastProcessUpdate = System.currentTimeMillis();
        }

        int x = 5;

        // Header
        tg.setForegroundColor(ALERT);
        tg.putString(x, 3, "[!] TOP CPU CONSUMERS");

        // Table header
        tg.setForegroundColor(DIM);
        tg.putString(x, 5, String.format("%-5s  %-35s  %-10s  %s", "RANK", "PROCESS NAME", "CPU %", "USAGE"));

        // Separator line
        tg.setForegroundColor(DIM);
        for (int i = x; i < width - 5; i++) {
            tg.setCharacter(i, 6, Symbols.SINGLE_LINE_HORIZONTAL);
        }

        // Process rows
        int barStartX = x + 54;
        int barW = Math.max(0, width - barStartX - 6);

        for (int i = 0; i < cachedProcesses.size(); i++) {
            var p = cachedProcesses.get(i);
            int row = 8 + i;
            if (row >= height - 4) break;

            // Rank + name + CPU percentage
            tg.setForegroundColor(i == 0 ? ALERT : TXT);
            String entry = String.format("#%-4d  %-35s  %6s%%",
                    i + 1, truncate(p.getName(), 35), df.format(p.getCpuUsage()));
            tg.putString(x, row, entry);

            // Mini usage bar
            if (barW > 0) {
                int filled = (int) (barW * Math.min(p.getCpuUsage(), 100.0) / 100.0);
                for (int j = 0; j < barW; j++) {
                    tg.setForegroundColor(j < filled ? (i == 0 ? ALERT : HI) : DIM);
                    tg.setCharacter(barStartX + j, row, Symbols.BLOCK_SOLID);
                }
            }
        }

        drawFooter(width, height, "PARASITE RADAR", "3");
    }

    // ═══════════════════════════════════════════════════
    //  CRYPTO TICKER  (full screen)
    // ═══════════════════════════════════════════════════

    private void drawCryptoTickerView() {
        TerminalSize size = screen.getTerminalSize();
        int width  = size.getColumns();
        int height = size.getRows();

        tg.setBackgroundColor(BG);
        tg.setForegroundColor(TXT);
        tg.fill(' ');

        drawBox(0, 0, width, height - 1, " CRYPTO TICKER ", HI);

        Map<String, Double> prices = cryptoService.getPrices();
        String[] coins = { "bitcoin", "ethereum", "solana", "dogecoin" };
        String[] sym   = { "BTC ", "ETH ", "SOL ", "DOGE" };
        String[] names = { "Bitcoin", "Ethereum", "Solana", "Dogecoin" };

        int x = 5;

        // Header
        tg.setForegroundColor(HI);
        tg.putString(x, 3, "LIVE CRYPTOCURRENCY PRICES");
        tg.setForegroundColor(DIM);
        tg.putString(x, 5, "Data from CoinGecko API  |  Auto-refresh every 60s");

        // Table header
        tg.setForegroundColor(DIM);
        tg.putString(x, 8, String.format("%-8s  %-15s  %15s", "SYMBOL", "NAME", "PRICE (USD)"));

        // Separator
        for (int i = x; i < width - 5; i++) {
            tg.setCharacter(i, 9, Symbols.SINGLE_LINE_HORIZONTAL);
        }

        // Coin rows
        for (int i = 0; i < coins.length; i++) {
            Double price = prices.get(coins[i]);
            int row = 11 + i * 3;
            if (row >= height - 5) break;

            tg.setForegroundColor(HI);
            tg.putString(x, row, sym[i]);

            tg.setForegroundColor(TXT);
            tg.putString(x + 10, row, names[i]);

            if (price != null) {
                tg.setForegroundColor(HI);
                tg.putString(x + 26, row, "$" + String.format("%,.2f", price));
            } else {
                tg.setForegroundColor(DIM);
                tg.putString(x + 26, row, "Loading...");
            }

            // Dotted separator between coins
            if (i < coins.length - 1) {
                tg.setForegroundColor(DIM);
                for (int j = x; j < x + 50; j++) {
                    tg.putString(j, row + 1, ".");
                }
            }
        }

        drawFooter(width, height, "CRYPTO TICKER", "4");
    }

    // ═══════════════════════════════════════════════════
    //  ALL TOGETHER VIEW (grid layout)
    // ═══════════════════════════════════════════════════

    private void drawAllTogetherView() {
        TerminalSize size = screen.getTerminalSize();
        int width  = size.getColumns();
        int height = size.getRows();

        tg.setBackgroundColor(BG);
        tg.setForegroundColor(TXT);
        tg.fill(' ');

        int leftWidth = width / 2 - 2;
        int rightX = width / 2 + 1;
        int rightWidth = width / 2 - 3;
        int topHeight = 17;

        int bottomY = 2 + topHeight;
        int bottomHeight = height - bottomY - 2;

        // ───────── SYSTEM VITALS ─────────
        drawProgressBar(4, 4, leftWidth - 4, "CPU USAGE", sysMon.getCpuLoad());
        drawProgressBar(4, 6, leftWidth - 4, "RAM USAGE", sysMon.getMemoryUsage());
        drawProgressBar(4, 8, leftWidth - 4, "STORAGE ", sysMon.getStorageUsage());

        double temp = sysMon.getCpuTemperature();
        tg.putString(4, 10, "CPU TEMP : ");
        tg.setForegroundColor(temp > 75 ? ALERT : HI);
        tg.putString(15, 10, temp <= 0 ? "N/A" : df.format(temp) + " C");

        tg.setForegroundColor(TXT);
        tg.putString(4, 11, "BATTERY  : ");
        tg.setForegroundColor(HI);
        tg.putString(15, 11, sysMon.getBatteryInfo());

        tg.setForegroundColor(TXT);
        tg.putString(4, 12, "PROCESSES: ");
        tg.setForegroundColor(HI);
        tg.putString(15, 12, String.valueOf(sysMon.getProcessCount()));

        tg.setForegroundColor(TXT);
        tg.putString(4, 13, "THREADS  : ");
        tg.setForegroundColor(HI);
        tg.putString(15, 13, String.valueOf(sysMon.getThreadCount()));

        // ───────── NETWORK & ENV ─────────
        tg.setForegroundColor(TXT);
        tg.putString(rightX + 2, 4, "OS      : ");
        tg.setForegroundColor(HI);
        tg.putString(rightX + 13, 4, sysMon.getOsName());

        tg.setForegroundColor(TXT);
        tg.putString(rightX + 2, 5, "UPTIME  : ");
        tg.setForegroundColor(HI);
        tg.putString(rightX + 13, 5, sysMon.getUptime());

        tg.setForegroundColor(TXT);
        tg.putString(rightX + 2, 6, "BRANCH  : ");
        tg.setForegroundColor(HI);
        tg.putString(rightX + 13, 6, envService.getGitBranch());

        tg.setForegroundColor(TXT);
        tg.putString(rightX + 2, 7, "WEATHER : ");
        tg.setForegroundColor(HI);
        tg.putString(rightX + 13, 7, envService.getWeather());

        tg.setForegroundColor(TXT);
        tg.putString(rightX + 2, 9, "FAN SPD : ");
        tg.setForegroundColor(HI);
        tg.putString(rightX + 13, 9, sysMon.getFanSpeed());

        tg.setForegroundColor(TXT);
        tg.putString(rightX + 2, 11, "NET DOWN: ");
        tg.setForegroundColor(HI);
        tg.putString(rightX + 13, 11, formatBytes(sysMon.getNetworkDownloadSpeed()) + "/s");

        tg.setForegroundColor(TXT);
        tg.putString(rightX + 2, 12, "NET UP  : ");
        tg.setForegroundColor(HI);
        tg.putString(rightX + 13, 12, formatBytes(sysMon.getNetworkUploadSpeed()) + "/s");

        // ───────── PARASITE RADAR ─────────
        if (System.currentTimeMillis() - lastProcessUpdate > 2000) {
            cachedProcesses = sysMon.getTopProcesses(3);
            lastProcessUpdate = System.currentTimeMillis();
        }

        tg.setForegroundColor(ALERT);
        tg.putString(4, bottomY + 1, "[!] TOP CONSUMERS");

        for (int i = 0; i < cachedProcesses.size(); i++) {
            var p = cachedProcesses.get(i);
            tg.setForegroundColor(i == 0 ? ALERT : TXT);
            tg.putString(4, bottomY + 3 + i,
                    (i + 1) + ". " + p.getName() + " (" + df.format(p.getCpuUsage()) + "%)");
        }

        // ───────── CRYPTO ─────────
        Map<String, Double> prices = cryptoService.getPrices();
        String[] coins = {"bitcoin", "ethereum", "solana", "dogecoin"};
        String[] sym = {"BTC", "ETH", "SOL", "DOGE"};

        int cy = bottomY + 2;
        for (int i = 0; i < coins.length; i++) {
            Double price = prices.get(coins[i]);
            if (price == null) continue;

            tg.setForegroundColor(TXT);
            tg.putString(rightX + 2, cy + i, sym[i] + " : ");
            tg.setForegroundColor(HI);
            tg.putString(rightX + 11, cy + i, "$" + String.format("%,.2f", price));
        }

        // ───────── BOXES ─────────
        drawBox(0, 0, width, height - 1, " TERMDASH ", HI);
        drawBox(2, 2, leftWidth, topHeight, " SYSTEM VITALS ", TXT);
        drawBox(rightX, 2, rightWidth, topHeight, " NETWORK & ENV ", TXT);
        drawBox(2, bottomY, leftWidth, bottomHeight, " PARASITE RADAR ", TXT);
        drawBox(rightX, bottomY, rightWidth, bottomHeight, " CRYPTO TICKER ", TXT);

        drawFooter(width, height, "ALL MODULES", "5");
    }

    // ═══════════════════════════════════════════════════
    //  SHARED HELPERS
    // ═══════════════════════════════════════════════════

    private void drawFooter(int width, int height, String viewName, String viewNum) {
        tg.setForegroundColor(DIM);
        centerText(0, width, height - 3, "ESC = BACK | 1-5 = SWITCH VIEW | q = QUIT");

        String footer = "[" + viewNum + "] " + viewName + " | " +
                LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) +
                " | q = EXIT";
        tg.setForegroundColor(TXT);
        centerText(0, width, height - 1, footer);
    }

    private void putLabel(int x, int y, String text) {
        tg.setForegroundColor(TXT);
        tg.putString(x, y, text);
    }

    private void centerText(int left, int right, int y, String text) {
        int x = left + (right - left - text.length()) / 2;
        tg.putString(Math.max(0, x), y, text);
    }

    private String truncate(String s, int maxLen) {
        return s.length() <= maxLen ? s : s.substring(0, maxLen - 2) + "..";
    }

    private String formatBytes(long b) {
        if (b < 1024) return b + " B";
        int e = (int) (Math.log(b) / Math.log(1024));
        return String.format("%.1f %sB", b / Math.pow(1024, e), "KMGT".charAt(e - 1));
    }

    private String formatBytesLong(long b) {
        if (b < 1024) return b + " B";
        if (b < 1024 * 1024) return String.format("%.1f KB", b / 1024.0);
        if (b < 1024L * 1024 * 1024) return String.format("%.1f MB", b / (1024.0 * 1024));
        return String.format("%.2f GB", b / (1024.0 * 1024 * 1024));
    }

    // ── Box drawing ──

    private void drawBox(int x, int y, int w, int h, String title, TextColor color) {
        tg.setForegroundColor(color);
        tg.setCharacter(x, y, Symbols.SINGLE_LINE_TOP_LEFT_CORNER);
        tg.setCharacter(x + w - 1, y, Symbols.SINGLE_LINE_TOP_RIGHT_CORNER);
        tg.setCharacter(x, y + h - 1, Symbols.SINGLE_LINE_BOTTOM_LEFT_CORNER);
        tg.setCharacter(x + w - 1, y + h - 1, Symbols.SINGLE_LINE_BOTTOM_RIGHT_CORNER);

        for (int i = x + 1; i < x + w - 1; i++) {
            tg.setCharacter(i, y, Symbols.SINGLE_LINE_HORIZONTAL);
            tg.setCharacter(i, y + h - 1, Symbols.SINGLE_LINE_HORIZONTAL);
        }
        for (int i = y + 1; i < y + h - 1; i++) {
            tg.setCharacter(x, i, Symbols.SINGLE_LINE_VERTICAL);
            tg.setCharacter(x + w - 1, i, Symbols.SINGLE_LINE_VERTICAL);
        }
        tg.setForegroundColor(HI);
        tg.putString(x + 2, y, title);
    }

    // ── Progress bar ──

    private void drawProgressBar(int x, int y, int w, String label, double v) {
        tg.setForegroundColor(TXT);
        tg.putString(x, y, label);

        tg.setForegroundColor(HI);
        tg.putString(x + label.length() + 1, y, String.format("%.1f%%", v * 100));

        int filled = (int) (w * v);
        for (int i = 0; i < w; i++) {
            tg.setForegroundColor(i < filled ? HI : DIM);
            tg.setCharacter(x + i, y + 1, Symbols.BLOCK_SOLID);
        }
    }
}
