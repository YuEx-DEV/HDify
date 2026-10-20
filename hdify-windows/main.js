const { app, BrowserWindow, ipcMain, dialog, shell, session, clipboard } = require('electron');
const path = require('path');
const fs = require('fs');
const os = require('os');
const http = require('http');
const { Readable } = require('stream');
const { spawn, execFile, exec, execFileSync } = require('child_process');
const ffmpegStatic = require('ffmpeg-static');
const ffprobeStatic = require('ffprobe-static');

// Internal HTTP media streaming proxy for zero-CORS HTML5 video, audio, and image preview
let mediaProxyServer = null;
let mediaProxyPort = 0;

function startMediaProxy() {
    if (mediaProxyServer) return Promise.resolve(mediaProxyPort);
    return new Promise((resolve) => {
        mediaProxyServer = http.createServer(async (req, res) => {
            res.setHeader('Access-Control-Allow-Origin', '*');
            res.setHeader('Access-Control-Allow-Methods', 'GET, HEAD, OPTIONS');
            res.setHeader('Access-Control-Allow-Headers', '*');
            res.setHeader('Access-Control-Allow-Private-Network', 'true');
            res.setHeader('Cross-Origin-Resource-Policy', 'cross-origin');

            if (req.method === 'OPTIONS') {
                res.writeHead(204);
                res.end();
                return;
            }

            try {
                const reqUrl = new URL(req.url, `http://127.0.0.1:${mediaProxyPort}`);
                const targetUrl = reqUrl.searchParams.get('url');

                if (!targetUrl) {
                    res.writeHead(400, { 'Content-Type': 'text/plain' });
                    res.end('Missing url parameter');
                    return;
                }

                let referer = 'https://www.google.com/';
                const lowerUrl = targetUrl.toLowerCase();
                if (lowerUrl.includes('tiktok') || lowerUrl.includes('tikwm') || lowerUrl.includes('byteoversea') || lowerUrl.includes('ibytedtos') || lowerUrl.includes('muscdn') || lowerUrl.includes('douyin')) {
                    referer = 'https://www.tiktok.com/';
                } else if (lowerUrl.includes('twitter') || lowerUrl.includes('twimg') || lowerUrl.includes('x.com') || lowerUrl.includes('fxtwitter') || lowerUrl.includes('vxtwitter') || lowerUrl.includes('t.co')) {
                    referer = 'https://twitter.com/';
                } else if (lowerUrl.includes('instagram') || lowerUrl.includes('cdninstagram') || lowerUrl.includes('fbcdn') || lowerUrl.includes('indown')) {
                    referer = 'https://www.instagram.com/';
                } else if (lowerUrl.includes('sndcdn') || lowerUrl.includes('soundcloud')) {
                    referer = 'https://soundcloud.com/';
                } else if (lowerUrl.includes('googlevideo') || lowerUrl.includes('youtube') || lowerUrl.includes('ytimg') || lowerUrl.includes('youtu.be') || lowerUrl.includes('invidious')) {
                    referer = 'https://www.youtube.com/';
                }

                const fetchHeaders = {
                    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36',
                    'Referer': referer,
                    'Accept-Encoding': 'identity'
                };

                if (req.headers.range) {
                    fetchHeaders['Range'] = req.headers.range;
                }

                const remoteRes = await fetch(targetUrl, {
                    method: req.method,
                    headers: fetchHeaders,
                    redirect: 'follow'
                });

                const resHeaders = {};
                remoteRes.headers.forEach((val, key) => {
                    const lk = key.toLowerCase();
                    // Strip encoding headers because undici in Node automatically decompressed the body.
                    // Passing content-encoding: gzip to Chromium when body is already raw uncompressed causes ERR_CONTENT_DECODING_FAILED!
                    if (!['content-encoding', 'transfer-encoding', 'content-security-policy', 'x-frame-options'].includes(lk) && !lk.startsWith('cross-origin')) {
                        resHeaders[key] = val;
                    }
                });

                // If remote response was compressed, undici automatically decompresses it.
                // In that case, Content-Length from remoteRes is incorrect for the uncompressed stream.
                if (remoteRes.headers.has('content-encoding')) {
                    delete resHeaders['content-length'];
                }

                resHeaders['access-control-allow-origin'] = '*';
                resHeaders['access-control-allow-private-network'] = 'true';
                resHeaders['cross-origin-resource-policy'] = 'cross-origin';
                resHeaders['access-control-expose-headers'] = 'Content-Length, Content-Range, Accept-Ranges, Content-Type';

                res.writeHead(remoteRes.status, resHeaders);

                if (req.method === 'HEAD' || !remoteRes.body) {
                    res.end();
                    return;
                }

                const nodeStream = Readable.fromWeb(remoteRes.body);
                nodeStream.on('error', () => { try { res.destroy(); } catch (_) {} });
                req.on('close', () => { try { nodeStream.destroy(); } catch (_) {} });
                nodeStream.pipe(res);

            } catch (err) {
                console.error('Media proxy error:', err.message);
                if (!res.headersSent) {
                    res.writeHead(502, { 'Content-Type': 'text/plain' });
                }
                res.end('Proxy error: ' + err.message);
            }
        });

        mediaProxyServer.listen(0, '127.0.0.1', () => {
            mediaProxyPort = mediaProxyServer.address().port;
            console.log(`HDify media streaming proxy started on 127.0.0.1:${mediaProxyPort}`);
            resolve(mediaProxyPort);
        });
    });
}

function getProxyStreamUrl(rawUrl) {
    if (!rawUrl || typeof rawUrl !== 'string') return '';
    if (rawUrl.startsWith('data:') || rawUrl.startsWith('blob:') || rawUrl.startsWith('file:')) return rawUrl;
    if (mediaProxyPort > 0) {
        return `http://127.0.0.1:${mediaProxyPort}/stream?url=${encodeURIComponent(rawUrl)}`;
    }
    return rawUrl;
}

// Enable GPU hardware rasterization & Zero-Copy compositing for 120Hz/60Hz smooth animations
app.commandLine.appendSwitch('enable-gpu-rasterization');
app.commandLine.appendSwitch('enable-zero-copy');
app.commandLine.appendSwitch('ignore-gpu-blocklist');
app.commandLine.appendSwitch('enable-accelerated-video-decode');

function getFfmpegPath() {
    const raw = typeof ffmpegStatic === 'string' ? ffmpegStatic : ffmpegStatic.path;
    return app.isPackaged ? raw.replace('app.asar', 'app.asar.unpacked') : raw;
}

function getFfprobePath() {
    const raw = typeof ffprobeStatic === 'string' ? ffprobeStatic : (ffprobeStatic.path || '');
    return app.isPackaged ? raw.replace('app.asar', 'app.asar.unpacked') : raw;
}

function getBinDir(engineName) {
    if (app.isPackaged) {
        const resPath = path.join(process.resourcesPath, 'bin', engineName);
        if (fs.existsSync(resPath)) return resPath;
    }
    return path.join(__dirname, 'bin', engineName);
}

let mainWindow = null;
let activeProcesses = new Map();

function createWindow() {
    const iconPath = path.join(__dirname, 'icon.png');
    const winOpts = {
        width: 1240,
        height: 840,
        minWidth: 1000,
        minHeight: 680,
        frame: false,
        backgroundColor: '#0e0e11',
        webPreferences: {
            preload: path.join(__dirname, 'preload.js'),
            contextIsolation: true,
            nodeIntegration: false,
            webSecurity: false
        }
    };
    if (fs.existsSync(iconPath)) {
        winOpts.icon = iconPath;
    }

    mainWindow = new BrowserWindow(winOpts);

    mainWindow.loadFile(path.join(__dirname, 'index.html'));

    mainWindow.on('closed', () => {
        mainWindow = null;
    });
}

app.whenReady().then(async () => {
    await startMediaProxy();

    // Intercept media stream requests to inject appropriate headers (Referer, Origin, User-Agent)
    // so TikTok, Instagram, Twitter/X, and YouTube media streams can be previewed without 403 Forbidden
    session.defaultSession.webRequest.onBeforeSendHeaders((details, callback) => {
        const url = (details.url || '').toLowerCase();
        let referer = null;
        if (url.includes('tiktok') || url.includes('tikwm') || url.includes('byteoversea') || url.includes('ibytedtos') || url.includes('muscdn') || url.includes('douyin')) {
            referer = 'https://www.tiktok.com/';
        } else if (url.includes('twitter') || url.includes('twimg') || url.includes('x.com') || url.includes('fxtwitter') || url.includes('vxtwitter') || url.includes('t.co')) {
            referer = 'https://twitter.com/';
        } else if (url.includes('instagram') || url.includes('cdninstagram') || url.includes('fbcdn') || url.includes('indown')) {
            referer = 'https://www.instagram.com/';
        } else if (url.includes('sndcdn') || url.includes('soundcloud')) {
            referer = 'https://soundcloud.com/';
        } else if (url.includes('googlevideo') || url.includes('youtube') || url.includes('ytimg') || url.includes('youtu.be') || url.includes('invidious')) {
            referer = 'https://www.youtube.com/';
        }
        if (referer) {
            details.requestHeaders['Referer'] = referer;
            details.requestHeaders['Origin'] = referer.replace(/\/$/, '');
        }
        details.requestHeaders['User-Agent'] = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36';
        callback({ requestHeaders: details.requestHeaders });
    });

    session.defaultSession.webRequest.onHeadersReceived((details, callback) => {
        const responseHeaders = Object.assign({}, details.responseHeaders);
        responseHeaders['access-control-allow-origin'] = ['*'];
        responseHeaders['access-control-allow-headers'] = ['*'];
        responseHeaders['access-control-allow-methods'] = ['GET, HEAD, OPTIONS'];
        responseHeaders['access-control-allow-private-network'] = ['true'];
        responseHeaders['cross-origin-resource-policy'] = ['cross-origin'];
        callback({ responseHeaders });
    });

    createWindow();


    app.on('activate', () => {
        if (BrowserWindow.getAllWindows().length === 0) createWindow();
    });
});

app.on('will-quit', () => {
    if (mediaProxyServer) {
        try { mediaProxyServer.close(); } catch (_) {}
    }
});

app.on('window-all-closed', () => {
    if (process.platform !== 'darwin') app.quit();
});

// Window controls
ipcMain.on('window-minimize', () => mainWindow?.minimize());
ipcMain.on('window-maximize', () => {
    if (mainWindow?.isMaximized()) {
        mainWindow.unmaximize();
    } else {
        mainWindow?.maximize();
    }
});
ipcMain.on('window-close', () => mainWindow?.close());

// Dialog & Filesystem
ipcMain.handle('dialog:openFile', async (event, options = {}) => {
    const filters = [];
    if (options.type === 'image') {
        filters.push({ name: 'Images', extensions: ['jpg', 'jpeg', 'png', 'webp', 'bmp'] });
    } else if (options.type === 'video') {
        filters.push({ name: 'Videos', extensions: ['mp4', 'mkv', 'mov', 'webm', 'avi'] });
    } else {
        filters.push({ name: 'Media Files', extensions: ['jpg', 'jpeg', 'png', 'webp', 'mp4', 'mkv', 'mov', 'webm'] });
    }

    const result = await dialog.showOpenDialog(mainWindow, {
        properties: ['openFile'],
        filters: filters
    });

    if (result.canceled || result.filePaths.length === 0) return null;
    return result.filePaths[0];
});

ipcMain.handle('dialog:openDirectory', async () => {
    const result = await dialog.showOpenDialog(mainWindow, {
        properties: ['openDirectory', 'createDirectory']
    });
    if (result.canceled || result.filePaths.length === 0) return null;
    return result.filePaths[0];
});

ipcMain.handle('fs:showInFolder', (event, targetPath) => {
    if (targetPath && fs.existsSync(targetPath)) {
        shell.showItemInFolder(targetPath);
        return true;
    }
    return false;
});

ipcMain.handle('fs:openExternal', (event, url) => {
    if (url) {
        try {
            if (fs.existsSync(url)) {
                shell.openPath(url);
            } else {
                shell.openExternal(url);
            }
        } catch (_) {
            shell.openExternal(url);
        }
    }
});

ipcMain.handle('fs:openPath', (event, targetPath) => {
    if (targetPath && fs.existsSync(targetPath)) {
        return shell.openPath(targetPath);
    }
    return 'File not found';
});

ipcMain.handle('fs:getDefaultOutputDir', () => {
    const outputDir = path.join(app.getPath('downloads'), 'HDify_Output');
    if (!fs.existsSync(outputDir)) {
        try { fs.mkdirSync(outputDir, { recursive: true }); } catch (_) {}
    }
    return outputDir;
});

ipcMain.handle('fs:saveDataUrl', async (event, { dataUrl, outputPath }) => {
    const base64Data = dataUrl.replace(/^data:image\/\w+;base64,/, '');
    const buffer = Buffer.from(base64Data, 'base64');
    fs.writeFileSync(outputPath, buffer);
    return { success: true, outputPath };
});

