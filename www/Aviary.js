var cordova = require('cordova'),
    exec = require('cordova/exec');

var Aviary = function () {
    this.OutputFormat = {
        JPEG: "JPEG",
        PNG: "PNG",
        WEBP: "WEBP"
    };

    this.Tools = {
        SHARPNESS: "SHARPNESS",
        BRIGHTNESS: "BRIGHTNESS",
        CONTRAST: "CONTRAST",
        SATURATION: "SATURATION",
        EFFECTS: "EFFECTS",
        RED_EYE: "RED_EYE",
        CROP: "CROP",
        WHITEN: "WHITEN",
        DRAWING: "DRAWING",
        STICKERS: "STICKERS",
        TEXT: "TEXT",
        BLEMISH: "BLEMISH",
        MEME: "MEME",
        ADJUST: "ADJUST",
        ENHANCE: "ENHANCE",
        COLORTEMP: "COLORTEMP",
        BORDERS: "BORDERS",
        COLOR_SPLASH: "COLOR_SPLASH",
        TILT_SHIFT: "TILT_SHIFT",
        ORIENTATION: "ORIENTATION",
        FRAMES: "FRAMES"
    };
};

Aviary.prototype = {
    prepareForShow: function(options) {
        options = options || {}
        cordova.exec(options.success, options.error, "Aviary", "prepareForShow", []);
    },

    show: function (options) {
        var _show = function () {
            cordova.exec(options.success, options.error, "Aviary", "show", [
                options.imageURI, // 0 - image URI
                options.outputFormat, // 1 - output format
                options.quality, // 2 - quality
                options.toolList, // 3 - tool list
                options.hideExitUnsaveConfirmation, // 4 - hide exit unsave confirmation
                options.enableEffectsPacks, // 5 - enable effects packs
                options.enableFramesPacks, // 6 - enable frames packs
                options.enableStickersPacks, // 7 - enable stickers packs
                options.disableVibration, // 8 - disable vibration
                options.inSaveOnNoChanges, // 9 - in save on no changes
                options.folderName // 10 - name of folder where images are saved
            ]);
        };

        /* 
            When selecting an image from an image picker and due to my lack of knowledge of iOS the only way I can get this to launch in iOS without the following error is to introduce a slight delay.

                WARNING: Attempt to present AFSDKViewController: 0x9e73470 on MainViewController: 0x9fc9780 while a presentation is in progress!

            the problem is discussed further here.
            http://stackoverflow.com/questions/20793056/cordova-on-ios-attempt-to-present-on-a-controller-while-a-presentation-is-in-pr 
        */

        if (device.platform.toLowerCase() == "ios") {
            setTimeout(function () {
                _show();
            }, 500);
        } else {
            _show();
        }
    }
};

var aviary = new Aviary();

module.exports = aviary;