/**
 * Global toastr config + notify helpers + safe error parser.
 *
 * Include AFTER jQuery + toastr.min.js, BEFORE page-specific scripts.
 * Apply on both shop and admin layouts.
 */
(function () {
    if (typeof window === 'undefined') return;

    // Toastr options — load 1 lan, dung cho moi trang
    if (typeof toastr !== 'undefined') {
        toastr.options = {
            closeButton: true,
            progressBar: true,
            positionClass: 'toast-top-right',
            preventDuplicates: false,
            timeOut: 3500,
            extendedTimeOut: 1500,
            showDuration: 200,
            hideDuration: 300,
            newestOnTop: true
        };
    }

    // ===== Helpers — luon co fallback alert() neu toastr fail =====
    window.notifySuccess = function (msg) {
        if (typeof toastr !== 'undefined') toastr.success(msg || 'Thành công');
        else alert(msg || 'Thành công');
    };
    window.notifyError = function (msg) {
        if (typeof toastr !== 'undefined') toastr.error(msg || 'Có lỗi xảy ra');
        else alert(msg || 'Có lỗi xảy ra');
    };
    window.notifyWarning = function (msg) {
        if (typeof toastr !== 'undefined') toastr.warning(msg || 'Cảnh báo');
        else alert(msg || 'Cảnh báo');
    };
    window.notifyInfo = function (msg) {
        if (typeof toastr !== 'undefined') toastr.info(msg || '');
        else if (msg) alert(msg);
    };

    // Parse error message SAFE — khong throw du backend tra gi
    window.parseAjaxError = function (xhr, fallback) {
        try {
            if (xhr && xhr.responseJSON && xhr.responseJSON.message) {
                return xhr.responseJSON.message;
            }
            if (xhr && xhr.responseText) {
                var j = JSON.parse(xhr.responseText);
                if (j && j.message) return j.message;
            }
        } catch (e) { /* ignore */ }
        return fallback || 'Có lỗi xảy ra, vui lòng thử lại';
    };
})();
