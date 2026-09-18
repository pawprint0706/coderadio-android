#!/usr/bin/env python3
"""Bootstrap pinned Gradle, verify its official SHA-256, then build or create the official wrapper.

Python 3.9+, Java 17, internet, and Android SDK 35 are needed on the build machine.
No Python or build tools are needed on the Android phone.
"""
from pathlib import Path
import hashlib
import os
import re
import shutil
import subprocess
import sys
import tempfile
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
VERSION = '8.11.1'
TOOLS = ROOT / '.tools'
DISTRIBUTION = f'https://services.gradle.org/distributions/gradle-{VERSION}-bin.zip'


def java_major(executable):
    result = subprocess.run(
        [str(executable), '-version'], capture_output=True, text=True, errors='replace'
    )
    match = re.search(r'version\s+"(?:1\.)?(\d+)', result.stdout + result.stderr)
    return int(match.group(1)) if result.returncode == 0 and match else None


def configure_java():
    candidates = []
    java_home = os.environ.get('JAVA_HOME')
    if java_home:
        candidates.append(Path(java_home) / 'bin' / ('java.exe' if os.name == 'nt' else 'java'))
    path_java = shutil.which('java')
    if path_java:
        candidates.append(Path(path_java))
    if os.name == 'nt':
        program_files = Path(os.environ.get('ProgramFiles', r'C:\Program Files'))
        candidates.append(program_files / 'Android' / 'Android Studio' / 'jbr' / 'bin' / 'java.exe')

    checked = set()
    for executable in candidates:
        executable = executable.resolve()
        if executable in checked or not executable.is_file():
            continue
        checked.add(executable)
        major = java_major(executable)
        if major is not None and major >= 17:
            home = executable.parent.parent
            os.environ['JAVA_HOME'] = str(home)
            os.environ['PATH'] = str(executable.parent) + os.pathsep + os.environ.get('PATH', '')
            print(f'Using Java {major} from {home}', flush=True)
            return
    raise RuntimeError('Java 17 or newer is required; set JAVA_HOME or install Android Studio')


def configure_android_sdk():
    if (ROOT / 'local.properties').is_file():
        return
    candidates = [os.environ.get('ANDROID_HOME'), os.environ.get('ANDROID_SDK_ROOT')]
    if os.name == 'nt':
        local_app_data = os.environ.get('LOCALAPPDATA')
        if local_app_data:
            candidates.append(Path(local_app_data) / 'Android' / 'Sdk')
    elif sys.platform == 'darwin':
        candidates.append(Path.home() / 'Library' / 'Android' / 'sdk')
    else:
        candidates.append(Path.home() / 'Android' / 'Sdk')

    for candidate in candidates:
        if not candidate:
            continue
        sdk = Path(candidate).expanduser().resolve()
        if sdk.is_dir() and (sdk / 'platforms').is_dir():
            os.environ['ANDROID_HOME'] = str(sdk)
            os.environ['ANDROID_SDK_ROOT'] = str(sdk)
            print(f'Using Android SDK from {sdk}', flush=True)
            return
    raise RuntimeError('Android SDK not found; set ANDROID_HOME or create local.properties')


def download(url, destination):
    request = urllib.request.Request(url, headers={'User-Agent': 'CodeRadioAndroid-build/1.0'})
    with urllib.request.urlopen(request, timeout=60) as response, destination.open('wb') as output:
        shutil.copyfileobj(response, output)


def install():
    TOOLS.mkdir(exist_ok=True)
    destination = TOOLS / f'gradle-{VERSION}'
    launcher = destination / 'bin' / ('gradle.bat' if os.name == 'nt' else 'gradle')
    checksum_file = TOOLS / f'gradle-{VERSION}.sha256'
    if launcher.exists() and checksum_file.exists():
        return launcher, checksum_file.read_text().strip()
    print(f'Downloading Gradle {VERSION} and verifying the official SHA-256…', flush=True)
    with tempfile.TemporaryDirectory(prefix='gradle-', dir=TOOLS) as temporary:
        temp = Path(temporary)
        expected_file = temp / 'checksum'
        archive = temp / 'gradle.zip'
        download(DISTRIBUTION + '.sha256', expected_file)
        expected = expected_file.read_text().strip().lower()
        if not re.fullmatch(r'[0-9a-f]{64}', expected):
            raise ValueError('Invalid official Gradle checksum')
        download(DISTRIBUTION, archive)
        digest = hashlib.sha256()
        with archive.open('rb') as stream:
            for chunk in iter(lambda: stream.read(1024 * 1024), b''):
                digest.update(chunk)
        if digest.hexdigest() != expected:
            raise ValueError('Gradle checksum mismatch; refusing to execute')
        unpacked = temp / 'unpacked'
        unpacked.mkdir()
        with zipfile.ZipFile(archive) as package:
            for entry in package.infolist():
                target = (unpacked / entry.filename).resolve()
                if unpacked.resolve() not in target.parents:
                    raise ValueError('Unsafe archive path')
            package.extractall(unpacked)
        if destination.exists():
            shutil.rmtree(destination)
        shutil.move(str(unpacked / f'gradle-{VERSION}'), destination)
        if os.name != 'nt':
            launcher.chmod(0o755)
        checksum_file.write_text(expected + '\n')
    return launcher, expected


def run(launcher, args, cwd):
    command = [str(launcher), '--no-daemon', *args]
    if os.name == 'nt':
        command = ['cmd', '/c', *command]
    return subprocess.run(command, cwd=cwd, check=True)


def main():
    configure_java()
    launcher, checksum = install()
    args = sys.argv[1:]
    if args == ['--setup']:
        # Generate authentic wrapper files from the verified distribution, without
        # resolving Android dependencies during wrapper generation.
        with tempfile.TemporaryDirectory(prefix='wrapper-', dir=TOOLS) as temp:
            build = Path(temp)
            (build / 'settings.gradle').write_text("rootProject.name = 'wrapper-bootstrap'\n")
            (build / 'build.gradle').write_text('')
            run(launcher, ['wrapper', '--gradle-version', VERSION, '--distribution-type', 'bin',
                           '--gradle-distribution-sha256-sum', checksum], build)
            for filename in ('gradlew', 'gradlew.bat'):
                shutil.copy2(build / filename, ROOT / filename)
            shutil.copytree(build / 'gradle', ROOT / 'gradle', dirs_exist_ok=True)
        print('Official Gradle wrapper generated. Open this folder in Android Studio.')
    else:
        configure_android_sdk()
        run(launcher, args or ['testDebugUnitTest', 'lintDebug', 'assembleDebug'], ROOT)


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        print(f'Build/setup failed: {error}', file=sys.stderr)
        sys.exit(1)
