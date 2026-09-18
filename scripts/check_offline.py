#!/usr/bin/env python3
"""Meaningful SDK-free checks. Full Android gates remain assembleDebug/testDebugUnitTest/lintDebug."""
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import xml.etree.ElementTree as ET
from gradle import configure_java

root = Path(__file__).resolve().parents[1]
source = root / 'app/src/main/java/io/github/pawprint0706/coderadio'
configure_java()
with tempfile.TemporaryDirectory(prefix='coderadio-check-') as temp:
    core = [source / (name + '.java') for name in ('RadioConfig', 'RadioRules', 'PlaybackIntent', 'StationSnapshot')]
    helpers = [root / 'scripts/CoreChecks.java', root / 'scripts/JavaSyntaxCheck.java']
    compiler = ['javac'] if shutil.which('javac') else ['java', '--add-modules', 'jdk.compiler', 'com.sun.tools.javac.Main']
    subprocess.run([*compiler, '-encoding', 'UTF-8', '-d', temp, *map(str, core + helpers)], check=True)
    subprocess.run(['java', '-cp', temp, 'CoreChecks'], check=True)
    sources = sorted((root / 'app/src').rglob('*.java'))
    subprocess.run(['java', '-cp', temp, 'JavaSyntaxCheck', *map(str, sources)], check=True)

xml_files = sorted((root / 'app/src/main').rglob('*.xml'))
for path in xml_files:
    ET.parse(path)
print(f'PASS: {len(xml_files)} XML resources/manifest parse')
values = ET.parse(root / 'app/src/main/res/values/strings.xml').getroot()
korean = ET.parse(root / 'app/src/main/res/values-ko/strings.xml').getroot()
names = {node.attrib['name'] for node in values}
assert names == {node.attrib['name'] for node in korean}, 'Translation keys differ'
java_text = '\n'.join(path.read_text(encoding='utf-8') for path in sources)
referenced = set(re.findall(r'R\.string\.(\w+)', java_text))
assert referenced <= names, f'Missing resources: {referenced - names}'
print(f'PASS: {len(names)} English/Korean keys and Java string references')
manifest = ET.parse(root / 'app/src/main/AndroidManifest.xml').getroot()
ns = '{http://schemas.android.com/apk/res/android}'
permissions = {node.attrib[ns + 'name'] for node in manifest.findall('uses-permission')}
assert 'android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK' in permissions
service = manifest.find('application/service')
assert service.attrib[ns + 'foregroundServiceType'] == 'mediaPlayback'
assert manifest.find('application').attrib[ns + 'usesCleartextTraffic'] == 'false'
print('PASS: Media playback foreground service and HTTPS-only manifest')
