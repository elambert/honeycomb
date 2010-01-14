//
//  ThreadArg.m
//  MusicDrop
//
//  Created by Sacha Arnoud on 4/2/06.
//  Copyright 2006 __MyCompanyName__. All rights reserved.
//

#import "ThreadArg.h"


@implementation ThreadArg

-(id)initWithNb:(int)_nb
{
  nb = _nb;
  return(self);
}

-(int)nb
{
  return(nb);
}

@end