ipcMain.handle('fs:saveTempImage', async (event, dataUrl) => {
    const base64Data = dataUrl.replace(/^data:image\/\w+;base64,/, '');
    const buffer = Buffer.from(base64Data, 'base64');
    const tempPath = path.join(os.tmpdir(), `hdify_cutout_${Date.now()}.png`);
    fs.writeFileSync(tempPath, buffer);
    return tempPath;
});

// GitHub Release Auto-Update Checker
ipcMain.handle('system:checkUpdate', async () => {
    return new Promise((resolve) => {
        const https = require('https');
        const currentVersion = app.getVersion() || '2.2.8';
        const options = {
            hostname: 'api.github.com',
            path: '/repos/YuEx-DEV/HDify/releases/latest',
            headers: {
                'User-Agent': `HDify-Desktop-v${currentVersion}`,
                'Accept': 'application/vnd.github.v3+json'
            },
            timeout: 9000
        };

        const req = https.get(options, (res) => {
            let data = '';
            res.on('data', chunk => data += chunk);
            res.on('end', () => {
                if (res.statusCode !== 200) {
                    return resolve({
                        success: false,
                        error: `GitHub API returned HTTP ${res.statusCode}`
                    });
                }
                try {
                    const json = JSON.parse(data);
                    const tag = (json.tag_name || '').trim();
                    const latestVersion = tag.replace(/^v/, '');

                    function isNewer(vNew, vCur) {
                        const n = vNew.split('.').map(x => parseInt(x, 10) || 0);
                        const c = vCur.split('.').map(x => parseInt(x, 10) || 0);
                        for (let i = 0; i < Math.max(n.length, c.length); i++) {
                            const p1 = n[i] || 0;
                            const p2 = c[i] || 0;
                            if (p1 > p2) return true;
                            if (p1 < p2) return false;
                        }
                        return false;
                    }

                    const hasUpdate = isNewer(latestVersion, currentVersion);
                    const assets = Array.isArray(json.assets) ? json.assets : [];
                    const setupAsset = assets.find(a => /setup/i.test(a.name) && a.name.endsWith('.exe'))?.browser_download_url || '';
                    const portableAsset = assets.find(a => /portable/i.test(a.name) && a.name.endsWith('.exe'))?.browser_download_url || '';
                    const apkAsset = assets.find(a => a.name.endsWith('.apk'))?.browser_download_url || '';

                    resolve({
                        success: true,
                        hasUpdate,
                        currentVersion,
                        latestVersion,
                        releaseTag: tag,
                        releaseTitle: json.name || tag,
                        releaseNotes: json.body || '',
                        releaseUrl: json.html_url || 'https://github.com/YuEx-DEV/HDify/releases',
                        setupAsset,
                        portableAsset,
                        apkAsset,
                        publishedAt: json.published_at || ''
                    });
                } catch (parseErr) {
                    resolve({ success: false, error: 'Failed to parse GitHub response' });
                }
            });
        });

        req.on('error', (err) => {
            resolve({ success: false, error: err.message });
        });

        req.on('timeout', () => {
            req.destroy();
            resolve({ success: false, error: 'Connection timeout' });
        });
    });
});

// Hardware Info
ipcMain.handle('system:getHardwareInfo', async () => {
    return new Promise((resolve) => {
        const info = {
            os: `${os.type()} ${os.release()} (${os.arch()})`,
            cpu: os.cpus()[0]?.model || 'Unknown CPU',
            cores: os.cpus().length,
            totalMemoryGb: (os.totalmem() / (1024 ** 3)).toFixed(1),
            gpus: []
        };

        exec('powershell -Command "Get-CimInstance Win32_VideoController | Select-Object -Property Name, AdapterRAM, DriverVersion | ConvertTo-Json"', (err, stdout) => {
            if (!err && stdout.trim()) {
                try {
                    const parsed = JSON.parse(stdout);
                    const list = Array.isArray(parsed) ? parsed : [parsed];
                    info.gpus = list.map(g => ({
                        name: g.Name || 'GPU Adapter',
                        memoryMb: g.AdapterRAM ? Math.round(g.AdapterRAM / (1024 * 1024)) : 0,
                        driver: g.DriverVersion || ''
                    }));
                } catch (_) {}
            }
            if (info.gpus.length === 0) {
                info.gpus.push({ name: 'Vulkan Compatible GPU', memoryMb: 2048, driver: 'Active' });
            }
            resolve(info);
        });
    });
});

// AI Image Upscale Runner
ipcMain.handle('ai:runImageUpscale', async (event, params) => {
    const { inputPath, outputPath, engine, scale = 2, denoise = -1, tta = false, tileSize = 0, customWidth, customHeight, modelName: customModelName, width, height } = params;
    const startTime = Date.now();

    if (!inputPath || !fs.existsSync(inputPath)) {
        throw new Error(`File input tidak ditemukan: ${inputPath}`);
    }

    const targetOutDir = path.dirname(outputPath);
    if (!fs.existsSync(targetOutDir)) {
        try { fs.mkdirSync(targetOutDir, { recursive: true }); } catch (_) {}
    }

    const tempUpscaleOut = (customWidth && customHeight) ? path.join(os.tmpdir(), `hdify_temp_scale_${Date.now()}.png`) : outputPath;
    const tempDir = path.dirname(tempUpscaleOut);
    if (!fs.existsSync(tempDir)) {
        try { fs.mkdirSync(tempDir, { recursive: true }); } catch (_) {}
    }

    // Determine dimensions to compute exact tiles
    let imgWidth = width || 0;
    let imgHeight = height || 0;
    if (!imgWidth || !imgHeight) {
        try {
            const probeRes = execFileSync(getFfprobePath(), [
                '-v', 'error',
                '-select_streams', 'v:0',
                '-show_entries', 'stream=width,height',
                '-of', 'csv=s=x:p=0',
                inputPath
            ], { encoding: 'utf8', timeout: 5000 });
            const parts = probeRes.trim().split('x');
            if (parts.length >= 2) {
                imgWidth = parseInt(parts[0], 10) || 0;
                imgHeight = parseInt(parts[1], 10) || 0;
            }
        } catch (_) {}
    }

    // Default tile size 200 ensures safety from Windows TDR (2s GPU timeout) on all GPUs
    const effectiveTileSize = (tileSize && tileSize > 0) ? tileSize : 200;
    const wTiles = imgWidth > 0 ? Math.ceil(imgWidth / effectiveTileSize) : 8;
    const hTiles = imgHeight > 0 ? Math.ceil(imgHeight / effectiveTileSize) : 6;
    const totalTiles = Math.max(1, wTiles * hTiles);

    let exePath = '';
    let args = [];
    let modelsDir = '';

    if (engine.startsWith('realcugan')) {
        const cuganDir = getBinDir('realcugan');
        exePath = path.join(cuganDir, 'realcugan-ncnn-vulkan.exe');
        let usePro = engine.includes('pro');
        // Real-CUGAN models-pro hanya mendukung skala 2x dan 3x. Jika 4x, otomatis alihkan ke models-se
        if (scale === 4 && usePro) {
            usePro = false;
        }
        modelsDir = usePro ? path.join(cuganDir, 'models-pro') : path.join(cuganDir, 'models-se');

        let safeDenoise = parseInt(denoise);
        if (usePro && (safeDenoise === 1 || safeDenoise === 2)) {
            safeDenoise = 0;
        }

        args = [
            '-i', inputPath,
            '-o', tempUpscaleOut,
            '-s', String(scale),
            '-t', String(effectiveTileSize),
            '-n', String(safeDenoise),
            '-m', modelsDir
        ];
    } else {
        const realesrDir = getBinDir('realesrgan');
        exePath = path.join(realesrDir, 'realesrgan-ncnn-vulkan.exe');
        modelsDir = path.join(realesrDir, 'models');
        let modelName = 'realesrgan-x4plus';
        if (customModelName) modelName = customModelName;
        else if (engine === 'realesrgan-anime' || engine === 'realesrgan-x4plus-anime') modelName = 'realesrgan-x4plus-anime';
        else if (engine === 'realesr-animevideov3' || (engine && engine.includes('animevideo'))) modelName = 'realesr-animevideov3';
        else if (engine && engine.startsWith('realesr')) modelName = engine;

        args = [
            '-i', inputPath,
            '-o', tempUpscaleOut,
            '-s', String(scale),
            '-t', String(effectiveTileSize),
            '-n', modelName,
            '-m', modelsDir
        ];
    }

    if (tta) args.push('-x');

    return new Promise((resolve, reject) => {
        if (!fs.existsSync(exePath)) {
            return reject(new Error(`Engine executable tidak ditemukan: ${exePath}`));
        }

        // Notify client immediately: stage preparing (model loading & Vulkan shader compiling)
        mainWindow?.webContents.send('task-progress', {
            type: 'image-upscale',
            stage: 'preparing',
            percent: 0,
            current: 0,
            total: totalTiles
        });

        const child = spawn(exePath, args, { cwd: path.dirname(exePath) });
        activeProcesses.set('image-upscale', child);

        let isRealPercentageReported = false;
        let currentTile = 0;
        let hasStartedProcessing = false;
        let stderrData = '';

        // Allow ~1.2s for Vulkan pipeline compilation and model weight loading (Preparing stage)
        const prepTimer = setTimeout(() => {
            if (!isRealPercentageReported && activeProcesses.has('image-upscale')) {
                hasStartedProcessing = true;
                currentTile = 1;
                const pct = Math.max(1, Math.round((currentTile / totalTiles) * 100));
                mainWindow?.webContents.send('task-progress', {
                    type: 'image-upscale',
                    stage: 'processing',
                    percent: pct,
                    current: currentTile,
                    total: totalTiles
                });
            }
        }, 1200);

        // Smooth tile progression for engines that don't output per-tile stdout (like Real-CUGAN)
        const msPerTile = tta ? (effectiveTileSize >= 400 ? 3000 : 1500) : (effectiveTileSize >= 400 ? 800 : 350);
        const tileTimer = setInterval(() => {
            if (isRealPercentageReported) {
                clearInterval(tileTimer);
                return;
            }
            if (hasStartedProcessing && currentTile < totalTiles - 1) {
                currentTile++;
                const pct = Math.min(99, Math.max(1, Math.round((currentTile / totalTiles) * 100)));
                mainWindow?.webContents.send('task-progress', {
                    type: 'image-upscale',
                    stage: 'processing',
                    percent: pct,
                    current: currentTile,
                    total: totalTiles
                });
            }
        }, msPerTile);

        const handleOut = (data) => {
            const str = data.toString();
            // Match percentages with both comma and dot (e.g. 0,00% or 0.00%)
            const match = str.match(/(\d+(?:[.,]\d+)?)%/);
            if (match) {
                isRealPercentageReported = true;
                clearTimeout(prepTimer);
                clearInterval(tileTimer);
                const parsed = parseFloat(match[1].replace(',', '.'));
                const pct = Math.max(0, Math.min(100, Math.round(parsed)));
                const curTile = Math.max(1, Math.min(totalTiles, Math.round((pct / 100) * totalTiles)));
                mainWindow?.webContents.send('task-progress', {
                    type: 'image-upscale',
                    stage: 'processing',
                    percent: pct,
                    current: curTile,
                    total: totalTiles,
                    raw: str.trim()
                });
            }
        };

        child.stdout.on('data', handleOut);
        child.stderr.on('data', (d) => {
            stderrData += d.toString();
            handleOut(d);
        });

        child.on('close', (code) => {
            clearTimeout(prepTimer);
            clearInterval(tileTimer);
            activeProcesses.delete('image-upscale');
            const durationMs = Date.now() - startTime;
            if (code === 0 && fs.existsSync(tempUpscaleOut)) {
                mainWindow?.webContents.send('task-progress', {
                    type: 'image-upscale',
                    stage: 'completed',
                    percent: 100,
                    current: totalTiles,
                    total: totalTiles
                });
                if (customWidth && customHeight) {
                    // Post-process to exact custom resolution using ffmpeg lanczos
                    const resizeArgs = [
                        '-y', '-i', tempUpscaleOut,
                        '-vf', `scale=${parseInt(customWidth)}:${parseInt(customHeight)}:flags=lanczos`,
                        outputPath
                    ];
                    execFile(getFfmpegPath(), resizeArgs, (rErr) => {
                        try { if (fs.existsSync(tempUpscaleOut)) fs.unlinkSync(tempUpscaleOut); } catch (_) {}
                        if (!rErr && fs.existsSync(outputPath)) {
                            resolve({ success: true, outputPath, durationMs });
                        } else {
                            reject(rErr || new Error('Gagal menerapkan resolusi target kustom'));
                        }
                    });
                } else {
                    resolve({ success: true, outputPath, durationMs });
                }
            } else {
                const detail = stderrData.trim() ? `: ${stderrData.trim()}` : '';
                reject(new Error(`AI Upscale gagal dengan exit code: ${code}${detail}`));
            }
        });

        child.on('error', (err) => {
            clearTimeout(prepTimer);
            clearInterval(tileTimer);
            activeProcesses.delete('image-upscale');
            reject(err);
        });
    });
});

