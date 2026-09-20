#!/usr/bin/env python3
"""Verify base/lib/**/*.so entries in a release AAB are 16 KB page-aligned.

zipalign (Android build-tools) understands the plain APK zip layout but not
the AAB container, so this walks the AAB's zip entries by hand: an
uncompressed .so entry's data must start at an offset that is a multiple of
16384 (16 KB) for the device loader to mmap it directly. Compressed entries
are exempt since they're inflated into memory rather than mapped from disk.

Usage: python3 check_16kb_alignment.py <path-to-release.aab>
"""
import sys
import zipfile


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: check_16kb_alignment.py <path-to-release.aab>", file=sys.stderr)
        return 2

    aab_path = sys.argv[1]
    bad = []
    with zipfile.ZipFile(aab_path) as z:
        so_entries = [
            i for i in z.infolist()
            if i.filename.startswith("base/lib/") and i.filename.endswith(".so")
        ]
        if not so_entries:
            print("No native libraries in release AAB — nothing to check.")
            return 0

        with open(aab_path, "rb") as f:
            for info in so_entries:
                f.seek(info.header_offset)
                header = f.read(30)
                name_len = int.from_bytes(header[26:28], "little")
                extra_len = int.from_bytes(header[28:30], "little")
                data_offset = info.header_offset + 30 + name_len + extra_len

                aligned = (data_offset % 16384) == 0
                compressed = info.compress_type != zipfile.ZIP_STORED
                status = "OK" if (aligned or compressed) else "MISALIGNED"
                print(
                    f"{status:11s} offset={data_offset:>10d} "
                    f"compressed={str(compressed):5s} {info.filename}"
                )
                if not aligned and not compressed:
                    bad.append(info.filename)

    if bad:
        print(f"FATAL: {len(bad)} uncompressed .so entries are not 16 KB aligned")
        return 1

    print("All native libraries in release AAB are 16 KB aligned (or compressed).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
