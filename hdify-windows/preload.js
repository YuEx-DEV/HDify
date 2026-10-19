const { contextBridge, ipcRenderer, webUtils } = require('electron');

contextBridge.exposeInMainWorld('hdifyApi', {
    // Window control
    minimize: () => ipcRenderer.send('window-minimize'),
    maximize: () => ipcRenderer.send('window-maximize'),
    close: () => ipcRenderer.send('window-close'),

    // File path resolver (Electron 30+ / Chromium File.path security mitigation)
    getPathForFile: (file) => {
        if (!file) return '';
        try {
            if (webUtils && typeof webUtils.getPathForFile === 'function') {
                const resolved = webUtils.getPathForFile(file);
                if (resolved) return resolved;
            }
        } catch (_) {}
        return file.path || '';
    },

    // Dialogs & FS
    openFileDialog: (options) => ipcRenderer.invoke('dialog:openFile', options),
    openDirectoryDialog: () => ipcRenderer.invoke('dialog:openDirectory'),
    showItemInFolder: (path) => ipcRenderer.invoke('fs:showInFolder', path),
    openExternal: (url) => ipcRenderer.invoke('fs:openExternal', url),
    openPath: (path) => ipcRenderer.invoke('fs:openPath', path),
    getDefaultOutputDir: () => ipcRenderer.invoke('fs:getDefaultOutputDir'),
    saveDataUrl: (options) => ipcRenderer.invoke('fs:saveDataUrl', options),
    saveTempImage: (dataUrl) => ipcRenderer.invoke('fs:saveTempImage', dataUrl),

    // Hardware & Benchmark
    getHardwareInfo: () => ipcRenderer.invoke('system:getHardwareInfo'),
    runBenchmark: () => ipcRenderer.invoke('system:runBenchmark'),

    // AI Super-Resolution (Images)
    runImageUpscale: (params) => ipcRenderer.invoke('ai:runImageUpscale', params),
    runImageCropPreview: (params) => ipcRenderer.invoke('ai:runImageCropPreview', params),
    cancelImageUpscale: () => ipcRenderer.invoke('ai:cancelImageUpscale'),
    getAiModelsInfo: () => ipcRenderer.invoke('system:getAiModelsInfo'),

    // Video Processing (Compressor & Upscaler)
    runVideoCompress: (params) => ipcRenderer.invoke('video:runCompress', params),
    cancelVideoCompress: () => ipcRenderer.invoke('video:cancelCompress'),
    getVideoMetadata: (filePath) => ipcRenderer.invoke('video:getMetadata', filePath),
    runVideoUpscale: (params) => ipcRenderer.invoke('video:runUpscale', params),
    cancelVideoUpscale: () => ipcRenderer.invoke('video:cancelUpscale'),

    // System / Clipboard
    getClipboardText: () => ipcRenderer.invoke('system:getClipboardText'),

    // Media Downloader
    extractMediaInfo: (url) => ipcRenderer.invoke('downloader:extract', url),
    downloadMedia: (options) => ipcRenderer.invoke('downloader:download', options),
    getStreamProxyUrl: (url) => ipcRenderer.invoke('downloader:getStreamProxyUrl', url),

    // Event listeners
    onProgress: (callback) => {
        const sub = (event, data) => callback(data);
        ipcRenderer.on('task-progress', sub);
        return () => ipcRenderer.removeListener('task-progress', sub);
    }
});
