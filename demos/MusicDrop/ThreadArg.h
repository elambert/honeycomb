//
//  ThreadArg.h
//  MusicDrop
//
//  Created by Sacha Arnoud on 4/2/06.
//  Copyright 2006 __MyCompanyName__. All rights reserved.
//

#import <Cocoa/Cocoa.h>


@interface ThreadArg : NSObject {
  int nb;
}

-(id)initWithNb:(int)nb;
-(int)nb;

@end
