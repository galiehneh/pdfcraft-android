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

  const origClick = HTMLAnchorElement.prototype.click;
  HTMLAnchorElement.prototype.click = function() {
    if (this.href && this.href.startsWith("blob:") && window.PdfCraftAndroid) {
      const href = this.href;
      const filename = this.download || "document.pdf";
      fetch(href)
        .then(function(res) { return res.blob(); })
        .then(function(blob) {
          const reader = new FileReader();
          reader.onloadend = function() {
            if (window.PdfCraftAndroid && window.PdfCraftAndroid.saveBlob) {
              window.PdfCraftAndroid.saveBlob(reader.result, filename);
            }
          };
          reader.readAsDataURL(blob);
        })
        .catch(function(err) {
          console.error("Blob download interception error:", err);
        });
    }
    return origClick.apply(this, arguments);
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
  };

  document.addEventListener('contextmenu', function(e) {
    e.preventDefault();
  }, false);
})();
