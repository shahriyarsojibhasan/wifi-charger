import sys
import os
import time
import struct
import hashlib

import serial
import esptool
from esptool.cmds import detect_chip
import esptool.loader

PORT = "COM4"
BAUD = 115200
FLASH_SIZE = 4 * 1024 * 1024  # 4 MB
SECTOR_SIZE = 4096
TOTAL_SECTORS = FLASH_SIZE // SECTOR_SIZE
OUTPUT_FILE = r"firmware\backup\nodemcu_default_firmware_4mb.bin"


def robust_slip_reader(port, trace_fn):
    """SLIP reader that ignores non-0xc0 framing noise between packets."""
    partial_packet = None
    in_escape = False
    while True:
        waiting = port.inWaiting()
        read_bytes = port.read(1 if waiting == 0 else waiting)
        if not read_bytes:
            raise esptool.loader.FatalError("No serial data received (timeout)")
        for b in read_bytes:
            b = bytes([b])
            if partial_packet is None:
                if b == b"\xc0":
                    partial_packet = b""
                else:
                    # Ignore inter-packet noise
                    continue
            elif in_escape:
                in_escape = False
                if b == b"\xdc":
                    partial_packet += b"\xc0"
                elif b == b"\xdd":
                    partial_packet += b"\xdb"
                else:
                    partial_packet = None
            elif b == b"\xdb":
                in_escape = True
            elif b == b"\xc0":
                if len(partial_packet) > 0:
                    yield partial_packet
                partial_packet = None
            else:
                partial_packet += b


def get_stub(port, baud):
    esp = detect_chip(port=port, baud=baud)
    esp = esp.run_stub()
    esp._slip_reader = robust_slip_reader(esp._port, esp.trace)
    return esp


def backup_flash(start_sector=0, max_sectors=TOTAL_SECTORS):
    os.makedirs(os.path.dirname(OUTPUT_FILE), exist_ok=True)

    print(f"Connecting to ESP8266 on {PORT} at {BAUD} baud...")
    esp = get_stub(PORT, BAUD)
    print("Stub flasher running and synchronized.")
    print(f"Backing up {max_sectors * SECTOR_SIZE // 1024} KB flash ({max_sectors} sectors) to:")
    print(f"  {OUTPUT_FILE}\n")

    # Open file for writing (or append if resuming)
    mode = "r+b" if (start_sector > 0 and os.path.exists(OUTPUT_FILE)) else "wb"
    out_f = open(OUTPUT_FILE, mode)
    if start_sector > 0:
        out_f.seek(start_sector * SECTOR_SIZE)

    total_retries = 0
    start_time = time.time()

    try:
        for idx in range(start_sector, start_sector + max_sectors):
            addr = idx * SECTOR_SIZE
            sector_data = None

            for attempt in range(5):
                try:
                    # Refresh slip reader in case previous attempt left it in bad state
                    esp._slip_reader = robust_slip_reader(esp._port, esp.trace)
                    sector_data = esp.read_flash(addr, SECTOR_SIZE)
                    break
                except Exception as e:
                    total_retries += 1
                    try:
                        esp._port.reset_input_buffer()
                        esp._port.reset_output_buffer()
                    except Exception:
                        pass

                    if attempt >= 2:
                        # Reconnect if stubborn
                        try:
                            esp._port.close()
                        except Exception:
                            pass
                        time.sleep(0.2)
                        esp = get_stub(PORT, BAUD)
                    else:
                        time.sleep(0.05)

            if sector_data is None:
                raise RuntimeError(f"Failed to read sector {idx} (0x{addr:05x}) after 5 attempts.")

            out_f.write(sector_data)
            out_f.flush()

            # Progress output
            done = idx - start_sector + 1
            percent = (done / max_sectors) * 100
            elapsed = time.time() - start_time
            kb_done = done * 4
            speed = (kb_done / elapsed) if elapsed > 0 else 0
            eta = (max_sectors - done) * 4 / speed if speed > 0 else 0

            print(
                f"\r[{done:4d}/{max_sectors}] {percent:5.1f}% | {kb_done:4d} KB / {max_sectors*4} KB | "
                f"{speed:4.1f} KB/s | ETA: {int(eta)}s (Retries: {total_retries})",
                end="",
                flush=True,
            )

        print(f"\n\nBackup finished successfully in {int(time.time() - start_time)} seconds.")
        print(f"Total size: {out_f.tell()} bytes.")

    finally:
        out_f.close()
        try:
            esp.hard_reset()
        except Exception:
            pass


if __name__ == "__main__":
    test_mode = "--test" in sys.argv
    if test_mode:
        print("Running quick test (16 sectors / 64 KB)...")
        backup_flash(start_sector=0, max_sectors=16)
    else:
        backup_flash(start_sector=0, max_sectors=TOTAL_SECTORS)
