/**
 * HDify Unified Version Synchronizer (Android <-> Windows)
 *
 * Memastikan bahwa setiap perubahan versi selalu serentak dan identik
 * di kedua platform:
 *  1. Android: app/build.gradle (versionName & auto-increment versionCode)
 *  2. Windows: hdify-windows/package.json (version)
 *  3. Windows: hdify-windows/index.html (titlebar & footer label)
 *  4. Windows: hdify-windows/README.md (documentation links)
 *
 * Penggunaan:
 *   node sync_version.js 2.2.6
 */

const fs = require('fs');
const path = require('path');

const newVersion = process.argv[2];

if (!newVersion || !/^\d+\.\d+\.\d+$/.test(newVersion)) {
    console.error('[ERROR] Masukkan format versi yang valid (contoh: node sync_version.js 2.2.6)');
    process.exit(1);
}

console.log(`\n======================================================`);
console.log(`  HDify Unified Version Sync -> Target: v${newVersion}`);
console.log(`======================================================\n`);

const rootDir = __dirname;

// 1. Update Android app/build.gradle
const androidGradlePath = path.join(rootDir, 'app', 'build.gradle');
if (fs.existsSync(androidGradlePath)) {
    let content = fs.readFileSync(androidGradlePath, 'utf8');
    
    // Auto-increment versionCode
    content = content.replace(/versionCode\s+(\d+)/, (match, code) => {
        const nextCode = parseInt(code, 10) + 1;
        console.log(`[Android] versionCode dinaikkan: ${code} -> ${nextCode}`);
        return `versionCode ${nextCode}`;
    });

    // Update versionName
    content = content.replace(/versionName\s+['"][^'"]+['"]/, (match) => {
        console.log(`[Android] versionName diupdate -> '${newVersion}'`);
        return `versionName '${newVersion}'`;
    });

    fs.writeFileSync(androidGradlePath, content, 'utf8');
    console.log(`[OK] app/build.gradle berhasil disinkronkan.\n`);
} else {
    console.warn(`[WARN] File app/build.gradle tidak ditemukan.`);
}

// 2. Update Windows package.json
const winPackagePath = path.join(rootDir, 'hdify-windows', 'package.json');
if (fs.existsSync(winPackagePath)) {
    let pkg = JSON.parse(fs.readFileSync(winPackagePath, 'utf8'));
    const oldVer = pkg.version;
    pkg.version = newVersion;
    fs.writeFileSync(winPackagePath, JSON.stringify(pkg, null, 2) + '\n', 'utf8');
    console.log(`[Windows] package.json version diupdate: ${oldVer} -> ${newVersion}`);
}

// 3. Update Windows index.html
const winHtmlPath = path.join(rootDir, 'hdify-windows', 'index.html');
if (fs.existsSync(winHtmlPath)) {
    let html = fs.readFileSync(winHtmlPath, 'utf8');
    html = html.replace(/<span class="titlebar-version">v[^<]+<\/span>/g, `<span class="titlebar-version">v${newVersion}</span>`);
    html = html.replace(/<div style="font-size: 11px; color: var\(--text-tertiary\);">HDify v[^<]+<\/div>/g, `<div style="font-size: 11px; color: var(--text-tertiary);">HDify v${newVersion}</div>`);
    fs.writeFileSync(winHtmlPath, html, 'utf8');
    console.log(`[Windows] index.html titlebar & footer diupdate -> v${newVersion}`);
}

// 4. Update Windows README.md
const winReadmePath = path.join(rootDir, 'hdify-windows', 'README.md');
if (fs.existsSync(winReadmePath)) {
    let readme = fs.readFileSync(winReadmePath, 'utf8');
    readme = readme.replace(/HDify_(Portable|Setup)_v\d+\.\d+\.\d+\.exe/g, (m, p1) => `HDify_${p1}_v${newVersion}.exe`);
    fs.writeFileSync(winReadmePath, readme, 'utf8');
    console.log(`[Windows] README.md tautan installer diupdate -> v${newVersion}`);
}

console.log(`\n======================================================`);
console.log(`  SINKRONISASI SELESAI! Kedua platform sekarang v${newVersion}`);
console.log(`======================================================\n`);
