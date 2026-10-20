#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/common.sh"
case "${1:-list}" in
  list) "$ADB" devices -l ;;
  pair)
    [[ "${2:-}" =~ ^[^[:space:]]+:[0-9]+$ && "${2:-}" != -* ]] || { echo 'Usage: connect-tv.sh pair TV_IP:PAIRING_PORT'; exit 2; }
    # adb prompts securely for the short-lived code, keeping it out of shell history.
    "$ADB" pair "$2" ;;
  connect)
    [[ "${2:-}" =~ ^[^[:space:]]+:[0-9]+$ && "${2:-}" != -* ]] || { echo 'Usage: connect-tv.sh connect TV_IP:DEBUG_PORT'; exit 2; }
    "$ADB" connect "$2"
    "$ADB" devices -l ;;
  usb-to-tcp)
    require_device "${2:-}"
    [[ "${3:-}" =~ ^[0-9]+$ ]] && (( $3 >= 1024 && $3 <= 65535 )) || { echo 'Supply an explicitly chosen TCP port (1024–65535).'; exit 2; }
    [[ "$SERIAL" != *:* && "$SERIAL" != emulator-* ]] || { echo 'Use an already authorized USB serial.'; exit 2; }
    "$ADB" -s "$SERIAL" tcpip "$3"
    echo 'Now connect to the IP and chosen port. This only works if the firmware supports TCP ADB.' ;;
  *) echo 'Usage: connect-tv.sh list | pair IP:PORT | connect IP:PORT | usb-to-tcp USB_SERIAL PORT'; exit 2 ;;
esac