ipcMain.handle('ai:cancelImageUpscale', () => {
    const child = activeProcesses.get('image-upscale');
    if (child) {
        child.kill();
        activeProcesses.delete('image-upscale');
        return true;
    }
    return false;
});

// Quick 1-Second Crop Preview (Center 300x300 tile)
ipcMain.handle('ai:runImageCropPreview', async (event, params) => {
    const { inputPath, engine = 'realcugan-se', scale = 2, denoise = -1, modelName: customModelName } = params;
    const tempCropIn = path.join(os.tmpdir(), `hdify_crop_in_${Date.now()}.png`);
    const tempCropOut = path.join(os.tmpdir(), `hdify_crop_out_${Date.now()}.png`);

    await new Promise((resolve, reject) => {
        const cropArgs = [
            '-y', '-i', inputPath,
            '-vf', 'crop=min(iw\\,300):min(ih\\,300):(iw-out_w)/2:(ih-out_h)/2',
            tempCropIn
        ];
        execFile(getFfmpegPath(), cropArgs, (err) => {
            if (err) reject(err);
            else resolve();
        });
    });

    let exePath = '';
    let args = [];
    if (engine.startsWith('realcugan')) {
        const cuganDir = getBinDir('realcugan');
        exePath = path.join(cuganDir, 'realcugan-ncnn-vulkan.exe');
        let usePro = engine.includes('pro');
        if (scale === 4 && usePro) usePro = false;
        const modelsDir = usePro ? path.join(cuganDir, 'models-pro') : path.join(cuganDir, 'models-se');
        let safeDenoise = parseInt(denoise);
        if (usePro && (safeDenoise === 1 || safeDenoise === 2)) safeDenoise = 0;
        args = ['-i', tempCropIn, '-o', tempCropOut, '-s', String(scale), '-n', String(safeDenoise), '-m', modelsDir];
    } else {
        const realesrDir = getBinDir('realesrgan');
        exePath = path.join(realesrDir, 'realesrgan-ncnn-vulkan.exe');
        const modelsDir = path.join(realesrDir, 'models');
        let modelName = 'realesrgan-x4plus';
        if (customModelName) modelName = customModelName;
        else if (engine === 'realesrgan-anime' || engine === 'realesrgan-x4plus-anime') modelName = 'realesrgan-x4plus-anime';
        else if (engine === 'realesr-animevideov3' || (engine && engine.includes('animevideo'))) modelName = 'realesr-animevideov3';
        else if (engine && engine.startsWith('realesr')) modelName = engine;
        args = ['-i', tempCropIn, '-o', tempCropOut, '-s', String(scale), '-n', modelName, '-m', modelsDir];
    }

    return new Promise((resolve, reject) => {
        execFile(exePath, args, { cwd: path.dirname(exePath) }, (err, stdout, stderr) => {
            try { if (fs.existsSync(tempCropIn)) fs.unlinkSync(tempCropIn); } catch (_) {}
            if (!err && fs.existsSync(tempCropOut)) {
                resolve({ success: true, outputPath: tempCropOut });
            } else {
                const detail = stderr ? `: ${stderr.trim()}` : (err ? `: ${err.message}` : '');
                reject(new Error(`Gagal membuat pratinjau crop${detail}`));
            }
        });
    });
});

// Video Metadata & Compression
ipcMain.handle('video:getMetadata', async (event, filePath) => {
    return new Promise((resolve, reject) => {
        if (!filePath || typeof filePath !== 'string' || !filePath.trim()) {
            return reject(new Error('Path file video tidak valid atau kosong. Silakan pilih kembali file video.'));
        }
        if (!fs.existsSync(filePath)) {
            return reject(new Error(`File video tidak ditemukan di path: ${filePath}`));
        }

        const args = [
            '-v', 'error',
            '-print_format', 'json',
            '-show_format',
            '-show_streams',
            filePath
        ];

        execFile(getFfprobePath(), args, { maxBuffer: 10 * 1024 * 1024 }, (err, stdout, stderr) => {
            if (err) return reject(new Error(stderr?.trim() || err.message));
            try {
                const data = JSON.parse(stdout);
                const videoStream = data.streams?.find(s => s.codec_type === 'video');
                const format = data.format || {};

                let fps = 30;
                if (videoStream?.r_frame_rate) {
                    const [num, den] = videoStream.r_frame_rate.split('/');
                    if (den && Number(den) > 0) fps = Math.round(Number(num) / Number(den));
                }

                resolve({
                    width: videoStream?.width || 0,
                    height: videoStream?.height || 0,
                    fps: fps,
                    duration: parseFloat(format.duration || videoStream?.duration || 0),
                    sizeBytes: parseInt(format.size || 0),
                    bitrate: parseInt(format.bit_rate || videoStream?.bit_rate || 0),
                    codec: videoStream?.codec_name || 'unknown'
                });
            } catch (e) {
                reject(new Error(`Gagal mem-parsing metadata video: ${e.message}`));
            }
        });
    });
});

let currentUpscaleTempDir = null;

async function runVulkanAiVideoUpscale(params, aiConfig) {
    const {
        inputPath,
        outputPath,
        bitrateQuality = 85,
        bitrateMbps,
        isPreview = false,
        sourceFps = 30,
        uhdMode = false,
        rifeMultiplier = '2x',
        videoModel = 'realesr-animevideov3'
    } = params;

    const {
        canRunRife,
        canRunEsrgan,
        rifeExe,
        rifeModelDir,
        esrganExe,
        esrganModelsDir,
        videoCodec,
        scaleFactor
    } = aiConfig;

    const tempDir = path.join(os.tmpdir(), 'hdify_vpipe_' + Date.now());
    currentUpscaleTempDir = tempDir;

    const framesIn = path.join(tempDir, 'in');
    const framesRife = path.join(tempDir, 'rife');
    const framesUpscale = path.join(tempDir, 'upscale');
    const tempAudio = path.join(tempDir, 'audio.aac');

    fs.mkdirSync(framesIn, { recursive: true });

    const cleanup = () => {
        try {
            if (fs.existsSync(tempDir)) {
                fs.rmSync(tempDir, { recursive: true, force: true });
            }
        } catch (_) {}
        currentUpscaleTempDir = null;
    };

    return new Promise(async (resolve, reject) => {
        try {
            // Stage 1: Frame & Audio Extraction
            const estimatedFrames = Math.max(1, Math.round((sourceFps || 30) * (isPreview ? 3 : (params.duration || 10))));
            mainWindow?.webContents.send('task-progress', {
                type: 'video-upscale',
                percent: 3,
                currentFrame: 0,
                totalFrames: estimatedFrames,
                stage: 'Tahap 1/3: Ekstraksi Frame & Audio Sumber...',
                status: 'Tahap 1/3: Ekstraksi Frame & Audio Sumber...'
            });

            const extractArgs = ['-y', '-i', inputPath];
            if (isPreview) extractArgs.push('-t', '3');
            extractArgs.push('-q:v', '2', path.join(framesIn, '%08d.png'));

            await new Promise((resExtract, rejExtract) => {
                const child = spawn(getFfmpegPath(), extractArgs);
                activeProcesses.set('video-upscale', child);

                child.stderr.on('data', (data) => {
                    const str = data.toString();
                    const frameMatch = str.match(/frame=\s*(\d+)/);
                    if (frameMatch) {
                        const cur = parseInt(frameMatch[1]);
                        const p = Math.min(14, Math.max(3, Math.round((cur / estimatedFrames) * 14)));
                        mainWindow?.webContents.send('task-progress', {
                            type: 'video-upscale',
                            percent: p,
                            currentFrame: cur,
                            totalFrames: estimatedFrames,
                            stage: 'Tahap 1/3: Ekstraksi Frame & Audio Sumber...',
                            status: 'Tahap 1/3: Ekstraksi Frame & Audio Sumber...'
                        });
                    }
                });

                child.on('close', code => (code === 0 ? resExtract() : rejExtract(new Error(`Gagal mengekstrak frame video (exit code: ${code})`))));
                child.on('error', rejExtract);
            });

            // Extract audio track (optional)
            const audioArgs = ['-y', '-i', inputPath];
            if (isPreview) audioArgs.push('-t', '3');
            audioArgs.push('-vn', '-c:a', 'aac', '-b:a', '192k', tempAudio);
            let hasAudio = false;
            await new Promise(resAudio => {
                const child = spawn(getFfmpegPath(), audioArgs);
                child.on('close', code => {
                    hasAudio = (code === 0 && fs.existsSync(tempAudio) && fs.statSync(tempAudio).size > 0);
                    resAudio();
                });
                child.on('error', () => resAudio());
            });

            const inFrames = fs.readdirSync(framesIn).filter(f => f.endsWith('.png'));
            const totalInFrames = inFrames.length;
            if (totalInFrames === 0) {
                cleanup();
                return reject(new Error('Tidak ada frame yang berhasil diekstrak dari video sumber.'));
            }

            let mult = rifeMultiplier === '4x' ? 4 : (rifeMultiplier === '3x' ? 3 : 2);
            let currentFramesDir = framesIn;

            // Stage 2A: RIFE v4.6 FlowNet (FPS Multiplier)
            if (canRunRife) {
                fs.mkdirSync(framesRife, { recursive: true });
                const targetFrames = totalInFrames * mult;
                const rifeArgs = ['-v', '-i', framesIn, '-o', framesRife, '-m', rifeModelDir, '-n', String(targetFrames)];
                if (uhdMode) rifeArgs.push('-u');

                mainWindow?.webContents.send('task-progress', {
                    type: 'video-upscale',
                    percent: 15,
                    currentFrame: 0,
                    totalFrames: targetFrames,
                    stage: `Tahap 2/3: AI RIFE v4.6 FlowNet (${mult}x FPS Vulkan)...`,
                    status: `Tahap 2/3: AI RIFE v4.6 FlowNet (${mult}x FPS Vulkan)...`
                });

                await new Promise((resRife, rejRife) => {
                    const child = spawn(rifeExe, rifeArgs, { cwd: path.dirname(rifeExe) });
                    activeProcesses.set('video-upscale', child);

                    const rifeStartTime = Date.now();
                    let lastReported = 0;

                    const reportRifeProgress = (doneCount) => {
                        if (doneCount <= lastReported && doneCount > 0) return;
                        lastReported = doneCount;
                        const ratio = Math.min(1.0, doneCount / targetFrames);
                        const base = 15;
                        const range = canRunEsrgan ? 35 : 70;
                        const overallPct = Math.min(canRunEsrgan ? 49 : 85, Math.round(base + ratio * range));

                        const elapsedSec = (Date.now() - rifeStartTime) / 1000;
                        const fps = elapsedSec > 0.5 ? (doneCount / elapsedSec).toFixed(1) : '0.0';
                        const remaining = Math.max(0, targetFrames - doneCount);
                        const etaSec = Number(fps) > 0 ? Math.round(remaining / Number(fps)) : 0;

                        mainWindow?.webContents.send('task-progress', {
                            type: 'video-upscale',
                            percent: overallPct,
                            currentFrame: doneCount,
                            totalFrames: targetFrames,
                            fps: fps,
                            etaSec: etaSec,
                            stage: `Tahap 2/3: AI RIFE v4.6 FlowNet (${mult}x FPS Vulkan)...`,
                            status: `Tahap 2/3: AI RIFE v4.6 FlowNet (${mult}x FPS Vulkan)...`
                        });
                    };

                    const pollTimer = setInterval(() => {
                        try {
                            if (fs.existsSync(framesRife)) {
                                const count = fs.readdirSync(framesRife).filter(f => f.endsWith('.png')).length;
                                reportRifeProgress(count);
                            }
                        } catch (_) {}
                    }, 250);

                    child.stderr.on('data', data => {
                        const str = data.toString();
                        if (str.includes('done')) {
                            try {
                                const count = fs.readdirSync(framesRife).filter(f => f.endsWith('.png')).length;
                                reportRifeProgress(count);
                            } catch (_) {}
                        }
                    });

                    child.on('close', code => {
                        clearInterval(pollTimer);
                        try {
                            const count = fs.readdirSync(framesRife).filter(f => f.endsWith('.png')).length;
                            reportRifeProgress(count);
                        } catch (_) {}
                        code === 0 ? resRife() : rejRife(new Error(`AI RIFE gagal dengan exit code: ${code}`));
                    });
                    child.on('error', err => {
                        clearInterval(pollTimer);
                        rejRife(err);
                    });
                });

                currentFramesDir = framesRife;
            }

            // Stage 2B: Real-ESRGAN Super-Resolution
            if (canRunEsrgan) {
                fs.mkdirSync(framesUpscale, { recursive: true });
                const upscaleInCount = fs.readdirSync(currentFramesDir).filter(f => f.endsWith('.png')).length;
                let chosenModel = videoModel || 'realesr-animevideov3';
                if (!['realesr-animevideov3', 'realesrgan-x4plus', 'realesrgan-x4plus-anime'].includes(chosenModel)) {
                    chosenModel = 'realesr-animevideov3';
                }
                const esrArgs = ['-i', currentFramesDir, '-o', framesUpscale, '-s', String(scaleFactor), '-n', chosenModel, '-m', esrganModelsDir];

                let modelLabel = 'Real-ESRGAN AnimeVideoV3';
                if (chosenModel === 'realesrgan-x4plus') modelLabel = 'Real-ESRGAN x4plus (Foto Realistik)';
                else if (chosenModel === 'realesrgan-x4plus-anime') modelLabel = 'Real-ESRGAN x4plus Anime';

                mainWindow?.webContents.send('task-progress', {
                    type: 'video-upscale',
                    percent: canRunRife ? 50 : 15,
                    currentFrame: 0,
                    totalFrames: upscaleInCount,
                    stage: `Tahap 2/3: ${modelLabel} Super-Resolution (x${scaleFactor})...`,
                    status: `Tahap 2/3: ${modelLabel} Super-Resolution (x${scaleFactor})...`
                });

                await new Promise((resEsr, rejEsr) => {
                    const child = spawn(esrganExe, esrArgs, { cwd: path.dirname(esrganExe) });
                    activeProcesses.set('video-upscale', child);

                    const esrStartTime = Date.now();
                    let lastReportedEsr = 0;

                    const reportEsrProgress = (doneCount) => {
                        if (doneCount <= lastReportedEsr && doneCount > 0) return;
                        lastReportedEsr = doneCount;
                        const ratio = Math.min(1.0, doneCount / upscaleInCount);
                        const base = canRunRife ? 50 : 15;
                        const range = canRunRife ? 35 : 70;
                        const overallPct = Math.min(85, Math.round(base + ratio * range));

                        const elapsedSec = (Date.now() - esrStartTime) / 1000;
                        const fps = elapsedSec > 0.5 ? (doneCount / elapsedSec).toFixed(1) : '0.0';
                        const remaining = Math.max(0, upscaleInCount - doneCount);
                        const etaSec = Number(fps) > 0 ? Math.round(remaining / Number(fps)) : 0;

                        mainWindow?.webContents.send('task-progress', {
                            type: 'video-upscale',
                            percent: overallPct,
                            currentFrame: doneCount,
                            totalFrames: upscaleInCount,
                            fps: fps,
                            etaSec: etaSec,
                            stage: `Tahap 2/3: ${modelLabel} Super-Resolution (x${scaleFactor})...`,
                            status: `Tahap 2/3: ${modelLabel} Super-Resolution (x${scaleFactor})...`
                        });
                    };

                    const esrPollTimer = setInterval(() => {
                        try {
                            if (fs.existsSync(framesUpscale)) {
                                const count = fs.readdirSync(framesUpscale).filter(f => f.endsWith('.png')).length;
                                reportEsrProgress(count);
                            }
                        } catch (_) {}
                    }, 250);

                    child.stderr.on('data', () => {
                        try {
                            const count = fs.readdirSync(framesUpscale).filter(f => f.endsWith('.png')).length;
                            reportEsrProgress(count);
                        } catch (_) {}
                    });

                    child.on('close', code => {
                        clearInterval(esrPollTimer);
                        try {
                            const count = fs.readdirSync(framesUpscale).filter(f => f.endsWith('.png')).length;
                            reportEsrProgress(count);
                        } catch (_) {}
                        code === 0 ? resEsr() : rejEsr(new Error(`Real-ESRGAN upscale gagal dengan exit code: ${code}`));
                    });
                    child.on('error', err => {
                        clearInterval(esrPollTimer);
                        rejEsr(err);
                    });
                });

                currentFramesDir = framesUpscale;
            }

            // Stage 3: Encoding & Muxing back to MP4
            const targetFps = Math.min(120, Math.round((sourceFps || 30) * (canRunRife ? mult : 1)));
            const muxArgs = [
                '-y',
                '-framerate', String(targetFps),
                '-i', path.join(currentFramesDir, '%08d.png')
            ];

            if (hasAudio) {
                muxArgs.push('-i', tempAudio, '-c:a', 'aac', '-b:a', '192k');
            }

            const qualityPct = Math.max(10, Math.min(100, parseInt(bitrateQuality) || 85));
            const computedBitrateK = (bitrateMbps && Number(bitrateMbps) > 0)
                ? Math.round(bitrateMbps * 1000)
                : Math.round(14000 * (qualityPct / 100) * (scaleFactor > 1 ? scaleFactor * 0.75 : 1));

            muxArgs.push(
                '-c:v', videoCodec,
                '-preset', 'medium',
                '-crf', '18',
                '-b:v', `${computedBitrateK}k`,
                '-pix_fmt', 'yuv420p',
                '-movflags', '+faststart',
                outputPath
            );

            const totalMuxFrames = fs.readdirSync(currentFramesDir).filter(f => f.endsWith('.png')).length;

            mainWindow?.webContents.send('task-progress', {
                type: 'video-upscale',
                percent: 86,
                currentFrame: 0,
                totalFrames: totalMuxFrames,
                stage: `Tahap 3/3: Muxing & Encoding MP4 (${videoCodec === 'libx265' ? 'HEVC H.265' : 'H.264 AVC'})...`,
                status: `Tahap 3/3: Muxing & Encoding MP4 (${videoCodec === 'libx265' ? 'HEVC H.265' : 'H.264 AVC'})...`
            });

            await new Promise((resMux, rejMux) => {
                const child = spawn(getFfmpegPath(), muxArgs);
                activeProcesses.set('video-upscale', child);

                child.stderr.on('data', (data) => {
                    const str = data.toString();
                    const frameMatch = str.match(/frame=\s*(\d+)/);
                    const fpsMatch = str.match(/fps=\s*([\d\.]+)/);
                    const speedMatch = str.match(/speed=\s*([\d\.]+)x/);
                    if (frameMatch && totalMuxFrames > 0) {
                        const cur = parseInt(frameMatch[1]);
                        const curFps = fpsMatch ? parseFloat(fpsMatch[1]) : 0;
                        const curSpeed = speedMatch ? speedMatch[1] + 'x' : '';
                        const p = Math.min(99, Math.round(86 + (cur / totalMuxFrames) * 13));
                        const remaining = Math.max(0, totalMuxFrames - cur);
                        const etaSec = curFps > 0 ? Math.round(remaining / curFps) : 0;

                        mainWindow?.webContents.send('task-progress', {
                            type: 'video-upscale',
                            percent: p,
                            currentFrame: cur,
                            totalFrames: totalMuxFrames,
                            fps: curFps,
                            speed: curSpeed,
                            etaSec: etaSec,
                            stage: `Tahap 3/3: Muxing & Encoding MP4 (${videoCodec === 'libx265' ? 'HEVC H.265' : 'H.264 AVC'})...`,
                            status: `Tahap 3/3: Muxing & Encoding MP4 (${videoCodec === 'libx265' ? 'HEVC H.265' : 'H.264 AVC'})...`
                        });
                    }
                });

                child.on('close', code => (code === 0 ? resMux() : rejMux(new Error(`Muxing video gagal dengan exit code: ${code}`))));
                child.on('error', rejMux);
            });

            cleanup();
            activeProcesses.delete('video-upscale');

            if (fs.existsSync(outputPath)) {
                const stat = fs.statSync(outputPath);
                mainWindow?.webContents.send('task-progress', {
                    type: 'video-upscale',
                    percent: 100,
                    completed: true,
                    outputPath,
                    sizeBytes: stat.size
                });
                resolve({ success: true, outputPath, sizeBytes: stat.size, isPreview });
            } else {
                reject(new Error('File output video tidak ditemukan setelah pemrosesan selesai.'));
            }
        } catch (err) {
            cleanup();
            activeProcesses.delete('video-upscale');
            reject(err);
        }
    });
}

