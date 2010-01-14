/* DropBox */

#import <Cocoa/Cocoa.h>

@interface DropBox : NSBox
{
    IBOutlet id controller;
    IBOutlet id label;
    NSColor *defaultColor;
    NSString *tmpDir;
    NSArray *files;
}

-(void)highlight:(BOOL)flag;

@end
