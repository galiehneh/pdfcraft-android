(function() {
  // Helper to find the hidden input element created by eframe TextAgent
  function getTextAgentInput() {
    return document.querySelector('input[type="text"]') || document.querySelector('input');
  }

  function notifyShowKeyboard() {
    if (window.PdfCraftAndroid && window.PdfCraftAndroid.showKeyboard) {
      window.PdfCraftAndroid.showKeyboard();
    }
  }

  function notifyHideKeyboard() {
    if (window.PdfCraftAndroid && window.PdfCraftAndroid.hideKeyboard) {
      window.PdfCraftAndroid.hideKeyboard();
    }
  }

  window.showSoftKeyboard = function() {
    const input = getTextAgentInput();
    if (input) {
      try { input.focus({ preventScroll: true }); } catch (e) { input.focus(); }
    }
    notifyShowKeyboard();
  };

  window.hideSoftKeyboard = function() {
    const input = getTextAgentInput();
    if (input) {
      try { input.blur(); } catch (e) {}
    }
    notifyHideKeyboard();
  };

  window.setPdfCraftEditMode = function(on) {
    window.isPdfCraftEditMode = on;
    if (!on) {
      window.hideSoftKeyboard();
    }
  };

  window.setDocumentOpened = function(opened) {
    if (window.PdfCraftAndroid && window.PdfCraftAndroid.setDocumentOpened) {
      window.PdfCraftAndroid.setDocumentOpened(opened);
    }
  };

  function hookAgentInput(input) {
    if (!input || input.__agentHooked) return;
    input.__agentHooked = true;
    input.addEventListener('focus', function() {
      notifyShowKeyboard();
    });
    input.addEventListener('blur', function() {
      notifyHideKeyboard();
    });
  }

  const agentObserver = new MutationObserver(function() {
    const input = getTextAgentInput();
    if (input) hookAgentInput(input);
  });
  agentObserver.observe(document.documentElement || document, { childList: true, subtree: true });

  // When canvas is tapped on mobile:
  document.addEventListener('DOMContentLoaded', function() {
    const canvas = document.getElementById('pdfcraft');
    if (canvas) {
      canvas.setAttribute('tabindex', '0');
      canvas.addEventListener('touchend', function(e) {
        const input = getTextAgentInput();
        if (window.isPdfCraftEditMode) {
          if (input) {
            try { input.focus({ preventScroll: true }); } catch (err) { input.focus(); }
          }
          notifyShowKeyboard();
        }
      }, { passive: true });
    }
  });

  const SAVE_CHUNK_BYTES = 512 * 1024;

  function bytesToBase64(bytes) {
    let bin = '';
    const step = 0x8000;
    for (let i = 0; i < bytes.length; i += step) {
      bin += String.fromCharCode.apply(null, bytes.subarray(i, i + step));
    }
    return btoa(bin);
  }

  // Streams a blob to native in ~512 KB chunks: beginSave -> appendChunk* -> finishSave.
  async function saveBlobNative(blob, filename) {
    const bridge = window.PdfCraftAndroid;
    const id = bridge.beginSave(filename || '', blob.type || '');
    if (!id) return; // native already reported the failure
    try {
      for (let off = 0; off < blob.size; off += SAVE_CHUNK_BYTES) {
        const buf = await blob.slice(off, off + SAVE_CHUNK_BYTES).arrayBuffer();
        if (!bridge.appendChunk(id, bytesToBase64(new Uint8Array(buf)))) return;
      }
      bridge.finishSave(id);
    } catch (err) {
      console.error('Blob save error:', err);
      bridge.abortSave(id);
      if (typeof window.onSaveResult === 'function') window.onSaveResult(false, filename || '', String(err));
    }
  }

  function canSaveNatively(url) {
    return typeof url === 'string' && url.startsWith('blob:') &&
      window.PdfCraftAndroid && window.PdfCraftAndroid.beginSave;
  }

  function interceptBlobUrl(url, filename) {
    return fetch(url)
      .then(function(res) { return res.blob(); })
      .then(function(blob) { return saveBlobNative(blob, filename); })
      .catch(function(err) { console.error('Blob download interception error:', err); });
  }

  const origClick = HTMLAnchorElement.prototype.click;
  HTMLAnchorElement.prototype.click = function() {
    if (canSaveNatively(this.href)) {
      interceptBlobUrl(this.href, this.download || '');
    }
    return origClick.apply(this, arguments);
  };

  const origOpen = window.open;
  window.open = function(url) {
    if (canSaveNatively(url)) {
      interceptBlobUrl(url, '');
      return null;
    }
    return origOpen.apply(this, arguments);
  };

  window.handleAndroidBack = function() {
    const ev = new KeyboardEvent('keydown', {
      key: 'Escape',
      code: 'Escape',
      keyCode: 27,
      which: 27,
      bubbles: true
    });
    window.dispatchEvent(ev);
    document.dispatchEvent(ev);
    return ev.defaultPrevented;
  };

  document.addEventListener('contextmenu', function(e) {
    e.preventDefault();
  }, false);
})();