ipcMain.handle('video:runCompress', async (event, params) => {
    const { inputPath, outputPath, targetBitrateMbps, codec = 'h264', resolution = 'original', forceHdr = false, duration = 0, sourceFps = 30 } = params;

    let videoCodec = codec === 'hevc' ? 'libx265' : 'libx264';
    let vfFilters = [];

    if (resolution === '1080p') {
        vfFilters.push('scale=-2:1080');
    } else if (resolution === '720p') {
        vfFilters.push('scale=-2:720');
    }

    if (forceHdr) {
        // Adaptive shadow recovery & vibrancy - prevents crushed dark areas
        vfFilters.push('eq=contrast=1.06:brightness=0.03:saturation=1.22,curves=all=\'0/0.04 0.5/0.52 1/1\'');
    }

    let filterArg = vfFilters.length > 0 ? ['-vf', vfFilters.join(',')] : [];
    const bitrateK = Math.round(targetBitrateMbps * 1000);

    const codecParams = videoCodec === 'libx264' ? [
        '-profile:v', 'high',
        '-level:v', '4.2',
        '-preset', 'medium',
        '-g', '30',
        '-pix_fmt', 'yuv420p'
    ] : [
        '-profile:v', 'main',
        '-preset', 'medium',
        '-g', '30',
        '-pix_fmt', 'yuv420p'
    ];

    const args = [
        '-y',
        '-i', inputPath,
        ...filterArg,
        '-c:v', videoCodec,
        ...codecParams,
        '-b:v', `${bitrateK}k`,
        '-maxrate', `${Math.round(bitrateK * 1.35)}k`,
        '-bufsize', `${Math.round(bitrateK * 2)}k`,
        '-c:a', 'aac',
        '-b:a', '192k',
        '-movflags', '+faststart',
        outputPath
    ];

    return new Promise((resolve, reject) => {
        const child = spawn(getFfmpegPath(), args);
        activeProcesses.set('video-compress', child);

        let totalDuration = duration || 0;
        const totalFrames = (sourceFps && totalDuration > 0) ? Math.round(totalDuration * sourceFps) : 0;

        child.stderr.on('data', (data) => {
            const str = data.toString();
            const timeMatch = str.match(/time=(\d+):(\d+):(\d+\.\d+)/);
            const frameMatch = str.match(/frame=\s*(\d+)/);
            const fpsMatch = str.match(/fps=\s*([\d\.]+)/);
            const speedMatch = str.match(/speed=\s*([\d\.]+)x/);

            let percent = 0;
            let currentSec = 0;
            if (timeMatch && totalDuration > 0) {
                const hours = parseInt(timeMatch[1]);
                const minutes = parseInt(timeMatch[2]);
                const seconds = parseFloat(timeMatch[3]);
                currentSec = hours * 3600 + minutes * 60 + seconds;
                percent = Math.min(99, Math.round((currentSec / totalDuration) * 100));
            }

            const currentFrame = frameMatch ? parseInt(frameMatch[1]) : 0;
            const currentFps = fpsMatch ? parseFloat(fpsMatch[1]) : 0;
            const currentSpeed = speedMatch ? speedMatch[1] + 'x' : '';

            let etaSec = 0;
            if (totalDuration > 0 && currentSec > 0 && currentSec < totalDuration) {
                const speedVal = speedMatch ? parseFloat(speedMatch[1]) : 1.0;
                if (speedVal > 0) {
                    etaSec = Math.max(1, Math.round((totalDuration - currentSec) / speedVal));
                }
            }

            mainWindow?.webContents.send('task-progress', {
                type: 'video-compress',
                percent,
                currentFrame,
                totalFrames,
                fps: currentFps,
                speed: currentSpeed,
                etaSec,
                stage: `Mengompres Video (${videoCodec === 'libx265' ? 'HEVC H.265' : 'H.264 AVC'})...`
            });
        });

        child.on('close', (code) => {
            activeProcesses.delete('video-compress');
            if (code === 0 && fs.existsSync(outputPath)) {
                const stat = fs.statSync(outputPath);
                mainWindow?.webContents.send('task-progress', {
                    type: 'video-compress',
                    percent: 100,
                    completed: true,
                    outputPath,
                    sizeBytes: stat.size
                });
                resolve({ success: true, outputPath, sizeBytes: stat.size });
            } else {
                reject(new Error(`Kompresi video gagal (code: ${code})`));
            }
        });

        child.on('error', (err) => {
            activeProcesses.delete('video-compress');
            reject(err);
        });
    });
});

ipcMain.handle('video:cancelCompress', () => {
    const child = activeProcesses.get('video-compress');
    if (child) {
        try { child.kill(); } catch (_) {}
        activeProcesses.delete('video-compress');
        return true;
    }
    return false;
});

