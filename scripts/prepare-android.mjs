// android/ は生成物なのでリポジトリに入れない。
// このスクリプトが android/ を作り、自作プラグインと必要な設定を流し込む。
import { execSync } from 'node:child_process';
import { existsSync, mkdirSync, copyFileSync, readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const PKG_DIR = 'android/app/src/main/java/com/sikumilab/plasmatouch';

if (!existsSync('android')) {
  console.log('> npx cap add android');
  execSync('npx cap add android', { stdio: 'inherit' });
} else {
  console.log('> android/ already exists, skipping cap add');
}

mkdirSync(PKG_DIR, { recursive: true });
for (const f of ['HapticLabPlugin.java', 'MainActivity.java']) {
  copyFileSync(join('native', f), join(PKG_DIR, f));
  console.log('  copied', f);
}

const manifestPath = 'android/app/src/main/AndroidManifest.xml';
let m = readFileSync(manifestPath, 'utf8');
if (!m.includes('permission.VIBRATE')) {
  m = m.replace(
    '<uses-permission android:name="android.permission.INTERNET" />',
    '<uses-permission android:name="android.permission.INTERNET" />\n    <uses-permission android:name="android.permission.VIBRATE" />'
  );
  writeFileSync(manifestPath, m);
  console.log('  added VIBRATE permission');
}

const varsPath = 'android/variables.gradle';
const v = readFileSync(varsPath, 'utf8');
// VibrationEffect / hasAmplitudeControl は API 26 から
const v2 = v.replace(/minSdkVersion\s*=\s*\d+/, 'minSdkVersion = 26');
if (v2 !== v) { writeFileSync(varsPath, v2); console.log('  minSdkVersion = 26'); }

console.log('prepare-android: done');
