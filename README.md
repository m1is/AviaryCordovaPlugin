Aviary Cordova Plugin
===================

A plugman compatible Cordova plugin for the Aviary photo editor.

http://www.aviary.com

Android Instructions
===================
plugman install -platform android -project %PROJECT_PATH% -plugin https://github.com/m1is/AviaryCordovaPlugin -variable API_KEY=%API_KEY%

iOS Instructions
===================
Still working on the iOS version.

How to use the plugin
===================
From a successful callback from the camera:

            var tools = cordova.plugins.Aviary.Tools;
            
            cordova.plugins.Aviary.show({
                imageURI: imageURI,
                outputFormat: cordova.plugins.Aviary.OutputFormat.JPEG,
                quality: 90,
                toolList: [
                    tools.CROP, tools.ENHANCE, tools.EFFECTS
                ],
                hideExitUnsaveConfirmation: false,
                enableEffectsPacks: true,
                enableFramesPacks: true,
                enableStickersPacks: true,
                disableVibration: false,
                folderName: "MyApp",
                success: function (result) {
                    var editedImageFileName = result.name;
                    var editedImageURI = result.src;
                    alert("File name: " + editedImageFileName + ", Image URI: " + editedImageURI);
                },
                error: function (message) {
                    alert(message);
                }
            });
          
            
For more information on the above options see the Aviary documentation.
http://developers.aviary.com/docs

Thanks
===================
Thank you to these projects on Github for the inspiration.

https://github.com/cmcdonaldca/Cordova-Aviary-Plugin

https://github.com/ryanvanderpol/AviaryGap
