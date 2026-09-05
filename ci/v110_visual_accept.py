#!/usr/bin/env python3
import os
import subprocess
import time

PKG = 'com.immersive.music'
ACTIVITY = f'{PKG}/{PKG}.MainActivity'
OUT = 'deliverable/v110-outer-halo-restored-internal-highlights-removed.png'


def adb(*args, check=True):
    p = subprocess.run(['adb', *args], stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False)
    if check and p.returncode != 0:
        raise RuntimeError(p.stderr.decode('utf-8', 'replace'))
    return p.stdout


def main():
    adb('shell', 'wm', 'size', '1080x2400')
    adb('shell', 'wm', 'density', '440')
    try:
        adb('shell', 'pm', 'clear', PKG, check=False)
        adb('shell', 'am', 'start', '-W', '-n', ACTIVITY)
        time.sleep(1.8)
        os.makedirs('deliverable', exist_ok=True)
        with open(OUT, 'wb') as f:
            subprocess.run(['adb', 'exec-out', 'screencap', '-p'], stdout=f, check=True)
        print('V110_VISUAL_CAPTURE=PASS')
    finally:
        adb('shell', 'wm', 'size', 'reset', check=False)
        adb('shell', 'wm', 'density', 'reset', check=False)


if __name__ == '__main__':
    main()
