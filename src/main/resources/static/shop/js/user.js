$(document).ready(function () {
    $("#loginForm").submit(function (e) {
        e.preventDefault();
    }).validate({
        rules: {
            login_email: {
                required: true,
                email: true,
                maxlength: 50
            },
            login_password: {
                required: true,
                rangelength: [6, 20]
            }
        },
        messages: {
            login_email: {
                required: "Vui lòng nhập email!",
                email: "Email không đúng định dạng!",

            },
            login_password: {
                required: "Vui lòng nhập mật khẩu!",
                rangelength: "Mật khẩu có độ dài từ 6-20 ký tự!",
            }
        },

        submitHandler: function () {
            let email = $("#login_email").val();
            let password = $("#login_password").val();

            req = {
                email: email,
                password: password
            }
            let myJSON = JSON.stringify(req);
            $.ajax({
                url: '/api/login',
                type: 'POST',
                data: myJSON,
                contentType: "application/json; charset=utf-8",
                success: function(data) {
                    toastr.success("Đăng nhập thành công");
                    signedValidate(true, data.fullName);
                    $('.modal').modal('hide');
                },
                error: function(error) {
                    toastr.warning(error.responseJSON.message);
                },
            });
        }

    });

    $("#registerForm").submit(function (e) {
        e.preventDefault();
    }).validate({
        rules: {
            register_full_name: {
                required: true,
                maxlength: 25
            },
            register_phone: {
                required: true,
                phone: true
            },
            register_email: {
                required: true,
                email: true,
                maxlength: 50
            },
            register_password: {
                required: true,
                rangelength: [6, 25]
            },
            register_confirm_password: {
                required: true,
                equalTo: "#register_password",
                rangelength: [6, 25]
            }
        },
        messages: {
            register_full_name: {
                required: "Vui lòng nhập đầy đủ họ và tên!",
                maxlength: "Tên có độ dài tối đa 25 ký tự!",

            },
            register_phone: {
                required: "Vui lòng nhập số điện thoại!",
            },
            register_email: {
                required: "Vui lòng nhập email!",
                email: "Email không đúng định dạng!",
                maxlength: "Email có độ dài tối đa 25 ký tự!",
            },
            register_password: {
                required: "Vui lòng nhập mật khẩu!",
                rangelength: "Mật khẩu có độ dài từ 6-20 ký tự!"
            },
            register_confirm_password: {
                required: "Vui lòng nhập lại mật khẩu!",
                equalTo: "Mật khẩu không trùng nhau!",
                rangelength: "Mật khẩu có độ dài từ 6-20 ký tự!"
            }
        },

        submitHandler: function () {
            let fullName = $("#register_full_name").val();
            let phone = $("#register_phone").val();
            let email = $("#register_email").val();
            let password = $("#register_password").val();
            let otp = $("#register_otp").val();

            if (!otp || otp.trim().length !== 6) {
                toastr.warning("Vui lòng nhập mã OTP 6 số đã gửi về email");
                return;
            }

            req = {
                fullName: fullName,
                email: email,
                password: password,
                phone: phone,
                otp: otp.trim()
            }
            var myJSON = JSON.stringify(req);
            $.ajax({
                url: '/api/register',
                type: 'POST',
                data: myJSON,
                contentType: "application/json; charset=utf-8",
                success: function(data) {
                    toastr.success("Đăng ký thành công");
                    signedValidate(true, data.fullName);
                    $('.modal').modal('hide');
                },
                error: function(error) {
                    let msg = (error.responseJSON && error.responseJSON.message) || 'Đăng ký thất bại';
                    toastr.warning(msg);
                },
            });
        }
    })

    // ===== OTP flow =====
    let otpSent = false;
    let otpCooldown = 0;
    let cooldownTimer = null;

    function setOtpCooldown(seconds) {
        otpCooldown = seconds;
        let $btn = $('#btn-send-otp');
        if (cooldownTimer) clearInterval(cooldownTimer);
        function tick() {
            if (otpCooldown <= 0) {
                $btn.prop('disabled', false).text(otpSent ? 'Gửi lại' : 'Gửi OTP');
                clearInterval(cooldownTimer);
                return;
            }
            $btn.prop('disabled', true).text('Gửi lại (' + otpCooldown + 's)');
            otpCooldown--;
        }
        tick();
        cooldownTimer = setInterval(tick, 1000);
    }

    // Delegate event — chac chan binding ngay ca khi modal load sau
    $(document).off('click.otpsend').on('click.otpsend', '#btn-send-otp', function(e) {
        e.preventDefault();
        e.stopPropagation();
        console.log('[OTP] Btn send-otp clicked');

        let email = ($('#register_email').val() || '').trim();
        let $msg = $('#otp-msg');
        $msg.hide();
        if (!email) {
            $msg.text('Vui lòng nhập email trước').css('color', '#dc3545').show();
            return;
        }
        let emailRegex = /^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/;
        if (!emailRegex.test(email)) {
            $msg.text('Email không đúng định dạng').css('color', '#dc3545').show();
            return;
        }
        // Visual feedback dang gui
        $('#btn-send-otp').prop('disabled', true).text('Đang gửi...');
        $msg.text('Đang gửi OTP, vui lòng chờ…').css('color', '#6b7280').show();

        $.ajax({
            url: '/api/register/send-otp',
            type: 'POST',
            data: JSON.stringify({ email: email }),
            contentType: 'application/json; charset=utf-8',
            success: function(data) {
                console.log('[OTP] Send success', data);
                otpSent = true;
                $('#otp-section').show();   // bo slideDown de chac chan visible
                $('#btn-do-register').prop('disabled', false);
                $msg.text('✓ ' + (data.message || 'Mã OTP đã được gửi tới email')).css('color', '#22c55e').show();
                setOtpCooldown(60);
                $('#register_otp').focus();
            },
            error: function(xhr) {
                console.error('[OTP] Send failed', xhr.status, xhr.responseText);
                $('#btn-send-otp').prop('disabled', false).text('Gửi OTP');
                let m;
                if (xhr.status === 0) {
                    m = 'Không kết nối được tới server';
                } else if (xhr.status === 404) {
                    m = 'Server chưa hỗ trợ tính năng này. Vui lòng restart ứng dụng (Spring Boot) để cài đặt endpoint /api/register/send-otp.';
                } else if (xhr.responseJSON && xhr.responseJSON.message) {
                    m = xhr.responseJSON.message;
                } else if (xhr.responseText) {
                    try {
                        let j = JSON.parse(xhr.responseText);
                        m = j.message || ('HTTP ' + xhr.status);
                    } catch (e) { m = 'HTTP ' + xhr.status; }
                } else {
                    m = 'Không gửi được OTP (status ' + xhr.status + ')';
                }
                $msg.text('✗ ' + m).css('color', '#dc3545').show();
            }
        });
    });

    // Reset OTP state khi modal mo lai
    $('#exampleModalRegister').on('hidden.bs.modal', function() {
        otpSent = false;
        $('#otp-section').hide();
        $('#otp-msg').hide();
        $('#btn-do-register').prop('disabled', true);
        $('#register_otp').val('');
        if (cooldownTimer) { clearInterval(cooldownTimer); cooldownTimer = null; }
        $('#btn-send-otp').prop('disabled', false).text('Gửi OTP');
    });

    // Neu user doi email sau khi da gui OTP → reset
    $('#register_email').on('input', function() {
        if (otpSent) {
            otpSent = false;
            $('#otp-section').slideUp(150);
            $('#btn-do-register').prop('disabled', true);
            $('#otp-msg').text('Email đã thay đổi — vui lòng gửi OTP lại').css('color', '#9ca3af').show();
        }
    });

    // ============================================================
    // ========== FORGOT PASSWORD flow ==========
    // ============================================================
    let forgotOtpSent = false;
    let forgotCooldown = 0;
    let forgotCooldownTimer = null;

    function setForgotCooldown(seconds) {
        forgotCooldown = seconds;
        let $btn = $('#btn-forgot-send-otp');
        if (forgotCooldownTimer) clearInterval(forgotCooldownTimer);
        function tick() {
            if (forgotCooldown <= 0) {
                $btn.prop('disabled', false).text(forgotOtpSent ? 'Gửi lại' : 'Gửi OTP');
                clearInterval(forgotCooldownTimer);
                return;
            }
            $btn.prop('disabled', true).text('Gửi lại (' + forgotCooldown + 's)');
            forgotCooldown--;
        }
        tick();
        forgotCooldownTimer = setInterval(tick, 1000);
    }

    // Send OTP for password reset
    $(document).off('click.forgotsend').on('click.forgotsend', '#btn-forgot-send-otp', function(e) {
        e.preventDefault();
        e.stopPropagation();
        console.log('[FORGOT] Btn send-otp clicked');
        let email = ($('#forgot_email').val() || '').trim();
        let $msg = $('#forgot-otp-msg');
        $msg.hide();
        if (!email) {
            $msg.text('Vui lòng nhập email').css('color', '#dc3545').show();
            return;
        }
        let emailRegex = /^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/;
        if (!emailRegex.test(email)) {
            $msg.text('Email không đúng định dạng').css('color', '#dc3545').show();
            return;
        }
        $('#btn-forgot-send-otp').prop('disabled', true).text('Đang gửi...');
        $msg.text('Đang gửi OTP, vui lòng chờ…').css('color', '#6b7280').show();

        $.ajax({
            url: '/api/forgot-password/send-otp',
            type: 'POST',
            data: JSON.stringify({ email: email }),
            contentType: 'application/json; charset=utf-8',
            success: function(data) {
                console.log('[FORGOT] Send success', data);
                forgotOtpSent = true;
                $('#forgot-otp-section').show();
                $('#btn-do-reset').prop('disabled', false);
                $msg.text('✓ ' + (data.message || 'Mã OTP đã được gửi tới email')).css('color', '#22c55e').show();
                setForgotCooldown(60);
                $('#forgot_otp').focus();
            },
            error: function(xhr) {
                console.error('[FORGOT] Send failed', xhr.status, xhr.responseText);
                $('#btn-forgot-send-otp').prop('disabled', false).text('Gửi OTP');
                let m;
                if (xhr.status === 0) {
                    m = 'Không kết nối được tới server';
                } else if (xhr.status === 404) {
                    m = 'Endpoint chưa tồn tại — restart Spring Boot để load /api/forgot-password/send-otp';
                } else if (xhr.responseJSON && xhr.responseJSON.message) {
                    m = xhr.responseJSON.message;
                } else if (xhr.responseText) {
                    try { m = JSON.parse(xhr.responseText).message || ('HTTP ' + xhr.status); }
                    catch(e) { m = 'HTTP ' + xhr.status; }
                } else {
                    m = 'Không gửi được OTP (status ' + xhr.status + ')';
                }
                $msg.text('✗ ' + m).css('color', '#dc3545').show();
            }
        });
    });

    // Reset state khi modal forgot mo lai
    $('#exampleModalForgot').on('hidden.bs.modal', function() {
        forgotOtpSent = false;
        $('#forgot-otp-section').hide();
        $('#forgot-otp-msg').hide();
        $('#btn-do-reset').prop('disabled', true);
        $('#forgot_otp, #forgot_new_password, #forgot_confirm_password').val('');
        if (forgotCooldownTimer) { clearInterval(forgotCooldownTimer); forgotCooldownTimer = null; }
        $('#btn-forgot-send-otp').prop('disabled', false).text('Gửi OTP');
    });

    // Doi email → reset
    $('#forgot_email').on('input', function() {
        if (forgotOtpSent) {
            forgotOtpSent = false;
            $('#forgot-otp-section').slideUp(150);
            $('#btn-do-reset').prop('disabled', true);
            $('#forgot-otp-msg').text('Email đã thay đổi — vui lòng gửi OTP lại').css('color', '#9ca3af').show();
        }
    });

    // Submit reset password
    $('#forgotForm').off('submit').on('submit', function(e) {
        e.preventDefault();
        let email = ($('#forgot_email').val() || '').trim();
        let otp = ($('#forgot_otp').val() || '').trim();
        let newPass = $('#forgot_new_password').val();
        let confirm = $('#forgot_confirm_password').val();

        if (!email) { toastr.warning('Vui lòng nhập email'); return; }
        if (!otp || otp.length !== 6) { toastr.warning('Vui lòng nhập OTP 6 số'); return; }
        if (!newPass || newPass.length < 6 || newPass.length > 20) {
            toastr.warning('Mật khẩu mới phải 6-20 ký tự');
            return;
        }
        if (newPass !== confirm) {
            toastr.warning('Mật khẩu nhập lại không trùng');
            return;
        }

        let $btn = $('#btn-do-reset');
        $btn.prop('disabled', true).text('Đang xử lý...');

        $.ajax({
            url: '/api/forgot-password/reset',
            type: 'POST',
            data: JSON.stringify({ email: email, otp: otp, newPassword: newPass }),
            contentType: 'application/json; charset=utf-8',
            success: function(data) {
                toastr.success(data.message || 'Đặt lại mật khẩu thành công');
                $('#exampleModalForgot').modal('hide');
                setTimeout(function() {
                    $('#exampleModal').modal('show');   // mo lai modal dang nhap
                }, 400);
            },
            error: function(xhr) {
                $btn.prop('disabled', false).text('Đặt lại mật khẩu');
                let m = (xhr.responseJSON && xhr.responseJSON.message) || 'Đặt lại mật khẩu thất bại';
                toastr.warning(m);
            }
        });
    });
});