// Video Upscale & Frame Interpolation (True Vulkan Neural Network Pipeline)
ipcMain.handle('video:runUpscale', async (event, params) => {
    const {
        inputPath,
        outputPath,
        scale = 1,
        fps = 'original',
        rifeEnabled = false,
        rifeMultiplier = '2x',
        uhdMode = false,
        codec = 'hevc',
        bitrateQuality = 85,
        bitrateMbps,
        isPreview = false,
        sourceFps = 30,
        duration = 0
    } = params;

    const scaleFactor = parseInt(scale) || 1;
    let videoCodec = (codec === 'h264' || codec === 'avc') ? 'libx264' : 'libx265';

    const rifeDir = getBinDir('rife');
    const rifeExe = path.join(rifeDir, 'rife-ncnn-vulkan-20221029-windows', 'rife-ncnn-vulkan.exe');
    const rifeModelDir = path.join(rifeDir, 'rife-v4.6');

    const esrganDir = getBinDir('realesrgan');
    const esrganExe = path.join(esrganDir, 'realesrgan-ncnn-vulkan.exe');
    const esrganModelsDir = path.join(esrganDir, 'models');

    const canRunRife = rifeEnabled && fs.existsSync(rifeExe) && fs.existsSync(rifeModelDir);
    const canRunEsrgan = scaleFactor > 1 && fs.existsSync(esrganExe) && fs.existsSync(esrganModelsDir);

    // If RIFE or Real-ESRGAN is enabled and available, run the real Vulkan AI pipeline
    if (canRunRife || canRunEsrgan) {
        return runVulkanAiVideoUpscale(params, {
            canRunRife,
            canRunEsrgan,
            rifeExe,
            rifeModelDir,
            esrganExe,
            esrganModelsDir,
            videoCodec,
            scaleFactor
        });
    }

    // Direct hardware-accelerated encode
    let vfFilters = [];
    if (scaleFactor === 2) vfFilters.push('scale=iw*2:ih*2:flags=lanczos');
    else if (scaleFactor === 3) vfFilters.push('scale=iw*3:ih*3:flags=lanczos');
    else if (scaleFactor === 4) vfFilters.push('scale=iw*4:ih*4:flags=lanczos');

    let filterArg = vfFilters.length > 0 ? ['-vf', vfFilters.join(',')] : [];
    const args = ['-y', '-i', inputPath];
    if (isPreview) args.push('-t', '3');

    args.push(...filterArg, '-c:v', videoCodec, '-preset', 'medium', '-crf', '18');

    if (bitrateMbps && Number(bitrateMbps) > 0) {
        args.push('-b:v', `${Math.round(bitrateMbps * 1000)}k`);
    } else {
        const qualityPct = Math.max(10, Math.min(100, parseInt(bitrateQuality) || 85));
        const computedBitrateK = Math.round(14000 * (qualityPct / 100) * (scaleFactor > 1 ? scaleFactor * 0.75 : 1));
        args.push('-b:v', `${computedBitrateK}k`);
    }

    args.push(
        '-c:a', 'aac',
        '-b:a', '192k',
        '-movflags', '+faststart',
        outputPath
    );

    return new Promise((resolve, reject) => {
        const child = spawn(getFfmpegPath(), args);
        activeProcesses.set('video-upscale', child);

        let totalDuration = isPreview ? 3 : (duration || 0);
        const totalFrames = (sourceFps && totalDuration > 0) ? Math.round(totalDuration * sourceFps) : 0;

        child.stderr.on('data', (data) => {
            const str = data.toString();
            const timeMatch = str.match(/time=(\d+):(\d+):(\d+\.\d+)/);
            const frameMatch = str.match(/frame=\s*(\d+)/);
            const fpsMatch = str.match(/fps=\s*([\d\.]+)/);
            const speedMatch = str.match(/speed=\s*([\d\.]+)x/);

            let percent = 0;
            let currentSec = 0;
            if (timeMatch && totalDuration > 0) {
                const hours = parseInt(timeMatch[1]);
                const minutes = parseInt(timeMatch[2]);
                const seconds = parseFloat(timeMatch[3]);
                currentSec = hours * 3600 + minutes * 60 + seconds;
                percent = Math.min(99, Math.round((currentSec / totalDuration) * 100));
            }

            const currentFrame = frameMatch ? parseInt(frameMatch[1]) : 0;
            const currentFps = fpsMatch ? parseFloat(fpsMatch[1]) : 0;
            const currentSpeed = speedMatch ? speedMatch[1] + 'x' : '';

            let etaSec = 0;
            if (totalDuration > 0 && currentSec > 0 && currentSec < totalDuration) {
                const speedVal = speedMatch ? parseFloat(speedMatch[1]) : 1.0;
                if (speedVal > 0) {
                    etaSec = Math.max(1, Math.round((totalDuration - currentSec) / speedVal));
                }
            }

            mainWindow?.webContents.send('task-progress', {
                type: 'video-upscale',
                percent,
                currentFrame,
                totalFrames,
                fps: currentFps,
                speed: currentSpeed,
                etaSec,
                stage: `Meng-encode Video (${videoCodec === 'libx265' ? 'HEVC H.265' : 'H.264 AVC'})...`,
                status: `Meng-encode Video (${videoCodec === 'libx265' ? 'HEVC H.265' : 'H.264 AVC'})...`
            });
        });

        child.on('close', (code) => {
            activeProcesses.delete('video-upscale');
            if (code === 0 && fs.existsSync(outputPath)) {
                const stat = fs.statSync(outputPath);
                mainWindow?.webContents.send('task-progress', {
                    type: 'video-upscale',
                    percent: 100,
                    completed: true,
                    outputPath,
                    sizeBytes: stat.size
                });
                resolve({ success: true, outputPath, sizeBytes: stat.size, isPreview });
            } else {
                reject(new Error(`Peningkatan video gagal (code: ${code})`));
            }
        });

        child.on('error', (err) => {
            activeProcesses.delete('video-upscale');
            reject(err);
        });
    });
});

ipcMain.handle('video:cancelUpscale', () => {
    const child = activeProcesses.get('video-upscale');
    if (child) {
        try { child.kill(); } catch (_) {}
        activeProcesses.delete('video-upscale');
    }
    if (currentUpscaleTempDir && fs.existsSync(currentUpscaleTempDir)) {
        try { fs.rmSync(currentUpscaleTempDir, { recursive: true, force: true }); } catch (_) {}
        currentUpscaleTempDir = null;
    }
    return true;
});

// Multiplatform Media Downloader (TikTok, Twitter/X, YouTube, Instagram, SoundCloud, Douyin, Web)

// 1. Resolve HTTP Redirects (e.g. vt.tiktok.com, t.co, youtu.be, instagram.com/share/)
async function resolveRedirectUrl(rawUrl) {
    if (!rawUrl || typeof rawUrl !== 'string') return '';
    const clean = rawUrl.trim();
    if (/vt\.tiktok\.com|vm\.tiktok\.com|v\.douyin\.com|t\.co|youtu\.be|instagram\.com\/share|bit\.ly|tinyurl\.com/i.test(clean)) {
        try {
            const res = await fetch(clean, {
                method: 'HEAD',
                headers: { 'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36' },
                redirect: 'follow',
                signal: AbortSignal.timeout(6000)
            });
            if (res.url && res.url !== clean) {
                return res.url;
            }
        } catch (_) {
            try {
                const gRes = await fetch(clean, {
                    method: 'GET',
                    headers: { 'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36' },
                    redirect: 'follow',
                    signal: AbortSignal.timeout(6000)
                });
                if (gRes.url && gRes.url !== clean) return gRes.url;
            } catch (_) {}
        }
    }
    return clean;
}

// 2. Strict Media Integrity Verification
function verifyDownloadedMedia(filePath, format) {
    if (!fs.existsSync(filePath)) {
        return { valid: false, reason: 'File hasil unduhan tidak ditemukan di disk.' };
    }
    const stats = fs.statSync(filePath);
    if (stats.size === 0) {
        return { valid: false, reason: 'Ukuran file 0 byte (unduhan kosong).' };
    }

    const fd = fs.openSync(filePath, 'r');
    const buf = Buffer.alloc(Math.min(512, stats.size));
    fs.readSync(fd, buf, 0, buf.length, 0);
    fs.closeSync(fd);

    const firstStr = buf.toString('utf8', 0, Math.min(buf.length, 256)).toLowerCase().trim();
    if (firstStr.startsWith('<!doctype') || firstStr.startsWith('<html') || firstStr.includes('<head') ||
        firstStr.includes('<body') || firstStr.startsWith('{"error"') || firstStr.startsWith('{"status"') ||
        firstStr.includes('making sure you\'re not a bot') || firstStr.includes('access denied')) {
        return { valid: false, reason: 'Tautan dialihkan ke halaman web verifikasi / captcha bot, bukan file media asli.' };
    }

    if (format === 'VIDEO') {
        if (stats.size < 10 * 1024) {
            return { valid: false, reason: `Ukuran file video terlalu kecil (${stats.size} bytes). File rusak atau terputus.` };
        }
        const hasFtyp = buf.indexOf(Buffer.from('ftyp')) !== -1 || buf.indexOf(Buffer.from('moov')) !== -1 || buf.indexOf(Buffer.from('mdat')) !== -1;
        const isEbml = buf[0] === 0x1A && buf[1] === 0x45 && buf[2] === 0xDF && buf[3] === 0xA3;
        if (!hasFtyp && !isEbml) {
            return { valid: false, reason: 'Header file video tidak valid (bukan file MP4/WebM yang dapat diputar).' };
        }
    } else if (format === 'AUDIO') {
        if (stats.size < 3 * 1024) {
            return { valid: false, reason: `Ukuran file audio terlalu kecil (${stats.size} bytes). File rusak atau terputus.` };
        }
        const isId3 = buf[0] === 0x49 && buf[1] === 0x44 && buf[2] === 0x33;
        const isMp3Sync = buf[0] === 0xFF && (buf[1] & 0xE0) === 0xE0;
        const isM4a = buf.indexOf(Buffer.from('ftyp')) !== -1 || buf.indexOf(Buffer.from('M4A ')) !== -1;
        const isOgg = buf[0] === 0x4F && buf[1] === 0x67 && buf[2] === 0x67 && buf[3] === 0x53;
        const isRiff = buf[0] === 0x52 && buf[1] === 0x49 && buf[2] === 0x46 && buf[3] === 0x46;
        if (!isId3 && !isMp3Sync && !isM4a && !isOgg && !isRiff) {
            return { valid: false, reason: 'Header file audio tidak valid (bukan format MP3/M4A/WAV/Ogg yang sah).' };
        }
    } else if (format === 'IMAGE') {
        if (stats.size < 512) {
            return { valid: false, reason: `Ukuran file gambar terlalu kecil (${stats.size} bytes).` };
        }
        const isJpg = buf[0] === 0xFF && buf[1] === 0xD8 && buf[2] === 0xFF;
        const isPng = buf[0] === 0x89 && buf[1] === 0x50 && buf[2] === 0x4E && buf[3] === 0x47;
        const isWebp = buf.indexOf(Buffer.from('WEBP')) !== -1;
        if (!isJpg && !isPng && !isWebp) {
            return { valid: false, reason: 'Header file gambar tidak valid (bukan JPG/PNG/WebP yang sah).' };
        }
    }

    return { valid: true };
}

// 3. Download Stream to File with Verification and Header Preservation
async function downloadStreamToFile(streamUrl, targetFilePath, options = {}) {
    const { onProgress, stage = 'Mengunduh media...', timeoutMs = 60000 } = options;

    let referer = 'https://www.google.com/';
    const lowerUrl = streamUrl.toLowerCase();
    if (lowerUrl.includes('tiktok') || lowerUrl.includes('tikwm') || lowerUrl.includes('byteoversea') || lowerUrl.includes('ibytedtos') || lowerUrl.includes('muscdn') || lowerUrl.includes('douyin')) {
        referer = 'https://www.tiktok.com/';
    } else if (lowerUrl.includes('twitter') || lowerUrl.includes('twimg') || lowerUrl.includes('x.com') || lowerUrl.includes('fxtwitter') || lowerUrl.includes('vxtwitter') || lowerUrl.includes('t.co')) {
        referer = 'https://twitter.com/';
    } else if (lowerUrl.includes('instagram') || lowerUrl.includes('cdninstagram') || lowerUrl.includes('fbcdn') || lowerUrl.includes('indown')) {
        referer = 'https://www.instagram.com/';
    } else if (lowerUrl.includes('sndcdn') || lowerUrl.includes('soundcloud')) {
        referer = 'https://soundcloud.com/';
    } else if (lowerUrl.includes('googlevideo') || lowerUrl.includes('youtube') || lowerUrl.includes('ytimg') || lowerUrl.includes('youtu.be') || lowerUrl.includes('invidious')) {
        referer = 'https://www.youtube.com/';
    }

    const headers = {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36',
        'Referer': referer,
        'Accept-Encoding': 'identity'
    };

    let response = null;
    try {
        response = await fetch(streamUrl, { headers, redirect: 'follow', signal: AbortSignal.timeout(timeoutMs) });
    } catch (_) {}

    // Fallback to local proxy stream if direct connection had CORS or dropped headers
    if (!response || !response.ok) {
        try {
            const proxyUrl = getProxyStreamUrl(streamUrl);
            response = await fetch(proxyUrl, { signal: AbortSignal.timeout(timeoutMs) });
        } catch (_) {}
    }

    if (!response || !response.ok) {
        throw new Error(`Koneksi download gagal (HTTP ${response ? response.status : 'Offline/Network error'})`);
    }

    const contentType = (response.headers.get('content-type') || '').toLowerCase();
    if (contentType.includes('text/html')) {
        throw new Error('Tautan download kedaluwarsa atau dialihkan ke halaman web captcha.');
    }

    const totalBytes = Number(response.headers.get('content-length')) || 0;
    let downloadedBytes = 0;
    const fileStream = fs.createWriteStream(targetFilePath);

    const reader = response.body.getReader();
    let isFirstChunk = true;
    let lastEmitTime = 0;

    try {
        while (true) {
            const { done, value } = await reader.read();
            if (done) break;

            if (isFirstChunk && value && value.length > 0) {
                isFirstChunk = false;
                const sampleStr = Buffer.from(value.slice(0, 256)).toString('utf8').toLowerCase();
                if (sampleStr.startsWith('<!doctype') || sampleStr.startsWith('<html') || sampleStr.includes('making sure you\'re not a bot')) {
                    throw new Error('Server mengembalikan halaman verifikasi bot alih-alih data media.');
                }
            }

            downloadedBytes += value.length;
            fileStream.write(Buffer.from(value));

            const now = Date.now();
            if (now - lastEmitTime >= 180 || done) {
                lastEmitTime = now;
                const percent = totalBytes > 0 ? Math.min(99, Math.round((downloadedBytes / totalBytes) * 100)) : -1;
                if (typeof onProgress === 'function') {
                    onProgress({ percent, downloadedBytes, totalBytes, stage });
                }
            }
        }
    } finally {
        await new Promise((res) => fileStream.end(res));
    }

    return { sizeBytes: downloadedBytes, contentType };
}

