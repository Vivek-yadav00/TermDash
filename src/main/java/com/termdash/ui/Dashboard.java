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

    private final DecimalFormat df = new DecimalFormat("0.0");

    private List<SystemMonitor.ProcessMetric> cachedProcesses = Collections.emptyList();
    private long lastProcessUpdate = 0;

    public Dashboard() throws IOException {
        Terminal terminal = new DefaultTerminalFactory()
                .setInitialTerminalSize(new TerminalSize(120, 38))
                .createTerminal();

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
            sysMon.updateNetworkSpeeds(); // 🔥 REQUIRED

            draw();
            screen.refresh();

            var key = screen.pollInput();
            if (key != null && key.getCharacter() != null && key.getCharacter() == 'q') break;

            Thread.sleep(120);
        }
        screen.stopScreen();
    }

    private void draw() {
        TerminalSize size = screen.getTerminalSize();
        int width = size.getColumns();
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

        // 🔥 FAN SPEED
        tg.setForegroundColor(TXT);
        tg.putString(rightX + 2, 9, "FAN SPD : ");
        tg.setForegroundColor(HI);
        tg.putString(rightX + 13, 9, sysMon.getFanSpeed());

        // 🔥 NETWORK SPEED
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
        drawBox(0, 0, width, height - 1, " TERMDASH ");
        drawBox(2, 2, leftWidth, topHeight, " SYSTEM VITALS ");
        drawBox(rightX, 2, rightWidth, topHeight, " NETWORK & ENV ");
        drawBox(2, bottomY, leftWidth, bottomHeight, " PARASITE RADAR ");
        drawBox(rightX, bottomY, rightWidth, bottomHeight, " CRYPTO TICKER ");

        String footer = "ONLINE | " +
                LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) +
                " | q = EXIT";

        tg.setForegroundColor(TXT);
        tg.putString((width - footer.length()) / 2, height - 1, footer);
    }

    private String formatBytes(long b) {
        if (b < 1024) return b + " B";
        int e = (int) (Math.log(b) / Math.log(1024));
        return String.format("%.1f %sB", b / Math.pow(1024, e), "KMGT".charAt(e - 1));
    }

    private void drawBox(int x, int y, int w, int h, String title) {
        tg.setForegroundColor(TXT);
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

    private void drawProgressBar(int x, int y, int w, String label, double v) {
        tg.setForegroundColor(TXT);
        tg.putString(x, y, label);
        int filled = (int) (w * v);
        for (int i = 0; i < w; i++) {
            tg.setForegroundColor(i < filled ? HI : TextColor.ANSI.BLACK_BRIGHT);
            tg.setCharacter(x + i, y + 1, Symbols.BLOCK_SOLID);
        }
    }
}
