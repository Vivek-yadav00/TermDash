# TermDash 🖥️

A terminal-based system monitoring dashboard built with Java. TermDash provides real-time system metrics and cryptocurrency prices in a beautiful Matrix-themed terminal UI.

![Java](https://img.shields.io/badge/Java-17+-orange)
![License](https://img.shields.io/badge/License-MIT-green)

## Features

- **System Monitoring**
  - CPU usage with real-time progress bar
  - Memory usage tracking
  - Storage/disk usage
  - CPU temperature monitoring
  - Battery status information
  - Process and thread count
  - Network speed (upload/download)

- **Cryptocurrency Prices**
  - Live prices for Bitcoin, Ethereum, Solana, Dogecoin, and Monero
  - Data fetched from CoinGecko API
  - Auto-refreshes every 60 seconds

- **Matrix-Themed UI**
  - Clean, terminal-based interface using Lanterna
  - Green-on-black color scheme
  - Visual progress bars and stats

## Prerequisites

- **Java 17** or higher (Ensure your `JAVA_HOME` environment variable points to a Java 17+ JDK)
- **Maven 3.6+** for building

> [!TIP]
> If your compilation fails with `invalid target release: 17`, it means your default terminal is pointing to Java 8. Set your `JAVA_HOME` temporarily before compiling:
> - **PowerShell:** `$env:JAVA_HOME="C:\Program Files\Java\jdk-24"`
> - **CMD:** `set JAVA_HOME=C:\Program Files\Java\jdk-24`


## Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/bixl007/termdash.git
   cd termdash
   ```

2. **Build the project**
   ```bash
   mvn clean package
   ```

## Running the Application

### Option A: Using Docker (easiest, no setup required)
If you have Docker installed, you can run the application directly without installing Java or Maven:

```bash
docker run -it --rm vivek01mxt/termdash:latest
```

### Option B: Building Locally (requires Java 17 & Maven)
After building the project, run the application using:

```bash
java -jar target/termdash-1.0-SNAPSHOT.jar
```

Or build and run in one command:

```bash
mvn clean package; java -jar target/termdash-1.0-SNAPSHOT.jar
```

## Controls

| Key / Control | Action |
|---|---|
| `Arrow Keys` | Move selection box between menu buttons |
| `Enter` | Open the selected module |
| `1` | Quick open **System Vitals** |
| `2` | Quick open **Network & Env** |
| `3` | Quick open **Parasite Radar** |
| `4` | Quick open **Crypto Ticker** |
| `5` | Quick open **All Modules Together** |
| `Esc` / `Backspace` | Go back to the Control Center (Main Menu) |
| `q` | Quit the application |

## Project Structure

```
termdash/
├── pom.xml
├── src/
│   └── main/
│       └── java/
│           └── com/
│               └── termdash/
│                   ├── TermDash.java          # Main entry point
│                   ├── service/
│                   │   ├── CryptoService.java     # Cryptocurrency API client
│                   │   ├── EnvironmentService.java # Environment utilities
│                   │   └── SystemMonitor.java     # System metrics collector
│                   └── ui/
│                       └── Dashboard.java     # Terminal UI renderer
└── target/                                    # Build output
```

## Dependencies

- [Lanterna](https://github.com/mabe02/lanterna) - Terminal UI library
- [OSHI](https://github.com/oshi/oshi) - Operating System and Hardware Information
- [Gson](https://github.com/google/gson) - JSON parsing for API responses

## License

This project is open source and available under the [MIT License](LICENSE).

## Contributing

Contributions are welcome! Feel free to submit issues and pull requests.