async function extractMediaFromUrl(url) {
    const urlMatch = url.match(/(https?:\/\/[^\s<>"'{}|\\^`]+)/i);
    const rawClean = urlMatch ? urlMatch[1].replace(/[.,;:!?)\]>'"-]+$/, '').trim() : url.trim();
    const cleanUrl = await resolveRedirectUrl(rawClean);

    // 1. TikTok & Douyin
    if (/tiktok\.com|douyin\.com/i.test(cleanUrl)) {
        try {
            const res = await fetch(`https://www.tikwm.com/api/?url=${encodeURIComponent(cleanUrl)}&hd=1`, {
                headers: { 'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36' }
            });
            const json = await res.json();
            if (json.code === 0 && json.data) {
                const d = json.data;
                const toAbsTikwm = (u) => {
                    if (!u) return '';
                    return u.startsWith('http') ? u : `https://www.tikwm.com${u.startsWith('/') ? '' : '/'}${u}`;
                };

                const isImages = Array.isArray(d.images) && d.images.length > 0;
                const options = [];

                if (isImages) {
                    d.images.forEach((imgUrl, i) => {
                        const absImg = toAbsTikwm(imgUrl);
                        options.push({
                            label: `Slide Foto #${i + 1} (Kualitas Asli)`,
                            format: 'IMAGE',
                            url: absImg,
                            previewUrl: getProxyStreamUrl(absImg),
                            quality: 'Original'
                        });
                    });
                    const motionUrl = d.hdplay || d.play;
                    if (motionUrl) {
                        const absMotion = toAbsTikwm(motionUrl);
                        options.push({
                            label: 'Video Live Photo / Slide MP4 (HD)',
                            format: 'VIDEO',
                            url: absMotion,
                            previewUrl: getProxyStreamUrl(absMotion),
                            quality: 'HD 1080p'
                        });
                    }
                } else {
                    if (d.hdplay) {
                        const absHd = toAbsTikwm(d.hdplay);
                        options.push({
                            label: 'Video HD (Tanpa Watermark)',
                            format: 'VIDEO',
                            url: absHd,
                            previewUrl: getProxyStreamUrl(absHd),
                            quality: '1080p HD'
                        });
                    }
                    if (d.play) {
                        const absSd = toAbsTikwm(d.play);
                        options.push({
                            label: options.length === 0 ? 'Video (Tanpa Watermark)' : 'Video Standar (SD)',
                            format: 'VIDEO',
                            url: absSd,
                            previewUrl: getProxyStreamUrl(absSd),
                            quality: 'SD'
                        });
                    }
                }

                if (d.music) {
                    const absMusic = toAbsTikwm(d.music);
                    options.push({
                        label: 'Audio / Musik MP3',
                        format: 'AUDIO',
                        url: absMusic,
                        previewUrl: getProxyStreamUrl(absMusic),
                        quality: 'MP3'
                    });
                }

                const rawThumb = toAbsTikwm(d.cover || (isImages ? d.images[0] : ''));

                return {
                    platform: 'TikTok',
                    title: d.title || 'TikTok Media',
                    author: d.author?.unique_id ? `@${d.author.unique_id}` : (d.author?.nickname || 'TikTok Creator'),
                    thumbnail: getProxyStreamUrl(rawThumb),
                    rawThumbnail: rawThumb,
                    options
                };
            }
        } catch (e) {
            console.error('TikTok extractor error:', e);
        }
    }

    // 2. Twitter / X
    const twitterMatch = cleanUrl.match(/(?:twitter|x|vxtwitter|fxtwitter|fixupx)\.com\/(?:#!\/)?(?:i\/status\/|i\/web\/status\/|[\w.-]+\/status\/)(\d+)/i);
    if (twitterMatch) {
        const tweetId = twitterMatch[1];

        // Strategy A: FxTwitter API
        try {
            const res = await fetch(`https://api.fxtwitter.com/status/${tweetId}`, {
                headers: { 'User-Agent': 'TelegramBot (like TwitterBot)' },
                signal: AbortSignal.timeout(6000)
            });
            if (res.ok) {
                const json = await res.json();
                if (json.tweet && json.tweet.media) {
                    const tweet = json.tweet;
                    const options = [];
                    let thumbnail = '';

                    if (tweet.media.videos && tweet.media.videos.length > 0) {
                        tweet.media.videos.forEach((v, i) => {
                            if (v.url) {
                                if (!thumbnail) thumbnail = v.thumbnail_url || '';
                                options.push({
                                    label: `Video MP4 HD ${tweet.media.videos.length > 1 ? '#' + (i + 1) : ''}`,
                                    format: 'VIDEO',
                                    url: v.url,
                                    previewUrl: getProxyStreamUrl(v.url),
                                    quality: 'HD'
                                });
                            }
                        });
                    }

                    if (tweet.media.photos && tweet.media.photos.length > 0) {
                        tweet.media.photos.forEach((p, i) => {
                            if (p.url) {
                                const highResUrl = p.url.includes('?') ? p.url : `${p.url}?name=orig`;
                                if (!thumbnail) thumbnail = p.url;
                                options.push({
                                    label: `Foto Slide #${i + 1} (Kualitas Asli)`,
                                    format: 'IMAGE',
                                    url: highResUrl,
                                    previewUrl: getProxyStreamUrl(highResUrl),
                                    quality: 'Original'
                                });
                            }
                        });
                    }

                    if (options.length > 0) {
                        return {
                            platform: 'Twitter / X',
                            title: tweet.text || 'Postingan Twitter / X',
                            author: tweet.author?.name ? `${tweet.author.name} (@${tweet.author.screen_name || ''})` : 'Twitter User',
                            thumbnail: getProxyStreamUrl(thumbnail),
                            rawThumbnail: thumbnail,
                            options
                        };
                    }
                }
            }
        } catch (e) {
            console.error('FxTwitter error:', e);
        }

        // Strategy B: Twitter Syndication Embed API
        try {
            const res = await fetch(`https://cdn.syndication.twimg.com/tweet-result?id=${tweetId}&lang=en&token=a`, {
                headers: {
                    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
                    'Origin': 'https://platform.twitter.com',
                    'Referer': 'https://platform.twitter.com/'
                },
                signal: AbortSignal.timeout(6000)
            });
            if (res.ok) {
                const json = await res.json();
                const options = [];
                let thumbnail = json.video?.poster || '';

                if (json.video && json.video.variants) {
                    const mp4s = json.video.variants
                        .filter(v => (v.type || v.content_type || '').includes('mp4'))
                        .sort((a, b) => (b.bitrate || 0) - (a.bitrate || 0));

                    mp4s.forEach((v, idx) => {
                        const videoUrl = v.src || v.url;
                        if (videoUrl) {
                            const label = idx === 0 ? 'Video MP4 HD (Tertinggi)' : (idx === 1 ? 'Video MP4 Medium' : 'Video MP4 Standar');
                            options.push({
                                label,
                                format: 'VIDEO',
                                url: videoUrl,
                                previewUrl: getProxyStreamUrl(videoUrl),
                                quality: idx === 0 ? 'HD' : 'SD'
                            });
                        }
                    });
                }

                if (json.photos && json.photos.length > 0) {
                    json.photos.forEach((p, i) => {
                        const pUrl = p.url ? (p.url.includes('?') ? p.url : `${p.url}?name=orig`) : '';
                        if (pUrl) {
                            if (!thumbnail) thumbnail = p.url;
                            options.push({
                                label: `Foto Slide #${i + 1} (Kualitas Asli)`,
                                format: 'IMAGE',
                                url: pUrl,
                                previewUrl: getProxyStreamUrl(pUrl),
                                quality: 'Original'
                            });
                        }
                    });
                }

                if (options.length > 0) {
                    return {
                        platform: 'Twitter / X',
                        title: (json.text || 'Postingan Twitter / X').replace(/https?:\/\/t\.co\/\w+/g, '').trim(),
                        author: json.user?.name ? `${json.user.name} (@${json.user.screen_name || ''})` : 'Twitter User',
                        thumbnail: getProxyStreamUrl(thumbnail),
                        rawThumbnail: thumbnail,
                        options
                    };
                }
            }
        } catch (e) {
            console.error('Twitter syndication error:', e);
        }
    }

    // 3. YouTube & Shorts & YT Music
    const ytMatch = cleanUrl.match(/(?:youtu\.be\/|youtube(?:-nocookie)?\.com\/(?:embed\/|v\/|shorts\/|live\/|watch\?v=|watch\?.+&v=|music\.youtube\.com\/watch\?v=))([a-zA-Z0-9_-]{11})/i);
    if (ytMatch) {
        const videoId = ytMatch[1];
        const instances = [
            'https://invidious.f5.si',
            'https://inv.nadeko.net',
            'https://invidious.nerdvpn.de',
            'https://iv.melmac.space'
        ];

        for (const instance of instances) {
            try {
                const res = await fetch(`${instance}/api/v1/videos/${videoId}`, {
                    headers: { 'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)' },
                    signal: AbortSignal.timeout(6000)
                });
                if (res.ok) {
                    const json = await res.json();
                    const options = [];
                    const rawThumb = `https://i.ytimg.com/vi/${videoId}/hqdefault.jpg`;

                    const audioStream = Array.isArray(json.adaptiveFormats) ? json.adaptiveFormats.find(f => (f.type || '').startsWith('audio/mp4')) || json.adaptiveFormats.find(f => (f.type || '').startsWith('audio/')) : null;

                    // 1080p HD adaptive stream
                    const stream1080 = Array.isArray(json.adaptiveFormats) ? json.adaptiveFormats.find(f => f.resolution === '1080p' && (f.type || '').includes('mp4')) : null;
                    if (stream1080 && stream1080.url && audioStream && audioStream.url) {
                        options.push({
                            label: 'Video HD 1080p MP4 (Kualitas Tertinggi)',
                            format: 'VIDEO',
                            url: stream1080.url,
                            audioUrl: audioStream.url,
                            previewUrl: getProxyStreamUrl(stream1080.url),
                            quality: '1080p HD'
                        });
                    }

                    // 720p HD stream
                    const stream720 = Array.isArray(json.adaptiveFormats) ? json.adaptiveFormats.find(f => f.resolution === '720p' && (f.type || '').includes('mp4')) : null;
                    const prog720 = Array.isArray(json.formatStreams) ? json.formatStreams.find(s => (s.resolution || '').includes('720')) : null;
                    if (stream720 && stream720.url && audioStream && audioStream.url) {
                        options.push({
                            label: 'Video HD 720p MP4',
                            format: 'VIDEO',
                            url: stream720.url,
                            audioUrl: audioStream.url,
                            previewUrl: getProxyStreamUrl(stream720.url),
                            quality: '720p HD'
                        });
                    } else if (prog720 && prog720.url) {
                        options.push({
                            label: 'Video HD 720p MP4',
                            format: 'VIDEO',
                            url: prog720.url,
                            previewUrl: getProxyStreamUrl(prog720.url),
                            quality: '720p HD'
                        });
                    }

                    // Standard progressive video stream (360p / itag 18)
                    if (Array.isArray(json.formatStreams)) {
                        json.formatStreams.forEach(stream => {
                            if (stream.url && !options.some(o => o.url === stream.url)) {
                                const resLabel = stream.resolution || '360p';
                                options.push({
                                    label: `Video Standar MP4 (${resLabel})`,
                                    format: 'VIDEO',
                                    url: stream.url,
                                    previewUrl: getProxyStreamUrl(stream.url),
                                    quality: resLabel
                                });
                            }
                        });
                    }

                    // Audio MP3 stream
                    if (audioStream && audioStream.url) {
                        options.push({
                            label: 'Audio / Musik MP3 (192kbps)',
                            format: 'AUDIO',
                            url: audioStream.url,
                            previewUrl: getProxyStreamUrl(audioStream.url),
                            quality: 'MP3'
                        });
                    }

                    if (options.length > 0) {
                        // Put audio at top for YouTube Music
                        if (/music\.youtube\.com/i.test(cleanUrl)) {
                            const aIdx = options.findIndex(o => o.format === 'AUDIO');
                            if (aIdx > 0) {
                                const [aOpt] = options.splice(aIdx, 1);
                                options.unshift(aOpt);
                            }
                        }

                        return {
                            platform: /music\.youtube\.com/i.test(cleanUrl) ? 'YT Music' : 'YouTube',
                            title: json.title || 'Video YouTube',
                            author: json.author || 'YouTube Creator',
                            thumbnail: getProxyStreamUrl(rawThumb),
                            rawThumbnail: rawThumb,
                            options
                        };
                    }
                }
            } catch (_) {}
        }

        // Robust Piped instance fallback
        const pipedInstances = ['https://pipedapi.kavin.rocks', 'https://api.piped.privacy.com.de'];
        for (const pInst of pipedInstances) {
            try {
                const pRes = await fetch(`${pInst}/streams/${videoId}`, { signal: AbortSignal.timeout(6000) });
                if (pRes.ok) {
                    const pJson = await pRes.json();
                    const options = [];
                    const rawThumb = `https://i.ytimg.com/vi/${videoId}/hqdefault.jpg`;
                    const aStream = Array.isArray(pJson.audioStreams) ? pJson.audioStreams[0] : null;

                    if (Array.isArray(pJson.videoStreams)) {
                        const v1080 = pJson.videoStreams.find(v => (v.quality || '').includes('1080'));
                        if (v1080 && v1080.url && aStream && aStream.url) {
                            options.push({
                                label: 'Video HD 1080p MP4',
                                format: 'VIDEO',
                                url: v1080.url,
                                audioUrl: aStream.url,
                                previewUrl: getProxyStreamUrl(v1080.url),
                                quality: '1080p HD'
                            });
                        }

                        const v720 = pJson.videoStreams.find(v => (v.quality || '').includes('720'));
                        if (v720 && v720.url && aStream && aStream.url) {
                            options.push({
                                label: 'Video HD 720p MP4',
                                format: 'VIDEO',
                                url: v720.url,
                                audioUrl: aStream.url,
                                previewUrl: getProxyStreamUrl(v720.url),
                                quality: '720p HD'
                            });
                        }
                    }

                    if (aStream && aStream.url) {
                        options.push({
                            label: 'Audio / Musik MP3',
                            format: 'AUDIO',
                            url: aStream.url,
                            previewUrl: getProxyStreamUrl(aStream.url),
                            quality: 'MP3'
                        });
                    }

                    if (options.length > 0) {
                        return {
                            platform: 'YouTube',
                            title: pJson.title || 'Video YouTube',
                            author: pJson.uploader || 'YouTube Creator',
                            thumbnail: getProxyStreamUrl(rawThumb),
                            rawThumbnail: rawThumb,
                            options
                        };
                    }
                }
            } catch (_) {}
        }

        throw new Error('Gagal mengekstrak stream video YouTube. Layanan publik YouTube sedang membatasi akses atau video bersifat privat.');
    }

    // 4. Instagram
    const igMatch = cleanUrl.match(/instagram\.com\/(?:p|reels?|tv|stories|share\/(?:p|reel))\/([A-Za-z0-9_-]+)/i);
    if (igMatch) {
        const shortcode = igMatch[1];
        try {
            // Strategy A: InDown API
            const homeResp = await fetch('https://indown.net/', {
                headers: {
                    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36'
                }
            });
            if (homeResp.ok) {
                const homeHtml = await homeResp.text();
                const expM = homeHtml.match(/k_exp="([^"]+)"/);
                const tokenM = homeHtml.match(/k_token="([^"]+)"/);
                if (expM && tokenM) {
                    const params = new URLSearchParams();
                    params.append('k_exp', expM[1]);
                    params.append('k_token', tokenM[1]);
                    params.append('q', cleanUrl);
                    params.append('t', 'media');
                    params.append('lang', 'en');
                    params.append('v', 'v2');

                    const searchResp = await fetch('https://indown.net/api/ajaxSearch', {
                        method: 'POST',
                        body: params,
                        headers: {
                            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
                            'X-Requested-With': 'XMLHttpRequest',
                            'Referer': 'https://indown.net/',
                            'Origin': 'https://indown.net'
                        }
                    });

                    if (searchResp.ok) {
                        const searchJson = await searchResp.json();
                        if (searchJson.status === 'ok' && searchJson.data) {
                            const html = searchJson.data;
                            const options = [];
                            let mainThumb = '';

                            const liBlocks = [...html.matchAll(/<li>([\s\S]*?)<\/li>/gi)].map(m => m[1]);
                            liBlocks.forEach((itemHtml, idx) => {
                                const isVideo = /icon-dlvideo|Download Video/i.test(itemHtml);
                                const thumbM = itemHtml.match(/src=["'](https?:\/\/[^"']+)["']/i) || itemHtml.match(/data-src=["'](https?:\/\/[^"']+)["']/i);
                                const itemThumb = thumbM ? thumbM[1] : '';
                                if (!mainThumb && itemThumb) mainThumb = itemThumb;

                                if (isVideo) {
                                    const vM = itemHtml.match(/<a[^>]*title=["']Download Video["'][^>]*href=["'](https?:\/\/[^"']+)["']/i) ||
                                               itemHtml.match(/<a[^>]*href=["'](https?:\/\/[^"']+)["'][^>]*class=["'][^"']*download-btn/i) ||
                                               itemHtml.match(/href=["'](https?:\/\/[^"']+)["']/i);
                                    if (vM && vM[1]) {
                                        options.push({
                                            label: `Video Instagram MP4 ${liBlocks.length > 1 ? '#' + (idx + 1) : '(HD)'}`,
                                            format: 'VIDEO',
                                            url: vM[1],
                                            previewUrl: getProxyStreamUrl(vM[1]),
                                            quality: 'HD'
                                        });
                                    }
                                } else {
                                    const pM = itemHtml.match(/<a[^>]*title=["']Download Image["'][^>]*href=["'](https?:\/\/[^"']+)["']/i) ||
                                               itemHtml.match(/<a[^>]*href=["'](https?:\/\/[^"']+)["'][^>]*class=["'][^"']*download-btn/i) ||
                                               itemHtml.match(/href=["'](https?:\/\/[^"']+)["']/i);
                                    if (pM && pM[1]) {
                                        options.push({
                                            label: `Foto Slide #${idx + 1} (Kualitas Asli)`,
                                            format: 'IMAGE',
                                            url: pM[1],
                                            previewUrl: getProxyStreamUrl(pM[1]),
                                            quality: 'Original'
                                        });
                                    }
                                }
                            });

                            if (options.length > 0) {
                                return {
                                    platform: 'Instagram',
                                    title: `Postingan Instagram (${shortcode})`,
                                    author: 'Instagram Creator',
                                    thumbnail: getProxyStreamUrl(mainThumb),
                                    rawThumbnail: mainThumb,
                                    options
                                };
                            }
                        }
                    }
                }
            }
        } catch (e) {
            console.error('Instagram InDown error:', e);
        }

        // Strategy B: Direct Embed Fallback
        try {
            const embedResp = await fetch(`https://www.instagram.com/p/${shortcode}/embed/captioned/`, {
                headers: { 'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36' }
            });
            if (embedResp.ok) {
                const embedHtml = await embedResp.text();
                const displayM = embedHtml.match(/display_url["']?\s*:\s*["']([^"']+)["']/i);
                if (displayM) {
                    const imgUrl = displayM[1].replace(/\\u0026/g, '&').replace(/\\\//g, '/');
                    return {
                        platform: 'Instagram',
                        title: `Postingan Instagram (${shortcode})`,
                        author: 'Instagram User',
                        thumbnail: getProxyStreamUrl(imgUrl),
                        rawThumbnail: imgUrl,
                        options: [
                            {
                                label: 'Foto (Kualitas Asli)',
                                format: 'IMAGE',
                                url: imgUrl,
                                previewUrl: getProxyStreamUrl(imgUrl),
                                quality: 'High'
                            }
                        ]
                    };
                }
            }
        } catch (_) {}
    }

    // 5. SoundCloud
    if (/soundcloud\.com/i.test(cleanUrl)) {
        try {
            const oRes = await fetch(`https://soundcloud.com/oembed?format=json&url=${encodeURIComponent(cleanUrl)}`, {
                headers: { 'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)' }
            });
            let title = 'Lagu SoundCloud';
            let author = 'SoundCloud Artist';
            let artwork = '';

            if (oRes.ok) {
                const oJson = await oRes.json();
                title = (oJson.title || title).replace(' by ' + (oJson.author_name || ''), '').trim();
                author = oJson.author_name || author;
                artwork = oJson.thumbnail_url || '';
            }

            const clientIds = [
                'tHzjkKIyQ5PCLhppl7qomsm6HKpf16tW',
                'a3e059563d7fd3372b49b37f00a00bcf',
                '2t9loNfh90kzJvxKyconeq9wOqv52e0m'
            ];

            for (const cid of clientIds) {
                try {
                    const rRes = await fetch(`https://api-v2.soundcloud.com/resolve?url=${encodeURIComponent(cleanUrl)}&client_id=${cid}`, {
                        headers: {
                            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)',
                            'Accept': 'application/json'
                        }
                    });
                    if (rRes.ok) {
                        const trackJson = await rRes.json();
                        title = trackJson.title || title;
                        author = trackJson.user?.username || author;
                        if (!artwork && trackJson.artwork_url) artwork = trackJson.artwork_url.replace('-large.', '-t500x500.');

                        const transcodings = trackJson.media?.transcodings || [];
                        let chosenUrl = null;
                        const prog = transcodings.find(t => t.format?.protocol === 'progressive');
                        if (prog && prog.url) chosenUrl = prog.url;
                        else if (transcodings.length > 0 && transcodings[0].url) chosenUrl = transcodings[0].url;

                        if (chosenUrl) {
                            const streamRes = await fetch(`${chosenUrl}?client_id=${cid}`, {
                                headers: { 'User-Agent': 'Mozilla/5.0' }
                            });
                            if (streamRes.ok) {
                                const streamJson = await streamRes.json();
                                if (streamJson.url) {
                                    return {
                                        platform: 'SoundCloud',
                                        title,
                                        author,
                                        thumbnail: getProxyStreamUrl(artwork),
                                        rawThumbnail: artwork,
                                        options: [
                                            {
                                                label: 'Audio / Musik MP3 (Kualitas Asli)',
                                                format: 'AUDIO',
                                                url: streamJson.url,
                                                previewUrl: getProxyStreamUrl(streamJson.url),
                                                quality: 'MP3'
                                            }
                                        ]
                                    };
                                }
                            }
                        }
                    }
                } catch (_) {}
            }
        } catch (e) {
            console.error('SoundCloud extractor error:', e);
        }
    }

    // 6. Generic Video/Audio Direct Link
    return {
        platform: 'Web Media',
        title: 'Online Video / Audio Stream',
        author: 'Content Creator',
        thumbnail: '',
        rawThumbnail: '',
        options: [
            { label: 'Download Original Media', format: 'VIDEO', url: cleanUrl, previewUrl: getProxyStreamUrl(cleanUrl), quality: 'HD' },
            { label: 'Audio Stream MP3', format: 'AUDIO', url: cleanUrl, previewUrl: getProxyStreamUrl(cleanUrl), quality: 'MP3' }
        ]
    };
}

ipcMain.handle('system:getClipboardText', () => {
    try {
        return clipboard.readText();
    } catch (_) {
        return '';
    }
});

ipcMain.handle('downloader:getStreamProxyUrl', (event, url) => {
    if (!url) return '';
    return getProxyStreamUrl(url);
});

ipcMain.handle('downloader:extract', async (event, url) => {
    if (!url || typeof url !== 'string') {
        throw new Error('Tautan tidak valid.');
    }
    const match = url.match(/(https?:\/\/[^\s<>"'{}|\\^`]+)/i);
    const clean = match ? match[1].replace(/[.,;:!?)\]>'"-]+$/, '').trim() : url.trim();
    if (!clean.startsWith('http')) {
        throw new Error('Tautan tidak valid.');
    }
    return extractMediaFromUrl(clean);
});

ipcMain.handle('downloader:download', async (event, options) => {
    const { url, audioUrl, filename, format, quality } = options;
    if (!url) throw new Error('Tautan unduhan tidak valid.');

    const downloadsDir = app.getPath('downloads');
    const safeName = (filename || 'HDify_media').replace(/[\\/:*?"<>|]/g, '_');
    const ext = format === 'AUDIO' ? '.mp3' : (format === 'IMAGE' ? '.jpg' : '.mp4');
    let finalPath = path.join(downloadsDir, safeName.endsWith(ext) ? safeName : safeName + ext);

    // Deduplicate file name if file already exists
    let counter = 1;
    const baseName = safeName.endsWith(ext) ? safeName.slice(0, -ext.length) : safeName;
    while (fs.existsSync(finalPath)) {
        finalPath = path.join(downloadsDir, `${baseName}_${counter}${ext}`);
        counter++;
    }

    const tempFilesToClean = [];
    const reportProgress = (pData) => {
        mainWindow?.webContents.send('task-progress', {
            type: 'media-download',
            percent: pData.percent,
            downloadedBytes: pData.downloadedBytes,
            totalBytes: pData.totalBytes,
            stage: pData.stage
        });
    };

    try {
        if (format === 'AUDIO') {
            // Audio Download + Automatic MP3 Transcoding & Verification
            const tempAudio = path.join(os.tmpdir(), `hdify_tmp_audio_${Date.now()}_${Math.random().toString(36).slice(2)}.tmp`);
            tempFilesToClean.push(tempAudio);

            await downloadStreamToFile(url, tempAudio, {
                stage: 'Mengunduh stream audio...',
                onProgress: reportProgress
            });

            reportProgress({ percent: 95, stage: 'Memproses audio MP3 standar...' });

            // Transcode to genuine, pristine MP3 using ffmpeg-static
            await new Promise((resolve, reject) => {
                const convArgs = ['-y', '-i', tempAudio, '-vn', '-c:a', 'libmp3lame', '-b:a', '192k', finalPath];
                execFile(getFfmpegPath(), convArgs, (err) => {
                    if (err) {
                        reject(new Error(`Gagal mengonversi audio ke MP3: ${err.message}`));
                    } else {
                        resolve();
                    }
                });
            });

            const integrity = verifyDownloadedMedia(finalPath, 'AUDIO');
            if (!integrity.valid) {
                try { if (fs.existsSync(finalPath)) fs.unlinkSync(finalPath); } catch (_) {}
                throw new Error(`File audio tidak valid: ${integrity.reason}`);
            }

            const finalStats = fs.statSync(finalPath);
            reportProgress({ percent: 100, stage: 'Selesai!', downloadedBytes: finalStats.size, totalBytes: finalStats.size });
            return { success: true, outputPath: finalPath, totalBytes: finalStats.size };

        } else if (format === 'VIDEO') {
            // Video Download: Single Progressive or Adaptive Muxing (Video + Audio)
            const tempVideo = path.join(os.tmpdir(), `hdify_tmp_video_${Date.now()}_${Math.random().toString(36).slice(2)}.tmp`);
            tempFilesToClean.push(tempVideo);

            if (audioUrl) {
                // Adaptive streams (e.g. YouTube 1080p/720p with separate video + audio streams)
                await downloadStreamToFile(url, tempVideo, {
                    stage: 'Mengunduh video HD (1/2)...',
                    onProgress: (p) => {
                        const halfPct = p.percent >= 0 ? Math.round(p.percent * 0.6) : -1;
                        reportProgress({ percent: halfPct, downloadedBytes: p.downloadedBytes, totalBytes: p.totalBytes, stage: 'Mengunduh video HD (1/2)...' });
                    }
                });

                const tempAudio = path.join(os.tmpdir(), `hdify_tmp_audio_${Date.now()}_${Math.random().toString(36).slice(2)}.tmp`);
                tempFilesToClean.push(tempAudio);

                await downloadStreamToFile(audioUrl, tempAudio, {
                    stage: 'Mengunduh audio jernih (2/2)...',
                    onProgress: (p) => {
                        const secondPct = p.percent >= 0 ? 60 + Math.round(p.percent * 0.3) : -1;
                        reportProgress({ percent: secondPct, downloadedBytes: p.downloadedBytes, totalBytes: p.totalBytes, stage: 'Mengunduh audio jernih (2/2)...' });
                    }
                });

                reportProgress({ percent: 92, stage: 'Menggabungkan video & audio HD...' });

                // Mux video & audio cleanly using ffmpeg-static
                await new Promise((resolve, reject) => {
                    const muxArgs = [
                        '-y',
                        '-i', tempVideo,
                        '-i', tempAudio,
                        '-c:v', 'copy',
                        '-c:a', 'aac',
                        '-movflags', '+faststart',
                        finalPath
                    ];
                    execFile(getFfmpegPath(), muxArgs, (err) => {
                        if (err) {
                            reject(new Error(`Gagal menggabungkan video dan audio: ${err.message}`));
                        } else {
                            resolve();
                        }
                    });
                });

            } else {
                // Single Progressive stream (TikTok, Instagram, Twitter, or progressive YouTube)
                await downloadStreamToFile(url, tempVideo, {
                    stage: 'Mengunduh video...',
                    onProgress: (p) => {
                        reportProgress({ percent: Math.min(95, p.percent), downloadedBytes: p.downloadedBytes, totalBytes: p.totalBytes, stage: 'Mengunduh video...' });
                    }
                });

                reportProgress({ percent: 96, stage: 'Memverifikasi dan memproses video...' });

                // Check if container needs remuxing to MP4
                const vIntegrity = verifyDownloadedMedia(tempVideo, 'VIDEO');
                if (!vIntegrity.valid) {
                    throw new Error(`File video tidak valid: ${vIntegrity.reason}`);
                }

                // Cleanly remux to standard MP4 with faststart to ensure compatibility
                await new Promise((resolve, reject) => {
                    const remuxArgs = ['-y', '-i', tempVideo, '-c', 'copy', '-movflags', '+faststart', finalPath];
                    execFile(getFfmpegPath(), remuxArgs, (err) => {
                        if (!err && fs.existsSync(finalPath)) {
                            resolve();
                        } else {
                            // If remux failed (e.g. raw copy issue), copy file directly
                            try {
                                fs.copyFileSync(tempVideo, finalPath);
                                resolve();
                            } catch (copyErr) {
                                reject(copyErr);
                            }
                        }
                    });
                });
            }

            const finalIntegrity = verifyDownloadedMedia(finalPath, 'VIDEO');
            if (!finalIntegrity.valid) {
                try { if (fs.existsSync(finalPath)) fs.unlinkSync(finalPath); } catch (_) {}
                throw new Error(`Verifikasi video gagal: ${finalIntegrity.reason}`);
            }

            const finalStats = fs.statSync(finalPath);
            reportProgress({ percent: 100, stage: 'Video siap!', downloadedBytes: finalStats.size, totalBytes: finalStats.size });
            return { success: true, outputPath: finalPath, totalBytes: finalStats.size };

        } else if (format === 'IMAGE') {
            // Image Download + Verification
            const tempImage = path.join(os.tmpdir(), `hdify_tmp_img_${Date.now()}_${Math.random().toString(36).slice(2)}.tmp`);
            tempFilesToClean.push(tempImage);

            await downloadStreamToFile(url, tempImage, {
                stage: 'Mengunduh foto...',
                onProgress: reportProgress
            });

            const imgIntegrity = verifyDownloadedMedia(tempImage, 'IMAGE');
            if (!imgIntegrity.valid) {
                throw new Error(`File gambar tidak valid: ${imgIntegrity.reason}`);
            }

            fs.copyFileSync(tempImage, finalPath);
            const finalStats = fs.statSync(finalPath);
            reportProgress({ percent: 100, stage: 'Foto berhasil disimpan!', downloadedBytes: finalStats.size, totalBytes: finalStats.size });
            return { success: true, outputPath: finalPath, totalBytes: finalStats.size };

        } else {
            throw new Error(`Format tidak didukung: ${format}`);
        }

    } catch (err) {
        // Clean up partial destination file on error so user never gets corrupt files!
        try {
            if (fs.existsSync(finalPath)) {
                fs.unlinkSync(finalPath);
            }
        } catch (_) {}
        throw err;

    } finally {
        // Clean up all temporary files
        tempFilesToClean.forEach(tmp => {
            try { if (fs.existsSync(tmp)) fs.unlinkSync(tmp); } catch (_) {}
        });
    }
});

// Benchmark runner
ipcMain.handle('system:runBenchmark', async () => {
    const realesrDir = getBinDir('realesrgan');
    const testImg = path.join(realesrDir, 'input.jpg');
    const testOut = path.join(os.tmpdir(), `hdify_bench_${Date.now()}.png`);
    const exePath = path.join(realesrDir, 'realesrgan-ncnn-vulkan.exe');
    const modelsDir = path.join(realesrDir, 'models');

    if (!fs.existsSync(exePath) || !fs.existsSync(testImg)) {
        return { passed: false, errorMessage: 'Benchmark test files not found' };
    }

    const t0 = Date.now();
    return new Promise((resolve) => {
        const args = ['-i', testImg, '-o', testOut, '-s', '2', '-m', modelsDir];
        execFile(exePath, args, { cwd: path.dirname(exePath) }, (err) => {
            const elapsedMs = Date.now() - t0;
            try { if (fs.existsSync(testOut)) fs.unlinkSync(testOut); } catch (_) {}

            if (!err) {
                resolve({
                    passed: true,
                    engineName: 'Real-ESRGAN Vulkan x2',
                    msPerTile: Math.round(elapsedMs / 4),
                    est1080pSec: ((elapsedMs / 4) * 0.035).toFixed(1),
                    est720pVideoSec: ((elapsedMs / 4) * 0.015).toFixed(1),
                    fps720p: (1000 / Math.max(1, (elapsedMs / 4))).toFixed(1)
                });
            } else {
                resolve({ passed: false, errorMessage: err.message });
            }
        });
    });
});

// AI Models Diagnostic Info
ipcMain.handle('system:getAiModelsInfo', async () => {
    const cuganDir = getBinDir('realcugan');
    const esrganDir = getBinDir('realesrgan');
    const rifeDir = getBinDir('rife');

    const models = [
        {
            name: 'Real-CUGAN SE (Standard Edition)',
            folder: 'models-se',
            installed: fs.existsSync(path.join(cuganDir, 'models-se')),
            type: 'Anime / Ilustrasi 2D',
            desc: 'Model ringan dan cepat dengan konsumsi memori rendah, optimal untuk ilustrasi 2D.'
        },
        {
            name: 'Real-CUGAN Pro (High Quality)',
            folder: 'models-pro',
            installed: fs.existsSync(path.join(cuganDir, 'models-pro')),
            type: 'Anime / Restorasi Garis',
            desc: 'Model resolusi tinggi untuk merestorasi detail garis dan menghapus noise kompresi kuat.'
        },
        {
            name: 'Real-CUGAN No-Scale (Denoise & Restoration)',
            folder: 'models-nose',
            installed: fs.existsSync(path.join(cuganDir, 'models-nose')),
            type: 'Denoise & Restorasi Kompresi',
            desc: 'Membersihkan noise dan artefak kompresi tanpa mengubah resolusi asli gambar.'
        },
        {
            name: 'Real-ESRGAN Plus (Photo & Realistic)',
            folder: 'models/realesrgan-x4plus',
            installed: fs.existsSync(path.join(esrganDir, 'models', 'realesrgan-x4plus.bin')),
            type: 'Foto Nyata / Pemandangan',
            desc: 'Neural network canggih untuk mempertajam tekstur foto manusia, alam, dan produk.'
        },
        {
            name: 'Real-ESRGAN Anime 6B',
            folder: 'models/realesrgan-x4plus-anime',
            installed: fs.existsSync(path.join(esrganDir, 'models', 'realesrgan-x4plus-anime.bin')),
            type: 'Anime & Manga Detail',
            desc: 'Dikhususkan untuk anime dengan mempertahankan ketajaman warna dan garis tepi halus.'
        },
        {
            name: 'Real-ESRGAN Video V3 (Multi-Scale)',
            folder: 'models/realesr-animevideov3',
            installed: fs.existsSync(path.join(esrganDir, 'models', 'realesr-animevideov3-x4.bin')),
            type: 'Video Frame Super-Resolution',
            desc: 'Model temporal ringan untuk frame video super-resolution real-time x2, x3, dan x4.'
        },
        {
            name: 'RIFE v4.6 FlowNet (Frame Interpolation)',
            folder: 'rife-v4.6',
            installed: fs.existsSync(path.join(rifeDir, 'rife-v4.6', 'flownet.bin')) ||
                       fs.existsSync(path.join(rifeDir, 'rife-ncnn-vulkan-20221029-windows', 'rife-ncnn-vulkan.exe')),
            type: 'Interpolasi Frame 60/120 FPS',
            desc: 'Deep neural network FlowNet untuk melipatgandakan frame rate video (2x / 3x / 4x) menjadi 60fps / 120fps.'
        },
        {
            name: 'Vulkan NCNN GPU Acceleration Core',
            folder: 'vulkan-runtime',
            installed: fs.existsSync(path.join(cuganDir, 'realcugan-ncnn-vulkan.exe')) &&
                       fs.existsSync(path.join(esrganDir, 'realesrgan-ncnn-vulkan.exe')),
            type: 'Hardware GPU Engine',
            desc: 'Pipeline akselerasi komputasi grafis Vulkan native untuk inferensi neural network berkecepatan tinggi.'
        }
    ];

    return {
        vulkanEngine: 'NCNN Vulkan Runtime (Active)',
        models: models
    };
});

