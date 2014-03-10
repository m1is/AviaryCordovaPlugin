// AviaryGap - v.1.0.0
// (c) 2013 Ryan Vanderpol, me@ryanvanderpol.com, MIT Licensed.
// AviaryPlugin.h may be freely distributed under the MIT license.
//
//  AviaryPlugin.h
//

#import <UIKit/UIKit.h>
#import <Cordova/CDVPlugin.h>
#import "AFPhotoEditorController.h"

@interface Aviary : CDVPlugin <AFPhotoEditorControllerDelegate> {
    AFPhotoEditorController* aviary;
}

@property (nonatomic, retain) AFPhotoEditorController *aviary;
@property (nonatomic, retain) NSString* pluginCallbackId;
@property (nonatomic, retain) NSNumber* quality;
@property (nonatomic, retain) NSString* originalImageURI;

- (void) prepareForShow:(CDVInvokedUrlCommand*)command;
- (void) show:(CDVInvokedUrlCommand*)command;

@end