$.validator.addMethod("phone", function (value, element) {
    return this.optional(element) || /((09|03|07|08|05)+([0-9]{8})\b)/g.test(value);
}, "Số điện thoại không hợp lệ!")

function signedValidate(status = false, fullname = '') {
    if (status == true) {
        isLogined = true;
        let signedLink = `
     <a href="/tai-khoan" id="account-setting">Xin chào ${fullname}</a>`;
        $('.account-setting').replaceWith(signedLink);
    } else {
        isLogined = false;
        let notSignedLink = `
              <a href="#" data-toggle="modal" data-target="#exampleModal" class="header-icon account-setting"><i class="icon-user-2"></i></a>
          `;
        $('.account-setting').replaceWith(notSignedLink);
    }
}

$(document).on('keyup', function (e) {
    let target = e.target;

    if (target.closest('.search-input')) {
        var keycode = (e.keyCode ? e.keyCode : e.which);
        if(keycode == '13'){
            searchProductByKeyword();
        }
    }
})


$('.search-button').click(function() {
    searchProductByKeyword();
})

function searchProductByKeyword() {
    let keyword = $('.search-input').val();
    if (keyword.length == 0) {
        toastr.warning("Vui lòng nhập từ khóa tìm kiếm");
        return
    }
    location.href="/api/tim-kiem?keyword="+keyword;
}