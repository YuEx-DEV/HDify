/**
 * Otomatis membersihkan file setup dan executable lama di folder dist/ sebelum build baru dibuat.
 * Memastikan hanya versi terbaru yang tersimpan di dist (sama seperti build APK di Android).
 */
const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

// Pastikan tidak ada proses HDify yang sedang berjalan agar file .exe tidak terkunci (EBUSY)
try {
    if (process.platform === 'win32') {
        execSync('taskkill /F /IM HDify*.exe /T', { stdio: 'ignore' });
    }
} catch (_) {}

const distDir = path.join(__dirname, 'dist');

if (fs.existsSync(distDir)) {
    console.log('[HDify Build] Membersihkan file setup/build lama di folder dist...');
    const files = fs.readdirSync(distDir);
    let removedCount = 0;

    for (const file of files) {
        // Hapus file setup/portable .exe lama, blockmap, log yml, dan folder win-unpacked
        const filePath = path.join(distDir, file);
        const stat = fs.statSync(filePath);
        if (file === 'win-unpacked' || file.startsWith('builder-effective-config') || (stat.isDirectory() && file.includes('unpacked'))) {
            try {
                fs.rmSync(filePath, { recursive: true, force: true });
                console.log(`  -> Menghapus folder sampah: ${file}`);
                removedCount++;
            } catch (err) {
                console.warn(`  [!] Gagal menghapus ${file}: ${err.message}`);
            }
        } else if (file.endsWith('.exe') || file.endsWith('.blockmap') || file.endsWith('.yml')) {
            try {
                fs.unlinkSync(filePath);
                console.log(`  -> Menghapus file lama: ${file}`);
                removedCount++;
            } catch (err) {
                console.warn(`  [!] Gagal menghapus ${file}: ${err.message}`);
            }
        }
    }
    if (removedCount === 0) {
        console.log('  -> Folder dist bersih (tidak ada setup lama).');
    } else {
        console.log(`  -> Selesai: ${removedCount} file setup lama berhasil dibersihkan.`);
    }
}
