Aviary Cordova Plugin
===================

A plugman compatible Cordova plugin for the Aviary photo editor.

http://www.aviary.com

Android Instructions
===================
plugman install -platform android -project %PROJECT_PATH% -plugin https://github.com/m1is/AviaryCordovaPlugin -variable API_KEY=%API_KEY%

iOS Instructions
===================
Create a new Cordova project in Xcode then follow the Aviary setup guide to configure the project for the SDK.

http://developers.aviary.com/docs/ios/setup-guide

Then run the following command:

plugman install -platform ios -project %PROJECT_PATH% -plugin https://github.com/m1is/AviaryCordovaPlugin


Cordova (3.0+) Install Note:
=============
cordova plugin add https://github.com/m1is/AviaryCordovaPlugin


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
          
To allow the plugin the execute any optimizations in preparation for showing the editor:

            cordova.plugins.Aviary.prepareForShow({
                success: function (result) {
                    alert("Aviary is prepared to show.");
                }
            });

This step is optional and currently only implemented by the iOS plugin.

Known Issues
===================
- With iOS projects if the plugin is uninstalled and reinstalled it seems to break the plugin with the following error: Plugin 'Aviary' not found, or is not a CDVPlugin.

Thanks
===================
Thank you to these projects on Github for the inspiration.

https://github.com/cmcdonaldca/Cordova-Aviary-Plugin

https://github.com/ryanvanderpol/AviaryGap
