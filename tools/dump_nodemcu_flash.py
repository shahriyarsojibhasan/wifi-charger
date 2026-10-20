import sys
import os
import time
import struct
import hashlib

from esptool.cmds import detect_chip
import esptool.loader

PORT = "COM4"
BAUD = 115200
FLASH_SIZE = 4 * 1024 * 1024  # 4 MB
SECTOR_SIZE = 4096
TOTAL_SECTORS = FLASH_SIZE // SECTOR_SIZE  # 1024 sectors
OUTPUT_FILE = r"firmware\backup\nodemcu_default_firmware_4mb.bin"


def make_tolerant_slip_reader(port, trace_fn):
    """Generator that ignores framing noise / trailing 0x00 bytes before packet 0xC0."""
    partial_packet = None
    in_escape = False
    while True:
        waiting = port.inWaiting()
        read_bytes = port.read(1 if waiting == 0 else waiting)
        if not read_bytes:
            raise esptool.loader.FatalError("Timeout: no serial data received")
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
                yield partial_packet
                partial_packet = None
            else:
                partial_packet += b


def connect_and_start_stub(port, baud):
    esptool.loader.slip_reader = make_tolerant_slip_reader
    esp = detect_chip(port=port, baud=baud)
    if hasattr(esp._port, "set_buffer_size"):
        try:
            esp._port.set_buffer_size(rx_size=1048576, tx_size=1048576)
        except Exception:
            pass
    esp = esp.run_stub()
    esp._slip_reader = make_tolerant_slip_reader(esp._port, esp.trace)
    return esp


def read_one_sector(esp, addr):
    """Read single 4096-byte sector and verify hardware MD5."""
    esp._slip_reader = make_tolerant_slip_reader(esp._port, esp.trace)
    data = esp.read_flash(addr, SECTOR_SIZE)
    if len(data) != SECTOR_SIZE:
        raise esptool.loader.FatalError(f"Unexpected sector length {len(data)}")
    return data


def backup_firmware():
    os.makedirs(os.path.dirname(OUTPUT_FILE), exist_ok=True)

    # Check existing file for resuming
    current_size = 0
    if os.path.exists(OUTPUT_FILE):
        current_size = os.path.getsize(OUTPUT_FILE)

    start_sector = current_size // SECTOR_SIZE
    if start_sector >= TOTAL_SECTORS:
        print(f"Backup already complete ({current_size} bytes in {OUTPUT_FILE}).")
        return

    print("=" * 60)
    print(f"NodeMCU (ESP8266) Firmware Backup Tool")
    print(f"Port:       {PORT} @ {BAUD} baud")
    print(f"Flash size: {FLASH_SIZE // (1024*1024)} MB ({TOTAL_SECTORS} sectors of 4KB)")
    print(f"Output:     {OUTPUT_FILE}")
    if start_sector > 0:
        print(f"Resuming:   From sector {start_sector} ({start_sector*4} KB already saved)")
    print("=" * 60)

    esp = None
    file_mode = "r+b" if (start_sector > 0 and os.path.exists(OUTPUT_FILE)) else "wb"
    out_f = open(OUTPUT_FILE, file_mode)
    out_f.seek(start_sector * SECTOR_SIZE)

    total_retries = 0
    total_reconnects = 0
    start_time = time.time()

    try:
        esp = connect_and_start_stub(PORT, BAUD)
        print("Connected to ESP8266 and stub flasher started successfully.\n")

        for sector_idx in range(start_sector, TOTAL_SECTORS):
            addr = sector_idx * SECTOR_SIZE
            sector_data = None

            for attempt in range(6):
                if esp is None:
                    time.sleep(0.3)
                    total_reconnects += 1
                    try:
                        esp = connect_and_start_stub(PORT, BAUD)
                    except Exception as conn_err:
                        time.sleep(0.5)
                        continue

                try:
                    sector_data = read_one_sector(esp, addr)
                    break
                except Exception as e:
                    total_retries += 1
                    # Drain serial buffers
                    try:
                        esp._port.reset_input_buffer()
                        esp._port.reset_output_buffer()
                    except Exception:
                        pass

                    if attempt >= 1:
                        # Close connection and trigger reconnect on next iteration
                        try:
                            esp._port.close()
                        except Exception:
                            pass
                        esp = None
                        time.sleep(0.2)
                    else:
                        time.sleep(0.05)

            if sector_data is None:
                raise RuntimeError(
                    f"Fatal: Failed to read sector {sector_idx} (0x{addr:05x}) after 6 attempts."
                )

            out_f.write(sector_data)
            out_f.flush()

            # Progress update
            completed = sector_idx + 1
            percent = (completed / TOTAL_SECTORS) * 100
            elapsed = time.time() - start_time
            done_bytes = (completed - start_sector) * SECTOR_SIZE
            speed = (done_bytes / 1024) / elapsed if elapsed > 0 else 0
            remaining_bytes = (TOTAL_SECTORS - completed) * SECTOR_SIZE
            eta_seconds = (remaining_bytes / 1024) / speed if speed > 0 else 0

            print(
                f"\r[{completed:4d}/{TOTAL_SECTORS}] {percent:5.1f}% | "
                f"{completed * 4:4d} KB / 4096 KB | "
                f"{speed:4.1f} KB/s | ETA: {int(eta_seconds):3d}s | "
                f"Retries: {total_retries}",
                end="",
                flush=True,
            )

        print("\n\n" + "=" * 60)
        final_size = out_f.tell()
        print(f"Backup SUCCESSFUL!")
        print(f"Total size:       {final_size} bytes ({final_size // (1024*1024)} MB)")
        print(f"Total time:       {int(time.time() - start_time)} seconds")
        print(f"Total retries:    {total_retries}")
        print(f"Total reconnects: {total_reconnects}")
        print("=" * 60)

    finally:
        out_f.close()
        if esp is not None:
            try:
                esp.hard_reset()
                esp._port.close()
            except Exception:
                pass


if __name__ == "__main__":
    backup_firmware()
