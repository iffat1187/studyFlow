/* =============================================
   StudyFlow - Profile Photo Preview
   Client-side convenience ONLY:
   1. Live preview of the chosen file.
   2. Early whitelist feedback (type + size) so the
      user hears about a bad file before submitting.

   Server-side validation in ProfileImageStorage is
   authoritative: the browser only helps, never
   decides what is allowed. A rejected file is
   still a valid serializable image *unless* the
   server rejects it first.
============================================= */

(function () {
    'use strict';

    var MAX_MB = 5;
    document.addEventListener('DOMContentLoaded', function () {

        var input = document.getElementById('profilePictureInput');
        if (!input) {
            return;
        }

        var preview = document.getElementById('profilePicturePreview');
        var message = document.getElementById('profilePhotoMessage');
        var uploadBtn = document.getElementById('profilePictureUploadBtn');

        function clearError() {
            if (message) {
                message.textContent = '';
            }
        }

        function setError(text) {
            if (message) {
                message.textContent = text;
            }
        }

        input.addEventListener('change', function () {
            clearError();

            var file = input.files && input.files.length ? input.files[0] : null;

            if (!file) {
                preview.style.display = 'none';
                preview.removeAttribute('src');
                if (uploadBtn) {
                    uploadBtn.disabled = true;
                }
                return;
            }

            var mime = (file.type || '').toLowerCase();
            if (mime !== 'image/jpeg' && mime !== 'image/jpg' && mime !== 'image/png') {
                setError('Only JPG, JPEG and PNG images are allowed.');
                preview.style.display = 'none';
                preview.removeAttribute('src');
                if (uploadBtn) {
                    uploadBtn.disabled = true;
                }
                return;
            }

            if (file.size > MAX_MB * 1024 * 1024) {
                setError('Profile image must be ' + MAX_MB + ' MB or smaller.');
                preview.style.display = 'none';
                preview.removeAttribute('src');
                if (uploadBtn) {
                    uploadBtn.disabled = true;
                }
                return;
            }

            var reader = new FileReader();
            reader.onload = function (event) {
                preview.setAttribute('src', event.target.result);
                preview.style.display = 'block';
                if (uploadBtn) {
                    uploadBtn.disabled = false;
                }
            };
            reader.readAsDataURL(file);
        });
    });
})();

/* =============================================
   StudyFlow - Password Visibility Toggle
   Only toggles each password input between
   "password" and "text" by its data-target id.
   Never reads, stores or exposes password values.
============================================= */

(function () {
    'use strict';

    document.addEventListener('DOMContentLoaded', function () {

        var toggles = document.querySelectorAll('.password-toggle');

        toggles.forEach(function (toggle) {

            toggle.addEventListener('click', function () {

                var id = toggle.getAttribute('data-target');
                if (!id) {
                    return;
                }

                var field = document.getElementById(id);
                if (!field) {
                    return;
                }

                var show = field.type === 'password';

                field.type = show ? 'text' : 'password';

                var icon = toggle.querySelector('i');
                if (icon) {
                    icon.setAttribute('class', show ? 'bi bi-eye-slash' : 'bi bi-eye');
                }
            });
        });
    });
})();